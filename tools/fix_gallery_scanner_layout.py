from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
GALLERY = ROOT / "GalleryActivity.kt"
EDITOR = ROOT / "GalleryEditorActivity.kt"

# Keep this stage narrowly scoped. Scanner/QR structure is owned by
# patch_requested_ui.py; the gallery gesture cleanup is owned by the final
# gesture stage that runs immediately after this one.
exec(Path("tools/fix_gallery_pager.py").read_text(encoding="utf-8"), globals())

main = MAIN.read_text(encoding="utf-8")
for old, new in [
    (
        'startActivity(Intent(this, ScannerActivity::class.java))',
        'startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)'
    ),
    (
        'startActivity(Intent(this, QrScannerActivity::class.java))',
        'startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)'
    ),
]:
    if new not in main:
        main = main.replace(old, new)
main = main.replace(
    'startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)',
    'startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)'
)
main = main.replace(
    'startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)',
    'startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)'
)
MAIN.write_text(main, encoding="utf-8")

qr = QR.read_text(encoding="utf-8")
qr = qr.replace('overridePendingTransition(0, 0)', 'overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)')
QR.write_text(qr, encoding="utf-8")

gallery = GALLERY.read_text(encoding="utf-8")
scanner = SCANNER.read_text(encoding="utf-8")
editor = EDITOR.read_text(encoding="utf-8")
cap_start = scanner.find("@Composable\nprivate fun ScannerCapture(")
cap_end = scanner.find("\n@Composable", cap_start + 1)
if cap_start < 0 or cap_end < 0:
    raise SystemExit("FINAL UI AUDIT FAILED: ScannerCapture boundary not found")
cap = scanner[cap_start:cap_end]

checks = {
    "scanner shared chrome": "mode = CameraSectionMode.SCAN" in cap and "CameraSectionBottomNavigation(" in cap and "onPrimaryAction = onCapture" in cap,
    "scanner PAGES action": "onFlip = onFinish" in cap and "scannerPageCount = pages.size" in cap,
    "scanner old controls removed": 'Text("Finish")' not in cap and "LazyRow(" not in cap and 'Text("MORE"' not in cap,
    "scanner header removed": 'Text("Scanner", color = ComposeColor.White' not in cap and 'IconButton(onClick = onBack)' not in cap,
    "gallery fixed viewer": 'MediaViewer(uri: Uri, isVideo: Boolean, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit)' in gallery and 'detectTransformGestures' in gallery,
    "gallery focal point zoom": 'val focalX = centroid.x - viewportWidth / 2f' in gallery and 'val focalY = centroid.y - viewportHeight / 2f' in gallery,
    "gallery smooth pinch": 'coerceIn(0.5f, 2f)' in gallery,
    "gallery zoom pan": 'mediaZoom' in gallery and 'mediaPanX' in gallery and 'mediaPanY' in gallery,
    "gallery video speed": 'VideoPlayer(uri, videoSpeed)' in gallery,
    "gallery full-image cache": 'fun getFull(uri: Uri)' in gallery and 'fun putFull(uri: Uri, bitmap: Bitmap)' in gallery and 'ThumbnailMemoryCache.get(uri, false)' in gallery,
    "gallery crop preserved": 'CropOverlay(' in editor and 'Done Crop' in editor,
    "main forward transition": 'R.anim.slide_in_right' in main and 'R.anim.slide_out_left' in main,
    "qr reverse transition": 'R.anim.slide_in_left' in qr and 'R.anim.slide_out_right' in qr,
}
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("FINAL UI AUDIT FAILED: " + "; ".join(failed))
print("Gallery/scanner finalizer passed: scanner structure isolated; gallery finalized before the no-swipe gesture stage.")
