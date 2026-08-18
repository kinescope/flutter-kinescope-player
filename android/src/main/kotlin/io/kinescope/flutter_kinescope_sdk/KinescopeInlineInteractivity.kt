package io.kinescope.flutter_kinescope_sdk

import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@OptIn(UnstableApi::class)
internal object KinescopeInlineInteractivity {
    private val rebindDelaysMs = longArrayOf(0L, 100L, 350L, 800L)

    fun restoreAfterPip(
        container: ViewGroup,
        inlineView: KinescopePlayerView,
        videoPlayer: KinescopeVideoPlayer,
    ) {
        val restore = Runnable {
            container.visibility = android.view.View.VISIBLE
            inlineView.visibility = android.view.View.VISIBLE

            inlineView.prepareForPictureInPicture(false)
            inlineView.refreshPlayerChromeAfterPictureInPictureExit()
            inlineView.applyTemplateOptions()
            KinescopeVideoSurfaceHelper.restoreVideoSurface(inlineView)
            KinescopeVideoSurfaceHelper.attachPlayer(inlineView, videoPlayer)
            resumeIfIdle(videoPlayer)
            KinescopePlayerViewChrome.scheduleStrip(inlineView)
            KinescopePlatformViewFocus.configureForFlutterTextInput(container, inlineView)
            KinescopeSettingsEmbedHelper.prepare(inlineView, container)

            inlineView.isClickable = true
            inlineView.isEnabled = true
            inlineView.invalidate()
            inlineView.requestLayout()
        }

        inlineView.post(restore)
        for (delayMs in rebindDelaysMs) {
            if (delayMs == 0L) {
                continue
            }
            inlineView.postDelayed({
                KinescopeVideoSurfaceHelper.restoreVideoSurface(inlineView)
                KinescopeVideoSurfaceHelper.attachPlayer(inlineView, videoPlayer)
                resumeIfIdle(videoPlayer)
                KinescopePlayerViewChrome.scheduleStrip(inlineView)
            }, delayMs)
        }
    }

    private fun resumeIfIdle(videoPlayer: KinescopeVideoPlayer) {
        val exoPlayer = videoPlayer.exoPlayer ?: return
        if (exoPlayer.playbackState == Player.STATE_IDLE && videoPlayer.getVideo() != null) {
            videoPlayer.play()
        }
    }
}
