# Flutter Kinescope SDK

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://kinescope.io/)

Flutter plugin for the Kinescope player.

This package supports Android and iOS and uses [flutter_inappwebview](https://pub.dev/packages/flutter_inappwebview) under-the-hood.

## Requirements

* **Android:** `minSdkVersion 17` and add support for `androidx` (see [AndroidX Migration](https://flutter.dev/docs/development/androidx-migration))
* **iOS:** `--ios-language swift`, Xcode version >= 11

## Usage

```dart
KinescopePlayer(
  yourVideoId,
  parameters: const PlayerParameters(
    autoplay: true,
    muted: true,
  ),
)
```

## Installation

Add `flutter_kinescope_sdk` to your `pubspec.yaml` file:

```yaml
dependencies:
  flutter_kinescope_sdk: ^0.0.1
```

## Changelog

All notable changes to this project will be documented in [this file](./CHANGELOG.md).

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
