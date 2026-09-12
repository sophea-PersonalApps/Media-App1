from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
GALLERY = ROOT / "GalleryActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"


def balanced_call(text, marker, label):
    start = text.find(marker)
    if start < 0:
        raise SystemExit(f"{label}: marker not found")
    open_pos = text.find("(", start)
    depth = 0
    for i in range(open_pos, len(text)):
        if text[i] == "(": depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                end = i + 1
                while end < len(text) and text[end] in " \t\n": end += 1
                if end < len(text) and text[end] == ",": end += 1
                return start, end
    raise SystemExit(f"{label}: unmatched parentheses")


def replace_once(text, old, new, label):
    n = text.count(old)
    if n == 1: return text.replace(old, new, 1)
    if n == 0 and new in text: return text
    raise SystemExit(f"{label}: expected 1 match, found {n}")

# Scanner: the generated baseline has CameraSectionControls(SCAN). Replace
# that whole call with the exact requested scanner controls: MORE | shutter | PAGES.
scanner = SCANNER.read_text(encoding="utf-8")
if "import androidx.compose.foundation.layout.width" not in scanner:
    anchor = "import androidx.compose.foundation.layout.height\n"
    if anchor in scanner:
        scanner = scanner.replace(anchor, anchor + "import androidx.compose.foundation.layout.width\n", 1)
if "CameraSectionControls(" in scanner:
    start, end = balanced_call(scanner, "CameraSectionControls(", "scanner controls")
    custom = '''Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }) {
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MoreVert, "More", tint = ComposeColor.White)
                        }
                        Text("MORE", color = ComposeColor.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        Modifier.size(72.dp).background(ComposeColor.White, CircleShape).padding(5.dp).clickable(onClick = onCapture),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(58.dp).background(ComposeColor.White, CircleShape))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = pages.isNotEmpty(), onClick = onFinish)) {
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Folder, "Pages", tint = ComposeColor.White)
                        }
                        Text("PAGES ${pages.size}", color = ComposeColor.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                },'''
    scanner = scanner[:start] + custom + scanner[end:]
# Remove any remaining capture-screen back/flip controls.
scanner = scanner.replace('IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ComposeColor.White) }\n', '')
scanner = scanner.replace('IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to scanner", tint = ComposeColor.White) }\n', '')
scanner = scanner.replace('IconButton(onClick = onFlip) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = ComposeColor.White) }\n', '')
# Forward navigation from scanner to camera/video/gallery/QR uses the same slide-in-right direction.
for old, new in [("android.R.anim.slide_in_right", "R.anim.slide_in_right"), ("android.R.anim.slide_out_left", "R.anim.slide_out_left"), ("android.R.anim.slide_in_left", "R.anim.slide_in_left"), ("android.R.anim.slide_out_right", "R.anim.slide_out_right")]: scanner = scanner.replace(old, new)
scanner = scanner.replace("overridePendingTransition(0, 0)", "overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)")
SCANNER.write_text(scanner, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
for old, new in [("android.R.anim.slide_in_right", "R.anim.slide_in_right"), ("android.R.anim.slide_out_left", "R.anim.slide_out_left"), ("android.R.anim.slide_in_left", "R.anim.slide_in_left"), ("android.R.anim.slide_out_right", "R.anim.slide_out_right")]: main = main.replace(old, new)
main = main.replace("overridePendingTransition(0, 0)", "overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)")
MAIN.write_text(main, encoding="utf-8")

qr = QR.read_text(encoding="utf-8")
qr = qr.replace('IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }\n', '')
for old, new in [("android.R.anim.slide_in_right", "R.anim.slide_in_right"), ("android.R.anim.slide_out_left", "R.anim.slide_out_left"), ("android.R.anim.slide_in_left", "R.anim.slide_in_left"), ("android.R.anim.slide_out_right", "R.anim.slide_out_right")]: qr = qr.replace(old, new)
qr = qr.replace("overridePendingTransition(0, 0)", "overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)")
QR.write_text(qr, encoding="utf-8")

# Gallery: keep the proven continuous adjacent-image implementation from the
# preceding patch. Only add missing z-order/animation imports or state if needed.
gallery = GALLERY.read_text(encoding="utf-8")
if "import androidx.compose.ui.zIndex" not in gallery:
    gallery = replace_once(gallery, "import androidx.compose.ui.viewinterop.AndroidView\n", "import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.ui.zIndex\n", "gallery zIndex import")
if ".zIndex(10f)" not in gallery:
    gallery = replace_once(gallery, "Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {", "Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {", "gallery action row")

# The generated gallery must already contain the continuous swipe state after
# patch_requested_ui/add_gallery_swipe_import. Do not silently accept a partial
# implementation: audit the exact behaviour before Gradle.
checks = {
    "scanner pages": 'PAGES' in scanner and 'onFinish' in scanner,
    "scanner more": 'MORE' in scanner,
    "scanner shutter": 'onCapture' in scanner,
    "scanner top back removed": 'Back to scanner' not in scanner,
    "gallery interactive swipe": 'swipeOffset' in gallery and 'Animatable' in gallery,
    "gallery adjacent photos": 'previousBitmap' in gallery and 'nextBitmap' in gallery,
    "gallery smooth commit": 'tween(180)' in gallery,
    "gallery action row above media": '.zIndex(10f)' in gallery,
    "main forward slide": 'R.anim.slide_in_right' in main and 'R.anim.slide_out_left' in main,
    "qr reverse slide": 'R.anim.slide_in_left' in qr and 'R.anim.slide_out_right' in qr,
    "qr top back removed": 'Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)' not in qr,
}
for name, ok in checks.items(): print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks.items() if not ok]
if failed: raise SystemExit("FINAL UI AUDIT FAILED: " + "; ".join(failed))
GALLERY.write_text(gallery, encoding="utf-8")
