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

import 'package:flutter/widgets.dart';
import '../kinescope_player_controller.dart';

class KinescopePlayerWeb extends StatefulWidget {
  final KinescopePlayerController controller;

  final double aspectRatio;

  const KinescopePlayerWeb({
    super.key,
    required this.controller,
    this.aspectRatio = 16 / 9,
  });

  @override
  _KinescopePlayerWebState createState() => _KinescopePlayerWebState();
}

class _KinescopePlayerWebState extends State<KinescopePlayerWeb> {
  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: widget.aspectRatio,
      child: const SizedBox.expand(),
    );
  }
}
