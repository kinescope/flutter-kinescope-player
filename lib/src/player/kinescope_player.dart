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

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../kinescope_player_controller.dart';
import 'kinescope_player_device.dart';
import 'kinescope_player_web.dart'
    if (dart.library.io) 'kinescope_player_web_vain.dart';

final kIsMobile =
    defaultTargetPlatform == TargetPlatform.iOS ||
    defaultTargetPlatform == TargetPlatform.android;

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
class KinescopePlayer extends StatelessWidget {
  /// The [controller] for this player.
  final KinescopePlayerController controller;

  /// Aspect ratio for the player,
  /// by default it's 16 / 9.
  final double aspectRatio;

  /// A widget to play Kinescope videos.
  const KinescopePlayer({
    super.key,
    required this.controller,
    this.aspectRatio = 16 / 9,
  });

  @override
  Widget build(BuildContext context) {
    if (kIsMobile && !kIsWeb) {
      return KinescopePlayerDevice(controller: controller);
    } else if (kIsWeb) {
      return KinescopePlayerWeb(controller: controller);
    } else {
      return const SizedBox.expand();
    }
  }
}
