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

import 'dart:io';

import 'package:flutter_kinescope_sdk_example/pages/offline_download_page.dart';
import 'package:flutter_kinescope_sdk_example/pages/player_page.dart';
import 'package:flutter_kinescope_sdk_example/theme/demo_theme.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

class MenuPage extends StatelessWidget {
  const MenuPage({Key? key}) : super(key: key);

  bool get _offlineSupported =>
      !kIsWeb && (Platform.isAndroid || Platform.isIOS);

  void _openPlayer(
    BuildContext context, {
    String videoId = initialVideoId,
    String title = 'Player view',
  }) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PlayerPage(videoId: videoId, title: title),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DemoTheme.demoWhite,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  style: DemoTheme.accentButtonStyle(),
                  onPressed: () => _openPlayer(context),
                  child: const Text('Player view'),
                ),
                const SizedBox(height: 25),
                ElevatedButton(
                  style: DemoTheme.accentButtonStyle(),
                  onPressed: () => _openPlayer(
                    context,
                    videoId: subtitlesVideoId,
                    title: 'Subtitles',
                  ),
                  child: const Text('Subtitles'),
                ),
                const SizedBox(height: 25),
                ElevatedButton(
                  style: DemoTheme.accentButtonStyle(),
                  onPressed: () => _openPlayer(
                    context,
                    videoId: chaptersVideoId,
                    title: 'Chapters',
                  ),
                  child: const Text('Chapters'),
                ),
                const SizedBox(height: 25),
                ElevatedButton(
                  style: DemoTheme.accentButtonStyle(),
                  onPressed: _offlineSupported
                      ? () {
                          Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => const OfflineDownloadPage(),
                            ),
                          );
                        }
                      : null,
                  child: Text(
                    _offlineSupported
                        ? 'Offline viewing'
                        : 'Offline viewing (Android and iOS)',
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
