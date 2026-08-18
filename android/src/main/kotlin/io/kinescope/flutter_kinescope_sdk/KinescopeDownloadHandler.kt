package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import io.kinescope.sdk.download.DownloadVideoOffline
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.shorts.download.OfflineDownloadQualityHelper
import org.json.JSONObject

@OptIn(UnstableApi::class)
class KinescopeDownloadHandler(
    private val contextProvider: () -> Context,
    private val eventSink: (Map<String, Any?>) -> Unit,
) {
    private var downloadListener: DownloadManager.Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val context: Context
        get() = contextProvider()

    fun initialize() {
        DownloadVideoOffline.initialize(context)
        DownloadVideoOffline.getDownloadManager(context).resumeDownloads()
        ensureDownloadListener()
    }

    fun startDownload(args: Map<*, *>) {
        initialize()
        val contentId = args["contentId"] as String
        val manifestUri = Uri.parse(args["manifestUri"] as String)
        val mimeType = args["mimeType"] as? String ?: MimeTypes.APPLICATION_M3U8
        val metadata = (args["metadata"] as? String)?.toByteArray(Charsets.UTF_8)
        val videoHeightPx = (args["videoHeightPx"] as? Number)?.toInt()
        val videoWidthPx = (args["videoWidthPx"] as? Number)?.toInt() ?: C.LENGTH_UNSET
        val qualityHint = args["qualityHint"] as? String
        val keySetIdBase64 = args["keySetId"] as? String
        val keySetId = if (!keySetIdBase64.isNullOrEmpty()) {
            Base64.decode(keySetIdBase64, Base64.DEFAULT)
        } else {
            null
        }

        if (videoHeightPx != null && videoHeightPx > 0) {
            DownloadVideoOffline.startDownloadWithQuality(
                context = context,
                contentId = contentId,
                manifestUri = manifestUri,
                videoHeightPx = videoHeightPx,
                videoWidthPx = videoWidthPx,
                mimeType = mimeType,
                data = metadata,
                keySetId = keySetId,
                qualityHint = qualityHint,
            )
        } else {
            val builder = DownloadRequest.Builder(contentId, manifestUri)
                .setMimeType(mimeType)
            metadata?.let { builder.setData(it) }
            keySetId?.let { builder.setKeySetId(it) }
            DownloadVideoOffline.startDownload(context, builder.build())
        }
        emitDownloadsChanged()
    }

    fun listDownloadQualities(
        videoId: String,
        apiKey: String?,
        callback: (Result<List<Map<String, Any?>>>) -> Unit,
    ) {
        initialize()
        val tempPlayer = KinescopeVideoPlayer(context.applicationContext)
        tempPlayer.loadVideo(
            videoId,
            onSuccess = { video ->
                // Release immediately — keeping KinescopeVideoPlayer alive while listing
                // qualities / starting other downloads blacks out the active PlatformView.
                mainHandler.post {
                    releaseQuietly(tempPlayer)
                    if (video == null) {
                        callback(Result.failure(IllegalStateException("Failed to load video metadata")))
                        return@post
                    }
                    try {
                        listQualitiesForVideo(video, apiKey) { result ->
                            mainHandler.post { callback(result) }
                        }
                    } catch (error: Exception) {
                        callback(Result.failure(error))
                    }
                }
            },
            onFailed = { error ->
                mainHandler.post {
                    releaseQuietly(tempPlayer)
                    callback(
                        Result.failure(
                            error ?: IllegalStateException("Failed to load video metadata"),
                        ),
                    )
                }
            },
        )
    }

    fun downloadVideo(
        videoId: String,
        contentId: String?,
        apiKey: String?,
        videoHeightPx: Int?,
        videoWidthPx: Int?,
        qualityHint: String?,
        callback: (Result<Map<String, Any?>>) -> Unit,
    ) {
        initialize()
        val tempPlayer = KinescopeVideoPlayer(context.applicationContext)
        tempPlayer.loadVideo(
            videoId,
            onSuccess = { video ->
                // Drop the metadata player before DRM probe / Media3 download. Overlapping
                // KinescopeVideoPlayer instances fight over the shared surface / CDM and
                // produce a black PlatformView when several downloads start close together.
                mainHandler.post {
                    releaseQuietly(tempPlayer)
                    try {
                        if (video == null) {
                            callback(Result.failure(IllegalStateException("Failed to load video metadata")))
                            return@post
                        }
                        fun startWithHeight(height: Int, width: Int, hint: String?) {
                            val result = startDownloadForVideo(
                                video = video,
                                contentId = contentId,
                                apiKey = apiKey,
                                videoHeightPx = height,
                                videoWidthPx = width,
                                qualityHint = hint,
                            ) { downloadResult ->
                                mainHandler.post { callback(downloadResult) }
                            }
                            if (result != null) {
                                callback(Result.success(result))
                            }
                        }

                        if (videoHeightPx != null && videoHeightPx > 0) {
                            startWithHeight(
                                videoHeightPx,
                                videoWidthPx ?: C.LENGTH_UNSET,
                                qualityHint,
                            )
                            return@post
                        }

                        listQualitiesForVideo(video, apiKey) { qualitiesResult ->
                            qualitiesResult
                                .onSuccess { qualities ->
                                    val chosen = qualities.maxByOrNull { quality ->
                                        (quality["height"] as? Number)?.toInt() ?: 0
                                    }
                                    if (chosen == null) {
                                        mainHandler.post {
                                            callback(
                                                Result.failure(
                                                    IllegalStateException("No downloadable qualities"),
                                                ),
                                            )
                                        }
                                        return@onSuccess
                                    }
                                    val height = (chosen["height"] as Number).toInt()
                                    val width = (chosen["width"] as? Number)?.toInt()
                                        ?: C.LENGTH_UNSET
                                    val hint = chosen["label"] as? String
                                        ?: chosen["qualityName"] as? String
                                    mainHandler.post {
                                        startWithHeight(height, width, hint)
                                    }
                                }
                                .onFailure { error ->
                                    mainHandler.post { callback(Result.failure(error)) }
                                }
                        }
                    } catch (error: Exception) {
                        callback(Result.failure(error))
                    }
                }
            },
            onFailed = { error ->
                mainHandler.post {
                    releaseQuietly(tempPlayer)
                    callback(
                        Result.failure(
                            error ?: IllegalStateException("Failed to load video metadata"),
                        ),
                    )
                }
            },
        )
    }

    private fun releaseQuietly(player: KinescopeVideoPlayer) {
        try {
            player.release()
        } catch (_: Exception) {
        }
    }

    private fun listQualitiesForVideo(
        video: KinescopeVideo,
        apiKey: String?,
        callback: (Result<List<Map<String, Any?>>>) -> Unit,
    ) {
        val (manifestUri, mimeType) = resolveManifest(video)
        val hints = video.qualityMap?.map { entry ->
            OfflineDownloadQualityHelper.QualityMapHint(
                height = entry.height,
                name = entry.name,
                label = entry.label,
            )
        }
        val licenseUrl = KinescopeDrmDownloadHelper.widevineLicenseUrl(
            video.id,
            KinescopeSdkConfig.resolveApiKey(apiKey),
        )
        val fromMap = OfflineDownloadQualityHelper.qualitiesFromQualityMap(hints)
            .map { it.toMap() }

        fun done(result: Result<List<Map<String, Any?>>>) {
            mainHandler.post { callback(result) }
        }

        // DRM streams often expose no clear tracks until a Widevine session opens —
        // quality_map from embed JSON is enough for the picker.
        if (fromMap.isNotEmpty() && video.drm?.widevine?.licenseUrl != null) {
            done(Result.success(fromMap))
            return
        }

        DownloadVideoOffline.listDownloadQualities(
            context = context,
            manifestUri = manifestUri,
            qualityMap = hints,
            mimeType = mimeType,
            drmLicenseUrl = licenseUrl,
        ) { result ->
            result
                .onSuccess { qualities ->
                    if (qualities.isNotEmpty()) {
                        done(Result.success(qualities.map { it.toMap() }))
                    } else if (fromMap.isNotEmpty()) {
                        done(Result.success(fromMap))
                    } else {
                        done(Result.failure(IllegalStateException("No downloadable qualities")))
                    }
                }
                .onFailure { error ->
                    if (fromMap.isNotEmpty()) {
                        done(Result.success(fromMap))
                    } else {
                        done(Result.failure(error))
                    }
                }
        }
    }

    private fun startDownloadForVideo(
        video: KinescopeVideo,
        contentId: String?,
        apiKey: String?,
        videoHeightPx: Int,
        videoWidthPx: Int,
        qualityHint: String?,
        onAsyncStarted: ((Result<Map<String, Any?>>) -> Unit)? = null,
    ): Map<String, Any?>? {
        val (manifestUri, mimeType) = resolveManifest(video)
        val resolvedContentId = contentId
            ?: KinescopeOfflineIds.stableContentId(manifestUri.toString(), videoHeightPx)

        val existing = DownloadVideoOffline.getDownloadById(context, resolvedContentId)
        if (existing?.state == Download.STATE_COMPLETED) {
            return existing.toMap()
        }

        if (onAsyncStarted != null) {
            KinescopeDrmDownloadHelper.startDownloadWithOptionalDrm(
                context = context,
                video = video,
                manifestUri = manifestUri,
                mimeType = mimeType,
                contentId = resolvedContentId,
                videoHeightPx = videoHeightPx,
                videoWidthPx = videoWidthPx,
                qualityHint = qualityHint,
                apiKey = KinescopeSdkConfig.resolveApiKey(apiKey),
            ) { result ->
                result
                    .onSuccess {
                        emitDownloadsChanged()
                        val download = DownloadVideoOffline.getDownloadById(context, resolvedContentId)
                        onAsyncStarted(
                            Result.success(
                                download?.toMap() ?: queuedDownloadMap(
                                    contentId = resolvedContentId,
                                    video = video,
                                    manifestUri = manifestUri,
                                    mimeType = mimeType,
                                    qualityHeight = videoHeightPx,
                                    qualityLabel = qualityHint,
                                ),
                            ),
                        )
                    }
                    .onFailure { error ->
                        onAsyncStarted(Result.failure(error))
                    }
            }
            return null
        }

        val metadata = KinescopeDrmDownloadHelper.buildMetadata(
            video = video,
            manifestUri = manifestUri.toString(),
            licenseUrl = KinescopeDrmDownloadHelper.widevineLicenseUrl(
                video.id,
                KinescopeSdkConfig.resolveApiKey(apiKey),
            ),
            contentId = resolvedContentId,
            qualityHeight = videoHeightPx,
            qualityLabel = qualityHint,
        )
        DownloadVideoOffline.startDownloadWithQuality(
            context = context,
            contentId = resolvedContentId,
            manifestUri = manifestUri,
            videoHeightPx = videoHeightPx,
            videoWidthPx = videoWidthPx,
            mimeType = mimeType,
            data = metadata,
            qualityHint = qualityHint,
        )
        emitDownloadsChanged()

        val download = DownloadVideoOffline.getDownloadById(context, resolvedContentId)
        return download?.toMap() ?: queuedDownloadMap(
            contentId = resolvedContentId,
            video = video,
            manifestUri = manifestUri,
            mimeType = mimeType,
            qualityHeight = videoHeightPx,
            qualityLabel = qualityHint,
        )
    }

    private fun resolveManifest(video: KinescopeVideo): Pair<Uri, String> {
        return when {
            !video.hlsLink.isNullOrEmpty() ->
                Uri.parse(video.hlsLink) to MimeTypes.APPLICATION_M3U8
            !video.dashLink.isNullOrEmpty() ->
                Uri.parse(video.dashLink) to MimeTypes.APPLICATION_MPD
            else -> throw IllegalStateException("Video has no downloadable stream")
        }
    }

    private fun queuedDownloadMap(
        contentId: String,
        video: KinescopeVideo,
        manifestUri: Uri,
        mimeType: String,
        qualityHeight: Int?,
        qualityLabel: String?,
    ): Map<String, Any?> = mapOf(
        "contentId" to contentId,
        "videoId" to video.id,
        "title" to video.title,
        "uri" to manifestUri.toString(),
        "mimeType" to mimeType,
        "state" to "queued",
        "percent" to 0,
        "bytesDownloaded" to 0L,
        "qualityHeight" to qualityHeight,
        "qualityLabel" to qualityLabel,
    )

    fun downloadFromUrl(
        url: String,
        contentId: String?,
        apiKey: String?,
        videoHeightPx: Int?,
        videoWidthPx: Int?,
        qualityHint: String?,
        title: String?,
        callback: (Result<Map<String, Any?>>) -> Unit,
    ) {
        initialize()
        try {
            when (val target = KinescopeDownloadUrlResolver.resolve(url)) {
                is KinescopeDownloadUrlResolver.Target.VideoId -> {
                    downloadVideo(
                        videoId = target.videoId,
                        contentId = contentId,
                        apiKey = apiKey,
                        videoHeightPx = videoHeightPx,
                        videoWidthPx = videoWidthPx,
                        qualityHint = qualityHint,
                        callback = callback,
                    )
                }
                is KinescopeDownloadUrlResolver.Target.Manifest -> {
                    downloadManifest(
                        manifestUri = target.uri,
                        mimeType = target.mimeType,
                        contentId = contentId,
                        videoHeightPx = videoHeightPx,
                        videoWidthPx = videoWidthPx,
                        qualityHint = qualityHint,
                        title = title,
                        callback = callback,
                    )
                }
            }
        } catch (error: Exception) {
            mainHandler.post { callback(Result.failure(error)) }
        }
    }

    fun listDownloadQualitiesFromUrl(
        url: String,
        apiKey: String?,
        callback: (Result<List<Map<String, Any?>>>) -> Unit,
    ) {
        initialize()
        try {
            when (val target = KinescopeDownloadUrlResolver.resolve(url)) {
                is KinescopeDownloadUrlResolver.Target.VideoId -> {
                    listDownloadQualities(target.videoId, apiKey, callback)
                }
                is KinescopeDownloadUrlResolver.Target.Manifest -> {
                    DownloadVideoOffline.listDownloadQualities(
                        context = context,
                        manifestUri = target.uri,
                        mimeType = target.mimeType,
                    ) { result ->
                        mainHandler.post {
                            result
                                .onSuccess { qualities ->
                                    if (qualities.isEmpty()) {
                                        callback(Result.failure(IllegalStateException("No downloadable qualities")))
                                    } else {
                                        callback(Result.success(qualities.map { it.toMap() }))
                                    }
                                }
                                .onFailure { callback(Result.failure(it)) }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            mainHandler.post { callback(Result.failure(error)) }
        }
    }

    private fun downloadManifest(
        manifestUri: Uri,
        mimeType: String,
        contentId: String?,
        videoHeightPx: Int?,
        videoWidthPx: Int?,
        qualityHint: String?,
        title: String?,
        callback: (Result<Map<String, Any?>>) -> Unit,
    ) {
        fun start(height: Int, width: Int, hint: String?) {
            val resolvedContentId = contentId
                ?: KinescopeOfflineIds.stableContentId(manifestUri.toString(), height)
            val existing = DownloadVideoOffline.getDownloadById(context, resolvedContentId)
            if (existing?.state == Download.STATE_COMPLETED) {
                mainHandler.post { callback(Result.success(existing.toMap())) }
                return
            }
            val metadata = KinescopeDrmDownloadHelper.buildManifestMetadata(
                manifestUri = manifestUri.toString(),
                contentId = resolvedContentId,
                title = title,
                qualityHeight = height,
                qualityLabel = hint,
            )
            DownloadVideoOffline.startDownloadWithQuality(
                context = context,
                contentId = resolvedContentId,
                manifestUri = manifestUri,
                videoHeightPx = height,
                videoWidthPx = width,
                mimeType = mimeType,
                data = metadata,
                qualityHint = hint,
                onError = { error ->
                    mainHandler.post { callback(Result.failure(error)) }
                },
                onStarted = {
                    emitDownloadsChanged()
                    val download = DownloadVideoOffline.getDownloadById(context, resolvedContentId)
                    mainHandler.post {
                        callback(
                            Result.success(
                                download?.toMap() ?: mapOf(
                                    "contentId" to resolvedContentId,
                                    "title" to title,
                                    "uri" to manifestUri.toString(),
                                    "mimeType" to mimeType,
                                    "state" to "queued",
                                    "percent" to 0,
                                    "bytesDownloaded" to 0L,
                                    "qualityHeight" to height,
                                    "qualityLabel" to hint,
                                ),
                            ),
                        )
                    }
                },
            )
        }

        if (videoHeightPx != null && videoHeightPx > 0) {
            start(videoHeightPx, videoWidthPx ?: C.LENGTH_UNSET, qualityHint)
            return
        }

        DownloadVideoOffline.listDownloadQualities(
            context = context,
            manifestUri = manifestUri,
            mimeType = mimeType,
        ) { result ->
            result
                .onSuccess { qualities ->
                    val chosen = qualities.maxByOrNull { it.height }
                    if (chosen == null) {
                        mainHandler.post {
                            callback(Result.failure(IllegalStateException("No downloadable qualities")))
                        }
                        return@onSuccess
                    }
                    start(
                        chosen.height,
                        chosen.width.takeIf { it > 0 } ?: C.LENGTH_UNSET,
                        chosen.label ?: chosen.qualityName,
                    )
                }
                .onFailure { error ->
                    mainHandler.post { callback(Result.failure(error)) }
                }
        }
    }

    fun removeDownload(downloadId: String) {
        initialize()
        DownloadVideoOffline.removeDownload(context, downloadId)
        emitDownloadsChanged()
    }

    fun getAllDownloads(): List<Map<String, Any?>> {
        initialize()
        val downloadManager = DownloadVideoOffline.getDownloadManager(context)
        val cursor = downloadManager.downloadIndex.getDownloads()
        val downloads = mutableListOf<Map<String, Any?>>()
        while (cursor.moveToNext()) {
            downloads.add(cursor.download.toMap())
        }
        cursor.close()
        return downloads
    }

    fun getCompletedDownloads(): List<Map<String, Any?>> {
        initialize()
        return DownloadVideoOffline.getAllCompletedDownloads(context).map { it.toMap() }
    }

    fun getDownload(downloadId: String): Map<String, Any?>? {
        initialize()
        return DownloadVideoOffline.getDownloadById(context, downloadId)?.toMap()
    }

    fun dispose() {
        downloadListener?.let { DownloadVideoOffline.removeDownloadListener(it) }
        downloadListener = null
    }

    private fun ensureDownloadListener() {
        if (downloadListener != null) {
            return
        }

        downloadListener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                emitDownloadUpdate(download, finalException)
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                emitDownloadsChanged()
            }
        }
        DownloadVideoOffline.addDownloadListener(context, downloadListener!!)
    }

    private fun emitDownloadUpdate(download: Download, error: Exception?) {
        val (percent, bytes) = DownloadVideoOffline.getDownloadProgress(download)
        postEvent(
            mapOf(
                "type" to "downloadChanged",
                "download" to download.toMap(percent, bytes),
                "error" to error?.message,
            ),
        )
    }

    private fun emitDownloadsChanged() {
        postEvent(
            mapOf(
                "type" to "downloadsChanged",
                "downloads" to getAllDownloads(),
            ),
        )
    }

    private fun postEvent(event: Map<String, Any?>) {
        mainHandler.post { eventSink(event) }
    }

    private fun parseTitle(download: Download): String? {
        KinescopeDrmDownloadHelper.parseMetadata(download.request.data)?.title?.let { return it }
        val data = download.request.data ?: return null
        return try {
            val title = JSONObject(String(data, Charsets.UTF_8)).optString("title")
            title.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVideoId(download: Download): String? {
        return KinescopeDrmDownloadHelper.parseMetadata(download.request.data)?.videoId
    }

    private fun Download.toMap(
        percent: Int = DownloadVideoOffline.getDownloadProgress(this).first,
        bytesDownloaded: Long = DownloadVideoOffline.getDownloadProgress(this).second,
    ): Map<String, Any?> {
        val metadata = KinescopeDrmDownloadHelper.parseMetadata(request.data)
        return mapOf(
            "contentId" to request.id,
            "videoId" to parseVideoId(this),
            "title" to parseTitle(this),
            "state" to stateToString(state),
            "percent" to percent,
            "bytesDownloaded" to bytesDownloaded,
            "contentLength" to contentLength,
            "mimeType" to request.mimeType,
            "uri" to request.uri.toString(),
            "qualityHeight" to metadata?.qualityHeight,
            "qualityLabel" to metadata?.qualityLabel,
        )
    }

    private fun OfflineDownloadQualityHelper.QualityOption.toMap(): Map<String, Any?> = mapOf(
        "height" to height,
        "width" to width.takeIf { it > 0 && it != C.LENGTH_UNSET },
        "bitrate" to bitrate.takeIf { it > 0 && it != C.RATE_UNSET_INT },
        "label" to label,
        "qualityName" to qualityName,
    )

    private fun stateToString(state: Int): String = when (state) {
        Download.STATE_QUEUED -> "queued"
        Download.STATE_STOPPED -> "stopped"
        Download.STATE_DOWNLOADING -> "downloading"
        Download.STATE_COMPLETED -> "completed"
        Download.STATE_FAILED -> "failed"
        Download.STATE_REMOVING -> "removing"
        Download.STATE_RESTARTING -> "restarting"
        else -> "unknown"
    }
}
