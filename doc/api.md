# Shared API

Types shared by **native (Android)** and **embed (iOS / Web)**.

← [Home](../README.md) · [Android](android-native.md) · [Embed](embed-webview.md)

---

## KinescopePlayer

```dart
KinescopePlayer(
  controller: controller,
  aspectRatio: 16 / 9,
)
```

Same widget on every platform: native player on Android, embed on iOS / Web.

---

## KinescopePlayerController

Controls playback and exposes status / time streams.

```dart
final controller = KinescopePlayerController(
  'yourVideoId',
  parameters: const PlayerParameters(autoplay: true),
);

controller.status.listen((status) {
  // KinescopePlayerStatus: play, pause, ended, …
});

controller.timeUpdateStream.listen((update) {
  // current position & percent
});
```

### Available methods

| Method | Description | Android | iOS / Web |
| ------ | ----------- | :---: | :---: |
| `play()` | Plays the currently cued / loaded video. | ✓ | ✓ |
| `pause()` | Pauses the currently playing video. | ✓ | ✓ |
| `stop()` | Stops and cancels loading of the current video. | ✓ | ✓ |
| `load(String videoId)` | Loads and plays the specified video. | ✓ | ✓ |
| `getCurrentTime()` | Returns current position (`Future<Duration>`). | ✓ | ✓ |
| `getDuration()` | Returns duration of the video (`Future<Duration>`). | ✓ | ✓ |
| `seekTo(Duration position)` | Seeks to a specified time in the video. | ✓ | ✓ |
| `mute()` | Mutes the player. | ✓ | ✓ |
| `unmute()` | Unmutes the player. | ✓ | ✓ |
| `setVolume(double volume)` | Sets volume `0.0`–`1.0`. **Android only.** | ✓ | — |
| `dispose()` | Closes status and time-update streams. | ✓ | ✓ |

---

## PlayerParameters

Initial player options passed into `KinescopePlayerController`.

On **iOS / Web**, parameters map to the Kinescope iframe embed
On **Android**, the native player applies a subset (marked in the **Android** column). Other values are ignored on Android.

### All parameters

| Parameter | Description | Default (typical) | Android | iOS / Web |
| --------- | ----------- | ----------------- | :---: | :---: |
| `autoplay` | Start playback when the player loads. | `false` / embed default | ✓ | ✓ |
| `muted` | Start muted. | embed default | ✓ | ✓ |
| `loop` | Restart the video automatically after it ends. | — | ✓ | ✓ |
| `playsinline` | Play inline without forcing fullscreen. | — | ✓ | ✓ |
| `texttrack` | Enable subtitles on load (when available). | — | ✓ native | ✓ embed |
| `controls` | Show player controls. On Android: master switch for native chrome. | — | ✓ | ✓ |
| `drmAuthToken` | Authorization Backend token (`drmauthtoken`) for DRM-protected videos. | — | ✓ | ✓ |
| `showDefaultPoster` | Built-in default poster when metadata has no poster URL. | `true` | ✓ | — |
| `referer` | HTTP `Referer` when the video has **domain restrictions** in the dashboard. Must match an allow-listed domain. Default native referer is `https://kinescope.io/`. Does not open embedding on other sites. | — | ✓ | — |
| `autofocus` | Set focus to the player. | `true` (embed) | — | ✓ |
| `autopause` | Pause when appropriate (embed behaviour). | — | — | ✓ |
| `preload` | Preload video metadata. | `true` (embed) | — | ✓ |
| `userAgent` | Overrides default User-Agent. | — | — | ✓ |
| `externalId` | Any string that represents a user on an external system (analytics). | `''` | — | ✓ |
| `baseUrl` | Custom embed host. | — | — | ✓ |
| `dnt` | Disable sent analytics. | — | — | ✓ |
| `background` | Disable controls; typically used with autoplay / muted / loop. | — | — | ✓ |
| `t` | Seek the video to this time (seconds) on load. | — | — | ✓ |
| `transparent` | Transparent background color. | — | — | ✓ |
| `speedbtn` | Visibility of the playback rate button. | — | — | ✓ |
| `header` | Visibility of the header. | — | — | ✓ |
| `disableFiles` | Hide additional materials. | — | — | ✓ |
| `watermark` | Watermark via [`WatermarkParameters`](#watermarkparameters) (`mode`, `text`). | empty | — | ✓ |
| `onEnterFullScreen` | Callback when entering fullscreen. | — | ✓ | ✓ |
| `onExitFullScreen` | Callback when exiting fullscreen. | — | ✓ | ✓ |
| `onEnterPictureInPicture` | Callback when entering PiP. | — | ✓ | — |
| `onExitPictureInPicture` | Callback when exiting PiP. | — | ✓ | — |

### Example

```dart
const PlayerParameters(
  autoplay: true,
  muted: true,
  loop: true,
  playsinline: true,
  texttrack: true,
  controls: true,
  header: true,
  speedbtn: true,
  drmAuthToken: 'your-auth-backend-token',
  showDefaultPoster: true,
  referer: 'https://your-domain.com/',
  userAgent: 'MyApp/1.0',
  externalId: 'user-123',
  watermark: WatermarkParameters(
    mode: 'regular',
    text: 'Confidential',
  ),
  onEnterFullScreen: null, // or your callback
  onExitFullScreen: null,
);
```

### WatermarkParameters

| Field | Description |
| ----- | ----------- |
| `mode` | Watermark mode (embed). |
| `text` | Watermark text. |

```dart
const WatermarkParameters(
  mode: 'regular',
  text: 'Confidential',
);
```

---

## Platform notes

| | |
| --- | --- |
| **Android native** | Setup, PiP, offline, chrome → [android-native.md](android-native.md) |
| **iOS / Web embed** | WebView / iframe specifics → [embed-webview.md](embed-webview.md) |
