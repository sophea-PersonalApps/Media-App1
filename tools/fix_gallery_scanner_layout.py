from pathlib import Path
import re

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
GALLERY = ROOT / "GalleryActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"

def balanced_call(text, marker, label):
    start = text.find(marker)
    if start < 0: raise SystemExit(f"{label}: marker not found")
    open_pos = text.find("(", start); depth = 0
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

scanner = SCANNER.read_text(encoding="utf-8")
if "import androidx.compose.foundation.layout.width" not in scanner:
    anchor = "import androidx.compose.foundation.layout.height\n"
    if anchor in scanner: scanner = scanner.replace(anchor, anchor + "import androidx.compose.foundation.layout.width\n", 1)
if "CameraSectionControls(" in scanner:
    start, end = balanced_call(scanner, "CameraSectionControls(", "scanner controls")
    custom = '''// ScannerSectionControls: MORE | shutter | PAGES\n                Row(\n                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),\n                    horizontalArrangement = Arrangement.SpaceBetween,\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }) {\n                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {\n                            Icon(Icons.Default.MoreVert, "More", tint = ComposeColor.White)\n                        }\n                        Text("MORE", color = ComposeColor.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)\n                    }\n                    Box(\n                        Modifier.size(72.dp).background(ComposeColor.White, CircleShape).padding(5.dp).clickable(onClick = onCapture),\n                        contentAlignment = Alignment.Center\n                    ) {\n                        Box(Modifier.size(58.dp).background(ComposeColor.White, CircleShape))\n                    }\n                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = pages.isNotEmpty(), onClick = onFinish)) {\n                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {\n                            Icon(Icons.Default.Folder, "Pages", tint = ComposeColor.White)\n                        }\n                        Text("PAGES ${pages.size}", color = ComposeColor.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)\n                    }\n                },'''
    scanner = scanner[:start] + custom + scanner[end:]
scanner = scanner.replace('IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ComposeColor.White) }\n', '')
scanner = scanner.replace('IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to scanner", tint = ComposeColor.White) }\n', '')
scanner = scanner.replace('IconButton(onClick = onFlip) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = ComposeColor.White) }\n', '')
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

gallery = GALLERY.read_text(encoding="utf-8")
if "import androidx.compose.ui.zIndex" not in gallery:
    gallery = replace_once(gallery, "import androidx.compose.ui.viewinterop.AndroidView\n", "import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.ui.zIndex\n", "gallery zIndex import")
if "import androidx.compose.animation.core.Animatable" not in gallery:
    anchor = "import androidx.compose.foundation.gestures.detectTransformGestures\n"
    if anchor in gallery: gallery = gallery.replace(anchor, anchor + "import androidx.compose.animation.core.Animatable\nimport androidx.compose.animation.core.tween\n", 1)
    else: gallery = gallery.replace("import androidx.compose.foundation", "import androidx.compose.animation.core.Animatable\nimport androidx.compose.animation.core.tween\nimport androidx.compose.foundation", 1)
if ".zIndex(10f)" not in gallery:
    gallery = replace_once(gallery, "Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {", "Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {", "gallery action row")
state_marker = "var viewportHeight by remember(uri) { mutableIntStateOf(0) }"
if "val swipeOffset = remember" not in gallery:
    if state_marker not in gallery: raise SystemExit("gallery viewport state not found")
    gallery = gallery.replace(state_marker, state_marker + '''\n                        val swipeOffset = remember { Animatable(0f) }\n                        val previousUri = if (currentIndex > 0) items[currentIndex - 1].uri else null\n                        val nextUri = if (currentIndex >= 0 && currentIndex < items.lastIndex) items[currentIndex + 1].uri else null\n                        var previousBitmap by remember(currentIndex) { mutableStateOf<Bitmap?>(null) }\n                        var nextBitmap by remember(currentIndex) { mutableStateOf<Bitmap?>(null) }\n                        LaunchedEffect(currentIndex, previousUri, nextUri) {\n                            previousBitmap = previousUri?.let { u -> withContext(Dispatchers.IO) { context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) } } }\n                            nextBitmap = nextUri?.let { u -> withContext(Dispatchers.IO) { context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) } } }\n                        }\n''', 1)
old_gesture_start = '.pointerInput(uri, currentIndex) {\n                                    var dragX = 0f\n                                    detectTransformGestures { _, pan, zoom, _ ->'
if old_gesture_start in gallery:
    old_gesture_end = '''                                    }\n                                },'''
    start = gallery.find(old_gesture_start); end = gallery.find(old_gesture_end, start)
    if end < 0: raise SystemExit("gallery basic gesture end not found")
    end += len(old_gesture_end)
    new_gesture = '''.pointerInput(uri, currentIndex) {\n                                    detectTransformGestures { _, pan, zoom, _ ->\n                                        val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)\n                                        val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f\n                                        val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f\n                                        photoZoom = newZoom\n                                        if (newZoom <= 1f) {\n                                            photoPanX = 0f\n                                            photoPanY = 0f\n                                            if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {\n                                                val maxOffset = viewportWidth.toFloat()\n                                                swipeOffset.snapTo((swipeOffset.value + pan.x).coerceIn(-maxOffset, maxOffset))\n                                            }\n                                        } else {\n                                            photoPanX = (photoPanX + pan.x).coerceIn(-maxPanX, maxPanX)\n                                            photoPanY = (photoPanY + pan.y).coerceIn(-maxPanY, maxPanY)\n                                            swipeOffset.snapTo(0f)\n                                        }\n                                    }\n                                    val threshold = viewportWidth.toFloat() * 0.5f\n                                    val targetIndex = when {\n                                        swipeOffset.value <= -threshold && currentIndex < items.lastIndex -> currentIndex + 1\n                                        swipeOffset.value >= threshold && currentIndex > 0 -> currentIndex - 1\n                                        else -> -1\n                                    }\n                                    if (targetIndex >= 0 && viewportWidth > 0) {\n                                        swipeOffset.animateTo(if (targetIndex > currentIndex) -viewportWidth.toFloat() else viewportWidth.toFloat(), tween(180))\n                                        onNavigate(targetIndex)\n                                        swipeOffset.snapTo(0f)\n                                    } else {\n                                        swipeOffset.animateTo(0f, tween(160))\n                                    }\n                                },'''
    gallery = gallery[:start] + new_gesture + gallery[end:]
single_image = '''Image(\n                                it.asImageBitmap(),\n                                "Photo",\n                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {\n                                    scaleX = photoZoom\n                                    scaleY = photoZoom\n                                    translationX = photoPanX\n                                    translationY = photoPanY\n                                },\n                                contentScale = ContentScale.Fit\n                            )'''
if single_image in gallery:
    gallery = gallery.replace(single_image, '''previousBitmap?.let { previous ->\n                                Image(previous.asImageBitmap(), "Previous photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer { translationX = swipeOffset.value - viewportWidth.toFloat() }, contentScale = ContentScale.Fit)\n                            }\n                            Image(\n                                it.asImageBitmap(),\n                                "Photo",\n                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {\n                                    scaleX = photoZoom\n                                    scaleY = photoZoom\n                                    translationX = photoPanX + swipeOffset.value\n                                    translationY = photoPanY\n                                },\n                                contentScale = ContentScale.Fit\n                            )\n                            nextBitmap?.let { next ->\n                                Image(next.asImageBitmap(), "Next photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer { translationX = swipeOffset.value + viewportWidth.toFloat() }, contentScale = ContentScale.Fit)\n                            }''', 1)
checks = {
    "scanner pages": 'PAGES' in scanner and 'onFinish' in scanner,
    "scanner more": 'MORE' in scanner,
    "scanner shutter": 'onCapture' in scanner,
    "scanner top back removed": 'Back to scanner' not in scanner,
    "gallery interactive swipe": 'swipeOffset' in gallery and 'Animatable' in gallery and 'snapTo' in gallery,
    "gallery adjacent photos": 'previousBitmap' in gallery and 'nextBitmap' in gallery,
    "gallery smooth commit": 'tween(180)' in gallery and 'animateTo' in gallery,
    "gallery action row above media": '.zIndex(10f)' in gallery,
    "main forward slide": 'R.anim.slide_in_right' in main and 'R.anim.slide_out_left' in main,
    "qr reverse slide": 'R.anim.slide_in_left' in qr and 'R.anim.slide_out_right' in qr,
    "qr top back removed": 'Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)' not in qr,
}
for name, ok in checks.items(): print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks.items() if not ok]
if failed: raise SystemExit("FINAL UI AUDIT FAILED: " + "; ".join(failed))
GALLERY.write_text(gallery, encoding="utf-8")