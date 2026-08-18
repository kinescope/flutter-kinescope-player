package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Re-binds PiP button clicks after chrome refresh without replacing the SDK session callback.
 */
@OptIn(UnstableApi::class)
internal object KinescopePipWiring {
    private const val TAG = "KinescopePip"
    private const val SDK_PACKAGE = "io.kinescope.sdk"
    private const val PIP_BUTTON_ID = "kinescope_picture_in_picture"
    private const val OPTIONS_STRIP_ID = "kinescope_options_expandable_strip"

    fun resolveLifecycle(
        activity: Activity,
        lifecycleProvider: () -> Lifecycle?,
    ): Lifecycle? {
        lifecycleProvider()?.let { return it }
        (activity as? LifecycleOwner)?.lifecycle?.let { return it }
        return null
    }

    fun wirePipButtons(
        activity: Activity,
        inlineView: KinescopePlayerView,
        additionalViews: List<KinescopePlayerView> = emptyList(),
    ) {
        (listOf(inlineView) + additionalViews).distinct().forEach { view ->
            val enterPip = view.onPictureInPictureButtonCallback
            if (enterPip == null) {
                Log.w(TAG, "PiP callback not set on player view")
                return@forEach
            }
            view.post {
                bindPipButton(view, enterPip)
                KinescopePlayerViewChrome.scheduleStrip(view)
            }
        }
    }

    fun createBinding(
        activity: Activity,
        lifecycle: Lifecycle,
        pipHostController: KinescopePipHostController,
        inlineView: () -> KinescopePlayerView,
        player: () -> io.kinescope.sdk.player.KinescopeVideoPlayer,
        additionalPlayerViews: () -> List<KinescopePlayerView>,
        onPrepareEnteringPip: () -> Unit = {},
        playbackSourceProvider: () -> KinescopePlayerView? = { null },
    ): KinescopePipBinding {
        return KinescopePipBinding(
            activity = activity,
            lifecycle = lifecycle,
            pipHostController = pipHostController,
            inlineView = inlineView,
            player = player,
            additionalPlayerViews = additionalPlayerViews,
            onPrepareEnteringPip = onPrepareEnteringPip,
            playbackSourceProvider = playbackSourceProvider,
        )
    }

    private fun bindPipButton(playerView: KinescopePlayerView, onEnterPip: () -> Unit) {
        val buttonId = playerView.resources.getIdentifier(PIP_BUTTON_ID, "id", SDK_PACKAGE)
        if (buttonId == 0) {
            Log.w(TAG, "PiP button id not found")
            return
        }
        val button = playerView.findViewById<View>(buttonId) ?: run {
            Log.w(TAG, "PiP button view not found")
            return
        }
        button.isClickable = true
        button.isEnabled = true
        button.isFocusable = false
        unclipAncestors(button)
        button.setOnClickListener {
            Log.d(TAG, "PiP button clicked")
            KinescopePlayerViewChrome.clearControlButtonPressState(playerView)
            onEnterPip()
        }
        KinescopePlayerViewChrome.scheduleStrip(playerView)
    }

    private fun unclipAncestors(button: View) {
        val stripId = button.resources.getIdentifier(OPTIONS_STRIP_ID, "id", SDK_PACKAGE)
        var parent = button.parent
        while (parent is ViewGroup) {
            parent.clipChildren = false
            parent.clipToPadding = false
            if (parent.id == stripId) {
                break
            }
            parent = parent.parent
        }
    }
}
