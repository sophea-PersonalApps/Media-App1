package com.devlinguistpro.mediatoolbox

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.AtomicBoolean

class GalleryEditorActivity : ComponentActivity() {
    private val saving = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getParcelableExtra<Uri>(EXTRA_URI)
        if (uri == null) {
            Toast.makeText(this, "Photo could not be opened", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent { MaterialTheme { GalleryEditor(uri, ::saveEdited, ::finish) } }
    }

    private fun saveEdited(sourceUri: Uri, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean) {
        if (!saving.compareAndSet(false, true)) return
        Thread {
            var outputUri: Uri? = null
            var source: Bitmap? = null
            var edited: Bitmap? = null
            try {
                source = decodeUriScaled(contentResolver, sourceUri, 4096) ?: throw IOException("Could not read source photo")
                edited = editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Edited_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Media Toolbox")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                outputUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Could not create image")
                contentResolver.openOutputStream(outputUri)?.use { output ->
                    if (!edited.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw IOException("Could not encode image")
                    output.flush()
                } ?: throw IOException("Could not open image")
                if (Build.VERSION.SDK_INT >= 29) {
                    if (contentResolver.update(outputUri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) != 1) throw IOException("Could not finish saving image")
                }
                runOnUiThread { Toast.makeText(this, "Edited photo saved", Toast.LENGTH_SHORT).show(); finish() }
            } catch (e: Exception) {
                outputUri?.let { runCatching { contentResolver.delete(it, null, null) } }
                runOnUiThread { Toast.makeText(this, "Could not save edited photo: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show() }
            } finally {
                edited?.let { if (!it.isRecycled) it.recycle() }
                source?.let { if (!it.isRecycled) it.recycle() }
                saving.set(false)
            }
        }.start()
    }

    companion object { const val EXTRA_URI = "source_uri" }
}

@Composable
private fun GalleryEditor(uri: Uri, onSave: (Uri, Float, Float, Float, Float, Boolean, Boolean) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var originalPreview by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        loading = true
        originalPreview?.let { if (!it.isRecycled) it.recycle() }
        preview?.let { if (!it.isRecycled) it.recycle() }
        originalPreview = null
        preview = null
        originalPreview = withContext(Dispatchers.IO) { runCatching { decodeUriScaled(context.contentResolver, uri, 1600) }.getOrNull() }
        loading = false
    }

    LaunchedEffect(originalPreview, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) {
        val source = originalPreview ?: return@LaunchedEffect
        val generated = withContext(Dispatchers.Default) { runCatching { editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) }.getOrNull() }
        preview?.let { old -> if (!old.isRecycled && old !== generated) old.recycle() }
        preview = generated
    }

    DisposableEffect(Unit) {
        onDispose {
            originalPreview?.let { if (!it.isRecycled) it.recycle() }
            preview?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel", tint = Color.White) }
                Text("Edit", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Button(onClick = { onSave(uri, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) }, enabled = !loading && preview != null) {
                    Icon(Icons.Default.Check, "Save"); Spacer(Modifier.size(5.dp)); Text("Save")
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when {
                    preview != null -> Image(preview!!.asImageBitmap(), "Edited photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    loading -> Text("Loading photo…", color = Color.LightGray)
                    else -> Text("Photo could not be loaded", color = Color.LightGray)
                }
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Brightness", color = Color.White)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -1f..1f)
                Text("Contrast", color = Color.White)
                Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.5f)
                Text("Saturation", color = Color.White)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { rotation = (rotation - 90f) % 360f }) { Icon(Icons.Default.RotateLeft, "Rotate left"); Spacer(Modifier.size(4.dp)); Text("Left") }
                    Button(onClick = { rotation = (rotation + 90f) % 360f }) { Icon(Icons.Default.RotateRight, "Rotate right"); Spacer(Modifier.size(4.dp)); Text("Right") }
                    Button(onClick = { flipHorizontal = !flipHorizontal }) { Icon(Icons.Default.Flip, "Flip horizontal"); Spacer(Modifier.size(4.dp)); Text("H") }
                    Button(onClick = { flipVertical = !flipVertical }) { Icon(Icons.Default.Flip, "Flip vertical"); Spacer(Modifier.size(4.dp)); Text("V") }
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; rotation = 0f; flipHorizontal = false; flipVertical = false }, modifier = Modifier.fillMaxWidth()) { Text("Reset edits") }
            }
        }
    }
}

private fun decodeUriScaled(resolver: android.content.ContentResolver, uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    return resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, options) }
}

private fun editBitmap(source: Bitmap, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean): Bitmap {
    val matrix = Matrix().apply { postRotate(rotation); postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f) }
    val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    val output = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val cm = ColorMatrix().apply {
        setSaturation(saturation)
        val scale = contrast
        postConcat(ColorMatrix(floatArrayOf(scale, 0f, 0f, 0f, brightness * 255f, 0f, scale, 0f, 0f, brightness * 255f, 0f, 0f, scale, 0f, brightness * 255f, 0f, 0f, 0f, 1f, 0f)))
    }
    val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(transformed, 0f, 0f, paint)
    if (transformed !== source) transformed.recycle()
    return output
}
