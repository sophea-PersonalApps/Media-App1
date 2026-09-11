from pathlib import Path

MAIN = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt")
GALLERY = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        if count == 0 and new in text:
            return text
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

# CAMERA: one gesture detector owns the preview. Pinch zoom accumulates locally so
# changing cameraZoom does not restart pointerInput in the middle of a pinch.
text = MAIN.read_text(encoding="utf-8")
text = text.replace("import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n", "")
old_camera = '''        Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {
            var drag = 0f
            detectHorizontalDragGestures(onHorizontalDrag = { _, amount -> drag += amount }, onDragEnd = {
                when { drag < -80f && selectedIndex < modes.lastIndex -> onModeChanged(modes[selectedIndex + 1]); drag > 80f && selectedIndex > 0 -> onModeChanged(modes[selectedIndex - 1]) }
                drag = 0f
            })
        })
        if (mode == CameraSectionMode.PHOTO || mode == CameraSectionMode.VIDEO) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode, currentLens) {
                detectTransformGestures { _, _, zoom, _ -> onZoom(cameraZoom * zoom) }
            })
        }
'''
new_camera = '''        Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode, currentLens) {
            var horizontalDrag = 0f
            var gestureZoom = cameraZoom
            detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                val cameraMode = mode == CameraSectionMode.PHOTO || mode == CameraSectionMode.VIDEO
                if (cameraMode && kotlin.math.abs(zoom - 1f) > 0.001f) {
                    gestureZoom = (gestureZoom * zoom).coerceIn(1f, 10f)
                    onZoom(gestureZoom)
                    horizontalDrag = 0f
                } else if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                    horizontalDrag += pan.x
                    when {
                        horizontalDrag <= -80f && selectedIndex < modes.lastIndex -> {
                            onModeChanged(modes[selectedIndex + 1])
                            horizontalDrag = 0f
                        }
                        horizontalDrag >= 80f && selectedIndex > 0 -> {
                            onModeChanged(modes[selectedIndex - 1])
                            horizontalDrag = 0f
                        }
                    }
                }
            }
        })
'''
text = replace_once(text, old_camera, new_camera, "camera gesture block")
MAIN.write_text(text, encoding="utf-8")

# GALLERY: pinch zoom plus one-finger pan after zooming. Positive pan.y moves the
# enlarged image down, exposing the upper part of the photo. Use onSizeChanged
# because viewport dimensions are needed inside the pointerInput coroutine.
text = GALLERY.read_text(encoding="utf-8")
if "import androidx.compose.ui.layout.onSizeChanged" not in text:
    anchor = "import androidx.compose.ui.graphics.graphicsLayer\n"
    if anchor in text:
        text = text.replace(anchor, anchor + "import androidx.compose.ui.layout.onSizeChanged\n", 1)
    else:
        text = text.replace("import androidx.compose.ui", "import androidx.compose.ui.layout.onSizeChanged\nimport androidx.compose.ui", 1)
old_gallery = '''                    bitmap?.let {
                        Box(Modifier.fillMaxSize().pointerInput(uri) {
                            detectTransformGestures { _, _, zoom, _ -> photoZoom = (photoZoom * zoom).coerceIn(1f, 8f) }
                        }, contentAlignment = Alignment.Center) {
                            Image(it.asImageBitmap(), "Photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer(scaleX = photoZoom, scaleY = photoZoom), contentScale = ContentScale.Fit)
                        }
                    } ?: CircularProgressIndicator(color = Color.White)
'''
new_gallery = '''                    bitmap?.let {
                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }
                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }
                        var viewportWidth by remember(uri) { mutableIntStateOf(0) }
                        var viewportHeight by remember(uri) { mutableIntStateOf(0) }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .onSizeChanged { size ->
                                    viewportWidth = size.width
                                    viewportHeight = size.height
                                }
                                .pointerInput(uri) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)
                                        val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f
                                        val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f
                                        photoZoom = newZoom
                                        if (newZoom <= 1f) {
                                            photoPanX = 0f
                                            photoPanY = 0f
                                        } else {
                                            photoPanX = (photoPanX + pan.x).coerceIn(-maxPanX, maxPanX)
                                            photoPanY = (photoPanY + pan.y).coerceIn(-maxPanY, maxPanY)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                it.asImageBitmap(),
                                "Photo",
                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                                    scaleX = photoZoom
                                    scaleY = photoZoom
                                    translationX = photoPanX
                                    translationY = photoPanY
                                },
                                contentScale = ContentScale.Fit
                            )
                        }
                    } ?: CircularProgressIndicator(color = Color.White)
'''
text = replace_once(text, old_gallery, new_gallery, "gallery viewer gesture block")
GALLERY.write_text(text, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
gallery = GALLERY.read_text(encoding="utf-8")
required = [
    "detectTransformGestures(panZoomLock = true)",
    "var gestureZoom = cameraZoom",
    "gestureZoom = (gestureZoom * zoom)",
    "var photoPanX by rememberSaveable(uri)",
    "var photoPanY by rememberSaveable(uri)",
    "translationX = photoPanX",
    "translationY = photoPanY",
    "onSizeChanged { size ->",
]
for item in required:
    if item not in main + gallery:
        raise SystemExit(f"Required pinch/pan implementation missing: {item}")
if "detectHorizontalDragGestures" in main:
    raise SystemExit("Old competing camera horizontal gesture handler remains")
if "onZoom(cameraZoom * zoom)" in main:
    raise SystemExit("Old non-accumulating camera zoom implementation remains")
print("Pinch zoom + gallery pan implementation applied and audited.")
