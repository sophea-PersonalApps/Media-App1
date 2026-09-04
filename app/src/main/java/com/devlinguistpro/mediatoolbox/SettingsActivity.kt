package com.devlinguistpro.mediatoolbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MediaToolboxPrefs {
    const val PREFS = "media_toolbox_settings"
    const val KEY_SCANNER_FOLDER = "scanner_pdf_folder"
    const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    const val KEY_GALLERY_SORT_NEWEST = "gallery_sort_newest"
    const val DEFAULT_SCANNER_FOLDER = "Choose a folder when saving"
}

class SettingsActivity : ComponentActivity() {
    private var scannerFolderName by mutableStateOf(MediaToolboxPrefs.DEFAULT_SCANNER_FOLDER)
    private var keepScreenOn by mutableStateOf(true)
    private var gallerySortNewest by mutableStateOf(true)
    private var aboutOpen by mutableStateOf(false)

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE).edit()
                .putString(MediaToolboxPrefs.KEY_SCANNER_FOLDER, uri.toString()).apply()
            scannerFolderName = folderName(uri)
            Toast.makeText(this, "Scanner folder saved", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Could not keep access to that folder", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings()
        setContent {
            MaterialTheme {
                SettingsScreen(
                    scannerFolderName, keepScreenOn, gallerySortNewest, aboutOpen,
                    ::finish, { folderPicker.launch(null) }, ::setKeepScreenOn,
                    ::setGallerySortNewest, { aboutOpen = true }, { aboutOpen = false }
                )
            }
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE)
        keepScreenOn = prefs.getBoolean(MediaToolboxPrefs.KEY_KEEP_SCREEN_ON, true)
        gallerySortNewest = prefs.getBoolean(MediaToolboxPrefs.KEY_GALLERY_SORT_NEWEST, true)
        scannerFolderName = prefs.getString(MediaToolboxPrefs.KEY_SCANNER_FOLDER, null)
            ?.let(Uri::parse)?.let(::folderName) ?: MediaToolboxPrefs.DEFAULT_SCANNER_FOLDER
    }

    private fun setKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE).edit()
            .putBoolean(MediaToolboxPrefs.KEY_KEEP_SCREEN_ON, value).apply()
    }

    private fun setGallerySortNewest(value: Boolean) {
        gallerySortNewest = value
        getSharedPreferences(MediaToolboxPrefs.PREFS, MODE_PRIVATE).edit()
            .putBoolean(MediaToolboxPrefs.KEY_GALLERY_SORT_NEWEST, value).apply()
    }

    private fun folderName(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri)?.substringAfterLast(':')?.let(Uri::decode)
            ?.takeIf { it.isNotBlank() } ?: "Chosen folder"
    } catch (_: Exception) { "Chosen folder" }
}

@Composable
private fun SettingsScreen(
    scannerFolderName: String,
    keepScreenOn: Boolean,
    gallerySortNewest: Boolean,
    aboutOpen: Boolean,
    onBack: () -> Unit,
    onChooseScannerFolder: () -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onGallerySortChanged: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onDismissAbout: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SettingsSection("Camera", Icons.Default.CameraAlt)
                SettingsSwitchRow("Keep screen on", "Prevent the display from sleeping while using camera or scanner", keepScreenOn, onKeepScreenOnChanged)
                Spacer(Modifier.height(20.dp))
                SettingsSection("Gallery", Icons.Default.CameraAlt)
                SettingsSwitchRow("Newest media first", "Show the newest photos and videos at the beginning", gallerySortNewest, onGallerySortChanged)
                Spacer(Modifier.height(20.dp))
                SettingsSection("Scanner", Icons.Default.Scanner)
                FolderSettingRow("PDF save folder", scannerFolderName, onChooseScannerFolder)
                Spacer(Modifier.height(20.dp))
                SettingsSection("QR scanner", Icons.Default.QrCodeScanner)
                InfoRow("QR results", "Detected QR results stay inside the scanner screen until you choose an action.")
                Spacer(Modifier.height(20.dp))
                SettingsSection("About", Icons.Default.CameraAlt)
                InfoRow("Media Toolbox", "Simple camera, gallery, document scanner and QR scanner in one app.", onAbout)
                Spacer(Modifier.height(28.dp))
            }
        }
        if (aboutOpen) AlertDialog(
            onDismissRequest = onDismissAbout,
            title = { Text("Media Toolbox") },
            text = { Text("A simple, local-first media toolbox. Photos and videos use Android media storage; scanner PDFs use the folder you choose.") },
            confirmButton = { Button(onClick = onDismissAbout) { Text("Done") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsSwitchRow(title: String, description: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Color.LightGray, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun FolderSettingRow(title: String, folderName: String, onChoose: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Folder, "Folder", tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(folderName, color = Color.LightGray, fontSize = 13.sp, maxLines = 2)
        }
        Button(onClick = onChoose) { Text("Choose") }
    }
}

@Composable
private fun InfoRow(title: String, description: String, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clickable(enabled = onClick != null, onClick = { onClick?.invoke() }).padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(3.dp))
        Text(description, color = Color.LightGray, fontSize = 13.sp)
    }
}
