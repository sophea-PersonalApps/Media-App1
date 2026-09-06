from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_exact(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

# This workflow starts from the known-good pre-feature MainActivity. Keep every
# edit explicit and uniquely matched so a changed source fails loudly instead
# of silently producing a malformed Kotlin file.
replace_exact(
    "    private var activeRecording: Recording? = null\n",
    "    private var activeRecording by mutableStateOf<Recording?>(null)\n",
    "activeRecording declaration",
)

replace_exact(
    "                    onVideoToggle = ::toggleVideoRecording,\n                    onFlip = ::flipCamera,\n",
    "                    onVideoToggle = ::toggleVideoRecording,\n                    isRecording = activeRecording != null,\n                    onFlip = ::flipCamera,\n",
    "MediaToolboxApp recording argument",
)

replace_exact(
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "MediaToolboxApp parameters",
)

replace_exact(
    "            onCapture,\n            onVideoToggle,\n            onFlip,\n            onOpenGallery,\n",
    "            onCapture,\n            onVideoToggle,\n            isRecording,\n            onFlip,\n            onOpenGallery,\n",
    "CameraScreen recording argument",
)

replace_exact(
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "CameraScreen parameters",
)

replace_exact(
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, {\n                onCapture()\n                showCaptureFlash = true\n            }, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
    "CameraControls call",
)

# Add the short visual shutter feedback state inside CameraScreen.
replace_exact(
    "    val context = LocalContext.current\n    val modes = CameraMode.entries\n    val selectedIndex = modes.indexOf(mode)\n",
    "    val context = LocalContext.current\n    val modes = CameraMode.entries\n    val selectedIndex = modes.indexOf(mode)\n    var showCaptureFlash by remember { mutableStateOf(false) }\n    LaunchedEffect(showCaptureFlash) {\n        if (showCaptureFlash) {\n            kotlinx.coroutines.delay(120)\n            showCaptureFlash = false\n        }\n    }\n",
    "capture feedback state",
)

replace_exact(
    "            modifier = Modifier.fillMaxSize()\n        )\n        Box(\n            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {",
    "            modifier = Modifier.fillMaxSize()\n        )\n        if (showCaptureFlash && mode == CameraMode.PHOTO) {\n            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.88f)))\n        }\n        Box(\n            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {",
    "capture flash overlay",
)

replace_exact(
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "CameraControls parameters",
)

replace_exact(
    "                ShutterButton(mode, onCapture, onVideoToggle)\n",
    "                ShutterButton(mode, isRecording, onCapture, onVideoToggle)\n",
    "ShutterButton call",
)

replace_exact(
    "            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {",
    "            if (mode == CameraMode.VIDEO && isRecording) {\n                Row(\n                    verticalAlignment = Alignment.CenterVertically,\n                    horizontalArrangement = Arrangement.Center,\n                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)\n                ) {\n                    Box(Modifier.size(10.dp).background(Color.Red, CircleShape))\n                    Spacer(Modifier.width(7.dp))\n                    Text(\"RECORDING\", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)\n                }\n            }\n            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {",
    "recording indicator",
)

replace_exact(
    "private fun ShutterButton(mode: CameraMode, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
    "private fun ShutterButton(mode: CameraMode, isRecording: Boolean, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
    "ShutterButton signature",
)

replace_exact(
    "        Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape))",
    "        Box(\n            Modifier\n                .size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp)\n                .background(\n                    if (mode == CameraMode.VIDEO && isRecording) Color.Red else Color.White,\n                    if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape\n                )\n        )",
    "recording shutter",
)

path.write_text(text, encoding="utf-8")
print("Camera feedback patch applied cleanly.")
