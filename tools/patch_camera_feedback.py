from pathlib import Path
import re

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


def patch_function(name: str, old: str, new: str, label: str) -> None:
    global text
    pattern = re.compile(
        rf"(private fun {re.escape(name)}\([^)]*\n)(.*?)(\n\) \{{)",
        re.DOTALL,
    )
    match = pattern.search(text)
    if not match:
        raise SystemExit(f"{label}: could not locate {name}()")
    body = match.group(2)
    count = body.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match in {name}(), found {count}")
    body = body.replace(old, new, 1)
    text = text[:match.start()] + match.group(1) + body + match.group(3) + text[match.end():]


# Start from the known-good source used by the successful APK builds and apply
# only the camera feedback feature. The script is idempotent for safe retries.
old_recording = "    private var activeRecording: Recording? = null\n"
new_recording = "    private var activeRecording by mutableStateOf<Recording?>(null)\n"
if old_recording in text:
    text = text.replace(old_recording, new_recording, 1)
elif new_recording not in text:
    raise SystemExit("activeRecording declaration not found")

# MediaToolboxApp state wiring.
if "isRecording = activeRecording != null" not in text:
    replace_once(
        "                    onVideoToggle = ::toggleVideoRecording,\n                    onFlip = ::flipCamera,\n",
        "                    onVideoToggle = ::toggleVideoRecording,\n                    isRecording = activeRecording != null,\n                    onFlip = ::flipCamera,\n",
        "MediaToolboxApp recording argument",
    )

if "    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenGallery" not in text:
    patch_function(
        "MediaToolboxApp",
        "    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n",
        "    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n",
        "MediaToolboxApp parameters",
    )

if "            onVideoToggle,\n            isRecording,\n            onFlip" not in text:
    replace_once(
        "            onCapture,\n            onVideoToggle,\n            onFlip,\n            onOpenGallery,\n",
        "            onCapture,\n            onVideoToggle,\n            isRecording,\n            onFlip,\n            onOpenGallery,\n",
        "CameraScreen recording argument",
    )

# CameraScreen parameters and camera controls wiring.
if "private fun CameraScreen(" in text and "private fun CameraScreen(\n    mode: CameraMode,\n    onModeChanged: (CameraMode) -> Unit,\n    onPreviewReady: (PreviewView) -> Unit,\n    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean," not in text:
    patch_function(
        "CameraScreen",
        "    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n",
        "    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n",
        "CameraScreen parameters",
    )

if "CameraControls(mode, selectedIndex, modes, onModeChanged, {" not in text:
    replace_once(
        "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
        "            CameraControls(mode, selectedIndex, modes, onModeChanged, {\n                onCapture()\n                showCaptureFlash = true\n            }, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
        "CameraControls call",
    )

# CameraScreen capture flash state and short white shutter flash.
if "var showCaptureFlash by remember" not in text:
    replace_once(
        "    val context = LocalContext.current\n    val modes = CameraMode.entries\n    val selectedIndex = modes.indexOf(mode)\n",
        "    val context = LocalContext.current\n    val modes = CameraMode.entries\n    val selectedIndex = modes.indexOf(mode)\n    var showCaptureFlash by remember { mutableStateOf(false) }\n    LaunchedEffect(showCaptureFlash) {\n        if (showCaptureFlash) {\n            kotlinx.coroutines.delay(120)\n            showCaptureFlash = false\n        }\n    }\n",
        "capture feedback state",
    )

if "showCaptureFlash && mode == CameraMode.PHOTO" not in text:
    replace_once(
        "            modifier = Modifier.fillMaxSize()\n        )\n        Box(\n            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {",
        "            modifier = Modifier.fillMaxSize()\n        )\n        if (showCaptureFlash && mode == CameraMode.PHOTO) {\n            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.88f)))\n        }\n        Box(\n            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {",
        "capture flash overlay",
    )

# CameraControls recording state and visible recording indicator.
if "private fun CameraControls(" in text and "    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n" not in text:
    patch_function(
        "CameraControls",
        "    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n",
        "    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n",
        "CameraControls parameters",
    )

if "ShutterButton(mode, isRecording," not in text:
    replace_once(
        "                ShutterButton(mode, onCapture, onVideoToggle)\n",
        "                ShutterButton(mode, isRecording, onCapture, onVideoToggle)\n",
        "ShutterButton call",
    )

if "Text(\"RECORDING\"" not in text:
    replace_once(
        "            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {",
        "            if (mode == CameraMode.VIDEO && isRecording) {\n                Row(\n                    verticalAlignment = Alignment.CenterVertically,\n                    horizontalArrangement = Arrangement.Center,\n                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)\n                ) {\n                    Box(Modifier.size(10.dp).background(Color.Red, CircleShape))\n                    Spacer(Modifier.width(7.dp))\n                    Text(\"RECORDING\", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)\n                }\n            }\n            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {",
        "recording indicator",
    )

# ShutterButton visual state.
if "private fun ShutterButton(mode: CameraMode, isRecording: Boolean" not in text:
    replace_once(
        "private fun ShutterButton(mode: CameraMode, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
        "private fun ShutterButton(mode: CameraMode, isRecording: Boolean, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
        "ShutterButton signature",
    )

if "if (mode == CameraMode.VIDEO && isRecording) Color.Red" not in text:
    replace_once(
        "        Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape))",
        "        Box(\n            Modifier\n                .size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp)\n                .background(\n                    if (mode == CameraMode.VIDEO && isRecording) Color.Red else Color.White,\n                    if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape\n                )\n        )",
        "recording shutter",
    )

path.write_text(text, encoding="utf-8")
print("Camera feedback patch applied cleanly.")
