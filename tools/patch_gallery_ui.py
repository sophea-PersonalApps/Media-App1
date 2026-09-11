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

# ALBUM TRANSITION: clear the old gallery list immediately when an album is
# selected, and show a loading state while its MediaStore query completes.
text = replace_once(
    text,
    '    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }\n    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }\n',
    '    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }\n    var mediaLoading by remember { mutableStateOf(false) }\n    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }\n',
    "media loading state",
)
old_effect = '''    LaunchedEffect(tab, selectedAlbumId, refreshToken, access) {
        if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) albums = withContext(Dispatchers.IO) { queryAlbums(context, access) }
        else if (selectedAlbumId != null) media = withContext(Dispatchers.IO) { queryAlbumMedia(context, selectedAlbumId!!, access) }
        else media = withContext(Dispatchers.IO) { when (tab) { GalleryTab.PHOTOS -> if (access.images) queryMedia(context, false) else emptyList(); GalleryTab.VIDEOS -> if (access.videos) queryMedia(context, true) else emptyList(); GalleryTab.ALBUMS -> emptyList() } }
    }
'''
new_effect = '''    LaunchedEffect(tab, selectedAlbumId, refreshToken, access) {
        mediaLoading = true
        if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) {
            albums = withContext(Dispatchers.IO) { queryAlbums(context, access) }
        } else if (selectedAlbumId != null) {
            media = withContext(Dispatchers.IO) { queryAlbumMedia(context, selectedAlbumId!!, access) }
        } else {
            media = withContext(Dispatchers.IO) { when (tab) { GalleryTab.PHOTOS -> if (access.images) queryMedia(context, false) else emptyList(); GalleryTab.VIDEOS -> if (access.videos) queryMedia(context, true) else emptyList(); GalleryTab.ALBUMS -> emptyList() } }
        }
        mediaLoading = false
    }
'''
text = replace_once(text, old_effect, new_effect, "media loading effect")
text = replace_once(
    text,
    'if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) AlbumGrid(albums) { album -> selectedAlbumId = album.id; selectedAlbumName = album.name }',
    'if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) AlbumGrid(albums) { album -> media = emptyList(); selectedAlbumId = album.id; selectedAlbumName = album.name }',
    "album selection clears stale media",
)
old_grid = '''            if (unavailable && selectedAlbumId == null) MissingMediaPermission(tab == GalleryTab.PHOTOS, requestPermission)
            else MediaGrid(media, selectionMode, selectedItems, { item -> selectionMode = true; selectedItems[item.uri.toString()] = item }) { item -> if (selectionMode) { val key = item.uri.toString(); if (selectedItems.containsKey(key)) selectedItems.remove(key) else selectedItems[key] = item; if (selectedItems.isEmpty()) selectionMode = false } else { selectedUri = item.uri; selectedIsVideo = item.isVideo } }
'''
new_grid = '''            if (unavailable && selectedAlbumId == null) MissingMediaPermission(tab == GalleryTab.PHOTOS, requestPermission)
            else if (mediaLoading) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            else MediaGrid(media, selectionMode, selectedItems, { item -> selectionMode = true; selectedItems[item.uri.toString()] = item }) { item -> if (selectionMode) { val key = item.uri.toString(); if (selectedItems.containsKey(key)) selectedItems.remove(key) else selectedItems[key] = item; if (selectedItems.isEmpty()) selectionMode = false } else { selectedUri = item.uri; selectedIsVideo = item.isVideo } }
'''
text = replace_once(text, old_grid, new_grid, "album loading display")

# VIDEO ACTION: make Speed a real IconButton in the exact same top action-bar
# position as Edit is for photos. The dropdown is anchored to that button.
if 'import androidx.compose.material.icons.filled.Speed' not in text:
    text = replace_once(text, 'import androidx.compose.material.icons.filled.Share\n', 'import androidx.compose.material.icons.filled.Share\nimport androidx.compose.material.icons.filled.Speed\n', "speed icon import")
old_speed = '''                if (isVideo) {
                    Box {
                        TextButton(onClick = { speedMenuOpen = true }) { Text("${videoSpeed}x", color = Color.White, fontWeight = FontWeight.Bold) }
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { selectedSpeed ->
                                DropdownMenuItem(text = { Text("${selectedSpeed}x") }, onClick = { videoSpeed = selectedSpeed; speedMenuOpen = false })
                            }
                        }
                    }
                } else {
'''
new_speed = '''                if (isVideo) {
                    Box {
                        IconButton(onClick = { speedMenuOpen = true }) { Icon(Icons.Default.Speed, "Playback speed", tint = Color.White) }
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { selectedSpeed ->
                                DropdownMenuItem(text = { Text("${selectedSpeed}x", fontWeight = if (videoSpeed == selectedSpeed) FontWeight.Bold else FontWeight.Normal) }, onClick = { videoSpeed = selectedSpeed; speedMenuOpen = false })
                            }
                        }
                    }
                } else {
'''
text = replace_once(text, old_speed, new_speed, "video speed icon action")

checks = {
    "album loading state": 'var mediaLoading by remember { mutableStateOf(false) }' in text,
    "album query loading": 'mediaLoading = true' in text and 'mediaLoading = false' in text,
    "album clears stale media": 'media = emptyList(); selectedAlbumId = album.id' in text,
    "album loading indicator": 'else if (mediaLoading) Box(Modifier.fillMaxWidth().weight(1f)' in text,
    "speed icon import": 'import androidx.compose.material.icons.filled.Speed' in text,
    "speed is top icon button": 'IconButton(onClick = { speedMenuOpen = true }) { Icon(Icons.Default.Speed, "Playback speed"' in text,
    "video speed menu": 'DropdownMenu(expanded = speedMenuOpen' in text,
    "video player receives speed": 'VideoPlayer(uri, videoSpeed)' in text,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("GALLERY UI AUDIT FAILED: " + "; ".join(failed))
GALLERY.write_text(text, encoding="utf-8")
print("Album transition flash removed and video Speed top action implemented.")
