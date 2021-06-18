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
import 'package:flutter_kinescope_sdk/src/utils/uri_builder.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('UriBuilder', () {
    group('buildEmbeddedVideoUri', () {
      test('builds valid Uri', () {
        final uri = UriBuilder.buildVideoUri(videoId: 'test');

        expect(
          uri.toString(),
          equals('https://kinescope.io/test'),
        );
      });
    });

    group('parametersToBehavior', () {
      test('returns null on empty parameters', () {
        const params = PlayerParameters();

        expect(UriBuilder.parametersToBehavior(params), isNull);
      });
      test('returns valid map', () {
        const params = PlayerParameters(
          autoplay: true,
          muted: true,
          loop: true,
        );

        expect(
          UriBuilder.parametersToBehavior(params),
          equals(
            {'autoplay': 'true', 'muted': 'true', 'loop': 'true'},
          ),
        );
      });
    });
  });
}
