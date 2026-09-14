from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")

# The previous pointerInput key included mediaZoom. Every zoom update therefore
# recreated the gesture detector and cancelled the pinch, forcing many tiny pinches.
text = text.replace(
    ".pointerInput(uri, currentIndex, isVideo, mediaZoom) {",
    ".pointerInput(uri, currentIndex, isVideo) {",
    1,
)

old_gesture = '''                        var horizontalDrag = 0f
                        var navigationStarted = false
                        detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                            val wasZoomed = mediaZoom > 1f
                            val newZoom = (mediaZoom * zoom).coerceIn(1f, 8f)
                            mediaZoom = newZoom

                            if (newZoom > 1f) {
                                horizontalDrag = 0f
                                mediaPanX = (mediaPanX + pan.x).coerceIn(-viewportWidth.toFloat() * (newZoom - 1f) / 2f, viewportWidth.toFloat() * (newZoom - 1f) / 2f)
                                mediaPanY = (mediaPanY + pan.y).coerceIn(-viewportHeight.toFloat() * (newZoom - 1f) / 2f, viewportHeight.toFloat() * (newZoom - 1f) / 2f)
                            } else {
                                mediaPanX = 0f
                                mediaPanY = 0f
                                if (zoom < 1f || wasZoomed) horizontalDrag = 0f
                                if (!navigationStarted && kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                    horizontalDrag += pan.x
                                    swipeScope.launch { swipeOffset.snapTo(horizontalDrag.coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())) }
                                    val threshold = minOf(140f, viewportWidth * 0.22f)
                                    val target = when {
                                        horizontalDrag <= -threshold && currentIndex < items.lastIndex -> currentIndex + 1
                                        horizontalDrag >= threshold && currentIndex > 0 -> currentIndex - 1
                                        else -> -1
                                    }
                                    if (target >= 0) {
                                        navigationStarted = true
                                        swipeScope.launch {
                                            swipeOffset.animateTo(if (target > currentIndex) -viewportWidth.toFloat() else viewportWidth.toFloat(), tween(180))
                                            onNavigate(target)
                                            swipeOffset.snapTo(0f)
                                        }
                                    }
                                }
                            }
                        }
                        horizontalDrag = 0f
                        navigationStarted = false
'''
new_gesture = '''                        var horizontalDrag = 0f
                        var navigationStarted = false
                        detectGalleryTransformGestures { _, pan, zoom, pointerCount ->
                            val wasZoomed = mediaZoom > 1f
                            val newZoom = (mediaZoom * zoom).coerceIn(1f, 8f)
                            mediaZoom = newZoom

                            if (newZoom > 1f) {
                                horizontalDrag = 0f
                                mediaPanX = (mediaPanX + pan.x).coerceIn(-viewportWidth.toFloat() * (newZoom - 1f) / 2f, viewportWidth.toFloat() * (newZoom - 1f) / 2f)
                                mediaPanY = (mediaPanY + pan.y).coerceIn(-viewportHeight.toFloat() * (newZoom - 1f) / 2f, viewportHeight.toFloat() * (newZoom - 1f) / 2f)
                            } else {
                                mediaPanX = 0f
                                mediaPanY = 0f
                                if (zoom < 1f || wasZoomed || pointerCount > 1) horizontalDrag = 0f
                                if (!navigationStarted && pointerCount == 1 && kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                    horizontalDrag += pan.x
                                    swipeOffset.snapTo(horizontalDrag.coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat()))
                                    val threshold = minOf(140f, viewportWidth * 0.22f)
                                    val target = when {
                                        horizontalDrag <= -threshold && currentIndex < items.lastIndex -> currentIndex + 1
                                        horizontalDrag >= threshold && currentIndex > 0 -> currentIndex - 1
                                        else -> -1
                                    }
                                    if (target >= 0) {
                                        navigationStarted = true
                                        swipeOffset.animateTo(if (target > currentIndex) -viewportWidth.toFloat() else viewportWidth.toFloat(), tween(180))
                                        onNavigate(target)
                                        swipeOffset.snapTo(0f)
                                    }
                                }
                            }
                        }
'''
if old_gesture not in text:
    raise SystemExit("Gallery gesture block not found")
text = text.replace(old_gesture, new_gesture, 1)

helper_marker = "@Composable private fun CachedFullImage"
helper = '''private suspend fun PointerInputScope.detectGalleryTransformGestures(onGesture: (centroid: Offset, pan: Offset, zoom: Float, pointerCount: Int) -> Unit) {
    awaitPointerEventScope {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var previousCentroid = Offset.Zero
            var previousSpan = 0f
            var haveCentroid = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                val centroid = pressed.map { it.position }.reduce { a, b -> a + b } / pressed.size.toFloat()
                val pan = if (haveCentroid) centroid - previousCentroid else Offset.Zero
                val span = if (pressed.size > 1) {
                    pressed.map { (it.position - centroid).getDistance() }.average().toFloat()
                } else 0f
                val zoom = if (pressed.size > 1 && previousSpan > 0f) (span / previousSpan).coerceIn(0.85f, 1.15f) else 1f

                onGesture(centroid, pan, zoom, pressed.size)
                previousCentroid = centroid
                previousSpan = span
                haveCentroid = true

                event.changes.forEach { change ->
                    if (change.positionChanged()) change.consume()
                }
            }
        }
    }
}

'''
if helper_marker not in text:
    raise SystemExit("CachedFullImage marker not found")
if "private suspend fun PointerInputScope.detectGalleryTransformGestures" not in text:
    text = text.replace(helper_marker, helper + helper_marker, 1)

imports = [
    "import androidx.compose.ui.geometry.Offset",
    "import androidx.compose.ui.input.pointer.PointerEventPass",
    "import androidx.compose.ui.input.pointer.PointerInputScope",
    "import androidx.compose.ui.input.pointer.awaitEachGesture",
    "import androidx.compose.ui.input.pointer.awaitFirstDown",
    "import androidx.compose.ui.input.pointer.positionChanged",
]
for line in imports:
    if line not in text:
        text = text.replace("import androidx.compose.ui.zIndex", line + "\nimport androidx.compose.ui.zIndex", 1)

path.write_text(text, encoding="utf-8")
print("Gallery gestures fixed: pinch detector is no longer recreated by zoom changes, and the same initial-pass gesture layer handles smooth photo/video swipe without competing child-view gesture consumption.")
