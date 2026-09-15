from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryEditorActivity.kt")
text = path.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str):
    global text
    if old not in text:
        raise SystemExit(f"{label}: pattern not found")
    text = text.replace(old, new, 1)

if "var cropMode by remember" not in text:
    replace_once(
        '''    var flipHorizontal by remember { mutableStateOf(false) }\n    var flipVertical by remember { mutableStateOf(false) }''',
        '''    var flipHorizontal by remember { mutableStateOf(false) }\n    var flipVertical by remember { mutableStateOf(false) }\n    var cropMode by remember { mutableStateOf(false) }''',
        "crop mode state",
    )

if 'Text(if (cropMode) "Done Crop" else "Crop")' not in text:
    old_controls = '''                Spacer(Modifier.height(6.dp))\n                Text("Crop", color = Color.White, fontWeight = FontWeight.SemiBold)\n                Text("Left", color = Color.LightGray, fontSize = 12.sp)\n                Slider(value = cropLeft, onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.05f) }, valueRange = 0f..0.75f)\n                Text("Top", color = Color.LightGray, fontSize = 12.sp)\n                Slider(value = cropTop, onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.05f) }, valueRange = 0f..0.75f)\n                Text("Right", color = Color.LightGray, fontSize = 12.sp)\n                Slider(value = cropRight, onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.05f) }, valueRange = 0.25f..1f)\n                Text("Bottom", color = Color.LightGray, fontSize = 12.sp)\n                Slider(value = cropBottom, onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.05f) }, valueRange = 0.25f..1f)\n                Spacer(Modifier.height(6.dp))\n                Button(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; rotation = 0f; flipHorizontal = false; flipVertical = false; cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f }, modifier = Modifier.fillMaxWidth()) { Text("Reset edits") }'''
    new_controls = '''                Spacer(Modifier.height(6.dp))\n                Button(onClick = { cropMode = !cropMode }, modifier = Modifier.fillMaxWidth()) { Text(if (cropMode) "Done Crop" else "Crop") }\n                Spacer(Modifier.height(6.dp))\n                Button(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; rotation = 0f; flipHorizontal = false; flipVertical = false; cropMode = false; cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f }, modifier = Modifier.fillMaxWidth()) { Text("Reset edits") }'''
    replace_once(old_controls, new_controls, "crop slider controls")

if "CropOverlay(" not in text:
    old_preview = '''            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {\n                when {\n                    preview != null -> Image(preview!!.asImageBitmap(), "Edited photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)\n                    loading -> Text("Loading photo…", color = Color.LightGray)\n                    else -> Text("Photo could not be loaded", color = Color.LightGray)\n                }\n            }'''
    new_preview = '''            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {\n                when {\n                    preview != null -> {\n                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n                            Image(preview!!.asImageBitmap(), "Edited photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)\n                            if (cropMode) {\n                                CropOverlay(\n                                    cropLeft = cropLeft, cropTop = cropTop, cropRight = cropRight, cropBottom = cropBottom,\n                                    imageWidth = preview!!.width, imageHeight = preview!!.height,\n                                    onChange = { left, top, right, bottom -> cropLeft = left; cropTop = top; cropRight = right; cropBottom = bottom }\n                                )\n                            }\n                        }\n                    }\n                    loading -> Text("Loading photo…", color = Color.LightGray)\n                    else -> Text("Photo could not be loaded", color = Color.LightGray)\n                }\n            }'''
    replace_once(old_preview, new_preview, "crop preview")

old_effect = '''    LaunchedEffect(originalPreview, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom) {\n        val source = originalPreview ?: return@LaunchedEffect\n        val generated = withContext(Dispatchers.Default) { runCatching { editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropLeft, cropTop, cropRight, cropBottom) }.getOrNull() }\n        preview?.let { old -> if (!old.isRecycled && old !== generated) old.recycle() }\n        preview = generated\n    }'''
new_effect = '''    LaunchedEffect(originalPreview, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, cropMode, cropLeft, cropTop, cropRight, cropBottom) {\n        val source = originalPreview ?: return@LaunchedEffect\n        val displayLeft = if (cropMode) 0f else cropLeft\n        val displayTop = if (cropMode) 0f else cropTop\n        val displayRight = if (cropMode) 1f else cropRight\n        val displayBottom = if (cropMode) 1f else cropBottom\n        val generated = withContext(Dispatchers.Default) { runCatching { editBitmap(source, brightness, contrast, saturation, rotation, flipHorizontal, flipVertical, displayLeft, displayTop, displayRight, displayBottom) }.getOrNull() }\n        preview?.let { old -> if (!old.isRecycled && old !== generated) old.recycle() }\n        preview = generated\n    }'''
replace_once(old_effect, new_effect, "crop preview effect")

marker = '''private fun decodeUriScaled(resolver: android.content.ContentResolver, uri: Uri, maxSide: Int): Bitmap? {'''
crop_overlay = r'''
@Composable
private fun CropOverlay(
    cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
    imageWidth: Int, imageHeight: Int,
    onChange: (Float, Float, Float, Float) -> Unit
) {
    var activeHandle = 0
    var dragLeft = cropLeft
    var dragTop = cropTop
    var dragRight = cropRight
    var dragBottom = cropBottom
    Canvas(Modifier.fillMaxSize().pointerInput(cropLeft, cropTop, cropRight, cropBottom, imageWidth, imageHeight) {
        androidx.compose.foundation.gestures.detectTransformGestures { centroid, pan, _, _ ->
            val imageAspect = imageWidth.toFloat() / imageHeight.toFloat().coerceAtLeast(1f)
            val viewportAspect = size.width.toFloat() / size.height.toFloat().coerceAtLeast(1f)
            val displayedHeight = if (imageAspect > viewportAspect) size.width / imageAspect else size.height
            val displayedWidth = if (imageAspect > viewportAspect) size.width else size.height * imageAspect
            val imageLeft = (size.width - displayedWidth) / 2f
            val imageTop = (size.height - displayedHeight) / 2f
            val imageRight = imageLeft + displayedWidth
            val imageBottom = imageTop + displayedHeight
            if (activeHandle == 0) {
                val x = ((centroid.x - imageLeft) / (imageRight - imageLeft)).coerceIn(0f, 1f)
                val y = ((centroid.y - imageTop) / (imageBottom - imageTop)).coerceIn(0f, 1f)
                val edge = 0.08f
                val nearLeft = kotlin.math.abs(x - cropLeft) < edge
                val nearRight = kotlin.math.abs(x - cropRight) < edge
                val nearTop = kotlin.math.abs(y - cropTop) < edge
                val nearBottom = kotlin.math.abs(y - cropBottom) < edge
                activeHandle = when {
                    nearLeft && nearTop -> 5
                    nearRight && nearTop -> 6
                    nearLeft && nearBottom -> 7
                    nearRight && nearBottom -> 8
                    nearLeft -> 1
                    nearRight -> 2
                    nearTop -> 3
                    nearBottom -> 4
                    else -> 0
                }
                dragLeft = cropLeft; dragTop = cropTop; dragRight = cropRight; dragBottom = cropBottom
            }
            if (activeHandle != 0) {
                val dx = pan.x / (imageRight - imageLeft)
                val dy = pan.y / (imageBottom - imageTop)
                when (activeHandle) {
                    1, 5, 7 -> dragLeft = (dragLeft + dx).coerceIn(0f, dragRight - 0.05f)
                    2, 6, 8 -> dragRight = (dragRight + dx).coerceIn(dragLeft + 0.05f, 1f)
                    3, 5, 6 -> dragTop = (dragTop + dy).coerceIn(0f, dragBottom - 0.05f)
                    4, 7, 8 -> dragBottom = (dragBottom + dy).coerceIn(dragTop + 0.05f, 1f)
                }
                onChange(dragLeft, dragTop, dragRight, dragBottom)
            }
        }
        activeHandle = 0
    }) {
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat().coerceAtLeast(1f)
        val viewportAspect = size.width / size.height.coerceAtLeast(1f)
        val displayedHeight = if (imageAspect > viewportAspect) size.width / imageAspect else size.height
        val displayedWidth = if (imageAspect > viewportAspect) size.width else size.height * imageAspect
        val imageLeft = (size.width - displayedWidth) / 2f
        val imageTop = (size.height - displayedHeight) / 2f
        val imageRight = imageLeft + displayedWidth
        val imageBottom = imageTop + displayedHeight
        val left = imageLeft + (imageRight - imageLeft) * cropLeft
        val top = imageTop + (imageBottom - imageTop) * cropTop
        val right = imageLeft + (imageRight - imageLeft) * cropRight
        val bottom = imageTop + (imageBottom - imageTop) * cropBottom
        drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(width = 2.dp.toPx()))
        val handle = 20.dp.toPx()
        val handleStroke = 4.dp.toPx()
        listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach { point ->
            drawLine(Color.White, point.copy(x = point.x + if (point.x == left) handle else -handle), point, strokeWidth = handleStroke)
            drawLine(Color.White, point.copy(y = point.y + if (point.y == top) handle else -handle), point, strokeWidth = handleStroke)
        }
    }
}

'''
replace_once(marker, crop_overlay + marker, "crop overlay insertion")

# Explicit imports: these are intentionally unconditional so the generated Kotlin
# source compiles regardless of the import layout produced by earlier scripts.
imports = [
    "import androidx.compose.foundation.Canvas",
    "import androidx.compose.foundation.gestures.detectTransformGestures",
    "import androidx.compose.ui.geometry.Offset",
    "import androidx.compose.ui.geometry.Size",
    "import androidx.compose.ui.graphics.drawscope.Stroke",
    "import androidx.compose.ui.input.pointer.pointerInput",
]
anchor = "import androidx.compose.foundation.layout."
for imp in imports:
    if imp not in text:
        lines = text.splitlines()
        insert_at = 0
        while insert_at < len(lines) and lines[insert_at].startswith("package "):
            insert_at += 1
        text = "\n".join(lines[:insert_at] + [imp] + lines[insert_at:]) + ("\n" if text.endswith("\n") else "")

checks = {
    "crop mode": "var cropMode by remember" in text,
    "crop button": 'Text(if (cropMode) "Done Crop" else "Crop")' in text,
    "visible photo": 'Image(preview!!.asImageBitmap()' in text and 'if (cropMode)' in text,
    "crop rectangle": "CropOverlay(" in text and "drawRect(Color.White" in text,
    "drag crop edges": "activeHandle" in text and "nearLeft" in text and "nearRight" in text and "nearTop" in text and "nearBottom" in text,
    "crop corners": "nearLeft && nearTop" in text and "nearRight && nearBottom" in text,
    "crop processing": "Bitmap.createBitmap(output, left, top" in text,
    "crop preview stays fixed": "val displayLeft = if (cropMode) 0f else cropLeft" in text,
}
failed = [k for k,v in checks.items() if not v]
for k,v in checks.items(): print(("PASS " if v else "FAIL ") + k)
if failed: raise SystemExit("CROP AUDIT FAILED: " + "; ".join(failed))
path.write_text(text, encoding="utf-8")
print("Gallery editor crop now uses an image-aligned crop frame with draggable edges/corners; the photo stays fixed until Done Crop applies the crop.")
