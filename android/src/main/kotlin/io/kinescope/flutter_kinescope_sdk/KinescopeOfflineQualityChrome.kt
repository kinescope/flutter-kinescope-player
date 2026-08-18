package io.kinescope.flutter_kinescope_sdk

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.player.quality.digitsFromQualityName
import io.kinescope.sdk.player.quality.qualityDisplayHeightPx
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Offline settings quality labels from embed `quality_map` (kotlin-kinescope-player 0.1.4).
 *
 * Offline playback bypasses [KinescopeVideoPlayer.loadVideo], so the player view never
 * receives `quality_map` from metadata. Without this, settings show raw [Format.height]
 * (often the long edge for portrait, e.g. 854) instead of names like `480p`.
 */
@OptIn(UnstableApi::class)
object KinescopeOfflineQualityChrome {
    fun attach(
        player: KinescopeVideoPlayer,
        metadata: OfflineDownloadMetadata?,
        views: () -> List<KinescopePlayerView>,
    ) {
        val exoPlayer = player.exoPlayer ?: return
        apply(player, metadata, views(), trackHeight = null, trackWidth = 0)

        val qualityHint = qualityHeightHint(metadata)
        if (qualityHint > 0) {
            pinVariant(views(), qualityHint)
        }

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    pinFromPlayer(player, exoPlayer, metadata, views)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    pinFromPlayer(player, exoPlayer, metadata, views)
                }
            },
        )
    }

    /** Re-apply after [KinescopePlayerView.switchTargetView] (fullscreen). */
    fun reapply(
        player: KinescopeVideoPlayer,
        metadata: OfflineDownloadMetadata?,
        views: List<KinescopePlayerView>,
    ) {
        val exoPlayer = player.exoPlayer
        val format = exoPlayer?.videoFormat
        apply(
            player = player,
            metadata = metadata,
            views = views,
            trackHeight = format?.height,
            trackWidth = format?.width ?: 0,
        )
        pinFromPlayer(player, exoPlayer, metadata) { views }
    }

    private fun pinFromPlayer(
        player: KinescopeVideoPlayer,
        exoPlayer: ExoPlayer?,
        metadata: OfflineDownloadMetadata?,
        views: () -> List<KinescopePlayerView>,
    ) {
        if (exoPlayer == null) {
            return
        }
        val format = exoPlayer.videoFormat
        apply(
            player = player,
            metadata = metadata,
            views = views(),
            trackHeight = format?.height,
            trackWidth = format?.width ?: 0,
        )

        val selectedLabel = metadata?.qualityLabel?.trim()?.takeIf { it.isNotEmpty() }
        val labelDigits = digitsFromQualityName(selectedLabel)
        val shortSide = qualityDisplayHeightPx(format?.width ?: 0, format?.height ?: 0)
        val variantId = labelDigits
            ?: shortSide.takeIf { it > 0 }
            ?: format?.height?.takeIf { it > 0 }
            ?: metadata?.qualityHeight?.takeIf { it > 0 }
            ?: return
        pinVariant(views(), variantId)
    }

    private fun apply(
        player: KinescopeVideoPlayer,
        metadata: OfflineDownloadMetadata?,
        views: List<KinescopePlayerView>,
        trackHeight: Int?,
        trackWidth: Int,
    ) {
        val names = buildQualityNames(metadata, trackHeight, trackWidth)
        if (names.isEmpty()) {
            return
        }
        player.setQualityNamesByHeight(names)
        for (view in views) {
            view.setQualityNamesByHeight(names)
        }
    }

    private fun pinVariant(views: List<KinescopePlayerView>, variantId: Int) {
        for (view in views) {
            view.setVideoQualityVariant(variantId)
        }
    }

    private fun buildQualityNames(
        metadata: OfflineDownloadMetadata?,
        trackHeight: Int?,
        trackWidth: Int,
    ): Map<Int, String> {
        val names = linkedMapOf<Int, String>()
        metadata?.qualityMap?.forEach { entry ->
            val name = entry.name.trim()
            if (name.isEmpty()) {
                return@forEach
            }
            if (entry.height > 0) {
                names[entry.height] = name
            }
            digitsFromQualityName(name)?.let { digits ->
                names[digits] = name
            }
        }

        val selectedLabel = metadata?.qualityLabel?.trim()?.takeIf { it.isNotEmpty() }
        if (selectedLabel != null) {
            digitsFromQualityName(selectedLabel)?.let { digits ->
                names[digits] = selectedLabel
            }
            metadata?.qualityHeight?.takeIf { it > 0 }?.let { height ->
                names[height] = selectedLabel
            }
            if (trackHeight != null && trackHeight > 0) {
                names[trackHeight] = selectedLabel
                val shortSide = qualityDisplayHeightPx(trackWidth, trackHeight)
                if (shortSide > 0) {
                    names[shortSide] = selectedLabel
                }
            }
        }
        return names
    }

    private fun qualityHeightHint(metadata: OfflineDownloadMetadata?): Int {
        digitsFromQualityName(metadata?.qualityLabel)?.let { return it }
        return metadata?.qualityHeight?.takeIf { it > 0 } ?: 0
    }
}
