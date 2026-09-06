package com.devlinguistpro.mediatoolbox

import android.Manifest
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private val pages = mutableStateListOf<String>()
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var showingPreview by mutableStateOf(false)
    private var selectedFolderName by mutableStateOf(MediaToolboxPrefs.DEFAULT_SCANNER_FOLDER)
    private var cameraPermissionGranted by mutableStateOf(false)
    private var cameraBindRequested = false
    private val savingPdf = AtomicBoolean(false)

    companion object {
        private const val STATE_PAGE_PATHS = "scanner_page_paths"
        private const val STATE_SHOWING_PREVIEW = "scanner_showing_preview"
        private const val STATE_LENS_FACING = "scanner_lens_facing"
        private const val SESSION_DIR = "scanner-session"
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> cameraPermissionGranted = granted; if (granted) bindCamera() }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: SecurityException) { Toast.makeText(this, "Could not keep access to that folder", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
        getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE).edit().putString(MediaToolboxPrefs.KEY_SCANNER_FOLDER, uri.toString()).apply()
        selectedFolderName = folderName(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreScannerState(savedInstanceState)
        cameraPermissionGranted = hasCameraPermission()
        applyKeepScreenOnPreference()
        selectedFolderName = currentFolderName()
        setContent { MaterialTheme { ScannerApp(pages, cameraPermissionGranted, showingPreview, selectedFolderName, { cameraPermission.launch(Manifest.permission.CAMERA) }, { previewView = it; cameraBindRequested = true; if (cameraPermissionGranted && !showingPreview) bindCamera() }, ::capturePage, ::deletePage, { if (pages.isNotEmpty()) { cameraProvider?.unbindAll(); showingPreview = true } }, ::finish, { showingPreview = false; cameraBindRequested = true; if (cameraPermissionGranted) bindCamera() }, ::flipCamera, { folderPicker.launch(null) }, ::savePdf) } }
    }

    private fun restoreScannerState(savedInstanceState: Bundle?) {
        val restoredPaths = savedInstanceState?.getStringArrayList(STATE_PAGE_PATHS).orEmpty()
            .filter { path -> File(path).isFile && File(path).length() > 0L }
        pages.clear()
        pages.addAll(restoredPaths)
        showingPreview = savedInstanceState?.getBoolean(STATE_SHOWING_PREVIEW, false) == true && pages.isNotEmpty()
        lensFacing = savedInstanceState?.getInt(STATE_LENS_FACING, CameraSelector.LENS_FACING_BACK) ?: CameraSelector.LENS_FACING_BACK
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_PAGE_PATHS, ArrayList(pages))
        outState.putBoolean(STATE_SHOWING_PREVIEW, showingPreview)
        outState.putInt(STATE_LENS_FACING, lensFacing)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOnPreference()
        val granted = hasCameraPermission()
        if (cameraPermissionGranted != granted) cameraPermissionGranted = granted
        if (granted && cameraBindRequested && !showingPreview) bindCamera()
    }

    private fun applyKeepScreenOnPreference() {
        val keepOn = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE)
            .getBoolean(MediaToolboxPrefs.KEY_KEEP_SCREEN_ON, true)
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        val view = previewView ?: return; if (!hasCameraPermission() || showingPreview) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ try { val provider = future.get(); cameraProvider = provider; val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }; val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build(); val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build(); provider.unbindAll(); imageCapture = capture; provider.bindToLifecycle(this, selector, preview, capture) } catch (_: Exception) { imageCapture = null; runOnUiThread { Toast.makeText(this, "Scanner camera could not start", Toast.LENGTH_SHORT).show() } }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePage() {
        val capture = imageCapture ?: run { Toast.makeText(this, "Scanner camera is not ready", Toast.LENGTH_SHORT).show(); bindCamera(); return }
        val sessionDir = File(filesDir, SESSION_DIR).apply { mkdirs() }
        val file = File(sessionDir, "scan_${System.currentTimeMillis()}.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) { runOnUiThread { if (file.exists() && file.length() > 0L) pages.add(file.absolutePath) else { file.delete(); Toast.makeText(this@ScannerActivity, "Could not capture page", Toast.LENGTH_SHORT).show() } } }
            override fun onError(exception: ImageCaptureException) { file.delete(); runOnUiThread { Toast.makeText(this@ScannerActivity, "Could not capture page", Toast.LENGTH_SHORT).show() } }
        })
    }
    private fun deletePage(index: Int) { if (savingPdf.get()) return; if (index in pages.indices) File(pages.removeAt(index)).delete(); if (pages.isEmpty()) showingPreview = false }
    private fun flipCamera() { if (savingPdf.get()) return; lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK; bindCamera() }
    private fun currentFolderName(): String = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE).getString(MediaToolboxPrefs.KEY_SCANNER_FOLDER, null)?.let(Uri::parse)?.let(::folderName) ?: MediaToolboxPrefs.DEFAULT_SCANNER_FOLDER
    private fun folderName(uri: Uri): String = try { DocumentsContract.getTreeDocumentId(uri)?.substringAfterLast(':')?.let(Uri::decode)?.takeIf { it.isNotBlank() } ?: "Chosen folder" } catch (_: Exception) { "Chosen folder" }

    private fun savePdf() {
        if (!savingPdf.compareAndSet(false, true)) return
        val folder = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE).getString(MediaToolboxPrefs.KEY_SCANNER_FOLDER, null)?.let(Uri::parse)
        if (folder == null) { savingPdf.set(false); Toast.makeText(this, "Choose a folder first", Toast.LENGTH_SHORT).show(); folderPicker.launch(null); return }
        val pagePaths = pages.toList()
        if (pagePaths.isEmpty()) { savingPdf.set(false); return }
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
                runOnUiThread { Toast.makeText(this, "Could not save PDF: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show() }
            } finally {
                document?.close()
                savingPdf.set(false)
            }
        }.start()
    }
    private fun hasPersistedWriteAccess(uri: Uri): Boolean = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

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
        } catch (e: Exception) { document.close(); throw e }
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

    private fun clearPages() { pages.forEach { File(it).delete() }; pages.clear() }
    override fun onDestroy() { cameraProvider?.unbindAll(); cameraExecutor.shutdown(); if (isFinishing) clearPages(); super.onDestroy() }
}

@Composable private fun ScannerApp(pages: List<String>, hasCameraPermission: Boolean, showingPreview: Boolean, folderName: String, onRequestPermission: () -> Unit, onPreviewReady: (PreviewView) -> Unit, onCapture: () -> Unit, onDeletePage: (Int) -> Unit, onFinish: () -> Unit, onBack: () -> Unit, onBackToScanner: () -> Unit, onFlip: () -> Unit, onChooseFolder: () -> Unit, onSave: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) { when { !hasCameraPermission -> ScannerPermission(onRequestPermission, onBack); showingPreview -> ScannerPreview(pages, onBackToScanner, onDeletePage, folderName, onChooseFolder, onSave); else -> ScannerCapture(pages, onPreviewReady, onCapture, onDeletePage, onFinish, onBack, onFlip) } }
}
@Composable private fun ScannerPermission(onRequest: () -> Unit, onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).statusBarsPadding().navigationBarsPadding().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("Camera access is needed", color = androidx.compose.ui.graphics.Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp)); Text("The scanner uses the camera to capture pages. No camera image is uploaded.", color = androidx.compose.ui.graphics.Color.LightGray); Spacer(Modifier.height(20.dp)); Button(onClick = onRequest) { Text("Allow camera") }; Spacer(Modifier.height(8.dp)); Button(onClick = onBack) { Text("Back") } } }
@Composable private fun ScannerCapture(pages: List<String>, onPreviewReady: (PreviewView) -> Unit, onCapture: () -> Unit, onDelete: (Int) -> Unit, onFinish: () -> Unit, onBack: () -> Unit, onFlip: () -> Unit) { val context = LocalContext.current; Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) { AndroidView(factory = { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.PERFORMANCE; onPreviewReady(this) } }, modifier = Modifier.fillMaxSize()); Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) }; Text("Scanner", color = androidx.compose.ui.graphics.Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)); IconButton(onClick = onFlip) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = androidx.compose.ui.graphics.Color.White) } }; Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.82f)).navigationBarsPadding().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { if (pages.isNotEmpty()) { LazyRow(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { itemsIndexed(pages) { index, path -> Box(Modifier.size(68.dp)) { LocalImage(path, Modifier.fillMaxSize()); IconButton(onClick = { onDelete(index) }, modifier = Modifier.align(Alignment.TopEnd).size(25.dp)) { Icon(Icons.Default.Delete, "Remove page", tint = androidx.compose.ui.graphics.Color.White) } } } }; Spacer(Modifier.height(8.dp)) }; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Text("${pages.size} page${if (pages.size == 1) "" else "s"}", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(end = 18.dp)); Box(Modifier.size(72.dp).background(androidx.compose.ui.graphics.Color.White, CircleShape).padding(5.dp).clickable(onClick = onCapture), contentAlignment = Alignment.Center) { Box(Modifier.size(58.dp).background(androidx.compose.ui.graphics.Color.Black, CircleShape)) }; Spacer(Modifier.size(18.dp)); Button(onClick = onFinish, enabled = pages.isNotEmpty()) { Text("Finish") } } } } } }
@Composable private fun ScannerPreview(pages: List<String>, onBack: () -> Unit, onDelete: (Int) -> Unit, folderName: String, onChooseFolder: () -> Unit, onSave: () -> Unit) { Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).statusBarsPadding().navigationBarsPadding()) { Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to scanner", tint = androidx.compose.ui.graphics.Color.White) }; Text("Preview", color = androidx.compose.ui.graphics.Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text("${pages.size} page${if (pages.size == 1) "" else "s"}", color = androidx.compose.ui.graphics.Color.LightGray) }; LazyRow(Modifier.fillMaxWidth().height(94.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(pages) { index, path -> Box(Modifier.size(86.dp)) { LocalImage(path, Modifier.fillMaxSize()); IconButton(onClick = { onDelete(index) }, modifier = Modifier.align(Alignment.TopEnd).size(27.dp)) { Icon(Icons.Default.Delete, "Delete page", tint = androidx.compose.ui.graphics.Color.White) } } } }; Spacer(Modifier.height(10.dp)); Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { if (pages.isNotEmpty()) LocalImage(pages[0], Modifier.fillMaxWidth().aspectRatio(0.72f)) }; Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Icon(Icons.Default.Folder, "PDF folder", tint = androidx.compose.ui.graphics.Color.White); Spacer(Modifier.size(8.dp)); Text(folderName, color = androidx.compose.ui.graphics.Color.White, maxLines = 1, modifier = Modifier.weight(1f)); Button(onClick = onChooseFolder) { Text("Choose") } }; Spacer(Modifier.height(10.dp)); Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = pages.isNotEmpty()) { Icon(Icons.Default.PictureAsPdf, "Save PDF"); Spacer(Modifier.size(8.dp)); Text("Save PDF") } } } }
@Composable private fun LocalImage(path: String, modifier: Modifier) { val bitmap = androidx.compose.runtime.remember(path) { decodePreview(path) }; bitmap?.let { Image(it.asImageBitmap(), "Scanned page", modifier, contentScale = ContentScale.Fit) } }
private fun decodePreview(path: String): Bitmap? = try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
