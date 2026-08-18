package io.kinescope.flutter_kinescope_sdk

import android.os.Build
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@OptIn(UnstableApi::class)
internal class KinescopePipPlatformSupport(
    private val container: View,
    private val inlineView: KinescopePlayerView,
    private val pipHostController: KinescopePipHostController,
    private val playerProvider: () -> KinescopeVideoPlayer,
    private val activityProvider: () -> android.app.Activity?,
    private val lifecycleProvider: () -> Lifecycle?,
    private val additionalViewsProvider: () -> List<KinescopePlayerView>,
    private val onPrepareEnteringPip: () -> Unit = {},
    private val playbackSourceProvider: () -> KinescopePlayerView? = { null },
    private val onRestoreFullscreenAfterPip: () -> Unit = {},
    private val onReturnedToInlineAfterPip: () -> Unit = {},
) {
    private var pipBinding: KinescopePipBinding? = null

    fun scheduleAttach() {
        pipHostController.attach(inlineView, playerProvider(), container)
        pipHostController.onRestoreFullscreenAfterPip = onRestoreFullscreenAfterPip
        pipHostController.onReturnedToInlineAfterPip = onReturnedToInlineAfterPip
        KinescopePipAttachHelper.scheduleAttach(container) {
            ensurePipWired()
        }
    }

    fun ensurePipWired(): Boolean {
        val activity = activityProvider() ?: return false
        attachSessionIfReady()
        pipBinding?.refreshCallbacks()
        return pipBinding != null
    }

    fun onChromeRefreshed() {
        ensurePipWired()
    }

    fun onFullscreenViewReady() {
        pipBinding?.refreshAdditionalViews() ?: ensurePipWired()
    }

    fun onInlineChromeRefreshed() {
        ensurePipWired()
    }

    fun dispose() {
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            activityProvider()?.isInPictureInPictureMode == true
        if (inPip) {
            // Keep binding + overlay alive for mode-change / exit; only drop inline.
            // Owner must set pipHostController.onAbandonedWithoutInline if it uniquely
            // owns the player (offline PlatformView) so PiP exit without reattach releases it.
            pipHostController.releaseInlineOnly()
            return
        }
        tearDownFully()
    }

    /** Detach PiP session after orphaned exit (inline PlatformView will not return). */
    fun tearDownAfterOrphanedPip() {
        pipBinding?.detach()
        pipBinding = null
        pipHostController.onRestoreFullscreenAfterPip = null
        pipHostController.onReturnedToInlineAfterPip = null
        // Overlay / player refs already cleared by PipHostController.abandonOrphanedPlayback.
    }

    private fun tearDownFully() {
        pipBinding?.detach()
        pipBinding = null
        pipHostController.detach()
        pipHostController.onRestoreFullscreenAfterPip = null
        pipHostController.onReturnedToInlineAfterPip = null
        pipHostController.onAbandonedWithoutInline = null
    }

    private fun attachSessionIfReady() {
        if (pipBinding != null) {
            return
        }
        val activity = activityProvider() ?: return
        val lifecycle = KinescopePipWiring.resolveLifecycle(activity, lifecycleProvider) ?: return
        pipBinding = KinescopePipWiring.createBinding(
            activity = activity,
            lifecycle = lifecycle,
            pipHostController = pipHostController,
            inlineView = { pipHostController.inlinePlayerView() },
            player = playerProvider,
            additionalPlayerViews = additionalViewsProvider,
            onPrepareEnteringPip = onPrepareEnteringPip,
            playbackSourceProvider = playbackSourceProvider,
        ).also { it.attach() }
    }
}
