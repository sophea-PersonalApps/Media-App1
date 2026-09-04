package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var result by mutableStateOf<String?>(null)
    private var cameraPermissionGranted by mutableStateOf(false)
    private val resultLocked = AtomicBoolean(false)

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
                QrScannerScreen(
                    hasCameraPermission = cameraPermissionGranted,
                    result = result,
                    onPreviewReady = {
                        previewView = it
                        if (cameraPermissionGranted) bindCamera()
                    },
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onBack = ::finish,
                    onFlip = ::flipCamera,
                    onScanAgain = {
                        resultLocked.set(false)
                        result = null
                    },
                    onCopy = { copyResult(result) },
                    onOpen = { openResult(result) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = hasCameraPermission()
        if (cameraPermissionGranted != granted) cameraPermissionGranted = granted
        if (granted && previewView != null) bindCamera()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        val view = previewView ?: return
        if (!hasCameraPermission()) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (resultLocked.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (!value.isNullOrBlank() && resultLocked.compareAndSet(false, true)) {
                                result = value
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }

                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "QR scanner camera could not start", Toast.LENGTH_SHORT).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        if (result != null) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCamera()
    }

    private fun copyResult(value: String?) {
        if (value.isNullOrBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QR code", value))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun openResult(value: String?) {
        if (value.isNullOrBlank()) return
        val uri = try { Uri.parse(value.trim()) } catch (_: Exception) { null }
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || scheme !in setOf("http", "https")) {
            Toast.makeText(this, "This QR code is not a web link", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        barcodeScanner.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun QrScannerScreen(
    hasCameraPermission: Boolean,
    result: String?,
    onPreviewReady: (PreviewView) -> Unit,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    onFlip: () -> Unit,
    onScanAgain: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (!hasCameraPermission) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera access is needed", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("The QR scanner reads codes directly on your phone. Nothing is uploaded.", color = Color.LightGray)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRequestPermission) { Text("Allow camera") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        } else {
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
                QrFinderOverlay()
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                    Text("QR SCANNER", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onFlip, enabled = result == null) {
                        Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White)
                    }
                }
                result?.let {
                    QrResultCard(it, onCopy, onOpen, onScanAgain, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun QrFinderOverlay() {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val finderSize = when {
            maxWidth < 340.dp -> 190.dp
            maxWidth < 420.dp -> 230.dp
            else -> 270.dp
        }
        Box(
            Modifier
                .size(finderSize)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
        )
        Text(
            "Point the camera at a QR code",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 150.dp)
        )
    }
}

@Composable
private fun QrResultCard(
    value: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier
) {
    val isWebLink = runCatching { Uri.parse(value.trim()).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .padding(18.dp)
    ) {
        Text("QR code found", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, "Copy")
                Spacer(Modifier.size(5.dp))
                Text("Copy")
            }
            Button(onClick = onOpen, enabled = isWebLink, modifier = Modifier.weight(1f)) { Text("Open link") }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text("Scan another") }
    }
}
