// Copyright (c) 2021-present, Kinescope
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_kinescope_sdk/src/platform/kinescope_android_bridge.dart';

/// Offline video downloads via [kotlin-kinescope-player](https://github.com/kinescope/kotlin-kinescope-player).
///
/// Android only. On other platforms methods are no-ops or return empty values.
class KinescopeOfflineDownload {
  KinescopeOfflineDownload._();

  static final KinescopeOfflineDownload instance = KinescopeOfflineDownload._();

  final _bridge = KinescopeAndroidBridge.instance;
  final _downloadUpdatesController =
      StreamController<KinescopeDownloadUpdate>.broadcast();

  StreamSubscription<Map<dynamic, dynamic>>? _downloadEventsSubscription;
  bool _initialized = false;

  /// Stream of download progress and state changes.
  Stream<KinescopeDownloadUpdate> get updates => _downloadUpdatesController.stream;

  bool get isSupported =>
      !kIsWeb && Platform.isAndroid;

  Future<void> initialize({String? apiKey}) async {
    if (!isSupported) {
      return;
    }
    if (apiKey != null && apiKey.isNotEmpty) {
      await _bridge.configure(apiKey: apiKey);
    }
    if (_initialized) {
      return;
    }
    _initialized = true;
    await _bridge.initializeDownloads();
    await _bridge.ensureDownloadPermissions();
    _downloadEventsSubscription ??=
        _bridge.downloadEvents.listen(_handleDownloadEvent);
  }

  Future<void> startDownload({
    required String contentId,
    required String manifestUri,
    String mimeType = 'application/x-mpegURL',
    String? metadata,
    String? keySetId,
    int? videoHeightPx,
    int? videoWidthPx,
    String? qualityHint,
  }) async {
    if (!isSupported) {
      return;
    }
    await initialize();
    await _bridge.startDownload(
      contentId: contentId,
      manifestUri: manifestUri,
      mimeType: mimeType,
      metadata: metadata,
      keySetId: keySetId,
      videoHeightPx: videoHeightPx,
      videoWidthPx: videoWidthPx,
      qualityHint: qualityHint,
    );
  }

  /// Lists downloadable HLS/DASH heights for [videoId] (0.1.4+ quality picker).
  Future<List<KinescopeDownloadQuality>> listDownloadQualities(
    String videoId, {
    String? apiKey,
  }) async {
    if (!isSupported) {
      throw UnsupportedError('Offline downloads are only available on Android');
    }
    await initialize();
    return _bridge.listDownloadQualities(videoId, apiKey: apiKey);
  }

  /// Fetches video metadata and starts a single-quality HLS/DASH download.
  ///
  /// Prefer picking a quality via [listDownloadQualities] and passing
  /// [videoHeightPx]. If height is omitted, the highest available quality is used.
  Future<KinescopeDownloadInfo> downloadVideo(
    String videoId, {
    String? contentId,
    String? apiKey,
    int? videoHeightPx,
    int? videoWidthPx,
    String? qualityHint,
  }) async {
    if (!isSupported) {
      throw UnsupportedError('Offline downloads are only available on Android');
    }
    await initialize();
    await _bridge.ensureDownloadPermissions();
    return _bridge.downloadVideo(
      videoId,
      contentId: contentId,
      apiKey: apiKey,
      videoHeightPx: videoHeightPx,
      videoWidthPx: videoWidthPx,
      qualityHint: qualityHint,
    );
  }

  /// Lists qualities for a Kinescope id/URL or a raw `.m3u8` / `.mpd` link.
  Future<List<KinescopeDownloadQuality>> listDownloadQualitiesFromUrl(
    String url, {
    String? apiKey,
  }) async {
    if (!isSupported) {
      throw UnsupportedError('Offline downloads are only available on Android');
    }
    await initialize();
    return _bridge.listDownloadQualitiesFromUrl(url, apiKey: apiKey);
  }

  /// Downloads from a Kinescope video id / page URL, or a direct HLS/DASH manifest.
  ///
  /// Examples:
  /// - `sEsxJQ7Hi4QLWwbmZEFfgz`
  /// - `https://kinescope.io/sEsxJQ7Hi4QLWwbmZEFfgz`
  /// - `https://kinescope.io/embed/sEsxJQ7Hi4QLWwbmZEFfgz`
  /// - `https://…/master.m3u8`
  Future<KinescopeDownloadInfo> downloadFromUrl(
    String url, {
    String? contentId,
    String? apiKey,
    int? videoHeightPx,
    int? videoWidthPx,
    String? qualityHint,
    String? title,
  }) async {
    if (!isSupported) {
      throw UnsupportedError('Offline downloads are only available on Android');
    }
    await initialize();
    await _bridge.ensureDownloadPermissions();
    return _bridge.downloadFromUrl(
      url,
      contentId: contentId,
      apiKey: apiKey,
      videoHeightPx: videoHeightPx,
      videoWidthPx: videoWidthPx,
      qualityHint: qualityHint,
      title: title,
    );
  }

  Future<void> removeDownload(String downloadId) async {
    if (!isSupported) {
      return;
    }
    await initialize();
    await _bridge.removeDownload(downloadId);
  }

  Future<List<KinescopeDownloadInfo>> getCompletedDownloads() async {
    if (!isSupported) {
      return const [];
    }
    await initialize();
    return _bridge.getCompletedDownloads();
  }

  Future<List<KinescopeDownloadInfo>> getAllDownloads() async {
    if (!isSupported) {
      return const [];
    }
    await initialize();
    return _bridge.getAllDownloads();
  }

  Future<List<KinescopeCatalogVideo>> getVideoCatalog() async {
    if (!isSupported) {
      return const [];
    }
    await initialize();
    return _bridge.getVideoCatalog();
  }

  Future<KinescopeDownloadInfo?> getDownload(String downloadId) async {
    if (!isSupported) {
      return null;
    }
    await initialize();
    return _bridge.getDownload(downloadId);
  }

  void _handleDownloadEvent(Map<dynamic, dynamic> event) {
    final type = event['type'];
    if (type == 'downloadChanged') {
      final downloadMap = event['download'];
      if (downloadMap is Map) {
        final info = KinescopeDownloadInfo.fromMap(
          Map<String, dynamic>.from(downloadMap),
        );
        _downloadUpdatesController.add(
          KinescopeDownloadUpdate(
            download: info,
            error: event['error'] as String?,
          ),
        );
      }
      return;
    }

    if (type == 'downloadsChanged') {
      final downloads = event['downloads'];
      if (downloads is List) {
        for (final item in downloads) {
          if (item is Map) {
            _downloadUpdatesController.add(
              KinescopeDownloadUpdate(
                download: KinescopeDownloadInfo.fromMap(
                  Map<String, dynamic>.from(item),
                ),
              ),
            );
          }
        }
      }
    }
  }

  Future<void> dispose() async {
    await _downloadEventsSubscription?.cancel();
    _downloadEventsSubscription = null;
    _initialized = false;
  }
}

class KinescopeDownloadUpdate {
  final KinescopeDownloadInfo download;
  final String? error;

  const KinescopeDownloadUpdate({
    required this.download,
    this.error,
  });
}
