# Flutter Kinescope SDK

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://kinescope.io/)

Flutter plugin for the [Kinescope](https://kinescope.io/) video player.

One Dart API (`KinescopePlayer` + `KinescopePlayerController`) on every platform.  
Under the hood there are **two implementations**:

| Part | Platforms | Engine |
| ---- | --------- | ------ |
| **Native** | Android | [`kotlin-kinescope-player` 0.1.5](https://github.com/kinescope/kotlin-kinescope-player) · ExoPlayer · native UI (fullscreen, PiP, settings, offline, DRM) |
| **WebView / embed** | iOS, Web | Kinescope iframe in WKWebView (iOS) or iframe / JS API (Web) |

## Documentation

| | |
| --- | --- |
| [**Android (native)**](doc/android-native.md) | Setup, UI, PiP, orientation, offline / DRM, quality picker |
| [**iOS & Web (embed)**](doc/embed-webview.md) | WebView / iframe, embed parameters |
| [**Shared API**](doc/api.md) | `KinescopePlayerController`, shared `PlayerParameters` |
| [Changelog](CHANGELOG.md) | Release history |

## Installation

```yaml
dependencies:
  flutter_kinescope_sdk: ^0.2.5
```

| | |
| --- | --- |
| Flutter | `>= 3.35.0` |
| Dart | `>= 3.9.0 < 4.0.0` |
| Android | `minSdk 24`, **JDK 17** |
| iOS | Swift, Xcode 11+ |

Android setup (manifest, API key, JDK) → [doc/android-native.md](doc/android-native.md).

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

Same widget everywhere: native player on Android, embed on iOS / Web.

## Example

```bash
cd example
flutter run
```

On Android the demo includes online playback and offline downloads with a quality picker.

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
