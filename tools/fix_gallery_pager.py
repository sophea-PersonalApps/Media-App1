from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")

cache_marker = "private object ThumbnailMemoryCache {"
if cache_marker not in text:
    raise SystemExit("ThumbnailMemoryCache not found")
if "fun getFull(uri: Uri)" not in text:
    old = '    fun put(uri: Uri, isVideo: Boolean, bitmap: Bitmap): Bitmap { cache.put("$isVideo|$uri", bitmap); return bitmap }\n}'
    new = '''    fun put(uri: Uri, isVideo: Boolean, bitmap: Bitmap): Bitmap { cache.put("$isVideo|$uri", bitmap); return bitmap }
    private const val FULL_PREFIX = "full|"
    fun getFull(uri: Uri): Bitmap? = cache.get(FULL_PREFIX + uri)
    fun putFull(uri: Uri, bitmap: Bitmap): Bitmap { cache.put(FULL_PREFIX + uri, bitmap); return bitmap }
}'''
    if old not in text:
        raise SystemExit("ThumbnailMemoryCache insertion point not found")
    text = text.replace(old, new, 1)

start = text.find("@Composable private fun MediaViewer(")
end = text.find("@Composable private fun VideoPlayer(", start)
if start < 0 or end < 0:
    raise SystemExit("Could not locate MediaViewer/VideoPlayer boundaries")

new_viewer = r'''@Composable private fun MediaViewer(uri: Uri, isVideo: Boolean, items: List<MediaItem>, currentIndex: Int, onNavigate: (Int) -> Unit, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var confirmDelete by rememberSaveable(uri) { mutableStateOf(false) }
    var mediaZoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var mediaPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var mediaPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var videoSpeed by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var speedMenuOpen by rememberSaveable(uri) { mutableStateOf(false) }
    var viewportWidth by remember(uri) { mutableIntStateOf(0) }
    var viewportHeight by remember(uri) { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(uri, currentIndex) {
        mediaZoom = 1f
        mediaPanX = 0f
        mediaPanY = 0f
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
            Box(
                Modifier.fillMaxWidth().weight(1f).onSizeChanged { viewportWidth = it.width; viewportHeight = it.height },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = mediaPanX
                        translationY = mediaPanY
                        scaleX = mediaZoom
                        scaleY = mediaZoom
                    }.pointerInput(uri, currentIndex) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldZoom = mediaZoom
                            val zoomRatio = zoom.coerceIn(0.5f, 2f)
                            val newZoom = (oldZoom * zoomRatio).coerceIn(1f, 8f)
                            val focalX = centroid.x - viewportWidth / 2f
                            val focalY = centroid.y - viewportHeight / 2f
                            val scaleRatio = if (oldZoom > 0f) newZoom / oldZoom else 1f
                            val hasPinch = zoomRatio != 1f
                            if (hasPinch) {
                                mediaPanX = ((mediaPanX + focalX) * scaleRatio - focalX + pan.x)
                                    .coerceIn(-viewportWidth.toFloat() * (newZoom - 1f) / 2f, viewportWidth.toFloat() * (newZoom - 1f) / 2f)
                                mediaPanY = ((mediaPanY + focalY) * scaleRatio - focalY + pan.y)
                                    .coerceIn(-viewportHeight.toFloat() * (newZoom - 1f) / 2f, viewportHeight.toFloat() * (newZoom - 1f) / 2f)
                            } else if (oldZoom > 1f) {
                                mediaPanX = (mediaPanX + pan.x).coerceIn(-viewportWidth.toFloat() * (oldZoom - 1f) / 2f, viewportWidth.toFloat() * (oldZoom - 1f) / 2f)
                                mediaPanY = (mediaPanY + pan.y).coerceIn(-viewportHeight.toFloat() * (oldZoom - 1f) / 2f, viewportHeight.toFloat() * (oldZoom - 1f) / 2f)
                            }
                            mediaZoom = newZoom
                        }
                    }
                ) {
                    if (isVideo) VideoPlayer(uri, videoSpeed) else CachedFullImage(uri, context)
                }
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete media?") }, text = { Text("Delete this ${if (isVideo) "video" else "photo"} from your device? This action cannot be undone.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
}

@Composable private fun CachedFullImage(uri: Uri, context: Context) {
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
    bitmap?.let { Image(it.asImageBitmap(), "Photo", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
}

'''
text = text[:start] + new_viewer + text[end:]

for line in [
    "import androidx.compose.animation.core.Animatable",
    "import androidx.compose.animation.core.tween",
    "import androidx.compose.foundation.gestures.detectTransformGestures",
    "import androidx.compose.ui.zIndex",
    "import kotlinx.coroutines.launch",
]:
    if line not in text:
        text = text.replace("import androidx.compose.foundation.layout.*", line + "\nimport androidx.compose.foundation.layout.*", 1)

path.write_text(text, encoding="utf-8")
print("Gallery viewer now uses Compose's supported transform detector for stable focal-point pinch/pan without swipe-between-items or adjacent-item transitions.")
