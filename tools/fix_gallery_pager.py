from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")
start = text.find("@Composable private fun MediaViewer(")
end = text.find("@Composable private fun VideoPlayer(", start)
if start < 0 or end < 0:
    raise SystemExit("Could not locate MediaViewer/VideoPlayer boundaries")

new_viewer = r'''@Composable private fun MediaViewer(uri: Uri, isVideo: Boolean, items: List<MediaItem>, currentIndex: Int, onNavigate: (Int) -> Unit, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var confirmDelete by rememberSaveable(uri) { mutableStateOf(false) }
    var photoZoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var videoSpeed by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var speedMenuOpen by rememberSaveable(uri) { mutableStateOf(false) }
    var viewportWidth by remember(uri) { mutableIntStateOf(0) }
    var viewportHeight by remember(uri) { mutableIntStateOf(0) }
    var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    val swipeOffset = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val previousItem = items.getOrNull(currentIndex - 1)
    val nextItem = items.getOrNull(currentIndex + 1)
    val context = LocalContext.current

    LaunchedEffect(uri, currentIndex) {
        swipeOffset.snapTo(0f)
        photoZoom = 1f
        photoPanX = 0f
        photoPanY = 0f
        // Warm adjacent photos in parallel, without blocking the current image.
        listOfNotNull(previousItem, nextItem).filter { !it.isVideo && ThumbnailMemoryCache.getFull(it.uri) == null }.forEach { item ->
            swipeScope.launch(Dispatchers.IO) {
                loadFullImage(context, item.uri)?.let { ThumbnailMemoryCache.putFull(item.uri, it) }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                if (isVideo) {
                    Box {
                        IconButton(onClick = { speedMenuOpen = true }) { Icon(Icons.Default.Speed, "Playback speed", tint = Color.White) }
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { selectedSpeed ->
                                DropdownMenuItem(text = { Text("${selectedSpeed}x", fontWeight = if (videoSpeed == selectedSpeed) FontWeight.Bold else FontWeight.Normal) }, onClick = { videoSpeed = selectedSpeed; speedMenuOpen = false })
                            }
                        }
                    }
                } else {
                    onEdit?.let { IconButton(onClick = it) { Icon(Icons.Default.Edit, "Edit", tint = Color.White) } }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }, contentAlignment = Alignment.Center) {
                if (previousItem != null) AdjacentMedia(previousItem, context, Modifier.fillMaxSize().graphicsLayer { translationX = swipeOffset.value - viewportWidth.toFloat() })
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = if (photoZoom <= 1f) swipeOffset.value else 0f
                        scaleX = if (isVideo) 1f else photoZoom
                        scaleY = if (isVideo) 1f else photoZoom
                    }.pointerInput(uri, currentIndex, isVideo, photoZoom) {
                        if (isVideo) {
                            var dragX = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, amount ->
                                    dragX += amount
                                    swipeScope.launch { swipeOffset.snapTo(dragX.coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())) }
                                },
                                onDragEnd = {
                                    val threshold = minOf(140f, viewportWidth * 0.22f)
                                    val target = when {
                                        dragX <= -threshold && currentIndex < items.lastIndex -> currentIndex + 1
                                        dragX >= threshold && currentIndex > 0 -> currentIndex - 1
                                        else -> -1
                                    }
                                    swipeScope.launch {
                                        if (target >= 0) {
                                            swipeOffset.animateTo(if (target > currentIndex) -viewportWidth.toFloat() else viewportWidth.toFloat(), tween(180))
                                            onNavigate(target)
                                            swipeOffset.snapTo(0f)
                                        } else swipeOffset.animateTo(0f, tween(160))
                                    }
                                    dragX = 0f
                                },
                                onDragCancel = { dragX = 0f; swipeScope.launch { swipeOffset.animateTo(0f, tween(160)) } }
                            )
                        } else {
                            var horizontalDrag = 0f
                            var navigationStarted = false
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)
                                photoZoom = newZoom
                                if (newZoom <= 1f) {
                                    photoPanX = 0f
                                    photoPanY = 0f
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
                                } else {
                                    horizontalDrag = 0f
                                    photoPanX = (photoPanX + pan.x).coerceIn(-viewportWidth.toFloat() * (newZoom - 1f) / 2f, viewportWidth.toFloat() * (newZoom - 1f) / 2f)
                                    photoPanY = (photoPanY + pan.y).coerceIn(-viewportHeight.toFloat() * (newZoom - 1f) / 2f, viewportHeight.toFloat() * (newZoom - 1f) / 2f)
                                }
                            }
                        }
                    }
                ) {
                    if (isVideo) VideoPlayer(uri, videoSpeed) else CachedFullImage(uri, context, photoPanX, photoPanY)
                }
                if (nextItem != null) AdjacentMedia(nextItem, context, Modifier.fillMaxSize().graphicsLayer { translationX = swipeOffset.value + viewportWidth.toFloat() })
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete media?") }, text = { Text("Delete this ${if (isVideo) "video" else "photo"} from your device? This action cannot be undone.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
}

@Composable private fun CachedFullImage(uri: Uri, context: Context, panX: Float, panY: Float) {
    // Paint the existing thumbnail immediately, then replace it with the full image when decoding finishes.
    var bitmap by remember(uri) { mutableStateOf(ThumbnailMemoryCache.getFull(uri) ?: ThumbnailMemoryCache.get(uri, false)) }
    LaunchedEffect(uri) {
        ThumbnailMemoryCache.getFull(uri)?.let { bitmap = it; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            loadFullImage(context, uri)?.let { full ->
                ThumbnailMemoryCache.putFull(uri, full)
                withContext(Dispatchers.Main) { bitmap = full }
            }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), "Photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer { translationX = panX; translationY = panY }, contentScale = ContentScale.Fit) }
}

@Composable private fun AdjacentMedia(item: MediaItem, context: Context, modifier: Modifier) {
    var bitmap by remember(item.uri, item.isVideo) { mutableStateOf(ThumbnailMemoryCache.get(item.uri, item.isVideo)) }
    LaunchedEffect(item.uri, item.isVideo) {
        if (bitmap == null) bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, item.uri, item.isVideo) }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), item.name, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
    }
}

'''
text = text[:start] + new_viewer + text[end:]
path.write_text(text, encoding="utf-8")
print("Gallery pager updated: pinch zoom is active from 1x, one-finger horizontal photo swipes remain live at 1x, adjacent images stay visible during the swipe, and cached thumbnails prevent a loading spinner while the full image decodes.")