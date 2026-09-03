package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
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
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private val pages = mutableStateListOf<String>()
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var showingPreview by mutableStateOf(false)
    private var selectedFolderName by mutableStateOf("Choose folder when saving")

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera()
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) { }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_FOLDER_URI, uri.toString()).apply()
        selectedFolderName = folderName(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        selectedFolderName = currentFolderName()
        setContent {
            MaterialTheme {
                ScannerApp(
                    pages = pages,
                    hasCameraPermission = hasCameraPermission(),
                    showingPreview = showingPreview,
                    folderName = selectedFolderName,
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { previewView = it; if (hasCameraPermission()) bindCamera() },
                    onCapture = ::capturePage,
                    onDeletePage = ::deletePage,
                    onFinish = { if (pages.isNotEmpty()) showingPreview = true },
                    onBack = ::finish,
                    onBackToScanner = { showingPreview = false },
                    onFlip = ::flipCamera,
                    onChooseFolder = { folderPicker.launch(null) },
                    onSave = ::savePdf
                )
            }
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
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()
                imageCapture = capture
                provider.bindToLifecycle(this, selector, preview, capture)
            } catch (_: Exception) {
                imageCapture = null
                runOnUiThread { Toast.makeText(this, "Scanner camera could not start", Toast.LENGTH_SHORT).show() }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePage() {
        val capture = imageCapture ?: return
        val file = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    runOnUiThread { if (file.exists()) pages.add(file.absolutePath) }
                }
                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    runOnUiThread { Toast.makeText(this@ScannerActivity, "Could not capture page", Toast.LENGTH_SHORT).show() }
                }
            }
        )
    }

    private fun deletePage(index: Int) {
        if (index in pages.indices) File(pages.removeAt(index)).delete()
        if (pages.isEmpty()) showingPreview = false
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCamera()
    }

    private fun currentFolderName(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_FOLDER_URI, null)?.let(Uri::parse)?.let(::folderName)
            ?: "Choose folder when saving"

    private fun folderName(uri: Uri): String =
        DocumentsContract.getTreeDocumentId(uri)?.substringAfterLast(':')?.replace('%20', ' ')
            ?.takeIf { it.isNotBlank() } ?: "Chosen folder"

    private fun savePdf() {
        val folder = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_FOLDER_URI, null)?.let(Uri::parse)
        if (folder == null) {
            Toast.makeText(this, "Choose a folder first", Toast.LENGTH_SHORT).show()
            folderPicker.launch(null)
            return
        }
        val pagePaths = pages.toList()
        if (pagePaths.isEmpty()) return

        Thread {
            var outputUri: Uri? = null
            var document: PdfDocument? = null
            try {
                document = createPdf(pagePaths)
                val name = "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
                outputUri = DocumentsContract.createDocument(contentResolver, folder, "application/pdf", name)
                    ?: throw IOException("Could not create PDF in selected folder")
                contentResolver.openOutputStream(outputUri, "w")?.use { document.writeTo(it) }
                    ?: throw IOException("Could not open PDF")
                runOnUiThread {
                    Toast.makeText(this, "PDF saved", Toast.LENGTH_SHORT).show()
                    clearPages()
                    finish()
                }
            } catch (_: Exception) {
                outputUri?.let { contentResolver.delete(it, null, null) }
                runOnUiThread { Toast.makeText(this, "Could not save PDF", Toast.LENGTH_LONG).show() }
            } finally {
                document?.close()
            }
        }.start()
    }

    private fun createPdf(paths: List<String>): PdfDocument {
        val document = PdfDocument()
        try {
            paths.forEachIndexed { index, path ->
                val bitmap = BitmapFactory.decodeFile(path) ?: throw IOException("Unable to read scan page")
                val maxSide = 2200
                val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat())
                val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                document.finishPage(page)
                bitmap.recycle()
            }
            return document
        } catch (e: Exception) {
            document.close()
            throw e
        }
    }

    private fun clearPages() {
        pages.forEach { File(it).delete() }
        pages.clear()
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        if (isFinishing) clearPages()
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "scanner_settings"
        private const val KEY_FOLDER_URI = "pdf_folder_uri"
    }
}

@Composable
private fun ScannerApp(
    pages: List<String>,
    hasCameraPermission: Boolean,
    showingPreview: Boolean,
    folderName: String,
    onRequestPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onDeletePage: (Int) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onBackToScanner: () -> Unit,
    onFlip: () -> Unit,
    onChooseFolder: () -> Unit,
    onSave: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
        when {
            !hasCameraPermission -> ScannerPermission(onRequestPermission, onBack)
            showingPreview -> ScannerPreview(pages, onBackToScanner, onDeletePage, folderName, onChooseFolder, onSave)
            else -> ScannerCapture(pages, onPreviewReady, onCapture, onDeletePage, onFinish, onBack, onFlip)
        }
    }
}

@Composable
private fun ScannerPermission(onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera access is needed", color = androidx.compose.ui.graphics.Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("The scanner uses the camera to capture pages. No camera image is uploaded.", color = androidx.compose.ui.graphics.Color.LightGray)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("Allow camera") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun ScannerCapture(
    pages: List<String>,
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onDelete: (Int) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onFlip: () -> Unit
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
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
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) }
                Text("Scanner", color = androidx.compose.ui.graphics.Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                IconButton(onClick = onFlip) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = androidx.compose.ui.graphics.Color.White) }
            }
            Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.82f)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pages.isNotEmpty()) {
                    LazyRow(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(pages) { index, path ->
                            Box(Modifier.size(68.dp)) {
                                LocalImage(path, Modifier.fillMaxSize())
                                IconButton(onClick = { onDelete(index) }, modifier = Modifier.align(Alignment.TopEnd).size(25.dp)) {
                                    Icon(Icons.Default.Delete, "Remove page", tint = androidx.compose.ui.graphics.Color.White)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("${pages.size} page${if (pages.size == 1) "" else "s"}", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(end = 18.dp))
                    Box(Modifier.size(72.dp).background(androidx.compose.ui.graphics.Color.White, CircleShape).padding(5.dp).clickable(onClick = onCapture), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(58.dp).background(androidx.compose.ui.graphics.Color.Black, CircleShape))
                    }
                    Spacer(Modifier.size(18.dp))
                    Button(onClick = onFinish, enabled = pages.isNotEmpty()) { Text("Finish") }
                }
            }
        }
    }
}

@Composable
private fun ScannerPreview(
    pages: List<String>,
    onBack: () -> Unit,
    onDelete: (Int) -> Unit,
    folderName: String,
    onChooseFolder: () -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to scanner", tint = androidx.compose.ui.graphics.Color.White) }
            Text("Preview", color = androidx.compose.ui.graphics.Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${pages.size} page${if (pages.size == 1) "" else "s"}", color = androidx.compose.ui.graphics.Color.LightGray)
        }
        LazyRow(Modifier.fillMaxWidth().height(94.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(pages) { index, path ->
                Box(Modifier.size(86.dp)) {
                    LocalImage(path, Modifier.fillMaxSize())
                    IconButton(onClick = { onDelete(index) }, modifier = Modifier.align(Alignment.TopEnd).size(27.dp)) {
                        Icon(Icons.Default.Delete, "Delete page", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            if (pages.isNotEmpty()) LocalImage(pages[0], Modifier.fillMaxWidth().aspectRatio(0.72f))
        }
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Folder, "PDF folder", tint = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.size(8.dp))
                Text(folderName, color = androidx.compose.ui.graphics.Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                Button(onClick = onChooseFolder) { Text("Choose") }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = pages.isNotEmpty()) {
                Icon(Icons.Default.PictureAsPdf, "Save PDF")
                Spacer(Modifier.size(8.dp))
                Text("Save PDF")
            }
        }
    }
}

@Composable
private fun LocalImage(path: String, modifier: Modifier) {
    val bitmap = androidx.compose.runtime.remember(path) { decodePreview(path) }
    bitmap?.let { Image(it.asImageBitmap(), "Scanned page", modifier, contentScale = ContentScale.Fit) }
}

private fun decodePreview(path: String): Bitmap? = try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
