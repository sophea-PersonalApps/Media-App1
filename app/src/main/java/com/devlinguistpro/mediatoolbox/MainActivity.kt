package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private enum class CameraMode(val label: String) {
    PHOTO("CAMERA"), VIDEO("VIDEO"), SCAN("SCAN"), QR("QR")
}

private enum class GalleryTab { PHOTOS, ALBUMS, VIDEOS }

private data class MediaItem(
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val isVideo: Boolean,
    val bucketId: String?,
    val bucketName: String?
)

private data class Album(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: Uri,
    val isVideo: Boolean
)

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val mediaExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var currentLens = CameraSelector.LENS_FACING_BACK
    private var previewView: PreviewView? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera()
    }

    private val galleryPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Gallery observes its permission state from the Activity and reloads itself.
    }

    private val deletePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                MediaToolboxApp(
                    hasCameraPermission = hasCameraPermission(),
                    hasGalleryPermission = hasGalleryPermission(),
                    onRequestCameraPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onRequestGalleryPermission = { requestGalleryPermissions() },
                    onPreviewReady = { view ->
                        previewView = view
                        if (hasCameraPermission()) bindCamera()
                    },
                    onCapture = { capturePhoto() },
                    onVideoToggle = { toggleVideoRecording() },
                    onFlip = { flipCamera() },
                    onDelete = { uri -> deleteMedia(uri) }
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasGalleryPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestGalleryPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            galleryPermission.launch(
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            )
        } else {
            galleryPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun bindCamera() {
        val view = previewView ?: return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HIGHEST,
                            androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)
                        )
                    )
                    .build()
                val video = VideoCapture.withOutput(recorder)
                imageCapture = capture
                videoCapture = video
                val selector = CameraSelector.Builder().requireLensFacing(currentLens).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture, video)
            } catch (_: Exception) {
                imageCapture = null
                videoCapture = null
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        if (activeRecording != null) return
        currentLens = if (currentLens == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCamera()
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Media Toolbox")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        val output = ImageCapture.OutputFileOptions.Builder(contentResolver, uri, values).build()
        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (Build.VERSION.SDK_INT >= 29) {
                    contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }, null, null)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                contentResolver.delete(uri, null, null)
            }
        })
    }

    private fun toggleVideoRecording() {
        val existing = activeRecording
        if (existing != null) {
            existing.stop()
            activeRecording = null
            return
        }

        val capture = videoCapture ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "VID_$timestamp.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Media Toolbox")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val mediaStoreOutput = androidx.camera.video.MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(values).build()

            activeRecording = capture.output
                .prepareRecording(this, mediaStoreOutput)
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        activeRecording = null
                        if (event.hasError()) contentResolver.delete(event.outputResults.outputUri, null, null)
                    }
                }
        } else {
            val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: return
            val file = File(dir, "VID_$timestamp.mp4")
            val output = FileOutputOptions.Builder(file).build()
            activeRecording = capture.output
                .prepareRecording(this, output)
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    if (event is VideoRecordEvent.Finalize) activeRecording = null
                }
        }
    }

    private fun queryMedia(context: Context, videosOnly: Boolean = false, bucketId: String? = null): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            if (videosOnly) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            if (videosOnly) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )
        val selection = if (bucketId != null) "${MediaStore.MediaColumns.BUCKET_ID} = ?" else null
        val args = if (bucketId != null) arrayOf(bucketId) else null
        resolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val bucketIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collection, id)
                result += MediaItem(
                    uri = uri,
                    name = cursor.getString(nameIndex) ?: "",
                    dateAdded = cursor.getLong(dateIndex),
                    isVideo = videosOnly,
                    bucketId = if (bucketIndex >= 0) cursor.getString(bucketIndex) else null,
                    bucketName = if (bucketNameIndex >= 0) cursor.getString(bucketNameIndex) else null
                )
            }
        }
        return result
    }

    private fun queryAlbums(context: Context): List<Album> {
        val all = mutableListOf<Album>()
        val photos = queryMedia(context, videosOnly = false)
        val videos = queryMedia(context, videosOnly = true)
        fun group(items: List<MediaItem>, isVideo: Boolean) {
            items.filter { !it.bucketId.isNullOrBlank() }.groupBy { it.bucketId!! }.forEach { (id, group) ->
                val cover = group.first().uri
                val name = group.first().bucketName?.takeIf { it.isNotBlank() } ?: "Unknown"
                all += Album(id, name, group.size, cover, isVideo)
            }
        }
        group(photos, false)
        group(videos, true)
        return all.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun deleteMedia(uri: Uri) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val request = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                deletePermissionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                contentResolver.delete(uri, null, null)
            }
        } catch (_: RecoverableSecurityException) {
            // Older Android versions may require user confirmation; the direct delete is retried by the UI.
        }
    }

    override fun onStop() {
        activeRecording?.stop()
        activeRecording = null
        super.onStop()
    }

    override fun onDestroy() {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        mediaExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun MediaToolboxApp(
    hasCameraPermission: Boolean,
    hasGalleryPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onRequestGalleryPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlip: () -> Unit,
    onDelete: (Uri) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(CameraMode.PHOTO) }
    var galleryOpen by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (galleryOpen) {
            GalleryScreen(
                hasPermission = hasGalleryPermission,
                onRequestPermission = onRequestGalleryPermission,
                onBackToCamera = { galleryOpen = false },
                onDelete = onDelete
            )
        } else if (!hasCameraPermission) {
            PermissionScreen(onRequestCameraPermission)
        } else {
            CameraScreen(
                mode = mode,
                onModeChanged = { mode = it },
                onPreviewReady = onPreviewReady,
                onCapture = onCapture,
                onVideoToggle = onVideoToggle,
                onFlip = onFlip,
                onOpenGallery = { galleryOpen = true }
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Camera access is needed", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text("Media Toolbox needs camera access to take photos and use its camera modes.", color = Color.LightGray, fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRequestPermission) { Text("Allow camera") }
        }
    }
}

@Composable
private fun CameraScreen(
    mode: CameraMode,
    onModeChanged: (CameraMode) -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlip: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val modes = CameraMode.entries
    val index = modes.indexOf(mode)
    val previous = modes.getOrNull(index - 1)
    val next = modes.getOrNull(index + 1)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    onPreviewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Spacer(Modifier.weight(1f))
            BottomCameraControls(
                mode, previous, next, onModeChanged, onCapture, onVideoToggle, onFlip, onOpenGallery
            )
            BottomNavigation(selectedGallery = false, onCamera = {}, onGallery = onOpenGallery)
        }
    }
}

@Composable
private fun BottomCameraControls(
    mode: CameraMode,
    previous: CameraMode?,
    next: CameraMode?,
    onModeChanged: (CameraMode) -> Unit,
    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlip: () -> Unit,
    onOpenGallery: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.82f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton({ Icon(Icons.Default.MoreVert, null, tint = Color.White) }, "MORE")
            ShutterButton(mode, onCapture, onVideoToggle)
            ControlButton({ Icon(Icons.Default.FlipCameraAndroid, null, tint = Color.White) }, "FLIP", onFlip)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (previous != null) ModeItem(previous, false) { onModeChanged(previous) }
            Spacer(Modifier.width(24.dp))
            ModeItem(mode, true) { }
            Spacer(Modifier.width(24.dp))
            if (next != null) ModeItem(next, false) { onModeChanged(next) }
        }
        Spacer(Modifier.height(6.dp))
        if (mode == CameraMode.PHOTO || mode == CameraMode.VIDEO) {
            Text("Swipe or tap a mode", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun ModeItem(mode: CameraMode, selected: Boolean, onClick: () -> Unit) {
    Text(
        mode.label,
        color = Color.White,
        fontSize = if (selected) 16.sp else 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.alpha(if (selected) 1f else 0.48f).clickable(onClick = onClick).padding(5.dp)
    )
}

@Composable
private fun ControlButton(icon: @Composable () -> Unit, label: String, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ShutterButton(mode: CameraMode, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {
    val enabled = mode == CameraMode.PHOTO || mode == CameraMode.VIDEO
    Box(
        Modifier.size(72.dp)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.45f), CircleShape)
            .clickable(enabled = enabled, onClick = if (mode == CameraMode.VIDEO) onVideoToggle else onPhoto)
            .padding(5.dp)
            .background(Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp)
                .background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape)
        )
    }
}

@Composable
private fun BottomNavigation(selectedGallery: Boolean, onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).height(58.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationItem(Icons.Default.PhotoCamera, "CAMERA", !selectedGallery, onCamera)
        NavigationItem(Icons.Default.VideoLibrary, "GALLERY", selectedGallery, onGallery)
    }
}

@Composable
private fun NavigationItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (selected) 1f else 0.55f), modifier = Modifier.size(20.dp))
        Text(label, color = Color.White.copy(alpha = if (selected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun GalleryScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onBackToCamera: () -> Unit,
    onDelete: (Uri) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedIsVideo by rememberSaveable { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }

    LaunchedEffect(hasPermission, tab, selectedAlbumId, refreshKey) {
        if (!hasPermission) return@LaunchedEffect
        mediaItems = withContextCompat(mediaExecutorFor(context)) {
            if (tab == GalleryTab.VIDEOS) {
                queryMedia(context, videosOnly = true)
            } else {
                queryMedia(context, videosOnly = false, bucketId = selectedAlbumId)
            }
        }
        if (tab == GalleryTab.ALBUMS) {
            albums = withContextCompat(mediaExecutorFor(context)) { queryAlbums(context) }
        }
    }

    if (selectedUri != null) {
        MediaViewer(
            uri = selectedUri!!,
            isVideo = selectedIsVideo,
            onBack = { selectedUri = null },
            onShare = { shareMedia(context, selectedUri!!) },
            onDelete = {
                onDelete(selectedUri!!)
                selectedUri = null
                refreshKey++
            }
        )
        return
    }

    if (!hasPermission) {
        GalleryPermissionScreen(onRequestPermission, onBackToCamera)
        return
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        GalleryHeader(onBackToCamera)
        if (tab == GalleryTab.ALBUMS) {
            AlbumGrid(albums) { album ->
                selectedAlbumId = album.id
                tab = if (album.isVideo) GalleryTab.VIDEOS else GalleryTab.PHOTOS
            }
        } else {
            MediaGrid(mediaItems) { item ->
                selectedUri = item.uri
                selectedIsVideo = item.isVideo
            }
        }
        GalleryTabs(tab, onTabSelected = {
            tab = it
            selectedAlbumId = null
        })
    }
}

@Composable
private fun GalleryPermissionScreen(onRequestPermission: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Allow photo and video access", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "This lets Media Toolbox show the photos and videos already stored on your device. It does not upload them.",
            color = Color.LightGray,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestPermission) { Text("Allow access") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack) { Text("Back to camera") }
    }
}

@Composable
private fun GalleryHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
        Text("Gallery", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
    }
}

@Composable
private fun GalleryTabs(tab: GalleryTab, onTabSelected: (GalleryTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GalleryTabItem("Photos", tab == GalleryTab.PHOTOS) { onTabSelected(GalleryTab.PHOTOS) }
        GalleryTabItem("Albums", tab == GalleryTab.ALBUMS) { onTabSelected(GalleryTab.ALBUMS) }
        GalleryTabItem("Videos", tab == GalleryTab.VIDEOS) { onTabSelected(GalleryTab.VIDEOS) }
    }
}

@Composable
private fun GalleryTabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White.copy(alpha = if (selected) 1f else 0.55f),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun MediaGrid(items: List<MediaItem>, onItemClick: (MediaItem) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No media found", color = Color.LightGray)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 105.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            MediaThumbnail(item.uri, item.name, Modifier.aspectRatio(1f)) { onItemClick(item) }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (Album) -> Unit) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums found", color = Color.LightGray)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { "${it.id}:${it.isVideo}" }) { album ->
            Column(Modifier.clickable { onAlbumClick(album) }) {
                MediaThumbnail(album.coverUri, album.name, Modifier.fillMaxWidth().aspectRatio(1f)) {}
                Spacer(Modifier.height(4.dp))
                Text(album.name, color = Color.White, maxLines = 1)
                Text("${album.count} items", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MediaThumbnail(uri: Uri, contentDescription: String, modifier: Modifier, onClick: () -> Unit) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(uri) {
        bitmap = withContextCompat(Executors.newSingleThreadExecutor()) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(uri, android.util.Size(400, 400), null)
                } else {
                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier.background(Color.DarkGray).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun MediaViewer(
    uri: Uri,
    isVideo: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(uri) {
            detectHorizontalDragGestures { _, dragAmount ->
                if (dragAmount > 120) onBack()
            }
        }
    ) {
        if (isVideo) {
            AndroidView(
                factory = {
                    android.widget.VideoView(it).apply {
                        setVideoURI(uri)
                        setOnPreparedListener { player -> player.isLooping = true; start() }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(uri) {
                bitmap = withContextCompat(Executors.newSingleThreadExecutor()) {
                    try {
                        if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, android.util.Size(1600, 1600), null)
                        else context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    } catch (_: Exception) { null }
                }
            }
            bitmap?.let {
                androidx.compose.foundation.Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Selected media",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.55f)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Row {
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(onClick = { showDeleteConfirmation = true }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
                IconButton(onClick = {}) { Icon(Icons.Default.FavoriteBorder, "Favourite", tint = Color.White) }
                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this item?") },
            text = { Text("The item will be removed from your media library.") },
            confirmButton = {
                Button(onClick = { showDeleteConfirmation = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

private fun shareMedia(context: Context, uri: Uri) {
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share media"))
}

private fun mediaExecutorFor(context: Context): ExecutorService = Executors.newSingleThreadExecutor()

private fun <T> withContextCompat(executor: ExecutorService, block: () -> T): T {
    return try {
        val future = executor.submit<T> { block() }
        future.get()
    } finally {
        executor.shutdown()
    }
}
