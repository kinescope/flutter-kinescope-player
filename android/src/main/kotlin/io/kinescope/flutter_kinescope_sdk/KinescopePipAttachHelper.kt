package io.kinescope.flutter_kinescope_sdk

import android.view.View
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Retries PiP wiring until Flutter has attached an Activity and Lifecycle.
 */
internal object KinescopePipAttachHelper {
    private val pending = CopyOnWriteArrayList<() -> Boolean>()

    fun scheduleAttach(anchor: View, attach: () -> Boolean) {
        if (attach()) {
            return
        }
        pending.add(attach)
        retry(anchor, attach, attempt = 0)
    }

    fun retryPending() {
        val snapshot = pending.toList()
        snapshot.forEach { attach ->
            if (attach()) {
                pending.remove(attach)
            }
        }
    }

    private fun retry(anchor: View, attach: () -> Boolean, attempt: Int) {
        anchor.post {
            if (attach()) {
                pending.remove(attach)
                return@post
            }
            if (attempt < 40) {
                anchor.postDelayed({ retry(anchor, attach, attempt + 1) }, 50)
            }
        }
    }
}
