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

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_kinescope_sdk/src/data/player_parameters.dart';
import 'package:flutter_kinescope_sdk/src/utils/uri_builder.dart';

/// A widget to play or stream Kinescope videos using the official embedded API
///
/// Using [KinescopePlayer] widget:
///
/// ```dart
/// KinescopePlayer(
///   yourVideoId,
///   parameters: const PlayerParameters(
///     autoplay: true,
///     muted: true,
///   ),
/// )
/// ```
class KinescopePlayer extends StatelessWidget {
  final String videoId;

  /// Initial [KinescopePlayer] parameters
  final PlayerParameters parameters;

  const KinescopePlayer(
    this.videoId, {
    Key? key,
    this.parameters = const PlayerParameters(),
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return InAppWebView(
      key: ValueKey(videoId),
      initialOptions: InAppWebViewGroupOptions(
        crossPlatform: InAppWebViewOptions(
          useShouldOverrideUrlLoading: true,
          mediaPlaybackRequiresUserGesture: false,
          transparentBackground: true,
          disableContextMenu: true,
          supportZoom: false,
          userAgent: Platform.isIOS
              ? 'Mozilla/5.0 (iPad; CPU iPhone OS 13_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)'
              : '',
        ),
        android: AndroidInAppWebViewOptions(
          useHybridComposition: true,
        ),
        ios: IOSInAppWebViewOptions(
          allowsInlineMediaPlayback: true,
          allowsBackForwardNavigationGestures: false,
        ),
      ),
      initialUrlRequest: URLRequest(
        url: UriBuilder.buildEmbeddedVideoUri(
          videoId: videoId,
          parameters: parameters,
        ),
      ),
      iosOnNavigationResponse: (_, __) async {
        return IOSNavigationResponseAction.CANCEL;
      },
      shouldOverrideUrlLoading: (_, __) async {
        return NavigationActionPolicy.CANCEL;
      },
    );
  }
}
