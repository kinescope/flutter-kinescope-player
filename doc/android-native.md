# Android — native player

Native side of the SDK: [`kotlin-kinescope-player` **0.1.5**](https://github.com/kinescope/kotlin-kinescope-player) via hybrid-composition `PlatformView`.

← [Home](../README.md) · [Embed (iOS / Web)](embed-webview.md) · [Shared API](api.md)

---

## Android-only features

| Feature | |
| ------- | --- |
| Native chrome (play/pause morph, settings, seek) | ✓ |
| Fullscreen + content-aware orientation | ✓ |
| Picture-in-Picture | ✓ |
| Offline download (single quality) + Widevine DRM | ✓ |
| Quality labels from embed `quality_map` | ✓ |
| `setVolume()` | ✓ |
| Subtitles | native |

---

## Setup

### 1. API key (offline / DRM)

```dart
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await KinescopeOfflineDownload.instance.initialize(
    apiKey: 'your-kinescope-api-key',
  );
  runApp(const MyApp());
}
```

Same key as in the [native demo](https://github.com/kinescope/kotlin-kinescope-player).

### 2. Manifest

See [example AndroidManifest.xml](../example/android/app/src/main/AndroidManifest.xml).

| Setting | Why |
| ------- | --- |
| Download / notification permissions | Offline cache |
| `android:supportsPictureInPicture="true"` | System PiP |
| Broad `android:configChanges` | Avoid Activity recreate on FS / PiP / rotate |

### 3. JDK 17

Builds need **JDK 17** (`flutter config --jdk-dir=…` or `org.gradle.java.home` in `gradle.properties`). JDK 25+ is not supported by the current AGP.

---

## Online playback

```dart
KinescopePlayer(
  controller: KinescopePlayerController(
    'yourVideoId',
    parameters: const PlayerParameters(
      autoplay: true,
      muted: true,
      loop: true,
      texttrack: true,
    ),
  ),
  aspectRatio: 16 / 9,
)
```

On Android the widget uses hybrid composition and a pop guard: **Back** exits fullscreen / hides the surface first, then pops the route — no leftover video frame.

Native player applies: `autoplay`, `muted`, `loop`, `controls`, `playsinline`, `texttrack`, `drmAuthToken`, `showDefaultPoster`, `referer`, PiP callbacks. Other iframe-only options are ignored — see [api.md](api.md).

### DRM Authorization Backend

```dart
parameters: const PlayerParameters(
  drmAuthToken: 'your-jwt-or-token',
  // showDefaultPoster: false, // optional: hide built-in poster fallback
),
```

Set `drmAuthToken` before the first load (it is passed into native options at player create).

### Domain restrictions

If the video is limited to specific domains in the Kinescope dashboard, set a matching `Referer` **before** `loadVideo`. The native SDK default is `https://kinescope.io/`. This does **not** open embedding on other sites — it only makes the app’s metadata / DRM requests match the allow list.

```dart
parameters: const PlayerParameters(
  referer: 'https://your-domain.com/',
  // often combined with Authorization Backend:
  // drmAuthToken: 'your-jwt-or-token',
),
```

See also native docs: [player-options.md](https://github.com/kinescope/kotlin-kinescope-player/blob/main/docs/player-options.md).

---

## Native UI (0.1.5)

Chrome is drawn by the native SDK, not a Flutter overlay.

| | |
| --- | --- |
| **Play / Pause** | 72 dp circle · play↔pause morph · replay at end |
| **Fullscreen** | Overlay · orientation follows video aspect (portrait stays portrait) |
| **Settings → Quality** | Names from `quality_map` (e.g. `480p`) |
| **PiP** | System PiP when the Activity opts in |
| **Captions** | Above the control bar · search in Settings → Subtitles |

---

## Offline download & playback

Caches **one** chosen HLS/DASH height (not the full ladder).

```dart
final downloads = KinescopeOfflineDownload.instance;

// By video id (catalog / API)
final qualities = await downloads.listDownloadQualities('yourVideoId');
final chosen = qualities.first; // or show a picker

final info = await downloads.downloadVideo(
  'yourVideoId',
  videoHeightPx: chosen.height,
  qualityHint: chosen.label,
);

// Or by link: video id, kinescope.io URL, or direct .m3u8 / .mpd
final fromUrl = await downloads.downloadFromUrl(
  'https://kinescope.io/yourVideoId',
  // 'https://cdn.example/master.m3u8',
  videoHeightPx: chosen.height,
  qualityHint: chosen.label,
);

downloads.updates.listen((u) {
  final d = u.download;
  // d.percent, d.state, d.progressLabel, d.qualityLabel, …
});

KinescopeOfflinePlayer(contentId: info.contentId);
```

Omit `videoHeightPx` to download the highest available quality.

| API | |
| --- | --- |
| `listDownloadQualities` / `downloadVideo` | By Kinescope video id |
| `listDownloadQualitiesFromUrl` / `downloadFromUrl` | By id, page URL, or HLS/DASH manifest |
| `getVideoCatalog()` | Project catalog |
| `getCompletedDownloads()` / `getAllDownloads()` | Local cache |
| `removeDownload(contentId)` | Delete |

`KinescopeOfflinePlayer` uses the same native chrome as online (FS, PiP, settings) and the same Back / pop guard.

Quality bottom-sheet example: [example app](../example/lib/main.dart).
