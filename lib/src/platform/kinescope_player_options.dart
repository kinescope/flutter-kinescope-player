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

Map<String, dynamic> kinescopePlayerOptionsMap(
  PlayerParameters parameters, {
  bool? autoplay,
}) {
  final options = <String, dynamic>{
    'autoplay': autoplay ?? parameters.autoplay ?? false,
    'muted': parameters.muted ?? false,
    'loop': parameters.loop ?? false,
    'controls': parameters.controls ?? true,
    'playsinline': parameters.playsinline ?? true,
  };

  if (parameters.texttrack != null) {
    options['texttrack'] = parameters.texttrack;
    options['showSubtitles'] = parameters.texttrack;
  }

  final drmAuthToken = parameters.drmAuthToken?.trim();
  if (drmAuthToken != null && drmAuthToken.isNotEmpty) {
    options['drmAuthToken'] = drmAuthToken;
  }

  if (parameters.showDefaultPoster != null) {
    options['showDefaultPoster'] = parameters.showDefaultPoster;
  }

  final referer = parameters.referer?.trim();
  if (referer != null && referer.isNotEmpty) {
    options['referer'] = referer;
  }

  return options;
}
