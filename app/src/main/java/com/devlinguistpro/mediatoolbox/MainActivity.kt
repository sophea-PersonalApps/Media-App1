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
import androidx.camera.core.Camera
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private enum class FlashSetting(val label: String) { OFF("Off"), AUTO("Auto"), ON("On") }

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording by mutableStateOf<Recording?>(null)
    private var currentLens = CameraSelector.LENS_FACING_BACK
    private var currentMode by mutableStateOf(CameraSectionMode.PHOTO)
    private var previewView: PreviewView? = null
    private var cameraPermissionGranted by mutableStateOf(false)
    private var flashSetting by mutableStateOf(FlashSetting.OFF)
    private var flashAvailable by mutableStateOf(false)
    private var cameraZoom by mutableFloatStateOf(1f)
    private var pendingVideoRecording = false
    private var cameraBindRequestId = 0L
    private var cameraBindingInProgress = false
    private var boundMode: CameraSectionMode? = null
    private var boundLens: Int? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        if (granted && !isFinishing && !isDestroyed) bindCamera()
    }

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!pendingVideoRecording || isFinishing || isDestroyed) return@registerForActivityResult
        if (granted && currentMode == CameraSectionMode.VIDEO) startVideoRecording() else {
            pendingVideoRecording = false
            if (!granted) Toast.makeText(this, "Microphone access is needed to record video with sound", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentMode = requestedMode(intent)
        cameraPermissionGranted = hasCameraPermission()
        applyKeepScreenOnPreference()
        setContent {
            MaterialTheme {
                MediaToolboxApp(
                    mode = currentMode,
                    hasCameraPermission = cameraPermissionGranted,
                    currentLens = currentLens,
                    flashSetting = flashSetting,
                    flashAvailable = flashAvailable,
                    cameraZoom = cameraZoom,
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { view -> previewView = view; if (cameraPermissionGranted) bindCamera() },
                    onCapture = ::capturePhoto,
                    onVideoToggle = ::toggleVideoRecording,
                    isRecording = activeRecording != null,
                    onFlip = ::flipCamera,
                    onOpenGallery = { startActivity(Intent(this, GalleryActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onCycleFlash = ::cycleFlash,
                    onZoom = ::updateCameraZoom,
                    onModeChanged = ::changeMode
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requested = requestedMode(intent)
        if (requested != currentMode) changeMode(requested)
    }

    private fun requestedMode(intent: Intent): CameraSectionMode = when (runCatching { CameraSectionMode.valueOf(intent.getStringExtra(EXTRA_CAMERA_SECTION_MODE).orEmpty()) }.getOrNull()) {
        CameraSectionMode.VIDEO -> CameraSectionMode.VIDEO
        else -> CameraSectionMode.PHOTO
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOnPreference()
        val granted = hasCameraPermission()
        if (cameraPermissionGranted != granted) cameraPermissionGranted = granted
        if (granted && previewView != null && cameraProvider == null) bindCamera()
    }

    private fun applyKeepScreenOnPreference() {
        val keepOn = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE).getBoolean(MediaToolboxPrefs.KEY_KEEP_SCREEN_ON, true)
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun changeMode(mode: CameraSectionMode) {
        if (mode == currentMode && (mode == CameraSectionMode.PHOTO || mode == CameraSectionMode.VIDEO)) return
        if (activeRecording != null && mode != CameraSectionMode.VIDEO) {
            pendingVideoRecording = false
            activeRecording?.stop()
            returnToCameraModeAfterRecordingStop(mode)
            return
        }
        pendingVideoRecording = false
        when (mode) {
            CameraSectionMode.PHOTO, CameraSectionMode.VIDEO -> {
                currentMode = mode
                cameraZoom = 1f
                bindCamera(forceRebind = true)
            }
            CameraSectionMode.SCAN -> startActivity(Intent(this, ScannerActivity::class.java))
            CameraSectionMode.QR -> startActivity(Intent(this, QrScannerActivity::class.java))
        }
    }

    private fun returnToCameraModeAfterRecordingStop(mode: CameraSectionMode) {
        when (mode) {
            CameraSectionMode.PHOTO, CameraSectionMode.VIDEO -> {
                currentMode = mode
                cameraZoom = 1f
                bindCamera(forceRebind = true)
            }
            else -> Unit
        }
    }

    private fun bindCamera(forceRebind: Boolean = false) {
        val view = previewView ?: return
        if (!hasCameraPermission() || isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        if (!forceRebind && cameraBindingInProgress) return
        if (!forceRebind && cameraProvider != null && boundMode == currentMode && boundLens == currentLens && ((currentMode == CameraSectionMode.PHOTO && imageCapture != null) || (currentMode == CameraSectionMode.VIDEO && videoCapture != null))) return

        val requestId = ++cameraBindRequestId
        cameraBindingInProgress = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (requestId != cameraBindRequestId || isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                if (requestId == cameraBindRequestId) cameraBindingInProgress = false
                return@addListener
            }
            try {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val selector = CameraSelector.Builder().requireLensFacing(currentLens).build()
                provider.unbindAll()
                imageCapture = null
                videoCapture = null
                flashAvailable = false
                camera = null

                when (currentMode) {
                    CameraSectionMode.PHOTO -> {
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(when (flashSetting) {
                                FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
                                FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
                                FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
                            }).build()
                        imageCapture = capture
                        val boundCamera = provider.bindToLifecycle(this, selector, preview, capture)
                        camera = boundCamera
                        flashAvailable = boundCamera.cameraInfo.hasFlashUnit()
                        if (!flashAvailable && flashSetting != FlashSetting.OFF) {
                            flashSetting = FlashSetting.OFF
                            provider.unbindAll()
                            val safeCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                                .build()
                            imageCapture = safeCapture
                            camera = provider.bindToLifecycle(this, selector, preview, safeCapture)
                        }
                    }
                    CameraSectionMode.VIDEO -> {
                        val recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HIGHEST, androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)))
                            .build()
                        val video = VideoCapture.withOutput(recorder)
                        videoCapture = video
                        camera = provider.bindToLifecycle(this, selector, preview, video)
                        if (pendingVideoRecording && hasAudioPermission()) startVideoRecording()
                    }
                    CameraSectionMode.SCAN, CameraSectionMode.QR -> Unit
                }
                camera?.cameraControl?.setZoomRatio(cameraZoom)
                boundMode = currentMode
                boundLens = currentLens
            } catch (_: Exception) {
                camera = null
                imageCapture = null
                videoCapture = null
                flashAvailable = false
                boundMode = null
                boundLens = null
                pendingVideoRecording = false
                runOnUiThread { Toast.makeText(this, "Camera could not start", Toast.LENGTH_SHORT).show() }
            } finally {
                if (requestId == cameraBindRequestId) cameraBindingInProgress = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateCameraZoom(value: Float) {
        if (currentMode != CameraSectionMode.PHOTO && currentMode != CameraSectionMode.VIDEO) return
        val maxZoom = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 10f
        cameraZoom = value.coerceIn(1f, maxZoom)
        camera?.cameraControl?.setZoomRatio(cameraZoom)
    }

    private fun cycleFlash() {
        if (activeRecording != null || !flashAvailable || currentMode != CameraSectionMode.PHOTO) return
        flashSetting = when (flashSetting) {
            FlashSetting.OFF -> FlashSetting.AUTO
            FlashSetting.AUTO -> FlashSetting.ON
            FlashSetting.ON -> FlashSetting.OFF
        }
        bindCamera(forceRebind = true)
    }

    private fun flipCamera() {
        if (activeRecording != null || isFinishing || isDestroyed) return
        currentLens = if (currentLens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        if (currentLens == CameraSelector.LENS_FACING_FRONT) flashSetting = FlashSetting.OFF
        cameraZoom = 1f
        bindCamera(forceRebind = true)
    }

    private fun capturePhoto() {
        val capture = imageCapture
        if (capture == null || currentMode != CameraSectionMode.PHOTO || isFinishing || isDestroyed) {
            Toast.makeText(this, "Camera is not ready", Toast.LENGTH_SHORT).show()
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Media Toolbox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val output = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                val uri = result.savedUri ?: run {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Photo was captured but could not be saved", Toast.LENGTH_LONG).show() }
                    return
                }
                try {
                    if (Build.VERSION.SDK_INT >= 29) contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                } catch (_: Exception) {
                    contentResolver.delete(uri, null, null)
                    runOnUiThread { Toast.makeText(this@MainActivity, "Could not finish saving photo", Toast.LENGTH_LONG).show() }
                }
            }
            override fun onError(exception: ImageCaptureException) { runOnUiThread { Toast.makeText(this@MainActivity, "Could not take photo", Toast.LENGTH_SHORT).show() } }
        })
    }

    private fun toggleVideoRecording() {
        if (currentMode != CameraSectionMode.VIDEO || isFinishing || isDestroyed) return
        activeRecording?.let { pendingVideoRecording = false; it.stop(); return }
        pendingVideoRecording = true
        if (!hasAudioPermission()) { audioPermission.launch(Manifest.permission.RECORD_AUDIO); return }
        startVideoRecording()
    }

    private fun startVideoRecording() {
        if (currentMode != CameraSectionMode.VIDEO || activeRecording != null || isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        if (!hasAudioPermission()) { pendingVideoRecording = true; audioPermission.launch(Manifest.permission.RECORD_AUDIO); return }
        val capture = videoCapture
        if (capture == null || boundMode != CameraSectionMode.VIDEO || boundLens != currentLens) { pendingVideoRecording = true; bindCamera(forceRebind = true); return }
        pendingVideoRecording = false
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        if (Build.VERSION.SDK_INT < 29) { Toast.makeText(this, "Video recording requires Android 10 or newer", Toast.LENGTH_LONG).show(); return }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VID_$timestamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Media Toolbox")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        try {
            val pending = capture.output.prepareRecording(this, output).withAudioEnabled()
            activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    val uri = event.outputResults.outputUri
                    if (event.hasError()) contentResolver.delete(uri, null, null) else runCatching { contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }.onFailure { contentResolver.delete(uri, null, null) }
                }
            }
        } catch (_: Exception) {
            pendingVideoRecording = false
            activeRecording = null
            runOnUiThread { Toast.makeText(this, "Could not start video recording", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onStop() { pendingVideoRecording = false; activeRecording?.stop(); super.onStop() }

    override fun onDestroy() {
        pendingVideoRecording = false
        activeRecording?.stop()
        cameraBindRequestId++
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        imageCapture = null
        videoCapture = null
        boundMode = null
        boundLens = null
        cameraBindingInProgress = false
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun MediaToolboxApp(
    mode: CameraSectionMode, hasCameraPermission: Boolean, currentLens: Int, flashSetting: FlashSetting, flashAvailable: Boolean, cameraZoom: Float,
    onRequestPermission: () -> Unit, onPreviewReady: (PreviewView) -> Unit, onCapture: () -> Unit, onVideoToggle: () -> Unit,
    isRecording: Boolean, onFlip: () -> Unit, onOpenGallery: () -> Unit, onOpenSettings: () -> Unit, onCycleFlash: () -> Unit,
    onZoom: (Float) -> Unit, onModeChanged: (CameraSectionMode) -> Unit
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (!hasCameraPermission) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("Camera access is needed", color = Color.White)
                    Spacer(Modifier.height(16.dp)); Button(onClick = onRequestPermission) { Text("Allow camera") }
                }
            }
        } else CameraScreen(mode, currentLens, cameraZoom, onPreviewReady, onCapture, onVideoToggle, isRecording, onFlip, onOpenGallery, flashSetting, flashAvailable, onCycleFlash, onZoom, onModeChanged, moreOpen) { moreOpen = it }
    }
    if (moreOpen) AlertDialog(
        onDismissRequest = { moreOpen = false }, title = { Text("More camera options") },
        text = { Column {
            if (mode == CameraSectionMode.PHOTO && flashAvailable) {
                Text("Flash: ${flashSetting.label}"); Spacer(Modifier.height(8.dp))
                Button(onClick = { onCycleFlash(); moreOpen = false }) {
                    val icon = when (flashSetting) { FlashSetting.OFF -> Icons.Default.FlashOff; FlashSetting.AUTO -> Icons.Default.FlashAuto; FlashSetting.ON -> Icons.Default.FlashOn }
                    Icon(icon, "Flash"); Spacer(Modifier.size(6.dp)); Text("Change flash")
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = { moreOpen = false; onOpenSettings() }) { Text("Settings") }
        } }, confirmButton = { Button(onClick = { moreOpen = false }) { Text("Done") } }
    )
}

@Composable
private fun CameraScreen(
    mode: CameraSectionMode, currentLens: Int, cameraZoom: Float, onPreviewReady: (PreviewView) -> Unit, onCapture: () -> Unit, onVideoToggle: () -> Unit,
    isRecording: Boolean, onFlip: () -> Unit, onOpenGallery: () -> Unit, flashSetting: FlashSetting, flashAvailable: Boolean,
    onCycleFlash: () -> Unit, onZoom: (Float) -> Unit, onModeChanged: (CameraSectionMode) -> Unit, moreOpen: Boolean, setMoreOpen: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val modes = CameraSectionMode.entries
    val selectedIndex = modes.indexOf(mode)
    var showCaptureFlash by remember { mutableStateOf(false) }
    LaunchedEffect(showCaptureFlash) { if (showCaptureFlash) { kotlinx.coroutines.delay(120); showCaptureFlash = false } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PreviewView(context).apply {
                scaleType = if (currentLens == CameraSelector.LENS_FACING_FRONT) PreviewView.ScaleType.FIT_CENTER else PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                onPreviewReady(this)
            } },
            update = { view -> view.scaleType = if (currentLens == CameraSelector.LENS_FACING_FRONT) PreviewView.ScaleType.FIT_CENTER else PreviewView.ScaleType.FILL_CENTER },
            modifier = Modifier.fillMaxSize()
        )
        if (showCaptureFlash && mode == CameraSectionMode.PHOTO) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.88f)))
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode, currentLens) {
            var horizontalDrag = 0f
            var gestureZoom = cameraZoom
            detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                val cameraMode = mode == CameraSectionMode.PHOTO || mode == CameraSectionMode.VIDEO
                if (cameraMode && kotlin.math.abs(zoom - 1f) > 0.001f) {
                    gestureZoom = (gestureZoom * zoom).coerceIn(1f, 10f)
                    onZoom(gestureZoom)
                    horizontalDrag = 0f
                } else if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                    horizontalDrag += pan.x
                    when {
                        horizontalDrag <= -80f && selectedIndex < modes.lastIndex -> {
                            onModeChanged(modes[selectedIndex + 1])
                            horizontalDrag = 0f
                        }
                        horizontalDrag >= 80f && selectedIndex > 0 -> {
                            onModeChanged(modes[selectedIndex - 1])
                            horizontalDrag = 0f
                        }
                    }
                }
            }
        })
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Spacer(Modifier.weight(1f))
            CameraSectionControls(
                mode = mode, onModeSelected = onModeChanged,
                onPrimaryAction = { when (mode) {
                    CameraSectionMode.PHOTO -> { onCapture(); showCaptureFlash = true }
                    CameraSectionMode.VIDEO -> onVideoToggle()
                    CameraSectionMode.SCAN, CameraSectionMode.QR -> Unit
                } },
                onFlip = onFlip, onMore = { setMoreOpen(true) }, primaryEnabled = mode == CameraSectionMode.PHOTO || mode == CameraSectionMode.VIDEO, isRecording = isRecording
            )
            CameraSectionBottomNavigation(cameraSelected = true, onCamera = { if (mode != CameraSectionMode.PHOTO) onModeChanged(CameraSectionMode.PHOTO) }, onGallery = onOpenGallery)
        }
    }
}