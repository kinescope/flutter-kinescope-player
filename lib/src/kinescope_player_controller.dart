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

import 'dart:async';
import 'package:flutter_kinescope_sdk/src/data/player_parameters.dart';
import 'package:flutter_kinescope_sdk/src/data/player_status.dart';
import 'package:flutter_kinescope_sdk/src/data/player_time_update.dart';
import 'package:flutter_kinescope_sdk/src/player/kinescope_player_navigation.dart';

/// Controls a Kinescope player, and provides status updates using [status] stream.
///
/// The video is displayed in a Flutter app by creating a `KinescopePlayer` widget.
class KinescopePlayerController {
  String _videoId;

  /// Initial `KinescopePlayer` parameters.
  final PlayerParameters parameters;

  /// StreamController for [status] stream.
  final statusController = StreamController<KinescopePlayerStatus>();

  /// Controller to communicate with WebView.
  late ControllerProxy controllerProxy = ControllerProxy();

  /// [Stream], that provides current player status
  Stream<KinescopePlayerStatus> get status => statusController.stream;

  /// Currently playing video id
  String get videoId => _videoId;

  /// StreamController for timeUpdate stream.
  final StreamController<KinescopePlayerTimeUpdate> timeUpdateController =
      StreamController<KinescopePlayerTimeUpdate>.broadcast();

  // [Stream] that provides current time of the video
  Stream<KinescopePlayerTimeUpdate> get timeUpdateStream =>
      timeUpdateController.stream;

  KinescopePlayerController(
    /// The video id with which the player initializes.
    String videoId, {
    this.parameters = const PlayerParameters(),
  }) : _videoId = videoId {
    timeUpdateStream;
  }

  /// Loads the video as per the [videoId] provided.
  void load(String videoId) {
    statusController.sink.add(KinescopePlayerStatus.unknown);
    controllerProxy.loadVideo(videoId);
    _videoId = videoId;
  }

  /// Plays the video.
  void play() {
    controllerProxy.play();
  }

  /// Pauses the video.
  void pause() {
    controllerProxy.pause();
  }

  /// Stops the video.
  void stop() {
    controllerProxy.stop();
  }

  /// Get current position.
  Future<Duration> getCurrentTime() async {
    return controllerProxy.getCurrentTime();
  }

  /// Get duration of video.
  Future<Duration> getDuration() async {
    return controllerProxy.getDuration();
  }

  /// Seek to any position.
  void seekTo(Duration duration) {
    controllerProxy.seekTo(duration);
  }

  /// Set volume level
  /// (0..1, where 0 is 0% and 1 is 100%)
  /// Works only on Android
  void setVolume(double value) {
    if (value > 0 || value <= 1) {
      controllerProxy.setVolume(value);
    }
  }

  /// Mutes the player.
  void mute() {
    controllerProxy.mute();
  }

  /// Unmutes the player.
  void unmute() {
    controllerProxy.unmute();
  }

  /// Close [statusController] and [timeUpdateController] stream.
  void dispose() {
    statusController.close();
    timeUpdateController.close();
  }
}
