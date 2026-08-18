package io.kinescope.flutter_kinescope_sdk

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper

object KinescopePipRegistry {
    private val bindings = mutableSetOf<KinescopePipBinding>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True while playback is hosted on the PiP overlay (enter prepared → exit settled). */
    @Volatile
    var isPictureInPictureSessionActive: Boolean = false

    private var lastDispatchedInPip: Boolean? = null
    private var dispatchGeneration = 0

    fun register(binding: KinescopePipBinding) {
        bindings.add(binding)
    }

    fun unregister(binding: KinescopePipBinding) {
        bindings.remove(binding)
    }

    /**
     * Dedupes MainActivity override + plugin [OnPictureInPictureModeChangedListener]
     * which otherwise fire twice per transition and storm surface rebinds.
     */
    fun dispatchModeChanged(isInPictureInPictureMode: Boolean, configuration: Configuration) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatchModeChanged(isInPictureInPictureMode, configuration) }
            return
        }
        if (lastDispatchedInPip == isInPictureInPictureMode) {
            return
        }
        lastDispatchedInPip = isInPictureInPictureMode
        val generation = ++dispatchGeneration
        // Snapshot so a mid-loop unregister cannot ConcurrentModification.
        bindings.toList().forEach { binding ->
            if (generation != dispatchGeneration) {
                return@forEach
            }
            binding.onPictureInPictureModeChanged(isInPictureInPictureMode, configuration)
        }
    }

    fun dispatchOnStop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatchOnStop() }
            return
        }
        bindings.toList().forEach { it.onStop() }
    }

    fun resetDispatchState() {
        lastDispatchedInPip = null
    }
}
