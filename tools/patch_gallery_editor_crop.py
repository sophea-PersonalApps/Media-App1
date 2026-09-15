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
    var dragLeft = cropLeft
    var dragTop = cropTop
    var dragRight = cropRight
    var dragBottom = cropBottom

    androidx.compose.foundation.Canvas(
        Modifier.fillMaxSize().pointerInput(cropLeft, cropTop, cropRight, cropBottom) {
            var activeHandle = 0
            androidx.compose.foundation.gestures.detectTransformGestures { centroid, pan, _, _ ->
                if (activeHandle == 0) {
                    val x = centroid.x / size.width
                    val y = centroid.y / size.height
                    val edge = 0.07f
                    val nearLeft = kotlin.math.abs(x - cropLeft) < edge
                    val nearRight = kotlin.math.abs(x - cropRight) < edge
                    val nearTop = kotlin.math.abs(y - cropTop) < edge
                    val nearBottom = kotlin.math.abs(y - cropBottom) < edge
                    activeHandle = when {
                        nearLeft -> 1
                        nearRight -> 2
                        nearTop -> 3
                        nearBottom -> 4
                        else -> 0
                    }
                    dragLeft = cropLeft
                    dragTop = cropTop
                    dragRight = cropRight
                    dragBottom = cropBottom
                }

                if (activeHandle != 0) {
                    val dx = pan.x / size.width
                    val dy = pan.y / size.height
                    when (activeHandle) {
                        1 -> dragLeft = (dragLeft + dx).coerceIn(0f, dragRight - 0.05f)
                        2 -> dragRight = (dragRight + dx).coerceIn(dragLeft + 0.05f, 1f)
                        3 -> dragTop = (dragTop + dy).coerceIn(0f, dragBottom - 0.05f)
                        4 -> dragBottom = (dragBottom + dy).coerceIn(dragTop + 0.05f, 1f)
                    }
                    onChange(dragLeft, dragTop, dragRight, dragBottom)
                }
            }
            activeHandle = 0
        }
    ) {
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
    }
}

'''
    replace_once(marker, crop_overlay + marker, "crop overlay insertion")

for imp in [
    "import androidx.compose.foundation.gestures.detectTransformGestures",
    "import androidx.compose.ui.input.pointer.pointerInput",
]:
    if imp not in text:
        text = text.replace("import androidx.compose.foundation.Image", imp + "\nimport androidx.compose.foundation.Image", 1)

checks = {
    "crop mode": "var cropMode by remember" in text,
    "crop button": 'Text(if (cropMode) "Done Crop" else "Crop")' in text,
    "visible photo": 'Image(preview!!.asImageBitmap()' in text and 'if (cropMode)' in text,
    "crop rectangle": "CropOverlay(" in text and "drawRect(Color.White" in text,
    "drag crop edges": "activeHandle" in text and "nearLeft" in text and "nearRight" in text and "nearTop" in text and "nearBottom" in text,
    "crop processing": "Bitmap.createBitmap(output, left, top" in text,
}
failed = [k for k,v in checks.items() if not v]
for k,v in checks.items(): print(("PASS " if v else "FAIL ") + k)
if failed: raise SystemExit("CROP AUDIT FAILED: " + "; ".join(failed))
path.write_text(text, encoding="utf-8")
print("Gallery editor crop now uses a Compose-supported transform detector for edge dragging and is safe to rerun on the generated editor source.")
