package io.kinescope.flutter_kinescope_sdk

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel

internal object KinescopePipFlutterNotifier {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var methodChannel: MethodChannel? = null

    fun install(channel: MethodChannel) {
        methodChannel = channel
    }

    fun notifyEnteringPip() {
        mainHandler.post {
            methodChannel?.invokeMethod("onEnterPictureInPicture", null)
        }
    }

    fun notifyExitingPip() {
        mainHandler.post {
            methodChannel?.invokeMethod("onExitPictureInPicture", null)
        }
    }
}
