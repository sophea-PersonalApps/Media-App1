from pathlib import Path

PATH = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt")
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count == 1:
        text = text.replace(old, new, 1)
        return
    if count == 0 and new in text:
        return
    raise SystemExit(f"{label}: expected 1 match or an already-patched version, found {count}")

replace_once(
    "    private var activeRecording: Recording? = null",
    "    private var activeRecording by mutableStateOf<Recording?>(null)",
    "activeRecording declaration",
)

replace_once(
    "                    onVideoToggle = ::toggleVideoRecording,\n                    onFlip = ::flipCamera,",
    "                    onVideoToggle = ::toggleVideoRecording,\n                    isRecording = activeRecording != null,\n                    onFlip = ::flipCamera,",
    "MediaToolboxApp call recording state",
)

old = """    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlip: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleFlash: () -> Unit,
    onModeChanged: (CameraMode) -> Unit
) {"""
new = """    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    isRecording: Boolean,
    onFlip: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleFlash: () -> Unit,
    onModeChanged: (CameraMode) -> Unit
) {"""
if "    isRecording: Boolean,\n    onFlip: () -> Unit," not in text:
    replace_once(old, new, "MediaToolboxApp signature")

replace_once(
    "            onCapture,\n            onVideoToggle,\n            onFlip,\n            onOpenGallery,",
    "            onCapture,\n            onVideoToggle,\n            isRecording,\n            onFlip,\n            onOpenGallery,",
    "CameraScreen recording argument",
)

# Only the CameraScreen declaration gets this parameter; do not touch the other composables.
if "private fun CameraScreen(" in text:
    start = text.index("private fun CameraScreen(")
    end = text.index(") {", start)
    signature = text[start:end]
    if "isRecording: Boolean" not in signature:
        old_sig = "    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,"
        if signature.count(old_sig) != 1:
            raise SystemExit("CameraScreen signature: expected exactly one video callback")
        signature = signature.replace(old_sig, "    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,", 1)
        text = text[:start] + signature + text[end:]
else:
    raise SystemExit("CameraScreen declaration not found")

if "var showCaptureFlash by remember" not in text:
    replace_once(
        "    val modes = CameraMode.entries\n    val selectedIndex = modes.indexOf(mode)",
        "    val modes = CameraMode.entries\n    val selectedIndex = modes.indexOf(mode)\n    var showCaptureFlash by remember { mutableStateOf(false) }\n    LaunchedEffect(showCaptureFlash) {\n        if (showCaptureFlash) {\n            kotlinx.coroutines.delay(120)\n            showCaptureFlash = false\n        }\n    }",
        "capture feedback state",
    )

replace_once(
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, {\n                onCapture()\n                if (mode == CameraMode.PHOTO) showCaptureFlash = true\n            }, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
    "CameraControls call",
)

replace_once(
    "            modifier = Modifier.fillMaxSize()\n        )\n        Box(\n            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {",
    "            modifier = Modifier.fillMaxSize()\n        )\n        if (showCaptureFlash && mode == CameraMode.PHOTO) {\n            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.88f)))\n        }\n        Box(\n            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {",
    "CameraScreen capture flash overlay",
)

# Again target only CameraControls' declaration, avoiding accidental parameter changes elsewhere.
start = text.index("private fun CameraControls(")
end = text.index(") {", start)
signature = text[start:end]
if "isRecording: Boolean" not in signature:
    old_sig = "    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,"
    if signature.count(old_sig) != 1:
        raise SystemExit("CameraControls signature: expected exactly one video callback")
    signature = signature.replace(old_sig, "    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,", 1)
    text = text[:start] + signature + text[end:]

replace_once(
    "                ShutterButton(mode, onCapture, onVideoToggle)",
    "                ShutterButton(mode, isRecording, onCapture, onVideoToggle)",
    "ShutterButton call",
)

if "Text(\"RECORDING\"" not in text:
    replace_once(
        "            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {",
        "            if (mode == CameraMode.VIDEO && isRecording) {\n                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {\n                    Box(Modifier.size(10.dp).background(Color.Red, CircleShape))\n                    Spacer(Modifier.width(7.dp))\n                    Text(\"RECORDING\", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)\n                }\n            }\n            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {",
        "recording indicator",
    )

replace_once(
    "private fun ShutterButton(mode: CameraMode, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
    "private fun ShutterButton(mode: CameraMode, isRecording: Boolean, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
    "ShutterButton signature",
)

replace_once(
    "        Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape))",
    "        Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(if (mode == CameraMode.VIDEO && isRecording) Color.Red else Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape))",
    "recording shutter",
)

PATH.write_text(text, encoding="utf-8")
print("Camera feedback patch applied cleanly.")
