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
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
private data class MediaAccess(val images: Boolean, val videos: Boolean) { val any: Boolean get() = images || videos }
private data class MediaItem(val uri: Uri, val name: String, val dateAdded: Long, val isVideo: Boolean, val bucketId: String?, val bucketName: String?)
private data class Album(val id: String, val name: String, val count: Int, val coverUri: Uri, val coverIsVideo: Boolean)

private object ThumbnailMemoryCache {
    private const val MAX_CACHE_BYTES = 32 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    fun get(uri: Uri, isVideo: Boolean): Bitmap? = cache.get("$isVideo|$uri")
    fun put(uri: Uri, isVideo: Boolean, bitmap: Bitmap): Bitmap {
        cache.put("$isVideo|$uri", bitmap)
        return bitmap
    }
}

class GalleryActivity : ComponentActivity() {
    private var mediaAccess by mutableStateOf(MediaAccess(false, false))
    private var pendingDeleteResult: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        mediaAccess = currentMediaAccess()
    }
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val callback = pendingDeleteResult
        pendingDeleteResult = null
        callback?.invoke(result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaAccess = currentMediaAccess()
        setContent {
            MaterialTheme {
                GalleryApp(
                    access = mediaAccess,
                    requestPermission = ::requestMediaPermission,
                    onBack = ::finish,
                    onDelete = ::deleteMedia,
                    onDeleteBatch = ::deleteMediaBatch,
                    onEdit = ::openEditor
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val access = currentMediaAccess()
        if (mediaAccess != access) mediaAccess = access
    }

    private fun currentMediaAccess(): MediaAccess {
        if (Build.VERSION.SDK_INT <= 32) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            return MediaAccess(granted, granted)
        }
        val images = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val videos = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED) {
            return MediaAccess(true, true)
        }
        return MediaAccess(images, videos)
    }

    private fun requestMediaPermission() {
        when {
            Build.VERSION.SDK_INT >= 34 -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
            Build.VERSION.SDK_INT >= 33 -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
            else -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun deleteMedia(uri: Uri, onResult: (Boolean) -> Unit) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                pendingDeleteResult = onResult
                val request = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                onResult(contentResolver.delete(uri, null, null) > 0)
            }
        } catch (e: RecoverableSecurityException) {
            pendingDeleteResult = onResult
            deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
        } catch (_: Exception) {
            onResult(false)
            Toast.makeText(this, "Could not delete item", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteMediaBatch(uris: List<Uri>, onResult: (Boolean) -> Unit) {
        if (uris.isEmpty()) {
            onResult(true)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                pendingDeleteResult = onResult
                val request = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                onResult(uris.all { contentResolver.delete(it, null, null) > 0 })
            }
        } catch (_: Exception) {
            onResult(false)
            Toast.makeText(this, "Could not delete selected items", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openEditor(uri: Uri) {
        startActivity(Intent(this, GalleryEditorActivity::class.java).putExtra(GalleryEditorActivity.EXTRA_URI, uri))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryApp(
    access: MediaAccess,
    requestPermission: () -> Unit,
    onBack: () -> Unit,
    onDelete: (Uri, (Boolean) -> Unit) -> Unit,
    onDeleteBatch: (List<Uri>, (Boolean) -> Unit) -> Unit,
    onEdit: (Uri) -> Unit
) {
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

    if (selectedUri != null) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.weight(1f)) {
                MediaViewer(
                    uri = selectedUri!!,
                    isVideo = selectedIsVideo,
                    onBack = { selectedUri = null },
                    onShare = { shareMedia(context, selectedUri!!) },
                    onEdit = if (selectedIsVideo) null else ({ onEdit(selectedUri!!) }),
                    onDelete = {
                        val uriBeingDeleted = selectedUri!!
                        onDelete(uriBeingDeleted) { deleted ->
                            if (deleted) {
                                selectedUri = null
                                refreshToken++
                            }
                        }
                    }
                )
            }
            GalleryBottomNavigation(onCamera = onBack, onGallery = { selectedUri = null }, gallerySelected = true)
        }
        return
    }

    if (!access.any) {
        GalleryPermissionScreen(requestPermission, onBack)
        return
    }

    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(tab, selectedAlbumId, refreshToken, access) {
        loading = true
        if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) {
            albums = withContext(Dispatchers.IO) { queryAlbums(context, access) }
        } else if (selectedAlbumId != null) {
            media = withContext(Dispatchers.IO) { queryAlbumMedia(context, selectedAlbumId!!, access) }
        } else {
            media = withContext(Dispatchers.IO) {
                when (tab) {
                    GalleryTab.PHOTOS -> if (access.images) queryMedia(context, false) else emptyList()
                    GalleryTab.VIDEOS -> if (access.videos) queryMedia(context, true) else emptyList()
                    GalleryTab.ALBUMS -> emptyList()
                }
            }
        }
        loading = false
    }

    LaunchedEffect(tab, selectedAlbumId) {
        selectionMode = false
        selectedItems.clear()
        showDeleteConfirmation = false
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete selected media?") },
            text = { Text("Delete ${selectedItems.size} selected item${if (selectedItems.size == 1) "" else "s"} from your device? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    val uris = selectedItems.values.map { it.uri }
                    onDeleteBatch(uris) { deleted ->
                        if (deleted) {
                            selectionMode = false
                            selectedItems.clear()
                            refreshToken++
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (selectionMode) {
            GallerySelectionBar(
                selectedCount = selectedItems.size,
                totalCount = media.size,
                onCancel = { selectionMode = false; selectedItems.clear() },
                onSelectAll = { media.forEach { selectedItems[it.uri.toString()] = it } },
                onClearSelection = { selectedItems.clear() },
                onShare = { shareMedia(context, selectedItems.values.map { it.uri }) },
                onDelete = { showDeleteConfirmation = true }
            )
        } else if (selectedAlbumId == null) {
            GalleryTabs(tab, access) { tab = it; selectedAlbumId = null; selectedAlbumName = null }
        }

        if (selectedAlbumId != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedAlbumId = null; selectedAlbumName = null; tab = GalleryTab.ALBUMS }) {
                    Icon(Icons.Default.ArrowBack, "Back to albums", tint = Color.White)
                }
                Text(selectedAlbumName ?: "Album", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { refreshToken++ }) {
                    Icon(Icons.Default.Refresh, "Refresh album", tint = Color.White)
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) {
                AlbumGrid(albums) { album -> selectedAlbumId = album.id; selectedAlbumName = album.name }
            } else {
                val unavailable = (tab == GalleryTab.PHOTOS && !access.images) || (tab == GalleryTab.VIDEOS && !access.videos)
                if (unavailable && selectedAlbumId == null) {
                    MissingMediaPermission(tab == GalleryTab.PHOTOS, requestPermission)
                } else if (media.isEmpty()) {
                    EmptyGalleryMessage(if (selectedAlbumId != null) "This album is empty" else "No media found")
                } else {
                    MediaGrid(
                        media = media,
                        selectionMode = selectionMode,
                        selectedItems = selectedItems,
                        onLongPress = { item -> selectionMode = true; selectedItems[item.uri.toString()] = item },
                        onClick = { item ->
                            if (selectionMode) {
                                val key = item.uri.toString()
                                if (selectedItems.containsKey(key)) selectedItems.remove(key) else selectedItems[key] = item
                                if (selectedItems.isEmpty()) selectionMode = false
                            } else {
                                selectedUri = item.uri
                                selectedIsVideo = item.isVideo
                            }
                        }
                    )
                }
            }
        }

        GalleryBottomNavigation(
            onCamera = onBack,
            onGallery = { selectedAlbumId = null; selectedAlbumName = null; tab = GalleryTab.PHOTOS },
            gallerySelected = true
        )
    }
}

@Composable
private fun EmptyGalleryMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color.LightGray, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GallerySelectionBar(
    selectedCount: Int,
    totalCount: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "Cancel selection", tint = Color.White) }
        Text("$selectedCount selected", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (selectedCount < totalCount) TextButton(onClick = onSelectAll) { Text("Select all", color = Color.White) }
        else TextButton(onClick = onClearSelection) { Text("Clear", color = Color.White) }
        IconButton(onClick = onShare, enabled = selectedCount > 0) { Icon(Icons.Default.Share, "Share selected", tint = Color.White) }
        IconButton(onClick = onDelete, enabled = selectedCount > 0) { Icon(Icons.Default.Delete, "Delete selected", tint = Color.White) }
    }
}

private fun queryMedia(context: Context, videosOnly: Boolean, bucketId: String? = null): List<MediaItem> {
    val collection = mediaCollection(videosOnly)
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.BUCKET_ID,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
    )
    val selection = bucketId?.let { "${MediaStore.MediaColumns.BUCKET_ID} = ?" }
    val args = bucketId?.let { arrayOf(it) }
    val result = ArrayList<MediaItem>()
    runCatching {
        context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val bucket = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val bucketName = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                result += MediaItem(
                    ContentUris.withAppendedId(collection, cursor.getLong(id)),
                    cursor.getString(name) ?: "",
                    cursor.getLong(date),
                    videosOnly,
                    if (bucket >= 0) cursor.getString(bucket) else null,
                    if (bucketName >= 0) cursor.getString(bucketName) else null
                )
            }
        }
    }
    return result
}

private fun queryAlbumMedia(context: Context, bucketId: String, access: MediaAccess): List<MediaItem> {
    val items = ArrayList<MediaItem>()
    if (access.images) items += queryMedia(context, false, bucketId)
    if (access.videos) items += queryMedia(context, true, bucketId)
    return items.sortedByDescending { it.dateAdded }
}

private fun queryAlbums(context: Context, access: MediaAccess): List<Album> {
    val grouped = LinkedHashMap<String, MutableList<MediaItem>>()
    val allItems = ArrayList<MediaItem>()
    if (access.images) allItems += queryMedia(context, false)
    if (access.videos) allItems += queryMedia(context, true)
    allItems.forEach { item -> item.bucketId?.let { grouped.getOrPut(it) { mutableListOf() }.add(item) } }
    return grouped.mapNotNull { (id, items) ->
        val cover = items.maxByOrNull { it.dateAdded } ?: return@mapNotNull null
        Album(id, cover.bucketName?.takeIf(String::isNotBlank) ?: "Unknown", items.size, cover.uri, cover.isVideo)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private fun mediaCollection(videosOnly: Boolean): Uri = if (Build.VERSION.SDK_INT >= 29) {
    if (videosOnly) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
} else if (videosOnly) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

@Composable
private fun GalleryTabs(tab: GalleryTab, access: MediaAccess, onSelected: (GalleryTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GalleryTab.values().forEach { value ->
            val label = value.name.lowercase().replaceFirstChar { it.uppercase() }
            val available = when (value) {
                GalleryTab.PHOTOS -> access.images
                GalleryTab.VIDEOS -> access.videos
                GalleryTab.ALBUMS -> access.any
            }
            Text(
                label,
                color = Color.White.copy(alpha = if (tab == value) 1f else if (available) 0.55f else 0.3f),
                fontWeight = if (tab == value) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onSelected(value) }.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Column(Modifier.fillMaxWidth().clickable { onAlbumClick(album) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.DarkGray)) {
                    AsyncMediaThumbnail(album.coverUri, album.coverIsVideo, Size(500, 500), Modifier.fillMaxSize())
                    if (album.coverIsVideo) VideoBadge(Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.55f)).padding(8.dp)) {
                        Column {
                            Text(album.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${album.count} item${if (album.count == 1) "" else "s"}", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    media: List<MediaItem>,
    selectionMode: Boolean,
    selectedItems: Map<String, MediaItem>,
    onLongPress: (MediaItem) -> Unit,
    onClick: (MediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(media, key = { it.uri.toString() }) { item ->
            val selected = selectedItems.containsKey(item.uri.toString())
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongPress(item) })
            ) {
                AsyncMediaThumbnail(item.uri, item.isVideo, Size(400, 400), Modifier.fillMaxSize())
                if (item.isVideo) VideoBadge(Modifier.align(Alignment.BottomEnd).padding(5.dp))
                if (selectionMode) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(25.dp)
                            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                if (selected) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f)))
            }
        }
    }
}

@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(25.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
        Text("▶", color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun AsyncMediaThumbnail(uri: Uri, isVideo: Boolean, targetSize: Size, modifier: Modifier) {
    var bitmap by remember(uri, isVideo, targetSize) { mutableStateOf<Bitmap?>(ThumbnailMemoryCache.get(uri, isVideo)) }
    LaunchedEffect(uri, isVideo, targetSize) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { loadThumbnail(LocalContext.current, uri, isVideo, targetSize) }
        }
    }
    Box(modifier.background(Color.DarkGray), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

private fun loadThumbnail(context: Context, uri: Uri, isVideo: Boolean, targetSize: Size): Bitmap? {
    ThumbnailMemoryCache.get(uri, isVideo)?.let { return it }
    val bitmap = runCatching {
        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                retriever.release()
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }.getOrNull() ?: return null
    return ThumbnailMemoryCache.put(uri, isVideo, bitmap)
}

@Composable
private fun MediaViewer(
    uri: Uri,
    isVideo: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
            if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = Color.White) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (isVideo) VideoViewer(uri) else FullImage(uri)
        }
    }
}

@Composable
private fun FullImage(uri: Uri) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { loadOrientedBitmap(context, uri) }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
    } else {
        CircularProgressIndicator()
    }
}

private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    val original = runCatching { context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull() ?: return null
    val orientation = runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { ExifInterface(it.fileDescriptor).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return original
    val matrix = Matrix().apply { postRotate(degrees) }
    return runCatching { Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true) }.getOrElse { original }
}

@Composable
private fun VideoViewer(uri: Uri) {
    var speed by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var menuOpen by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = {
                    VideoView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setMediaController(MediaController(context))
                        setVideoURI(uri)
                        setOnPreparedListener { mp -> player = mp; mp.playbackParams = mp.playbackParams.apply { this.speed = speed }; start() }
                        setOnCompletionListener { player = null }
                        setOnErrorListener { _, _, _ -> Toast.makeText(context, "Could not play this video", Toast.LENGTH_SHORT).show(); true }
                    }
                },
                update = { view ->
                    player?.let { it.playbackParams = it.playbackParams.apply { this.speed = speed } }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            Box {
                Button(onClick = { menuOpen = true }) { Text("${speed}×") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf(0.5f, 1f, 1.5f, 2f).forEach { value ->
                        DropdownMenuItem(text = { Text("${value}×") }, onClick = { speed = value; menuOpen = false })
                    }
                }
            }
        }
    }
}

private fun shareMedia(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share media")) }
}

private fun shareMedia(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    if (uris.size == 1) {
        shareMedia(context, uris.first())
        return
    }
    val types = uris.mapNotNull { context.contentResolver.getType(it) }.distinct()
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (types.size == 1) types.first() else "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share selected media")) }
}

@Composable
private fun GalleryPermissionScreen(requestPermission: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text("Media access is needed", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("Allow access so Media Toolbox can show your photos and videos.", color = Color.LightGray, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(20.dp))
        Button(onClick = requestPermission) { Text("Allow media") }
        TextButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MissingMediaPermission(photos: Boolean, requestPermission: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (photos) "Photo access is not enabled" else "Video access is not enabled", color = Color.White, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = requestPermission) { Text("Allow access") }
    }
}

@Composable
private fun GalleryBottomNavigation(onCamera: () -> Unit, onGallery: () -> Unit, gallerySelected: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).height(58.dp).navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationItem("CAMERA", !gallerySelected, onCamera)
        NavigationItem("GALLERY", gallerySelected, onGallery)
    }
}

@Composable
private fun NavigationItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp)) {
        Text(label, color = Color.White.copy(alpha = if (selected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
