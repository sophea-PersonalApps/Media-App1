package com.devlinguistpro.mediatoolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class CameraSectionMode(val label: String) { PHOTO("CAMERA"), VIDEO("VIDEO"), SCAN("SCAN"), QR("QR") }
internal const val EXTRA_CAMERA_SECTION_MODE = "camera_section_mode"

@Composable
internal fun CameraSectionControls(
    mode: CameraSectionMode,
    onModeSelected: (CameraSectionMode) -> Unit,
    onPrimaryAction: () -> Unit,
    onFlip: () -> Unit,
    onMore: () -> Unit,
    primaryEnabled: Boolean = true,
    isRecording: Boolean = false
) {
    val modes = CameraSectionMode.entries
    val selectedIndex = modes.indexOf(mode)
    Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.82f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CameraChromeControl("MORE", { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }, onMore)
                CameraChromeShutter(mode, isRecording, primaryEnabled, onPrimaryAction)
                CameraChromeControl("FLIP", { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White) }, onFlip)
            }
            if (mode == CameraSectionMode.VIDEO && isRecording) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(Color.Red, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text("RECORDING", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                modes.getOrNull(selectedIndex - 1)?.let { previous ->
                    CameraChromeMode(previous, false) { onModeSelected(previous) }
                    Spacer(Modifier.width(18.dp))
                }
                CameraChromeMode(mode, true) {}
                modes.getOrNull(selectedIndex + 1)?.let { next ->
                    Spacer(Modifier.width(18.dp))
                    CameraChromeMode(next, false) { onModeSelected(next) }
                }
            }
        }
    }
}

@Composable
internal fun CameraSectionBottomNavigation(cameraSelected: Boolean, onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.Black).height(58.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        CameraChromeNavigationItem("CAMERA", cameraSelected, onCamera)
        CameraChromeNavigationItem("GALLERY", !cameraSelected, onGallery)
    }
}

@Composable
internal fun CameraSectionMore(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("More camera options") }, text = {
        Button(onClick = { onDismiss(); onOpenSettings() }) {
            Icon(Icons.Default.Settings, "Settings")
            Spacer(Modifier.size(6.dp))
            Text("Settings")
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun CameraChromeControl(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CameraChromeShutter(mode: CameraSectionMode, isRecording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(72.dp).background(Color.White.copy(alpha = if (enabled) 1f else 0.45f), CircleShape).clickable(enabled = enabled, onClick = onClick).padding(5.dp).background(Color.Black, CircleShape), contentAlignment = Alignment.Center) {
        val recording = mode == CameraSectionMode.VIDEO && isRecording
        val innerShape = if (recording) RoundedCornerShape(10.dp) else CircleShape
        val innerSize = if (recording) 36.dp else if (mode == CameraSectionMode.VIDEO) 52.dp else 58.dp
        val innerColor = if (mode == CameraSectionMode.VIDEO) Color.Red else Color.White
        Box(Modifier.size(innerSize).background(innerColor, innerShape))
    }
}

@Composable
private fun CameraChromeMode(mode: CameraSectionMode, selected: Boolean, onClick: () -> Unit) {
    Text(mode.label, color = Color.White, fontSize = if (selected) 16.sp else 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.alpha(if (selected) 1f else 0.48f).clickable(onClick = onClick).padding(5.dp))
}

@Composable
private fun CameraChromeNavigationItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp)) {
        Text(label, color = Color.White.copy(alpha = if (selected) 1f else 0.55f), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
