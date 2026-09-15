from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")

# The final viewer is fixed to one media item. Remove stale pager-only imports and
# the old navigation callback that otherwise make the no-swipe audit fail.
text = text.replace("import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n", "")
text = text.replace("items: List<MediaItem>, currentIndex: Int, onNavigate: (Int) -> Unit, onBack:", "onBack:")
old_call = "MediaViewer(selectedUri!!, selectedIsVideo, media, selectedIndex, { index -> if (index in media.indices) { selectedIndex = index; selectedUri = media[index].uri; selectedIsVideo = media[index].isVideo } }, { selectedUri = null; selectedIndex = -1 }, { shareMedia(context, selectedUri!!) }, if (selectedIsVideo) null else ({ onEdit(selectedUri!!) })) { val uriBeingDeleted = selectedUri!!; onDelete(uriBeingDeleted) { deleted -> if (deleted) { selectedUri = null; selectedIndex = -1; refreshToken++ } } }"
new_call = "MediaViewer(selectedUri!!, selectedIsVideo, { selectedUri = null; selectedIndex = -1 }, { shareMedia(context, selectedUri!!) }, if (selectedIsVideo) null else ({ onEdit(selectedUri!!) })) { val uriBeingDeleted = selectedUri!!; onDelete(uriBeingDeleted) { deleted -> if (deleted) { selectedUri = null; selectedIndex = -1; refreshToken++ } } }"
if old_call in text:
    text = text.replace(old_call, new_call, 1)

required = {
    "stable gallery pointer input": ".pointerInput(uri, currentIndex)" in text,
    "gallery transform gestures": "detectTransformGestures" in text,
    "focal point zoom": "val focalX = centroid.x - viewportWidth / 2f" in text and "val focalY = centroid.y - viewportHeight / 2f" in text,
    "smooth pinch ratio": "coerceIn(0.5f, 2f)" in text,
    "responsive continuous pan": "mediaPanX = (mediaPanX + pan.x)" in text and "mediaPanY = (mediaPanY + pan.y)" in text,
    "no adjacent pager": "AdjacentMedia(" not in text and "previousItem" not in text and "nextItem" not in text,
    "no swipe animation": "swipeOffset" not in text and "videoNavigationStarted" not in text and "detectHorizontalDragGestures" not in text,
    "no gallery navigation callback": "onNavigate(" not in text,
}
for name, ok in required.items():
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in required.items() if not ok]
if failed:
    raise SystemExit("GALLERY GESTURE AUDIT FAILED: " + "; ".join(failed))
path.write_text(text, encoding="utf-8")
print("Gallery gesture audit passed: one fixed media item with direct pinch/pan only; swipe-between-items is explicitly disabled.")
