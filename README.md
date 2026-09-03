# Media Toolbox

Native Android media utility app.

## Current version: 0.3

0.3 adds the local Gallery foundation while retaining the 0.1 camera and 0.2 video foundation.

### Camera
- Camera is the primary screen.
- Runtime camera permission is requested only when needed.
- Rear/front camera switching is available.
- Photo capture saves to Android MediaStore under `DCIM/Media Toolbox`.
- Video capture saves to Android MediaStore under `DCIM/Media Toolbox` on Android 10+.
- Camera, Video, Scan and QR use a non-looping centre-focused mode selector.
- Camera and Gallery are persistent navigation destinations.

### Gallery
- Reads device media through Android MediaStore.
- Photos are shown newest-first.
- Videos have a dedicated tab.
- Albums are derived from Android media bucket information rather than requiring hard-coded folder names.
- Existing media is not copied or moved just to display it.
- Thumbnails are loaded through Android's media APIs.
- Full-screen photo viewing is available.
- Video playback is available.
- Share uses the existing media URI with temporary read permission.
- Delete uses Android's user-confirmed media deletion flow on Android 11+.
- Gallery permission is requested only when the Gallery is opened.

## Storage model

Existing photos and videos are discovered automatically from Android's media library. The app does not require a manually configured Gallery folder.

New camera media continues to use the app's `DCIM/Media Toolbox` location. A configurable scanner/PDF destination will be added with the Scanner milestone.

## Architecture principles

- Native Android/Kotlin.
- CameraX for camera lifecycle, photo capture and video recording.
- MediaStore for user-visible media storage and Gallery discovery.
- No network service, account, database, overlay, accessibility service, or unnecessary background service.
- Only camera and media-read permissions are declared for the current features.
- Camera resources are unbound when the activity is destroyed.
- Failed photo/video writes are cleaned up where Android exposes a removable pending media entry.
- Gallery does not upload or transmit media.

## Development policy

APK builds are intentionally disabled from automatic execution while the remaining versions are developed. Device/build verification will happen after the planned feature set is complete.
