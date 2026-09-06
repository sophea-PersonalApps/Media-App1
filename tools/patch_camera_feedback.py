from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

# Keep the recording state observable by Compose so the controls can immediately
# reflect start/stop events.
replace_once(
    "    private var activeRecording: Recording? = null\n",
    "    private var activeRecording by mutableStateOf<Recording?>(null)\n",
    "recording state"
)

replace_once(
    "                    onCapture = ::capturePhoto,\n                    onVideoToggle = ::toggleVideoRecording,\n                    onFlip = ::flipCamera,\n",
    "                    onCapture = ::capturePhoto,\n                    onVideoToggle = ::toggleVideoRecording,\n                    isRecording = activeRecording != null,\n                    onFlip = ::flipCamera,\n",
    "app recording state"
)

replace_once(
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n",
    "app composable recording parameter"
)

replace_once(
    "            onCapture,\n            onVideoToggle,\n            onFlip,\n            onOpenGallery,\n",
    "            onCapture,\n            onVideoToggle,\n            isRecording,\n            onFlip,\n            onOpenGallery,\n",
    "app composable recording argument"
)

replace_once(
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n",
    "camera screen recording parameter"
)

replace_once(
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)\n",
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)\n",
    "camera controls argument"
)

replace_once(
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "camera controls recording parameter"
)

replace_once(
    "                ShutterButton(mode, onCapture, onVideoToggle)\n",
    "                ShutterButton(mode, isRecording, onCapture, onVideoToggle)\n",
    "shutter call"
)

replace_once(
    "            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {\n",
    "            if (mode == CameraMode.VIDEO && isRecording) {\n                Row(\n                    verticalAlignment = Alignment.CenterVertically,\n                    horizontalArrangement = Arrangement.Center,\n                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)\n                ) {\n                    Box(Modifier.size(10.dp).background(Color.Red, CircleShape))\n                    Spacer(Modifier.width(7.dp))\n                    Text(\"RECORDING\", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)\n                }\n            }\n            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {\n",
    "recording indicator"
)

replace_once(
    "private fun ShutterButton(mode: CameraMode, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {\n",
    "private fun ShutterButton(mode: CameraMode, isRecording: Boolean, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {\n",
    "shutter signature"
)

replace_once(
    "        Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape))\n",
    "        Box(\n            Modifier\n                .size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp)\n                .background(\n                    if (mode == CameraMode.VIDEO && isRecording) Color.Red else Color.White,\n                    if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape\n                )\n        )\n",
    "recording shutter"
)

replace_once(
    "    val context = LocalContext.current\n    val modes = CameraMode.entries\n",
    "    val context = LocalContext.current\n    val modes = CameraMode.entries\n    var showCaptureFlash by remember { mutableStateOf(false) }\n    LaunchedEffect(showCaptureFlash) {\n        if (showCaptureFlash) {\n            kotlinx.coroutines.delay(120)\n            showCaptureFlash = false\n        }\n    }\n",
    "capture feedback state"
)

replace_once(
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, {\n                onCapture()\n                showCaptureFlash = true\n            }, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)\n",
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, {\n                onCapture()\n                showCaptureFlash = true\n            }, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)\n",
    "camera controls feedback call"
)

replace_once(
    "            modifier = Modifier.fillMaxSize()\n        )\n        Box(\n",
    "            modifier = Modifier.fillMaxSize()\n        )\n        if (showCaptureFlash && mode == CameraMode.PHOTO) {\n            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.88f)))\n        }\n        Box(\n",
    "capture flash overlay"
)

path.write_text(text, encoding="utf-8")
print("Camera feedback patch applied cleanly.")
