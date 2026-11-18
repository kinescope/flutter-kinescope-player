# Web — embed

On Web the player is the **Kinescope embed** (iframe / JS player API).

iOS uses the native player → [ios-native.md](ios-native.md).

← [Home](../README.md) · [Android (native)](android-native.md) · [iOS (native)](ios-native.md) · [Shared API](api.md)

---

## Not available here

Native chrome, Picture-in-Picture, and offline download live in the native players:

- [Android](android-native.md)
- [iOS](ios-native.md)  

---

## Playback

Same widget as on Android and iOS:

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

On Web, parameters map to embed query / options. Full list → [api.md](api.md).

---

## Useful embed parameters

Full table (defaults and Android / iOS / Web): [api.md — PlayerParameters](api.md#playerparameters).

Highlights for embed-only options:

| Parameter | |
| --------- | --- |
| `baseUrl` | Custom embed host |
| `userAgent` | Field exists; the current embed player does not apply it |
| `preload` | Preload metadata |
| `autofocus` / `autopause` | Focus / pause behaviour |
| `dnt` | Disable analytics |
| `background` / `transparent` | Chromeless / transparent |
| `t` | Start at time (seconds) |
| `header`, `speedbtn`, `disableFiles`, `watermark` | Embed UI |
| `externalId` | Analytics user id |

---

## Events & controller

`play` / `pause` / `seek` / `mute` / `setVolume` / `status` / `timeUpdate` go through the same `KinescopePlayerController` as on Android and iOS. `setVolume()` is forwarded to the embed JS player.
