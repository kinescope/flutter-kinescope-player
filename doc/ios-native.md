# iOS — native player

Native side of the SDK: [`ios-kinescope-player` **1.0.3**](https://github.com/kinescope/ios-kinescope-player) (`KinescopeSDK`) via a `UiKitView`.

← [Home](../README.md) · [Android (native)](android-native.md) · [Web (embed)](embed-webview.md) · [Shared API](api.md)

---

## iOS player

| Feature | Flutter widget |
| ------- | --- |
| Native chrome (play/pause, seek, settings, quality) | ✓ |
| Fullscreen | ✓ native controller |
| Picture-in-Picture button | ✓ when the device supports it |
| `setVolume()` / mute | ✓ |
| Subtitles | settings menu in the native chrome |
| `drmAuthToken` | ✓ on the video metadata request |
| `referer` (domain restrictions) | ✓ SDK config, applied before load |
| Offline download / FairPlay through `KinescopeOfflineDownload` | ✓ same Flutter API as Android |
| Offline playback | `KinescopeOfflinePlayer` plays the downloaded file |

`KinescopeOfflineDownload` lists qualities, downloads one HLS rendition with FairPlay, and reports progress at most twice a second. `KinescopeOfflinePlayer` plays the local file and keeps the rendition chosen at download time, so the settings gear shows that quality. Subtitles and chapters stay in the native player chrome when the video has them.

---

## Setup

Deployment target is **iOS 15**. Flutter resolves `KinescopeSDK` **1.0.3** from git when Swift Package Manager is on (default since recent Flutter).

### CocoaPods

1.0.3 is a git tag. Trunk does not serve later releases. In the app `Podfile`:

```ruby
platform :ios, '15.0'

target 'Runner' do
  use_frameworks!
  pod 'KinescopeSDK', :git => 'https://github.com/kinescope/ios-kinescope-player.git', :tag => '1.0.3'
end

post_install do |installer|
  installer.pods_project.targets.each do |target|
    target.build_configurations.each do |config|
      config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.0'
    end
  end
end
```

`M3U8Parser` 1.1.x (1.2 renamed the product to `M3U8Kit`) and `SwiftProtobuf` declare an older minimum iOS. The `post_install` block keeps current Xcode from rejecting those targets.

### Picture-in-Picture

Add background audio to the app `Info.plist` so the system PiP session can continue:

```xml
<key>UIBackgroundModes</key>
<array>
  <string>audio</string>
</array>
```

The example app already has this key.

### API key

A public video plays without a dashboard token. Pass the token to `KinescopeOfflineDownload.initialize` when the app lists videos from the dashboard. `KinescopePlayer` itself does not need it.

---

## Online playback

Same widget as on Android:

```dart
KinescopePlayer(
  controller: KinescopePlayerController(
    'yourVideoId',
    parameters: const PlayerParameters(
      autoplay: true,
      muted: true,
      loop: true,
    ),
  ),
  aspectRatio: 16 / 9,
)
```

Native player applies: `autoplay`, `muted`, `loop`, `controls`, `playsinline`, `drmAuthToken`, `referer`.

`playsinline: false` opens native fullscreen once the video has loaded.

`controls: false` hides the native option buttons (settings, fullscreen, PiP, AirPlay, subtitles, download).

Iframe-only options (`header`, `speedbtn`, `watermark`, `baseUrl`, …) are ignored. `userAgent` is ignored here and on Web. See [api.md](api.md).

### DRM Authorization Backend

```dart
parameters: const PlayerParameters(
  drmAuthToken: 'your-jwt-or-token',
),
```

Passed as `drmAuthToken` on `KinescopePlayerConfig` before the first load.

### Domain restrictions

If the video is limited to specific domains in the Kinescope dashboard, set a matching `Referer` before the player is created. The SDK default is `https://kinescope.io/`.

```dart
parameters: const PlayerParameters(
  referer: 'https://your-domain.com/',
),
```

---

## Native UI (1.0.3)

Chrome is drawn by `KinescopeSDK`, not a Flutter overlay.

| | |
| --- | --- |
| **Play / Pause** | Center control and the timeline |
| **Fullscreen** | Native fullscreen controller. Orientation follows the video |
| **Settings** | Quality, speed, scale. The gear stays plain below 720, shows **HD** from 720 through 1440, and **4K** from 2160. The cutoff is inside KinescopeSDK 1.0.3 |
| **Subtitles** | Own button when the video has them, and a row in settings |
| **Chapters** | Own button when the video has them |
| **PiP** | System PiP button when `AVPictureInPictureController` is available |

Flutter `onEnterFullScreen` / `onExitFullScreen` run when that native fullscreen controller is presented or dismissed. Back leaves fullscreen before the route pops.

`onEnterPictureInPicture` / `onExitPictureInPicture` are Android-only. On iOS, PiP is the button in the native chrome.
