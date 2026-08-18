# Changelog


## 0.2.5

Android player aligned with **kotlin-kinescope-player 0.1.5** 

- Native dependency `0.1.5`
- `PlayerParameters.drmAuthToken` — Authorization Backend token (Android options; iOS/Web `?drmauthtoken=` on the video URL)
- `PlayerParameters.showDefaultPoster` — opt out of the built-in default poster (Android)
- `PlayerParameters.referer` — HTTP Referer for **domain restrictions** (Android `setReferer` / options; default remains `https://kinescope.io/`)
- Offline library UI: in-progress downloads with progress; optimistic remove hides Media3 `removing` rows
- DRM download probe: release metadata player early, serialize Widevine probes; license-acquire timeout frees the probe queue if the CDM callback never returns
- Multiple offline PlatformViews: hide/exitFullscreen target a single `contentId` (no longer broadcast to every session)
- PiP orphan teardown: null-safe detach after dispose during PiP so `release()` still runs
- Docs: `doc/api.md`, `doc/android-native.md` (DRM auth + domain restrictions)

## 0.2.4

- fix mobile html

## 0.2.3

- Added `KinescopePlayerController` with stream providing `KinescopePlayerTimeUpdate`

## 0.2.2

- Add player parameters: `onEnterFullScreen`, `onExitFullScreen`

## 0.2.1

- fix base url ios

## 0.2.0

- change webview_flutter
- drm web

## 0.1.10

- fix live seek

## 0.1.9

- fix ios

## 0.1.8

- update web html

## 0.1.7

- update flutter_inappwebview 6.1.5

## 0.1.6

- Android PROTECTED_MEDIA_ID
- Add player parameters: `baseUrl`

## 0.1.5

- update flutter_inappwebview 6
- fix build web

## 0.1.4

- Add player parameters: `disableFiles`, `watermark`

## 0.1.3

- Update player parameters.

## 0.1.2

- Added more player parameters.

## 0.1.1

- Added parameter `userAgent` for able override default UserAgent.

## 0.1.0

- Added `KinescopePlayerController` with stream providing `KinescopePlayerStatus` and some methods to control video playback.

## 0.0.1

- **Initial Release:** introduce `flutter_kinescope_sdk` with `KinescopePlayer` widget and `PlayerParameters` for initial player setup.
