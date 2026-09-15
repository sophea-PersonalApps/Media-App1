from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryEditorActivity.kt")
text = path.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str):
    global text
    if old not in text:
        raise SystemExit(f"{label}: pattern not found")
    text = text.replace(old, new, 1)

replace_once('''private fun saveEdited(sourceUri: Uri, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean) {''', '''private fun saveEdited(sourceUri: Uri, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean, cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float) {''', "save callback signature")
replace_once('''edited = editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical)''', '''edited = editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom)''', "save crop arguments")
replace_once('''private fun GalleryEditor(uri: Uri, onSave: (Uri, Float, Float, Float, Float, Boolean, Boolean) -> Unit, onCancel: () -> Unit) {''', '''private fun GalleryEditor(uri: Uri, onSave: (Uri, Float, Float, Float, Float, Boolean, Boolean, Float, Float, Float, Float) -> Unit, onCancel: () -> Unit) {''', "editor callback signature")
replace_once('''    var flipHorizontal by remember { mutableStateOf(false) }\n    var flipVertical by remember { mutableStateOf(false) }''', '''    var flipHorizontal by remember { mutableStateOf(false) }\n    var flipVertical by remember { mutableStateOf(false) }\n    var cropMode by remember { mutableStateOf(false) }\n    var cropLeft by remember { mutableFloatStateOf(0f) }\n    var cropTop by remember { mutableFloatStateOf(0f) }\n    var cropRight by remember { mutableFloatStateOf(1f) }\n    var cropBottom by remember { mutableFloatStateOf(1f) }''', "crop state")
replace_once('''LaunchedEffect(originalPreview, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) {''', '''LaunchedEffect(originalPreview, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom) {''', "crop preview effect")
replace_once('''val generated = withContext(Dispatchers.Default) { runCatching { editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) }.getOrNull() }''', '''val generated = withContext(Dispatchers.Default) { runCatching { editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom) }.getOrNull() }''', "crop preview transform")
replace_once('''Button(onClick = { onSave(uri, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) }, enabled = !loading && preview != null) {''', '''Button(onClick = { onSave(uri, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom) }, enabled = !loading && preview != null) {''', "save crop values")

old_controls = '''                Spacer(Modifier.height(6.dp))\n                Button(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; rotation = 0f; flipHorizontal = false; flipVertical = false }, modifier = Modifier.fillMaxWidth()) { Text("Reset edits") }'''
new_controls = '''                Spacer(Modifier.height(6.dp))\n                Button(onClick = { cropMode = !cropMode }, modifier = Modifier.fillMaxWidth()) { Text(if (cropMode) "Done Crop" else "Crop") }\n                Spacer(Modifier.height(6.dp))\n                Button(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; rotation = 0f; flipHorizontal = false; flipVertical = false; cropMode = false; cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f }, modifier = Modifier.fillMaxWidth()) { Text("Reset edits") }'''
replace_once(old_controls, new_controls, "crop button")

old_preview = '''            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {\n                when {\n                    preview != null -> Image(preview!!.asImageBitmap(), "Edited photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)\n                    loading -> Text("Loading photo…", color = Color.LightGray)\n                    else -> Text("Photo could not be loaded", color = Color.LightGray)\n                }\n            }'''
new_preview = '''            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {\n                when {\n                    preview != null -> {\n                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n                            Image(preview!!.asImageBitmap(), "Edited photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)\n                            if (cropMode) {\n                                CropOverlay(cropLeft, cropTop, cropRight, cropBottom) { left, top, right, bottom ->\n                                    cropLeft = left\n                                    cropTop = top\n                                    cropRight = right\n                                    cropBottom = bottom\n                                }\n                            }\n                        }\n                    }\n                    loading -> Text("Loading photo…", color = Color.LightGray)\n                    else -> Text("Photo could not be loaded", color = Color.LightGray)\n                }\n            }'''
replace_once(old_preview, new_preview, "crop preview")

marker = '''private fun decodeUriScaled(resolver: android.content.ContentResolver, uri: Uri, maxSide: Int): Bitmap? {'''
crop_overlay = r'''
@Composable
private fun CropOverlay(
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    onChange: (Float, Float, Float, Float) -> Unit
) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val left = size.width * cropLeft
        val top = size.height * cropTop
        val right = size.width * cropRight
        val bottom = size.height * cropBottom
        val stroke = 2.dp.toPx()
        drawRect(Color.White, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        val handle = 18.dp.toPx()
        val handleStroke = 4.dp.toPx()
        val corners = listOf(
            androidx.compose.ui.geometry.Offset(left, top),
            androidx.compose.ui.geometry.Offset(right, top),
            androidx.compose.ui.geometry.Offset(left, bottom),
            androidx.compose.ui.geometry.Offset(right, bottom)
        )
        corners.forEach { point ->
            drawLine(Color.White, point.copy(x = point.x + if (point.x == left) handle else -handle), point, strokeWidth = handleStroke)
            drawLine(Color.White, point.copy(y = point.y + if (point.y == top) handle else -handle), point, strokeWidth = handleStroke)
        }
    }.pointerInput(cropLeft, cropTop, cropRight, cropBottom) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x = down.position.x / size.width
                val y = down.position.y / size.height
                val edge = 0.07f
                val nearLeft = kotlin.math.abs(x - cropLeft) < edge
                val nearRight = kotlin.math.abs(x - cropRight) < edge
                val nearTop = kotlin.math.abs(y - cropTop) < edge
                val nearBottom = kotlin.math.abs(y - cropBottom) < edge
                if (!(nearLeft || nearRight || nearTop || nearBottom)) continue
                var left = cropLeft
                var top = cropTop
                var right = cropRight
                var bottom = cropBottom
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    val px = (change.position.x / size.width).coerceIn(0f, 1f)
                    val py = (change.position.y / size.height).coerceIn(0f, 1f)
                    if (nearLeft) left = px.coerceIn(0f, right - 0.05f)
                    if (nearRight) right = px.coerceIn(left + 0.05f, 1f)
                    if (nearTop) top = py.coerceIn(0f, bottom - 0.05f)
                    if (nearBottom) bottom = py.coerceIn(top + 0.05f, 1f)
                    onChange(left, top, right, bottom)
                    change.consume()
                }
            }
        }
    }
}

'''
replace_once(marker, crop_overlay + marker, "crop overlay insertion")
replace_once('''private fun editBitmap(source: Bitmap, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean): Bitmap {''', '''private fun editBitmap(source: Bitmap, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean, cropLeft: Float = 0f, cropTop: Float = 0f, cropRight: Float = 1f, cropBottom: Float = 1f): Bitmap {''', "edit bitmap signature")
replace_once('''    canvas.drawBitmap(transformed, 0f, 0f, paint)\n    if (transformed !== source) transformed.recycle()\n    return output''', '''    canvas.drawBitmap(transformed, 0f, 0f, paint)\n    if (transformed !== source) transformed.recycle()\n    val left = (output.width * cropLeft.coerceIn(0f, 0.95f)).toInt()\n    val top = (output.height * cropTop.coerceIn(0f, 0.95f)).toInt()\n    val right = (output.width * cropRight.coerceIn(0.05f, 1f)).toInt().coerceAtLeast(left + 1)\n    val bottom = (output.height * cropBottom.coerceIn(0.05f, 1f)).toInt().coerceAtLeast(top + 1)\n    val cropped = if (left == 0 && top == 0 && right == output.width && bottom == output.height) output else Bitmap.createBitmap(output, left, top, (right - left).coerceAtMost(output.width - left), (bottom - top).coerceAtMost(output.height - top))\n    if (cropped !== output) output.recycle()\n    return cropped''', "apply crop")

checks = {
    "crop mode": "var cropMode by remember" in text,
    "crop button": 'Text(if (cropMode) "Done Crop" else "Crop")' in text,
    "visible photo": 'Image(preview!!.asImageBitmap()' in text and 'if (cropMode)' in text,
    "crop rectangle": "CropOverlay(" in text and "drawRect(Color.White" in text,
    "drag crop edges": "nearLeft" in text and "nearRight" in text and "nearTop" in text and "nearBottom" in text,
    "crop processing": "Bitmap.createBitmap(output, left, top" in text,
}
failed = [k for k,v in checks.items() if not v]
for k,v in checks.items(): print(("PASS " if v else "FAIL ") + k)
if failed: raise SystemExit("CROP AUDIT FAILED: " + "; ".join(failed))
path.write_text(text, encoding="utf-8")
print("Gallery editor crop replaced with an image-visible crop mode using a draggable crop rectangle and edge/corner handles.")
