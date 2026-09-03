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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.*
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
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var result by mutableStateOf<String?>(null)
    private var scannerReady by mutableStateOf(false)
    private val resultLocked = AtomicBoolean(false)

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                QrScannerScreen(
                    result = result,
                    onPreviewReady = { previewView = it; if (hasCameraPermission()) bindCamera() },
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onBack = ::finish,
                    onFlip = ::flipCamera,
                    onScanAgain = { resultLocked.set(false); result = null; bindCamera() },
                    onCopy = { copyResult(result) },
                    onOpen = { openResult(result) }
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        val view = previewView ?: return
        if (!hasCameraPermission()) return
        scannerReady = false
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)
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
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.firstOrNull()?.rawValue
                            if (!value.isNullOrBlank() && resultLocked.compareAndSet(false, true)) {
                                result = value
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)
                scannerReady = true
            } catch (_: Exception) {
                scannerReady = false
                runOnUiThread { Toast.makeText(this, "QR scanner camera could not start", Toast.LENGTH_SHORT).show() }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        if (result != null) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
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
        val uri = try { Uri.parse(value) } catch (_: Exception) { null }
        if (uri == null || uri.scheme !in setOf("http", "https")) {
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
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun QrScannerScreen(
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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
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
                    factory = { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.PERFORMANCE; onPreviewReady(this) } },
                    modifier = Modifier.fillMaxSize()
                )
                QrFinderOverlay()
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                    Text("QR SCANNER", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onFlip, enabled = result == null) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White) }
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
    Box(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 170.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(250.dp).background(Color.Transparent, RoundedCornerShape(20.dp)))
        Text("Point the camera at a QR code", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }
}

@Composable
private fun QrResultCard(value: String, onCopy: () -> Unit, onOpen: () -> Unit, onScanAgain: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.92f)).padding(18.dp)) {
        Text("QR code found", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 15.sp, maxLines = 5)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, "Copy")
                Spacer(Modifier.size(5.dp))
                Text("Copy")
            }
            Button(onClick = onOpen, enabled = value.startsWith("https://") || value.startsWith("http://"), modifier = Modifier.weight(1f)) { Text("Open link") }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text("Scan another") }
    }
}
