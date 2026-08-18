package io.kinescope.flutter_kinescope_sdk

import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * - Inline [PlatformView]: TextureView (required for hybrid composition).
 * - Fullscreen Activity overlay: SurfaceView (matches kotlin-kinescope-player stand).
 * - PiP Activity overlay: TextureView — SurfaceView intermittently draws black under Flutter
 *   / PiP composition while audio keeps playing.
 */
@OptIn(UnstableApi::class)
internal object KinescopeFlutterPlayerViewFactory {
    fun createInline(context: android.content.Context): KinescopePlayerView {
        return KinescopePlayerView(
            context,
            null,
            useTextureSurface = true,
        )
    }

    fun createOverlay(context: android.content.Context): KinescopePlayerView {
        return KinescopePlayerView(
            context,
            null,
            useTextureSurface = false,
        )
    }

    fun createPipOverlay(context: android.content.Context): KinescopePlayerView {
        return KinescopePlayerView(
            context,
            null,
            useTextureSurface = true,
        )
    }

    @Deprecated("Use createInline or createOverlay", ReplaceWith("createInline(context)"))
    fun create(context: android.content.Context): KinescopePlayerView = createInline(context)
}
