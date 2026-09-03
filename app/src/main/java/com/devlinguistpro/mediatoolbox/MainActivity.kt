package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
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
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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

private enum class CameraMode(val label: String) { PHOTO("CAMERA"), VIDEO("VIDEO"), SCAN("SCAN"), QR("QR") }

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var currentLens = CameraSelector.LENS_FACING_BACK
    private var previewView: PreviewView? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                MediaToolboxApp(
                    hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { view ->
                        previewView = view
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera()
                    },
                    onCapture = { capturePhoto() },
                    onFlip = { flipCamera() }
                )
            }
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
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                imageCapture = capture
                val selector = CameraSelector.Builder().requireLensFacing(currentLens).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture)
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        currentLens = if (currentLens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCamera()
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Media Toolbox")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        val output = ImageCapture.OutputFileOptions.Builder(contentResolver, uri, values).build()
        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            override fun onError(exception: ImageCaptureException) {
                contentResolver.delete(uri, null, null)
            }
        })
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun MediaToolboxApp(hasCameraPermission: Boolean, onRequestPermission: () -> Unit, onPreviewReady: (PreviewView) -> Unit, onCapture: () -> Unit, onFlip: () -> Unit) {
    var mode by remember { mutableStateOf(CameraMode.PHOTO) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (!hasCameraPermission) PermissionScreen(onRequestPermission)
        else CameraScreen(mode, { mode = it }, onPreviewReady, onCapture, onFlip)
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
            Surface(Modifier.clickable(onClick = onRequestPermission), shape = RoundedCornerShape(22.dp), color = Color.White) {
                Text("Allow camera", color = Color.Black, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CameraScreen(mode: CameraMode, onModeChanged: (CameraMode) -> Unit, onPreviewReady: (PreviewView) -> Unit, onCapture: () -> Unit, onFlip: () -> Unit) {
    val context = LocalContext.current
    val modes = CameraMode.entries
    val index = modes.indexOf(mode)
    val previous = modes.getOrNull(index - 1)
    val next = modes.getOrNull(index + 1)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.PERFORMANCE; onPreviewReady(this) } }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Spacer(Modifier.weight(1f))
            BottomCameraControls(mode, previous, next, onModeChanged, onCapture, onFlip)
            BottomNavigation()
        }
    }
}

@Composable
private fun BottomCameraControls(mode: CameraMode, previous: CameraMode?, next: CameraMode?, onModeChanged: (CameraMode) -> Unit, onCapture: () -> Unit, onFlip: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.82f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ControlButton({ Icon(Icons.Default.MoreVert, null, tint = Color.White) }, "MORE")
            ShutterButton(mode, onCapture)
            ControlButton({ Icon(Icons.Default.FlipCameraAndroid, null, tint = Color.White) }, "FLIP", onFlip)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (previous != null) ModeItem(previous, false) { onModeChanged(previous) }
            Spacer(Modifier.width(24.dp))
            ModeItem(mode, true) { }
            Spacer(Modifier.width(24.dp))
            if (next != null) ModeItem(next, false) { onModeChanged(next) }
        }
    }
}

@Composable
private fun ModeItem(mode: CameraMode, selected: Boolean, onClick: () -> Unit) {
    Text(mode.label, color = Color.White, fontSize = if (selected) 16.sp else 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.alpha(if (selected) 1f else 0.48f).clickable(onClick = onClick).padding(5.dp))
}

@Composable
private fun ControlButton(icon: @Composable () -> Unit, label: String, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ShutterButton(mode: CameraMode, onClick: () -> Unit) {
    val enabled = mode == CameraMode.PHOTO
    Box(Modifier.size(72.dp).background(Color.White.copy(alpha = if (enabled) 1f else 0.45f), CircleShape).clickable(enabled = enabled, onClick = onClick).padding(5.dp).background(Color.Black, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(58.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun BottomNavigation() {
    Row(Modifier.fillMaxWidth().background(Color.Black).height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("●", color = Color.White, fontSize = 14.sp); Text("CAMERA", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("▣", color = Color.White, fontSize = 14.sp); Text("GALLERY", color = Color.White, fontSize = 11.sp) }
    }
}
