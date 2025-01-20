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

/// Current status of the player.
@immutable
class KinescopePlayerStatus {
  /// Denotes [KinescopePlayerStatus] when player is not loaded with video.
  static const KinescopePlayerStatus unknown =
      KinescopePlayerStatus._('unknown');

  /// Denotes [KinescopePlayerStatus] when player is init to call js.
  static const KinescopePlayerStatus init = KinescopePlayerStatus._('init');

  /// Denotes [KinescopePlayerStatus] when player is ready to play video.
  static const KinescopePlayerStatus ready = KinescopePlayerStatus._('ready');

  /// Denotes [KinescopePlayerStatus] when player is playing video.
  static const KinescopePlayerStatus playing =
      KinescopePlayerStatus._('playing');

  /// Denotes [KinescopePlayerStatus] when player is buffering bytes from the internet.
  static const KinescopePlayerStatus waiting =
      KinescopePlayerStatus._('waiting');

  /// Denotes [KinescopePlayerStatus] when player is paused.
  static const KinescopePlayerStatus pause = KinescopePlayerStatus._('pause');

  /// Denotes [KinescopePlayerStatus] when player has ended playing a video.
  static const KinescopePlayerStatus ended = KinescopePlayerStatus._('ended');

  /// List of available values of player status
  static Iterable<KinescopePlayerStatus> values = [
    KinescopePlayerStatus.init,
    KinescopePlayerStatus.ready,
    KinescopePlayerStatus.playing,
    KinescopePlayerStatus.waiting,
    KinescopePlayerStatus.pause,
    KinescopePlayerStatus.ended,
    KinescopePlayerStatus.unknown,
  ];

  final String _value;

  const KinescopePlayerStatus._(this._value);

  @override
  String toString() => _value;
}
