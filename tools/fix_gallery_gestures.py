from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")

# Final viewer is intentionally a single fixed media item. Remove any stale
# pager-only import/callback text left by earlier compatibility patches.
text = text.replace("import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n", "")
text = text.replace("items: List<MediaItem>, currentIndex: Int, onNavigate: (Int) -> Unit, onBack:", "onBack:")

required = {
    "stable gallery pointer input": ".pointerInput(uri)" in text,
    "gallery transform gestures": "detectTransformGestures" in text,
    "focal point zoom": "val focalX = centroid.x - viewportWidth / 2f" in text and "val focalY = centroid.y - viewportHeight / 2f" in text,
    "smooth pinch ratio": "coerceIn(0.5f, 2f)" in text,
    "responsive continuous pan": "mediaPanX = (mediaPanX + pan.x)" in text and "mediaPanY = (mediaPanY + pan.y)" in text,
    "no adjacent pager": "AdjacentMedia(" not in text and "previousItem" not in text and "nextItem" not in text,
    "no swipe animation": "swipeOffset" not in text and "videoNavigationStarted" not in text and "detectHorizontalDragGestures" not in text,
    "no gallery navigation callback": "onNavigate(" not in text,
    "fixed media viewer": "MediaViewer(uri: Uri, isVideo: Boolean, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit)" in text,
}
for name, ok in required.items():
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in required.items() if not ok]
if failed:
    raise SystemExit("GALLERY GESTURE AUDIT FAILED: " + "; ".join(failed))
path.write_text(text, encoding="utf-8")
print("Gallery gesture audit passed: one fixed media item with direct pinch/pan only; swipe-between-items is explicitly disabled.")
