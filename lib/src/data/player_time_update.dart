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
/// see https://player.kinescope.io/latest/docs/iframe/IframeRegular.html
@immutable
class KinescopePlayerTimeUpdate {
  final double? currentTime;
  final int? percent;

  const KinescopePlayerTimeUpdate({
    required this.percent,
    required this.currentTime,
  });

  factory KinescopePlayerTimeUpdate.fromJson(Map<String, dynamic> data) {
    final currentTime = data['currentTime'];
    final percent = data['percent'];
    return KinescopePlayerTimeUpdate(
      currentTime: currentTime is double ? currentTime : 0,
      percent: percent is int ? percent : 0,
    );
  }
}
