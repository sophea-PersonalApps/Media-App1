# Media Toolbox

Native Android/Kotlin media utility app.

## Current version: 0.6

0.6 completes the planned camera, video, gallery, editor, document scanner and QR scanner feature set.

### Camera
- Camera is the primary screen and opens in Camera mode.
- Runtime camera permission is requested only when needed.
- Rear/front camera switching is available.
- Photo capture saves to Android MediaStore under `DCIM/Media Toolbox`.
- Video capture saves to Android MediaStore under `DCIM/Media Toolbox` on Android 10+.
- Camera, Video, Scan and QR use a non-looping centre-focused mode selector.
- Modes can be selected by tapping adjacent labels or swiping across the preview area.
- The layout uses adaptive Compose sizing so controls have smaller side margins on narrow displays.
- Camera and Gallery are persistent navigation destinations.
- More camera options provide a real Off/Auto/On flash control. HDR is not forced because support varies between devices.

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

### Gallery editor
- Brightness, contrast and saturation adjustments.
- 90-degree left/right rotation.
- Horizontal/vertical flipping.
- Edited photos are saved as a new JPEG, leaving the original intact.

### Document scanner
- Captures one or multiple pages.
- Shows captured-page thumbnails while scanning.
- Individual pages can be removed.
- Finish opens a preview before saving.
- PDF output contains one page per captured scan.
- The user chooses the destination folder through Android's document picker.
- The selected PDF folder is remembered for later scans.
- Temporary scan files are cleaned up after a successful save or when the scanner is finished.

### QR scanner
- Dedicated QR scanning activity.
- Continuous CameraX image analysis.
- ML Kit QR-only detection.
- Correct camera-frame rotation is supplied to the scanner.
- Detection locks after a valid result to prevent repeated triggers.
- Displays the decoded QR contents.
- Copies decoded text to the clipboard.
- Opens HTTP/HTTPS QR results only after the user presses `Open link`.
- Non-web QR contents are never treated as links automatically.
- `Scan another` unlocks the scanner for another QR code.
- Rear/front camera switching is available.
- Camera permission is handled at runtime.

## Storage model

Existing photos and videos are discovered automatically from Android's media library. The app does not require a manually configured Gallery folder.

New camera photos/videos continue to use the app's `DCIM/Media Toolbox` location. Scanned PDFs use the user-selected document-tree folder.

## Compatibility and architecture

- Minimum Android API: 26.
- Android 11 (API 30) is supported by the storage and permission paths used by the app.
- Newer Android releases use their newer media-permission behavior where required.
- Native Android/Kotlin.
- CameraX for camera lifecycle, photo capture, video recording and scanner camera access.
- MediaStore for user-visible camera media and Gallery discovery.
- Android document-provider APIs for user-selected PDF folders.
- ML Kit for on-device QR recognition.
- No network service, account, database, overlay, accessibility service, or unnecessary background service.
- Camera resources are unbound when activities are destroyed.
- Failed photo/video/PDF writes are cleaned up where Android exposes a removable entry.
- Gallery, scanner and QR data remain on-device; there is no upload service.

## Development policy

APK builds are intentionally verified after the planned feature set is complete. Device verification should include Android 11 and at least one newer Android version, plus a narrow-display test, because hardware camera capabilities vary by device.
