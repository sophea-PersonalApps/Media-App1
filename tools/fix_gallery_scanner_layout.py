from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"
GALLERY = ROOT / "GalleryActivity.kt"

# Scanner/QR structure is owned by patch_requested_ui.py. Gallery gesture cleanup
# is owned by the following final gesture stage. This stage must not reject or
# overwrite either implementation, which was the source of the repeated short
# failures.
exec(Path("tools/fix_gallery_pager.py").read_text(encoding="utf-8"), globals())

main = MAIN.read_text(encoding="utf-8")
if 'startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)' not in main:
    main = main.replace(
        'startActivity(Intent(this, ScannerActivity::class.java))',
        'startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)'
    )
if 'startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)' not in main:
    main = main.replace(
        'startActivity(Intent(this, QrScannerActivity::class.java))',
        'startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)'
    )
MAIN.write_text(main, encoding="utf-8")

qr = QR.read_text(encoding="utf-8")
qr = qr.replace('overridePendingTransition(0, 0)', 'overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)')
QR.write_text(qr, encoding="utf-8")

gallery = GALLERY.read_text(encoding="utf-8")
required = {
    "fixed media viewer": 'MediaViewer(uri: Uri, isVideo: Boolean, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit)' in gallery,
    "focal point zoom": 'val focalX = centroid.x - viewportWidth / 2f' in gallery and 'val focalY = centroid.y - viewportHeight / 2f' in gallery,
    "smooth pinch": 'coerceIn(0.5f, 2f)' in gallery,
    "responsive zoom pan": 'mediaZoom' in gallery and 'mediaPanX' in gallery and 'mediaPanY' in gallery,
    "video speed": 'VideoPlayer(uri, videoSpeed)' in gallery,
}
for name, ok in required.items():
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in required.items() if not ok]
if failed:
    raise SystemExit("GALLERY FINALIZER FAILED: " + "; ".join(failed))
print("Gallery finalizer passed; scanner/QR left to their dedicated stages and no-swipe cleanup left to the final gesture stage.")
