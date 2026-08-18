package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.player.KinescopePictureInPictureSession
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@OptIn(UnstableApi::class)
class KinescopePipBinding(
    private val activity: Activity,
    private val lifecycle: Lifecycle,
    private val pipHostController: KinescopePipHostController,
    private val inlineView: () -> KinescopePlayerView,
    private val player: () -> KinescopeVideoPlayer,
    private val additionalPlayerViews: () -> List<KinescopePlayerView>,
    private val onPrepareEnteringPip: () -> Unit = {},
    private val playbackSourceProvider: () -> KinescopePlayerView? = { null },
) {
    private var session: KinescopePictureInPictureSession? = null
    private var fallbackCoordinator: KinescopePipCoordinator? = null
    private var attached = false

    fun attach() {
        if (attached) {
            refreshCallbacks()
            return
        }
        attached = true

        val appCompatActivity = activity as? AppCompatActivity
        if (appCompatActivity != null) {
            session = KinescopePictureInPictureSession(
                activity = appCompatActivity,
                playerView = { pipHostController.activeView() },
                player = player,
                additionalPlayerViews = {
                    additionalPlayerViews() + pipHostController.additionalViews()
                },
            ).apply {
                onEnteringPip = {
                    val playbackSource = playbackSourceProvider()
                        ?: pipHostController.inlinePlayerView()
                    onPrepareEnteringPip()
                    pipHostController.prepareForEnter(playbackSource)
                }
                onExitingPip = {
                    pipHostController.prepareForExit()
                }
                attach()
            }
        } else {
            fallbackCoordinator = KinescopePipCoordinator(
                activity = activity,
                lifecycle = lifecycle,
                pipHostController = pipHostController,
                inlineView = { pipHostController.activeView() },
                player = player,
                additionalPlayerViews = {
                    additionalPlayerViews() + pipHostController.additionalViews()
                },
                onPrepareEnteringPip = onPrepareEnteringPip,
                playbackSourceProvider = playbackSourceProvider,
            ).also { it.attach() }
        }

        refreshCallbacks()
        KinescopePipRegistry.register(this)
    }

    fun detach() {
        if (!attached) {
            return
        }
        attached = false
        KinescopePipRegistry.unregister(this)
        session?.detach()
        session = null
        fallbackCoordinator?.detach()
        fallbackCoordinator = null
    }

    fun onStop() {
        session?.onStop()
        fallbackCoordinator?.onStop()
    }

    fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        // Session XOR fallback already handle enter/exit rebind + Flutter notify.
        // Do not double-call rebindActivePlayback here — that caused black frames.
        session?.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        fallbackCoordinator?.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            pipHostController.bringOverlayToFront()
        }
    }

    fun refreshAdditionalViews() {
        refreshCallbacks()
    }

    fun refreshCallbacks() {
        propagatePictureInPictureCallbacks()
        val inline = pipHostController.inlinePlayerViewOrNull() ?: return
        KinescopePipWiring.wirePipButtons(
            activity = activity,
            inlineView = inline,
            additionalViews = additionalPlayerViews() + pipHostController.additionalViews(),
        )
    }

    private fun propagatePictureInPictureCallbacks() {
        val inline = pipHostController.inlinePlayerViewOrNull() ?: return
        val enterCallback = inline.onPictureInPictureButtonCallback ?: return
        (additionalPlayerViews() + pipHostController.additionalViews())
            .distinct()
            .forEach { view ->
                if (view !== inline) {
                    view.onPictureInPictureButtonCallback = enterCallback
                }
            }
    }
}
