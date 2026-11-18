import Flutter
import KinescopeSDK

final class KinescopeEventRelay: NSObject, FlutterStreamHandler {
    var sink: FlutterEventSink?

    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        sink = events
        return nil
    }

    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        sink = nil
        return nil
    }
}

final class KinescopeIosDownloads: NSObject, KinescopeVideoDownloadableDelegate {
    var onEvent: (([String: Any]) -> Void)?

    private var attached = false
    private var records: [String: [String: Any]] = [:]
    private var lastProgressEmit: [String: CFTimeInterval] = [:]

    func reattach() {
        attached = false
        ensureAttached()
    }

    func ensureAttached() {
        guard !attached else {
            return
        }
        attached = true
        Kinescope.shared.videoDownloader.add(delegate: self)
        Kinescope.shared.videoDownloader.restore()
    }

    func listQualities(videoId: String, result: @escaping FlutterResult) {
        ensureAttached()
        Kinescope.shared.inspector.video(id: videoId, onSuccess: { video in
            KinescopeOffline.qualities(for: video) { qualities in
                result(qualities.map { quality in
                    [
                        "height": quality.height,
                        "label": quality.name,
                        "qualityName": quality.name,
                    ]
                })
            }
        }, onError: { error in
            Self.finish(result, FlutterError(code: "QUALITIES_FAILED", message: String(describing: error), details: nil))
        })
    }

    func listQualitiesFromUrl(_ raw: String, result: @escaping FlutterResult) {
        if let videoId = kinescopeVideoId(from: raw) {
            listQualities(videoId: videoId, result: result)
            return
        }
        guard let url = URL(string: raw), raw.contains(".m3u8") else {
            result(FlutterError(code: "QUALITIES_FAILED", message: "Unrecognized download link", details: nil))
            return
        }
        URLSession.shared.dataTask(with: url) { data, _, _ in
            let playlist = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
            let heights = Self.variantHeights(in: playlist)
            DispatchQueue.main.async {
                result(heights.map { height in
                    [
                        "height": height,
                        "label": "\(height)p",
                        "qualityName": "\(height)p",
                    ]
                })
            }
        }.resume()
    }

    func downloadVideo(
        videoId: String,
        height: Int?,
        qualityHint: String?,
        result: @escaping FlutterResult
    ) {
        ensureAttached()
        Kinescope.shared.inspector.video(id: videoId, onSuccess: { [weak self] video in
            Self.onMain {
                guard let self else {
                    return
                }
                guard let url = URL(string: video.hlsLink) else {
                    result(FlutterError(code: "DOWNLOAD_FAILED", message: "Video has no stream", details: nil))
                    return
                }
                let record = self.remember(
                    videoId: video.id,
                    title: video.title,
                    manifest: video.hlsLink,
                    height: height,
                    qualityHint: qualityHint,
                    state: "downloading",
                    percent: 0
                )
                Kinescope.shared.videoDownloader.enqueueDownload(
                    videoId: video.id,
                    url: url,
                    video: video,
                    minimumHeight: height
                )
                self.emit(record)
                result(record)
            }
        }, onError: { error in
            Self.finish(result, FlutterError(code: "DOWNLOAD_FAILED", message: String(describing: error), details: nil))
        })
    }

    func downloadFromUrl(
        _ raw: String,
        height: Int?,
        qualityHint: String?,
        title: String?,
        result: @escaping FlutterResult
    ) {
        if let videoId = kinescopeVideoId(from: raw) {
            downloadVideo(videoId: videoId, height: height, qualityHint: qualityHint, result: result)
            return
        }
        guard let url = URL(string: raw) else {
            result(FlutterError(code: "DOWNLOAD_FAILED", message: "Unrecognized download link", details: nil))
            return
        }
        ensureAttached()
        let videoId = url.lastPathComponent.isEmpty ? raw : url.lastPathComponent
        let record = remember(
            videoId: videoId,
            title: title ?? videoId,
            manifest: raw,
            height: height,
            qualityHint: qualityHint,
            state: "downloading",
            percent: 0
        )
        Kinescope.shared.videoDownloader.enqueueDownload(
            videoId: videoId,
            url: url,
            video: nil,
            minimumHeight: height
        )
        emit(record)
        result(record)
    }

    func startDownload(contentId: String, manifest: String, height: Int?, qualityHint: String?) {
        ensureAttached()
        guard let url = URL(string: manifest) else {
            return
        }
        let record = remember(
            videoId: contentId,
            title: storedTitle(for: contentId) ?? contentId,
            manifest: manifest,
            height: height,
            qualityHint: qualityHint,
            state: "downloading",
            percent: 0
        )
        Kinescope.shared.videoDownloader.enqueueDownload(
            videoId: contentId,
            url: url,
            video: nil,
            minimumHeight: height
        )
        emit(record)
    }

    func catalog(result: @escaping FlutterResult) {
        KinescopeDashboard.videos { outcome in
            switch outcome {
            case .success(let videos):
                result(videos.map { video in
                    var item: [String: Any] = [
                        "id": video.id,
                        "title": video.title,
                    ]
                    if let duration = video.duration {
                        item["duration"] = duration
                    }
                    return item
                })
            case .failure(let error):
                result(FlutterError(code: "CATALOG_FAILED", message: String(describing: error), details: nil))
            }
        }
    }

    func download(id: String) -> [String: Any]? {
        ensureAttached()
        if let record = records[id] {
            return record
        }
        guard Kinescope.shared.videoDownloader.downloadedList().contains(id) else {
            return nil
        }
        return remember(
            videoId: id,
            title: storedTitle(for: id) ?? id,
            manifest: nil,
            height: nil,
            qualityHint: nil,
            state: "completed",
            percent: 100
        )
    }

    func allDownloads() -> [[String: Any]] {
        ensureAttached()
        for videoId in Kinescope.shared.videoDownloader.downloadedList() {
            if records[videoId]?["state"] as? String == "downloading" {
                continue
            }
            let current = records[videoId]
            _ = remember(
                videoId: videoId,
                title: current?["title"] as? String ?? storedTitle(for: videoId) ?? videoId,
                manifest: current?["uri"] as? String,
                height: current?["qualityHeight"] as? Int,
                qualityHint: current?["qualityLabel"] as? String,
                state: "completed",
                percent: 100
            )
        }
        return Array(records.values)
    }

    func remove(videoId: String) {
        ensureAttached()
        _ = Kinescope.shared.videoDownloader.delete(videoId: videoId)
        var record = records[videoId] ?? remember(
            videoId: videoId,
            title: videoId,
            manifest: nil,
            height: nil,
            qualityHint: nil,
            state: "removing",
            percent: 0
        )
        record["state"] = "removing"
        records.removeValue(forKey: videoId)
        emit(record)
    }

    func localFile(for videoId: String) -> URL? {
        ensureAttached()
        return Kinescope.shared.videoDownloader.getLocation(by: videoId)
    }

    /// Asset name chosen for this download, so offline playback can pin that rendition.
    func savedQualityName(for videoId: String) -> String? {
        if let label = records[videoId]?["qualityLabel"] as? String, !label.isEmpty {
            return label
        }
        let saved = UserDefaults.standard.dictionary(forKey: Self.qualitiesKey) as? [String: String]
        let label = saved?[videoId]
        return (label?.isEmpty == false) ? label : nil
    }

    func videoDownloadProgress(videoId: String, progress: Double) {
        Self.onMain { [weak self] in
            guard let self else {
                return
            }
            let percent = Int(min(max(progress, 0), 100))
            let current = self.records[videoId]
            let record = self.remember(
                videoId: videoId,
                title: current?["title"] as? String ?? self.storedTitle(for: videoId) ?? videoId,
                manifest: current?["uri"] as? String,
                height: current?["qualityHeight"] as? Int,
                qualityHint: current?["qualityLabel"] as? String,
                state: "downloading",
                percent: percent
            )
            let now = CACurrentMediaTime()
            let last = self.lastProgressEmit[videoId] ?? 0
            if percent < 100, now - last < 0.5 {
                return
            }
            self.lastProgressEmit[videoId] = now
            self.emit(record)
        }
    }

    func videoDownloadError(videoId: String, error: KinescopeDownloadError) {
        Self.onMain { [weak self] in
            guard let self else {
                return
            }
            let current = self.records[videoId]
            let record = self.remember(
                videoId: videoId,
                title: current?["title"] as? String ?? videoId,
                manifest: current?["uri"] as? String,
                height: current?["qualityHeight"] as? Int,
                qualityHint: current?["qualityLabel"] as? String,
                state: "failed",
                percent: current?["percent"] as? Int ?? 0
            )
            self.emit(record)
        }
    }

    func videoDownloadComplete(videoId: String) {
        Self.onMain { [weak self] in
            guard let self else {
                return
            }
            let current = self.records[videoId]
            let record = self.remember(
                videoId: videoId,
                title: current?["title"] as? String ?? self.storedTitle(for: videoId) ?? videoId,
                manifest: current?["uri"] as? String,
                height: current?["qualityHeight"] as? Int,
                qualityHint: current?["qualityLabel"] as? String,
                state: "completed",
                percent: 100
            )
            self.emit(record)
        }
    }

    private func remember(
        videoId: String,
        title: String?,
        manifest: String?,
        height: Int?,
        qualityHint: String?,
        state: String,
        percent: Int
    ) -> [String: Any] {
        var record: [String: Any] = [
            "contentId": videoId,
            "videoId": videoId,
            "title": title ?? videoId,
            "state": state,
            "percent": percent,
            "mimeType": "application/x-mpegURL",
        ]
        if let manifest {
            record["uri"] = manifest
        }
        if let height {
            record["qualityHeight"] = height
        }
        if let qualityHint {
            record["qualityLabel"] = qualityHint
        }
        records[videoId] = record
        if let title, title != videoId {
            var saved = UserDefaults.standard.dictionary(forKey: Self.titlesKey) as? [String: String] ?? [:]
            saved[videoId] = title
            UserDefaults.standard.set(saved, forKey: Self.titlesKey)
        }
        if let qualityHint, !qualityHint.isEmpty {
            var saved = UserDefaults.standard.dictionary(forKey: Self.qualitiesKey) as? [String: String] ?? [:]
            saved[videoId] = qualityHint
            UserDefaults.standard.set(saved, forKey: Self.qualitiesKey)
        }
        return record
    }

    private func storedTitle(for videoId: String) -> String? {
        let saved = UserDefaults.standard.dictionary(forKey: Self.titlesKey) as? [String: String]
        return saved?[videoId]
    }

    private static let titlesKey = "kinescope.offline.titles"
    private static let qualitiesKey = "kinescope.offline.qualities"

    private static func onMain(_ work: @escaping () -> Void) {
        if Thread.isMainThread {
            work()
        } else {
            DispatchQueue.main.async(execute: work)
        }
    }

    private static func finish(_ result: @escaping FlutterResult, _ value: Any?) {
        onMain {
            result(value)
        }
    }

    private func emit(_ record: [String: Any]) {
        let payload: [String: Any] = [
            "type": "downloadChanged",
            "download": record,
        ]
        if Thread.isMainThread {
            onEvent?(payload)
        } else {
            DispatchQueue.main.async { [onEvent] in
                onEvent?(payload)
            }
        }
    }

    private static func variantHeights(in playlist: String) -> [Int] {
        var seen = Set<Int>()
        var heights: [Int] = []
        for line in playlist.split(whereSeparator: \.isNewline) where line.contains("RESOLUTION=") {
            guard let range = line.range(of: "RESOLUTION="),
                  let token = line[range.upperBound...].split(separator: ",").first else {
                continue
            }
            let sides = token.split(separator: "x")
            guard sides.count == 2, let height = Int(sides[1]), height > 0, seen.insert(height).inserted else {
                continue
            }
            heights.append(height)
        }
        return heights.sorted(by: >)
    }
}

func kinescopeVideoId(from raw: String) -> String? {
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
        return nil
    }
    if trimmed.contains(".m3u8") || trimmed.contains(".mpd") {
        return nil
    }
    guard trimmed.contains("://") || trimmed.lowercased().contains("kinescope.") else {
        return trimmed
    }
    guard let components = URLComponents(string: trimmed.contains("://") ? trimmed : "https://\(trimmed)"),
          let host = components.host?.lowercased(),
          host.contains("kinescope") else {
        return trimmed
    }
    let skipped: Set<String> = ["embed", "video", "watch", "player", "share", "v", "live", "e"]
    let parts = components.path
        .split(separator: "/")
        .map(String.init)
        .filter { !$0.isEmpty && !skipped.contains($0.lowercased()) }
    return parts.last ?? trimmed
}
