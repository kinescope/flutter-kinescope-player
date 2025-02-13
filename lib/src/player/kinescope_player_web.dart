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
import 'dart:convert';
import 'dart:js_interop';
import 'dart:ui_web' as ui_web;

import 'package:flutter/widgets.dart';
import 'package:web/web.dart' as web;

import '../data/player_status.dart';
import '../data/player_time_update.dart';
import '../kinescope_player_controller.dart';
import '../utils/uri_builder.dart';

const _scheme = 'https';
const _kinescopeUri = 'kinescope.io';

class KinescopePlayerWeb extends StatefulWidget {
  /// The [controller] for this player.
  final KinescopePlayerController controller;

  /// Aspect ratio for the player,
  /// by default it's 16 / 9.
  final double aspectRatio;

  /// A widget to play Kinescope videos.
  const KinescopePlayerWeb({
    super.key,
    required this.controller,
    this.aspectRatio = 16 / 9,
  });

  @override
  _KinescopePlayerWebState createState() => _KinescopePlayerWebState();
}

class _KinescopePlayerWebState extends State<KinescopePlayerWeb> {
  Completer<Duration>? getCurrentTimeCompleter;

  Completer<Duration>? getDurationCompleter;

  late String videoId;
  late String externalId;
  late String baseUrl;

  static int _nextIFrameId = 0;

  web.HTMLIFrameElement get iFrame => _iFrame;

  late final web.HTMLIFrameElement _iFrame;

  @override
  void initState() {
    super.initState();
    videoId = widget.controller.videoId;
    externalId = widget.controller.parameters.externalId ?? '';
    baseUrl = widget.controller.parameters.baseUrl ??
        Uri(
          scheme: _scheme,
          host: _kinescopeUri,
        ).toString();

    _iFrame = web.HTMLIFrameElement()
      ..id = 'webView${_nextIFrameId++}'
      ..allow =
          'autoplay; fullscreen; picture-in-picture; encrypted-media; gyroscope; accelerometer; clipboard-write;'
      ..sandbox.value =
          'allow-forms allow-modals allow-popups allow-presentation allow-same-origin allow-scripts allow-downloads allow-pointer-lock'
      ..style.width = '100%'
      ..style.height = '100%'
      ..style.border = 'none';

    ui_web.platformViewRegistry.registerViewFactory(
      _iFrame.id,
      (int viewId) => _iFrame,
    );

    _iFrame.src =
        '/assets/packages/flutter_kinescope_sdk/assets/web/web_support.html';

    web.window.addEventListener('message', handleMessage.toJS);

    widget.controller.controllerProxy
      ..setLoadVideoCallback(_proxyLoadVideo)
      ..setPlayCallback(_proxyPlay)
      ..setPauseCallback(_proxyPause)
      ..setStopCallback(_proxyStop)
      ..setGetCurrentTimeCallback(_proxyGetCurrentTime)
      ..setGetDurationCallback(_proxyGetDuration)
      ..setSeekToCallback(_proxySeekTo)
      ..setSetVolumeCallback(_proxySetVolume)
      ..setMuteCallback(_proxyMute)
      ..setUnuteCallback(_proxyUnmute);
  }

  void _proxyLoadVideo(String videoId) {
    final _externalId = '$externalId';
    final _behaviour = jsonEncode(
        UriBuilder.parametersToBehavior(widget.controller.parameters));
    final _ui = jsonEncode(
        UriBuilder.parametersToUI(widget.controller.parameters, escape: false));
    final _time = UriBuilder.parameterSeekTo(widget.controller.parameters);

    postMessage({
      'action': 'flutter_player_load_video',
      'value': videoId,
      'externalId': _externalId,
      'behaviour': _behaviour,
      'ui': _ui,
      'time': _time,
    });
  }

  void _proxyPlay() {
    postMessage({'action': 'flutter_player_play'});
  }

  void _proxyPause() {
    postMessage({'action': 'flutter_player_pause'});
  }

  void _proxyStop() {
    postMessage({'action': 'flutter_player_stop'});
  }

  Future<Duration> _proxyGetCurrentTime() async {
    getCurrentTimeCompleter = Completer<Duration>();
    postMessage({'action': 'flutter_call_get_current_time'});
    final time = await getCurrentTimeCompleter?.future;
    return time ?? Duration.zero;
  }

  Future<Duration> _proxyGetDuration() async {
    getDurationCompleter = Completer<Duration>();
    postMessage({'action': 'flutter_call_get_duration'});
    final duration = await getDurationCompleter?.future;
    return duration ?? Duration.zero;
  }

  void _proxySeekTo(Duration duration) {
    postMessage(
        {'action': 'flutter_player_seek_to', 'value': duration.inSeconds});
  }

  void _proxySetVolume(double value) {
    postMessage({'action': 'flutter_player_volume', 'value': value});
  }

  void _proxyMute() {
    postMessage({'action': 'flutter_player_mute'});
  }

  void _proxyUnmute() {
    postMessage({'action': 'flutter_player_unmute'});
  }

  void postMessage(Map<String, dynamic> data) {
    if (!iFrame.contentWindow.isNull) {
      iFrame.contentWindow?.postMessage(json.encode(data).toJS);
    }
  }

  void handleMessage(web.Event e) {
    if (e is web.MessageEvent) {
      final parsedJson = jsonDecode(e.data.toString());
      final pm = WebPostMessage.fromJson(parsedJson as Map<String, dynamic>);

      if (pm.action == 'flutter_player_event') {
        if (videoId.isNotEmpty) {
          _proxyLoadVideo('${UriBuilder.buildVideoUri(videoId: videoId)}');
        }
      }

      if (pm.action == 'flutter_action_event') {
        widget.controller.statusController.add(
          KinescopePlayerStatus.values.firstWhere(
            (value) => value.toString() == pm.value,
            orElse: () => KinescopePlayerStatus.unknown,
          ),
        );
      }

      if (pm.action == 'flutter_action_current_time') {
        final dynamic seconds = double.parse(pm.value.toString());
        if (seconds is num) {
          getCurrentTimeCompleter?.complete(
            Duration(milliseconds: (seconds * 1000).ceil()),
          );
        }
      }

      if (pm.action == 'flutter_action_duration') {
        final dynamic seconds = double.parse(pm.value.toString());
        if (seconds is num) {
          getDurationCompleter?.complete(
            Duration(milliseconds: (seconds * 1000).ceil()),
          );
        }
      }

      if (pm.action == 'flutter_action_fullscreen') {
        final dynamic isFullscreen = bool.parse(pm.value.toString());
        if (isFullscreen is bool) {
          if (isFullscreen &&
              widget.controller.parameters.onEnterFullScreen != null) {
            widget.controller.parameters.onEnterFullScreen!();
          }
          if (!isFullscreen &&
              widget.controller.parameters.onExitFullScreen != null) {
            widget.controller.parameters.onExitFullScreen!();
          }
        }
      }

      if (pm.action == 'flutter_action_time_update') {
        try {
          widget.controller.timeUpdateController.add(
            KinescopePlayerTimeUpdate.fromJson(
                pm.value as Map<String, dynamic>),
          );
        } catch (e) {
          debugPrint('Error decoding time update data: $e');
        }
      }
    }
  }

  @override
  void dispose() {
    web.window.removeEventListener('message', handleMessage.toJS);
    widget.controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: widget.aspectRatio,
      child: HtmlElementView(viewType: _iFrame.id),
    );
  }
}

class WebPostMessage {
  WebPostMessage({required this.action, required this.value});
  final String action;
  final dynamic value;

  factory WebPostMessage.fromJson(Map<String, dynamic> data) {
    final action = data['action'] as String;
    final value = data['value'] as dynamic;
    return WebPostMessage(action: action, value: value);
  }
}
