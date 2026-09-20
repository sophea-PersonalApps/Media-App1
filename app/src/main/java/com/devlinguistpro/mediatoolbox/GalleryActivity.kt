package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class GalleryTab { PHOTOS, VIDEOS, ALBUMS }
private data class MediaAccess(val images: Boolean, val videos: Boolean, val partial: Boolean = false) { val any: Boolean get() = images || videos }
private data class MediaItem(val uri: Uri, val name: String, val dateAdded: Long, val isVideo: Boolean, val bucketId: String?, val bucketName: String?)
private data class Album(val id: String, val name: String, val count: Int, val coverUri: Uri, val coverIsVideo: Boolean)

private object ThumbnailMemoryCache {
    private const val MAX_CACHE_BYTES = 32 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) { override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount }
    fun get(uri: Uri, isVideo: Boolean): Bitmap? = cache.get("$isVideo|$uri")
    fun put(uri: Uri, isVideo: Boolean, bitmap: Bitmap): Bitmap { cache.put("$isVideo|$uri", bitmap); return bitmap }
}

class GalleryActivity : ComponentActivity() {
    private var mediaAccess by mutableStateOf(MediaAccess(false, false))
    private var pendingDeleteResult: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { mediaAccess = currentMediaAccess() }
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result -> val callback = pendingDeleteResult; pendingDeleteResult = null; callback?.invoke(result.resultCode == RESULT_OK) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaAccess = currentMediaAccess()
        setContent { MaterialTheme { GalleryApp(mediaAccess, ::requestMediaPermission, ::finish, ::deleteMedia, ::deleteMediaBatch, ::openEditor) } }
    }
    override fun onResume() { super.onResume(); val access = currentMediaAccess(); if (mediaAccess != access) mediaAccess = access }
    private fun currentMediaAccess(): MediaAccess {
        if (Build.VERSION.SDK_INT <= 32) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            return MediaAccess(granted, granted, false)
        }
        val imagesFull = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val videosFull = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 34) {
            val selected = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            if (selected) return MediaAccess(imagesFull || selected, videosFull || selected, !imagesFull || !videosFull)
        }
        return MediaAccess(imagesFull, videosFull, false)
    }
    private fun requestMediaPermission() { when { Build.VERSION.SDK_INT >= 34 -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)); Build.VERSION.SDK_INT >= 33 -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)); else -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) } }
    private fun deleteMedia(uri: Uri, onResult: (Boolean) -> Unit) {
        try {
            if (Build.VERSION.SDK_INT >= 30) { pendingDeleteResult = onResult; val request = MediaStore.createDeleteRequest(contentResolver, listOf(uri)); deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build()) }
            else onResult(contentResolver.delete(uri, null, null) > 0)
        } catch (e: RecoverableSecurityException) { pendingDeleteResult = onResult; deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()) }
        catch (_: Exception) { onResult(false); Toast.makeText(this, "Could not delete item", Toast.LENGTH_SHORT).show() }
    }
    private fun deleteMediaBatch(uris: List<Uri>, onResult: (Boolean) -> Unit) {
        if (uris.isEmpty()) { onResult(true); return }
        try {
            if (Build.VERSION.SDK_INT >= 30) { pendingDeleteResult = onResult; val request = MediaStore.createDeleteRequest(contentResolver, uris); deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build()) }
            else onResult(uris.all { contentResolver.delete(it, null, null) > 0 })
        } catch (_: Exception) { onResult(false); Toast.makeText(this, "Could not delete selected items", Toast.LENGTH_SHORT).show() }
    }
    private fun openEditor(uri: Uri) { startActivity(Intent(this, GalleryEditorActivity::class.java).putExtra(GalleryEditorActivity.EXTRA_URI, uri)) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryApp(access: MediaAccess, requestPermission: () -> Unit, onBack: () -> Unit, onDelete: (Uri, (Boolean) -> Unit) -> Unit, onDeleteBatch: (List<Uri>, (Boolean) -> Unit) -> Unit, onEdit: (Uri) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedIsVideo by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val selectedItems = remember { mutableStateMapOf<String, MediaItem>() }
    val context = LocalContext.current

    BackHandler(enabled = selectedUri != null || selectedAlbumId != null || selectionMode) {
        when {
            selectedUri != null -> selectedUri = null
            selectionMode -> { selectionMode = false; selectedItems.clear() }
            selectedAlbumId != null -> { selectedAlbumId = null; selectedAlbumName = null; tab = GalleryTab.ALBUMS }
        }
    }

    if (selectedUri != null) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.weight(1f)) { MediaViewer(media, selectedUri!!, { selectedUri = null }, { item -> shareMedia(context, item.uri) }, { item -> if (!item.isVideo) onEdit(item.uri) }, { item -> val uriBeingDeleted = item.uri; onDelete(uriBeingDeleted) { deleted -> if (deleted) { selectedUri = null; refreshToken++ } } }) }
            GalleryBottomNavigation(onCamera = onBack, onGallery = { selectedUri = null }, gallerySelected = true)
        }
        return
    }
    if (!access.any) { GalleryPermissionScreen(requestPermission, onBack); return }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    LaunchedEffect(tab, selectedAlbumId, refreshToken, access) {
        if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) albums = withContext(Dispatchers.IO) { queryAlbums(context, access) }
        else if (selectedAlbumId != null) media = withContext(Dispatchers.IO) { queryAlbumMedia(context, selectedAlbumId!!, access) }
        else media = withContext(Dispatchers.IO) { when (tab) { GalleryTab.PHOTOS -> if (access.images) queryMedia(context, false) else emptyList(); GalleryTab.VIDEOS -> if (access.videos) queryMedia(context, true) else emptyList(); GalleryTab.ALBUMS -> emptyList() } }
    }
    LaunchedEffect(tab, selectedAlbumId) { selectionMode = false; selectedItems.clear(); showDeleteConfirmation = false }
    if (showDeleteConfirmation) AlertDialog(onDismissRequest = { showDeleteConfirmation = false }, title = { Text("Delete selected media?") }, text = { Text("Delete ${selectedItems.size} selected item${if (selectedItems.size == 1) "" else "s"} from your device? This action cannot be undone.") }, confirmButton = { TextButton(onClick = { showDeleteConfirmation = false; val uris = selectedItems.values.map { it.uri }; onDeleteBatch(uris) { deleted -> if (deleted) { selectionMode = false; selectedItems.clear(); refreshToken++ } } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } })
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (selectionMode) GallerySelectionBar(selectedItems.size, media.size, { selectionMode = false; selectedItems.clear() }, { media.forEach { selectedItems[it.uri.toString()] = it } }, { selectedItems.clear() }, { shareMedia(context, selectedItems.values.map { it.uri }) }, { showDeleteConfirmation = true })
        else if (selectedAlbumId == null) {
            if (access.partial) PartialMediaAccessBanner(requestPermission)
            GalleryTabs(tab, access) { tab = it; selectedAlbumId = null; selectedAlbumName = null }
        }
        if (selectedAlbumId != null) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { selectedAlbumId = null; selectedAlbumName = null; tab = GalleryTab.ALBUMS }) { Icon(Icons.Default.ArrowBack, "Back to albums", tint = Color.White) }; Text(selectedAlbumName ?: "Album", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, "Refresh album", tint = Color.White) }
        }
        if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) AlbumGrid(albums) { album -> selectedAlbumId = album.id; selectedAlbumName = album.name }
        else {
            val unavailable = (tab == GalleryTab.PHOTOS && !access.images) || (tab == GalleryTab.VIDEOS && !access.videos)
            if (unavailable && selectedAlbumId == null) MissingMediaPermission(tab == GalleryTab.PHOTOS, requestPermission)
            else MediaGrid(media, selectionMode, selectedItems, { item -> selectionMode = true; selectedItems[item.uri.toString()] = item }) { item -> if (selectionMode) { val key = item.uri.toString(); if (selectedItems.containsKey(key)) selectedItems.remove(key) else selectedItems[key] = item; if (selectedItems.isEmpty()) selectionMode = false } else { selectedUri = item.uri; selectedIsVideo = item.isVideo } }
        }
        GalleryBottomNavigation(onCamera = onBack, onGallery = { selectedAlbumId = null; selectedAlbumName = null; tab = GalleryTab.PHOTOS }, gallerySelected = true)
    }
}

@Composable
private fun PartialMediaAccessBanner(requestPermission: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF202020)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Showing only media you selected", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = requestPermission) { Text("Change access") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GallerySelectionBar(selectedCount: Int, totalCount: Int, onCancel: () -> Unit, onSelectAll: () -> Unit, onClearSelection: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "Cancel selection", tint = Color.White) }
        Text("$selectedCount selected", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (selectedCount < totalCount) TextButton(onClick = onSelectAll) { Text("Select all", color = Color.White) } else TextButton(onClick = onClearSelection) { Text("Clear", color = Color.White) }
        IconButton(onClick = onShare, enabled = selectedCount > 0) { Icon(Icons.Default.Share, "Share selected", tint = Color.White) }
        IconButton(onClick = onDelete, enabled = selectedCount > 0) { Icon(Icons.Default.Delete, "Delete selected", tint = Color.White) }
    }
}

private fun queryMedia(context: Context, videosOnly: Boolean, bucketId: String? = null): List<MediaItem> {
    val collection = mediaCollection(videosOnly)
    val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
    val selection = bucketId?.let { "${MediaStore.MediaColumns.BUCKET_ID} = ?" }
    val args = bucketId?.let { arrayOf(it) }
    val result = ArrayList<MediaItem>()
    runCatching { context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID); val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME); val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED); val bucket = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID); val bucketName = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        while (cursor.moveToNext()) result += MediaItem(ContentUris.withAppendedId(collection, cursor.getLong(id)), cursor.getString(name) ?: "", cursor.getLong(date), videosOnly, if (bucket >= 0) cursor.getString(bucket) else null, if (bucketName >= 0) cursor.getString(bucketName) else null)
    } }
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
@Composable private fun MediaGrid(items: List<MediaItem>, selectionMode: Boolean, selectedItems: Map<String, MediaItem>, onLongClick: (MediaItem) -> Unit, onClick: (MediaItem) -> Unit) { if (items.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No media found", color = Color.LightGray) } else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 105.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { items(items, key = { it.uri.toString() }) { item -> MediaThumbnail(item.uri, item.name, item.isVideo, Modifier.aspectRatio(1f), selectedItems.containsKey(item.uri.toString()), selectionMode, { onLongClick(item) }, { onClick(item) }) } } }
@Composable private fun ColumnScope.AlbumGrid(albums: List<Album>, onClick: (Album) -> Unit) { if (albums.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No albums found", color = Color.LightGray) } else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 150.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(albums, key = { it.id }) { album -> Column(Modifier.fillMaxWidth().clickable { onClick(album) }) { MediaThumbnail(album.coverUri, album.name, album.coverIsVideo, Modifier.fillMaxWidth().aspectRatio(1f)); Spacer(Modifier.height(4.dp)); Text(album.name, color = Color.White, maxLines = 1); Text("${album.count} items", color = Color.LightGray, fontSize = 12.sp) } } } }

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun MediaThumbnail(uri: Uri, description: String, isVideo: Boolean, modifier: Modifier, selected: Boolean = false, selectionMode: Boolean = false, onLongClick: () -> Unit = {}, onClick: () -> Unit = {}) { val context = LocalContext.current; var bitmap by remember(uri) { mutableStateOf(ThumbnailMemoryCache.get(uri, isVideo)) }; LaunchedEffect(uri, isVideo) { if (bitmap == null) bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, uri, isVideo) } }; Box(modifier.background(if (selected) Color.White.copy(alpha = 0.32f) else Color.DarkGray).combinedClickable(onClick = onClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) { bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }; if (bitmap == null && isVideo) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp); if (isVideo) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("VIDEO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }; if (selected) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("✓", color = Color.Black, fontWeight = FontWeight.Bold) } else if (selectionMode) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)) } }

@Composable private fun MediaViewer(uri: Uri, isVideo: Boolean, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var confirmDelete by rememberSaveable(uri) { mutableStateOf(false) }
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
                    val context = LocalContext.current
                    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(uri) { bitmap = withContext(Dispatchers.IO) { loadFullImage(context, uri) } }
                    bitmap?.let {
                        Box(Modifier.fillMaxSize().pointerInput(uri) {
                            detectTransformGestures { _, _, zoom, _ -> photoZoom = (photoZoom * zoom).coerceIn(1f, 8f) }
                        }, contentAlignment = Alignment.Center) {
                            Image(it.asImageBitmap(), "Photo", Modifier.fillMaxSize().padding(8.dp).graphicsLayer(scaleX = photoZoom, scaleY = photoZoom), contentScale = ContentScale.Fit)
                        }
                    } ?: CircularProgressIndicator(color = Color.White)
                }
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete media?") }, text = { Text("Delete this ${if (isVideo) "video" else "photo"} from your device? This action cannot be undone.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
}

@Composable private fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    var state by remember(uri) { mutableStateOf(VideoLoadState.LOADING) }
    var retryKey by remember(uri) { mutableIntStateOf(0) }
    var speed by remember(uri) { mutableFloatStateOf(1f) }
    var speedMenuOpen by remember(uri) { mutableStateOf(false) }
    var activePlayer by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var videoZoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    val videoView = remember(uri) { VideoView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        isFocusable = true
        isFocusableInTouchMode = true
        pivotX = 0.5f
        pivotY = 0.5f
        setMediaController(MediaController(context).also { it.setAnchorView(this) })
        setOnTouchListener { _, event ->
            if (event.pointerCount >= 2 && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                // The ScaleGestureDetector below is attached after creation; this branch simply
                // allows the normal VideoView handling to continue for playback controls.
            }
            false
        }
    }}
    val scaleDetector = remember(videoView) {
        android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                videoZoom = (videoZoom * detector.scaleFactor).coerceIn(1f, 5f)
                videoView.scaleX = videoZoom
                videoView.scaleY = videoZoom
                return true
            }
        })
    }
    DisposableEffect(videoView, scaleDetector) {
        val listener: (ViewGroup, MotionEvent) -> Boolean = { _, event -> scaleDetector.onTouchEvent(event); false }
        videoView.setOnTouchListener { _, event -> scaleDetector.onTouchEvent(event); false }
        onDispose {
            videoView.setOnTouchListener(null)
            activePlayer = null
            runCatching { videoView.stopPlayback() }
        }
    }
    LaunchedEffect(uri, retryKey) {
        state = VideoLoadState.LOADING
        activePlayer = null
        videoView.scaleX = videoZoom
        videoView.scaleY = videoZoom
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { videoView })
        when (state) {
            VideoLoadState.LOADING -> CircularProgressIndicator(color = Color.White)
            VideoLoadState.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text("Could not play this video", color = Color.White, fontSize = 16.sp); Spacer(Modifier.height(10.dp)); Button(onClick = { retryKey++ }) { Icon(Icons.Default.Refresh, "Retry"); Spacer(Modifier.size(5.dp)); Text("Retry") } }
            VideoLoadState.READY, VideoLoadState.COMPLETED -> Unit
        }
        if (state == VideoLoadState.READY || state == VideoLoadState.COMPLETED) Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Button(onClick = { speedMenuOpen = true }) { Text("${speed}x") }
            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { selectedSpeed -> DropdownMenuItem(text = { Text("${selectedSpeed}x") }, onClick = { speed = selectedSpeed; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) activePlayer?.let { player -> runCatching { player.playbackParams = player.playbackParams.apply { this.speed = selectedSpeed } } }; speedMenuOpen = false }) } }
        }
    }
}
private enum class VideoLoadState { LOADING, READY, COMPLETED, ERROR }

private fun shareMedia(context: Context, uri: Uri) { shareMedia(context, listOf(uri)) }

private fun shareMedia(context: Context, uris: List<Uri>) { if (uris.isEmpty()) return; val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { type = context.contentResolver.getType(uris.first()) ?: "*/*"; putExtra(Intent.EXTRA_STREAM, uris.first()) } else Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; runCatching { context.startActivity(Intent.createChooser(intent, "Share media")) } }

private fun loadThumbnail(context: Context, uri: Uri, isVideo: Boolean): Bitmap? { ThumbnailMemoryCache.get(uri, isVideo)?.let { return it }; val bitmap = runCatching { if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(360, 360), null) else { val kind = if (isVideo) MediaStore.Video.Thumbnails.MINI_KIND else MediaStore.Images.Thumbnails.MINI_KIND; if (isVideo) MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(uri), kind, null) else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(uri), kind, null) } }.getOrNull(); if (bitmap != null) return ThumbnailMemoryCache.put(uri, isVideo, bitmap); if (!isVideo) return null; val fallback = runCatching { MediaMetadataRetriever().use { retriever -> retriever.setDataSource(context, uri); retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } }.getOrNull(); return fallback?.let { ThumbnailMemoryCache.put(uri, true, it) } }

private fun loadFullImage(context: Context, uri: Uri): Bitmap? { val maxDimension = 2048; return runCatching { val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, bounds) }; if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null; val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension); val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }; val bitmap = context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, options) } ?: return@runCatching null; val orientation = context.contentResolver.openInputStream(uri)?.use { input -> ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL; val matrix = Matrix(); when (orientation) { ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f); ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f); ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f); ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }; ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f); ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }; ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f); else -> return@runCatching bitmap }; Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() } }.getOrNull() }

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int { var sample = 1; while (width / (sample * 2) >= maxDimension && height / (sample * 2) >= maxDimension) sample *= 2; return sample }