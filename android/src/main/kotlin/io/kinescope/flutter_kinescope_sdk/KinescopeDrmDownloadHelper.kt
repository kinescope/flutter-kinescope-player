package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import io.kinescope.sdk.download.DownloadVideoOffline
import io.kinescope.sdk.models.videos.KinescopeQualityMapEntry
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.drm.DrmContentProtection
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

@OptIn(UnstableApi::class)
object KinescopeDrmDownloadHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    /** DRM streams: wait for PSSH before failing (never fall back to clear cache). */
    private const val DRM_PROBE_TIMEOUT_MS = 30_000L
    /** After PSSH: wait for OfflineLicenseHelper callback or fail and free the queue slot. */
    private const val DRM_LICENSE_TIMEOUT_MS = 30_000L

    /** One Widevine probe at a time — concurrent CDM sessions black out active players. */
    private val probeQueue = ArrayDeque<() -> Unit>()
    private var probeRunning = false

    private fun enqueueProbe(work: () -> Unit) {
        mainHandler.post {
            probeQueue.addLast(work)
            drainProbeQueue()
        }
    }

    private fun drainProbeQueue() {
        if (probeRunning) {
            return
        }
        val next = probeQueue.pollFirst() ?: return
        probeRunning = true
        next.invoke()
    }

    private fun releaseProbeSlot() {
        mainHandler.post {
            probeRunning = false
            drainProbeQueue()
        }
    }

    fun widevineLicenseUrl(videoId: String, apiKey: String? = null): String {
        val base = "https://license.kinescope.io/v1/vod/$videoId/acquire/widevine"
        val token = KinescopeSdkConfig.resolveApiKey(apiKey) ?: return base
        return "$base?token=$token"
    }

    fun buildMetadata(
        video: KinescopeVideo,
        manifestUri: String,
        licenseUrl: String?,
        contentId: String,
        qualityHeight: Int? = null,
        qualityLabel: String? = null,
    ): ByteArray {
        val json = JSONObject().apply {
            put("contentId", contentId)
            put("videoId", video.id)
            put("title", video.title)
            put("manifestUri", manifestUri)
            if (!licenseUrl.isNullOrBlank()) {
                put("licenseUrl", licenseUrl)
            }
            if (qualityHeight != null && qualityHeight > 0) {
                put("qualityHeight", qualityHeight)
            }
            if (!qualityLabel.isNullOrBlank()) {
                put("qualityLabel", qualityLabel)
            }
            val qualityMap = video.qualityMap
            if (!qualityMap.isNullOrEmpty()) {
                put(
                    "qualityMap",
                    JSONArray().apply {
                        qualityMap.forEach { entry ->
                            put(
                                JSONObject().apply {
                                    put("height", entry.height)
                                    put("name", entry.name)
                                    entry.label?.let { put("label", it) }
                                },
                            )
                        }
                    },
                )
            }
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun buildManifestMetadata(
        manifestUri: String,
        contentId: String,
        title: String? = null,
        qualityHeight: Int? = null,
        qualityLabel: String? = null,
    ): ByteArray {
        val json = JSONObject().apply {
            put("contentId", contentId)
            put("manifestUri", manifestUri)
            if (!title.isNullOrBlank()) {
                put("title", title)
            }
            if (qualityHeight != null && qualityHeight > 0) {
                put("qualityHeight", qualityHeight)
            }
            if (!qualityLabel.isNullOrBlank()) {
                put("qualityLabel", qualityLabel)
            }
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun parseMetadata(data: ByteArray?): OfflineDownloadMetadata? {
        if (data == null || data.isEmpty()) {
            return null
        }
        return try {
            val json = JSONObject(String(data, Charsets.UTF_8))
            OfflineDownloadMetadata(
                contentId = json.optString("contentId").takeIf { it.isNotEmpty() },
                videoId = json.optString("videoId").takeIf { it.isNotEmpty() },
                title = json.optString("title").takeIf { it.isNotEmpty() },
                manifestUri = json.optString("manifestUri").takeIf { it.isNotEmpty() },
                licenseUrl = json.optString("licenseUrl").takeIf { it.isNotEmpty() },
                qualityHeight = json.optInt("qualityHeight", 0).takeIf { it > 0 },
                qualityLabel = json.optString("qualityLabel").takeIf { it.isNotEmpty() },
                qualityMap = parseQualityMap(json.optJSONArray("qualityMap")),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQualityMap(array: JSONArray?): List<KinescopeQualityMapEntry> {
        if (array == null || array.length() == 0) {
            return emptyList()
        }
        val entries = ArrayList<KinescopeQualityMapEntry>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val height = item.optInt("height", 0)
            if (name.isEmpty() || height <= 0) {
                continue
            }
            entries.add(
                KinescopeQualityMapEntry(
                    label = item.optString("label").takeIf { it.isNotEmpty() },
                    name = name,
                    height = height,
                ),
            )
        }
        return entries
    }

    /**
     * Starts a quality-filtered download, acquiring an offline Widevine license only when
     * [KinescopeVideo.drm] indicates DRM. Clear videos skip the probe entirely so a failed
     * Widevine session cannot abort the download.
     */
    fun startDownloadWithOptionalDrm(
        context: Context,
        video: KinescopeVideo,
        manifestUri: Uri,
        mimeType: String,
        contentId: String,
        videoHeightPx: Int,
        videoWidthPx: Int = C.LENGTH_UNSET,
        qualityHint: String? = null,
        apiKey: String? = null,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        val appContext = context.applicationContext
        val manifest = manifestUri.toString()
        val drmLicenseFromVideo = video.drm?.widevine?.licenseUrl?.takeIf { it.isNotBlank() }
        val requiresDrm = drmLicenseFromVideo != null
        val licenseUrl = drmLicenseFromVideo
            ?: widevineLicenseUrl(video.id, apiKey)
        val metadata = buildMetadata(
            video = video,
            manifestUri = manifest,
            licenseUrl = if (requiresDrm) licenseUrl else null,
            contentId = contentId,
            qualityHeight = videoHeightPx,
            qualityLabel = qualityHint,
        )
        val hlsLink = video.hlsLink ?: manifest

        var finished = false
        var drmProbeStarted = false
        var holdsProbeSlot = false
        var tempPlayer: ExoPlayer? = null
        var licenseTimeoutRunnable: Runnable? = null

        fun cancelLicenseTimeout() {
            licenseTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            licenseTimeoutRunnable = null
        }

        fun finish(result: Result<Unit>) {
            if (finished) {
                return
            }
            finished = true
            cancelLicenseTimeout()
            if (holdsProbeSlot) {
                holdsProbeSlot = false
                releaseProbeSlot()
            }
            mainHandler.post { onComplete(result) }
        }

        fun releaseProbePlayer() {
            tempPlayer?.release()
            tempPlayer = null
        }

        fun startQualityDownload(keySetId: ByteArray?) {
            startDownloadRequest(
                context = appContext,
                contentId = contentId,
                manifestUri = manifestUri,
                mimeType = mimeType,
                metadata = metadata,
                keySetId = keySetId,
                videoHeightPx = videoHeightPx,
                videoWidthPx = videoWidthPx,
                qualityHint = qualityHint,
                licenseUrl = if (keySetId != null) licenseUrl else null,
                onStarted = { finish(Result.success(Unit)) },
                onError = { error -> finish(Result.failure(error)) },
            )
        }

        // Clear / unknown DRM: never force a Widevine MediaItem — probe errors used to
        // abort downloadVideo for ordinary non-DRM titles.
        if (!requiresDrm) {
            startQualityDownload(keySetId = null)
            return
        }

        // ExoPlayer + listeners must run on the main looper.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                startDownloadWithOptionalDrm(
                    context = context,
                    video = video,
                    manifestUri = manifestUri,
                    mimeType = mimeType,
                    contentId = contentId,
                    videoHeightPx = videoHeightPx,
                    videoWidthPx = videoWidthPx,
                    qualityHint = qualityHint,
                    apiKey = apiKey,
                    onComplete = onComplete,
                )
            }
            return
        }

        enqueueProbe {
            holdsProbeSlot = true
            if (finished) {
                holdsProbeSlot = false
                releaseProbeSlot()
                return@enqueueProbe
            }

            fun startDrmDownload(pssh: ByteArray) {
                if (finished) {
                    return
                }
                val drmConfigurator = DrmConfigurator(appContext)
                val protection = DrmContentProtection(
                    schemeUri = C.WIDEVINE_UUID.toString(),
                    licenseUrl = licenseUrl,
                    schemeUuid = C.WIDEVINE_UUID,
                )
                // PSSH timeout no longer applies once drmProbeStarted — without this,
                // a silent downloadOfflineLicense leaves probeRunning stuck forever.
                val licenseTimeout = Runnable {
                    if (finished) {
                        return@Runnable
                    }
                    releaseProbePlayer()
                    finish(
                        Result.failure(
                            IllegalStateException(
                                "Timed out acquiring offline DRM license. Check network and apiKey, then retry.",
                            ),
                        ),
                    )
                }
                licenseTimeoutRunnable = licenseTimeout
                mainHandler.postDelayed(licenseTimeout, DRM_LICENSE_TIMEOUT_MS)

                drmConfigurator.downloadOfflineLicense(
                    videoUrl = hlsLink,
                    drmContentProtection = protection,
                    contentId = contentId,
                    psshData = pssh,
                ) { keySetId ->
                    // License exchange completed — drop the acquire timeout before Media3
                    // startDownload, which can take longer than DRM_LICENSE_TIMEOUT_MS.
                    cancelLicenseTimeout()
                    if (finished) {
                        return@downloadOfflineLicense
                    }
                    if (keySetId == null) {
                        finish(
                            Result.failure(
                                IllegalStateException(
                                    "Failed to acquire offline DRM license. Configure Kinescope SDK apiKey.",
                                ),
                            ),
                        )
                        return@downloadOfflineLicense
                    }
                    startQualityDownload(keySetId)
                }
            }

            fun onPsshFound(pssh: ByteArray) {
                if (finished || drmProbeStarted) {
                    return
                }
                drmProbeStarted = true
                releaseProbePlayer()
                startDrmDownload(pssh)
            }

            fun widevinePssh(drmInitData: DrmInitData?): ByteArray? {
                if (drmInitData == null) {
                    return null
                }
                for (i in 0 until drmInitData.schemeDataCount) {
                    val schemeData = drmInitData.get(i)
                    if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                        return schemeData.data
                    }
                }
                return null
            }

            fun psshFromPlayer(player: ExoPlayer): ByteArray? {
                widevinePssh(player.videoFormat?.drmInitData)?.let { return it }
                val tracks = player.currentTracks
                for (group in tracks.groups) {
                    val mediaGroup = group.mediaTrackGroup
                    for (i in 0 until mediaGroup.length) {
                        widevinePssh(mediaGroup.getFormat(i).drmInitData)?.let { return it }
                    }
                }
                return null
            }

            // Native DrmHelper only invokes its callback when Context is an Activity
            // (`(context as? Activity)?.runOnUiThread`). Flutter often has only
            // applicationContext here — PSSH was found but the callback never fired.
            // Probe with AnalyticsListener + mainHandler instead.
            tempPlayer = ExoPlayer.Builder(appContext).build().also { player ->
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus= */ false,
                )
                player.volume = 0f
                player.playWhenReady = false

                val analyticsListener = object : AnalyticsListener {
                    override fun onDownstreamFormatChanged(
                        eventTime: AnalyticsListener.EventTime,
                        mediaLoadData: MediaLoadData,
                    ) {
                        widevinePssh(mediaLoadData.trackFormat?.drmInitData)?.let { onPsshFound(it) }
                    }

                    override fun onDrmSessionAcquired(
                        eventTime: AnalyticsListener.EventTime,
                        state: Int,
                    ) {
                        psshFromPlayer(player)?.let { onPsshFound(it) }
                    }

                    override fun onTracksChanged(
                        eventTime: AnalyticsListener.EventTime,
                        trackGroups: Tracks,
                    ) {
                        psshFromPlayer(player)?.let { onPsshFound(it) }
                    }
                }

                player.addAnalyticsListener(analyticsListener)
                player.addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            if (finished) {
                                return
                            }
                            // Prefer PSSH if the error happened after tracks loaded.
                            psshFromPlayer(player)?.let {
                                onPsshFound(it)
                                return
                            }
                            releaseProbePlayer()
                            if (drmProbeStarted) {
                                finish(
                                    Result.failure(
                                        error.cause
                                            ?: IllegalStateException("DRM license acquisition failed"),
                                    ),
                                )
                                return
                            }
                            finish(
                                Result.failure(
                                    IllegalStateException(
                                        "DRM-protected video: failed to obtain PSSH / offline license",
                                        error,
                                    ),
                                ),
                            )
                        }
                    },
                )

                val mediaItem = MediaItem.Builder()
                    .setUri(hlsLink)
                    .setDrmUuid(C.WIDEVINE_UUID)
                    .setDrmLicenseUri(licenseUrl)
                    .setDrmMultiSession(true)
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()

                // Poll briefly — same idea as native DrmHelper's 6×500ms loop.
                fun pollPssh(attempt: Int) {
                    if (finished || drmProbeStarted || attempt >= 40) {
                        return
                    }
                    psshFromPlayer(player)?.let {
                        onPsshFound(it)
                        return
                    }
                    mainHandler.postDelayed({ pollPssh(attempt + 1) }, 500L)
                }
                mainHandler.postDelayed({ pollPssh(0) }, 500L)
            }

            mainHandler.postDelayed(
                {
                    if (finished || drmProbeStarted) {
                        return@postDelayed
                    }
                    releaseProbePlayer()
                    // Never cache DRM content without keySetId.
                    finish(
                        Result.failure(
                            IllegalStateException(
                                "Timed out waiting for DRM PSSH. Check network and apiKey, then retry.",
                            ),
                        ),
                    )
                },
                DRM_PROBE_TIMEOUT_MS,
            )
        }
    }

    private fun startDownloadRequest(
        context: Context,
        contentId: String,
        manifestUri: Uri,
        mimeType: String,
        metadata: ByteArray,
        keySetId: ByteArray?,
        videoHeightPx: Int,
        videoWidthPx: Int = C.LENGTH_UNSET,
        qualityHint: String? = null,
        licenseUrl: String? = null,
        onStarted: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        DownloadVideoOffline.startDownloadWithQuality(
            context = context,
            contentId = contentId,
            manifestUri = manifestUri,
            videoHeightPx = videoHeightPx,
            videoWidthPx = videoWidthPx,
            mimeType = mimeType,
            data = metadata,
            keySetId = keySetId,
            drmLicenseUrl = licenseUrl,
            qualityHint = qualityHint,
            onError = onError,
            onStarted = onStarted,
        )
    }
}

data class OfflineDownloadMetadata(
    val contentId: String?,
    val videoId: String?,
    val title: String?,
    val manifestUri: String?,
    val licenseUrl: String?,
    val qualityHeight: Int? = null,
    val qualityLabel: String? = null,
    /** Embed `quality_map` for settings labels (0.1.4+). */
    val qualityMap: List<KinescopeQualityMapEntry> = emptyList(),
)
