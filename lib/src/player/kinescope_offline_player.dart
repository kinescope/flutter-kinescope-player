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

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_kinescope_sdk/src/data/player_parameters.dart';
import 'package:flutter_kinescope_sdk/src/platform/kinescope_offline_download.dart';
import 'package:flutter_kinescope_sdk/src/platform/kinescope_player_options.dart';
import 'package:flutter_kinescope_sdk/src/player/kinescope_android_fullscreen.dart';
import 'package:flutter_kinescope_sdk/src/player/kinescope_android_platform_view.dart';

const _viewType = 'kinescope-offline-player-view';
const _methodChannel = MethodChannel('flutter_kinescope_sdk');
const _androidViewKey = ValueKey<String>('kinescope_offline_player_view');

/// Plays a video that was previously downloaded via [KinescopeOfflineDownload].
///
/// Uses the same native KinescopePlayerView UI as the online player.
/// Android only.
class KinescopeOfflinePlayer extends StatefulWidget {
  final String contentId;
  final PlayerParameters parameters;
  final double aspectRatio;
  final ValueChanged<bool>? onFullscreenChanged;

  const KinescopeOfflinePlayer({
    super.key,
    required this.contentId,
    this.parameters = const PlayerParameters(),
    this.aspectRatio = 16 / 9,
    this.onFullscreenChanged,
  });

  @override
  State<KinescopeOfflinePlayer> createState() => _KinescopeOfflinePlayerState();
}

class _KinescopeOfflinePlayerState extends State<KinescopeOfflinePlayer> {
  var _isFullscreen = false;
  var _platformViewHidden = false;

  @override
  void initState() {
    super.initState();
    _methodChannel.setMethodCallHandler(_handleNativeUiEvents);
  }

  @override
  void dispose() {
    if (_isFullscreen) {
      applyKinescopeAndroidFullscreenUi(fullscreen: false);
    }
    applyKinescopeAndroidPictureInPictureUi(pictureInPicture: false);
    _methodChannel.setMethodCallHandler(null);
    super.dispose();
  }

  Future<void> _handleNativeUiEvents(MethodCall call) async {
    if (call.method == 'onEnterFullscreen') {
      if (!mounted) {
        return;
      }
      widget.onFullscreenChanged?.call(true);
      widget.parameters.onEnterFullScreen?.call();
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
      widget.onFullscreenChanged?.call(false);
      widget.parameters.onExitFullScreen?.call();
    } else if (call.method == 'onEnterPictureInPicture') {
      widget.parameters.onEnterPictureInPicture?.call();
    } else if (call.method == 'onExitPictureInPicture') {
      widget.parameters.onExitPictureInPicture?.call();
    }
  }

  Future<bool> _interceptFullscreenPop() async {
    if (!_isFullscreen) {
      return false;
    }
    await _methodChannel.invokeMethod<void>(
      'exitOfflineFullscreen',
      widget.contentId,
    );
    return true;
  }

  Future<void> _preparePlatformViewPop() async {
    if (_isFullscreen) {
      applyKinescopeAndroidFullscreenUi(fullscreen: false);
      _isFullscreen = false;
    }
    await _methodChannel.invokeMethod<void>(
      'hideOfflinePlayerView',
      widget.contentId,
    );
    if (mounted) {
      setState(() => _platformViewHidden = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return KinescopeAndroidPopGuard(
      onInterceptPop: _interceptFullscreenPop,
      onPreparePop: _preparePlatformViewPop,
      child: _platformViewHidden
          ? kinescopeAndroidPlayerPlaceholder(aspectRatio: widget.aspectRatio)
          : kinescopeAndroidPlayerHost(
              isFullscreen: _isFullscreen,
              isPictureInPicture: false,
              aspectRatio: widget.aspectRatio,
              androidView: KinescopeAndroidPlatformView(
                key: _androidViewKey,
                viewType: _viewType,
                creationParams: {
                  'contentId': widget.contentId,
                  'options': kinescopePlayerOptionsMap(
                    widget.parameters,
                    autoplay: true,
                  ),
                },
                gestureRecognizers: kinescopeAndroidViewGestureRecognizers(),
              ),
            ),
    );
  }
}
