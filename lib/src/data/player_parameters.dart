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
class PlayerParameters {
  final bool? autofocus;
  final bool? autoplay;
  final bool? autopause;
  final bool? muted;
  final bool? loop;
  final bool? playsinline;
  final bool? preload;
  final bool? texttrack;
  final bool? dnt;
  final bool? background;
  final int? t;
  final bool? transparent;
  final bool? speedbtn;
  final bool? header;
  final bool? controls;
  final bool? disableFiles;
  final WatermarkParameters watermark;

  /// Sets the user-agent.
  final String? userAgent;
  final String? externalId;
  final String? baseUrl;

  const PlayerParameters({
    this.autofocus,
    this.autoplay,
    this.autopause,
    this.muted,
    this.loop,
    this.playsinline,
    this.preload,
    this.texttrack,
    this.dnt,
    this.background,
    this.t,
    this.transparent,
    this.speedbtn,
    this.header,
    this.controls,
    this.userAgent,
    this.externalId,
    this.baseUrl,
    this.disableFiles,
    this.watermark = const WatermarkParameters(),
  });
}

class WatermarkParameters {
  final String? mode;
  final String? text;
  const WatermarkParameters({this.mode, this.text});
}
