# iOS & Web — embed / WebView

On iOS and Web the player is the **Kinescope embed** (not native ExoPlayer).

| Platform | How it is embedded |
| -------- | ------------------ |
| **iOS** | iframe inside **WKWebView** ([`webview_flutter`](https://pub.dev/packages/webview_flutter)) |
| **Web** | iframe / JS player API |

← [Home](../README.md) · [Android (native)](android-native.md) · [Shared API](api.md)

---

## Not available here

Everything that lives only in [Android native](android-native.md):

- native chrome / morph play-pause  
- Picture-in-Picture  
- offline download / DRM  
- `setVolume()`  
- content-aware orientation  

---

## Playback

Same widget as on Android:

```dart
KinescopePlayer(
  controller: KinescopePlayerController(
    'yourVideoId',
    parameters: const PlayerParameters(
      autoplay: true,
      muted: true,
      loop: true,
      texttrack: true,
      header: true,
      speedbtn: true,
    ),
  ),
  aspectRatio: 16 / 9,
)
```

On iOS / Web, parameters map to embed query / options. Full list → [api.md](api.md) (iOS / Web section).

---

## Useful embed parameters

Full table (including defaults and Android support): [api.md — PlayerParameters](api.md#playerparameters).

Highlights for embed-only options:

| Parameter | |
| --------- | --- |
| `baseUrl` | Custom embed host |
| `userAgent` | Override User-Agent (iOS) |
| `preload` | Preload metadata |
| `autofocus` / `autopause` | Focus / pause behaviour |
| `dnt` | Disable analytics |
| `background` / `transparent` | Chromeless / transparent |
| `t` | Start at time (seconds) |
| `header`, `speedbtn`, `disableFiles`, `watermark` | Embed UI |
| `externalId` | Analytics user id |

---

## Events & controller

`play` / `pause` / `seek` / `status` / `timeUpdate` go through the same `KinescopePlayerController` as on Android.

`setVolume()` is **not** supported on iOS / Web.
