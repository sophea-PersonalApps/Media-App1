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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
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

class GalleryEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getParcelableExtra<Uri>(EXTRA_URI)
        setContent { MaterialTheme { GalleryEditor(uri, ::saveEdited, ::finish) } }
    }

    private fun saveEdited(bitmap: Bitmap) {
        Thread {
            try {
                val resolver = contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Edited_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Media Toolbox")
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create image")
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) error("Could not encode image")
                    } ?: error("Could not open image")
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                runOnUiThread { Toast.makeText(this, "Edited photo saved", Toast.LENGTH_SHORT).show(); finish() }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, "Could not save edited photo", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    companion object { const val EXTRA_URI = "source_uri" }
}

@Composable
private fun GalleryEditor(uri: Uri?, onSave: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        original = withContext(Dispatchers.IO) {
            uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) }.getOrNull() }
        }
    }

    val preview = remember(original, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) {
        original?.let { editBitmap(it, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel", tint = Color.White) }
                Text("Edit", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().weight(1f))
                IconButton(onClick = { preview?.let(onSave) }, enabled = preview != null) {
                    Icon(Icons.Default.Check, "Save", tint = Color.White)
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                preview?.let { Image(it.asImageBitmap(), "Edited photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }

            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Brightness", color = Color.White)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -1f..1f)
                Text("Contrast", color = Color.White)
                Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.5f)
                Text("Saturation", color = Color.White)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { rotation = (rotation - 90f) % 360f }) {
                        Icon(Icons.Default.RotateLeft, "Rotate left"); Spacer(Modifier.size(4.dp)); Text("Left")
                    }
                    Button(onClick = { rotation = (rotation + 90f) % 360f }) {
                        Icon(Icons.Default.RotateRight, "Rotate right"); Spacer(Modifier.size(4.dp)); Text("Right")
                    }
                    Button(onClick = { flipHorizontal = !flipHorizontal }) {
                        Icon(Icons.Default.Flip, "Flip horizontal"); Text("H")
                    }
                    Button(onClick = { flipVertical = !flipVertical }) {
                        Icon(Icons.Default.Flip, "Flip vertical"); Text("V")
                    }
                }
            }
        }
    }
}

private fun editBitmap(source: Bitmap, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean): Bitmap {
    val matrix = Matrix().apply {
        postRotate(rotation)
        postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f)
    }
    val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    val output = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val cm = ColorMatrix().apply {
        setSaturation(saturation)
        val scale = contrast
        postConcat(ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, brightness * 255f,
            0f, scale, 0f, 0f, brightness * 255f,
            0f, 0f, scale, 0f, brightness * 255f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
    val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(transformed, 0f, 0f, paint)
    if (transformed !== source) transformed.recycle()
    return output
}
