package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
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
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.input.pointer.pointerInput
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

private enum class CameraMode(val label: String) { PHOTO("CAMERA"), VIDEO("VIDEO"), SCAN("SCAN"), QR("QR") }
private enum class FlashSetting(val label: String) { OFF("Off"), AUTO("Auto"), ON("On") }

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var currentLens = CameraSelector.LENS_FACING_BACK
    private var currentMode = CameraMode.PHOTO
    private var previewView: PreviewView? = null
    private var cameraPermissionGranted by mutableStateOf(false)
    private var flashSetting by mutableStateOf(FlashSetting.OFF)
    private var flashAvailable by mutableStateOf(false)
    private var pendingVideoRecording = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        if (granted) bindCamera()
    }

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (pendingVideoRecording) {
            pendingVideoRecording = false
            if (granted) startVideoRecording()
            else Toast.makeText(this, "Microphone access is needed to record video with sound", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraPermissionGranted = hasCameraPermission()
        applyKeepScreenOnPreference()
        setContent {
            MaterialTheme {
                MediaToolboxApp(
                    hasCameraPermission = cameraPermissionGranted,
                    flashSetting = flashSetting,
                    flashAvailable = flashAvailable,
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { view -> previewView = view; if (cameraPermissionGranted) bindCamera() },
                    onCapture = ::capturePhoto,
                    onVideoToggle = ::toggleVideoRecording,
                    onFlip = ::flipCamera,
                    onOpenGallery = { startActivity(Intent(this, GalleryActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onCycleFlash = ::cycleFlash,
                    onModeChanged = ::changeMode
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOnPreference()
        val granted = hasCameraPermission()
        if (cameraPermissionGranted != granted) cameraPermissionGranted = granted
        if (granted && previewView != null) bindCamera()
    }

    private fun applyKeepScreenOnPreference() {
        val keepOn = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE)
            .getBoolean(MediaToolboxPrefs.KEY_KEEP_SCREEN_ON, true)
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun changeMode(mode: CameraMode) {
        if (activeRecording != null && mode != CameraMode.VIDEO) { activeRecording?.stop(); activeRecording = null }
        if (mode == CameraMode.SCAN) { startActivity(Intent(this, ScannerActivity::class.java)); return }
        if (mode == CameraMode.QR) { startActivity(Intent(this, QrScannerActivity::class.java)); return }
        currentMode = mode
        bindCamera()
    }

    private fun bindCamera() {
        val view = previewView ?: return
        if (!hasCameraPermission()) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get(); cameraProvider = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val selector = CameraSelector.Builder().requireLensFacing(currentLens).build()
                provider.unbindAll(); imageCapture = null; videoCapture = null; flashAvailable = false
                if (currentMode == CameraMode.PHOTO) {
                    val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setFlashMode(when (flashSetting) {
                        FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
                        FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
                        FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
                    }).build()
                    imageCapture = capture
                    val camera = provider.bindToLifecycle(this, selector, preview, capture)
                    flashAvailable = camera.cameraInfo.hasFlashUnit()
                    if (!flashAvailable && flashSetting != FlashSetting.OFF) {
                        flashSetting = FlashSetting.OFF
                        provider.unbindAll()
                        val safeCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setFlashMode(ImageCapture.FLASH_MODE_OFF).build()
                        imageCapture = safeCapture
                        provider.bindToLifecycle(this, selector, preview, safeCapture)
                    }
                } else {
                    val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST, androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(Quality.FHD))).build()
                    val video = VideoCapture.withOutput(recorder); videoCapture = video
                    provider.bindToLifecycle(this, selector, preview, video)
                }
            } catch (_: Exception) {
                imageCapture = null; videoCapture = null; flashAvailable = false
                runOnUiThread { Toast.makeText(this, "Camera could not start", Toast.LENGTH_SHORT).show() }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun cycleFlash() {
        if (activeRecording != null || !flashAvailable || currentMode != CameraMode.PHOTO) return
        flashSetting = when (flashSetting) { FlashSetting.OFF -> FlashSetting.AUTO; FlashSetting.AUTO -> FlashSetting.ON; FlashSetting.ON -> FlashSetting.OFF }
        bindCamera()
    }

    private fun flipCamera() {
        if (activeRecording != null) return
        currentLens = if (currentLens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        if (currentLens == CameraSelector.LENS_FACING_FRONT) flashSetting = FlashSetting.OFF
        bindCamera()
    }

    private fun capturePhoto() {
        val capture = imageCapture
        if (capture == null || currentMode != CameraMode.PHOTO) { Toast.makeText(this, "Camera is not ready", Toast.LENGTH_SHORT).show(); return }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val tempFile = File(cacheDir, "IMG_$timestamp.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(tempFile).build(), cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Media Toolbox"); put(MediaStore.Images.Media.IS_PENDING, 1) }
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Could not create gallery item")
                    try {
                        contentResolver.openOutputStream(uri)?.use { output -> tempFile.inputStream().use { input -> input.copyTo(output) } } ?: throw IllegalStateException("Could not open gallery item")
                        if (Build.VERSION.SDK_INT >= 29) contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                        runOnUiThread { Toast.makeText(this@MainActivity, "Photo saved", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
                } catch (_: Exception) { runOnUiThread { Toast.makeText(this@MainActivity, "Could not save photo", Toast.LENGTH_SHORT).show() } }
                finally { tempFile.delete() }
            }
            override fun onError(exception: ImageCaptureException) { tempFile.delete(); runOnUiThread { Toast.makeText(this@MainActivity, "Could not take photo", Toast.LENGTH_SHORT).show() } }
        })
    }

    private fun toggleVideoRecording() {
        if (currentMode != CameraMode.VIDEO) return
        activeRecording?.let { it.stop(); return }
        if (!hasAudioPermission()) {
            pendingVideoRecording = true
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVideoRecording()
    }

    private fun startVideoRecording() {
        if (currentMode != CameraMode.VIDEO || activeRecording != null) return
        val capture = videoCapture ?: run { Toast.makeText(this, "Video camera is not ready", Toast.LENGTH_SHORT).show(); return }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, "VID_$timestamp.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4"); put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Media Toolbox"); put(MediaStore.Video.Media.IS_PENDING, 1) }
            val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
            activeRecording = capture.output.prepareRecording(this, output).withAudioEnabled().start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) { activeRecording = null; if (event.hasError()) { contentResolver.delete(event.outputResults.outputUri, null, null); Toast.makeText(this, "Could not save video", Toast.LENGTH_SHORT).show() } else { contentResolver.update(event.outputResults.outputUri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null); Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show() } }
            }
        } else {
            val directory = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: run { Toast.makeText(this, "Video storage is unavailable", Toast.LENGTH_SHORT).show(); return }
            val file = File(directory, "VID_$timestamp.mp4")
            activeRecording = capture.output.prepareRecording(this, FileOutputOptions.Builder(file).build()).withAudioEnabled().start(ContextCompat.getMainExecutor(this)) { event -> if (event is VideoRecordEvent.Finalize) { activeRecording = null; if (event.hasError()) Toast.makeText(this, "Could not save video", Toast.LENGTH_SHORT).show() else Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show() } }
        }
    }

    override fun onStop() { activeRecording?.stop(); activeRecording = null; super.onStop() }
    override fun onDestroy() { activeRecording?.stop(); activeRecording = null; cameraProvider?.unbindAll(); cameraExecutor.shutdown(); super.onDestroy() }
}

@Composable
private fun MediaToolboxApp(hasCameraPermission: Boolean, flashSetting: FlashSetting, flashAvailable: Boolean, onRequestPermission: () -> Unit, onPreviewReady: (PreviewView) -> Unit, onCapture: () -> Unit, onVideoToggle: () -> Unit, onFlip: () -> Unit, onOpenGallery: () -> Unit, onOpenSettings: () -> Unit, onCycleFlash: () -> Unit, onModeChanged: (CameraMode) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(CameraMode.PHOTO) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (!hasCameraPermission) PermissionScreen(onRequestPermission)
        else CameraScreen(mode, { selected -> if (selected == CameraMode.SCAN || selected == CameraMode.QR) onModeChanged(selected) else { mode = selected; onModeChanged(selected) } }, onPreviewReady, onCapture, onVideoToggle, onFlip, onOpenGallery, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen) { moreOpen = it }
    }
}

@Composable private fun PermissionScreen(onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) { Text("Camera access is needed", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(10.dp)); Text("Media Toolbox needs camera access to take photos and use its camera modes.", color = Color.LightGray, fontSize = 15.sp); Spacer(Modifier.height(20.dp)); Button(onClick = onRequestPermission) { Text("Allow camera") } } }
}

@Composable private fun CameraScreen(mode: CameraMode, onModeChanged: (CameraMode) -> Unit, onPreviewReady: (PreviewView) -> Unit, onCapture: () -> Unit, onVideoToggle: () -> Unit, onFlip: () -> Unit, onOpenGallery: () -> Unit, onOpenSettings: () -> Unit, flashSetting: FlashSetting, flashAvailable: Boolean, onCycleFlash: () -> Unit, moreOpen: Boolean, setMoreOpen: (Boolean) -> Unit) {
    val context = LocalContext.current
    val modes = CameraMode.entries; val selectedIndex = modes.indexOf(mode)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.PERFORMANCE; onPreviewReady(this) } }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) { var drag = 0f; detectHorizontalDragGestures(onHorizontalDrag = { _, amount -> drag += amount }, onDragEnd = { when { drag < -80f && selectedIndex < modes.lastIndex -> onModeChanged(modes[selectedIndex + 1]); drag > 80f && selectedIndex > 0 -> onModeChanged(modes[selectedIndex - 1]) }; drag = 0f }) })
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) { Spacer(Modifier.weight(1f)); CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen); BottomNavigation(onCamera = { onModeChanged(CameraMode.PHOTO) }, onGallery = onOpenGallery, cameraSelected = mode == CameraMode.PHOTO) }
    }
}

@Composable private fun CameraControls(mode: CameraMode, selectedIndex: Int, modes: List<CameraMode>, onModeChanged: (CameraMode) -> Unit, onCapture: () -> Unit, onVideoToggle: () -> Unit, onFlip: () -> Unit, onOpenSettings: () -> Unit, flashSetting: FlashSetting, flashAvailable: Boolean, onCycleFlash: () -> Unit, moreOpen: Boolean, setMoreOpen: (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.82f))) {
        val sidePadding = when { maxWidth < 340.dp -> 8.dp; maxWidth < 400.dp -> 16.dp; else -> 22.dp }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ControlButton({ Icon(Icons.Default.MoreVert, "More", tint = Color.White) }, "MORE") { setMoreOpen(true) }
                ShutterButton(mode, onCapture, onVideoToggle)
                ControlButton({ Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White) }, "FLIP", onFlip)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val previous = modes.getOrNull(selectedIndex - 1); val next = modes.getOrNull(selectedIndex + 1)
                if (previous != null) ModeItem(previous, false) { onModeChanged(previous) }; Spacer(Modifier.width(18.dp)); ModeItem(modes[selectedIndex], true) {}; Spacer(Modifier.width(18.dp)); if (next != null) ModeItem(next, false) { onModeChanged(next) }
            }
        }
    }
    if (moreOpen) AlertDialog(onDismissRequest = { setMoreOpen(false) }, title = { Text("More camera options") }, text = { Column {
        if (mode == CameraMode.PHOTO && flashAvailable) {
            Text("Flash: ${flashSetting.label}"); Spacer(Modifier.height(8.dp))
            Button(onClick = { onCycleFlash(); setMoreOpen(false) }) { val icon = when (flashSetting) { FlashSetting.OFF -> Icons.Default.FlashOff; FlashSetting.AUTO -> Icons.Default.FlashAuto; FlashSetting.ON -> Icons.Default.FlashOn }; Icon(icon, "Flash"); Spacer(Modifier.size(6.dp)); Text("Change flash") }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { setMoreOpen(false); onOpenSettings() }) { Icon(Icons.Default.Settings, "Settings"); Spacer(Modifier.size(6.dp)); Text("Settings") }
        Spacer(Modifier.height(8.dp)); Text("Options are kept simple; device-specific camera features such as HDR are not forced because support varies by device.", color = Color.Gray, fontSize = 12.sp)
    } }, confirmButton = { Button(onClick = { setMoreOpen(false) }) { Text("Done") } })
}

@Composable private fun ModeItem(mode: CameraMode, selected: Boolean, onClick: () -> Unit) { Text(mode.label, color = Color.White, fontSize = if (selected) 16.sp else 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.alpha(if (selected) 1f else 0.48f).clickable(onClick = onClick).padding(5.dp)) }
@Composable private fun ControlButton(icon: @Composable () -> Unit, label: String, onClick: () -> Unit = {}) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) { Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }; Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium) } }
@Composable private fun ShutterButton(mode: CameraMode, onPhoto: () -> Unit, onVideoToggle: () -> Unit) { val context = LocalContext.current; val enabled = mode == CameraMode.PHOTO || mode == CameraMode.VIDEO || mode == CameraMode.SCAN || mode == CameraMode.QR; val action = when (mode) { CameraMode.PHOTO -> onPhoto; CameraMode.VIDEO -> onVideoToggle; CameraMode.SCAN -> ({ context.startActivity(Intent(context, ScannerActivity::class.java)) }); CameraMode.QR -> ({ context.startActivity(Intent(context, QrScannerActivity::class.java)) }) }; Box(Modifier.size(72.dp).background(Color.White.copy(alpha = if (enabled) 1f else 0.45f), CircleShape).clickable(enabled = enabled, onClick = action).padding(5.dp).background(Color.Black, CircleShape), contentAlignment = Alignment.Center) { Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape)) } }
@Composable private fun BottomNavigation(onCamera: () -> Unit, onGallery: () -> Unit, cameraSelected: Boolean) { Row(Modifier.fillMaxWidth().background(Color.Black).height(58.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { NavigationItem("CAMERA", cameraSelected, onCamera); NavigationItem("GALLERY", !cameraSelected, onGallery) } }
@Composable private fun NavigationItem(label: String, selected: Boolean, onClick: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp)) { Text(label, color = Color.White.copy(alpha = if (selected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } }
