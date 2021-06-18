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

import 'package:flutter_kinescope_sdk/src/data/player_parameters.dart';

const _scheme = 'https';
const _kinescopeUri = 'kinescope.io';

class UriBuilder {
  static Uri buildVideoUri({
    required String videoId,
  }) =>
      Uri(
        scheme: _scheme,
        host: _kinescopeUri,
        pathSegments: [videoId],
      );

  static Map<String, Object>? parametersToBehavior(
    PlayerParameters args,
  ) {
    final behaviorMap = {
      if (args.autofocus != null)   'autofocus':   '${args.autofocus}',
      if (args.autoplay != null)    'autoplay':    '${args.autoplay}',
      if (args.autopause != null)   'autopause':   '${args.autopause}',
      if (args.muted != null)       'muted':       '${args.muted}',
      if (args.loop != null)        'loop':        '${args.loop}',
      if (args.playsinline != null) 'playsinline': '${args.playsinline}',
      if (args.preload != null)     'preload':     '${args.preload}',
      if (args.texttrack != null)   'texttrack':   '${args.texttrack}',
      if (args.dnt != null)         'dnt':         '${args.dnt}',
      if (args.background != null)  'background':  '${args.background}',
      if (args.t != null)           't':           '${args.t}',
      if (args.transparent != null) 'transparent': '${args.transparent}',
      if (args.speedbtn != null)    'speedbtn':    '${args.speedbtn}',
      if (args.header != null)      'header':      '${args.header}',
      if (args.controls != null)    'controls':    '${args.controls}',
    };

    return behaviorMap.isNotEmpty ? behaviorMap : null;
  }
}
