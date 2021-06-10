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
      if (args.autoplay != null) 'autoPlay': '${args.autoplay}',
      if (args.muted != null) 'muted': '${args.muted}',
      if (args.loop != null) 'loop': '${args.loop}',
    };

    return behaviorMap.isNotEmpty ? behaviorMap : null;
  }
}
