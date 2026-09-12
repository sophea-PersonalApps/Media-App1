from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
GALLERY = ROOT / "GalleryActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"


def replace_once(path, text, old, new, label):
    count = text.count(old)
    if count != 1:
        if count == 0 and new in text:
            return text
        raise SystemExit(f"{path.name}: {label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

# Scanner layout is already bottom-anchored by the preceding scanner patch on
# the current test branch. Only add the anchor here when the older form remains.
scanner = SCANNER.read_text(encoding="utf-8")
if ".align(Alignment.BottomCenter)" not in scanner:
    old_layout = '        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {\n            Column(Modifier.fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f)).navigationBarsPadding().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {'
    new_layout = '        Column(Modifier.fillMaxSize().align(Alignment.BottomCenter)) {\n            Column(Modifier.fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f)).navigationBarsPadding().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {'
    if old_layout in scanner:
        scanner = scanner.replace(old_layout, new_layout, 1)

# The scanner needs MORE | shutter | PAGES instead of the normal MORE | shutter |
# FLIP row. Replace the scanner-specific page/capture row if it is still present.
if 'ScannerSectionControls(' not in scanner:
    start = scanner.find('                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {')
    nav = scanner.find('                CameraSectionBottomNavigation(', start)
    if start < 0 or nav < 0:
        raise SystemExit("Scanner capture controls block not found")
    scanner = scanner[:start] + '''                ScannerSectionControls(
                    pages = pages,
                    onCapture = onCapture,
                    onPages = onFinish,
                    onModeSelected = { scannerModeAction(context, it) }
                )
''' + scanner[nav:]

# Scanner/QR capture screens do not need an extra top-left Back button.
scanner = scanner.replace('            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to scanner", tint = ComposeColor.White) }\n', '')

# The custom scanner controls use Modifier.width, so make that import explicit.
if 'import androidx.compose.foundation.layout.width' not in scanner:
    marker = 'import androidx.compose.foundation.layout.weight\n'
    if marker in scanner:
        scanner = scanner.replace(marker, marker + 'import androidx.compose.foundation.layout.width\n', 1)
    else:
        marker = 'import androidx.compose.foundation.layout.height\n'
        scanner = replace_once(SCANNER, scanner, marker, marker + 'import androidx.compose.foundation.layout.width\n', 'scanner width import')

# Use application-owned horizontal slide resources. Android's platform R does
# not provide slide_in_right/slide_out_left on this target.
scanner = scanner.replace('android.R.anim.slide_in_right', 'R.anim.slide_in_right').replace('android.R.anim.slide_out_left', 'R.anim.slide_out_left').replace('android.R.anim.slide_in_left', 'R.anim.slide_in_left').replace('android.R.anim.slide_out_right', 'R.anim.slide_out_right')
SCANNER.write_text(scanner, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
main = main.replace('android.R.anim.slide_in_right', 'R.anim.slide_in_right').replace('android.R.anim.slide_out_left', 'R.anim.slide_out_left').replace('android.R.anim.slide_in_left', 'R.anim.slide_in_left').replace('android.R.anim.slide_out_right', 'R.anim.slide_out_right')
MAIN.write_text(main, encoding="utf-8")

qr = QR.read_text(encoding="utf-8")
qr = qr.replace('                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }\n', '')
qr = qr.replace('android.R.anim.slide_in_right', 'R.anim.slide_in_right').replace('android.R.anim.slide_out_left', 'R.anim.slide_out_left').replace('android.R.anim.slide_in_left', 'R.anim.slide_in_left').replace('android.R.anim.slide_out_right', 'R.anim.slide_out_right')
QR.write_text(qr, encoding="utf-8")

gallery = GALLERY.read_text(encoding="utf-8")
if 'import androidx.compose.ui.zIndex' not in gallery:
    gallery = replace_once(GALLERY, gallery, 'import androidx.compose.ui.viewinterop.AndroidView\n', 'import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.ui.zIndex\n', 'gallery zIndex')
if 'import androidx.compose.animation.core.Animatable' not in gallery:
    gallery = replace_once(GALLERY, gallery, 'import androidx.camera.core.CameraSelector\n', 'import androidx.camera.core.CameraSelector\nimport androidx.compose.animation.core.Animatable\nimport androidx.compose.animation.core.tween\n', 'gallery animation imports')
gallery = replace_once(GALLERY, gallery, '            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {', '            Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {', 'gallery action row')

# Apply the continuous interactive photo swipe if it has not already been generated.
if 'var swipeOffset by rememberSaveable(uri)' not in gallery:
    gallery = replace_once(GALLERY, gallery,
        '                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }',
        '                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var swipeOffset by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        val previousUri = items.getOrNull(currentIndex - 1)?.uri\n                        val nextUri = items.getOrNull(currentIndex + 1)?.uri\n                        var previousBitmap by remember(previousUri) { mutableStateOf<Bitmap?>(null) }\n                        var nextBitmap by remember(nextUri) { mutableStateOf<Bitmap?>(null) }',
        'gallery adjacent state')
    gallery = replace_once(GALLERY, gallery,
        '                        var viewportHeight by remember(uri) { mutableIntStateOf(0) }\n                        Box(\n',
        '                        var viewportHeight by remember(uri) { mutableIntStateStateOf(0) }\n                        LaunchedEffect(previousUri, nextUri) {\n                            val loaded = withContext(Dispatchers.IO) { Pair(previousUri?.let { loadFullImage(context, it) }, nextUri?.let { loadFullImage(context, it) }) }\n                            previousBitmap = loaded.first\n                            nextBitmap = loaded.second\n                        }\n                        Box(\n',
        'gallery adjacent preload')
    gallery = gallery.replace('mutableIntStateStateOf(0)', 'mutableIntStateOf(0)', 1)

    old_gesture = '''                        .pointerInput(uri, currentIndex) {
                                    var dragX = 0f
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)
                                        val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f
                                        val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f
                                        photoZoom = newZoom
                                        if (newZoom <= 1f) {
                                            photoPanX = 0f
                                            photoPanY = 0f
                                            if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                                dragX += pan.x
                                                swipeOffset = (swipeOffset + pan.x).coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())
                                                val commitDistance = viewportWidth.toFloat() * 0.5f
                                                if (viewportWidth > 0 && swipeOffset <= -commitDistance && currentIndex < items.lastIndex) { onNavigate(currentIndex + 1); swipeOffset = 0f; dragX = 0f }
                                                else if (viewportWidth > 0 && swipeOffset >= commitDistance && currentIndex > 0) { onNavigate(currentIndex - 1); swipeOffset = 0f; dragX = 0f }
                                            }
                                        } else {
                                            dragX = 0f
                                            photoPanX = (photoPanX + pan.x).coerceIn(-maxPanX, maxPanX)
                                            photoPanY = (photoPanY + pan.y).coerceIn(-maxPanY, maxPanY)
                                        }
                                    }
                                },'''
    new_gesture = '''                        .pointerInput(uri, currentIndex) {
                            while (true) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)
                                    val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f
                                    val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f
                                    photoZoom = newZoom
                                    if (newZoom <= 1f) {
                                        photoPanX = 0f; photoPanY = 0f
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
                                        settle.animateTo(targetOffset, tween(180)); swipeOffset = settle.value; onNavigate(targetIndex); swipeOffset = 0f
                                    } else {
                                        settle.animateTo(0f, tween(160)); swipeOffset = settle.value; swipeOffset = 0f
                                    }
                                }
                            }
                        },'''
    if old_gesture in gallery:
        gallery = gallery.replace(old_gesture, new_gesture, 1)
    else:
        raise SystemExit('Gallery old swipe gesture not found')

if 'previousBitmap' not in gallery or 'settle.animateTo(targetOffset, tween(180))' not in gallery:
    raise SystemExit('Gallery continuous swipe implementation missing')
GALLERY.write_text(gallery, encoding="utf-8")

checks = {
    'scanner bottom anchored': '.align(Alignment.BottomCenter)' in scanner,
    'scanner custom controls': 'ScannerSectionControls(' in scanner and 'PAGES' in scanner,
    'scanner width import': 'import androidx.compose.foundation.layout.width' in scanner,
    'scanner top-left back removed': 'Back to scanner' not in scanner,
    'gallery continuous swipe': 'settle.animateTo(targetOffset, tween(180))' in gallery,
    'gallery adjacent preload': 'previousBitmap' in gallery and 'nextBitmap' in gallery,
    'gallery action row z-index': '.zIndex(10f)' in gallery,
    'main forward slide': 'R.anim.slide_in_right' in main and 'R.anim.slide_out_left' in main,
    'QR top-left back removed': 'Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)' not in qr,
    'QR reverse slide': 'R.anim.slide_in_left' in qr and 'R.anim.slide_out_right' in qr,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items(): print(('PASS ' if ok else 'FAIL ') + name)
if failed:
    raise SystemExit('FINAL UI AUDIT FAILED: ' + '; '.join(failed))
