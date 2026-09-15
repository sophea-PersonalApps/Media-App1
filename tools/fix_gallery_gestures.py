from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")

text = text.replace(
    ".pointerInput(uri, currentIndex, isVideo, mediaZoom) {",
    ".pointerInput(uri, currentIndex, isVideo) {",
    1,
)

text = text.replace(
    "    var viewportHeight by remember(uri) { mutableIntStateOf(0) }\n    val swipeOffset = remember { Animatable(0f) }",
    "    var viewportHeight by remember(uri) { mutableIntStateOf(0) }\n    var dragOffset by remember { mutableFloatStateOf(0f) }\n    var videoNavigationStarted by remember(uri) { mutableStateOf(false) }\n    val swipeOffset = remember { Animatable(0f) }",
    1,
)
text = text.replace("        swipeOffset.snapTo(0f)\n        mediaZoom = 1f", "        dragOffset = 0f\n        videoNavigationStarted = false\n        mediaZoom = 1f", 1)

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
                                    dragOffset = horizontalDrag.coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())
                                    val threshold = minOf(140f, viewportWidth * 0.22f)
                                    val target = when {
                                        horizontalDrag <= -threshold && currentIndex < items.lastIndex -> currentIndex + 1
                                        horizontalDrag >= threshold && currentIndex > 0 -> currentIndex - 1
                                        else -> -1
                                    }
                                    if (target >= 0) {
                                        navigationStarted = true
                                        swipeScope.launch {
                                            val navigationAnim = Animatable(dragOffset)
                                            navigationAnim.animateTo(
                                                if (target > currentIndex) -viewportWidth.toFloat() else viewportWidth.toFloat(),
                                                tween(180)
                                            ) { dragOffset = value }
                                            onNavigate(target)
                                            dragOffset = 0f
                                        }
                                    }
                                }
                            }
                        }
'''
if old_gesture not in text:
    raise SystemExit("Gallery gesture block not found")
text = text.replace(old_gesture, new_gesture, 1)

text = text.replace("translationX = swipeOffset.value - viewportWidth.toFloat()", "translationX = dragOffset - viewportWidth.toFloat()")
text = text.replace("translationX = if (mediaZoom <= 1f) swipeOffset.value else mediaPanX", "translationX = if (mediaZoom <= 1f) dragOffset else mediaPanX")
text = text.replace("translationX = swipeOffset.value + viewportWidth.toFloat()", "translationX = dragOffset + viewportWidth.toFloat()")

old_video_call = "                    if (isVideo) VideoPlayer(uri, videoSpeed) else CachedFullImage(uri, context)"
new_video_call = '''                    if (isVideo) {
                        VideoPlayer(
                            uri,
                            videoSpeed,
                            onGesture = { panX, panY, gestureZoom, pointerCount ->
                                val wasZoomed = mediaZoom > 1f
                                val newZoom = (mediaZoom * gestureZoom).coerceIn(1f, 8f)
                                mediaZoom = newZoom
                                if (newZoom > 1f) {
                                    mediaPanX = (mediaPanX + panX).coerceIn(-viewportWidth.toFloat() * (newZoom - 1f) / 2f, viewportWidth.toFloat() * (newZoom - 1f) / 2f)
                                    mediaPanY = (mediaPanY + panY).coerceIn(-viewportHeight.toFloat() * (newZoom - 1f) / 2f, viewportHeight.toFloat() * (newZoom - 1f) / 2f)
                                    dragOffset = 0f
                                } else {
                                    mediaPanX = 0f
                                    mediaPanY = 0f
                                    if (!videoNavigationStarted && pointerCount == 1 && kotlin.math.abs(panX) > kotlin.math.abs(panY)) {
                                        dragOffset = (dragOffset + panX).coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())
                                        val threshold = minOf(140f, viewportWidth * 0.22f)
                                        val target = when {
                                            dragOffset <= -threshold && currentIndex < items.lastIndex -> currentIndex + 1
                                            dragOffset >= threshold && currentIndex > 0 -> currentIndex - 1
                                            else -> -1
                                        }
                                        if (target >= 0) {
                                            videoNavigationStarted = true
                                            swipeScope.launch {
                                                val navigationAnim = Animatable(dragOffset)
                                                navigationAnim.animateTo(
                                                    if (target > currentIndex) -viewportWidth.toFloat() else viewportWidth.toFloat(),
                                                    tween(180)
                                                ) { dragOffset = value }
                                                onNavigate(target)
                                                dragOffset = 0f
                                                videoNavigationStarted = false
                                            }
                                        }
                                    }
                                }
                            },
                            onGestureEnd = {
                                if (!videoNavigationStarted && dragOffset != 0f) {
                                    swipeScope.launch {
                                        val settle = Animatable(dragOffset)
                                        settle.animateTo(0f, tween(120)) { dragOffset = value }
                                        dragOffset = 0f
                                    }
                                }
                            }
                        )
                    } else CachedFullImage(uri, context)'''
if old_video_call not in text:
    raise SystemExit("VideoPlayer call not found")
text = text.replace(old_video_call, new_video_call, 1)

old_video_sig = "@Composable private fun VideoPlayer(uri: Uri, speed: Float) {"
new_video_sig = "@Composable private fun VideoPlayer(uri: Uri, speed: Float, onGesture: (panX: Float, panY: Float, zoom: Float, pointerCount: Int) -> Unit = { _, _, _, _ -> }, onGestureEnd: () -> Unit = {}) {"
if old_video_sig not in text:
    raise SystemExit("VideoPlayer signature not found")
text = text.replace(old_video_sig, new_video_sig, 1)

old_video_box = '''    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { videoView })
        when (state) {'''
new_video_box = '''    DisposableEffect(videoView, onGesture, onGestureEnd) {
        var lastX = 0f
        var lastY = 0f
        var lastSpan = 0f
        var multiTouch = false
        videoView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    lastSpan = 0f
                    multiTouch = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    multiTouch = true
                    if (event.pointerCount >= 2) {
                        val dx = event.getX(1) - event.getX(0)
                        val dy = event.getY(1) - event.getY(0)
                        lastSpan = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val dx = event.getX(1) - event.getX(0)
                        val dy = event.getY(1) - event.getY(0)
                        val span = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        val zoom = if (lastSpan > 0f) (span / lastSpan).coerceIn(0.5f, 2f) else 1f
                        if (lastSpan > 0f) onGesture(0f, 0f, zoom, event.pointerCount)
                        lastSpan = span
                    } else if (!multiTouch) {
                        val panX = event.x - lastX
                        val panY = event.y - lastY
                        onGesture(panX, panY, 1f, 1)
                        lastX = event.x
                        lastY = event.y
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    multiTouch = true
                    lastSpan = 0f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onGestureEnd()
                    multiTouch = false
                    lastSpan = 0f
                }
            }
            false
        }
        onDispose { videoView.setOnTouchListener(null) }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { videoView })
        when (state) {'''
if old_video_box not in text:
    raise SystemExit("VideoPlayer AndroidView block not found")
text = text.replace(old_video_box, new_video_box, 1)

# Keep the audit marker but make pinch response substantially less quantized than the previous 15% cap.
text = text.replace("(span / previousSpan).coerceIn(0.85f, 1.15f)", "(span / previousSpan).coerceIn(0.5f, 2f)")

helper_marker = "@Composable private fun CachedFullImage"
helper = '''private suspend fun PointerInputScope.detectGalleryTransformGestures(onGesture: (centroid: Offset, pan: Offset, zoom: Float, pointerCount: Int) -> Unit) {
    awaitPointerEventScope {
        while (true) {
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
                val zoom = if (pressed.size > 1 && previousSpan > 0f) (span / previousSpan).coerceIn(0.5f, 2f) else 1f

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
helper_start = text.find("private suspend fun PointerInputScope.detectGalleryTransformGestures")
if helper_start >= 0:
    helper_end = text.find(helper_marker, helper_start)
    if helper_end < 0:
        raise SystemExit("Existing gallery gesture helper boundary not found")
    text = text[:helper_start] + helper + text[helper_end:]
else:
    text = text.replace(helper_marker, helper + helper_marker, 1)

imports = [
    "import androidx.compose.ui.geometry.Offset",
    "import androidx.compose.ui.input.pointer.PointerEventPass",
    "import androidx.compose.ui.input.pointer.PointerInputScope",
    "import androidx.compose.foundation.gestures.awaitFirstDown",
    "import androidx.compose.ui.input.pointer.positionChanged",
]
for line in imports:
    if line not in text:
        text = text.replace("import androidx.compose.ui.zIndex", line + "\nimport androidx.compose.ui.zIndex", 1)

path.write_text(text, encoding="utf-8")
print("Gallery gestures updated: direct smoother pinch scaling, video touch forwarding for pinch/swipe, and the existing adjacent-item pager animation is preserved.")
