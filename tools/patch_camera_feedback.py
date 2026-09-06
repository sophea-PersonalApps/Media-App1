from pathlib import Path
import re

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once_in_block(block: str, old: str, new: str, label: str) -> str:
    count = block.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match in its function, found {count}")
    return block.replace(old, new, 1)


def function_block(source: str, name: str, next_name: str | None) -> tuple[str, str, str]:
    start_marker = f"private fun {name}"
    start = source.find(start_marker)
    if start < 0:
        raise SystemExit(f"Missing {name}()")
    if next_name is not None:
        end_marker = f"private fun {next_name}"
        end = source.find(end_marker, start + len(start_marker))
        if end < 0:
            raise SystemExit(f"Missing end marker {next_name}() after {name}()")
    else:
        end = len(source)
    return source[:start], source[start:end], source[end:]

# 1. Make the recording state observable to Compose. This is deliberately
#    idempotent so a rerun cannot corrupt an already-patched source tree.
old_recording = "    private var activeRecording: Recording? = null\n"
new_recording = "    private var activeRecording by mutableStateOf<Recording?>(null)\n"
if old_recording in text:
    text = text.replace(old_recording, new_recording, 1)
elif new_recording not in text:
    raise SystemExit("Could not locate activeRecording declaration")

# 2. MediaToolboxApp: patch only this function, avoiding the identical
#    parameter list used by CameraScreen.
prefix, block, suffix = function_block(text, "MediaToolboxApp(", "PermissionScreen(")
block = replace_once_in_block(
    block,
    "                    onVideoToggle = ::toggleVideoRecording,\n                    onFlip = ::flipCamera,\n",
    "                    onVideoToggle = ::toggleVideoRecording,\n                    isRecording = activeRecording != null,\n                    onFlip = ::flipCamera,\n",
    "MediaToolboxApp call recording state"
) if "isRecording = activeRecording != null" not in block else block
block = replace_once_in_block(
    block,
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n",
    "MediaToolboxApp parameter"
) if "    isRecording: Boolean," not in block else block
block = replace_once_in_block(
    block,
    "            onCapture,\n            onVideoToggle,\n            onFlip,\n            onOpenGallery,\n",
    "            onCapture,\n            onVideoToggle,\n            isRecording,\n            onFlip,\n            onOpenGallery,\n",
    "MediaToolboxApp argument"
) if "            isRecording,\n            onFlip" not in block else block
text = prefix + block + suffix

# 3. CameraScreen: patch only its own parameter list and controls call.
prefix, block, suffix = function_block(text, "CameraScreen(", "PermissionScreen(")
# CameraScreen appears after PermissionScreen, so the generic next marker above
# may select the wrong boundary. Locate the next composable declaration instead.
start = text.find("private fun CameraScreen(")
end = text.find("@Composable\nprivate fun", start + 10)
if start < 0 or end < 0:
    raise SystemExit("Could not isolate CameraScreen()")
block = text[start:end]
block = replace_once_in_block(
    block,
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenGallery: () -> Unit,\n",
    "CameraScreen parameter"
) if "    isRecording: Boolean," not in block else block
block = replace_once_in_block(
    block,
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
    "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
    "CameraControls argument"
) if "CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, isRecording," not in block else block

# Capture feedback is intentionally scoped to CameraScreen, so it cannot land
# in another composable that happens to have the same context/modes declarations.
if "var showCaptureFlash by remember" not in block:
    block = replace_once_in_block(
        block,
        "    val context = LocalContext.current\n    val modes = CameraMode.entries\n",
        "    val context = LocalContext.current\n    val modes = CameraMode.entries\n    var showCaptureFlash by remember { mutableStateOf(false) }\n    LaunchedEffect(showCaptureFlash) {\n        if (showCaptureFlash) {\n            kotlinx.coroutines.delay(120)\n            showCaptureFlash = false\n        }\n    }\n",
        "CameraScreen capture feedback state"
    )

if "showCaptureFlash && mode == CameraMode.PHOTO" not in block:
    block = replace_once_in_block(
        block,
        "            modifier = Modifier.fillMaxSize()\n        )\n        Box(\n",
        "            modifier = Modifier.fillMaxSize()\n        )\n        if (showCaptureFlash && mode == CameraMode.PHOTO) {\n            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.88f)))\n        }\n        Box(\n",
        "CameraScreen capture flash overlay"
    )

if "showCaptureFlash = true" not in block:
    block = replace_once_in_block(
        block,
        "            CameraControls(mode, selectedIndex, modes, onModeChanged, onCapture, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
        "            CameraControls(mode, selectedIndex, modes, onModeChanged, {\n                onCapture()\n                showCaptureFlash = true\n            }, onVideoToggle, isRecording, onFlip, onOpenSettings, flashSetting, flashAvailable, onCycleFlash, moreOpen, setMoreOpen)",
        "CameraScreen capture callback"
    )
text = text[:start] + block + text[end:]

# 4. CameraControls: isolate by its function name, then add the recording
#    indicator and pass state to the shutter button.
start = text.find("private fun CameraControls(")
end = text.find("@Composable\nprivate fun", start + 10)
if start < 0 or end < 0:
    raise SystemExit("Could not isolate CameraControls()")
block = text[start:end]
block = replace_once_in_block(
    block,
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    onFlip: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "    onCapture: () -> Unit,\n    onVideoToggle: () -> Unit,\n    isRecording: Boolean,\n    onFlip: () -> Unit,\n    onOpenSettings: () -> Unit,\n",
    "CameraControls parameter"
) if "    isRecording: Boolean," not in block else block
block = replace_once_in_block(
    block,
    "                ShutterButton(mode, onCapture, onVideoToggle)",
    "                ShutterButton(mode, isRecording, onCapture, onVideoToggle)",
    "ShutterButton call"
) if "ShutterButton(mode, isRecording," not in block else block
if "Text(\"RECORDING\"" not in block:
    block = replace_once_in_block(
        block,
        "            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {\n",
        "            if (mode == CameraMode.VIDEO && isRecording) {\n                Row(\n                    verticalAlignment = Alignment.CenterVertically,\n                    horizontalArrangement = Arrangement.Center,\n                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)\n                ) {\n                    Box(Modifier.size(10.dp).background(Color.Red, CircleShape))\n                    Spacer(Modifier.width(7.dp))\n                    Text(\"RECORDING\", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)\n                }\n            }\n            Spacer(Modifier.height(8.dp))\n            Row(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {\n",
        "recording indicator"
    )
text = text[:start] + block + text[end:]

# 5. ShutterButton: make the video shutter visibly indicate recording.
start = text.find("private fun ShutterButton(")
end = text.find("@Composable\nprivate fun", start + 10)
if start < 0 or end < 0:
    raise SystemExit("Could not isolate ShutterButton()")
block = text[start:end]
block = replace_once_in_block(
    block,
    "private fun ShutterButton(mode: CameraMode, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
    "private fun ShutterButton(mode: CameraMode, isRecording: Boolean, onPhoto: () -> Unit, onVideoToggle: () -> Unit) {",
    "ShutterButton signature"
) if "isRecording: Boolean" not in block else block
if "mode == CameraMode.VIDEO && isRecording" not in block:
    block = replace_once_in_block(
        block,
        "        Box(Modifier.size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp).background(Color.White, if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape))",
        "        Box(\n            Modifier\n                .size(if (mode == CameraMode.VIDEO) 42.dp else 58.dp)\n                .background(\n                    if (mode == CameraMode.VIDEO && isRecording) Color.Red else Color.White,\n                    if (mode == CameraMode.VIDEO) RoundedCornerShape(10.dp) else CircleShape\n                )\n        )",
        "recording shutter"
    )
text = text[:start] + block + text[end:]

path.write_text(text, encoding="utf-8")
print("Camera feedback patch applied cleanly.")
