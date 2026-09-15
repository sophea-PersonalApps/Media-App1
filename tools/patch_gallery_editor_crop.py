from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryEditorActivity.kt")
text = path.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str):
    global text
    if old not in text:
        raise SystemExit(f"{label}: pattern not found")
    text = text.replace(old, new, 1)

replace_once(
'''private fun saveEdited(sourceUri: Uri, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean) {''',
'''private fun saveEdited(sourceUri: Uri, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean, cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float) {''',
"save callback signature")
replace_once(
'''edited = editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical)''',
'''edited = editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom)''',
"save crop arguments")
replace_once(
'''private fun GalleryEditor(uri: Uri, onSave: (Uri, Float, Float, Float, Float, Boolean, Boolean) -> Unit, onCancel: () -> Unit) {''',
'''private fun GalleryEditor(uri: Uri, onSave: (Uri, Float, Float, Float, Float, Boolean, Boolean, Float, Float, Float, Float) -> Unit, onCancel: () -> Unit) {''',
"editor callback signature")
replace_once(
'''    var flipHorizontal by remember { mutableStateOf(false) }\n    var flipVertical by remember { mutableStateOf(false) }''',
'''    var flipHorizontal by remember { mutableStateOf(false) }\n    var flipVertical by remember { mutableStateOf(false) }\n    var cropLeft by remember { mutableFloatStateOf(0f) }\n    var cropTop by remember { mutableFloatStateOf(0f) }\n    var cropRight by remember { mutableFloatStateOf(1f) }\n    var cropBottom by remember { mutableFloatStateOf(1f) }''',
"crop state")
replace_once(
'''LaunchedEffect(originalPreview, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) {''',
'''LaunchedEffect(originalPreview, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom) {''',
"crop preview effect")
replace_once(
'''val generated = withContext(Dispatchers.Default) { runCatching { editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) }.getOrNull() }''',
'''val generated = withContext(Dispatchers.Default) { runCatching { editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom) }.getOrNull() }''',
"crop preview transform")
replace_once(
'''Button(onClick = { onSave(uri, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical) }, enabled = !loading && preview != null) {''',
'''Button(onClick = { onSave(uri, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom) }, enabled = !loading && preview != null) {''',
"save crop values")
replace_once(
'''                Spacer(Modifier.height(6.dp))\n                Button(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; rotation = 0f; flipHorizontal = false; flipVertical = false }, modifier = Modifier.fillMaxWidth()) { Text("Reset edits") }''',
'''                Spacer(Modifier.height(6.dp))\n                Text("Crop", color = Color.White, fontWeight = FontWeight.SemiBold)\n                Text("Left", color = Color.LightGray, fontSize = 12.sp)\n                Slider(value = cropLeft, onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.05f) }, valueRange = 0f..0.75f)\n                Text("Top", color = Color.LightGray, fontSize = 12.sp)\n                Slider(value = cropTop, onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.05f) }, valueRange = 0f..0.75f)\n                Text("Right", color = Color.LightGray, fontSize = 12.sp)\n                Slider(value = cropRight, onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.05f) }, valueRange = 0.25f..1f)\n                Text("Bottom", color = Color.LightGray, fontSize = 12.sp)\n                Slider(value = cropBottom, onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.05f) }, valueRange = 0.25f..1f)\n                Spacer(Modifier.height(6.dp))\n                Button(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; rotation = 0f; flipHorizontal = false; flipVertical = false; cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f }, modifier = Modifier.fillMaxWidth()) { Text("Reset edits") }''',
"crop controls")
replace_once(
'''private fun editBitmap(source: Bitmap, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean): Bitmap {''',
'''private fun editBitmap(source: Bitmap, brightness: Float, contrast: Float, saturation: Float, rotation: Float, flipHorizontal: Boolean, flipVertical: Boolean, cropLeft: Float = 0f, cropTop: Float = 0f, cropRight: Float = 1f, cropBottom: Float = 1f): Bitmap {''',
"edit bitmap signature")
replace_once(
'''    canvas.drawBitmap(transformed, 0f, 0f, paint)\n    if (transformed !== source) transformed.recycle()\n    return output''',
'''    canvas.drawBitmap(transformed, 0f, 0f, paint)\n    if (transformed !== source) transformed.recycle()\n    val left = (output.width * cropLeft.coerceIn(0f, 0.95f)).toInt()\n    val top = (output.height * cropTop.coerceIn(0f, 0.95f)).toInt()\n    val right = (output.width * cropRight.coerceIn(0.05f, 1f)).toInt().coerceAtLeast(left + 1)\n    val bottom = (output.height * cropBottom.coerceIn(0.05f, 1f)).toInt().coerceAtLeast(top + 1)\n    val cropped = if (left == 0 && top == 0 && right == output.width && bottom == output.height) output else Bitmap.createBitmap(output, left, top, (right - left).coerceAtMost(output.width - left), (bottom - top).coerceAtMost(output.height - top))\n    if (cropped !== output) output.recycle()\n    return cropped''',
"apply crop")

checks = {
    "crop state": "var cropLeft by remember" in text and "var cropBottom by remember" in text,
    "crop controls": 'Text("Crop", color = Color.White' in text and 'Slider(value = cropLeft' in text,
    "crop save": "cropLeft, cropTop, cropRight, cropBottom" in text,
    "crop processing": "Bitmap.createBitmap(output, left, top" in text,
}
failed = [k for k,v in checks.items() if not v]
for k,v in checks.items(): print(("PASS " if v else "FAIL ") + k)
if failed: raise SystemExit("CROP AUDIT FAILED: " + "; ".join(failed))
path.write_text(text, encoding="utf-8")
print("Gallery editor crop controls and crop processing added.")
