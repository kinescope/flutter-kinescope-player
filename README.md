# Flutter Kinescope SDK

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://kinescope.io/)

Flutter plugin for the [Kinescope](https://kinescope.io/) video player.

One Dart API (`KinescopePlayer` + `KinescopePlayerController`) on every platform.  
Under the hood there are **three implementations**:

| Part | Platforms | Engine |
| ---- | --------- | ------ |
| **Native** | Android | [`kotlin-kinescope-player` 0.1.5](https://github.com/kinescope/kotlin-kinescope-player) · ExoPlayer · native UI (fullscreen, PiP, settings, offline, DRM) |
| **Native** | iOS | [`ios-kinescope-player` 1.0.3](https://github.com/kinescope/ios-kinescope-player) · AVPlayer · native UI (fullscreen, PiP, settings, offline) |
| **Embed** | Web | Kinescope iframe / JS API |

## Documentation

| | |
| --- | --- |
| [**Android (native)**](doc/android-native.md) | Setup, UI, PiP, orientation, offline / DRM, quality picker |
| [**iOS (native)**](doc/ios-native.md) | KinescopeSDK 1.0.3, fullscreen, PiP, DRM token, offline |
| [**Web (embed)**](doc/embed-webview.md) | iframe / JS player, embed parameters |
| [**Shared API**](doc/api.md) | `KinescopePlayerController`, shared `PlayerParameters` |
| [Changelog](CHANGELOG.md) | Release history |

## Installation

```yaml
dependencies:
  flutter_kinescope_sdk: ^0.2.6
```

| | |
| --- | --- |
| Flutter | `>= 3.35.0` |
| Dart | `>= 3.9.0 < 4.0.0` |
| Android | `minSdk 24`, **JDK 17** |
| iOS | Swift, **iOS 15**, Xcode |

Android setup (manifest, API key, JDK) → [doc/android-native.md](doc/android-native.md).

iOS setup (deployment target, CocoaPods tag, PiP) → [doc/ios-native.md](doc/ios-native.md).

## Quick start

```dart
KinescopePlayer(
  controller: KinescopePlayerController(
    'yourVideoId',
    parameters: const PlayerParameters(
      autoplay: true,
      muted: true,
    ),
  ),
  aspectRatio: 16 / 9,
)
```

Same widget everywhere: native player on Android and iOS, embed on Web.

## Example

```bash
cd example
flutter run
```

The demo menu opens the player, sample videos with subtitles and chapters, and offline downloads with a quality picker on Android and iOS.

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
