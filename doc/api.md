# Shared API

Types shared by **native (Android, iOS)** and **embed (Web)**.

← [Home](../README.md) · [Android](android-native.md) · [iOS](ios-native.md) · [Web](embed-webview.md)

---

## KinescopePlayer

```dart
KinescopePlayer(
  controller: controller,
  aspectRatio: 16 / 9,
)
```

Same widget on every platform: native player on Android and iOS, embed on Web.

---

## KinescopePlayerController

Controls playback and exposes status / time streams.

```dart
final controller = KinescopePlayerController(
  'yourVideoId',
  parameters: const PlayerParameters(autoplay: true),
);

controller.status.listen((status) {
  // KinescopePlayerStatus: init, ready, playing, waiting, pause, ended, unknown
});

controller.timeUpdateStream.listen((update) {
  // current position & percent
});
```

### Available methods

| Method | Description | Android | iOS | Web |
| ------ | ----------- | :---: | :---: | :---: |
| `play()` | Plays the currently cued / loaded video. | ✓ | ✓ | ✓ |
| `pause()` | Pauses the currently playing video. | ✓ | ✓ | ✓ |
| `stop()` | Stops and cancels loading of the current video. | ✓ | ✓ | ✓ |
| `load(String videoId)` | Loads the specified video. Playback follows `autoplay`. | ✓ | ✓ | ✓ |
| `getCurrentTime()` | Returns current position (`Future<Duration>`). | ✓ | ✓ | ✓ |
| `getDuration()` | Returns duration of the video (`Future<Duration>`). | ✓ | ✓ | ✓ |
| `seekTo(Duration position)` | Seeks to a specified time in the video. | ✓ | ✓ | ✓ |
| `mute()` | Mutes the player. | ✓ | ✓ | ✓ |
| `unmute()` | Unmutes the player. | ✓ | ✓ | ✓ |
| `setVolume(double volume)` | Sets volume `0.0`–`1.0`. Web forwards it to the embed JS player. | ✓ | ✓ | ✓ |
| `dispose()` | Closes status and time-update streams. | ✓ | ✓ | ✓ |

---

## PlayerParameters

Initial options for `KinescopePlayerController`.

Android and iOS pass a native subset into the player. Web maps parameters onto the Kinescope iframe. `—` means that platform ignores the value.

On Android and iOS, an unset bool uses the default in the table. On Web, an unset value is omitted and the embed keeps its own default.

### All parameters

| Parameter | What it does | Default | Android | iOS | Web |
| --------- | ------------ | ------- | :---: | :---: | :---: |
| `autoplay` | Start playback after the video is ready. On iOS the player starts, then pauses when this is `false`. | `false` | ✓ | ✓ | ✓ |
| `muted` | Start muted. | `false` | ✓ | ✓ | ✓ |
| `loop` | Replay from the start when playback ends. | `false` | ✓ | ✓ | ✓ |
| `playsinline` | Stay inline. On iOS, `false` opens native fullscreen after the video loads. Android forwards the flag to the native player. | `true` | ✓ | ✓ | ✓ |
| `texttrack` | Turn subtitles on at load when the video has them. iOS ignores the flag; the native chrome has its own subtitles button and a settings row. | unset | ✓ | menu | ✓ |
| `controls` | Show controls. Android toggles play, seek, duration, options, fullscreen, and chapters. iOS `false` hides settings, fullscreen, PiP, AirPlay, subtitles, and download. | `true` | ✓ | ✓ | ✓ |
| `drmAuthToken` | Authorization Backend token for DRM. Android: native options. iOS: metadata request (`KinescopePlayerConfig`). Web: `?drmauthtoken=` on the video URL. | — | ✓ | ✓ | ✓ |
| `showDefaultPoster` | Built-in poster when metadata has no poster URL. | `true` | ✓ | — | — |
| `referer` | HTTP `Referer` for dashboard **domain restrictions**. Must match an allow-listed domain. Does not open embedding on other sites. | `https://kinescope.io/` | ✓ | ✓ | — |
| `autofocus` | Set focus to the player. | embed | — | — | ✓ |
| `autopause` | Pause when appropriate (embed behaviour). | embed | — | — | ✓ |
| `preload` | Preload video metadata. | embed | — | — | ✓ |
| `externalId` | String that represents a user on an external system (analytics). | `''` | — | — | ✓ |
| `baseUrl` | Custom embed host. | — | — | — | ✓ |
| `dnt` | Disable sent analytics. | embed | — | — | ✓ |
| `background` | Disable controls; typically used with autoplay, muted, and loop. | embed | — | — | ✓ |
| `t` | Seek to this time (seconds) once the embed is ready. | — | — | — | ✓ |
| `transparent` | Transparent background color. | embed | — | — | ✓ |
| `speedbtn` | Visibility of the playback rate button. | embed | — | — | ✓ |
| `header` | Visibility of the header. | embed | — | — | ✓ |
| `disableFiles` | Hide additional materials. | embed | — | — | ✓ |
| `watermark` | Watermark via [`WatermarkParameters`](#watermarkparameters) (`mode`, `text`). | empty | — | — | ✓ |
| `userAgent` | Present on `PlayerParameters`. The current Android, iOS, and Web players do not apply it. | — | — | — | — |
| `onEnterFullScreen` | Callback when entering fullscreen. | — | ✓ | ✓ | ✓ |
| `onExitFullScreen` | Callback when exiting fullscreen. | — | ✓ | ✓ | ✓ |
| `onEnterPictureInPicture` | Callback when entering PiP. iOS PiP is the system button and does not call this. | — | ✓ | — | — |
| `onExitPictureInPicture` | Callback when exiting PiP. iOS does not call this. | — | ✓ | — | — |

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
| **iOS native** | KinescopeSDK 1.0.3, fullscreen, PiP, offline → [ios-native.md](ios-native.md) |
| **Web embed** | iframe specifics → [embed-webview.md](embed-webview.md) |
