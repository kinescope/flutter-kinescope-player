package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager

internal object KinescopeSoftKeyboard {
    fun dismiss(activity: Activity) {
        val focused = activity.currentFocus
        val token = focused?.windowToken ?: activity.window?.decorView?.windowToken
        if (token != null) {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(token, 0)
        }
        focused?.clearFocus()
        KinescopePlatformViewFocus.releaseAllInputFocus()
    }
}
