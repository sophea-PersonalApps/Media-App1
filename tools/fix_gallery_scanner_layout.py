from pathlib import Path
import re

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
SCANNER = ROOT / "ScannerActivity.kt"
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"
GALLERY = ROOT / "GalleryActivity.kt"

def balanced_block(text, start):
    op = text.find("{")
    if op < 0: raise SystemExit("Could not find opening brace")
    depth = 0
    for i in range(op, len(text)):
        if text[i] == "{": depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0: return start, i + 1
    raise SystemExit("Unbalanced Kotlin block")

scanner = SCANNER.read_text(encoding="utf-8")
cap_start = scanner.find("@Composable\nprivate fun ScannerCapture(")
cap_end = scanner.find("\n@Composable", cap_start + 1)
if cap_start < 0 or cap_end < 0: raise SystemExit("ScannerCapture function boundary not found")
cap = scanner[cap_start:cap_end]
page_marker = cap.find('Text("${pages.size} page${if (pages.size == 1) "" else "s"}')
if page_marker < 0: raise SystemExit("Scanner page-count control not found")
box_start = cap.find('    Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {')
if box_start < 0: raise SystemExit("ScannerCapture outer Box not found")
box_a, box_e = balanced_block(cap, box_start)
replacement = '''Column(Modifier.fillMaxSize().background(ComposeColor.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        onPreviewReady(this)
                    } },
                    modifier = Modifier.fillMaxSize()
                )
                ScannerPageGuide(detectedQuad)
            }
            CameraSectionControls(
                mode = CameraSectionMode.SCAN,
                onModeSelected = { scannerModeAction(context, it) },
                onPrimaryAction = onCapture,
                onFlip = onFinish,
                onMore = { },
                primaryEnabled = true,
                scannerPageCount = pages.size
            )
            CameraSectionBottomNavigation(
                cameraSelected = true,
                onCamera = onOpenCamera,
                onGallery = onOpenGallery
            )
        }'''
cap = cap[:box_a] + replacement + cap[box_e:]
scanner = scanner[:cap_start] + cap + scanner[cap_end:]
scanner = scanner.replace(
    'CameraSectionMode.PHOTO, CameraSectionMode.VIDEO -> context.startActivity(Intent(context, MainActivity::class.java).putExtras(cameraModeIntent(mode)))',
    'CameraSectionMode.PHOTO, CameraSectionMode.VIDEO -> { context.startActivity(Intent(context, MainActivity::class.java).putExtras(cameraModeIntent(mode))); (context as? ComponentActivity)?.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right) }')
scanner = scanner.replace(
    'CameraSectionMode.QR -> context.startActivity(Intent(context, QrScannerActivity::class.java))',
    'CameraSectionMode.QR -> { context.startActivity(Intent(context, QrScannerActivity::class.java)); (context as? ComponentActivity)?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left) }')
SCANNER.write_text(scanner, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
MAIN.write_text(main.replace('overridePendingTransition(0, 0)', 'overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)'), encoding="utf-8")
qr = QR.read_text(encoding="utf-8")
QR.write_text(qr.replace('overridePendingTransition(0, 0)', 'overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)'), encoding="utf-8")

gallery = GALLERY.read_text(encoding="utf-8")
imports = [
    'import androidx.compose.animation.core.Animatable',
    'import androidx.compose.animation.core.tween',
    'import androidx.compose.foundation.gestures.detectHorizontalDragGestures',
    'import androidx.compose.foundation.gestures.detectTransformGestures',
    'import androidx.compose.ui.zIndex',
    'import androidx.compose.ui.layout.onSizeChanged',
    'import kotlinx.coroutines.launch',
]
anchor = 'import androidx.compose.foundation.combinedClickable\n'
for imp in imports:
    if imp not in gallery:
        gallery = gallery.replace(anchor, anchor + imp + '\n', 1) if anchor in gallery else gallery
GALLERY.write_text(gallery, encoding="utf-8")
exec(Path("tools/fix_gallery_pager.py").read_text(encoding="utf-8"), globals())
gallery = GALLERY.read_text(encoding="utf-8")
for imp in imports:
    if imp not in gallery:
        gallery = gallery.replace(anchor, anchor + imp + '\n', 1) if anchor in gallery else gallery
GALLERY.write_text(gallery, encoding="utf-8")

scanner_final = SCANNER.read_text(encoding="utf-8")
cap_start = scanner_final.find("@Composable\nprivate fun ScannerCapture(")
cap_end = scanner_final.find("\n@Composable", cap_start + 1)
if cap_start < 0 or cap_end < 0: raise SystemExit("Final ScannerCapture boundary not found")
cap = scanner_final[cap_start:cap_end]
main_final = MAIN.read_text(encoding="utf-8")
qr_final = QR.read_text(encoding="utf-8")
gallery_final = GALLERY.read_text(encoding="utf-8")
checks = {
    "scanner camera-sized preview region": 'Box(Modifier.fillMaxWidth().weight(1f))' in cap and 'AndroidView(' in cap and 'ScannerPageGuide(detectedQuad)' in cap,
    "scanner controls below preview": cap.find('CameraSectionControls(') > cap.find('Box(Modifier.fillMaxWidth().weight(1f))'),
    "scanner bottom navigation below controls": cap.find('CameraSectionBottomNavigation(') > cap.find('CameraSectionControls('),
    "scanner shared controls": 'mode = CameraSectionMode.SCAN' in cap and 'onPrimaryAction = onCapture' in cap,
    "scanner PAGES action": 'onFlip = onFinish' in cap and 'scannerPageCount = pages.size' in cap,
    "scanner old controls removed": 'Text("Finish")' not in cap and 'LazyRow(' not in cap and 'Text("MORE"' not in cap,
    "scanner capture header removed": 'Text("Scanner", color = ComposeColor.White' not in cap and 'IconButton(onClick = onBack)' not in cap,
    "gallery animation imports": 'import androidx.compose.animation.core.Animatable' in gallery_final and 'import androidx.compose.animation.core.tween' in gallery_final,
    "gallery gesture imports": 'import androidx.compose.foundation.gestures.detectHorizontalDragGestures' in gallery_final and 'import androidx.compose.foundation.gestures.detectTransformGestures' in gallery_final,
    "gallery zIndex import": 'import androidx.compose.ui.zIndex' in gallery_final,
    "gallery coroutine import": 'import kotlinx.coroutines.launch' in gallery_final,
    "gallery interactive pager": 'Animatable' in gallery_final and 'swipeOffset' in gallery_final and 'animateTo' in gallery_final,
    "gallery adjacent media": 'AdjacentMedia(' in gallery_final and 'previousItem' in gallery_final and 'nextItem' in gallery_final,
    "gallery zoom pan": 'detectTransformGestures' in gallery_final and 'photoPanX' in gallery_final and 'photoPanY' in gallery_final,
    "gallery action row above media": '.zIndex(10f)' in gallery_final,
    "main forward slide": 'R.anim.slide_in_right' in main_final and 'R.anim.slide_out_left' in main_final,
    "qr reverse navigation": 'R.anim.slide_in_left' in qr_final and 'R.anim.slide_out_right' in qr_final,
}
for name, ok in checks.items(): print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks.items() if not ok]
if failed: raise SystemExit("FINAL UI AUDIT FAILED: " + "; ".join(failed))
print("Final gallery/scanner/QR layout audit passed.")