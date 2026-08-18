package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Moves playback to an Activity-level TextureView overlay before system PiP.
 *
 * FlutterView is intentionally left visible: hiding it disposes hybrid PlatformViews
 * on some OEMs. The overlay sits above Flutter with a black background.
 */
@OptIn(UnstableApi::class)
class KinescopePipHostController(
    private val activityProvider: () -> Activity?,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var inlineView: KinescopePlayerView? = null
    private var inlineContainer: View? = null
    private var player: KinescopeVideoPlayer? = null
    private var overlayContainer: FrameLayout? = null
    private var hostView: KinescopePlayerView? = null
    private var isHostedForPip = false
    private var pipReturnView: KinescopePlayerView? = null
    private var waitingForInlineReattach = false
    private var abandonOrphanedRunnable: Runnable? = null

    var onRestoreFullscreenAfterPip: (() -> Unit)? = null
    var onReturnedToInlineAfterPip: (() -> Unit)? = null
    /**
     * Invoked when PiP ends and no inline PlatformView reattaches (e.g. offline view was
     * disposed while still in PiP). Owners that uniquely hold [KinescopeVideoPlayer] must
     * release it here to avoid audio-without-surface and ExoPlayer leaks.
     */
    var onAbandonedWithoutInline: (() -> Unit)? = null

    fun isHostedForPip(): Boolean = isHostedForPip

    fun attach(inlineView: KinescopePlayerView, player: KinescopeVideoPlayer, container: View? = null) {
        this.inlineView = inlineView
        this.inlineContainer = container
        this.player = player
        if (waitingForInlineReattach && !isHostedForPip) {
            completePendingInlineReturn()
        } else if (isHostedForPip) {
            // New PlatformView while still in PiP — keep host as source of truth.
            rebindActivePlayback()
        }
    }

    fun detach() {
        cancelAbandonOrphanedPlayback()
        val inPip = isActivityInPictureInPictureMode()
        if (isHostedForPip && inPip) {
            releaseInlineOnly()
            return
        }
        if (isHostedForPip) {
            moveBackToInline()
        }
        removeOverlay()
        inlineView = null
        inlineContainer = null
        player = null
        waitingForInlineReattach = false
        onAbandonedWithoutInline = null
        KinescopePipRegistry.isPictureInPictureSessionActive = false
    }

    /** Drops the inline PlatformView reference while PiP overlay keeps playback. */
    fun releaseInlineOnly() {
        inlineView = null
        inlineContainer = null
        pipReturnView = null
        rebindActivePlayback()
    }

    fun rebindActivePlayback() {
        val videoPlayer = player ?: return
        val host = hostView ?: return
        if (!isHostedForPip) {
            return
        }
        host.visibility = View.VISIBLE
        overlayContainer?.isVisible = true
        bringOverlayToFront()
        KinescopeVideoSurfaceHelper.restoreVideoSurface(host)
        host.setPlayer(videoPlayer)
        KinescopeVideoSurfaceHelper.attachPlayer(host, videoPlayer)
        KinescopeVideoSurfaceHelper.rebindAfterLayout(host, videoPlayer)
    }

    private fun isActivityInPictureInPictureMode(): Boolean {
        val activity = activityProvider() ?: return false
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            activity.isInPictureInPictureMode
    }

    fun hideImmediately() {
        overlayContainer?.isVisible = false
        hostView?.visibility = View.GONE
        inlineView?.visibility = View.GONE
        inlineContainer?.visibility = View.GONE
    }

    fun inlinePlayerViewOrNull(): KinescopePlayerView? = inlineView ?: hostView

    fun inlinePlayerView(): KinescopePlayerView =
        inlinePlayerViewOrNull() ?: error("PiP host has no player view")

    fun activeViewOrNull(): KinescopePlayerView? {
        return if (isHostedForPip) {
            hostView ?: inlineView
        } else {
            inlineView ?: hostView
        }
    }

    fun activeView(): KinescopePlayerView =
        activeViewOrNull() ?: error("PiP host has no player view")

    fun additionalViews(): List<KinescopePlayerView> {
        val inline = inlineView ?: return emptyList()
        val host = hostView
        return if (host != null && host !== inline) {
            listOf(inline)
        } else {
            emptyList()
        }
    }

    fun prepareForEnter(playbackSource: KinescopePlayerView? = null) {
        val activity = activityProvider() ?: return
        KinescopeSoftKeyboard.dismiss(activity)

        val inline = inlineView ?: return
        val videoPlayer = player ?: return
        val source = playbackSource ?: inline
        pipReturnView = source
        waitingForInlineReattach = false

        if (isHostedForPip) {
            hostView?.visibility = View.VISIBLE
            bringOverlayToFront()
            KinescopeVideoSurfaceHelper.rebindAfterLayout(hostView ?: source, videoPlayer)
            return
        }

        // Fresh TextureView each PiP session — reused views intermittently stay black.
        removeOverlay()
        ensureOverlay(activity)
        val overlay = overlayContainer ?: return
        val host = hostView ?: return

        host.visibility = View.VISIBLE
        overlay.isVisible = true
        bringOverlayToFront()

        KinescopeVideoSurfaceHelper.restoreVideoSurface(host)
        KinescopePlayerView.switchTargetView(source, host, videoPlayer)
        hideInlineForPip()
        host.applyTemplateOptions()
        host.refreshPlayerChrome()
        KinescopePlayerViewChrome.scheduleStrip(host)
        isHostedForPip = true
        KinescopePipRegistry.isPictureInPictureSessionActive = true
        bringOverlayToFront()
        host.setPlayer(videoPlayer)
        KinescopeVideoSurfaceHelper.attachPlayer(host, videoPlayer)
        KinescopeVideoSurfaceHelper.rebindAfterLayout(host, videoPlayer)
        host.post {
            bringOverlayToFront()
            KinescopeVideoSurfaceHelper.attachPlayer(host, videoPlayer)
        }
    }

    private fun hideInlineForPip() {
        // Only the inline PlatformView — do not hide FlutterView (causes PlatformView dispose).
        inlineView?.visibility = View.INVISIBLE
        inlineContainer?.visibility = View.INVISIBLE
    }

    private fun showInlineAfterPip() {
        inlineView?.let { KinescopeVideoSurfaceHelper.restoreVideoSurface(it) }
        inlineView?.visibility = View.VISIBLE
        inlineContainer?.visibility = View.VISIBLE
    }

    private fun hidePipHost() {
        hostView?.visibility = View.GONE
        overlayContainer?.isVisible = false
    }

    fun bringOverlayToFront() {
        val overlay = overlayContainer ?: return
        overlay.isVisible = true
        overlay.bringToFront()
        (overlay.parent as? ViewGroup)?.bringChildToFront(overlay)
        overlay.translationZ = 10001f
        overlay.elevation = 10001f
    }

    fun prepareForExit() {
        moveBackToInline()
    }

    fun moveBackToInline() {
        val host = hostView ?: return
        val videoPlayer = player ?: return
        if (!isHostedForPip) {
            return
        }

        val restore = Runnable {
            activityProvider()?.let(KinescopeSoftKeyboard::dismiss)
            KinescopeVideoSurfaceHelper.cancelPendingRebinds(host)

            val inline = inlineView
            val preferred = pipReturnView
            val target = when {
                preferred != null && preferred !== host && preferred.parent != null -> preferred
                inline != null && inline.parent != null -> inline
                else -> null
            }

            if (target == null) {
                // Inline PlatformView not ready — pause (never play without a surface), wait
                // briefly for attach(), then abandon to avoid eternal audio / ExoPlayer leaks.
                waitingForInlineReattach = true
                isHostedForPip = false
                KinescopePipRegistry.isPictureInPictureSessionActive = false
                hidePipHost()
                try {
                    videoPlayer.pause()
                } catch (_: Exception) {
                }
                onReturnedToInlineAfterPip?.invoke()
                scheduleAbandonOrphanedPlayback()
                return@Runnable
            }

            finishReturnToTarget(host, target, videoPlayer, inline)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            restore.run()
        } else {
            mainHandler.post(restore)
        }
    }

    private fun completePendingInlineReturn() {
        cancelAbandonOrphanedPlayback()
        val host = hostView ?: return
        val videoPlayer = player ?: return
        val inline = inlineView ?: return
        waitingForInlineReattach = false
        finishReturnToTarget(host, inline, videoPlayer, inline)
    }

    private fun scheduleAbandonOrphanedPlayback() {
        cancelAbandonOrphanedPlayback()
        val runnable = Runnable { abandonOrphanedPlayback() }
        abandonOrphanedRunnable = runnable
        mainHandler.postDelayed(runnable, ORPHANED_INLINE_REATTACH_TIMEOUT_MS)
    }

    private fun cancelAbandonOrphanedPlayback() {
        abandonOrphanedRunnable?.let { mainHandler.removeCallbacks(it) }
        abandonOrphanedRunnable = null
    }

    private fun abandonOrphanedPlayback() {
        abandonOrphanedRunnable = null
        if (!waitingForInlineReattach) {
            return
        }
        waitingForInlineReattach = false
        val videoPlayer = player
        try {
            videoPlayer?.pause()
            videoPlayer?.stop()
        } catch (_: Exception) {
        }
        hostView?.let { host ->
            videoPlayer?.let { KinescopeVideoSurfaceHelper.unbindView(host, it) }
        }
        removeOverlay()
        inlineView = null
        inlineContainer = null
        pipReturnView = null
        player = null
        isHostedForPip = false
        KinescopePipRegistry.isPictureInPictureSessionActive = false
        val cleanup = onAbandonedWithoutInline
        onAbandonedWithoutInline = null
        cleanup?.invoke()
    }

    companion object {
        private const val ORPHANED_INLINE_REATTACH_TIMEOUT_MS = 2_000L
    }

    private fun finishReturnToTarget(
        host: KinescopePlayerView,
        target: KinescopePlayerView,
        videoPlayer: KinescopeVideoPlayer,
        inline: KinescopePlayerView?,
    ) {
        cancelAbandonOrphanedPlayback()
        KinescopePlayerView.switchTargetView(host, target, videoPlayer)
        host.prepareForPictureInPicture(false)
        hidePipHost()
        showInlineAfterPip()
        if (target === inline) {
            val container = inlineContainer as? ViewGroup
            if (container != null) {
                KinescopeInlineInteractivity.restoreAfterPip(container, inline, videoPlayer)
            } else {
                KinescopeVideoSurfaceHelper.restoreVideoSurface(inline)
                KinescopeVideoSurfaceHelper.rebindAfterLayout(inline, videoPlayer)
            }
            onReturnedToInlineAfterPip?.invoke()
        } else {
            target.prepareForPictureInPicture(false)
            target.refreshPlayerChromeAfterPictureInPictureExit()
            target.applyTemplateOptions()
            KinescopeVideoSurfaceHelper.rebindAfterLayout(target, videoPlayer)
            KinescopePlayerViewChrome.scheduleStrip(target)
            onRestoreFullscreenAfterPip?.invoke()
        }
        isHostedForPip = false
        KinescopePipRegistry.isPictureInPictureSessionActive = false
        pipReturnView = null
        waitingForInlineReattach = false
        // Destroy overlay so the next PiP enter gets a fresh TextureView.
        mainHandler.post { removeOverlay() }
    }

    private fun ensureOverlay(activity: Activity) {
        if (hostView != null) {
            return
        }

        val pipHostView = KinescopeFlutterPlayerViewFactory.createPipOverlay(activity).apply {
            applyTemplateOptions()
        }

        val container = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            clipChildren = false
            clipToPadding = false
            isVisible = false
            elevation = 10001f
            translationZ = 10001f
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(
                pipHostView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        activity.addContentView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        overlayContainer = container
        hostView = pipHostView
        KinescopePlayerViewChrome.prepare(pipHostView)
    }

    private fun removeOverlay() {
        hostView?.let { KinescopeVideoSurfaceHelper.cancelPendingRebinds(it) }
        overlayContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
        }
        overlayContainer = null
        hostView = null
    }
}
