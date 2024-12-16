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

import 'package:flutter/material.dart';
import 'package:flutter_kinescope_sdk/flutter_kinescope_sdk.dart';

const _initialVideoId = 'sEsxJQ7Hi4QLWwbmZEFfgz';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Kinescope SDK example',
      theme: ThemeData(
        primarySwatch: Colors.purple,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({Key? key}) : super(key: key);

  @override
  _HomePageState createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _textEditingController = TextEditingController(text: _initialVideoId);
  late KinescopePlayerController _kinescopeController;

  @override
  void initState() {
    super.initState();
    _kinescopeController = KinescopePlayerController(
      _initialVideoId,
      parameters: const PlayerParameters(
        // autoplay: true,
        // header: false,
        // muted: true,
        // loop: true,
        // background: true,
        // disableFiles: true,
        watermark: WatermarkParameters(
          mode: 'random',
          text: 'water-text',
        ),
        // t: 20,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<KinescopePlayerStatus>(
      stream: _kinescopeController.status,
      initialData: KinescopePlayerStatus.unknown,
      builder: (context, snapshot) {
        final isUnknown = snapshot.data == KinescopePlayerStatus.unknown;

        return Scaffold(
          appBar: AppBar(
            title: const Text('Kinescope SDK example'),
          ),
          body: Column(
            children: [
              SizedBox(
                height: MediaQuery.of(context).size.height / 4,
                child: KinescopePlayer(
                  controller: _kinescopeController,
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: TextField(
                  controller: _textEditingController,
                  decoration: const InputDecoration(labelText: 'Video ID'),
                  onSubmitted: (id) {
                    _kinescopeController.load(id);
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      onPressed: !isUnknown ? _play : null,
                      icon: const Icon(Icons.play_arrow),
                    ),
                    IconButton(
                      onPressed: !isUnknown ? _pause : null,
                      icon: const Icon(Icons.pause),
                    ),
                    IconButton(
                      onPressed: !isUnknown ? _stop : null,
                      icon: const Icon(Icons.stop),
                    ),
                    IconButton(
                      onPressed: !isUnknown ? _unmute : null,
                      icon: const Icon(Icons.volume_up),
                    ),
                    IconButton(
                      onPressed: !isUnknown ? _mute : null,
                      icon: const Icon(Icons.volume_off),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Column(
                      children: [
                        IconButton(
                          onPressed: !isUnknown ? _rewindBackward : null,
                          icon: const Icon(Icons.fast_rewind),
                          tooltip: 'rewind 10 seconds backward',
                        ),
                        const Text('- 10 sec'),
                      ],
                    ),
                    TextButton(
                      onPressed: !isUnknown ? _seekToCenter : null,
                      child: const Text('seek to center'),
                    ),
                    Column(
                      children: [
                        IconButton(
                          onPressed: !isUnknown ? _rewindForward : null,
                          icon: const RotatedBox(
                            quarterTurns: 2,
                            child: Icon(Icons.fast_rewind),
                          ),
                          tooltip: 'rewind 10 seconds forward',
                        ),
                        const Text('+ 10 sec'),
                      ],
                    ),
                  ],
                ),
              ),
              Container(
                width: double.infinity,
                height: 50,
                margin: const EdgeInsets.all(8.0),
                decoration: BoxDecoration(
                  color: Theme.of(context).primaryColor,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Center(
                  child: Text(
                    'KinescopePlayerStatus: ${snapshot.data}',
                    style: const TextStyle(color: Colors.white),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _pause() {
    _kinescopeController.pause();
  }

  void _play() {
    _kinescopeController.play();
  }

  void _stop() {
    _kinescopeController.stop();
  }

  void _unmute() {
    _kinescopeController.unmute();
  }

  void _mute() {
    _kinescopeController.mute();
  }

  Future<void> _rewindBackward() async {
    final currentTime = await _kinescopeController.getCurrentTime();

    final duration = Duration(
      seconds: currentTime.inSeconds - 10 > 0 ? currentTime.inSeconds - 10 : 0,
    );

    _kinescopeController.seekTo(duration);
  }

  Future<void> _rewindForward() async {
    final currentTime = await _kinescopeController.getCurrentTime();

    _kinescopeController.seekTo(
      currentTime + const Duration(seconds: 10),
    );
  }

  Future<void> _seekToCenter() async {
    final duration = await _kinescopeController.getDuration();
    _kinescopeController.seekTo(
      Duration(
        seconds: duration.inSeconds ~/ 2,
      ),
    );
  }
}
