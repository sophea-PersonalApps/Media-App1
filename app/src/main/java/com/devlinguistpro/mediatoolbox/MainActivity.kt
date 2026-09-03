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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private enum class CameraMode(val label: String) {
    PHOTO("CAMERA"), VIDEO("VIDEO"), SCAN("SCAN"), QR("QR")
}

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var currentLens = CameraSelector.LENS_FACING_BACK
    private var previewView: PreviewView? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) bindCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                MediaToolboxApp(
                    hasCameraPermission = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED,
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { view ->
                        previewView = view
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            bindCamera()
                        }
                    },
                    onCapture = { capturePhoto() },
                    onFlip = { flipCamera() }
                )
            }
        }
    }

    private fun bindCamera() {
        val view = previewView ?: return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = view.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture
                val selector = CameraSelector.Builder()
                    .requireLensFacing(currentLens)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture)
            } catch (_: Exception) {
                // Camera unavailable: the UI remains usable and can request a retry later.
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        currentLens = if (currentLens == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCamera()
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        val name = "IMG_${
SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        }"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Media Toolbox")
        }
        val output = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = Unit
            override fun onError(exception: ImageCaptureException) = Unit
        })
    }

    override fun onDestroy() {
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
    onFlip: () -> Unit
) {
    var mode by remember { mutableStateOf(CameraMode.PHOTO) }

    LaunchedEffect(Unit) {
        // Camera is the default for a fresh launch.
        mode = CameraMode.PHOTO
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        if (!hasCameraPermission) {
            PermissionScreen(onRequestPermission)
        } else {
            CameraScreen(
                mode = mode,
                onModeChanged = { mode = it },
                onPreviewReady = onPreviewReady,
                onCapture = onCapture,
                onFlip = onFlip
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Camera access is needed", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Media Toolbox needs camera access to take photos and use its camera modes.",
                color = Color.LightGray,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                modifier = Modifier.clickable(onClick = onRequestPermission),
                shape = RoundedCornerShape(22.dp),
                color = Color.White
            ) {
                Text("Allow camera", color = Color.Black, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CameraScreen(
    mode: CameraMode,
    onModeChanged: (CameraMode) -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onFlip: () -> Unit
) {
    val context = LocalContext.current
    val modes = CameraMode.entries
    val index = modes.indexOf(mode)
    val previous = modes.getOrNull(index - 1)
    val next = modes.getOrNull(index + 1)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Spacer(Modifier.weight(1f))
            BottomCameraControls(
                mode = mode,
                previous = previous,
                next = next,
                onModeChanged = onModeChanged,
                onCapture = onCapture,
                onFlip = onFlip
            )
            BottomNavigation()
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
    onFlip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.82f)).padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(icon = { Icon(Icons.Default.MoreVert, null, tint = Color.White) }, label = "MORE")
            ShutterButton(mode = mode, onClick = onCapture)
            ControlButton(icon = { Icon(Icons.Default.FlipCameraAndroid, null, tint = Color.White) }, label = "FLIP", onClick = onFlip)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
    Text(
        mode.label,
        color = Color.White,
        fontSize = if (selected) 16.sp else 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.alpha(if (selected) 1f else 0.48f).clickable(onClick = onClick).padding(5.dp)
    )
}

@Composable
private fun ControlButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ShutterButton(mode: CameraMode, onClick: () -> Unit) {
    val enabled = mode == CameraMode.PHOTO
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.45f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp)
            .background(Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(58.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun BottomNavigation() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.Black).height(58.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("●", color = Color.White, fontSize = 14.sp)
            Text("CAMERA", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("GALLERY", color = Color.White, fontSize = 11.sp)
        }
    }
}
