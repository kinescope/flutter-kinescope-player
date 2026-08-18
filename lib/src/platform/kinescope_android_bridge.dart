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

import 'package:flutter/services.dart';
import 'package:flutter_kinescope_sdk/src/data/player_status.dart';
import 'package:flutter_kinescope_sdk/src/data/player_time_update.dart';

const _methodChannel = MethodChannel('flutter_kinescope_sdk');
const _playerEventChannel = EventChannel('flutter_kinescope_sdk/player_events');
const _downloadEventChannel =
    EventChannel('flutter_kinescope_sdk/download_events');

class KinescopeAndroidBridge {
  KinescopeAndroidBridge._();

  static final KinescopeAndroidBridge instance = KinescopeAndroidBridge._();

  /// Kinescope project API key for DRM offline downloads and license URLs.
  Future<void> configure({required String apiKey}) {
    return _methodChannel.invokeMethod<void>('configure', {
      'apiKey': apiKey,
    });
  }

  Stream<Map<dynamic, dynamic>> get playerEvents =>
      _playerEventChannel.receiveBroadcastStream().map(
        (event) => Map<dynamic, dynamic>.from(event as Map),
      );

  Stream<Map<dynamic, dynamic>> get downloadEvents =>
      _downloadEventChannel.receiveBroadcastStream().map(
        (event) => Map<dynamic, dynamic>.from(event as Map),
      );

  Future<int> createPlayer(Map<String, dynamic> options) async {
    final playerId = await _methodChannel.invokeMethod<int>(
      'createPlayer',
      options,
    );
    return playerId ?? 0;
  }

  Future<void> hidePlayerView(int playerId) {
    return _methodChannel.invokeMethod<void>('hidePlayerView', playerId);
  }

  Future<void> exitFullscreen(int playerId) {
    return _methodChannel.invokeMethod<void>('exitFullscreen', playerId);
  }

  Future<void> disposePlayer(int playerId) {
    return _methodChannel.invokeMethod<void>('disposePlayer', playerId);
  }

  Future<void> loadVideo(int playerId, String videoId) {
    return _methodChannel.invokeMethod<void>('loadVideo', {
      'playerId': playerId,
      'videoId': videoId,
    });
  }

  Future<void> play(int playerId) {
    return _methodChannel.invokeMethod<void>('play', playerId);
  }

  Future<void> pause(int playerId) {
    return _methodChannel.invokeMethod<void>('pause', playerId);
  }

  Future<void> stop(int playerId) {
    return _methodChannel.invokeMethod<void>('stop', playerId);
  }

  Future<void> seekTo(int playerId, Duration position) {
    return _methodChannel.invokeMethod<void>('seekTo', {
      'playerId': playerId,
      'positionMs': position.inMilliseconds,
    });
  }

  Future<Duration> getCurrentTime(int playerId) async {
    final seconds = await _methodChannel.invokeMethod<double>(
      'getCurrentTime',
      playerId,
    );
    return Duration(milliseconds: ((seconds ?? 0) * 1000).round());
  }

  Future<Duration> getDuration(int playerId) async {
    final seconds = await _methodChannel.invokeMethod<double>(
      'getDuration',
      playerId,
    );
    return Duration(milliseconds: ((seconds ?? 0) * 1000).round());
  }

  Future<void> setVolume(int playerId, double volume) {
    return _methodChannel.invokeMethod<void>('setVolume', {
      'playerId': playerId,
      'volume': volume,
    });
  }

  Future<void> mute(int playerId) {
    return _methodChannel.invokeMethod<void>('mute', playerId);
  }

  Future<void> unmute(int playerId) {
    return _methodChannel.invokeMethod<void>('unmute', playerId);
  }

  Future<void> initializeDownloads() {
    return _methodChannel.invokeMethod<void>('initializeDownloads');
  }

  Future<bool> ensureDownloadPermissions() async {
    final granted = await _methodChannel.invokeMethod<bool>(
      'ensureDownloadPermissions',
    );
    return granted ?? false;
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
  }) {
    return _methodChannel.invokeMethod<void>('startDownload', {
      'contentId': contentId,
      'manifestUri': manifestUri,
      'mimeType': mimeType,
      if (metadata != null) 'metadata': metadata,
      if (keySetId != null) 'keySetId': keySetId,
      if (videoHeightPx != null) 'videoHeightPx': videoHeightPx,
      if (videoWidthPx != null) 'videoWidthPx': videoWidthPx,
      if (qualityHint != null) 'qualityHint': qualityHint,
    });
  }

  Future<List<KinescopeDownloadQuality>> listDownloadQualities(
    String videoId, {
    String? apiKey,
  }) async {
    final result = await _methodChannel.invokeMethod<List<dynamic>>(
      'listDownloadQualities',
      {
        'videoId': videoId,
        if (apiKey != null) 'apiKey': apiKey,
      },
    );
    return result
            ?.map(
              (item) => KinescopeDownloadQuality.fromMap(
                Map<String, dynamic>.from(item as Map),
              ),
            )
            .toList() ??
        const [];
  }

  Future<KinescopeDownloadInfo> downloadVideo(
    String videoId, {
    String? contentId,
    String? apiKey,
    int? videoHeightPx,
    int? videoWidthPx,
    String? qualityHint,
  }) async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>(
      'downloadVideo',
      {
        'videoId': videoId,
        if (contentId != null) 'contentId': contentId,
        if (apiKey != null) 'apiKey': apiKey,
        if (videoHeightPx != null) 'videoHeightPx': videoHeightPx,
        if (videoWidthPx != null) 'videoWidthPx': videoWidthPx,
        if (qualityHint != null) 'qualityHint': qualityHint,
      },
    );
    return KinescopeDownloadInfo.fromMap(result ?? const {});
  }

  /// Lists qualities for a Kinescope video id/URL or a raw `.m3u8` / `.mpd` manifest.
  Future<List<KinescopeDownloadQuality>> listDownloadQualitiesFromUrl(
    String url, {
    String? apiKey,
  }) async {
    final result = await _methodChannel.invokeMethod<List<dynamic>>(
      'listDownloadQualitiesFromUrl',
      {
        'url': url,
        if (apiKey != null) 'apiKey': apiKey,
      },
    );
    return result
            ?.map(
              (item) => KinescopeDownloadQuality.fromMap(
                Map<String, dynamic>.from(item as Map),
              ),
            )
            .toList() ??
        const [];
  }

  /// Downloads from a Kinescope video id/page URL or a direct HLS/DASH manifest URI.
  Future<KinescopeDownloadInfo> downloadFromUrl(
    String url, {
    String? contentId,
    String? apiKey,
    int? videoHeightPx,
    int? videoWidthPx,
    String? qualityHint,
    String? title,
  }) async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>(
      'downloadFromUrl',
      {
        'url': url,
        if (contentId != null) 'contentId': contentId,
        if (apiKey != null) 'apiKey': apiKey,
        if (videoHeightPx != null) 'videoHeightPx': videoHeightPx,
        if (videoWidthPx != null) 'videoWidthPx': videoWidthPx,
        if (qualityHint != null) 'qualityHint': qualityHint,
        if (title != null) 'title': title,
      },
    );
    return KinescopeDownloadInfo.fromMap(result ?? const {});
  }

  Future<void> removeDownload(String downloadId) {
    return _methodChannel.invokeMethod<void>('removeDownload', downloadId);
  }

  Future<List<KinescopeDownloadInfo>> getCompletedDownloads() async {
    final result = await _methodChannel.invokeMethod<List<dynamic>>(
      'getCompletedDownloads',
    );
    return result
            ?.map(
              (item) => KinescopeDownloadInfo.fromMap(
                Map<String, dynamic>.from(item as Map),
              ),
            )
            .toList() ??
        const [];
  }

  Future<List<KinescopeDownloadInfo>> getAllDownloads() async {
    final result = await _methodChannel.invokeMethod<List<dynamic>>(
      'getAllDownloads',
    );
    return result
            ?.map(
              (item) => KinescopeDownloadInfo.fromMap(
                Map<String, dynamic>.from(item as Map),
              ),
            )
            .toList() ??
        const [];
  }

  Future<List<KinescopeCatalogVideo>> getVideoCatalog() async {
    final result = await _methodChannel.invokeMethod<List<dynamic>>(
      'getVideoCatalog',
    );
    return result
            ?.map(
              (item) => KinescopeCatalogVideo.fromMap(
                Map<String, dynamic>.from(item as Map),
              ),
            )
            .toList() ??
        const [];
  }

  Future<KinescopeDownloadInfo?> getDownload(String downloadId) async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>(
      'getDownload',
      downloadId,
    );
    if (result == null) {
      return null;
    }
    return KinescopeDownloadInfo.fromMap(result);
  }
}

class KinescopeDownloadInfo {
  final String contentId;
  final String? videoId;
  final String? title;
  final String? manifestUri;
  final String? mimeType;
  final String? state;
  final int? percent;
  final int? bytesDownloaded;
  final int? contentLength;
  final int? qualityHeight;
  final String? qualityLabel;

  const KinescopeDownloadInfo({
    required this.contentId,
    this.videoId,
    this.title,
    this.manifestUri,
    this.mimeType,
    this.state,
    this.percent,
    this.bytesDownloaded,
    this.contentLength,
    this.qualityHeight,
    this.qualityLabel,
  });

  bool get isCompleted => state == 'completed';

  bool get isDownloading =>
      state == 'downloading' || state == 'queued' || state == 'restarting';

  bool get isFailed => state == 'failed';

  /// Media3 marks cancelled / deleted entries as removing until the cache is cleared.
  /// Hide these from library UI so long cancels don't leave a stuck row.
  bool get isRemoving => state == 'removing';

  /// Whether this entry should appear in offline library lists.
  bool get isVisibleInLibrary => !isRemoving;

  String get progressLabel {
    final downloadedMb = ((bytesDownloaded ?? 0) / (1024 * 1024)).toStringAsFixed(1);
    final totalMb = contentLength != null && contentLength! > 0
        ? (contentLength! / (1024 * 1024)).toStringAsFixed(1)
        : null;
    final percentValue = percent ?? 0;
    final quality = qualityLabel;

    if (isFailed) {
      return 'Failed';
    }
    if (isCompleted) {
      return quality != null ? 'Completed ($quality)' : 'Completed';
    }
    if (state == 'queued' || state == 'restarting') {
      return quality != null
          ? 'Waiting to download ($quality)...'
          : 'Waiting to download...';
    }
    final prefix = quality != null ? '$quality · ' : '';
    if (totalMb != null) {
      return '${prefix}Downloading: ${downloadedMb}MB / ${totalMb}MB ($percentValue%)';
    }
    return '${prefix}Downloading: ${downloadedMb}MB ($percentValue%)';
  }

  factory KinescopeDownloadInfo.fromMap(Map<String, dynamic> map) {
    return KinescopeDownloadInfo(
      contentId: map['contentId'] as String? ?? '',
      videoId: map['videoId'] as String?,
      title: map['title'] as String?,
      manifestUri: map['uri'] as String? ?? map['manifestUri'] as String?,
      mimeType: map['mimeType'] as String?,
      state: map['state'] as String?,
      percent: _readInt(map['percent']),
      bytesDownloaded: _readInt(map['bytesDownloaded']),
      contentLength: _readInt(map['contentLength']),
      qualityHeight: _readInt(map['qualityHeight']),
      qualityLabel: map['qualityLabel'] as String?,
    );
  }

  static int? _readInt(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return null;
  }
}

/// A single downloadable HLS/DASH height for offline caching.
class KinescopeDownloadQuality {
  final int height;
  final int? width;
  final int? bitrate;
  final String label;
  final String? qualityName;

  const KinescopeDownloadQuality({
    required this.height,
    required this.label,
    this.width,
    this.bitrate,
    this.qualityName,
  });

  factory KinescopeDownloadQuality.fromMap(Map<String, dynamic> map) {
    return KinescopeDownloadQuality(
      height: _readInt(map['height']) ?? 0,
      width: _readInt(map['width']),
      bitrate: _readInt(map['bitrate']),
      label: map['label'] as String? ??
          map['qualityName'] as String? ??
          '${map['height']}p',
      qualityName: map['qualityName'] as String?,
    );
  }

  static int? _readInt(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return null;
  }
}

class KinescopeCatalogVideo {
  final String id;
  final String title;
  final double? durationSeconds;

  const KinescopeCatalogVideo({
    required this.id,
    required this.title,
    this.durationSeconds,
  });

  factory KinescopeCatalogVideo.fromMap(Map<String, dynamic> map) {
    final duration = map['duration'];
    return KinescopeCatalogVideo(
      id: map['id'] as String? ?? '',
      title: map['title'] as String? ?? 'Untitled',
      durationSeconds: duration is num ? duration.toDouble() : null,
    );
  }

  String get durationLabel {
    final seconds = durationSeconds;
    if (seconds == null || seconds <= 0) {
      return '';
    }
    final total = seconds.round();
    final minutes = total ~/ 60;
    final secs = total % 60;
    return '${minutes.toString().padLeft(1, '0')}:${secs.toString().padLeft(2, '0')}';
  }
}

KinescopePlayerStatus? statusFromAndroidEvent(String? value) {
  if (value == null) {
    return null;
  }
  return KinescopePlayerStatus.values.firstWhere(
    (status) => status.toString() == value,
    orElse: () => KinescopePlayerStatus.unknown,
  );
}

KinescopePlayerTimeUpdate? timeUpdateFromAndroidEvent(Map<dynamic, dynamic> event) {
  final currentTime = event['currentTime'];
  final percent = event['percent'];
  if (currentTime is! num || percent is! num) {
    return null;
  }
  return KinescopePlayerTimeUpdate(
    currentTime: currentTime.toDouble(),
    percent: percent.round(),
  );
}
