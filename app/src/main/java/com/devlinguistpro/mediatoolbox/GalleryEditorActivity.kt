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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

private enum class EditTool(val title: String) {
    ADJUST("Adjust"), ROTATE("Rotate"), FLIP("Flip")
}

class GalleryEditorActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                GalleryEditorScreen(
                    uri = uri,
                    onCancel = { finish() },
                    onSave = { bitmap -> saveEditedImage(uri, bitmap) }
                )
            }
        }
    }

    private fun saveEditedImage(sourceUri: Uri, bitmap: Bitmap) {
        executor.execute {
            var destination: Uri? = null
            try {
                val sourceName = contentResolver.query(
                    sourceUri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0) ?: "IMG"
                        val relativePath = if (Build.VERSION.SDK_INT >= 29) cursor.getString(1) else null
                        name to relativePath
                    } else "IMG" to null
                } ?: ("IMG" to null)

                val originalName = sourceName.first.substringBeforeLast('.', sourceName.first)
                val timestamp = System.currentTimeMillis()
                val outputName = "${originalName}_edited_$timestamp.jpg"
                val relativePath = sourceName.second?.takeIf { it.isNotBlank() }
                    ?: Environment.DIRECTORY_PICTURES + "/Media Toolbox"

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                destination = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IOException("Unable to create output image")

                contentResolver.openOutputStream(destination, "w")?.use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                        throw IOException("Unable to encode output image")
                    }
                } ?: throw IOException("Unable to open output image")

                if (Build.VERSION.SDK_INT >= 29) {
                    contentResolver.update(
                        destination,
                        ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        },
                        null,
                        null
                    )
                }

                runOnUiThread {
                    Toast.makeText(this, "Edited photo saved", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                destination?.let { contentResolver.delete(it, null, null) }
                runOnUiThread {
                    Toast.makeText(this, "Could not save the edited photo", Toast.LENGTH_LONG).show()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "media_uri"
    }
}

@Composable
private fun GalleryEditorScreen(
    uri: Uri,
    onCancel: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf<Bitmap?>(null) }
    var tool by remember { mutableStateOf(EditTool.ADJUST) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val bitmap = withContext(Dispatchers.IO) { decodeForEditing(context, uri) }
        original = bitmap
        working = bitmap
    }

    val preview = remember(working, brightness, contrast, saturation) {
        working?.let { applyAdjustments(it, brightness, contrast, saturation) }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel, enabled = !busy) {
                    Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                }
                Text(
                    "Edit photo",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        preview?.let {
                            busy = true
                            onSave(it.copy(it.config ?: Bitmap.Config.ARGB_8888, false))
                        }
                    },
                    enabled = preview != null && !busy
                ) {
                    Icon(Icons.Default.Check, "Save", tint = Color.White)
                }
            }

            Box(
                Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                preview?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Edited photo preview",
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: Text("Loading photo…", color = Color.LightGray)
            }

            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EditTool.entries) { item ->
                    EditorToolButton(item, selected = item == tool) { tool = item }
                }
            }

            when (tool) {
                EditTool.ADJUST -> AdjustControls(
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    onBrightness = { brightness = it },
                    onContrast = { contrast = it },
                    onSaturation = { saturation = it }
                )
                EditTool.ROTATE -> RotateControls(
                    onLeft = { working = rotate(working ?: return@RotateControls, -90f) },
                    onRight = { working = rotate(working ?: return@RotateControls, 90f) }
                )
                EditTool.FLIP -> FlipControls(
                    onFlipHorizontal = { working = flip(working ?: return@FlipControls, horizontal = true) },
                    onFlipVertical = { working = flip(working ?: return@FlipControls, horizontal = false) }
                )
            }
        }
    }
}

@Composable
private fun EditorToolButton(tool: EditTool, selected: Boolean, onClick: () -> Unit) {
    val icon = when (tool) {
        EditTool.ADJUST -> Icons.Default.Tune
        EditTool.ROTATE -> Icons.Default.RotateRight
        EditTool.FLIP -> Icons.Default.Flip
    }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, tool.title, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(tool.title, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun AdjustControls(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        AdjustmentRow("Brightness", brightness, -1f, 1f, onBrightness)
        AdjustmentRow("Contrast", contrast, 0.5f, 1.5f, onContrast)
        AdjustmentRow("Saturation", saturation, 0f, 2f, onSaturation)
    }
}

@Composable
private fun AdjustmentRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.size(width = 85.dp, height = 20.dp))
        Slider(value = value, onValueChange = onChange, valueRange = min..max, modifier = Modifier.weight(1f))
        Text(
            if (label == "Brightness") "${(value * 100).roundToInt()}" else "${(value * 100).roundToInt()}%",
            color = Color.LightGray,
            fontSize = 11.sp,
            modifier = Modifier.size(width = 40.dp, height = 20.dp)
        )
    }
}

@Composable
private fun RotateControls(onLeft: () -> Unit, onRight: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = onLeft) {
            Icon(Icons.Default.RotateLeft, "Rotate left")
            Spacer(Modifier.size(6.dp))
            Text("Left")
        }
        Spacer(Modifier.size(12.dp))
        Button(onClick = onRight) {
            Icon(Icons.Default.RotateRight, "Rotate right")
            Spacer(Modifier.size(6.dp))
            Text("Right")
        }
    }
}

@Composable
private fun FlipControls(onFlipHorizontal: () -> Unit, onFlipVertical: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = onFlipHorizontal) { Text("Flip horizontal") }
        Spacer(Modifier.size(12.dp))
        Button(onClick = onFlipVertical) { Text("Flip vertical") }
    }
}

private fun decodeForEditing(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxDimension = 2048
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) {
        null
    }
}

private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun flip(bitmap: Bitmap, horizontal: Boolean): Bitmap {
    val matrix = Matrix().apply { postScale(if (horizontal) -1f else 1f, if (horizontal) 1f else -1f) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun applyAdjustments(
    source: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float
): Bitmap {
    if (brightness == 0f && contrast == 1f && saturation == 1f) return source

    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val matrix = ColorMatrix()
    matrix.setSaturation(saturation)

    val scale = contrast
    val translate = (-0.5f * scale + 0.5f + brightness) * 255f
    val contrastMatrix = ColorMatrix(floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
    ))
    matrix.postConcat(contrastMatrix)
    val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return result
}
