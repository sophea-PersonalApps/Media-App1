from pathlib import Path

# Existing scanner layout work is intentionally retained; this finalizer only delegates
# to the current gallery pager implementation and audits the generated result.
exec(Path("tools/fix_gallery_pager.py").read_text(encoding="utf-8"), globals())

scanner = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/ScannerActivity.kt").read_text(encoding="utf-8")
cap_start = scanner.find("@Composable\nprivate fun ScannerCapture(")
cap_end = scanner.find("\n@Composable", cap_start + 1)
if cap_start < 0 or cap_end < 0:
    raise SystemExit("FINAL UI AUDIT FAILED: ScannerCapture boundary not found")
cap = scanner[cap_start:cap_end]

gallery = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt").read_text(encoding="utf-8")

checks = {
    "scanner camera-sized preview region": "CameraSectionPreview(" in cap,
    "scanner controls below preview": "CameraSectionControls(" in cap,
    "scanner bottom navigation below controls": "CameraSectionBottomNavigation(" in cap,
    "scanner shared controls": "mode = CameraSectionMode.SCAN" in cap and "onPrimaryAction = onCapture" in cap,
    "scanner PAGES action": "onFlip = onFinish" in cap and "scannerPageCount = pages.size" in cap,
    "scanner old controls removed": 'Text("Finish")' not in cap and "LazyRow(" not in cap and 'Text("MORE"' not in cap,
    "scanner capture header removed": 'Text("Scanner", color = ComposeColor.White' not in cap and 'IconButton(onClick = onBack)' not in cap,
    "gallery stable viewer": ".pointerInput(uri, currentIndex)" in gallery and "detectTransformGestures" in gallery and "val focalX = centroid.x - viewportWidth / 2f" in gallery,
    "gallery no adjacent media": "AdjacentMedia(" not in gallery and "swipeOffset" not in gallery and "videoNavigationStarted" not in gallery,
    "gallery no swipe state": "detectHorizontalDragGestures" not in gallery,
    "gallery zoom pan": "mediaPanX" in gallery and "mediaPanY" in gallery and "mediaZoom" in gallery,
    "gallery focal point": "focalX = centroid.x - viewportWidth / 2f" in gallery and "focalY = centroid.y - viewportHeight / 2f" in gallery,
    "gallery smooth pinch": "coerceIn(0.5f, 2f)" in gallery,
    "gallery video speed": "VideoPlayer(uri, videoSpeed)" in gallery,
    "gallery full-image cache": "fun getFull(uri: Uri)" in gallery and "fun putFull(uri: Uri, bitmap: Bitmap)" in gallery and "ThumbnailMemoryCache.get(uri, false)" in gallery,
    "gallery action row above media": "zIndex" in gallery,
    "main forward slide": "R.anim.slide_in_right" in Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt").read_text(encoding="utf-8"),
    "qr reverse navigation": "R.anim.slide_out_right" in Path("app/src/main/java/com/devlinguistpro/mediatoolbox/QrScannerActivity.kt").read_text(encoding="utf-8"),
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("FINAL UI AUDIT FAILED: " + "; ".join(failed))
print("Final gallery/scanner/QR layout audit passed: stable no-swipe gallery viewer and shared scanner chrome.")
