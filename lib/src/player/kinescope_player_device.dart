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
import 'package:flutter/widgets.dart';

import 'package:webview_flutter_android/webview_flutter_android.dart';
import 'package:webview_flutter_platform_interface/webview_flutter_platform_interface.dart';
import 'package:webview_flutter_wkwebview/webview_flutter_wkwebview.dart';

import '../data/player_status.dart';
import '../kinescope_player_controller.dart';
import '../utils/uri_builder.dart';

const _scheme = 'https';
const _kinescopeUri = 'kinescope.io';

class KinescopePlayerDevice extends StatefulWidget {
  final KinescopePlayerController controller;

  /// Aspect ratio for the player,
  /// by default it's 16 / 9.
  final double aspectRatio;

  /// A widget to play Kinescope videos.
  const KinescopePlayerDevice({
    super.key,
    required this.controller,
    this.aspectRatio = 16 / 9,
  });

  @override
  _KinescopePlayerState createState() => _KinescopePlayerState();
}

class _KinescopePlayerState extends State<KinescopePlayerDevice> {
  late PlatformWebViewController controller;

  Completer<Duration>? getCurrentTimeCompleter;

  Completer<Duration>? getDurationCompleter;

  late String videoId;
  late String externalId;
  late String baseUrl;

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

    late final PlatformWebViewControllerCreationParams params;

    if (WebViewPlatform.instance is WebKitWebViewPlatform) {
      params = WebKitWebViewControllerCreationParams(
        allowsInlineMediaPlayback: true,
        mediaTypesRequiringUserAction: const <PlaybackMediaTypes>{},
      );
    } else {
      params = const PlatformWebViewControllerCreationParams();
    }

    final controller = PlatformWebViewController(params);

    // ignore: cascade_invocations
    controller
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setPlatformNavigationDelegate(
        PlatformNavigationDelegate(
          const PlatformNavigationDelegateCreationParams(),
        )
          ..setOnNavigationRequest((request) {
            if (request.url.contains(_kinescopeUri)) {
              debugPrint('blocking navigation to ${request.url}');
              return NavigationDecision.prevent;
            }
            debugPrint('allowing navigation to ${request.url}');
            return NavigationDecision.navigate;
          })
          ..setOnUrlChange(
            (change) {
              debugPrint('url change to ${change.url}');
            },
          ),
      )
      ..addJavaScriptChannel(
        JavaScriptChannelParams(
          name: 'Events',
          onMessageReceived: (message) {
            widget.controller.statusController.add(
              KinescopePlayerStatus.values.firstWhere(
                (value) => value.toString() == message.message,
                orElse: () => KinescopePlayerStatus.unknown,
              ),
            );
          },
        ),
      )
      ..addJavaScriptChannel(
        JavaScriptChannelParams(
          name: 'CurrentTime',
          onMessageReceived: (message) {
            final dynamic seconds = double.parse(message.message);
            if (seconds is num) {
              getCurrentTimeCompleter?.complete(
                Duration(milliseconds: (seconds * 1000).ceil()),
              );
            }
          },
        ),
      )
      ..addJavaScriptChannel(
        JavaScriptChannelParams(
          name: 'Duration',
          onMessageReceived: (message) {
            final dynamic seconds = double.parse(message.message);
            if (seconds is num) {
              getDurationCompleter?.complete(
                Duration(milliseconds: (seconds * 1000).ceil()),
              );
            }
          },
        ),
      )
      ..setOnPlatformPermissionRequest(
        (request) {
          debugPrint(
            'requesting permissions for ${request.types.map((type) => type.name)}',
          );
          request.grant();
        },
      )
      ..setOnConsoleMessage((message) {
        debugPrint('js: ${message.message}');
      })
      ..setUserAgent(getUserArgent())
      ..loadHtmlString(_player, baseUrl: baseUrl);

    if (controller is AndroidWebViewController) {
      AndroidWebViewController.enableDebugging(true);
      controller.setMediaPlaybackRequiresUserGesture(false);
    }

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

    this.controller = controller;
  }

  @override
  void dispose() {
    widget.controller.dispose();
    super.dispose();
  }

  void _proxyLoadVideo(String videoId) {
    controller.runJavaScript(
      'loadVideo("$videoId");',
    );
  }

  void _proxyPlay() {
    controller.runJavaScript('play();');
  }

  void _proxyPause() {
    controller.runJavaScript('pause();');
  }

  void _proxyStop() {
    controller.runJavaScript('stop();');
  }

  Future<Duration> _proxyGetCurrentTime() async {
    getCurrentTimeCompleter = Completer<Duration>();

    await controller.runJavaScript(
      'getCurrentTime();',
    );

    final time = await getCurrentTimeCompleter?.future;

    return time ?? Duration.zero;
  }

  Future<Duration> _proxyGetDuration() async {
    getDurationCompleter = Completer<Duration>();

    await controller.runJavaScript(
      'getDuration();',
    );

    final duration = await getDurationCompleter?.future;

    return duration ?? Duration.zero;
  }

  void _proxySeekTo(Duration duration) {
    controller.runJavaScript(
      'seekTo(${duration.inSeconds});',
    );
  }

  void _proxySetVolume(double value) {
    controller.runJavaScript('setVolume($value);');
  }

  void _proxyMute() {
    controller.runJavaScript('mute();');
  }

  void _proxyUnmute() {
    controller.runJavaScript('unmute();');
  }

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: widget.aspectRatio,
      child: PlatformWebViewWidget(
        PlatformWebViewWidgetCreationParams(controller: controller),
      ).build(context),
    );
  }

  String? getUserArgent() {
    return (Platform.isIOS
        ? 'Mozilla/5.0 (iPad; CPU iPhone OS 13_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) KinescopePlayerFlutter/10'
        : 'Mozilla/5.0 (Android 9.0; Mobile; rv:59.0) Gecko/59.0 Firefox/59.0 KinescopePlayerFlutter/0.1.10');
  }

  // ignore: member-ordering-extended
  String get _player => '''
<!DOCTYPE html>
<html>

<head>
    <meta charset="utf-8" />
    <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>
    <style>
        html, body {
            padding: 0;
            margin: 0;
            width: 100%;
            height: 100%;
        }
        #player {
            position: fixed;
            width: 100%;
            height: 100%;
            left: 0;
            top: 0;
        }
    </style>

    <script>
        window.addEventListener("flutterInAppWebViewPlatformReady", function (event) {
            Events.postMessage('ready');
        });

        let kinescopePlayerFactory = null;

        let kinescopePlayer = null;

        let initialVideoUri = '${UriBuilder.buildVideoUri(videoId: videoId)}';

        function onKinescopeIframeAPIReady(playerFactory) {
            kinescopePlayerFactory = playerFactory;

            loadVideo(initialVideoUri);
        }

        function loadVideo(videoUri) {
            if (kinescopePlayer != null) {
                kinescopePlayer.destroy();
                kinescopePlayer = null;
            }

            if (kinescopePlayerFactory != null) {
                var devElement = document.createElement("div");
                devElement.id = "player";
                document.body.append(devElement);

                kinescopePlayerFactory
                    .create('player', {
                        url: videoUri,
                        size: { width: '100%', height: '100%' },
                        settings: {
                          externalId: '$externalId'
                        },
                        behaviour: ${UriBuilder.parametersToBehavior(widget.controller.parameters)},
                        ui: ${UriBuilder.parametersToUI(widget.controller.parameters)}
                    })
                    .then(function (player) {
                        kinescopePlayer = player;
                        Events.postMessage('init');

                        player.once(player.Events.Ready, function (event) {
                          var time = ${UriBuilder.parameterSeekTo(widget.controller.parameters)};
                          if(time > 0 || time === 0) {
                             event.target.seekTo(time);
                          }
                        });
                        player.on(player.Events.Ready, function (event) { Events.postMessage('ready'); });
                        player.on(player.Events.Playing, function (event) { Events.postMessage('playing'); });
                        player.on(player.Events.Waiting, function (event) { Events.postMessage('waiting'); });
                        player.on(player.Events.Pause, function (event) { Events.postMessage( 'pause'); });
                        player.on(player.Events.Ended, function (event) { Events.postMessage('ended'); });
                    });
            }
        }

        function play() {
            if (kinescopePlayer != null)
              kinescopePlayer.play();
        }

        function pause() {
            if (kinescopePlayer != null)
              kinescopePlayer.pause();
        }

        function stop() {
            if (kinescopePlayer != null)
              kinescopePlayer.stop();
        }

        function getCurrentTime() {
            if (kinescopePlayer != null)
              return kinescopePlayer.getCurrentTime();
        }

        function seekTo(seconds) {
            if (kinescopePlayer != null)
              kinescopePlayer.seekTo(seconds);
        }

        function getCurrentTime() {
            if (kinescopePlayer != null)
              kinescopePlayer.getCurrentTime().then((value) => {
                CurrentTime.postMessage(value);
              });
        }

        function getDuration() {
            if (kinescopePlayer != null)
              kinescopePlayer.getDuration().then((value) => {
                Duration.postMessage(value);
              });
        }

        function setVolume(value) {
            if (kinescopePlayer != null)
              kinescopePlayer.setVolume(value);
        }

        function mute() {
            if (kinescopePlayer != null)
              kinescopePlayer.mute();
        }

        function unmute() {
            if (kinescopePlayer != null)
              kinescopePlayer.unmute();
        }
    </script>
</head>

<body>
    <script>
        var tag = document.createElement('script');

        tag.src = 'https://player.kinescope.io/latest/iframe.player.js';
        var firstScriptTag = document.getElementsByTagName('script')[0];
        firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
    </script>
</body>

</html>
''';
}
