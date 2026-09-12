from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
GALLERY = ROOT / "GalleryActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        if count == 0 and new in text:
            return text
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def replace_balanced_block(text, marker, replacement, label):
    start = text.find(marker)
    if start < 0:
        raise SystemExit(f"{label}: marker not found")
    brace = text.find("{", start)
    if brace < 0:
        raise SystemExit(f"{label}: opening brace not found")
    depth = 0
    end = None
    for i in range(brace, len(text)):
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end is None:
        raise SystemExit(f"{label}: unmatched braces")
    # Include the comma following a Modifier lambda when present.
    if end < len(text) and text[end] == ",":
        end += 1
    return text[:start] + replacement + text[end:]

# Scanner: bottom controls are the scanner-specific MORE | shutter | PAGES row.
scanner = SCANNER.read_text(encoding="utf-8")
if ".align(Alignment.BottomCenter)" not in scanner:
    scanner = scanner.replace(
        "        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {",
        "        Column(Modifier.fillMaxSize().align(Alignment.BottomCenter)) {", 1)
if "ScannerSectionControls(" not in scanner:
    start = scanner.find("                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {")
    nav = scanner.find("                CameraSectionBottomNavigation(", start)
    if start < 0 or nav < 0:
        raise SystemExit("Scanner capture controls block not found")
    scanner = scanner[:start] + """                ScannerSectionControls(
                    pages = pages,
                    onCapture = onCapture,
                    onPages = onFinish,
                    onModeSelected = { scannerModeAction(context, it) }
                )
""" + scanner[nav:]
scanner = scanner.replace('            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to scanner", tint = ComposeColor.White) }\n', '')
if "import androidx.compose.foundation.layout.width" not in scanner:
    marker = "import androidx.compose.foundation.layout.weight\n"
    if marker in scanner:
        scanner = scanner.replace(marker, marker + "import androidx.compose.foundation.layout.width\n", 1)
    else:
        marker = "import androidx.compose.foundation.layout.height\n"
        scanner = replace_once(scanner, marker, marker + "import androidx.compose.foundation.layout.width\n", "scanner width import")
for old, new in [
    ("android.R.anim.slide_in_right", "R.anim.slide_in_right"),
    ("android.R.anim.slide_out_left", "R.anim.slide_out_left"),
    ("android.R.anim.slide_in_left", "R.anim.slide_in_left"),
    ("android.R.anim.slide_out_right", "R.anim.slide_out_right"),
]:
    scanner = scanner.replace(old, new)
SCANNER.write_text(scanner, encoding="utf-8")

# Main and QR use the app's four existing slide resources.
main = MAIN.read_text(encoding="utf-8")
for old, new in [
    ("android.R.anim.slide_in_right", "R.anim.slide_in_right"),
    ("android.R.anim.slide_out_left", "R.anim.slide_out_left"),
    ("android.R.anim.slide_in_left", "R.anim.slide_in_left"),
    ("android.R.anim.slide_out_right", "R.anim.slide_out_right"),
]:
    main = main.replace(old, new)
MAIN.write_text(main, encoding="utf-8")

qr = QR.read_text(encoding="utf-8")
qr = qr.replace('                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }\n', '')
for old, new in [
    ("android.R.anim.slide_in_right", "R.anim.slide_in_right"),
    ("android.R.anim.slide_out_left", "R.anim.slide_out_left"),
    ("android.R.anim.slide_in_left", "R.anim.slide_in_left"),
    ("android.R.anim.slide_out_right", "R.anim.slide_out_right"),
]:
    qr = qr.replace(old, new)
QR.write_text(qr, encoding="utf-8")

# Gallery: this step runs after the requested-UI patch, so patch the resulting
# gesture by its actual marker rather than relying on brittle indentation.
gallery = GALLERY.read_text(encoding="utf-8")
if "import androidx.compose.ui.zIndex" not in gallery:
    gallery = replace_once(gallery, "import androidx.compose.ui.viewinterop.AndroidView\n", "import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.ui.zIndex\n", "gallery zIndex import")
if "import androidx.compose.animation.core.Animatable" not in gallery:
    gallery = replace_once(gallery, "import androidx.camera.core.CameraSelector\n", "import androidx.camera.core.CameraSelector\nimport androidx.compose.animation.core.Animatable\nimport androidx.compose.animation.core.tween\n", "gallery animation imports")
if ".zIndex(10f)" not in gallery:
    gallery = replace_once(gallery, "            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {", "            Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {", "gallery action row")

# Add adjacent-media state once.
if "var swipeOffset by rememberSaveable(uri)" not in gallery:
    gallery = replace_once(
        gallery,
        "                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }",
        """                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }
                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }
                        var swipeOffset by rememberSaveable(uri) { mutableFloatStateOf(0f) }
                        val previousUri = items.getOrNull(currentIndex - 1)?.uri
                        val nextUri = items.getOrNull(currentIndex + 1)?.uri
                        var previousBitmap by remember(previousUri) { mutableStateOf<Bitmap?>(null) }
                        var nextBitmap by remember(nextUri) { mutableStateOf<Bitmap?>(null) }""",
        "gallery adjacent state")
    gallery = replace_once(
        gallery,
        "                        var viewportHeight by remember(uri) { mutableIntStateOf(0) }\n                        Box(\n",
        """                        var viewportHeight by remember(uri) { mutableIntStateOf(0) }
                        LaunchedEffect(previousUri, nextUri) {
                            val loaded = withContext(Dispatchers.IO) {
                                Pair(
                                    previousUri?.let { loadFullImage(context, it) },
                                    nextUri?.let { loadFullImage(context, it) }
                                )
                            }
                            previousBitmap = loaded.first
                            nextBitmap = loaded.second
                        }
                        Box(
""",
        "gallery adjacent preload")

# Replace whatever swipe gesture the preceding patch generated.
gallery = replace_balanced_block(
    gallery,
    ".pointerInput(uri, currentIndex) {",
    """.pointerInput(uri, currentIndex) {
                            while (true) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)
                                    val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f
                                    val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f
                                    photoZoom = newZoom
                                    if (newZoom <= 1f) {
                                        photoPanX = 0f
                                        photoPanY = 0f
                                        if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                            swipeOffset = (swipeOffset + pan.x).coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())
                                        }
                                    } else {
                                        swipeOffset = 0f
                                        photoPanX = (photoPanX + pan.x).coerceIn(-maxPanX, maxPanX)
                                        photoPanY = (photoPanY + pan.y).coerceIn(-maxPanY, maxPanY)
                                    }
                                }
                                if (photoZoom <= 1f && viewportWidth > 0 && swipeOffset != 0f) {
                                    val goingNext = swipeOffset < 0f
                                    val canNavigate = if (goingNext) currentIndex < items.lastIndex else currentIndex > 0
                                    val commitDistance = viewportWidth.toFloat() * 0.5f
                                    val targetIndex = if (goingNext) currentIndex + 1 else currentIndex - 1
                                    val targetOffset = if (goingNext) -viewportWidth.toFloat() else viewportWidth.toFloat()
                                    val settle = Animatable(swipeOffset)
                                    if (canNavigate && kotlin.math.abs(swipeOffset) >= commitDistance) {
                                        settle.animateTo(targetOffset, tween(180))
                                        swipeOffset = settle.value
                                        onNavigate(targetIndex)
                                        swipeOffset = 0f
                                    } else {
                                        settle.animateTo(0f, tween(160))
                                        swipeOffset = settle.value
                                        swipeOffset = 0f
                                    }
                                }
                            }
                        }""",
    "gallery swipe gesture")

# Make the current and adjacent photos move together during the drag.
old_image = """                            Image(
                                it.asImageBitmap(),
                                "Photo",
                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                                    scaleX = photoZoom
                                    scaleY = photoZoom
                                    translationX = photoPanX
                                    translationY = photoPanY
                                },
                                contentScale = ContentScale.Fit
                            )"""
new_image = """                            previousBitmap?.let { previous ->
                                Image(
                                    previous.asImageBitmap(), "Previous photo",
                                    Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                                        translationX = swipeOffset - viewportWidth.toFloat()
                                    },
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Image(
                                it.asImageBitmap(), "Photo",
                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                                    scaleX = photoZoom
                                    scaleY = photoZoom
                                    translationX = photoPanX + swipeOffset
                                    translationY = photoPanY
                                },
                                contentScale = ContentScale.Fit
                            )
                            nextBitmap?.let { next ->
                                Image(
                                    next.asImageBitmap(), "Next photo",
                                    Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                                        translationX = swipeOffset + viewportWidth.toFloat()
                                    },
                                    contentScale = ContentScale.Fit
                                )
                            }"""
if old_image in gallery:
    gallery = gallery.replace(old_image, new_image, 1)

if "previousBitmap" not in gallery or "nextBitmap" not in gallery or "settle.animateTo(targetOffset, tween(180))" not in gallery:
    raise SystemExit("Gallery continuous interactive swipe implementation missing")
GALLERY.write_text(gallery, encoding="utf-8")

checks = {
    "scanner bottom controls": "ScannerSectionControls(" in scanner,
    "scanner pages": "PAGES" in scanner,
    "scanner width import": "import androidx.compose.foundation.layout.width" in scanner,
    "scanner top back removed": "Back to scanner" not in scanner,
    "gallery interactive swipe": "swipeOffset" in gallery and "Animatable" in gallery,
    "gallery adjacent photos": "previousBitmap" in gallery and "nextBitmap" in gallery,
    "gallery smooth commit": "settle.animateTo(targetOffset, tween(180))" in gallery,
    "gallery action row above media": ".zIndex(10f)" in gallery,
    "main slide": "R.anim.slide_in_right" in main and "R.anim.slide_out_left" in main,
    "qr top back removed": "Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)" not in qr,
    "qr reverse slide": "R.anim.slide_in_left" in qr and "R.anim.slide_out_right" in qr,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("FINAL UI AUDIT FAILED: " + "; ".join(failed))
