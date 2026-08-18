package io.kinescope.flutter_kinescope_sdk

import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import io.kinescope.sdk.view.KinescopePlayPauseMorphView
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Softens Material ripples on chrome icon buttons without wiping the 0.1.5
 * centre play/pause morph control ([KinescopePlayPauseMorphView] circle + AVD).
 */
internal object KinescopePlayerViewChrome {
    private const val SDK_PACKAGE = "io.kinescope.sdk"

    /** Bottom-bar / strip icons only — not the centre morph play/pause. */
    private val rippleStripButtonIds = listOf(
        "kinescope_fullscreen",
        "kinescope_picture_in_picture",
        "kinescope_options",
        "kinescope_chapters",
        "kinescope_playlist",
        "kinescope_subtitles",
        "kinescope_options_dots",
        "kinescope_cast",
    )

    fun prepare(playerView: KinescopePlayerView) {
        playerView.post {
            stripControlButtonRipples(playerView)
            clearControlButtonPressState(playerView)
        }
    }

    fun scheduleStrip(playerView: KinescopePlayerView) {
        playerView.post {
            stripControlButtonRipples(playerView)
            clearControlButtonPressState(playerView)
        }
    }

    fun stripControlButtonRipples(playerView: KinescopePlayerView) {
        for (idName in rippleStripButtonIds) {
            findViewBySdkId(playerView, idName)?.let(::stripRipple)
        }
        stripRipplesInTree(playerView)
    }

    fun clearControlButtonPressState(playerView: KinescopePlayerView) {
        for (idName in rippleStripButtonIds) {
            findViewBySdkId(playerView, idName)?.let(::clearPressState)
        }
        findViewBySdkId(playerView, "kinescope_play_pause")?.let(::clearPressState)
        clearPressStateInTree(playerView)
    }

    private fun findViewBySdkId(root: View, idName: String): View? {
        val id = root.resources.getIdentifier(idName, "id", SDK_PACKAGE)
        return if (id != 0) root.findViewById(id) else null
    }

    private fun stripRipple(view: View) {
        if (view is KinescopePlayPauseMorphView) {
            // Keep [bg_play_pause_button]; clear only Material ripple overlays.
            if (view.foreground is RippleDrawable) {
                view.foreground = null
            }
            view.stateListAnimator = null
            return
        }
        view.background = null
        view.foreground = null
        view.stateListAnimator = null
    }

    private fun clearPressState(view: View) {
        view.cancelPendingInputEvents()
        view.isPressed = false
        view.isSelected = false
        view.isActivated = false
        view.clearFocus()
        view.jumpDrawablesToCurrentState()
        view.refreshDrawableState()
    }

    private fun stripRipplesInTree(root: View) {
        if (root is ImageButton && root !is KinescopePlayPauseMorphView) {
            stripRipple(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                stripRipplesInTree(root.getChildAt(index))
            }
        }
    }

    private fun clearPressStateInTree(root: View) {
        if (root is ImageButton) {
            clearPressState(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                clearPressStateInTree(root.getChildAt(index))
            }
        }
    }
}
