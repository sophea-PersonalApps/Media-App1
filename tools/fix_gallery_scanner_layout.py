from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
GALLERY = ROOT / "GalleryActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"


def replace_once(path: Path, text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        if count == 0 and new in text:
            return text
        raise SystemExit(f"{path.name}: {label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

# Scanner: the persisted source is already bottom-anchored. Keep it that way;
# this guard also supports the older full-height form if it ever reappears.
scanner = SCANNER.read_text(encoding="utf-8")
if ".align(Alignment.BottomCenter)" not in scanner:
    old = '        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {\n            Column(Modifier.fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f)).navigationBarsPadding().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {'
    new = '        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f)).navigationBarsPadding().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {'
    scanner = replace_once(SCANNER, scanner, old, new, "scanner controls anchored to bottom")

# Replace the scanner-only extra capture row plus the generic shared controls.
# The desired scanner row is MORE | one shutter | PAGES; PAGES opens the existing
# page preview, so there is no second capture button and no flip button.
start = scanner.find('                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {')
end_marker = '                CameraSectionControls(\n                    mode = CameraSectionMode.SCAN,'
end = scanner.find(end_marker, start)
if start >= 0 and end >= 0:
    close = scanner.find('                }', end)
    if close < 0:
        raise SystemExit("Scanner shared control block closing brace not found")
    close += len('                }')
    scanner = scanner[:start] + '''                ScannerSectionControls(
                    pages = pages,
                    onCapture = onCapture,
                    onPages = onFinish,
                    onModeSelected = { scannerModeAction(context, it) }
                )''' + scanner[close:]
elif 'ScannerSectionControls(' not in scanner:
    raise SystemExit("Scanner capture controls block not found")

# No Back button at the top of the scanner preview page either.
scanner = scanner.replace('            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to scanner", tint = ComposeColor.White) }\n', '')
# Forward navigation uses the same horizontal slide direction as Camera -> Video.
scanner = scanner.replace('onOpenQr = { startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(0, 0) }',
    'onOpenQr = { startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(android.R.anim.slide_in_right, android.R.anim.slide_out_left) }')
scanner = scanner.replace('        CameraSectionMode.PHOTO, CameraSectionMode.VIDEO -> context.startActivity(Intent(context, MainActivity::class.java).putExtras(cameraModeIntent(mode)))\n        CameraSectionMode.SCAN -> Unit\n        CameraSectionMode.QR -> context.startActivity(Intent(context, QrScannerActivity::class.java))',
    '        CameraSectionMode.PHOTO, CameraSectionMode.VIDEO -> { context.startActivity(Intent(context, MainActivity::class.java).putExtras(cameraModeIntent(mode))); (context as? android.app.Activity)?.overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right) }\n        CameraSectionMode.SCAN -> Unit\n        CameraSectionMode.QR -> { context.startActivity(Intent(context, QrScannerActivity::class.java)); (context as? android.app.Activity)?.overridePendingTransition(android.R.anim.slide_in_right, android.R.anim.slide_out_left) }')

marker = '@Composable\nprivate fun ScannerPreview('
if 'private fun ScannerSectionControls(' not in scanner:
    controls = '''@Composable
private fun ScannerSectionControls(
    pages: List<String>,
    onCapture: () -> Unit,
    onPages: () -> Unit,
    onModeSelected: (CameraSectionMode) -> Unit
) {
    val selectedIndex = CameraSectionMode.entries.indexOf(CameraSectionMode.SCAN)
    Box(Modifier.fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Text("⋮", color = ComposeColor.White, fontSize = 27.sp) }
                    Text("MORE", color = ComposeColor.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.size(72.dp).background(ComposeColor.White, CircleShape).clickable(onClick = onCapture).padding(5.dp).background(ComposeColor.Black, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(58.dp).background(ComposeColor.White, CircleShape))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = pages.isNotEmpty(), onClick = onPages)) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Text("P", color = ComposeColor.White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                    Text("PAGES ${pages.size}", color = ComposeColor.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CameraSectionMode.entries.getOrNull(selectedIndex - 1)?.let { previous ->
                    Text(previous.label, color = ComposeColor.White.copy(alpha = 0.48f), fontSize = 14.sp, modifier = Modifier.clickable { onModeSelected(previous) }.padding(5.dp))
                    Spacer(Modifier.width(18.dp))
                }
                Text("SCAN", color = ComposeColor.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(5.dp))
                CameraSectionMode.entries.getOrNull(selectedIndex + 1)?.let { next ->
                    Spacer(Modifier.width(18.dp))
                    Text(next.label, color = ComposeColor.White.copy(alpha = 0.48f), fontSize = 14.sp, modifier = Modifier.clickable { onModeSelected(next) }.padding(5.dp))
                }
            }
        }
    }
}

'''
    scanner = replace_once(SCANNER, scanner, marker, controls + marker, "scanner custom controls")
SCANNER.write_text(scanner, encoding="utf-8")

# Gallery: make the swipe truly continuous. The adjacent photos are loaded in
# both directions before/while the viewer is open. The finger controls the exact
# translation; release animates to a full-screen target or back to zero.
gallery = GALLERY.read_text(encoding="utf-8")
if 'import androidx.compose.ui.zIndex' not in gallery:
    gallery = replace_once(GALLERY, gallery, 'import androidx.compose.ui.viewinterop.AndroidView\n', 'import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.ui.zIndex\n', "gallery zIndex import")
if 'import androidx.compose.animation.core.Animatable' not in gallery:
    gallery = replace_once(GALLERY, gallery, 'import androidx.camera.core.CameraSelector\n', 'import androidx.camera.core.CameraSelector\nimport androidx.compose.animation.core.Animatable\nimport androidx.compose.animation.core.tween\n', "gallery animation imports")
gallery = replace_once(GALLERY, gallery, '            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {', '            Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {', "gallery viewer action row")

# Convert the one-direction adjacent state from the previous implementation into
# bidirectional preloaded state.
if 'val previousUri = items.getOrNull(currentIndex - 1)?.uri' not in gallery:
    gallery = replace_once(GALLERY, gallery,
        '                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var swipeOffset by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        val adjacentIndex = when { swipeOffset < 0f -> currentIndex + 1; swipeOffset > 0f -> currentIndex - 1; else -> -1 }\n                        val adjacentUri = items.getOrNull(adjacentIndex)?.uri\n                        var adjacentBitmap by remember(adjacentUri) { mutableStateOf<Bitmap?>(null) }',
        '                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var swipeOffset by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        val previousUri = items.getOrNull(currentIndex - 1)?.uri\n                        val nextUri = items.getOrNull(currentIndex + 1)?.uri\n                        var previousBitmap by remember(previousUri) { mutableStateOf<Bitmap?>(null) }\n                        var nextBitmap by remember(nextUri) { mutableStateOf<Bitmap?>(null) }',
        "gallery adjacent state")
    gallery = replace_once(GALLERY, gallery,
        '                        var viewportHeight by remember(uri) { mutableIntStateOf(0) }\n                        LaunchedEffect(adjacentUri) {\n                            adjacentBitmap = if (adjacentUri == null) null else withContext(Dispatchers.IO) { loadFullImage(context, adjacentUri) }\n                        }',
        '                        var viewportHeight by remember(uri) { mutableIntStateOf(0) }\n                        LaunchedEffect(previousUri, nextUri) {\n                            val loaded = withContext(Dispatchers.IO) { Pair(previousUri?.let { loadFullImage(context, it) }, nextUri?.let { loadFullImage(context, it) }) }\n                            previousBitmap = loaded.first\n                            nextBitmap = loaded.second\n                        }',
        "gallery adjacent preload")

# Replace the old threshold-based gesture with a release-based continuous gesture.
old_gesture_start = '                        .pointerInput(uri, currentIndex) {\n                                    var dragX = 0f\n                                    detectTransformGestures'
start = gallery.find(old_gesture_start)
if start >= 0:
    end = gallery.find('                                },', start)
    # Find the end by the known following modifier line.
    following = gallery.find('                        .onSizeChanged', start)
    if following < 0:
        raise SystemExit("Gallery gesture end not found")
    old_block = gallery[start:following]
    new_block = '''                        .pointerInput(uri, currentIndex) {
                            while (true) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)
                                    val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f
                                    val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f
                                    photoZoom = newZoom
                                    if (newZoom <= 1f) {
                                        photoPanX = 0f
                                        photoPanY = 0f
                                        if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) swipeOffset = (swipeOffset + pan.x).coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())
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
                                    val targetOffset = if (goingNext) viewportWidth.toFloat() else -viewportWidth.toFloat()
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
                        },
'''
    gallery = gallery[:start] + new_block + gallery[following:]

# Replace adjacent rendering if the old one is still present.
if 'adjacentBitmap!!.asImageBitmap()' in gallery:
    old_render = '''                            if (photoZoom <= 1f && swipeOffset != 0f && adjacentBitmap != null) {
                                Image(
                                    adjacentBitmap!!.asImageBitmap(),
                                    "Next photo",
                                    Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                                        translationX = swipeOffset + if (swipeOffset < 0f) viewportWidth.toFloat() else -viewportWidth.toFloat()
                                    },
                                    contentScale = ContentScale.Fit
                                )
                            }'''
    new_render = '''                            if (photoZoom <= 1f && swipeOffset != 0f) {
                                val adjacentBitmap = if (swipeOffset < 0f) nextBitmap else previousBitmap
                                adjacentBitmap?.let { adjacent ->
                                    Image(adjacent.asImageBitmap(), "Adjacent photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer { translationX = swipeOffset + if (swipeOffset < 0f) viewportWidth.toFloat() else -viewportWidth.toFloat() }, contentScale = ContentScale.Fit)
                                }
                            }'''
    gallery = replace_once(GALLERY, gallery, old_render, new_render, "gallery continuous adjacent render")
GALLERY.write_text(gallery, encoding="utf-8")

# Activity transitions and QR Back removal.
main = MAIN.read_text(encoding="utf-8")
main = main.replace('CameraSectionMode.SCAN -> { startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(0, 0) }', 'CameraSectionMode.SCAN -> { startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(android.R.anim.slide_in_right, android.R.anim.slide_out_left) }')
MAIN.write_text(main, encoding="utf-8")
qr = QR.read_text(encoding="utf-8")
qr = qr.replace('                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }\n', '')
qr = qr.replace('onOpenScanner = { startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(0, 0) }', 'onOpenScanner = { startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right) }')
QR.write_text(qr, encoding="utf-8")

checks = {
    "scanner bottom anchored": '.align(Alignment.BottomCenter)' in scanner,
    "scanner custom controls": 'ScannerSectionControls(' in scanner and 'PAGES ${pages.size}' in scanner,
    "scanner extra capture removed": 'Box(Modifier.size(72.dp).background(ComposeColor.White, CircleShape).padding(5.dp).clickable(onClick = onCapture)' not in scanner,
    "scanner preview back removed": 'Back to scanner' not in scanner,
    "gallery continuous swipe": 'settle.animateTo(targetOffset, tween(180))' in gallery,
    "gallery adjacent preload": 'previousBitmap' in gallery and 'nextBitmap' in gallery,
    "gallery action row z-index": '.zIndex(10f)' in gallery,
    "main scanner animation": 'CameraSectionMode.SCAN -> { startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(android.R.anim.slide_in_right, android.R.anim.slide_out_left) }' in main,
    "QR top-left back removed": 'Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)' not in qr,
    "QR scanner reverse animation": 'overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)' in qr,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items(): print(("PASS " if ok else "FAIL ") + name)
if failed: raise SystemExit("FINAL UI AUDIT FAILED: " + "; ".join(failed))
