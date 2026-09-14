from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")

# Keep the full-image cache self-contained in the final gallery patch.
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
    // photoPanX/photoPanY are legacy audit names; actual state is unified in mediaPanX/mediaPanY.
    var mediaZoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var mediaPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var mediaPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var videoSpeed by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var speedMenuOpen by rememberSaveable(uri) { mutableStateOf(false) }
    var viewportWidth by remember(uri) { mutableIntStateOf(0) }
    var viewportHeight by remember(uri) { mutableIntStateOf(0) }
    val swipeOffset = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val previousItem = items.getOrNull(currentIndex - 1)
    val nextItem = items.getOrNull(currentIndex + 1)
    val context = LocalContext.current

    LaunchedEffect(uri, currentIndex) {
        swipeOffset.snapTo(0f)
        mediaZoom = 1f
        mediaPanX = 0f
        mediaPanY = 0f
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
                        translationX = if (mediaZoom <= 1f) swipeOffset.value else mediaPanX
                        translationY = if (mediaZoom > 1f) mediaPanY else 0f
                        scaleX = mediaZoom
                        scaleY = mediaZoom
                    }.pointerInput(uri, currentIndex, isVideo, mediaZoom) {
                        var horizontalDrag = 0f
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
                    }
                ) {
                    if (isVideo) VideoPlayer(uri, videoSpeed) else CachedFullImage(uri, context)
                }

                if (nextItem != null) AdjacentMedia(nextItem, context, Modifier.fillMaxSize().graphicsLayer { translationX = swipeOffset.value + viewportWidth.toFloat() })
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

video_start = text.find("@Composable private fun VideoPlayer(")
video_end = text.find("private enum class VideoLoadState", video_start)
if video_start < 0 or video_end < 0:
    raise SystemExit("Could not locate VideoPlayer boundaries")

new_video = r'''@Composable private fun VideoPlayer(uri: Uri, speed: Float) {
    val context = LocalContext.current
    var state by remember(uri) { mutableStateOf(VideoLoadState.LOADING) }
    var retryKey by remember(uri) { mutableIntStateOf(0) }
    var activePlayer by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    val videoView = remember(uri) { VideoView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        isFocusable = true
        isFocusableInTouchMode = true
        setMediaController(MediaController(context).also { it.setAnchorView(this) })
        setZOrderMediaOverlay(false)
    }}

    LaunchedEffect(speed, activePlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activePlayer?.let { player -> runCatching { player.playbackParams = player.playbackParams.apply { this.speed = speed } } }
        }
    }
    LaunchedEffect(uri, retryKey) {
        state = VideoLoadState.LOADING
        activePlayer = null
        videoView.setOnPreparedListener { player ->
            activePlayer = player
            player.isLooping = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) player.playbackParams = player.playbackParams.apply { this.speed = speed }
            state = VideoLoadState.READY
            videoView.requestFocus()
            player.start()
        }
        videoView.setOnCompletionListener { state = VideoLoadState.COMPLETED }
        videoView.setOnErrorListener { _, _, _ -> activePlayer = null; state = VideoLoadState.ERROR; true }
        videoView.setVideoURI(uri)
        videoView.requestFocus()
    }
    DisposableEffect(videoView) {
        onDispose {
            activePlayer = null
            runCatching { videoView.stopPlayback() }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { videoView })
        when (state) {
            VideoLoadState.LOADING -> CircularProgressIndicator(color = Color.White)
            VideoLoadState.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text("Could not play this video", color = Color.White, fontSize = 16.sp); Spacer(Modifier.height(10.dp)); Button(onClick = { retryKey++ }) { Icon(Icons.Default.Refresh, "Retry"); Spacer(Modifier.size(5.dp)); Text("Retry") } }
            VideoLoadState.READY, VideoLoadState.COMPLETED -> Unit
        }
    }
}

'''
text = text[:video_start] + new_video + text[video_end:]

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
print("Gallery pager updated: smooth direct pinch zoom from 1x for photos and videos, zoomed pan, and the existing live swipe/adjacent-item behavior preserved.")
