# Media Toolbox

Native Android media utility app.

## Version 0.1

The first milestone is deliberately limited to the camera foundation:

- Camera opens as the primary screen.
- Runtime camera permission is requested only when needed.
- Rear/front camera switching is available.
- Photo capture saves to Android MediaStore under `DCIM/Media Toolbox`.
- Camera, Video, Scan and QR modes are represented by a non-looping centre-focused mode selector so the later features can be added without redesigning navigation.
- Gallery navigation is reserved for the next milestone.

## Architecture principles

- Native Android/Kotlin.
- CameraX for camera lifecycle and capture.
- MediaStore for user-visible photo storage.
- No network service, account, database, overlay, accessibility service, or unnecessary permissions in 0.1.
- Camera resources are unbound when the activity is destroyed.
- Failed photo writes are cleaned up rather than leaving pending MediaStore entries.

## Build

The GitHub Actions workflow builds the debug APK and uploads it as the `media-toolbox-debug` artifact.
