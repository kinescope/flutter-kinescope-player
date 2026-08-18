package io.kinescope.flutter_kinescope_sdk

import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@OptIn(UnstableApi::class)
internal object KinescopeVideoSurfaceHelper {
    private const val SDK_PACKAGE = "io.kinescope.sdk"
    private const val EXO_PLAYER_VIEW_ID = "view_exoplayer"

    private val rebindGenerations = ConcurrentHashMap<Int, AtomicInteger>()

    /**
     * Soft bind without clearing the shared ExoPlayer surface.
     * Never assigns `player = null` — that clears video output for every other view.
     */
    fun attachPlayer(playerView: KinescopePlayerView, videoPlayer: KinescopeVideoPlayer) {
        val exoPlayer = videoPlayer.exoPlayer ?: return
        val exoView = findExoPlayerView(playerView) ?: return
        restoreVideoSurfaceInTree(playerView)
        playerView.post {
            restoreVideoSurfaceInTree(playerView)
            if (exoView.player !== exoPlayer) {
                exoView.player = exoPlayer
            }
            exoView.invalidate()
            playerView.invalidate()
            playerView.requestLayout()
        }
    }

    fun rebind(playerView: KinescopePlayerView, videoPlayer: KinescopeVideoPlayer) {
        // Full clear is only safe when this view exclusively owns playback.
        if (KinescopePipRegistry.isPictureInPictureSessionActive) {
            attachPlayer(playerView, videoPlayer)
            return
        }
        val exoPlayer = videoPlayer.exoPlayer ?: return
        val exoView = findExoPlayerView(playerView) ?: return
        playerView.post {
            restoreVideoSurfaceInTree(playerView)
            exoView.player = null
            exoPlayer.clearVideoSurface()
            restoreVideoSurfaceInTree(playerView)
            exoView.player = exoPlayer
            exoView.invalidate()
            playerView.invalidate()
            playerView.requestLayout()
        }
    }

    fun rebindAfterLayout(
        playerView: KinescopePlayerView,
        videoPlayer: KinescopeVideoPlayer,
        delaysMs: LongArray = longArrayOf(0L, 64L, 250L, 600L),
    ) {
        val key = System.identityHashCode(playerView)
        val generation = rebindGenerations.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
        restoreVideoSurface(playerView)
        attachPlayer(playerView, videoPlayer)
        for (delayMs in delaysMs) {
            if (delayMs == 0L) {
                continue
            }
            playerView.postDelayed({
                if (rebindGenerations[key]?.get() != generation) {
                    return@postDelayed
                }
                restoreVideoSurface(playerView)
                attachPlayer(playerView, videoPlayer)
            }, delayMs)
        }
    }

    fun cancelPendingRebinds(playerView: KinescopePlayerView) {
        val key = System.identityHashCode(playerView)
        rebindGenerations.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
    }

    fun restoreVideoSurface(playerView: KinescopePlayerView) {
        restoreVideoSurfaceInTree(playerView)
        playerView.invalidate()
        playerView.requestLayout()
    }

    /**
     * Hides this view without calling [KinescopePlayerView.setPlayer](null).
     * Media3's setPlayer(null) clears the shared ExoPlayer video surface.
     */
    fun unbindView(playerView: KinescopePlayerView, @Suppress("UNUSED_PARAMETER") videoPlayer: KinescopeVideoPlayer? = null) {
        cancelPendingRebinds(playerView)
        hideAllSurfaces(playerView)
        playerView.visibility = View.GONE
        (playerView.parent as? View)?.let { parent ->
            parent.visibility = View.GONE
            parent.invalidate()
        }
        if (!KinescopePipRegistry.isPictureInPictureSessionActive) {
            playerView.setPlayer(null)
        }
        playerView.invalidate()
    }

    fun teardown(playerView: KinescopePlayerView, videoPlayer: KinescopeVideoPlayer? = null) {
        cancelPendingRebinds(playerView)
        hideAllSurfaces(playerView)
        playerView.visibility = View.GONE
        (playerView.parent as? View)?.let { parent ->
            parent.visibility = View.GONE
            parent.invalidate()
        }
        playerView.setPlayer(null)
        val exoPlayer = videoPlayer?.exoPlayer
        if (exoPlayer != null) {
            exoPlayer.pause()
            exoPlayer.clearVideoSurface()
            exoPlayer.stop()
        }
        playerView.invalidate()
    }

    private fun findExoPlayerView(playerView: KinescopePlayerView): PlayerView? {
        val exoViewId = playerView.resources.getIdentifier(EXO_PLAYER_VIEW_ID, "id", SDK_PACKAGE)
        if (exoViewId == 0) {
            return null
        }
        return playerView.findViewById(exoViewId)
    }

    private fun restoreVideoSurfaceInTree(view: View) {
        when (view) {
            is TextureView -> {
                view.visibility = View.VISIBLE
                view.alpha = 1f
            }
            is SurfaceView -> {
                view.visibility = View.VISIBLE
                view.alpha = 1f
            }
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    restoreVideoSurfaceInTree(view.getChildAt(index))
                }
            }
        }
    }

    private fun hideAllSurfaces(view: View) {
        when (view) {
            is TextureView -> {
                view.visibility = View.GONE
                view.alpha = 0f
            }
            is SurfaceView -> {
                view.visibility = View.GONE
                view.alpha = 0f
            }
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    hideAllSurfaces(view.getChildAt(index))
                }
            }
        }
    }
}
