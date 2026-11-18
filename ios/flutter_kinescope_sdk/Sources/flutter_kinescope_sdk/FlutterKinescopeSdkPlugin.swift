import Flutter
import KinescopeSDK
import UIKit

public class FlutterKinescopeSdkPlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
    private let registry = KinescopeIosPlayerRegistry()
    private let downloads = KinescopeIosDownloads()
    private let downloadEvents = KinescopeEventRelay()
    private var methodChannel: FlutterMethodChannel?
    private var fullscreenTimer: Timer?
    private var fullscreenStates: [Int: Bool] = [:]
    private var offlineSessions: [String: KinescopeIosOfflineSession] = [:]
    private var offlineFullscreen: [String: Bool] = [:]
    private var apiKey: String?
    private var referer = "https://kinescope.io/"
    private var appliedApiKey: String?
    private var appliedReferer: String?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = FlutterKinescopeSdkPlugin()
        let channel = FlutterMethodChannel(
            name: "flutter_kinescope_sdk",
            binaryMessenger: registrar.messenger()
        )
        instance.methodChannel = channel
        registrar.addMethodCallDelegate(instance, channel: channel)

        let playerEvents = FlutterEventChannel(
            name: "flutter_kinescope_sdk/player_events",
            binaryMessenger: registrar.messenger()
        )
        playerEvents.setStreamHandler(instance)

        let downloadEvents = FlutterEventChannel(
            name: "flutter_kinescope_sdk/download_events",
            binaryMessenger: registrar.messenger()
        )
        downloadEvents.setStreamHandler(instance.downloadEvents)
        instance.downloads.onEvent = { [weak instance] payload in
            instance?.downloadEvents.sink?(payload)
        }

        registrar.register(
            KinescopeIosPlayerViewFactory(registry: instance.registry),
            withId: "kinescope-player-view"
        )
        registrar.register(
            KinescopeIosOfflineViewFactory(plugin: instance),
            withId: "kinescope-offline-player-view"
        )

        instance.registry.onEvent = { [weak instance] payload in
            instance?.playerEventSink?(payload)
        }
        instance.applySdkConfig()
        instance.startFullscreenPolling()
    }

    private var playerEventSink: FlutterEventSink?

    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        playerEventSink = events
        return nil
    }

    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        playerEventSink = nil
        return nil
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "configure":
            let args = call.arguments as? [String: Any]
            if let key = args?["apiKey"] as? String, !key.isEmpty {
                apiKey = key
            }
            applySdkConfig()
            result(nil)
        case "createPlayer":
            let options = KinescopeIosPlayerOptions(args: call.arguments as? [String: Any])
            if let referer = options.referer {
                self.referer = referer
                applySdkConfig()
            }
            result(registry.create(options: options))
        case "disposePlayer":
            if let playerId = playerId(from: call.arguments) {
                fullscreenStates.removeValue(forKey: playerId)
                registry.dispose(playerId)
            }
            result(nil)
        case "hidePlayerView":
            if let playerId = playerId(from: call.arguments) {
                registry.session(playerId)?.hideView()
            }
            result(nil)
        case "exitFullscreen":
            if let playerId = playerId(from: call.arguments) {
                registry.session(playerId)?.exitFullscreen()
            }
            result(nil)
        case "loadVideo":
            let args = call.arguments as? [String: Any]
            guard
                let playerId = playerId(from: args?["playerId"]),
                let videoId = args?["videoId"] as? String,
                let session = registry.session(playerId)
            else {
                result(FlutterError(code: "LOAD_FAILED", message: "Player not found", details: nil))
                return
            }
            applySdkConfig()
            session.loadVideo(videoId)
            result(nil)
        case "play":
            session(for: call.arguments)?.play()
            result(nil)
        case "pause":
            session(for: call.arguments)?.pause()
            result(nil)
        case "stop":
            session(for: call.arguments)?.stop()
            result(nil)
        case "seekTo":
            let args = call.arguments as? [String: Any]
            let positionMs = (args?["positionMs"] as? NSNumber)?.doubleValue ?? 0
            session(for: args?["playerId"])?.seek(to: positionMs / 1000)
            result(nil)
        case "getCurrentTime":
            result(session(for: call.arguments)?.currentTime() ?? 0)
        case "getDuration":
            result(session(for: call.arguments)?.duration() ?? 0)
        case "setVolume":
            let args = call.arguments as? [String: Any]
            let volume = (args?["volume"] as? NSNumber)?.floatValue ?? 1
            session(for: args?["playerId"])?.setVolume(volume)
            result(nil)
        case "mute":
            session(for: call.arguments)?.mute()
            result(nil)
        case "unmute":
            session(for: call.arguments)?.unmute()
            result(nil)
        case "initializeDownloads":
            applySdkConfig()
            downloads.ensureAttached()
            result(nil)
        case "ensureDownloadPermissions":
            result(true)
        case "listDownloadQualities":
            let args = call.arguments as? [String: Any]
            applySdkConfig()
            guard let videoId = args?["videoId"] as? String, !videoId.isEmpty else {
                result(FlutterError(code: "QUALITIES_FAILED", message: "Missing video id", details: nil))
                return
            }
            downloads.listQualities(videoId: videoId, result: result)
        case "listDownloadQualitiesFromUrl":
            let args = call.arguments as? [String: Any]
            applySdkConfig()
            guard let url = args?["url"] as? String, !url.isEmpty else {
                result(FlutterError(code: "QUALITIES_FAILED", message: "Missing download link", details: nil))
                return
            }
            downloads.listQualitiesFromUrl(url, result: result)
        case "downloadVideo":
            let args = call.arguments as? [String: Any]
            applySdkConfig()
            guard let videoId = args?["videoId"] as? String, !videoId.isEmpty else {
                result(FlutterError(code: "DOWNLOAD_FAILED", message: "Missing video id", details: nil))
                return
            }
            downloads.downloadVideo(
                videoId: videoId,
                height: kinescopeInt(args?["videoHeightPx"]),
                qualityHint: args?["qualityHint"] as? String,
                result: result
            )
        case "downloadFromUrl":
            let args = call.arguments as? [String: Any]
            applySdkConfig()
            guard let url = args?["url"] as? String, !url.isEmpty else {
                result(FlutterError(code: "DOWNLOAD_FAILED", message: "Missing download link", details: nil))
                return
            }
            downloads.downloadFromUrl(
                url,
                height: kinescopeInt(args?["videoHeightPx"]),
                qualityHint: args?["qualityHint"] as? String,
                title: args?["title"] as? String,
                result: result
            )
        case "startDownload":
            let args = call.arguments as? [String: Any]
            applySdkConfig()
            if let contentId = args?["contentId"] as? String,
               let manifest = args?["manifestUri"] as? String {
                downloads.startDownload(
                    contentId: contentId,
                    manifest: manifest,
                    height: kinescopeInt(args?["videoHeightPx"]),
                    qualityHint: args?["qualityHint"] as? String
                )
            }
            result(nil)
        case "getAllDownloads":
            applySdkConfig()
            result(downloads.allDownloads())
        case "getCompletedDownloads":
            applySdkConfig()
            result(downloads.allDownloads().filter { $0["state"] as? String == "completed" })
        case "getDownload":
            applySdkConfig()
            result(downloads.download(id: call.arguments as? String ?? ""))
        case "removeDownload":
            applySdkConfig()
            if let videoId = call.arguments as? String {
                downloads.remove(videoId: videoId)
            }
            result(nil)
        case "getVideoCatalog":
            applySdkConfig()
            downloads.catalog(result: result)
        case "hideOfflinePlayerView":
            if let contentId = call.arguments as? String {
                offlineSessions[contentId]?.hide()
            }
            result(nil)
        case "exitOfflineFullscreen":
            if let contentId = call.arguments as? String {
                offlineSessions[contentId]?.exitFullscreen()
            }
            result(nil)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func session(for arguments: Any?) -> KinescopeIosPlayerSession? {
        guard let playerId = playerId(from: arguments) else {
            return nil
        }
        return registry.session(playerId)
    }

    private func playerId(from arguments: Any?) -> Int? {
        if let map = arguments as? [String: Any] {
            return kinescopeInt(map["playerId"])
        }
        return kinescopeInt(arguments)
    }

    private func applySdkConfig() {
        if appliedApiKey == apiKey, appliedReferer == referer, Kinescope.shared.config != nil {
            return
        }
        appliedApiKey = apiKey
        appliedReferer = referer
        Kinescope.shared.setConfig(
            KinescopeConfig(referer: referer, apiKey: apiKey)
        )
        downloads.reattach()
    }

    func attachOffline(contentId: String, view: KinescopePlayerView) {
        applySdkConfig()
        offlineSessions[contentId]?.detach(view: view)
        guard let fileURL = downloads.localFile(for: contentId) else {
            return
        }
        let session = KinescopeIosOfflineSession(
            contentId: contentId,
            view: view,
            fileURL: fileURL,
            qualityName: downloads.savedQualityName(for: contentId)
        )
        session.start()
        offlineSessions[contentId] = session
    }

    func detachOffline(contentId: String, view: KinescopePlayerView) {
        guard let session = offlineSessions[contentId], session.owns(view) else {
            return
        }
        session.detach(view: view)
        offlineSessions.removeValue(forKey: contentId)
        offlineFullscreen.removeValue(forKey: contentId)
    }

    private func startFullscreenPolling() {
        fullscreenTimer?.invalidate()
        let timer = Timer(timeInterval: 0.3, repeats: true) { [weak self] _ in
            self?.pollFullscreen()
        }
        RunLoop.main.add(timer, forMode: .common)
        fullscreenTimer = timer
    }

    private func pollFullscreen() {
        var liveIds = Set<Int>()
        for session in registry.allSessions() {
            liveIds.insert(session.id)
            let fullscreen = session.isFullscreen
            let previous = fullscreenStates[session.id] ?? false
            guard fullscreen != previous else {
                continue
            }
            fullscreenStates[session.id] = fullscreen
            methodChannel?.invokeMethod(
                fullscreen ? "onEnterFullscreen" : "onExitFullscreen",
                arguments: nil
            )
        }
        fullscreenStates = fullscreenStates.filter { liveIds.contains($0.key) }

        var liveOffline = Set<String>()
        for (contentId, session) in offlineSessions {
            liveOffline.insert(contentId)
            let fullscreen = session.isFullscreen
            let previous = offlineFullscreen[contentId] ?? false
            guard fullscreen != previous else {
                continue
            }
            offlineFullscreen[contentId] = fullscreen
            methodChannel?.invokeMethod(
                fullscreen ? "onEnterFullscreen" : "onExitFullscreen",
                arguments: nil
            )
        }
        offlineFullscreen = offlineFullscreen.filter { liveOffline.contains($0.key) }
    }
}

final class KinescopeIosPlayerViewFactory: NSObject, FlutterPlatformViewFactory {
    private let registry: KinescopeIosPlayerRegistry

    init(registry: KinescopeIosPlayerRegistry) {
        self.registry = registry
    }

    func create(
        withFrame frame: CGRect,
        viewIdentifier viewId: Int64,
        arguments args: Any?
    ) -> FlutterPlatformView {
        KinescopeIosPlayerPlatformView(
            frame: frame,
            arguments: args as? [String: Any],
            registry: registry
        )
    }

    func createArgsCodec() -> FlutterMessageCodec & NSObjectProtocol {
        FlutterStandardMessageCodec.sharedInstance()
    }
}

final class KinescopeIosPlayerPlatformView: NSObject, FlutterPlatformView {
    private let playerView: KinescopePlayerView
    private let registry: KinescopeIosPlayerRegistry
    private let playerId: Int?

    init(frame: CGRect, arguments: [String: Any]?, registry: KinescopeIosPlayerRegistry) {
        self.registry = registry
        playerId = kinescopeInt(arguments?["playerId"])
        let created = KinescopePlayerView()
        created.frame = frame
        created.setLayout(with: .default)
        playerView = created
        super.init()
        playerView.backgroundColor = .black
        playerView.clipsToBounds = true
        if let playerId {
            registry.session(playerId)?.attach(view: playerView)
        }
    }

    func view() -> UIView {
        playerView
    }

    deinit {
        if let playerId {
            registry.session(playerId)?.detach(view: playerView)
        }
    }
}

final class KinescopeIosOfflineViewFactory: NSObject, FlutterPlatformViewFactory {
    private let plugin: FlutterKinescopeSdkPlugin

    init(plugin: FlutterKinescopeSdkPlugin) {
        self.plugin = plugin
    }

    func create(
        withFrame frame: CGRect,
        viewIdentifier viewId: Int64,
        arguments args: Any?
    ) -> FlutterPlatformView {
        KinescopeIosOfflinePlatformView(
            frame: frame,
            arguments: args as? [String: Any],
            plugin: plugin
        )
    }

    func createArgsCodec() -> FlutterMessageCodec & NSObjectProtocol {
        FlutterStandardMessageCodec.sharedInstance()
    }
}

final class KinescopeIosOfflinePlatformView: NSObject, FlutterPlatformView {
    private let playerView: KinescopePlayerView
    private let plugin: FlutterKinescopeSdkPlugin
    private let contentId: String?

    init(frame: CGRect, arguments: [String: Any]?, plugin: FlutterKinescopeSdkPlugin) {
        self.plugin = plugin
        contentId = arguments?["contentId"] as? String
        let created = KinescopePlayerView()
        created.frame = frame
        created.setLayout(with: .default)
        playerView = created
        super.init()
        playerView.backgroundColor = .black
        playerView.clipsToBounds = true
        if let contentId, !contentId.isEmpty {
            plugin.attachOffline(contentId: contentId, view: playerView)
        }
    }

    func view() -> UIView {
        playerView
    }

    deinit {
        if let contentId {
            plugin.detachOffline(contentId: contentId, view: playerView)
        }
    }
}

final class KinescopeIosOfflineSession: KinescopeVideoPlayerDelegate {
    private weak var playerView: KinescopePlayerView?
    private var player: KinescopeVideoPlayer?
    private let qualityName: String?
    private var didPinQuality = false

    init(contentId: String, view: KinescopePlayerView, fileURL: URL, qualityName: String?) {
        playerView = view
        self.qualityName = qualityName
        let created = KinescopeVideoPlayer(
            config: KinescopePlayerConfig(videoId: contentId, localPlaybackURL: fileURL)
        )
        player = created
    }

    func start() {
        guard let player, let playerView else {
            return
        }
        player.setDelegate(delegate: self)
        player.attach(view: playerView)
        player.play()
    }

    func playerDidLoadVideo(error: Error?) {
        guard error == nil else {
            return
        }
        pinQuality()
    }

    /// Same as Android offline playback: keep the downloaded rendition and show it on the gear.
    private func pinQuality() {
        guard !didPinQuality, let qualityName else {
            return
        }
        let trimmed = qualityName.trimmingCharacters(in: .whitespacesAndNewlines)
        let digits = trimmed.filter(\.isNumber)
        // "1080" would be rewritten to "1080p" and miss the asset, switching playback to the network.
        guard !trimmed.isEmpty, digits != trimmed else {
            return
        }
        didPinQuality = true
        DispatchQueue.main.async { [weak self] in
            self?.player?.selectQuality(named: qualityName)
        }
    }

    func owns(_ view: KinescopePlayerView) -> Bool {
        playerView === view
    }

    func hide() {
        player?.pause()
        playerView?.isHidden = true
    }

    func detach(view: KinescopePlayerView) {
        guard playerView === view else {
            return
        }
        player?.pause()
        player?.stop()
        player?.detach(view: view)
        player = nil
        playerView = nil
    }

    var isFullscreen: Bool {
        kinescopeViewIsFullscreen(playerView)
    }

    func exitFullscreen() {
        guard isFullscreen else {
            return
        }
        player?.enterFullscreen()
    }
}

func kinescopeViewIsFullscreen(_ playerView: UIView?) -> Bool {
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

private func kinescopeInt(_ raw: Any?) -> Int? {
    switch raw {
    case let value as Int:
        return value
    case let value as Int64:
        return Int(value)
    case let value as NSNumber:
        return Int(truncating: value)
    default:
        return nil
    }
}
