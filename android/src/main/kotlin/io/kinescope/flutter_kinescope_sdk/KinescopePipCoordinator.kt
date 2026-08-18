package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.R
import io.kinescope.sdk.player.KinescopePictureInPicture
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

/** Fallback PiP coordinator when [androidx.appcompat.app.AppCompatActivity] is unavailable. */
@OptIn(UnstableApi::class)
class KinescopePipCoordinator(
    private val activity: Activity,
    private val lifecycle: Lifecycle,
    private val pipHostController: KinescopePipHostController,
    private val inlineView: () -> KinescopePlayerView,
    private val player: () -> KinescopeVideoPlayer,
    private val additionalPlayerViews: () -> List<KinescopePlayerView> = { emptyList() },
    private val onPrepareEnteringPip: () -> Unit = {},
    private val playbackSourceProvider: () -> KinescopePlayerView? = { null },
) {
    private var receiverRegistered = false
    private var pipEntryPending = false
    private var pipEntryRecoveryRunnable: Runnable? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KinescopePictureInPicture.ACTION_PLAY_PAUSE) {
                togglePlayback()
            }
        }
    }

    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity.isInPictureInPictureMode
            ) {
                KinescopePictureInPicture.updateActions(activity, player().exoPlayer)
            }
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            detach()
        }
    }

    fun attach() {
        refreshCallbacks()
        player().bindLifecycle(
            lifecycle = lifecycle,
            isPipActive = {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode
            },
        )
        registerPipReceiver()
        lifecycle.addObserver(lifecycleObserver)
        player().exoPlayer?.addListener(playbackListener)
    }

    fun detach() {
        cancelPictureInPictureEntryRecovery()
        pipEntryPending = false
        unregisterPipReceiver()
        lifecycle.removeObserver(lifecycleObserver)
        runCatching { player().exoPlayer?.removeListener(playbackListener) }
        // After orphaned PiP exit, overlay/inline are already cleared — do not throw.
        pipHostController.activeViewOrNull()?.onPictureInPictureButtonCallback = null
        runCatching {
            additionalPlayerViews().forEach { it.onPictureInPictureButtonCallback = null }
        }
    }

    fun onStop() {
        // onStop fires both when the user leaves the app with PiP floating and when PiP is
        // dismissed. Stopping on every onStop made expand-from-PiP return an idle empty player
        // on low-memory devices. Dismiss vs expand is handled by onExitedPictureInPictureMode.
    }

    fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        @Suppress("UNUSED_PARAMETER") newConfig: Configuration,
    ) {
        val videoPlayer = player()
        if (isInPictureInPictureMode) {
            cancelPictureInPictureEntryRecovery()
            pipEntryPending = false
            pipHostController.bringOverlayToFront()
            prepareAllPlayerViewsForPictureInPicture(true)
            videoPlayer.play()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                KinescopePictureInPicture.updateActions(activity, videoPlayer.exoPlayer)
            }
            pipHostController.bringOverlayToFront()
            // Soft attach only — avoid rebind storms from dual dispatch remnants.
            pipHostController.activeViewOrNull()?.let { active ->
                KinescopeVideoSurfaceHelper.attachPlayer(active, videoPlayer)
            }
            KinescopePipFlutterNotifier.notifyEnteringPip()
        } else {
            cancelPictureInPictureEntryRecovery()
            pipEntryPending = false
            if (pipHostController.isHostedForPip()) {
                pipHostController.prepareForExit()
                prepareAllPlayerViewsForPictureInPicture(false)
                refreshPlayerChromeAfterPictureInPictureExit()
                val anchor = pipHostController.activeViewOrNull()
                anchor?.post {
                    KinescopePipFlutterNotifier.notifyExitingPip()
                } ?: KinescopePipFlutterNotifier.notifyExitingPip()
                if (anchor != null) {
                    KinescopePictureInPicture.onExitedPictureInPictureMode(
                        activity = activity,
                        anchorView = anchor,
                        onDismissed = { videoPlayer.stop() },
                    )
                } else {
                    videoPlayer.stop()
                }
            }
            KinescopePipRegistry.resetDispatchState()
        }
    }

    fun refreshAdditionalViews() {
        refreshCallbacks()
    }

    fun refreshCallbacks() {
        val enter = ::enterPictureInPicture
        val view = pipHostController.activeViewOrNull() ?: return
        view.onPictureInPictureButtonCallback = enter
        additionalPlayerViews().forEach { additional ->
            additional.onPictureInPictureButtonCallback = enter
        }
        KinescopePipWiring.wirePipButtons(
            activity = activity,
            inlineView = view,
            additionalViews = additionalPlayerViews() + pipHostController.additionalViews(),
        )
    }

    private fun prepareAllPlayerViewsForPictureInPicture(preparing: Boolean) {
        val active = pipHostController.activeViewOrNull() ?: return
        (listOf(active) + additionalPlayerViews()).distinct().forEach { view ->
            view.prepareForPictureInPicture(preparing)
        }
    }

    private fun refreshPlayerChromeAfterPictureInPictureExit() {
        val inline = pipHostController.inlinePlayerViewOrNull() ?: return
        (listOf(inline) + additionalPlayerViews()).distinct().forEach { view ->
            view.refreshPlayerChromeAfterPictureInPictureExit()
        }
    }

    private fun refreshAllPlayerViewsChrome() {
        val active = pipHostController.activeViewOrNull() ?: return
        (listOf(active) + additionalPlayerViews()).distinct().forEach { view ->
            view.refreshPlayerChrome()
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val videoPlayer = player()
        if (videoPlayer.exoPlayer?.isPlaying != true) {
            videoPlayer.play()
        }
        val playbackSource = playbackSourceProvider()
            ?: pipHostController.inlinePlayerView()
        onPrepareEnteringPip()
        pipHostController.prepareForEnter(playbackSource)
        val view = pipHostController.activeView()
        pipEntryPending = true
        val anchorView = view.getPipAnchorView()
        view.doOnLayout {
            anchorView.doOnLayout {
                view.post {
                    val entered = KinescopePictureInPicture.enter(
                        activity = activity,
                        anchorView = anchorView,
                        aspectRatio = KinescopePictureInPicture.getAspectRatio(videoPlayer.exoPlayer),
                        exoPlayer = videoPlayer.exoPlayer,
                    )
                    if (!entered) {
                        cancelPictureInPictureEntryRecovery()
                        pipEntryPending = false
                        KinescopePipFlutterNotifier.notifyExitingPip()
                        pipHostController.prepareForExit()
                        prepareAllPlayerViewsForPictureInPicture(false)
                        refreshAllPlayerViewsChrome()
                        Toast.makeText(activity, R.string.player_pip_unavailable, Toast.LENGTH_SHORT).show()
                    } else {
                        schedulePictureInPictureEntryRecovery(view)
                    }
                }
            }
        }
    }

    private fun schedulePictureInPictureEntryRecovery(view: KinescopePlayerView) {
        cancelPictureInPictureEntryRecovery()
        val recovery = Runnable {
            pipEntryRecoveryRunnable = null
            if (!pipEntryPending) {
                return@Runnable
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) {
                pipEntryPending = false
                return@Runnable
            }
            pipEntryPending = false
            KinescopePipFlutterNotifier.notifyExitingPip()
            pipHostController.prepareForExit()
            prepareAllPlayerViewsForPictureInPicture(false)
            refreshAllPlayerViewsChrome()
        }
        pipEntryRecoveryRunnable = recovery
        view.postDelayed(recovery, PIP_ENTRY_RECOVERY_TIMEOUT_MS)
    }

    private fun cancelPictureInPictureEntryRecovery() {
        val runnable = pipEntryRecoveryRunnable ?: return
        pipEntryRecoveryRunnable = null
        pipHostController.activeViewOrNull()?.removeCallbacks(runnable)
        runCatching { inlineView().removeCallbacks(runnable) }
    }

    private fun togglePlayback() {
        val videoPlayer = player()
        val exoPlayer = videoPlayer.exoPlayer ?: return
        if (exoPlayer.isPlaying) {
            videoPlayer.pause()
        } else {
            videoPlayer.play()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            KinescopePictureInPicture.updateActions(activity, exoPlayer)
        }
    }

    private fun registerPipReceiver() {
        if (receiverRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val filter = IntentFilter(KinescopePictureInPicture.ACTION_PLAY_PAUSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(pipReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterPipReceiver() {
        if (!receiverRegistered) {
            return
        }
        activity.unregisterReceiver(pipReceiver)
        receiverRegistered = false
    }

    private companion object {
        private const val PIP_ENTRY_RECOVERY_TIMEOUT_MS = 2000L
    }
}
