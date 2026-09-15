    return result
}
private fun queryAlbumMedia(context: Context, bucketId: String, access: MediaAccess): List<MediaItem> { val items = ArrayList<MediaItem>(); if (access.images) items += queryMedia(context, false, bucketId); if (access.videos) items += queryMedia(context, true, bucketId); return items.sortedByDescending { it.dateAdded } }
private fun queryAlbums(context: Context, access: MediaAccess): List<Album> {
    val grouped = LinkedHashMap<String, MutableList<MediaItem>>(); val allItems = ArrayList<MediaItem>(); if (access.images) allItems += queryMedia(context, false); if (access.videos) allItems += queryMedia(context, true)
    allItems.forEach { item -> item.bucketId?.let { grouped.getOrPut(it) { mutableListOf() }.add(item) } }
    return grouped.mapNotNull { (id, items) -> val cover = items.maxByOrNull { it.dateAdded } ?: return@mapNotNull null; Album(id, cover.bucketName?.takeIf(String::isNotBlank) ?: "Unknown", items.size, cover.uri, cover.isVideo) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
private fun mediaCollection(videosOnly: Boolean): Uri = if (Build.VERSION.SDK_INT >= 29) { if (videosOnly) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) } else if (videosOnly) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

@Composable private fun GalleryTabs(tab: GalleryTab, access: MediaAccess, onSelected: (GalleryTab) -> Unit) { Row(Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) { GalleryTab.values().forEach { value -> val label = value.name.lowercase().replaceFirstChar { it.uppercase() }; val available = when (value) { GalleryTab.PHOTOS -> access.images; GalleryTab.VIDEOS -> access.videos; GalleryTab.ALBUMS -> access.any }; Text(label, color = Color.White.copy(alpha = if (tab == value) 1f else if (available) 0.55f else 0.3f), fontWeight = if (tab == value) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable { onSelected(value) }.padding(horizontal = 20.dp, vertical = 8.dp)) } } }
@Composable private fun GalleryBottomNavigation(onCamera: () -> Unit, onGallery: () -> Unit, gallerySelected: Boolean) { Row(Modifier.fillMaxWidth().background(Color.Black).height(58.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { Text("CAMERA", color = Color.White.copy(alpha = if (!gallerySelected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (!gallerySelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable(onClick = onCamera).padding(horizontal = 20.dp, vertical = 10.dp)); Text("GALLERY", color = Color.White.copy(alpha = if (gallerySelected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (gallerySelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable(onClick = onGallery).padding(horizontal = 20.dp, vertical = 10.dp)) } }
@Composable private fun GalleryPermissionScreen(requestPermission: () -> Unit, onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(Color.Black).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("Allow photo and video access", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp)); Text("Media Toolbox uses Android's media library to show photos and videos already on your device. Your media is not uploaded.", color = Color.LightGray, fontSize = 15.sp); Spacer(Modifier.height(20.dp)); Button(onClick = requestPermission) { Text("Allow access") }; Spacer(Modifier.height(8.dp)); Button(onClick = onBack) { Text("Back to camera") } } }
@Composable private fun ColumnScope.MissingMediaPermission(photos: Boolean, requestPermission: () -> Unit) { Column(Modifier.fillMaxWidth().weight(1f).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(if (photos) "Photo access is not enabled" else "Video access is not enabled", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp)); Button(onClick = requestPermission) { Text("Change media access") } } }

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ColumnScope.MediaGrid(items: List<MediaItem>, selectionMode: Boolean, selectedItems: Map<String, MediaItem>, onLongClick: (MediaItem) -> Unit, onClick: (MediaItem) -> Unit) { if (items.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No media found", color = Color.LightGray) } else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 105.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { items(items, key = { it.uri.toString() }) { item -> MediaThumbnail(item.uri, item.name, item.isVideo, Modifier.aspectRatio(1f), selectedItems.containsKey(item.uri.toString()), selectionMode, { onLongClick(item) }, { onClick(item) }) } } }
@Composable private fun ColumnScope.AlbumGrid(albums: List<Album>, onClick: (Album) -> Unit) { if (albums.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No albums found", color = Color.LightGray) } else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 150.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(albums, key = { it.id }) { album -> Column(Modifier.fillMaxWidth().clickable { onClick(album) }) { MediaThumbnail(album.coverUri, album.name, album.coverIsVideo, Modifier.fillMaxWidth().aspectRatio(1f)); Spacer(Modifier.height(4.dp)); Text(album.name, color = Color.White, maxLines = 1); Text("${album.count} items", color = Color.LightGray, fontSize = 12.sp) } } } }

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun MediaThumbnail(uri: Uri, description: String, isVideo: Boolean, modifier: Modifier, selected: Boolean = false, selectionMode: Boolean = false, onLongClick: () -> Unit = {}, onClick: () -> Unit = {}) { val context = LocalContext.current; var bitmap by remember(uri) { mutableStateOf(ThumbnailMemoryCache.get(uri, isVideo)) }; LaunchedEffect(uri, isVideo) { if (bitmap == null) bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, uri, isVideo) } }; Box(modifier.background(if (selected) Color.White.copy(alpha = 0.32f) else Color.DarkGray).combinedClickable(onClick = onClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) { bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }; if (bitmap == null && isVideo) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp); if (isVideo) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("VIDEO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }; if (selected) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("✓", color = Color.Black, fontWeight = FontWeight.Bold) } else if (selectionMode) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)) } }

@Composable private fun MediaViewer(uri: Uri, isVideo: Boolean, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var confirmDelete by rememberSaveable(uri) { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                onEdit?.let { IconButton(onClick = it) { Icon(Icons.Default.Edit, "Edit", tint = Color.White) } }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isVideo) VideoPlayer(uri) else ZoomablePhoto(uri)
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete media?") }, text = { Text("Delete this ${if (isVideo) "video" else "photo"} from your device? This action cannot be undone.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
}

@Composable private fun ZoomablePhoto(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var scale by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    LaunchedEffect(uri) { bitmap = withContext(Dispatchers.IO) { loadFullImage(context, uri) } }
    bitmap?.let { image ->
        Box(Modifier.fillMaxSize().pointerInput(uri) {
            detectTransformGestures { _, pan, zoom, _ ->
                val nextScale = (scale * zoom).coerceIn(1f, 8f)
                scale = nextScale
                if (nextScale <= 1.001f) { offsetX = 0f; offsetY = 0f }
                else { offsetX += pan.x; offsetY += pan.y }
            }
        }, contentAlignment = Alignment.Center) {
            Image(image.asImageBitmap(), "Photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }, contentScale = ContentScale.Fit)
        }
    } ?: CircularProgressIndicator(color = Color.White)
}

@Composable private fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    var state by remember(uri) { mutableStateOf(VideoLoadState.LOADING) }
    var retryKey by remember(uri) { mutableIntStateOf(0) }
    var speed by remember(uri) { mutableFloatStateOf(1f) }
    var speedMenuOpen by remember(uri) { mutableStateOf(false) }
    var activePlayer by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var videoZoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var videoOffsetX by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var videoOffsetY by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    val videoView = remember(uri) { VideoView(context).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); isFocusable = true; isFocusableInTouchMode = true; setMediaController(MediaController(context).also { it.setAnchorView(this) }) } }
    val scaleDetector = remember(videoView) { android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() { override fun onScale(detector: android.view.ScaleGestureDetector): Boolean { videoZoom = (videoZoom * detector.scaleFactor).coerceIn(1f, 5f); if (videoZoom <= 1.001f) { videoOffsetX = 0f; videoOffsetY = 0f }; videoView.scaleX = videoZoom; videoView.scaleY = videoZoom; videoView.translationX = videoOffsetX; videoView.translationY = videoOffsetY; return true } }) }
    DisposableEffect(videoView, scaleDetector) { videoView.setOnTouchListener { _, event -> if (event.pointerCount >= 2 || scaleDetector.isInProgress) scaleDetector.onTouchEvent(event); false }; onDispose { videoView.setOnTouchListener(null); activePlayer = null; runCatching { videoView.stopPlayback() } } }
    LaunchedEffect(uri, retryKey) { state = VideoLoadState.LOADING; activePlayer = null; videoView.setOnPreparedListener { player -> activePlayer = player; player.isLooping = false; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) player.playbackParams = player.playbackParams.apply { this.speed = speed }; state = VideoLoadState.READY; videoView.requestFocus(); player.start() }; videoView.setOnCompletionListener { state = VideoLoadState.COMPLETED }; videoView.setOnErrorListener { _, _, _ -> activePlayer = null; state = VideoLoadState.ERROR; true }; videoView.setVideoURI(uri); videoView.requestFocus() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { videoView })
        when (state) { VideoLoadState.LOADING -> CircularProgressIndicator(color = Color.White); VideoLoadState.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text("Could not play this video", color = Color.White, fontSize = 16.sp); Spacer(Modifier.height(10.dp)); Button(onClick = { retryKey++ }) { Icon(Icons.Default.Refresh, "Retry"); Spacer(Modifier.size(5.dp)); Text("Retry") } }; VideoLoadState.READY, VideoLoadState.COMPLETED -> Unit }
        if (state == VideoLoadState.READY || state == VideoLoadState.COMPLETED) Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { Button(onClick = { speedMenuOpen = true }) { Text("${speed}x") }; DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { selectedSpeed -> DropdownMenuItem(text = { Text("${selectedSpeed}x") }, onClick = { speed = selectedSpeed; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) activePlayer?.let { player -> runCatching { player.playbackParams = player.playbackParams.apply { this.speed = selectedSpeed } } }; speedMenuOpen = false }) } } }
    }
}

