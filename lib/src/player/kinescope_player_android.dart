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
import 'package:flutter/widgets.dart';
import 'package:flutter_kinescope_sdk/src/kinescope_player_controller.dart';
import 'package:flutter_kinescope_sdk/src/platform/kinescope_android_bridge.dart';
import 'package:flutter_kinescope_sdk/src/platform/kinescope_player_options.dart';
import 'package:flutter_kinescope_sdk/src/player/kinescope_android_fullscreen.dart';
import 'package:flutter_kinescope_sdk/src/player/kinescope_android_platform_view.dart';

const _viewType = 'kinescope-player-view';
const _androidViewKey = ValueKey<String>('kinescope_player_view');

class KinescopePlayerAndroid extends StatefulWidget {
  final KinescopePlayerController controller;
  final double aspectRatio;

  const KinescopePlayerAndroid({
    super.key,
    required this.controller,
    this.aspectRatio = 16 / 9,
  });

  @override
  State<KinescopePlayerAndroid> createState() => _KinescopePlayerAndroidState();
}

class _KinescopePlayerAndroidState extends State<KinescopePlayerAndroid> {
  final _bridge = KinescopeAndroidBridge.instance;
  int? _playerId;
  var _isFullscreen = false;
  StreamSubscription<Map<dynamic, dynamic>>? _playerEventsSubscription;
  static const _methodChannel = MethodChannel('flutter_kinescope_sdk');

  @override
  void initState() {
    super.initState();
    _initializePlayer();
  }

  Future<void> _initializePlayer() async {
    final parameters = widget.controller.parameters;
    final playerId = await _bridge.createPlayer(
      kinescopePlayerOptionsMap(parameters),
    );

    if (!mounted) {
      await _bridge.disposePlayer(playerId);
      return;
    }

    setState(() => _playerId = playerId);

    _playerEventsSubscription = _bridge.playerEvents.listen(_handlePlayerEvent);
    _methodChannel.setMethodCallHandler(_handleNativeUiEvents);

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

    await _bridge.loadVideo(playerId, widget.controller.videoId);
  }

  Future<void> _handleNativeUiEvents(MethodCall call) async {
    final parameters = widget.controller.parameters;
    if (call.method == 'onEnterFullscreen') {
      if (!mounted) {
        return;
      }
      parameters.onEnterFullScreen?.call();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) {
          return;
        }
        setState(() => _isFullscreen = true);
        applyKinescopeAndroidFullscreenUi(fullscreen: true);
      });
    } else if (call.method == 'onExitFullscreen') {
      if (!mounted) {
        return;
      }
      setState(() => _isFullscreen = false);
      applyKinescopeAndroidFullscreenUi(fullscreen: false);
      parameters.onExitFullScreen?.call();
    } else if (call.method == 'onEnterPictureInPicture') {
      parameters.onEnterPictureInPicture?.call();
    } else if (call.method == 'onExitPictureInPicture') {
      parameters.onExitPictureInPicture?.call();
    }
  }

  void _handlePlayerEvent(Map<dynamic, dynamic> event) {
    final eventPlayerId = event['playerId'];
    if (eventPlayerId != _playerId) {
      return;
    }

    final type = event['type'];
    if (type == 'status') {
      final status = statusFromAndroidEvent(event['status'] as String?);
      if (status != null && !widget.controller.statusController.isClosed) {
        widget.controller.statusController.add(status);
      }
      return;
    }

    if (type == 'timeUpdate') {
      final update = timeUpdateFromAndroidEvent(event);
      if (update != null && !widget.controller.timeUpdateController.isClosed) {
        widget.controller.timeUpdateController.add(update);
      }
    }
  }

  void _proxyLoadVideo(String videoId) {
    final playerId = _playerId;
    if (playerId == null) {
      return;
    }
    _bridge.loadVideo(playerId, videoId);
  }

  void _proxyPlay() {
    final playerId = _playerId;
    if (playerId == null) {
      return;
    }
    _bridge.play(playerId);
  }

  void _proxyPause() {
    final playerId = _playerId;
    if (playerId == null) {
      return;
    }
    _bridge.pause(playerId);
  }

  void _proxyStop() {
    final playerId = _playerId;
    if (playerId == null) {
      return;
    }
    _bridge.stop(playerId);
  }

  Future<Duration> _proxyGetCurrentTime() {
    final playerId = _playerId;
    if (playerId == null) {
      return Future.value(Duration.zero);
    }
    return _bridge.getCurrentTime(playerId);
  }

  Future<Duration> _proxyGetDuration() {
    final playerId = _playerId;
    if (playerId == null) {
      return Future.value(Duration.zero);
    }
    return _bridge.getDuration(playerId);
  }

  void _proxySeekTo(Duration duration) {
    final playerId = _playerId;
    if (playerId == null) {
      return;
    }
    _bridge.seekTo(playerId, duration);
  }

  void _proxySetVolume(double value) {
    final playerId = _playerId;
    if (playerId == null) {
      return;
    }
    _bridge.setVolume(playerId, value);
  }

  void _proxyMute() {
    final playerId = _playerId;
    if (playerId == null) {
      return;
    }
    _bridge.mute(playerId);
  }

  void _proxyUnmute() {
    final playerId = _playerId;
    if (playerId == null) {
      return;
    }
    _bridge.unmute(playerId);
  }

  Future<bool> _interceptFullscreenPop() async {
    if (!_isFullscreen) {
      return false;
    }
    final playerId = _playerId;
    if (playerId != null) {
      await _bridge.exitFullscreen(playerId);
    }
    return true;
  }

  Future<void> _preparePlatformViewPop() async {
    if (_isFullscreen) {
      applyKinescopeAndroidFullscreenUi(fullscreen: false);
      _isFullscreen = false;
    }
    final playerId = _playerId;
    if (playerId != null) {
      await Future.wait([
        _bridge.pause(playerId),
        _bridge.hidePlayerView(playerId),
      ]);
    }
  }

  @override
  void dispose() {
    if (_isFullscreen) {
      applyKinescopeAndroidFullscreenUi(fullscreen: false);
    }
    applyKinescopeAndroidPictureInPictureUi(pictureInPicture: false);
    _playerEventsSubscription?.cancel();
    _methodChannel.setMethodCallHandler(null);
    final playerId = _playerId;
    if (playerId != null) {
      _bridge.disposePlayer(playerId);
    }
    widget.controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return KinescopeAndroidPopGuard(
      onInterceptPop: _interceptFullscreenPop,
      onPreparePop: _preparePlatformViewPop,
      child: _buildPlayerBody(context),
    );
  }

  Widget _buildPlayerBody(BuildContext context) {
    final playerId = _playerId;
    if (playerId == null) {
      return kinescopeAndroidPlayerPlaceholder(aspectRatio: widget.aspectRatio);
    }

    final androidView = KinescopeAndroidPlatformView(
      key: _androidViewKey,
      viewType: _viewType,
      creationParams: {'playerId': playerId},
      gestureRecognizers: kinescopeAndroidViewGestureRecognizers(),
    );

    return kinescopeAndroidPlayerHost(
      isFullscreen: _isFullscreen,
      isPictureInPicture: false,
      aspectRatio: widget.aspectRatio,
      androidView: androidView,
    );
  }
}
