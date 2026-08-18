package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.flutter.plugin.common.EventChannel
import io.kinescope.sdk.player.KinescopePlayerOptions
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView
import java.util.concurrent.atomic.AtomicLong

@OptIn(UnstableApi::class)
class KinescopePlayerRegistry(
    private val eventSink: (Map<String, Any?>) -> Unit,
) {
    private val players = mutableMapOf<Long, PlayerEntry>()
    private val nextId = AtomicLong(1)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val chromeRefreshListeners = mutableMapOf<Long, () -> Unit>()
    private val viewHideHandlers = mutableMapOf<Long, () -> Unit>()
    private val fullscreenExitHandlers = mutableMapOf<Long, () -> Unit>()

    data class PlayerEntry(
        val player: KinescopeVideoPlayer,
        var playerView: KinescopePlayerView? = null,
        val listener: Player.Listener,
        val timeUpdateRunnable: Runnable,
        val texttrackPreference: Boolean?,
    )

    fun create(context: Context, args: Map<*, *>?): Long {
        val options = KinescopePlayerOptionsFactory.fromMap(args)
        val texttrackPreference = args?.get("texttrack") as? Boolean

        val id = nextId.getAndIncrement()
        val player = KinescopeVideoPlayer(context, options)
        if (options.showSubtitlesButton) {
            player.setShowSubtitles(true)
        }
        val referer = options.referer?.trim()
        if (!referer.isNullOrEmpty()) {
            player.setReferer(referer)
        }
        var entryRef: PlayerEntry? = null

        val timeUpdateRunnable = object : Runnable {
            override fun run() {
                val entry = entryRef ?: return
                val exoPlayer = entry.player.exoPlayer ?: return
                val durationMs = exoPlayer.duration
                if (durationMs <= 0) {
                    mainHandler.postDelayed(this, TIME_UPDATE_INTERVAL_MS)
                    return
                }
                val currentMs = exoPlayer.currentPosition
                val percent = ((currentMs.toDouble() / durationMs.toDouble()) * 100).toInt()
                emit(
                    id,
                    mapOf(
                        "type" to "timeUpdate",
                        "currentTime" to currentMs / 1000.0,
                        "percent" to percent,
                    ),
                )
                mainHandler.postDelayed(this, TIME_UPDATE_INTERVAL_MS)
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> emit(id, statusEvent("unknown"))
                    Player.STATE_BUFFERING -> emit(id, statusEvent("waiting"))
                    Player.STATE_READY -> {
                        if (player.exoPlayer?.isPlaying == true) {
                            emit(id, statusEvent("playing"))
                        } else {
                            emit(id, statusEvent("ready"))
                        }
                    }
                    Player.STATE_ENDED -> emit(id, statusEvent("ended"))
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    emit(id, statusEvent("playing"))
                    mainHandler.removeCallbacks(timeUpdateRunnable)
                    mainHandler.post(timeUpdateRunnable)
                } else {
                    val state = player.exoPlayer?.playbackState
                    if (state == Player.STATE_ENDED) {
                        emit(id, statusEvent("ended"))
                    } else if (state == Player.STATE_READY) {
                        emit(id, statusEvent("pause"))
                    }
                    mainHandler.removeCallbacks(timeUpdateRunnable)
                }
            }
        }

        val entry = PlayerEntry(
            player = player,
            listener = listener,
            timeUpdateRunnable = timeUpdateRunnable,
            texttrackPreference = texttrackPreference,
        )
        entryRef = entry
        players[id] = entry
        scheduleListenerAttach(id, player, listener)
        emit(id, statusEvent("init"))
        return id
    }

    /**
     * [KinescopeVideoPlayer] may expose [KinescopeVideoPlayer.exoPlayer] = null right after
     * construction. Retry until the engine exists so status / timeUpdate streams work.
     */
    private fun scheduleListenerAttach(
        playerId: Long,
        player: KinescopeVideoPlayer,
        listener: Player.Listener,
        attempt: Int = 0,
    ) {
        val exoPlayer = player.exoPlayer
        if (exoPlayer != null) {
            exoPlayer.addListener(listener)
            return
        }
        if (attempt >= LISTENER_ATTACH_MAX_ATTEMPTS) {
            return
        }
        mainHandler.postDelayed(
            {
                if (players[playerId]?.player !== player) {
                    return@postDelayed
                }
                scheduleListenerAttach(playerId, player, listener, attempt + 1)
            },
            LISTENER_ATTACH_RETRY_MS,
        )
    }

    fun setChromeRefreshListener(playerId: Long, listener: (() -> Unit)?) {
        if (listener == null) {
            chromeRefreshListeners.remove(playerId)
        } else {
            chromeRefreshListeners[playerId] = listener
        }
    }

    fun attachView(playerId: Long, playerView: KinescopePlayerView) {
        val entry = getEntry(playerId)
        entry.playerView = playerView
        playerView.visibility = android.view.View.VISIBLE
        if (KinescopePipRegistry.isPictureInPictureSessionActive) {
            // PiP overlay owns the shared surface — only remember the inline view.
            return
        }
        playerView.setPlayer(entry.player)
        playerView.applyTemplateOptions()
        if (entry.player.getVideo() != null) {
            KinescopeVideoSurfaceHelper.restoreVideoSurface(playerView)
            KinescopeVideoSurfaceHelper.attachPlayer(playerView, entry.player)
            playerView.refreshPlayerChrome()
            notifyChromeRefreshed(playerId)
        }
    }

    fun clearViewReference(playerId: Long) {
        val entry = players[playerId] ?: return
        entry.playerView = null
    }

    fun setViewHideHandler(playerId: Long, handler: (() -> Unit)?) {
        if (handler == null) {
            viewHideHandlers.remove(playerId)
        } else {
            viewHideHandlers[playerId] = handler
        }
    }

    fun setFullscreenExitHandler(playerId: Long, handler: (() -> Unit)?) {
        if (handler == null) {
            fullscreenExitHandlers.remove(playerId)
        } else {
            fullscreenExitHandlers[playerId] = handler
        }
    }

    fun exitFullscreen(playerId: Long) {
        fullscreenExitHandlers[playerId]?.invoke()
    }

    fun detachView(playerId: Long) {
        val entry = players[playerId] ?: return
        entry.playerView?.let { view ->
            // Keep ExoPlayer alive — PlatformViews are often recreated while PiP/fullscreen
            // overlays still own playback (especially on low-memory devices).
            KinescopeVideoSurfaceHelper.unbindView(view, entry.player)
            entry.playerView = null
        }
    }

    fun hideView(playerId: Long) {
        viewHideHandlers[playerId]?.invoke()
        val entry = players[playerId] ?: return
        entry.playerView?.let { view ->
            KinescopeVideoSurfaceHelper.teardown(view, entry.player)
        }
    }

    fun get(playerId: Long): KinescopeVideoPlayer = getEntry(playerId).player

    fun loadVideo(
        playerId: Long,
        videoId: String,
        onSuccess: () -> Unit,
        onFailed: (Throwable?) -> Unit,
    ) {
        val entry = getEntry(playerId)
        val player = entry.player
        when (entry.texttrackPreference) {
            true -> player.setShowSubtitles(true)
            false -> player.setShowSubtitles(false)
            null -> Unit
        }
        player.loadVideo(
            videoId,
            onSuccess = { video ->
                mainHandler.post {
                    applyQualityMapLabels(entry, video)
                    entry.playerView?.applyTemplateOptions()
                    entry.playerView?.refreshPlayerChrome()
                    notifyChromeRefreshed(playerId)
                    onSuccess()
                }
            },
            onFailed = { error ->
                mainHandler.post { onFailed(error) }
            },
        )
    }

    private fun applyQualityMapLabels(entry: PlayerEntry, video: io.kinescope.sdk.models.videos.KinescopeVideo?) {
        val qualityMap = video?.qualityMap ?: return
        val names = linkedMapOf<Int, String>()
        for (item in qualityMap) {
            val name = item.name.trim()
            if (name.isEmpty()) {
                continue
            }
            if (item.height > 0) {
                names[item.height] = name
            }
            Regex("""(\d+)""").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { digits ->
                names[digits] = name
            }
        }
        if (names.isEmpty()) {
            return
        }
        entry.player.setQualityNamesByHeight(names)
        entry.playerView?.setQualityNamesByHeight(names)
    }

    fun dispose(playerId: Long) {
        val entry = players.remove(playerId) ?: return
        chromeRefreshListeners.remove(playerId)
        viewHideHandlers.remove(playerId)
        fullscreenExitHandlers.remove(playerId)
        mainHandler.removeCallbacks(entry.timeUpdateRunnable)
        entry.player.exoPlayer?.removeListener(entry.listener)
        entry.playerView?.let { view ->
            KinescopeVideoSurfaceHelper.teardown(view, entry.player)
            entry.playerView = null
        }
        entry.player.release()
    }

    fun disposeAll() {
        players.keys.toList().forEach { dispose(it) }
    }

    private fun getEntry(playerId: Long): PlayerEntry =
        players[playerId] ?: throw IllegalStateException("No player for id $playerId")

    private fun emit(playerId: Long, payload: Map<String, Any?>) {
        mainHandler.post {
            eventSink(payload + mapOf("playerId" to playerId))
        }
    }

    private fun statusEvent(status: String): Map<String, Any?> =
        mapOf("type" to "status", "status" to status)

    private fun notifyChromeRefreshed(playerId: Long) {
        chromeRefreshListeners[playerId]?.invoke()
    }

    companion object {
        private const val TIME_UPDATE_INTERVAL_MS = 500L
        private const val LISTENER_ATTACH_RETRY_MS = 50L
        private const val LISTENER_ATTACH_MAX_ATTEMPTS = 40
    }
}
