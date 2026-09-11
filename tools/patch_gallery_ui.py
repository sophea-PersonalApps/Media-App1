from pathlib import Path

GALLERY = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        if count == 0 and new in text:
            return text
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

text = GALLERY.read_text(encoding="utf-8")

# ALBUMS: the previous AlbumGrid put a clickable parent around MediaThumbnail,
# but MediaThumbnail itself has a combinedClickable that consumes the tap. Put
# the album click on the thumbnail itself so album covers actually open.
old_album = '''@Composable private fun ColumnScope.AlbumGrid(albums: List<Album>, onClick: (Album) -> Unit) { if (albums.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No albums found", color = Color.LightGray) } else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 150.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(albums, key = { it.id }) { album -> Column(Modifier.fillMaxWidth().clickable { onClick(album) }) { MediaThumbnail(album.coverUri, album.name, album.coverIsVideo, Modifier.fillMaxWidth().aspectRatio(1f)); Spacer(Modifier.height(4.dp)); Text(album.name, color = Color.White, maxLines = 1); Text("${album.count} items", color = Color.LightGray, fontSize = 12.sp) } } } }
'''
new_album = '''@Composable private fun ColumnScope.AlbumGrid(albums: List<Album>, onClick: (Album) -> Unit) { if (albums.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No albums found", color = Color.LightGray) } else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 150.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(albums, key = { it.id }) { album -> Column(Modifier.fillMaxWidth()) { MediaThumbnail(album.coverUri, album.name, album.coverIsVideo, Modifier.fillMaxWidth().aspectRatio(1f), onClick = { onClick(album) }); Spacer(Modifier.height(4.dp)); Text(album.name, color = Color.White, maxLines = 1); Text("${album.count} items", color = Color.LightGray, fontSize = 12.sp) } } } }
'''
text = replace_once(text, old_album, new_album, "album grid")

# VIDEO VIEWER TOP BAR: keep the exact same Back / Share / action / Delete
# structure as photos, replacing Edit with a speed button for videos.
old_header = '''    var confirmDelete by rememberSaveable(uri) { mutableStateOf(false) }
    var photoZoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                onEdit?.let { IconButton(onClick = it) { Icon(Icons.Default.Edit, "Edit", tint = Color.White) } }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isVideo) VideoPlayer(uri) else {
'''
new_header = '''    var confirmDelete by rememberSaveable(uri) { mutableStateOf(false) }
    var photoZoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var videoSpeed by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var speedMenuOpen by rememberSaveable(uri) { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                if (isVideo) {
                    Box {
                        TextButton(onClick = { speedMenuOpen = true }) { Text("${videoSpeed}x", color = Color.White, fontWeight = FontWeight.Bold) }
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { selectedSpeed ->
                                DropdownMenuItem(text = { Text("${selectedSpeed}x") }, onClick = { videoSpeed = selectedSpeed; speedMenuOpen = false })
                            }
                        }
                    }
                } else {
                    onEdit?.let { IconButton(onClick = it) { Icon(Icons.Default.Edit, "Edit", tint = Color.White) } }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isVideo) VideoPlayer(uri, videoSpeed) else {
'''
text = replace_once(text, old_header, new_header, "media viewer video action bar")

# VIDEO PLAYER: speed is now controlled by the viewer's top action button.
text = replace_once(text, '@Composable private fun VideoPlayer(uri: Uri) {', '@Composable private fun VideoPlayer(uri: Uri, speed: Float) {', "video player signature")
text = replace_once(text, '    var speed by remember(uri) { mutableFloatStateOf(1f) }\n    var speedMenuOpen by remember(uri) { mutableStateOf(false) }\n', '', "old video speed state")
old_speed_overlay = '''        if (state == VideoLoadState.READY || state == VideoLoadState.COMPLETED) Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Button(onClick = { speedMenuOpen = true }) { Text("${speed}x") }
            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { selectedSpeed -> DropdownMenuItem(text = { Text("${selectedSpeed}x") }, onClick = { speed = selectedSpeed; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) activePlayer?.let { player -> runCatching { player.playbackParams = player.playbackParams.apply { this.speed = selectedSpeed } } }; speedMenuOpen = false }) } }
        }
'''
text = replace_once(text, old_speed_overlay, '', "old video speed overlay")
anchor = '''    LaunchedEffect(uri, retryKey) {
'''
insert = '''    LaunchedEffect(speed, activePlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activePlayer?.let { player ->
                runCatching { player.playbackParams = player.playbackParams.apply { this.speed = speed } }
            }
        }
    }
    LaunchedEffect(uri, retryKey) {
'''
text = replace_once(text, anchor, insert, "video speed effect anchor")

# Final audit: prove both requested fixes are present and the old video speed
# overlay / broken album parent click are gone.
checks = {
    "album thumbnail owns click": 'MediaThumbnail(album.coverUri, album.name, album.coverIsVideo, Modifier.fillMaxWidth().aspectRatio(1f), onClick = { onClick(album) })' in text,
    "video speed state": 'var videoSpeed by rememberSaveable(uri)' in text,
    "video speed top button": 'Text("${videoSpeed}x", color = Color.White' in text,
    "video speed menu": 'speedMenuOpen = true' in text,
    "video player receives speed": 'VideoPlayer(uri, videoSpeed)' in text,
    "old video speed overlay removed": 'Box(Modifier.align(Alignment.TopEnd).padding(12.dp))' not in text,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("GALLERY UI AUDIT FAILED: " + "; ".join(failed))
GALLERY.write_text(text, encoding="utf-8")
print("Video viewer actions and album opening implementation applied and audited.")
