package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.offline.Download
import io.kinescope.sdk.download.DownloadVideoOffline
import io.kinescope.sdk.models.players.syncLegacyChromeFlags
import io.kinescope.sdk.player.KinescopePlayerOptions
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.view.KinescopePlayerView

@OptIn(UnstableApi::class)
object KinescopePlayerOptionsFactory {
    fun fromMap(args: Map<*, *>?): KinescopePlayerOptions {
        val showSubtitles = args?.get("showSubtitles") as? Boolean
            ?: args?.get("texttrack") as? Boolean
            ?: false

        return KinescopePlayerOptions().apply {
            autoplay = args?.get("autoplay") as? Boolean ?: false
            muted = args?.get("muted") as? Boolean ?: false
            loop = args?.get("loop") as? Boolean ?: false
            controls = args?.get("controls") as? Boolean ?: true
            playsinline = args?.get("playsinline") as? Boolean ?: true
            pictureInPicture = args?.get("pictureInPicture") as? Boolean ?: true
            showSubtitlesButton = showSubtitles
            // 0.1.5 chrome defaults (play/pause morph, seek, quality, scale, …)
            showPlayPauseButton = controls
            showSeekBar = controls
            showDuration = controls
            showOptionsButton = controls
            showFullscreenButton = controls
            showChaptersButton = controls
            showPlaybackSpeedInSettings = true
            showAudioOnlyQualityInSettings = true
            showAudioTracksInSettings = true
            videoScale = true
            showDefaultPoster = args?.get("showDefaultPoster") as? Boolean ?: true
            val token = (args?.get("drmAuthToken") as? String)?.trim()
            if (!token.isNullOrEmpty()) {
                drmAuthToken = token
            }
            val refererValue = (args?.get("referer") as? String)?.trim()
            if (!refererValue.isNullOrEmpty()) {
                referer = refererValue
            }
            syncLegacyChromeFlags()
        }
    }
}

@OptIn(UnstableApi::class)
object KinescopeOfflinePlayback {
    private const val TAG = "KinescopeOfflinePlayback"

    /**
     * Prepares offline Media3 source and returns download metadata used for
     * settings quality labels ([KinescopeOfflineQualityChrome]).
     */
    fun prepare(
        context: Context,
        contentId: String,
        player: KinescopeVideoPlayer,
        playerViews: () -> List<KinescopePlayerView> = { emptyList() },
    ): OfflineDownloadMetadata? {
        val appContext = context.applicationContext
        DownloadVideoOffline.initialize(appContext)

        val download = resolveDownload(appContext, contentId)
            ?: throw IllegalStateException("Download not found: $contentId")

        if (download.state != Download.STATE_COMPLETED) {
            throw IllegalStateException("Download is not completed yet")
        }

        val exoPlayer = player.exoPlayer
            ?: throw IllegalStateException("ExoPlayer is not available")

        val metadata = KinescopeDrmDownloadHelper.parseMetadata(download.request.data)
        val manifestUri = metadata?.manifestUri?.toUri() ?: download.request.uri
        val stableContentId = KinescopeOfflineIds.stableContentId(manifestUri.toString())
        val keySetId = resolveKeySetId(appContext, download, metadata, stableContentId)
        val hasDrm = keySetId != null
        val licenseUrl = if (hasDrm) {
            resolveLicenseUrl(metadata, manifestUri.toString())
        } else {
            null
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(DownloadVideoOffline.getDownloadCache(appContext))
            .setUpstreamDataSourceFactory(null)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())

        // Prefer request.toMediaItem() so streamKeys match the single-quality cache.
        val mediaItemBuilder = download.request.toMediaItem().buildUpon()

        if (hasDrm && keySetId != null && !licenseUrl.isNullOrBlank()) {
            mediaItemBuilder
                .setDrmUuid(C.WIDEVINE_UUID)
                .setDrmLicenseUri(licenseUrl)
                .setDrmMultiSession(true)
                .setDrmKeySetId(keySetId)
        }

        val mediaItem = mediaItemBuilder.build()
        val mediaSource = when (download.request.mimeType) {
            MimeTypes.APPLICATION_MPD ->
                DashMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem)
            else ->
                HlsMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem)
        }

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(
                        TAG,
                        "Offline playback error: code=${error.errorCodeName} message=${error.message}",
                        error,
                    )
                }
            },
        )

        KinescopeOfflineQualityChrome.attach(
            player = player,
            metadata = metadata,
            views = playerViews,
        )

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = player.kinescopePlayerOptions.autoplay
        return metadata
    }

    private fun resolveDownload(context: Context, contentId: String): Download? {
        DownloadVideoOffline.getDownloadById(context, contentId)?.let { return it }

        val cursor = DownloadVideoOffline.getDownloadManager(context).downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            val download = cursor.download
            val metadata = KinescopeDrmDownloadHelper.parseMetadata(download.request.data)
            if (
                download.request.id == contentId ||
                metadata?.videoId == contentId ||
                metadata?.contentId == contentId
            ) {
                cursor.close()
                return download
            }
        }
        cursor.close()
        return null
    }

    private fun resolveKeySetId(
        context: Context,
        download: Download,
        metadata: OfflineDownloadMetadata?,
        stableContentId: String,
    ): ByteArray? {
        download.request.keySetId?.let { return it }

        val drmConfigurator = DrmConfigurator(context.applicationContext)
        val candidateIds = linkedSetOf(
            stableContentId,
            download.request.id,
            metadata?.contentId,
            metadata?.videoId,
        )

        for (id in candidateIds) {
            if (id.isNullOrEmpty()) {
                continue
            }
            drmConfigurator.loadOfflineLicenseFromStorage(id)?.let { return it }
        }
        return null
    }

    private fun resolveLicenseUrl(
        metadata: OfflineDownloadMetadata?,
        manifestUri: String,
    ): String? {
        metadata?.licenseUrl?.takeIf { it.isNotBlank() }?.let { return it }

        val videoId = metadata?.videoId
            ?: KinescopeOfflineIds.videoIdFromManifest(manifestUri)
            ?: return null
        return KinescopeDrmDownloadHelper.widevineLicenseUrl(videoId)
    }
}
