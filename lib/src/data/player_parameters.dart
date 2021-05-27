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

/// Initial Kinescope player parameters
@immutable
class PlayerParameters {
  /// Start the video automatically after loading the player.
  final bool? autoplay;

  /// Whether the sound is turned on at the time of initials.
  final bool? muted;

  const PlayerParameters({
    this.autoplay,
    this.muted,
  });
}
