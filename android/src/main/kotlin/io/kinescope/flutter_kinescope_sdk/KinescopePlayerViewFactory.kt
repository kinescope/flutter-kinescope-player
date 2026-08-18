package io.kinescope.flutter_kinescope_sdk



import android.content.Context

import android.view.View

import android.view.ViewGroup

import android.widget.FrameLayout

import androidx.lifecycle.Lifecycle

import androidx.media3.common.util.UnstableApi

import io.flutter.plugin.common.StandardMessageCodec

import io.flutter.plugin.platform.PlatformView

import io.flutter.plugin.platform.PlatformViewFactory

import io.kinescope.sdk.view.KinescopePlayerView



@OptIn(UnstableApi::class)

class KinescopePlayerViewFactory(

    private val registry: KinescopePlayerRegistry,

    private val activityProvider: () -> android.app.Activity?,

    private val lifecycleProvider: () -> Lifecycle?,

    private val onFullscreenChanged: (Boolean) -> Unit,

) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {



    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {

        @Suppress("UNCHECKED_CAST")

        val params = args as Map<String, Any?>

        val playerId = (params["playerId"] as Number).toLong()

        return KinescopePlayerPlatformView(

            context = context,

            playerId = playerId,

            registry = registry,

            activityProvider = activityProvider,

            lifecycleProvider = lifecycleProvider,

            onFullscreenChanged = onFullscreenChanged,

        )

    }

}



@OptIn(UnstableApi::class)

class KinescopePlayerPlatformView(

    context: Context,

    private val playerId: Long,

    private val registry: KinescopePlayerRegistry,

    private val activityProvider: () -> android.app.Activity?,

    private val lifecycleProvider: () -> Lifecycle?,

    onFullscreenChanged: (Boolean) -> Unit,

) : PlatformView {



    private val container = KinescopeTouchForwardingLayout(context)

    private val playerView = KinescopeFlutterPlayerViewFactory.createInline(
        activityProvider() ?: context,
    )

    private val fullscreenController = KinescopeFullscreenController(

        activityProvider = activityProvider,

        onFullscreenChanged = onFullscreenChanged,

    )

    private val pipHostController = KinescopePipHostController(activityProvider = activityProvider)

    private val pipSupport = KinescopePipPlatformSupport(

        container = container,

        inlineView = playerView,

        pipHostController = pipHostController,

        playerProvider = { registry.get(playerId) },

        activityProvider = activityProvider,

        lifecycleProvider = lifecycleProvider,

        additionalViewsProvider = {

            fullscreenController.getFullscreenView()?.let { listOf(it) } ?: emptyList()

        },

        onPrepareEnteringPip = {
            fullscreenController.dismissOverlayForPictureInPicture()
        },
        playbackSourceProvider = {
            fullscreenController.currentPlaybackView()
        },
        onRestoreFullscreenAfterPip = {
            fullscreenController.restoreAfterPictureInPicture()
        },
        onReturnedToInlineAfterPip = {
            fullscreenController.finishDeferredFullscreenExitAfterPip()
        },
    )



    init {

        container.addView(

            playerView,

            FrameLayout.LayoutParams(

                FrameLayout.LayoutParams.MATCH_PARENT,

                FrameLayout.LayoutParams.MATCH_PARENT,

            ),

        )

        registry.attachView(playerId, playerView)
        KinescopePlatformViewFocus.registerContainer(container)
        KinescopePlatformViewFocus.configureForFlutterTextInput(container, playerView)
        KinescopeSettingsEmbedHelper.prepare(playerView, container)
        KinescopePlayerViewChrome.prepare(playerView)
        registry.setChromeRefreshListener(playerId) {
            KinescopePlayerViewChrome.scheduleStrip(playerView)
            pipSupport.onChromeRefreshed()
        }

        fullscreenController.attach(playerView, registry.get(playerId))
        registry.setFullscreenExitHandler(playerId) {
            fullscreenController.requestExitFullscreen()
        }

        fullscreenController.onFullscreenViewReady = {

            pipSupport.onFullscreenViewReady()

        }

        fullscreenController.onInlineChromeRefreshed = {

            pipSupport.onInlineChromeRefreshed()

        }

        pipSupport.scheduleAttach()

        registry.setViewHideHandler(playerId) {
            fullscreenController.hideImmediately()
            pipHostController.hideImmediately()
            container.visibility = View.GONE
        }

    }



    override fun getView(): View = container



    override fun dispose() {
        registry.setViewHideHandler(playerId, null)
        registry.setFullscreenExitHandler(playerId, null)
        KinescopePlatformViewFocus.unregisterContainer(container)
        registry.setChromeRefreshListener(playerId, null)

        val inPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            activityProvider()?.isInPictureInPictureMode == true
        pipSupport.dispose()
        if (!inPip) {
            fullscreenController.detach()
            container.visibility = View.GONE
            registry.detachView(playerId)
        } else {
            // Do not unbind/setPlayer(null) — that clears the shared PiP surface.
            container.visibility = View.GONE
            registry.clearViewReference(playerId)
            pipHostController.rebindActivePlayback()
        }
        (container.parent as? ViewGroup)?.removeView(container)
    }

}

