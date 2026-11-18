import AVFoundation
import KinescopeSDK
import UIKit

struct KinescopeIosPlayerOptions {
    let autoplay: Bool
    let muted: Bool
    let loop: Bool
    let controls: Bool
    let playsinline: Bool
    let drmAuthToken: String?
    let referer: String?

    init(args: [String: Any]?) {
        autoplay = args?["autoplay"] as? Bool ?? false
        muted = args?["muted"] as? Bool ?? false
        loop = args?["loop"] as? Bool ?? false
        controls = args?["controls"] as? Bool ?? true
        playsinline = args?["playsinline"] as? Bool ?? true
        let token = (args?["drmAuthToken"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        drmAuthToken = (token?.isEmpty == false) ? token : nil
        let refererValue = (args?["referer"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        referer = (refererValue?.isEmpty == false) ? refererValue : nil
    }
}

final class KinescopeIosPlayerRegistry {
    private var sessions: [Int: KinescopeIosPlayerSession] = [:]
    private var nextId = 1
    var onEvent: (([String: Any]) -> Void)?

    func create(options: KinescopeIosPlayerOptions) -> Int {
        let id = nextId
        nextId += 1
        let session = KinescopeIosPlayerSession(id: id, options: options) { [weak self] payload in
            self?.onEvent?(payload)
        }
        sessions[id] = session
        session.emitStatus("init")
        return id
    }

    func session(_ id: Int) -> KinescopeIosPlayerSession? {
        sessions[id]
    }

    func allSessions() -> [KinescopeIosPlayerSession] {
        Array(sessions.values)
    }

    func dispose(_ id: Int) {
        guard let session = sessions.removeValue(forKey: id) else {
            return
        }
        session.teardown()
    }
}

final class KinescopeIosPlayerSession: NSObject, KinescopeVideoPlayerDelegate {
    let id: Int
    let options: KinescopeIosPlayerOptions
    private let onEvent: ([String: Any]) -> Void
    private(set) var player: KinescopeVideoPlayer?
    private weak var playerView: KinescopePlayerView?
    private var rememberedVolume: Float = 1

    init(id: Int, options: KinescopeIosPlayerOptions, onEvent: @escaping ([String: Any]) -> Void) {
        self.id = id
        self.options = options
        self.onEvent = onEvent
    }

    func loadVideo(_ videoId: String) {
        let next = KinescopeVideoPlayer(
            config: KinescopePlayerConfig(
                videoId: videoId,
                looped: options.loop,
                drmAuthToken: options.drmAuthToken
            )
        )
        replacePlayer(next)
        applyOptions(to: next)
        if let playerView {
            playerView.isHidden = false
            next.attach(view: playerView)
        }
        next.play()
    }

    func attach(view: KinescopePlayerView) {
        playerView = view
        view.isHidden = false
        player?.attach(view: view)
    }

    func detach(view: KinescopePlayerView) {
        guard playerView === view else {
            return
        }
        player?.detach(view: view)
        playerView = nil
    }

    func hideView() {
        player?.pause()
        playerView?.isHidden = true
    }

    func teardown() {
        player?.pause()
        player?.stop()
        if let playerView {
            player?.detach(view: playerView)
        }
        player = nil
        playerView = nil
    }

    var isFullscreen: Bool {
        guard let playerView else {
            return false
        }
        var responder: UIResponder? = playerView
        while let current = responder {
            if current is KinescopeFullscreenViewController {
                return true
            }
            responder = current.next
        }
        guard let root = playerView.window?.rootViewController else {
            return false
        }
        var top = root
        while let presented = top.presentedViewController {
            top = presented
        }
        return top is KinescopeFullscreenViewController
    }

    func exitFullscreen() {
        guard isFullscreen else {
            return
        }
        player?.enterFullscreen()
    }

    func play() {
        player?.play()
    }

    func pause() {
        player?.pause()
    }

    func stop() {
        player?.stop()
    }

    func seek(to seconds: TimeInterval) {
        player?.seek(to: seconds)
    }

    func currentTime() -> TimeInterval {
        player?.currentTime ?? 0
    }

    func duration() -> TimeInterval {
        player?.duration ?? 0
    }

    func setVolume(_ volume: Float) {
        let clamped = min(max(volume, 0), 1)
        if clamped > 0 {
            rememberedVolume = clamped
        }
        player?.setVolume(clamped)
        player?.isMuted = clamped == 0
    }

    func mute() {
        if let volume = player?.volume, volume > 0 {
            rememberedVolume = volume
        }
        player?.isMuted = true
    }

    func unmute() {
        player?.isMuted = false
        if (player?.volume ?? 0) == 0 {
            player?.setVolume(rememberedVolume > 0 ? rememberedVolume : 1)
        }
    }

    func playerDidPlay() {
        emitStatus("playing")
    }

    func playerDidPause() {
        emitStatus("pause")
    }

    func playerDidStop() {
        emitStatus("pause")
    }

    func playerDidLoadVideo(error: Error?) {
        if error != nil {
            emitStatus("unknown")
            return
        }
        emitStatus("ready")
        if !options.autoplay {
            player?.pause()
        }
        if !options.playsinline {
            player?.enterFullscreen()
        }
    }

    func playerDidFinishPlaying() {
        emitStatus("ended")
    }

    func player(playbackPositionMovedTo time: TimeInterval) {
        let length = duration()
        let percent = length > 0 ? Int((time / length) * 100) : 0
        emit([
            "playerId": id,
            "type": "timeUpdate",
            "currentTime": time,
            "percent": percent,
        ])
    }

    func player(changedTimeControlStatusTo status: AVPlayer.TimeControlStatus) {
        switch status {
        case .playing:
            emitStatus("playing")
        case .paused:
            emitStatus("pause")
        case .waitingToPlayAtSpecifiedRate:
            emitStatus("waiting")
        @unknown default:
            break
        }
    }

    private func replacePlayer(_ next: KinescopeVideoPlayer) {
        if let playerView {
            player?.detach(view: playerView)
        }
        player?.pause()
        player?.stop()
        player = next
        next.setDelegate(delegate: self)
    }

    private func applyOptions(to player: KinescopeVideoPlayer) {
        player.setLoop(enabled: options.loop)
        if options.muted {
            player.isMuted = true
            player.setVolume(0)
        }
        if !options.controls {
            player.disableOptions([
                .more,
                .fullscreen,
                .settings,
                .attachments,
                .download,
                .airPlay,
                .subtitles,
                .pip,
            ])
        }
    }

    func emitStatus(_ status: String) {
        emit([
            "playerId": id,
            "type": "status",
            "status": status,
        ])
    }

    private func emit(_ payload: [String: Any]) {
        if Thread.isMainThread {
            onEvent(payload)
        } else {
            DispatchQueue.main.async { [onEvent] in
                onEvent(payload)
            }
        }
    }
}
