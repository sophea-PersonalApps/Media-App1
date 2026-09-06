from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


# Make Recording observable by Compose.
old = "    private var activeRecording: Recording? = null"
new = "    private var activeRecording by mutableStateOf<Recording?>(null)"
if old in text:
    replace_once(old, new, "activeRecording declaration")
elif new not in text:
    raise SystemExit("activeRecording declaration not found")

# Wire recording state through the composable hierarchy. These complete,
# function-specific signatures avoid matching similarly shaped callbacks.
if "isRecording = activeRecording != null" not in text:
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

old = """            onCapture,
            onVideoToggle,
            onFlip,
            onOpenGallery,"""
new = """            onCapture,
            onVideoToggle,
            isRecording,
            onFlip,
            onOpenGallery,"""
if "            onVideoToggle,\n            isRecording,\n            onFlip," not in text:
    replace_once(old, new, "CameraScreen recording argument")

# CameraScreen receives recording state.
old = """    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlip: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,"""
new = """    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    isRecording: Boolean,
    onFlip: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,"""
if text.count(old) == 1 and "private fun CameraScreen(" in text and "    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenGallery" not in text[text.find("private fun CameraScreen("):]:
    replace_once(old, new, "CameraScreen signature")

# Add a short visual shutter flash for photos.
if "var showCaptureFlash by remember" not in text:
    replace_once(
        "    val modes = CameraMode.entries\n    val selectedIndex = modes.indexOf(mode)",
        "    val modes = CameraMode.entries\n    val selectedIndex = modes.indexOf(mode)\n    var showCaptureFlash by remember { mutableStateOf(false) }\n    LaunchedEffect(showCaptureFlash) {\n        if (showCaptureFlash) {\n            kotlinx.coroutines.delay(120)\n            showCaptureFlash = false\n        }\n    }",
        "capture feedback state",
    )

# Trigger the flash only after the actual photo capture callback is invoked.
if "showCaptureFlash = true" not in text:
    replace_once(
        "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
        "            CameraControls(mode, selectedIndex, modes, onModeChanged, {\n                onCapture()\n                showCaptureFlash = true\n            }, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
        "CameraScreen capture callback",
    )

if "showCaptureFlash && mode == CameraMode.PHOTO" not in text:
    replace_once(
        """            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {""",
        """            modifier = Modifier.fillMaxSize()
        )
        if (showCaptureFlash && mode == CameraMode.PHOTO) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.88f)))
        }
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.TopCenter).pointerInput(mode) {""",
        "CameraScreen capture flash overlay",
    )

# CameraControls receives recording state and exposes it to the shutter.
old = """    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlip: () -> Unit,
    onOpenSettings: () -> Unit,"""
new = """    onCapture: () -> Unit,
    onVideoToggle: () -> Unit,
    isRecording: Boolean,
    onFlip: () -> Unit,
    onOpenSettings: () -> Unit,"""
if "private fun CameraControls(" in text and "    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenSettings" not in text[text.find("private fun CameraControls("):]:
    replace_once(old, new, "CameraControls signature")

if "ShutterButton(mode, isRecording, onCapture, onVideoToggle)" not in text:
    replace_once(
        "                ShutterButton(mode, onCapture, onVideoToggle)",
        "                ShutterButton(mode, isRecording, onCapture, onVideoToggle)",
        "ShutterButton call",
    )

if "Text(\"RECORDING\"" not in text:
    replace_once(
        """            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {""",
        """            if (mode == CameraMode.VIDEO && isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Box(Modifier.size(10.dp).background(Color.Red, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(\"RECORDING\", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {""",
        "recording indicator",
    )

# Recording shutter changes from white to red while active.
if "private fun ShutterButton(mode: CameraMode, isRecording: Boolean" not in text:
    replace_once(
        "private fun ShutterButton(mode: CameraMode, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
        "private fun ShutterButton(mode: CameraMode, isRecording: Boolean, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
        "ShutterButton signature",
    )

if "if (mode == CameraMode.VIDEO && isRecording) Color.Red" not in text:
    replace_once(
        "        Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape))",
        """        Box(
            Modifier
                .size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp)
                .background(
                    if (mode == CameraMode.VIDEO && isRecording) Color.Red else Color.White,
                    if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape
                )
        )""",
        "recording shutter",
    )

path.write_text(text, encoding="utf-8")
print("Camera feedback patch applied cleanly.")
