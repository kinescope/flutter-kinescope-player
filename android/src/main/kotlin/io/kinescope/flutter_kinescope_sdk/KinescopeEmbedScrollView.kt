package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.view.MotionEvent
import android.view.ViewParent
import android.widget.ScrollView

/**
 * ScrollView tuned for Flutter hybrid-composition platform views.
 * Walks the parent chain so vertical drags reach this view instead of being intercepted.
 */
internal class KinescopeEmbedScrollView(context: Context) : ScrollView(context) {
    init {
        isFillViewport = false
        isNestedScrollingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        disallowAncestorsIntercept(true)
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        disallowAncestorsIntercept(true)
        return super.onTouchEvent(event)
    }

    private fun disallowAncestorsIntercept(disallow: Boolean) {
        var parent: ViewParent? = parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            parent = parent.parent
        }
    }
}
