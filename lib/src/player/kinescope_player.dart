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

import 'dart:io';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/widgets.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../data/player_status.dart';
import '../kinescope_player_controller.dart';
import '../utils/uri_builder.dart';

/// A widget to play or stream Kinescope videos using the official embedded API
///
/// Using [KinescopePlayer] widget:
///
/// ```dart
/// KinescopePlayer(
///   controller: KinescopePlayerController(
///     yourVideoId,
///     parameters: const PlayerParameters(
///       autoplay: true,
///       muted: true,
///       loop: true,
///     ),
///   ),
///   aspectRatio: 16 / 10,
/// )
/// ```
class KinescopePlayer extends StatefulWidget {
  /// The [controller] for this player.
  final KinescopePlayerController controller;

  /// Aspect ratio for the player,
  /// by default it's 16 / 9.
  final double aspectRatio;

  /// A widget to play Kinescope videos.
  const KinescopePlayer({
    Key? key,
    required this.controller,
    this.aspectRatio = 16 / 9,
  }) : super(key: key);

  @override
  _KinescopePlayerState createState() => _KinescopePlayerState();
}

class _KinescopePlayerState extends State<KinescopePlayer> {
  late String videoId;
  late String externalId;
  late String baseUrl;

  @override
  void initState() {
    super.initState();
    videoId = widget.controller.videoId;
    externalId = widget.controller.parameters.externalId ?? '';
    baseUrl = widget.controller.parameters.baseUrl ?? 'https://kinescope.io';
  }

  @override
  void dispose() {
    widget.controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: widget.aspectRatio,
      child: InAppWebView(
        onWebViewCreated: (controller) {
          widget.controller.webViewController = controller;
          controller
            ..addJavaScriptHandler(
              handlerName: 'events',
              callback: (args) {
                final event = (args.first as String).toLowerCase();

                widget.controller.statusController.add(
                  KinescopePlayerStatus.values.firstWhere(
                    (value) => value.toString() == event,
                    orElse: () => KinescopePlayerStatus.unknown,
                  ),
                );
              },
            )
            ..addJavaScriptHandler(
              handlerName: 'getCurrentTimeResult',
              callback: (args) {
                final dynamic seconds = args.first;
                if (seconds is num) {
                  widget.controller.getCurrentTimeCompleter?.complete(
                    Duration(milliseconds: (seconds * 1000).ceil()),
                  );
                }
              },
            )
            ..addJavaScriptHandler(
              handlerName: 'getDurationResult',
              callback: (args) {
                final dynamic seconds = args.first;
                if (seconds is num) {
                  widget.controller.getDurationCompleter?.complete(
                    Duration(milliseconds: (seconds * 1000).ceil()),
                  );
                }
              },
            );
        },
        initialSettings: InAppWebViewSettings(
          useShouldOverrideUrlLoading: true,
          mediaPlaybackRequiresUserGesture: false,
          transparentBackground: true,
          disableContextMenu: true,
          supportZoom: false,
          userAgent: widget.controller.parameters.userAgent ?? getUserArgent(),
          allowsInlineMediaPlayback: true,
          allowsBackForwardNavigationGestures: false,
        ),
        onPermissionRequest: (controller, permissionRequest) async {
          return PermissionResponse(
            resources: [PermissionResourceType.PROTECTED_MEDIA_ID],
            action: PermissionResponseAction.GRANT,
          );
        },
        onNavigationResponse: (_, __) async {
          return NavigationResponseAction.CANCEL;
        },
        shouldOverrideUrlLoading: (_, __) async => Platform.isIOS
            ? NavigationActionPolicy.ALLOW
            : NavigationActionPolicy.CANCEL,
        onConsoleMessage: (_, message) {
          debugPrint('js: ${message.message}');
        },
        initialData: InAppWebViewInitialData(
          data: _player,
          baseUrl: WebUri(baseUrl),
        ),
      ),
    );
  }

  String? getUserArgent() {
    if (kIsWeb) {
      return null;
    }

    return (Platform.isIOS
        ? 'Mozilla/5.0 (iPad; CPU iPhone OS 13_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) KinescopePlayerFlutter/0.1.6'
        : 'Mozilla/5.0 (Android 9.0; Mobile; rv:59.0) Gecko/59.0 Firefox/59.0 KinescopePlayerFlutter/0.1.6');
  }

  // ignore: member-ordering-extended
  String get _player => '''
<!DOCTYPE html>
<html>

<head>
    <meta charset="utf-8" />
    <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>
    <style>
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
            window.flutter_inappwebview.callHandler('events', 'ready');
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
                        player.once(player.Events.Ready, function (event) {
                          event.target.seekTo(${UriBuilder.parameterSeekTo(widget.controller.parameters)});
                        });
                        player.on(player.Events.Ready, function (event) { window.flutter_inappwebview.callHandler('events', 'ready'); });
                        player.on(player.Events.Playing, function (event) { window.flutter_inappwebview.callHandler('events', 'playing'); });
                        player.on(player.Events.Waiting, function (event) { window.flutter_inappwebview.callHandler('events', 'waiting'); });
                        player.on(player.Events.Pause, function (event) { window.flutter_inappwebview.callHandler('events', 'pause'); });
                        player.on(player.Events.Ended, function (event) { window.flutter_inappwebview.callHandler('events', 'ended'); });
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
                window.flutter_inappwebview.callHandler('getCurrentTimeResult', value);
              });
        }

        function getDuration() {
            if (kinescopePlayer != null)
              kinescopePlayer.getDuration().then((value) => {
                window.flutter_inappwebview.callHandler('getDurationResult', value);
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
