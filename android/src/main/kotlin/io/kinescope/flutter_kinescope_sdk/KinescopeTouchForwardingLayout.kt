package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Forwards touch events to native children and prevents Flutter parents from
 * intercepting gestures meant for the embedded player controls.
 */
internal class KinescopeTouchForwardingLayout(context: Context) : FrameLayout(context) {
    init {
        isClickable = false
        isFocusable = false
        clipChildren = true
        clipToPadding = true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = false
}
