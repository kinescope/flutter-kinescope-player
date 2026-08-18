package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.player.KinescopeContentOrientationController
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Fullscreen player overlay on top of the Activity content view.
 *
 * Video moves to overlay via [KinescopePlayerView.switchTargetView].
 * Screen orientation follows content aspect via [KinescopeContentOrientationController]
 * (portrait videos stay portrait in fullscreen — kotlin-kinescope-player 0.1.4+).
 */
@OptIn(UnstableApi::class)
class KinescopeFullscreenController(
    private val activityProvider: () -> Activity?,
    private val onFullscreenChanged: (Boolean) -> Unit = {},
) {
    private var inlineView: KinescopePlayerView? = null
    private var overlayContainer: FrameLayout? = null
    private var fullscreenView: KinescopePlayerView? = null
    private var player: KinescopeVideoPlayer? = null
    private var isVideoFullscreen = false
    private var deferredFullscreenExitForPip = false
    private var savedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val mainHandler = Handler(Looper.getMainLooper())
    private var backCallback: OnBackPressedCallback? = null
    private var orientationController: KinescopeContentOrientationController? = null

    /** Invoked after [KinescopePlayerView.switchTargetView] in either direction. */
    var onAfterSwitchTargetView: (() -> Unit)? = null

    fun attach(
        inlineView: KinescopePlayerView,
        player: KinescopeVideoPlayer,
    ) {
        this.inlineView = inlineView
        this.player = player

        inlineView.setIsFullscreen(false)
        inlineView.onFullscreenButtonCallback = { onFullscreenButtonClicked(inlineView) }
        KinescopePlayerViewChrome.prepare(inlineView)
        ensureOrientationController()
    }

    fun getFullscreenView(): KinescopePlayerView? = fullscreenView

    fun isFullscreenActive(): Boolean = isVideoFullscreen

    fun currentPlaybackView(): KinescopePlayerView? {
        return if (isVideoFullscreen) {
            fullscreenView
        } else {
            inlineView
        }
    }

    /** Exits fullscreen when Back is pressed from Flutter (PopGuard) or native chrome. */
    fun requestExitFullscreen() {
        if (isVideoFullscreen) {
            exitFullscreen()
        }
    }

    /**
     * Hides fullscreen chrome before PiP without moving playback back to the inline PlatformView.
     * Playback is transferred to the PiP host in [KinescopePipHostController.prepareForEnter].
     *
     * Does not restore orientation or notify Flutter yet — that would unlock rotation mid-PiP
     * enter and recreate the Activity on some devices.
     */
    fun dismissOverlayForPictureInPicture() {
        if (!isVideoFullscreen) {
            return
        }

        isVideoFullscreen = false
        deferredFullscreenExitForPip = true
        setBackCallbackEnabled(false)
        // Keep orientation locked while entering PiP — unlocking here recreates the Activity
        // on landscape content and blacks out the surface handoff.
        overlayContainer?.isVisible = false
    }

    /** @deprecated Use [dismissOverlayForPictureInPicture] + PiP host handoff instead. */
    fun exitFullscreenForPictureInPicture() {
        dismissOverlayForPictureInPicture()
    }

    /**
     * Applies orientation / Flutter fullscreen-exit that was deferred when entering PiP
     * from fullscreen and returning to the inline player.
     */
    fun finishDeferredFullscreenExitAfterPip() {
        if (!deferredFullscreenExitForPip) {
            return
        }
        deferredFullscreenExitForPip = false
        activityProvider()?.let {
            it.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        orientationController?.setFullscreen(false)
        onFullscreenChanged(false)
    }

    /** Re-shows fullscreen chrome after PiP when playback returns to the fullscreen overlay. */
    fun restoreAfterPictureInPicture() {
        val activity = activityProvider() ?: return
        val fullscreen = fullscreenView ?: return
        val kinescopePlayer = player ?: return
        val overlay = overlayContainer ?: return

        deferredFullscreenExitForPip = false

        if (isVideoFullscreen) {
            return
        }

        isVideoFullscreen = true
        onFullscreenChanged(true)
        setBackCallbackEnabled(true)
        ensureOrientationController()
        orientationController?.setFullscreen(true)

        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        mainHandler.post {
            if (!isVideoFullscreen) {
                return@post
            }

            overlay.isVisible = true
            overlay.bringToFront()
            (overlay.parent as? ViewGroup)?.bringChildToFront(overlay)
            fullscreen.applyTemplateOptions()
            fullscreen.refreshPlayerChrome()
            KinescopePlayerViewChrome.stripControlButtonRipples(fullscreen)
            KinescopePlayerViewChrome.clearControlButtonPressState(fullscreen)
            KinescopeVideoSurfaceHelper.attachPlayer(fullscreen, kinescopePlayer)
            onInlineChromeRefreshed?.invoke()
            ViewCompat.requestApplyInsets(overlay)
            orientationController?.apply()
        }
    }

    fun detach() {
        if (isVideoFullscreen) {
            exitFullscreen()
        } else if (deferredFullscreenExitForPip) {
            finishDeferredFullscreenExitAfterPip()
        }
        orientationController?.detach()
        orientationController = null
        removeBackCallback()
        removeOverlay()
        inlineView?.onFullscreenButtonCallback = null
        inlineView = null
        player = null
    }

    fun hideImmediately() {
        isVideoFullscreen = false
        deferredFullscreenExitForPip = false
        setBackCallbackEnabled(false)
        orientationController?.setFullscreen(false)
        overlayContainer?.isVisible = false
        fullscreenView?.visibility = android.view.View.GONE
        inlineView?.visibility = android.view.View.GONE
        activityProvider()?.let { activity ->
            activity.requestedOrientation = savedOrientation
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun onFullscreenButtonClicked(sourceView: KinescopePlayerView) {
        KinescopePlayerViewChrome.clearControlButtonPressState(sourceView)
        mainHandler.post { toggleFullscreen() }
    }

    private fun ensureOverlay(activity: Activity) {
        if (fullscreenView != null) {
            return
        }

        val fullscreenPlayerView = KinescopeFlutterPlayerViewFactory.createOverlay(activity).apply {
            setIsFullscreen(true)
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
            elevation = 10000f
            translationZ = 10000f
            addView(
                fullscreenPlayerView,
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
        fullscreenView = fullscreenPlayerView
        fullscreenPlayerView.onFullscreenButtonCallback = {
            onFullscreenButtonClicked(fullscreenPlayerView)
        }
        KinescopePlayerViewChrome.prepare(fullscreenPlayerView)
        // Re-wire orientation with both inline + fullscreen views.
        orientationController?.detach()
        orientationController = null
        ensureOrientationController()
        onFullscreenViewReady?.invoke(fullscreenPlayerView)
    }

    var onFullscreenViewReady: ((KinescopePlayerView) -> Unit)? = null
    var onInlineChromeRefreshed: (() -> Unit)? = null

    private fun ensureOrientationController() {
        val activity = activityProvider() ?: return
        val inline = inlineView ?: return
        if (orientationController != null) {
            orientationController?.setFullscreen(isVideoFullscreen)
            return
        }
        orientationController = KinescopeContentOrientationController(
            activity = activity,
            playerViews = {
                listOfNotNull(inlineView, fullscreenView)
            },
        ).also {
            it.attach()
            it.setFullscreen(isVideoFullscreen)
        }
        // Capture baseline orientation once when first attached.
        if (savedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            savedOrientation = activity.requestedOrientation
        }
    }

    private fun toggleFullscreen() {
        if (isVideoFullscreen) {
            exitFullscreen()
        } else {
            enterFullscreen()
        }
    }

    private fun enterFullscreen() {
        val activity = activityProvider() ?: return
        val inline = inlineView ?: return
        val kinescopePlayer = player ?: return

        ensureOverlay(activity)
        val overlay = overlayContainer ?: return
        val fullscreen = fullscreenView ?: return

        isVideoFullscreen = true
        deferredFullscreenExitForPip = false
        onFullscreenChanged(true)
        setBackCallbackEnabled(true)

        savedOrientation = activity.requestedOrientation
        ensureOrientationController()
        orientationController?.setFullscreen(true)

        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        mainHandler.post {
            if (!isVideoFullscreen) {
                return@post
            }

            KinescopePlayerViewChrome.clearControlButtonPressState(inline)

            overlay.isVisible = true
            overlay.bringToFront()
            (overlay.parent as? ViewGroup)?.invalidate()

            KinescopePlayerView.switchTargetView(inline, fullscreen, kinescopePlayer)
            fullscreen.applyTemplateOptions()
            fullscreen.refreshPlayerChrome()
            KinescopePlayerViewChrome.stripControlButtonRipples(fullscreen)
            KinescopePlayerViewChrome.clearControlButtonPressState(fullscreen)
            onAfterSwitchTargetView?.invoke()
            onInlineChromeRefreshed?.invoke()
            ViewCompat.requestApplyInsets(overlay)
            orientationController?.apply()
        }
    }

    private fun exitFullscreen() {
        val activity = activityProvider()
        val inline = inlineView
        val fullscreen = fullscreenView
        val kinescopePlayer = player

        isVideoFullscreen = false
        deferredFullscreenExitForPip = false
        setBackCallbackEnabled(false)
        onFullscreenChanged(false)

        orientationController?.setFullscreen(false)
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        mainHandler.post {
            if (inline != null && fullscreen != null && kinescopePlayer != null) {
                KinescopePlayerViewChrome.clearControlButtonPressState(fullscreen)

                KinescopePlayerView.switchTargetView(fullscreen, inline, kinescopePlayer)
                inline.applyTemplateOptions()
                inline.refreshPlayerChrome()
                KinescopePlayerViewChrome.stripControlButtonRipples(inline)
                KinescopePlayerViewChrome.clearControlButtonPressState(inline)
                onAfterSwitchTargetView?.invoke()
                onInlineChromeRefreshed?.invoke()
                orientationController?.apply()
            }
            overlayContainer?.isVisible = false
        }
    }

    private fun setBackCallbackEnabled(enabled: Boolean) {
        val activity = activityProvider() as? ComponentActivity
        if (activity == null) {
            return
        }
        if (enabled) {
            val existing = backCallback
            if (existing == null) {
                val callback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        exitFullscreen()
                    }
                }
                backCallback = callback
                activity.onBackPressedDispatcher.addCallback(callback)
            } else {
                existing.isEnabled = true
            }
        } else {
            backCallback?.isEnabled = false
        }
    }

    private fun removeBackCallback() {
        backCallback?.remove()
        backCallback = null
    }

    private fun removeOverlay() {
        orientationController?.detach()
        orientationController = null
        overlayContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
        }
        overlayContainer = null
        fullscreenView = null
    }
}
