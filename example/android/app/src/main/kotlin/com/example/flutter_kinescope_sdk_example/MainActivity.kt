package com.example.flutter_kinescope_sdk_example

import android.content.res.Configuration
import android.os.Build
import io.flutter.embedding.android.FlutterFragmentActivity

/**
 * PiP mode / onStop are handled by [io.kinescope.flutter_kinescope_sdk.FlutterKinescopeSdkPlugin]
 * via ComponentActivity listeners. Do not also call [io.kinescope.flutter_kinescope_sdk.KinescopePipRegistry]
 * here — dual dispatch storms surface rebinds and causes intermittent black PiP frames.
 */
class MainActivity : FlutterFragmentActivity() {
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }
    }
}
