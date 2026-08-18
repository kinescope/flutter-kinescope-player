package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import io.kinescope.sdk.view.KinescopePlayerView
import java.lang.ref.WeakReference

/**
 * Hybrid-composition platform views can keep Android focus and block Flutter TextFields
 * from opening the soft keyboard. Release native focus when the user taps elsewhere.
 */
internal object KinescopePlatformViewFocus {
    private val containers = mutableListOf<WeakReference<View>>()
    private var installedActivity: Activity? = null
    private var previousWindowCallback: Window.Callback? = null

    fun registerContainer(container: View) {
        containers.add(WeakReference(container))
        pruneDeadReferences()
    }

    fun unregisterContainer(container: View) {
        containers.removeAll { it.get() == null || it.get() === container }
    }

    fun configureForFlutterTextInput(container: ViewGroup, playerView: KinescopePlayerView) {
        container.isFocusable = false
        container.isFocusableInTouchMode = false
        container.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        playerView.isFocusable = false
        playerView.isFocusableInTouchMode = false
        playerView.isClickable = true
        playerView.clearFocus()
    }

    fun install(activity: Activity) {
        if (installedActivity === activity) {
            return
        }
        uninstall()
        val window = activity.window ?: return
        val delegate = window.callback ?: return
        previousWindowCallback = delegate
        window.callback = FocusReleaseWindowCallback(delegate) { rawX, rawY ->
            releaseInputFocusOutside(rawX, rawY)
        }
        installedActivity = activity
    }

    fun uninstall() {
        val activity = installedActivity ?: return
        val window = activity.window
        val previous = previousWindowCallback
        if (window != null && previous != null) {
            window.callback = previous
        }
        installedActivity = null
        previousWindowCallback = null
    }

    fun releaseAllInputFocus() {
        containers.forEach { reference ->
            reference.get()?.let(::clearFocusInTree)
        }
    }

    private fun releaseInputFocusOutside(rawX: Float, rawY: Float) {
        pruneDeadReferences()
        val hitsPlatformView = containers.any { reference ->
            val view = reference.get() ?: return@any false
            if (view.visibility != View.VISIBLE) {
                return@any false
            }
            val bounds = Rect()
            if (!view.getGlobalVisibleRect(bounds)) {
                return@any false
            }
            bounds.contains(rawX.toInt(), rawY.toInt())
        }
        if (!hitsPlatformView) {
            releaseAllInputFocus()
        }
    }

    private fun clearFocusInTree(view: View) {
        view.clearFocus()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                clearFocusInTree(view.getChildAt(index))
            }
        }
    }

    private fun pruneDeadReferences() {
        containers.removeAll { it.get() == null }
    }

    private class FocusReleaseWindowCallback(
        private val delegate: Window.Callback,
        private val onTouchDownOutside: (Float, Float) -> Unit,
    ) : Window.Callback by delegate {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                onTouchDownOutside(event.rawX, event.rawY)
            }
            return delegate.dispatchTouchEvent(event)
        }
    }
}
