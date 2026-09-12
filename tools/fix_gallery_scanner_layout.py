from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
GALLERY = ROOT / "GalleryActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
MAIN = ROOT / "MainActivity.kt"
QR = ROOT / "QrScannerActivity.kt"

def replace_once(text, old, new, label):
    n = text.count(old)
    if n == 1: return text.replace(old, new, 1)
    if n == 0 and new in text: return text
    raise SystemExit(f"{label}: expected 1 match, found {n}")

scanner = SCANNER.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")
qr = QR.read_text(encoding="utf-8")

old_duplicate = '''            Column(Modifier.fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f)).navigationBarsPadding().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pages.isNotEmpty()) {
                    LazyRow(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(pages) { index, path ->
                            Box(Modifier.size(68.dp)) { LocalImage(path, Modifier.fillMaxSize()); IconButton(onClick = { onDelete(index) }, modifier = Modifier.align(Alignment.TopEnd).size(25.dp)) { Icon(Icons.Default.Delete, "Remove page", tint = ComposeColor.White) } }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("${pages.size} page${if (pages.size == 1) "" else "s"}", color = ComposeColor.White, modifier = Modifier.padding(end = 18.dp))
                    Box(Modifier.size(72.dp).background(ComposeColor.White, CircleShape).padding(5.dp).clickable(onClick = onCapture), contentAlignment = Alignment.Center) { Box(Modifier.size(58.dp).background(ComposeColor.Black, CircleShape)) }
                    Spacer(Modifier.size(18.dp)); Button(onClick = onFinish, enabled = pages.isNotEmpty()) { Text("Finish") }
                }
                Spacer(Modifier.height(8.dp))
                CameraSectionControls(
                    mode = CameraSectionMode.SCAN,
                    onModeSelected = { scannerModeAction(context, it) },
                    onPrimaryAction = onCapture,
                    onFlip = onFlip,
                    onMore = { },
                    primaryEnabled = true
                )
                CameraSectionBottomNavigation(cameraSelected = true, onCamera = onOpenCamera, onGallery = onOpenGallery)
            }
'''
new_shared = '''            Column(Modifier.fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f)).navigationBarsPadding()) {
                CameraSectionControls(
                    mode = CameraSectionMode.SCAN,
                    onModeSelected = { scannerModeAction(context, it) },
                    onPrimaryAction = onCapture,
                    onFlip = onFinish,
                    onMore = { },
                    primaryEnabled = true
                )
                CameraSectionBottomNavigation(cameraSelected = true, onCamera = onOpenCamera, onGallery = onOpenGallery)
            }
'''
scanner = replace_once(scanner, old_duplicate, new_shared, "scanner duplicate capture controls")
scanner = scanner.replace('''            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ComposeColor.White) }
                Text("Scanner", color = ComposeColor.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onFlip) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = ComposeColor.White) }
            }
''', '')
SCANNER.write_text(scanner, encoding="utf-8")

gallery = GALLERY.read_text(encoding="utf-8")
if "import androidx.compose.ui.zIndex" not in gallery:
    gallery = replace_once(gallery, "import androidx.compose.ui.viewinterop.AndroidView\n", "import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.ui.zIndex\n", "gallery zIndex import")
if "import androidx.compose.animation.core.Animatable" not in gallery:
    anchor = "import androidx.compose.foundation.gestures.detectTransformGestures\n"
    if anchor in gallery:
        gallery = gallery.replace(anchor, anchor + "import androidx.compose.animation.core.Animatable\nimport androidx.compose.animation.core.tween\n", 1)
if "import androidx.compose.runtime.rememberCoroutineScope" not in gallery:
    anchor = "import androidx.compose.runtime.rememberSaveable\n"
    if anchor in gallery: gallery = gallery.replace(anchor, anchor + "import androidx.compose.runtime.rememberCoroutineScope\n", 1)
if "import kotlinx.coroutines.launch" not in gallery:
    anchor = "import kotlinx.coroutines.withContext\n"
    if anchor in gallery: gallery = gallery.replace(anchor, anchor + "import kotlinx.coroutines.launch\n", 1)
if ".zIndex(10f)" not in gallery:
    gallery = replace_once(gallery, "Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {", "Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {", "gallery action row")
state_marker = "var viewportHeight by remember(uri) { mutableIntStateOf(0) }"
if "val swipeOffset = remember" not in gallery:
    if state_marker not in gallery: raise SystemExit("gallery viewport state not found")
    gallery = gallery.replace(state_marker, state_marker + '''
                        val swipeOffset = remember { Animatable(0f) }
                        val swipeScope = rememberCoroutineScope()
                        val previousUri = if (currentIndex > 0) items[currentIndex - 1].uri else null
                        val nextUri = if (currentIndex >= 0 && currentIndex < items.lastIndex) items[currentIndex + 1].uri else null
                        var previousBitmap by remember(currentIndex) { mutableStateOf<Bitmap?>(null) }
                        var nextBitmap by remember(currentIndex) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(currentIndex, previousUri, nextUri) {
                            previousBitmap = previousUri?.let { u -> withContext(Dispatchers.IO) { context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) } } }
                            nextBitmap = nextUri?.let { u -> withContext(Dispatchers.IO) { context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) } } }
                        }
''', 1)
old_gesture_start = '''.pointerInput(uri, currentIndex) {
                                    var dragX = 0f
                                    detectTransformGestures { _, pan, zoom, _ ->'''
if old_gesture_start in gallery:
    old_gesture_end = '''                                    }
                                },'''
    start = gallery.find(old_gesture_start); end = gallery.find(old_gesture_end, start)
    if end < 0: raise SystemExit("gallery gesture end not found")
    end += len(old_gesture_end)
    new_gesture = '''.pointerInput(uri, currentIndex) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)
                                        val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f
                                        val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f
                                        photoZoom = newZoom
                                        if (newZoom <= 1f) {
                                            photoPanX = 0f
                                            photoPanY = 0f
                                            if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                                val maxOffset = viewportWidth.toFloat()
                                                swipeScope.launch { swipeOffset.snapTo((swipeOffset.value + pan.x).coerceIn(-maxOffset, maxOffset)) }
                                            }
                                        } else {
                                            photoPanX = (photoPanX + pan.x).coerceIn(-maxPanX, maxPanX)
                                            photoPanY = (photoPanY + pan.y).coerceIn(-maxPanY, maxPanY)
                                            swipeScope.launch { swipeOffset.snapTo(0f) }
                                        }
                                    }
                                    val threshold = viewportWidth.toFloat() * 0.5f
                                    val targetIndex = when {
                                        swipeOffset.value <= -threshold && currentIndex < items.lastIndex -> currentIndex + 1
                                        swipeOffset.value >= threshold && currentIndex > 0 -> currentIndex - 1
                                        else -> -1
                                    }
                                    if (targetIndex >= 0 && viewportWidth > 0) {
                                        swipeOffset.animateTo(if (targetIndex > currentIndex) -viewportWidth.toFloat() else viewportWidth.toFloat(), tween(180))
                                        onNavigate(targetIndex)
                                        swipeOffset.snapTo(0f)
                                    } else {
                                        swipeOffset.animateTo(0f, tween(160))
                                    }
                                },'''
    gallery = gallery[:start] + new_gesture + gallery[end:]
single_image = '''Image(
                                it.asImageBitmap(),
                                "Photo",
                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                                    scaleX = photoZoom
                                    scaleY = photoZoom
                                    translationX = photoPanX
                                    translationY = photoPanY
                                },
                                contentScale = ContentScale.Fit
                            )'''
if single_image in gallery:
    gallery = gallery.replace(single_image, '''previousBitmap?.let { previous ->
                                Image(previous.asImageBitmap(), "Previous photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer { translationX = swipeOffset.value - viewportWidth.toFloat() }, contentScale = ContentScale.Fit)
                            }
                            Image(
                                it.asImageBitmap(),
                                "Photo",
                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                                    scaleX = photoZoom
                                    scaleY = photoZoom
                                    translationX = photoPanX + swipeOffset.value
                                    translationY = photoPanY
                                },
                                contentScale = ContentScale.Fit
                            )
                            nextBitmap?.let { next ->
                                Image(next.asImageBitmap(), "Next photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer { translationX = swipeOffset.value + viewportWidth.toFloat() }, contentScale = ContentScale.Fit)
                            }''', 1)

checks = {
    "scanner shared controls": "CameraSectionControls(" in scanner and "CameraSectionBottomNavigation(" in scanner and 'mode = CameraSectionMode.SCAN' in scanner and 'onFlip = onFinish' in scanner,
    "scanner duplicate row removed": 'Text("Finish")' not in scanner and 'Text("${pages.size} page' not in scanner,
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
