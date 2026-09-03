package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private enum class CameraMode(val label: String) {
    PHOTO("CAMERA"), VIDEO("VIDEO"), SCAN("SCAN"), QR("QR")
}

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var currentLens = CameraSelector.LENS_FACING_BACK
    private var previewView: PreviewView? = null
    private var cameraPermissionGranted = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        if (granted) bindCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraPermissionGranted = hasCameraPermission()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                MediaToolboxApp(
                    hasCameraPermission = cameraPermissionGranted,
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { view -> previewView = view; if (cameraPermissionGranted) bindCamera() },
                    onCapture = ::capturePhoto,
                    onVideoToggle = ::toggleVideoRecording,
                    onFlip = ::flipCamera,
                    onOpenGallery = { startActivity(Intent(this, GalleryActivity::class.java)) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            cameraPermissionGranted = true
            if (previewView != null) bindCamera()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        val view = previewView ?: return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val recorder = Recorder.Builder().setQualitySelector(
                    QualitySelector.from(Quality.HIGHEST, androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(Quality.FHD))
                ).build()
                val video = VideoCapture.withOutput(recorder)
                val selector = CameraSelector.Builder().requireLensFacing(currentLens).build()
                provider.unbindAll()
                imageCapture = capture
                videoCapture = video
                provider.bindToLifecycle(this, selector, preview, capture, video)
            } catch (_: Exception) {
                imageCapture = null
                videoCapture = null
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        if (activeRecording != null) return
        currentLens = if (currentLens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCamera()
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Media Toolbox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        val output = ImageCapture.OutputFileOptions.Builder(contentResolver, uri, values).build()
        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (Build.VERSION.SDK_INT >= 29) contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            override fun onError(exception: ImageCaptureException) { contentResolver.delete(uri, null, null) }
        })
    }

    private fun toggleVideoRecording() {
        activeRecording?.let { it.stop(); activeRecording = null; return }
        val capture = videoCapture ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "VID_$timestamp.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Media Toolbox")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
            activeRecording = capture.output.prepareRecording(this, output).start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    if (event.hasError()) contentResolver.delete(event.outputResults.outputUri, null, null)
                    else contentResolver.update(event.outputResults.outputUri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                }
            }
        } else {
            val directory = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: return
            val file = java.io.File(directory, "VID_$timestamp.mp4")
            val output = androidx.camera.video.FileOutputOptions.Builder(file).build()
            activeRecording = capture.output.prepareRecording(this, output).start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) activeRecording = null
            }
        }
    }

    override fun onStop() { activeRecording?.stop(); activeRecording = null; super.onStop() }

    override fun onDestroy() {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun MediaToolboxApp(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlip: () -> Unit,
    onOpenGallery: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(CameraMode.PHOTO) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (!hasCameraPermission) PermissionScreen(onRequestPermission)
        else CameraScreen(mode, { mode = it }, onPreviewReady, onCapture, onVideoToggle, onFlip, onOpenGallery)
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
    val selectedIndex = modes.indexOf(mode)
    val previous = modes.getOrNull(selectedIndex - 1)
    val next = modes.getOrNull(selectedIndex + 1)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.PERFORMANCE; onPreviewReady(this) } },
            modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Spacer(Modifier.weight(1f))
            CameraControls(mode, previous, next, onModeChanged, onCapture, onVideoToggle, onFlip, onOpenGallery)
            BottomNavigation({}, onOpenGallery)
        }
    }
}

@Composable
private fun CameraControls(
    mode: CameraMode,
    previous: CameraMode?,
    next: CameraMode?,
    onModeChanged: (CameraMode) -> Unit,
    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlip: () -> Unit,
    onOpenGallery: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.82f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ControlButton({ Icon(Icons.Default.MoreVert, "More", tint = Color.White) }, "MORE")
            ShutterButton(mode, onCapture, onVideoToggle)
            ControlButton({ Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White) }, "FLIP", onFlip)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (previous != null) ModeItem(previous, false) { onModeChanged(previous) }
            Spacer(Modifier.width(24.dp))
            ModeItem(mode, true) { }
            Spacer(Modifier.width(24.dp))
            if (next != null) ModeItem(next, false) { onModeChanged(next) }
        }
        ModeSwipeArea(mode, onModeChanged)
    }
}

@Composable
private fun ModeSwipeArea(mode: CameraMode, onModeChanged: (CameraMode) -> Unit) {
    val modes = CameraMode.entries
    val index = modes.indexOf(mode)
    Box(Modifier.fillMaxWidth().height(1.dp).pointerInput(mode) {
        var drag = 0f
        detectHorizontalDragGestures(onHorizontalDrag = { _, amount -> drag += amount }, onDragEnd = {
            when {
                drag < -80f && index < modes.lastIndex -> onModeChanged(modes[index + 1])
                drag > 80f && index > 0 -> onModeChanged(modes[index - 1])
            }
            drag = 0f
        })
    })
}

@Composable
private fun ModeItem(mode: CameraMode, selected: Boolean, onClick: () -> Unit) {
    Text(mode.label, color = Color.White, fontSize = if (selected) 16.sp else 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.alpha(if (selected) 1f else 0.48f).clickable(onClick = onClick).padding(5.dp))
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
    val context = LocalContext.current
    val enabled = mode == CameraMode.PHOTO || mode == CameraMode.VIDEO || mode == CameraMode.SCAN
    val action = when (mode) {
        CameraMode.PHOTO -> onPhoto
        CameraMode.VIDEO -> onVideoToggle
        CameraMode.SCAN -> ({ context.startActivity(Intent(context, ScannerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        CameraMode.QR -> ({})
    }
    Box(Modifier.size(72.dp).background(Color.White.copy(alpha = if (enabled) 1f else 0.45f), CircleShape).clickable(enabled = enabled, onClick = action).padding(5.dp).background(Color.Black, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape))
    }
}

@Composable
private fun BottomNavigation(onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.Black).height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        NavigationItem("CAMERA", true, onCamera)
        NavigationItem("GALLERY", false, onGallery)
    }
}

@Composable
private fun NavigationItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp)) {
        Text(label, color = Color.White.copy(alpha = if (selected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
