from pathlib import Path

path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt")
text = path.read_text(encoding="utf-8")
import_line = "import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n"
if import_line not in text:
    marker = "import androidx.compose.foundation.combinedClickable\n"
    if text.count(marker) != 1:
        raise SystemExit("GalleryActivity.kt: could not find unique Compose gesture import marker")
    text = text.replace(marker, marker + import_line, 1)
    path.write_text(text, encoding="utf-8")

scanner_path = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/ScannerActivity.kt")
scanner = scanner_path.read_text(encoding="utf-8")
old_nav = "CameraSectionBottomNavigation(cameraSelected = true, onCamera = onOpenCamera, onGallery = onOpenGallery)"
new_nav = '''/* CameraSectionBottomNavigation( replacement: keep the shared CAMERA/GALLERY behavior inline. */
                Row(
                    Modifier.fillMaxWidth().background(ComposeColor.Black).height(58.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CAMERA",
                        color = ComposeColor.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onOpenCamera).padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    Text(
                        "GALLERY",
                        color = ComposeColor.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.clickable(onClick = onOpenGallery).padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }'''
count = scanner.count(old_nav)
if count == 2:
    scanner = scanner.replace(old_nav, new_nav)
    scanner_path.write_text(scanner, encoding="utf-8")
    print("Replaced both scanner bottom-navigation calls with inline CAMERA/GALLERY navigation.")
elif count == 0:
    print("Scanner bottom navigation already replaced.")
else:
    raise SystemExit(f"ScannerActivity.kt: expected 2 bottom-navigation calls, found {count}")
