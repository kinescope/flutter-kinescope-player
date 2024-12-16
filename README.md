# Flutter Kinescope SDK

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://kinescope.io/)

Flutter plugin for the Kinescope player.

This package supports Android and iOS and uses [flutter_inappwebview](https://pub.dev/packages/flutter_inappwebview) under-the-hood.

## Requirements

- **Android:** `minSdkVersion 17` and add support for `androidx` (see [AndroidX Migration](https://flutter.dev/docs/development/androidx-migration))
- **iOS:** `--ios-language swift`, Xcode version >= 11

## Usage

```dart
KinescopePlayer(
  controller: KinescopePlayerController(
    yourVideoId,
    parameters: const PlayerParameters(
      autoplay: true,
      muted: true,
      loop: true,
    ),
  ),
  aspectRatio: 16 / 10,
)
```

### Available methods

Methods available for `KinescopePlayerController`.

| Method                    | Description                                       |
| ------------------------- | ------------------------------------------------- |
| play()                    | Plays the currently cued/loaded video.            |
| pause()                   | Pauses the currently playing video.               |
| stop()                    | Stops and cancels loading of the current video.   |
| load(String videoId)      | Loads and plays the specified video.              |
| getCurrentTime()          | Returns current position.                         |
| getDuration()             | Returns duration of video.                        |
| seekTo(Duration position) | Seeks to a specified time in the video.           |
| mute()                    | Mutes the player.                                 |
| ummute()                  | Unmutes the player.                               |
| setVolume(double volume)  | Sets the volume of player. Works only on Android. |

### PlayerParameters

You can set initial Kinescope player parameters using PlayerParameters.

| Parameter    | Description                                                                                                     |
| ------------ | --------------------------------------------------------------------------------------------------------------- |
| autoplay     | Specifies whether the initial video will automatically start to play when the player loads. **Default = false** |
| muted        | Mutes the player. **Default = true**                                                                            |
| loop         | Restart the video automatically after it's ended.                                                               |
| userAgent    | Overrides default UserAgent                                                                                     |
| externalId   | Any string that represents a user on an external system. It's used in analytics. **Default = ''**               |
| autofocus    | Set focus to player. **Default = true**                                                                         |
| autoplay     | Start playback on load. **Default = true**                                                                      |
| playsinline  | Play video without full screen.                                                                                 |
| preload      | Preload video metadata. **Default = true**                                                                      |
| texttrack    | Enable subtitles on load                                                                                        |
| dnt          | Disable sent analytics                                                                                          |
| background   | Disable any controls. Set **autoplay**, **muted**, **loop** to **true**                                         |
| t            | Seek the video to the time                                                                                      |
| transparent  | Transparent background color                                                                                    |
| speedbtn     | Visibility the playback rate button                                                                             |
| header       | Visibility header                                                                                               |
| controls     | Visibility controls                                                                                             |
| disableFiles | Hide additional materials                                                                                       |
| watermark    | Set watermark                                                                                                   |

For a more detailed usage example, go to [example](./example/lib/main.dart).

## Installation

Add `flutter_kinescope_sdk` to your `pubspec.yaml` file:

```yaml
dependencies:
  flutter_kinescope_sdk: ^0.1.10
```

## Changelog

All notable changes to this project will be documented in [this file](./CHANGELOG.md).

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
