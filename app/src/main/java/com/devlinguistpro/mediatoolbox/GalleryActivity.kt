package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class GalleryTab { PHOTOS, VIDEOS, ALBUMS }
private data class MediaItem(val uri: Uri, val name: String, val dateAdded: Long, val isVideo: Boolean, val bucketId: String?, val bucketName: String?)
private data class Album(val id: String, val name: String, val count: Int, val coverUri: Uri, val containsVideo: Boolean)

class GalleryActivity : ComponentActivity() {
    private var mediaPermissionGranted by mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        mediaPermissionGranted = if (Build.VERSION.SDK_INT >= 33) {
            results[Manifest.permission.READ_MEDIA_IMAGES] == true || results[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
    }
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPermissionGranted = hasMediaPermission()
        setContent { MaterialTheme { GalleryApp(mediaPermissionGranted, ::requestMediaPermission, ::finish, ::deleteMedia, ::openEditor) } }
    }

    override fun onResume() {
        super.onResume()
        val granted = hasMediaPermission()
        if (mediaPermissionGranted != granted) mediaPermissionGranted = granted
    }

    private fun hasMediaPermission(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
        else permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    private fun deleteMedia(uri: Uri) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val request = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else contentResolver.delete(uri, null, null)
        } catch (e: RecoverableSecurityException) {
            deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
        } catch (_: Exception) { Toast.makeText(this, "Could not delete item", Toast.LENGTH_SHORT).show() }
    }

    private fun openEditor(uri: Uri) {
        startActivity(Intent(this, GalleryEditorActivity::class.java).putExtra(GalleryEditorActivity.EXTRA_URI, uri))
    }
}

@Composable
private fun GalleryApp(hasPermission: Boolean, requestPermission: () -> Unit, onBack: () -> Unit, onDelete: (Uri) -> Unit, onEdit: (Uri) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedIsVideo by rememberSaveable { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    if (selectedUri != null) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.weight(1f)) {
                MediaViewer(selectedUri!!, selectedIsVideo, { selectedUri = null }, { shareMedia(context, selectedUri!!) }, if (selectedIsVideo) null else ({ onEdit(selectedUri!!) }), { onDelete(selectedUri!!); selectedUri = null; refreshToken++ })
            }
            GalleryBottomNavigation(onCamera = onBack, onGallery = { selectedUri = null }, gallerySelected = true)
        }
        return
    }

    if (!hasPermission) { GalleryPermissionScreen(requestPermission, onBack); return }

    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }

    LaunchedEffect(tab, selectedAlbumId, refreshToken) {
        if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) {
            albums = withContext(Dispatchers.IO) { queryAlbums(context) }
        } else {
            media = withContext(Dispatchers.IO) { queryMedia(context, tab == GalleryTab.VIDEOS, selectedAlbumId) }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (selectedAlbumId != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedAlbumId = null; selectedAlbumName = null; tab = GalleryTab.ALBUMS }) { Icon(Icons.Default.ArrowBack, "Back to albums", tint = Color.White) }
                Text(selectedAlbumName ?: "Album", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }

        if (selectedAlbumId == null) {
            GalleryTabs(tab) { tab = it; selectedAlbumId = null; selectedAlbumName = null }
        }

        if (tab == GalleryTab.ALBUMS && selectedAlbumId == null) {
            AlbumGrid(albums) { album ->
                selectedAlbumId = album.id
                selectedAlbumName = album.name
                tab = GalleryTab.PHOTOS
            }
        } else {
            MediaGrid(media) { item -> selectedUri = item.uri; selectedIsVideo = item.isVideo }
        }

        GalleryBottomNavigation(onCamera = onBack, onGallery = { selectedAlbumId = null; selectedAlbumName = null; tab = GalleryTab.PHOTOS }, gallerySelected = true)
    }
}

private fun queryMedia(context: Context, videosOnly: Boolean, bucketId: String? = null): List<MediaItem> {
    val collection = mediaCollection(videosOnly)
    val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
    val selection = bucketId?.let { "${MediaStore.MediaColumns.BUCKET_ID} = ?" }
    val args = bucketId?.let { arrayOf(it) }
    val result = ArrayList<MediaItem>()
    context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val bucket = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        while (cursor.moveToNext()) result += MediaItem(ContentUris.withAppendedId(collection, cursor.getLong(id)), cursor.getString(name) ?: "", cursor.getLong(date), videosOnly, if (bucket >= 0) cursor.getString(bucket) else null, if (bucketName >= 0) cursor.getString(bucketName) else null)
    }
    return result
}

private fun queryAlbums(context: Context): List<Album> {
    val grouped = LinkedHashMap<String, MutableList<MediaItem>>()
    (queryMedia(context, false) + queryMedia(context, true)).forEach { item -> item.bucketId?.let { grouped.getOrPut(it) { mutableListOf() }.add(item) } }
    return grouped.mapNotNull { (id, items) ->
        val cover = items.maxByOrNull { it.dateAdded } ?: return@mapNotNull null
        Album(id, cover.bucketName?.takeIf(String::isNotBlank) ?: "Unknown", items.size, cover.uri, items.any { it.isVideo })
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private fun mediaCollection(videosOnly: Boolean): Uri = if (Build.VERSION.SDK_INT >= 29) {
    if (videosOnly) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
} else if (videosOnly) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

@Composable
private fun GalleryTabs(tab: GalleryTab, onSelected: (GalleryTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        GalleryTab.values().forEach { value ->
            val label = value.name.lowercase().replaceFirstChar { it.uppercase() }
            Text(label, color = Color.White.copy(alpha = if (tab == value) 1f else 0.55f), fontWeight = if (tab == value) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable { onSelected(value) }.padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun GalleryBottomNavigation(onCamera: () -> Unit, onGallery: () -> Unit, gallerySelected: Boolean) {
    Row(Modifier.fillMaxWidth().background(Color.Black).height(58.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Text("CAMERA", color = Color.White.copy(alpha = if (!gallerySelected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (!gallerySelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable(onClick = onCamera).padding(horizontal = 20.dp, vertical = 10.dp))
        Text("GALLERY", color = Color.White.copy(alpha = if (gallerySelected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (gallerySelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable(onClick = onGallery).padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

@Composable
private fun GalleryPermissionScreen(requestPermission: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Allow photo and video access", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp)); Text("Media Toolbox uses Android's media library to show photos and videos already on your device. Your media is not uploaded.", color = Color.LightGray, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp)); Button(onClick = requestPermission) { Text("Allow access") }
        Spacer(Modifier.height(8.dp)); Button(onClick = onBack) { Text("Back to camera") }
    }
}

@Composable
private fun ColumnScope.MediaGrid(items: List<MediaItem>, onClick: (MediaItem) -> Unit) {
    if (items.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No media found", color = Color.LightGray) }
    else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 105.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(items, key = { it.uri.toString() }) { item -> MediaThumbnail(item.uri, item.name, item.isVideo, Modifier.aspectRatio(1f)) { onClick(item) } }
    }
}

@Composable
private fun ColumnScope.AlbumGrid(albums: List<Album>, onClick: (Album) -> Unit) {
    if (albums.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No albums found", color = Color.LightGray) }
    else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 150.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.id }) { album ->
            Column(Modifier.fillMaxWidth().clickable { onClick(album) }) {
                MediaThumbnail(album.coverUri, album.name, album.containsVideo, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(4.dp)); Text(album.name, color = Color.White, maxLines = 1); Text("${album.count} items", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MediaThumbnail(uri: Uri, description: String, isVideo: Boolean, modifier: Modifier, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) { bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, uri, isVideo) } }
    Box(modifier.background(Color.DarkGray).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (isVideo) {
            Box(Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("VIDEO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MediaViewer(uri: Uri, isVideo: Boolean, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.weight(1f)); IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                onEdit?.let { IconButton(onClick = it) { Icon(Icons.Default.Edit, "Edit", tint = Color.White) } }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isVideo) VideoPlayer(uri)
                else {
                    val context = LocalContext.current
                    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(uri) { bitmap = withContext(Dispatchers.IO) { loadFullImage(context, uri) } }
                    bitmap?.let { Image(it.asImageBitmap(), "Photo", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            VideoView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setVideoURI(uri)
                setOnPreparedListener { player -> player.isLooping = false; start() }
                setOnErrorListener { _, _, _ -> Toast.makeText(context, "Could not play video", Toast.LENGTH_SHORT).show(); true }
            }
        },
        update = { view -> if (view.tag != uri.toString()) { view.tag = uri.toString(); view.setVideoURI(uri); view.start() } }
    )
}

private fun shareMedia(context: Context, uri: Uri) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = context.contentResolver.getType(uri) ?: "*/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share media"))
}

private fun loadThumbnail(context: Context, uri: Uri, isVideo: Boolean): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        context.contentResolver.loadThumbnail(uri, Size(360, 360), null)
    } else {
        val kind = if (isVideo) MediaStore.Video.Thumbnails.MINI_KIND else MediaStore.Images.Thumbnails.MINI_KIND
        if (isVideo) MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(uri), kind, null)
        else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(uri), kind, null)
    }
}.getOrElse {
    runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
}

private fun loadFullImage(context: Context, uri: Uri): Bitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}.getOrNull()
