package io.kinescope.flutter_kinescope_sdk

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks every active offline PlatformView so Flutter pop / hide can target one session.
 * Hybrid-composition views leave a video frame if torn down only in [PlatformView.dispose].
 */
internal object KinescopeOfflinePlayerSession {
    private val sessions = CopyOnWriteArrayList<Handle>()

    class Handle internal constructor(
        val contentId: String,
        private val onHide: () -> Unit,
        private val onExitFullscreen: () -> Unit,
    ) {
        @Volatile
        var isFullscreen: Boolean = false
            private set

        fun setFullscreen(active: Boolean) {
            isFullscreen = active
        }

        fun hide() {
            onHide()
        }

        fun exitFullscreen() {
            onExitFullscreen()
        }
    }

    fun register(
        contentId: String,
        onHide: () -> Unit,
        onExitFullscreen: () -> Unit,
    ): Handle {
        val handle = Handle(contentId, onHide, onExitFullscreen)
        sessions.add(handle)
        return handle
    }

    fun unregister(handle: Handle) {
        sessions.remove(handle)
    }

    fun isFullscreenActive(): Boolean = sessions.any { it.isFullscreen }

    /**
     * Hides a single offline PlatformView identified by [contentId].
     * Never broadcasts to every session — that paused/hid sibling players.
     */
    fun hideView(contentId: String) {
        sessions.firstOrNull { it.contentId == contentId }?.hide()
    }

    /**
     * Exits fullscreen for [contentId] when provided; otherwise only sessions that are
     * currently fullscreen (safe Back when id is unknown).
     */
    fun exitFullscreen(contentId: String? = null) {
        if (contentId != null) {
            sessions.firstOrNull { it.contentId == contentId }?.exitFullscreen()
            return
        }
        sessions.toList().filter { it.isFullscreen }.forEach { it.exitFullscreen() }
    }
}
