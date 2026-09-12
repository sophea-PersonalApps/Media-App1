from pathlib import Path
import subprocess

MAIN = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt")

subprocess.run(["git", "checkout", "origin/main", "--",
                "app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt",
                "app/src/main/java/com/devlinguistpro/mediatoolbox/ScannerActivity.kt",
                "app/src/main/java/com/devlinguistpro/mediatoolbox/QrScannerActivity.kt"], check=True)

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        if count == 0 and new in text:
            return text
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

text = MAIN.read_text(encoding="utf-8")
text = text.replace("import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n", "")
# The baseline property cameraZoom already generates setCameraZoom(Float) on MainActivity.
# Rename the private helper so it cannot clash with that generated property setter.
text = text.replace("private fun setCameraZoom(value: Float)", "private fun updateCameraZoom(value: Float)")
text = text.replace("onZoom = ::setCameraZoom", "onZoom = ::updateCameraZoom")
old_camera = '''        Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {
            var drag = 0f
            detectHorizontalDragGestures(onHorizontalDrag = { _, amount -> drag += amount }, onDragEnd = {
                when { drag < -80f && selectedIndex < modes.lastIndex -> onModeChanged(modes[selectedIndex + 1]); drag > 80f && selectedIndex > 0 -> onModeChanged(modes[selectedIndex - 1]) }
                drag = 0f
            })
        })
        if (mode == CameraSectionMode.PHOTO || mode == CameraSectionMode.VIDEO) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode, currentLens) {
                detectTransformGestures { _, _, zoom, _ -> onZoom(cameraZoom * zoom) }
            })
        }
'''
new_camera = '''        Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode, currentLens) {
            var horizontalDrag = 0f
            var gestureZoom = cameraZoom
            detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                val cameraMode = mode == CameraSectionMode.PHOTO || mode == CameraSectionMode.VIDEO
                if (cameraMode && kotlin.math.abs(zoom - 1f) > 0.001f) {
                    gestureZoom = (gestureZoom * zoom).coerceIn(1f, 10f)
                    onZoom(gestureZoom)
                    horizontalDrag = 0f
                } else if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                    horizontalDrag += pan.x
                    when {
                        horizontalDrag <= -80f && selectedIndex < modes.lastIndex -> { onModeChanged(modes[selectedIndex + 1]); horizontalDrag = 0f }
                        horizontalDrag >= 80f && selectedIndex > 0 -> { onModeChanged(modes[selectedIndex - 1]); horizontalDrag = 0f }
                    }
                }
            }
        })
'''
text = replace_once(text, old_camera, new_camera, "camera gesture block")
MAIN.write_text(text, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
for item in ["detectTransformGestures(panZoomLock = true)", "var gestureZoom = cameraZoom", "gestureZoom = (gestureZoom * zoom)", "private fun updateCameraZoom(value: Float)"]:
    if item not in main: raise SystemExit(f"Required camera pinch implementation missing: {item}")
if "detectHorizontalDragGestures" in main: raise SystemExit("Old competing camera horizontal gesture handler remains")
if "onZoom(cameraZoom * zoom)" in main: raise SystemExit("Old non-accumulating camera zoom implementation remains")
if "private fun setCameraZoom(value: Float)" in main: raise SystemExit("Conflicting setCameraZoom helper remains")
print("Clean camera/scanner/QR sources restored from origin/main; camera pinch implementation applied and audited. Gallery is intentionally left to the Gallery pager finalizer.")