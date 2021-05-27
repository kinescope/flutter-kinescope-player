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
const _embed = 'embed';

class UriBuilder {
  static Uri buildEmbeddedVideoUri({
    required String videoId,
    required PlayerParameters parameters,
  }) =>
      Uri(
        scheme: _scheme,
        host: _kinescopeUri,
        pathSegments: [_embed, videoId],
        queryParameters: parametersToQueryParameters(parameters),
      );

  static Map<String, Object>? parametersToQueryParameters(
    PlayerParameters args,
  ) {
    final queryParameters = {
      if (args.autoplay != null) 'autoplay': '${args.autoplay}',
      if (args.muted != null) 'muted': '${args.muted}',
    };

    return queryParameters.isNotEmpty ? queryParameters : null;
  }
}
