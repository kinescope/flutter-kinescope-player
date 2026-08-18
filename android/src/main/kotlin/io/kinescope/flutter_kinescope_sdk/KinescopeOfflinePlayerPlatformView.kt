package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@OptIn(UnstableApi::class)
class KinescopeOfflinePlayerViewFactory(
    private val activityProvider: () -> Activity?,
    private val lifecycleProvider: () -> Lifecycle?,
    private val onFullscreenChanged: (Boolean) -> Unit,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        @Suppress("UNCHECKED_CAST")
        val params = args as Map<String, Any?>
        val contentId = params["contentId"] as String
        @Suppress("UNCHECKED_CAST")
        val options = params["options"] as? Map<String, Any?>
        return KinescopeOfflinePlayerPlatformView(
            context = context,
            contentId = contentId,
            options = options,
            activityProvider = activityProvider,
            lifecycleProvider = lifecycleProvider,
            onFullscreenChanged = onFullscreenChanged,
        )
    }
}

@OptIn(UnstableApi::class)
class KinescopeOfflinePlayerPlatformView(
    private val context: Context,
    private val contentId: String,
    options: Map<String, Any?>?,
    private val activityProvider: () -> Activity?,
    private val lifecycleProvider: () -> Lifecycle?,
    onFullscreenChanged: (Boolean) -> Unit,
) : PlatformView {

    private val kinescopePlayer = KinescopeVideoPlayer(
        context.applicationContext,
        KinescopePlayerOptionsFactory.fromMap(options),
    ).also { player ->
        if (player.kinescopePlayerOptions.showSubtitlesButton) {
            player.setShowSubtitles(true)
        }
        val referer = player.kinescopePlayerOptions.referer?.trim()
        if (!referer.isNullOrEmpty()) {
            player.setReferer(referer)
        }
    }

    private val container = KinescopeTouchForwardingLayout(context).apply {
        setBackgroundColor(Color.BLACK)
    }
    private val playerView = KinescopeFlutterPlayerViewFactory.createInline(
        activityProvider() ?: context,
    )
    private val fullscreenController = KinescopeFullscreenController(
        activityProvider = activityProvider,
        onFullscreenChanged = { fullscreen ->
            sessionHandle?.setFullscreen(fullscreen)
            onFullscreenChanged(fullscreen)
        },
    )
    private val pipHostController = KinescopePipHostController(activityProvider = activityProvider)
    private var offlineMetadata: OfflineDownloadMetadata? = null
    private var sessionHandle: KinescopeOfflinePlayerSession.Handle? = null
    private val pipSupport = KinescopePipPlatformSupport(
        container = container,
        inlineView = playerView,
        pipHostController = pipHostController,
        playerProvider = { kinescopePlayer },
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
        playerView.setPlayer(kinescopePlayer)
        playerView.applyTemplateOptions()
        KinescopePlatformViewFocus.registerContainer(container)
        KinescopePlatformViewFocus.configureForFlutterTextInput(container, playerView)
        KinescopeSettingsEmbedHelper.prepare(playerView, container)
        KinescopePlayerViewChrome.prepare(playerView)
        fullscreenController.attach(playerView, kinescopePlayer)
        sessionHandle = KinescopeOfflinePlayerSession.register(
            contentId = contentId,
            onHide = ::hideImmediately,
            onExitFullscreen = { fullscreenController.requestExitFullscreen() },
        )
        fullscreenController.onFullscreenViewReady = {
            pipSupport.onFullscreenViewReady()
            reapplyOfflineQualityLabels()
        }
        fullscreenController.onInlineChromeRefreshed = {
            pipSupport.onInlineChromeRefreshed()
            reapplyOfflineQualityLabels()
        }
        fullscreenController.onAfterSwitchTargetView = {
            reapplyOfflineQualityLabels()
        }
        pipSupport.scheduleAttach()
        prepareOfflinePlayback()
    }

    private fun hideImmediately() {
        fullscreenController.hideImmediately()
        pipHostController.hideImmediately()
        kinescopePlayer.pause()
        KinescopeVideoSurfaceHelper.cancelPendingRebinds(playerView)
        KinescopeVideoSurfaceHelper.unbindView(playerView, kinescopePlayer)
        playerView.visibility = View.GONE
        container.visibility = View.GONE
        container.setBackgroundColor(Color.BLACK)
        container.invalidate()
    }

    private fun playbackViews(): List<KinescopePlayerView> {
        return buildList {
            add(playerView)
            fullscreenController.getFullscreenView()?.let { add(it) }
        }
    }

    private fun reapplyOfflineQualityLabels() {
        val metadata = offlineMetadata ?: return
        KinescopeOfflineQualityChrome.reapply(
            player = kinescopePlayer,
            metadata = metadata,
            views = playbackViews(),
        )
    }

    private fun prepareOfflinePlayback(attempt: Int = 0) {
        playerView.post {
            if (kinescopePlayer.exoPlayer == null) {
                if (attempt < 10) {
                    playerView.postDelayed({ prepareOfflinePlayback(attempt + 1) }, 50)
                } else {
                    Log.e(
                        "KinescopeOfflinePlayer",
                        "ExoPlayer is not available for contentId=$contentId",
                    )
                }
                return@post
            }

            try {
                offlineMetadata = KinescopeOfflinePlayback.prepare(
                    context = context.applicationContext,
                    contentId = contentId,
                    player = kinescopePlayer,
                    playerViews = { playbackViews() },
                )
                pipSupport.onChromeRefreshed()
            } catch (error: Exception) {
                Log.e(
                    "KinescopeOfflinePlayer",
                    "Failed to prepare offline playback for contentId=$contentId",
                    error,
                )
            }
        }
    }

    override fun getView(): View = container

    override fun dispose() {
        sessionHandle?.let { KinescopeOfflinePlayerSession.unregister(it) }
        sessionHandle = null
        KinescopePlatformViewFocus.unregisterContainer(container)
        val inPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            activityProvider()?.isInPictureInPictureMode == true
        pipSupport.dispose()
        if (!inPip) {
            fullscreenController.detach()
            KinescopeVideoSurfaceHelper.teardown(playerView, kinescopePlayer)
            kinescopePlayer.release()
        } else {
            // Leave shared ExoPlayer surface on the PiP host; release when PiP ends
            // without a new inline PlatformView (see onAbandonedWithoutInline).
            playerView.visibility = View.GONE
            container.visibility = View.GONE
            pipHostController.onAbandonedWithoutInline = {
                try {
                    pipSupport.tearDownAfterOrphanedPip()
                    fullscreenController.detach()
                } finally {
                    try {
                        kinescopePlayer.pause()
                        kinescopePlayer.stop()
                    } catch (_: Exception) {
                    }
                    kinescopePlayer.release()
                }
            }
            pipHostController.rebindActivePlayback()
        }
        container.visibility = View.GONE
        (container.parent as? ViewGroup)?.removeView(container)
    }
}
