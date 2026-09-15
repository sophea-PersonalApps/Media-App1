from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
MAIN = ROOT / "MainActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
QR = ROOT / "QrScannerActivity.kt"
GALLERY = ROOT / "GalleryActivity.kt"

# This stage owns only scanner/QR chrome. GalleryActivity is deliberately
# untouched here; the final gallery stages own all gallery behavior.
main = MAIN.read_text(encoding="utf-8")
scanner = SCANNER.read_text(encoding="utf-8")
qr = QR.read_text(encoding="utf-8")
gallery_before = GALLERY.read_text(encoding="utf-8")

scanner = scanner.replace(
    '            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {\n                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ComposeColor.White) }\n                Text("Scanner", color = ComposeColor.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)\n                IconButton(onClick = onFlip) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = ComposeColor.White) }\n            }\n', '')

# Preserve existing camera/scanner architecture and only replace the old
# activity-transition animation when the exact legacy value is present.
main = main.replace('overridePendingTransition(0, 0)', 'overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)')
scanner = scanner.replace('overridePendingTransition(0, 0)', 'overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)')
qr = qr.replace('overridePendingTransition(0, 0)', 'overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)')

qr = qr.replace(
    '                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {\n                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }\n                    Text("QR SCANNER", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)\n                    IconButton(onClick = onFlip, enabled = result == null) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White) }\n                }\n',
    '                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }\n')

MAIN.write_text(main, encoding="utf-8")
SCANNER.write_text(scanner, encoding="utf-8")
QR.write_text(qr, encoding="utf-8")

gallery_after = GALLERY.read_text(encoding="utf-8")
if gallery_after != gallery_before:
    raise SystemExit("REQUESTED UI PATCH BUG: GalleryActivity changed")
for token in ("var selectedIndex", "onNavigate(", "currentIndex + 1", "currentIndex - 1", "AdjacentMedia(", "swipeOffset", "videoNavigationStarted"):
    if token in gallery_after:
        raise SystemExit("REQUESTED UI PATCH BUG: stale gallery navigation token: " + token)

checks = {
    "scanner header removed": 'Text("Scanner", color = ComposeColor.White' not in scanner,
    "scanner shared bottom controls": 'CameraSectionControls(' in scanner and 'CameraSectionBottomNavigation(' in scanner,
    "scanner no capture back button": 'IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ComposeColor.White) }' not in scanner,
    "QR header removed": 'Text("QR SCANNER", color = Color.White' not in qr,
    "gallery untouched": gallery_after == gallery_before,
}
for name, ok in checks.items(): print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks.items() if not ok]
if failed: raise SystemExit("REQUESTED UI AUDIT FAILED: " + "; ".join(failed))
print("Requested UI patch passed: scanner/QR only; GalleryActivity was not modified.")
