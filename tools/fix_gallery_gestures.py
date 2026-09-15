from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")

# Gallery navigation intentionally does not swipe between media items anymore.
# Keep this final step as a strict audit so later workflow stages cannot reintroduce
# the old pager/adjacent-item gesture implementation.
required = {
    "stable gallery pointer input": ".pointerInput(uri, currentIndex)" in text,
    "gallery transform gestures": "detectGalleryTransformGestures" in text,
    "focal point zoom": "val focalX = centroid.x - viewportWidth / 2f" in text and "val focalY = centroid.y - viewportHeight / 2f" in text,
    "smooth pinch ratio": "coerceIn(0.5f, 2f)" in text,
    "no adjacent pager": "AdjacentMedia(" not in text,
    "no swipe animation": "swipeOffset" not in text and "videoNavigationStarted" not in text,
}
for name, ok in required.items():
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in required.items() if not ok]
if failed:
    raise SystemExit("GALLERY GESTURE AUDIT FAILED: " + "; ".join(failed))
print("Gallery gesture audit passed: stable pinch/pan only; swipe-between-items remains disabled.")
