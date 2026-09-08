package com.devlinguistpro.mediatoolbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val processingExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val pages = mutableStateListOf<String>()
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var showingPreview by mutableStateOf(false)
    private var selectedFolderName by mutableStateOf(MediaToolboxPrefs.DEFAULT_SCANNER_FOLDER)
    private var cameraPermissionGranted by mutableStateOf(false)
    private var cameraBindRequested = false
    private var cameraBindGeneration = 0L
    private var lifecycleActive = false
    private var captureInProgress = AtomicBoolean(false)
    private val savingPdf = AtomicBoolean(false)
    private var detectedQuad by mutableStateOf<DocumentDetector.Quad?>(null)

    companion object {
        private const val STATE_PAGE_PATHS = "scanner_page_paths"
        private const val STATE_SHOWING_PREVIEW = "scanner_showing_preview"
        private const val STATE_LENS_FACING = "scanner_lens_facing"
        private const val SESSION_DIR = "scanner-session"
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        if (granted && lifecycleActive && !isFinishing) bindCamera()
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            Toast.makeText(this, "Could not keep access to that folder", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE)
            .edit()
            .putString(MediaToolboxPrefs.KEY_SCANNER_FOLDER, uri.toString())
            .apply()
        selectedFolderName = folderName(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreScannerState(savedInstanceState)
        cameraPermissionGranted = hasCameraPermission()
        applyKeepScreenOnPreference()
        selectedFolderName = currentFolderName()

        setContent {
            MaterialTheme {
                ScannerApp(
                    pages = pages,
                    hasCameraPermission = cameraPermissionGranted,
                    showingPreview = showingPreview,
                    folderName = selectedFolderName,
                    detectedQuad = detectedQuad,
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { view ->
                        previewView = view
                        cameraBindRequested = true
                        if (cameraPermissionGranted && lifecycleActive && !showingPreview) bindCamera()
                    },
                    onCapture = ::capturePage,
                    onDeletePage = ::deletePage,
                    onFinish = {
                        if (pages.isNotEmpty()) {
                            unbindCamera()
                            showingPreview = true
                        }
                    },
                    onBack = ::finish,
                    onBackToScanner = {
                        showingPreview = false
                        cameraBindRequested = true
                        if (cameraPermissionGranted && lifecycleActive) bindCamera()
                    },
                    onFlip = ::flipCamera,
                    onChooseFolder = { folderPicker.launch(null) },
                    onSave = ::savePdf,
                    onOpenCamera = { startActivity(Intent(this, MainActivity::class.java)) },
                    onOpenGallery = { startActivity(Intent(this, GalleryActivity::class.java)) },
                    onOpenQr = { startActivity(Intent(this, QrScannerActivity::class.java)) }
                )
            }
        }
    }

    private fun restoreScannerState(savedInstanceState: Bundle?) {
        pages.clear()
        val restored = savedInstanceState?.getStringArrayList(STATE_PAGE_PATHS).orEmpty().filter { path ->
            val file = File(path)
            file.isFile && file.length() > 0L
        }
        pages.addAll(restored)
        showingPreview = savedInstanceState?.getBoolean(STATE_SHOWING_PREVIEW, false) == true && pages.isNotEmpty()
        lensFacing = savedInstanceState?.getInt(STATE_LENS_FACING, CameraSelector.LENS_FACING_BACK)
            ?: CameraSelector.LENS_FACING_BACK
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_PAGE_PATHS, ArrayList(pages))
        outState.putBoolean(STATE_SHOWING_PREVIEW, showingPreview)
        outState.putInt(STATE_LENS_FACING, lensFacing)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        lifecycleActive = true
        applyKeepScreenOnPreference()
        val granted = hasCameraPermission()
        if (cameraPermissionGranted != granted) cameraPermissionGranted = granted
        if (granted && cameraBindRequested && !showingPreview) bindCamera()
    }

    override fun onPause() {
        lifecycleActive = false
        unbindCamera()
        super.onPause()
    }

    private fun applyKeepScreenOnPreference() {
        val keepOn = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE)
            .getBoolean(MediaToolboxPrefs.KEY_KEEP_SCREEN_ON, true)
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun unbindCamera() {
        cameraBindGeneration++
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        imageCapture = null
        detectedQuad = null
        cameraProvider?.unbindAll()
    }

    private fun bindCamera() {
        val view = previewView ?: return
        if (!hasCameraPermission() || showingPreview || isFinishing || !lifecycleActive) return

        val requestId = ++cameraBindGeneration
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                if (!lifecycleActive || isFinishing || showingPreview || requestId != cameraBindGeneration) return@addListener
                val provider = future.get()
                if (!lifecycleActive || isFinishing || showingPreview || requestId != cameraBindGeneration) return@addListener

                val targetRotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(targetRotation)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setTargetRotation(targetRotation)
                    .setOutputImageRotationEnabled(true)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(processingExecutor) { image ->
                    try {
                        val quad = DocumentDetector.detect(image)
                        ContextCompat.getMainExecutor(this).execute {
                            if (lifecycleActive && !isFinishing && !showingPreview && requestId == cameraBindGeneration) {
                                detectedQuad = quad
                            }
                        }
                    } catch (_: Exception) {
                        ContextCompat.getMainExecutor(this).execute {
                            if (lifecycleActive && !isFinishing && !showingPreview && requestId == cameraBindGeneration) {
                                detectedQuad = null
                            }
                        }
                    } finally {
                        image.close()
                    }
                }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                if (!lifecycleActive || isFinishing || showingPreview || requestId != cameraBindGeneration) {
                    analysis.clearAnalyzer()
                    return@addListener
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture, analysis)
                cameraProvider = provider
                imageCapture = capture
                imageAnalysis = analysis
            } catch (_: Exception) {
                if (requestId != cameraBindGeneration) return@addListener
                imageCapture = null
                imageAnalysis = null
                detectedQuad = null
                if (lifecycleActive && !isFinishing) Toast.makeText(this, "Scanner camera could not start", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePage() {
        val capture = imageCapture ?: run {
            Toast.makeText(this, "Scanner camera is not ready", Toast.LENGTH_SHORT).show()
            if (lifecycleActive) bindCamera()
            return
        }
        if (!lifecycleActive || !captureInProgress.compareAndSet(false, true)) return

        val sessionDir = File(filesDir, SESSION_DIR).apply { mkdirs() }
        val rawFile = File(sessionDir, "raw_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(rawFile).build()

        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                processingExecutor.execute {
                    try {
                        val processed = processDocument(rawFile)
                        rawFile.delete()
                        runOnUiThread {
                            if (isDestroyed) {
                                processed?.delete()
                            } else if (processed != null && processed.isFile && processed.length() > 0L) {
                                pages.add(processed.absolutePath)
                            } else {
                                processed?.delete()
                                if (!isFinishing) Toast.makeText(this@ScannerActivity, "Could not process page", Toast.LENGTH_SHORT).show()
                            }
                            captureInProgress.set(false)
                        }
                    } catch (_: Exception) {
                        rawFile.delete()
                        runOnUiThread {
                            if (!isDestroyed && !isFinishing) Toast.makeText(this@ScannerActivity, "Could not process page", Toast.LENGTH_SHORT).show()
                            captureInProgress.set(false)
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                rawFile.delete()
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) Toast.makeText(this@ScannerActivity, "Could not capture page", Toast.LENGTH_SHORT).show()
                    captureInProgress.set(false)
                }
            }
        })
    }

    private fun processDocument(rawFile: File): File? {
        if (!rawFile.isFile || rawFile.length() == 0L) return null
        val source = decodeBitmap(rawFile, 2200) ?: return null
        val rotated = rotateFromExif(rawFile, source)
        if (rotated !== source) source.recycle()

        val detected = DocumentDetector.detect(rotated)
        val corrected = if (detected != null && detected.confidence >= 0.45f) {
            DocumentDetector.warp(rotated, detected, 2200)
        } else null
        val pageBitmap = corrected ?: rotated
        if (pageBitmap !== rotated) rotated.recycle()

        val sessionDir = File(filesDir, SESSION_DIR).apply { mkdirs() }
        val output = File(sessionDir, "page_${System.currentTimeMillis()}.jpg")
        return try {
            output.outputStream().use { stream ->
                if (!pageBitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) throw IOException("JPEG encode failed")
            }
            pageBitmap.recycle()
            output
        } catch (_: Exception) {
            pageBitmap.recycle()
            output.delete()
            null
        }
    }

    private fun decodeBitmap(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun rotateFromExif(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching { ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun deletePage(index: Int) {
        if (savingPdf.get() || captureInProgress.get()) return
        if (index in pages.indices) File(pages.removeAt(index)).delete()
        if (pages.isEmpty()) showingPreview = false
    }

    private fun flipCamera() {
        if (savingPdf.get() || captureInProgress.get() || !lifecycleActive) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        unbindCamera()
        if (cameraPermissionGranted && !showingPreview) bindCamera()
    }

    private fun currentFolderName(): String = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE)
        .getString(MediaToolboxPrefs.KEY_SCANNER_FOLDER, null)?.let(Uri::parse)?.let(::folderName)
        ?: MediaToolboxPrefs.DEFAULT_SCANNER_FOLDER

    private fun folderName(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri)?.substringAfterLast(':')?.let(Uri::decode)?.takeIf { it.isNotBlank() }
            ?: "Chosen folder"
    } catch (_: Exception) { "Chosen folder" }

    private fun savePdf() {
        if (!savingPdf.compareAndSet(false, true)) return
        val folder = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE)
            .getString(MediaToolboxPrefs.KEY_SCANNER_FOLDER, null)?.let(Uri::parse)
        if (folder == null) {
            savingPdf.set(false)
            Toast.makeText(this, "Choose a folder first", Toast.LENGTH_SHORT).show()
            folderPicker.launch(null)
            return
        }
        val pagePaths = pages.toList()
        if (pagePaths.isEmpty()) {
            savingPdf.set(false)
            return
        }
        Thread {
            var outputUri: Uri? = null
            var document: PdfDocument? = null
            try {
                if (!hasPersistedWriteAccess(folder)) throw IOException("Folder access is no longer available")
                pagePaths.forEachIndexed { index, path ->
                    val file = File(path)
                    if (!file.isFile || file.length() == 0L) throw IOException("Scan page ${index + 1} is unavailable")
                }
                document = createPdf(pagePaths)
                val name = "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.pdf"
                outputUri = DocumentsContract.createDocument(contentResolver, folder, "application/pdf", name)
                    ?: throw IOException("Could not create PDF in selected folder")
                contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                    document!!.writeTo(output)
                    output.flush()
                } ?: throw IOException("Could not open PDF for writing")
                runOnUiThread {
                    Toast.makeText(this, "PDF saved", Toast.LENGTH_SHORT).show()
                    clearPages()
                    finish()
                }
            } catch (e: Exception) {
                outputUri?.let { runCatching { contentResolver.delete(it, null, null) } }
                runOnUiThread {
                    Toast.makeText(this, "Could not save PDF: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                document?.close()
                savingPdf.set(false)
            }
        }.start()
    }

    private fun hasPersistedWriteAccess(uri: Uri): Boolean = contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isWritePermission
    }

    private fun createPdf(paths: List<String>): PdfDocument {
        val document = PdfDocument()
        try {
            paths.forEachIndexed { index, path ->
                val bitmap = decodePdfBitmap(File(path), 2200) ?: throw IOException("Unable to read scan page ${index + 1}")
                try {
                    val maxPageSide = 2200f
                    val scale = minOf(1f, maxPageSide / maxOf(bitmap.width, bitmap.height).toFloat())
                    val width = (bitmap.width * scale).toInt().coerceIn(1, 2200)
                    val height = (bitmap.height * scale).toInt().coerceIn(1, 2200)
                    val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                    try {
                        page.canvas.drawColor(Color.WHITE)
                        page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    } finally { document.finishPage(page) }
                } finally { bitmap.recycle() }
            }
            if (paths.isEmpty()) throw IOException("No scan pages")
            return document
        } catch (e: Exception) {
            document.close()
            throw e
        }
    }

    private fun decodePdfBitmap(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun clearPages() {
        pages.forEach { File(it).delete() }
        pages.clear()
    }

    override fun onDestroy() {
        lifecycleActive = false
        unbindCamera()
        cameraExecutor.shutdown()
        processingExecutor.shutdown()
        if (isFinishing) clearPages()
        super.onDestroy()
    }
}

@Composable
private fun ScannerApp(
    pages: List<String>,
    hasCameraPermission: Boolean,
    showingPreview: Boolean,
    folderName: String,
    detectedQuad: DocumentDetector.Quad?,
    onRequestPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onDeletePage: (Int) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onBackToScanner: () -> Unit,
    onFlip: () -> Unit,
    onChooseFolder: () -> Unit,
    onSave: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenQr: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = ComposeColor.Black) {
        when {
            !hasCameraPermission -> ScannerPermission(onRequestPermission, onBack)
            showingPreview -> ScannerPreview(
                pages, onBackToScanner, onDeletePage, folderName, onChooseFolder, onSave,
                onOpenCamera, onOpenGallery, onOpenQr
            )
            else -> ScannerCapture(
                pages, detectedQuad, onPreviewReady, onCapture, onDeletePage, onFinish, onBack, onFlip,
                onOpenCamera, onOpenGallery, onOpenQr
            )
        }
    }
}

@Composable
private fun ScannerPermission(onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(ComposeColor.Black).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera access is needed", color = ComposeColor.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("The scanner uses the camera to capture pages. No camera image is uploaded.", color = ComposeColor.LightGray)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("Allow camera") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun ScannerCapture(
    pages: List<String>,
    detectedQuad: DocumentDetector.Quad?,
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onDelete: (Int) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onFlip: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenQr: () -> Unit
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {
        AndroidView(
            factory = {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    onPreviewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        ScannerPageGuide(detectedQuad)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ComposeColor.White) }
                Text("Scanner", color = ComposeColor.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onFlip) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = ComposeColor.White) }
            }
            Column(
                Modifier.fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f)).navigationBarsPadding().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (pages.isNotEmpty()) {
                    LazyRow(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(pages) { index, path ->
                            Box(Modifier.size(68.dp)) {
                                LocalImage(path, Modifier.fillMaxSize())
                                IconButton(onClick = { onDelete(index) }, modifier = Modifier.align(Alignment.TopEnd).size(25.dp)) {
                                    Icon(Icons.Default.Delete, "Remove page", tint = ComposeColor.White)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("${pages.size} page${if (pages.size == 1) "" else "s"}", color = ComposeColor.White, modifier = Modifier.padding(end = 18.dp))
                    Box(
                        Modifier.size(72.dp).background(ComposeColor.White, CircleShape).padding(5.dp).clickable(onClick = onCapture),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(58.dp).background(ComposeColor.Black, CircleShape))
                    }
                    Spacer(Modifier.size(18.dp))
                    Button(onClick = onFinish, enabled = pages.isNotEmpty()) { Text("Finish") }
                }
                ScannerNavigation(onOpenCamera, onOpenGallery, onOpenQr)
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
    onSave: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenQr: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(ComposeColor.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to scanner", tint = ComposeColor.White) }
            Text("Preview", color = ComposeColor.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${pages.size} page${if (pages.size == 1) "" else "s"}", color = ComposeColor.LightGray)
        }
        LazyRow(Modifier.fillMaxWidth().height(94.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(pages) { index, path ->
                Box(Modifier.size(86.dp)) {
                    LocalImage(path, Modifier.fillMaxSize())
                    IconButton(onClick = { onDelete(index) }, modifier = Modifier.align(Alignment.TopEnd).size(27.dp)) {
                        Icon(Icons.Default.Delete, "Delete page", tint = ComposeColor.White)
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
                Icon(Icons.Default.Folder, "PDF folder", tint = ComposeColor.White)
                Spacer(Modifier.size(8.dp))
                Text(folderName, color = ComposeColor.White, maxLines = 1, modifier = Modifier.weight(1f))
                Button(onClick = onChooseFolder) { Text("Choose") }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = pages.isNotEmpty()) {
                Icon(Icons.Default.PictureAsPdf, "Save PDF")
                Spacer(Modifier.size(8.dp))
                Text("Save PDF")
            }
            Spacer(Modifier.height(8.dp))
            ScannerNavigation(onOpenCamera, onOpenGallery, onOpenQr)
        }
    }
}

@Composable
private fun ScannerPageGuide(quad: DocumentDetector.Quad?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val pageWidth = (maxWidth * 0.82f).coerceAtMost(360.dp)
        val pageHeight = (pageWidth * 1.32f).coerceAtMost(maxHeight * 0.66f)
        Canvas(Modifier.size(maxWidth, maxHeight)) {
            if (quad == null) {
                val left = (size.width - pageWidth.toPx()) / 2f
                val top = (size.height - pageHeight.toPx()) / 2f
                drawRoundRect(
                    color = ComposeColor.White.copy(alpha = 0.8f),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(pageWidth.toPx(), pageHeight.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                )
            } else {
                val scale = minOf(size.width / quad.width.toFloat(), size.height / quad.height.toFloat())
                val offsetX = (size.width - quad.width * scale) / 2f
                val offsetY = (size.height - quad.height * scale) / 2f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(offsetX + quad.topLeft.x * scale, offsetY + quad.topLeft.y * scale)
                    lineTo(offsetX + quad.topRight.x * scale, offsetY + quad.topRight.y * scale)
                    lineTo(offsetX + quad.bottomRight.x * scale, offsetY + quad.bottomRight.y * scale)
                    lineTo(offsetX + quad.bottomLeft.x * scale, offsetY + quad.bottomLeft.y * scale)
                    close()
                }
                drawPath(path, color = ComposeColor.White.copy(alpha = 0.95f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
            }
        }
        Text(
            if (quad == null) "Align the page inside the guide" else "Page detected",
            color = ComposeColor.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = pageHeight + 18.dp)
        )
    }
}

@Composable
private fun ScannerNavigation(onOpenCamera: () -> Unit, onOpenGallery: () -> Unit, onOpenQr: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onOpenCamera) {
            Icon(Icons.Default.PhotoCamera, "Camera")
            Spacer(Modifier.size(5.dp))
            Text("Camera")
        }
        Button(onClick = onOpenGallery) { Text("Gallery") }
        Button(onClick = onOpenQr) { Text("QR") }
    }
}

@Composable
private fun LocalImage(path: String, modifier: Modifier) {
    val bitmap = androidx.compose.runtime.remember(path) { decodePreview(path) }
    bitmap?.let { Image(it.asImageBitmap(), "Scanned page", modifier, contentScale = ContentScale.Fit) }
}

private fun decodePreview(path: String): Bitmap? {
    return try {
        val file = File(path)
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    } catch (_: Exception) {
        null
    }
}
