from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
MAIN = ROOT / "MainActivity.kt"
GALLERY = ROOT / "GalleryActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"
QR = ROOT / "QrScannerActivity.kt"


def replace_once(path: Path, text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        if count == 0 and new in text:
            return text
        raise SystemExit(f"{path.name}: {label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# CAMERA -> SCANNER / QR: keep the existing camera/video mode chrome. The
# scanner and QR activities remain separate because their camera pipelines are
# separate, but their Android activity transitions are removed and their
# capture screens no longer add a second header/navigation section.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    MAIN, main,
    '            CameraSectionMode.SCAN -> startActivity(Intent(this, ScannerActivity::class.java))\n            CameraSectionMode.QR -> startActivity(Intent(this, QrScannerActivity::class.java))',
    '            CameraSectionMode.SCAN -> { startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(0, 0) }\n            CameraSectionMode.QR -> { startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(0, 0) }',
    "camera mode transition",
)
MAIN.write_text(main, encoding="utf-8")

scanner = SCANNER.read_text(encoding="utf-8")
scanner = replace_once(
    SCANNER, scanner,
    '            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {\n                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ComposeColor.White) }\n                Text("Scanner", color = ComposeColor.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)\n                IconButton(onClick = onFlip) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = ComposeColor.White) }\n            }\n',
    '',
    "scanner capture header",
)
# No top scanner Back button: the shared bottom camera controls provide the
# CAMERA mode and therefore the return path.
scanner = replace_once(
    SCANNER, scanner,
    'onOpenCamera = { startActivity(Intent(this, MainActivity::class.java).putExtras(cameraModeIntent(CameraSectionMode.PHOTO))) },',
    'onOpenCamera = { startActivity(Intent(this, MainActivity::class.java).putExtras(cameraModeIntent(CameraSectionMode.PHOTO))); overridePendingTransition(0, 0) },',
    "scanner camera transition",
)
scanner = replace_once(
    SCANNER, scanner,
    'onOpenVideo = { startActivity(Intent(this, MainActivity::class.java).putExtras(cameraModeIntent(CameraSectionMode.VIDEO))) },',
    'onOpenVideo = { startActivity(Intent(this, MainActivity::class.java).putExtras(cameraModeIntent(CameraSectionMode.VIDEO))); overridePendingTransition(0, 0) },',
    "scanner video transition",
)
scanner = replace_once(
    SCANNER, scanner,
    'onOpenGallery = { startActivity(Intent(this, GalleryActivity::class.java)) },',
    'onOpenGallery = { startActivity(Intent(this, GalleryActivity::class.java)); overridePendingTransition(0, 0) },',
    "scanner gallery transition",
)
scanner = replace_once(
    SCANNER, scanner,
    'onOpenQr = { startActivity(Intent(this, QrScannerActivity::class.java)) }',
    'onOpenQr = { startActivity(Intent(this, QrScannerActivity::class.java)); overridePendingTransition(0, 0) }',
    "scanner QR transition",
)
# When returning from scanner preview, keep the same no-animation behaviour.
scanner = replace_once(
    SCANNER, scanner,
    'onBackToScanner = { showingPreview = false; cameraBindRequested = true; if (cameraPermissionGranted && lifecycleActive) bindCamera() },',
    'onBackToScanner = { showingPreview = false; cameraBindRequested = true; if (cameraPermissionGranted && lifecycleActive) bindCamera() },',
    "scanner preview transition",
)
SCANNER.write_text(scanner, encoding="utf-8")

qr = QR.read_text(encoding="utf-8")
# Remove the full-width QR title/header. Keep a simple Back button in the top-left
# corner as requested, without creating a separate header band.
qr = replace_once(
    QR, qr,
    '                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {\n                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }\n                    Text("QR SCANNER", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)\n                    IconButton(onClick = onFlip, enabled = result == null) { Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White) }\n                }\n',
    '                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }\n',
    "QR header",
)
qr = replace_once(
    QR, qr,
    'onOpenCamera = { startActivity(Intent(this, MainActivity::class.java).putExtras(cameraModeIntent(CameraSectionMode.PHOTO))) },',
    'onOpenCamera = { startActivity(Intent(this, MainActivity::class.java).putExtras(cameraModeIntent(CameraSectionMode.PHOTO))); overridePendingTransition(0, 0) },',
    "QR camera transition",
)
qr = replace_once(
    QR, qr,
    'onOpenVideo = { startActivity(Intent(this, MainActivity::class.java).putExtras(cameraModeIntent(CameraSectionMode.VIDEO))) },',
    'onOpenVideo = { startActivity(Intent(this, MainActivity::class.java).putExtras(cameraModeIntent(CameraSectionMode.VIDEO))); overridePendingTransition(0, 0) },',
    "QR video transition",
)
qr = replace_once(
    QR, qr,
    'onOpenGallery = { startActivity(Intent(this, GalleryActivity::class.java)) },',
    'onOpenGallery = { startActivity(Intent(this, GalleryActivity::class.java)); overridePendingTransition(0, 0) },',
    "QR gallery transition",
)
qr = replace_once(
    QR, qr,
    'onOpenScanner = { startActivity(Intent(this, ScannerActivity::class.java)) }',
    'onOpenScanner = { startActivity(Intent(this, ScannerActivity::class.java)); overridePendingTransition(0, 0) }',
    "QR scanner transition",
)
QR.write_text(qr, encoding="utf-8")

# ---------------------------------------------------------------------------
# GALLERY: open a viewer with its position in the currently displayed list and
# allow horizontal swipes to move between adjacent media items. Photo zoom/pan
# remains the same; at zoom 1 a horizontal drag becomes next/previous instead.
# ---------------------------------------------------------------------------
gallery = GALLERY.read_text(encoding="utf-8")

gallery = replace_once(
    GALLERY, gallery,
    '    var selectedIsVideo by rememberSaveable { mutableStateOf(false) }\n    var selectionMode by rememberSaveable { mutableStateOf(false) }',
    '    var selectedIsVideo by rememberSaveable { mutableStateOf(false) }\n    var selectedIndex by rememberSaveable { mutableIntStateOf(-1) }\n    var selectionMode by rememberSaveable { mutableStateOf(false) }',
    "gallery viewer index state",
)
gallery = replace_once(
    GALLERY, gallery,
    '    val selectedItems = remember { mutableStateMapOf<String, MediaItem>() }\n    val context = LocalContext.current\n',
    '    val selectedItems = remember { mutableStateMapOf<String, MediaItem>() }\n    val context = LocalContext.current\n    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }\n    var mediaLoading by remember { mutableStateOf(false) }\n    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }\n',
    "gallery media state hoisting",
)
gallery = replace_once(
    GALLERY, gallery,
    '            Box(Modifier.weight(1f)) { MediaViewer(selectedUri!!, selectedIsVideo, { selectedUri = null }, { shareMedia(context, selectedUri!!) }, if (selectedIsVideo) null else ({ onEdit(selectedUri!!) })) { val uriBeingDeleted = selectedUri!!; onDelete(uriBeingDeleted) { deleted -> if (deleted) { selectedUri = null; refreshToken++ } } } }',
    '            Box(Modifier.weight(1f)) { MediaViewer(selectedUri!!, selectedIsVideo, media, selectedIndex, { index -> if (index in media.indices) { selectedIndex = index; selectedUri = media[index].uri; selectedIsVideo = media[index].isVideo } }, { selectedUri = null; selectedIndex = -1 }, { shareMedia(context, selectedUri!!) }, if (selectedIsVideo) null else ({ onEdit(selectedUri!!) })) { val uriBeingDeleted = selectedUri!!; onDelete(uriBeingDeleted) { deleted -> if (deleted) { selectedUri = null; selectedIndex = -1; refreshToken++ } } } }',
    "gallery viewer navigation wiring",
)
gallery = replace_once(
    GALLERY, gallery,
    '    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }\n    var mediaLoading by remember { mutableStateOf(false) }\n    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }\n    LaunchedEffect(tab, selectedAlbumId, refreshToken, access) {',
    '    LaunchedEffect(tab, selectedAlbumId, refreshToken, access) {',
    "remove duplicate media state",
)
gallery = replace_once(
    GALLERY, gallery,
    ' else { selectedUri = item.uri; selectedIsVideo = item.isVideo } }',
    ' else { selectedIndex = media.indexOfFirst { it.uri == item.uri }; selectedUri = item.uri; selectedIsVideo = item.isVideo } }',
    "gallery item viewer index",
)
# Replace the viewer signature and add video swipe handling on the viewer itself.
gallery = replace_once(
    GALLERY, gallery,
    '@Composable private fun MediaViewer(uri: Uri, isVideo: Boolean, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {',
    '@Composable private fun MediaViewer(uri: Uri, isVideo: Boolean, items: List<MediaItem>, currentIndex: Int, onNavigate: (Int) -> Unit, onBack: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {',
    "viewer signature",
)
# The existing MediaViewer already has the correct top-right order. Keep it, but
# make the VideoView explicitly remain a normal media surface so Compose controls
# are not hidden by an elevated surface ordering.
gallery = replace_once(
    GALLERY, gallery,
    '        setMediaController(MediaController(context).also { it.setAnchorView(this) })\n',
    '        setMediaController(MediaController(context).also { it.setAnchorView(this) })\n        setZOrderMediaOverlay(false)\n',
    "video surface ordering",
)
# Add a video-only horizontal swipe detector to the viewer Column. Photo swipes
# are handled inside the photo transform detector below so they cannot conflict
# with photo panning when zoomed.
gallery = replace_once(
    GALLERY, gallery,
    '        Column(Modifier.fillMaxSize()) {\n',
    '        Column(Modifier.fillMaxSize().pointerInput(uri, currentIndex, isVideo) {\n            if (isVideo) {\n                var dragX = 0f\n                detectHorizontalDragGestures(\n                    onHorizontalDrag = { _, amount -> dragX += amount },\n                    onDragEnd = {\n                        if (dragX <= -80f && currentIndex < items.lastIndex) onNavigate(currentIndex + 1)\n                        else if (dragX >= 80f && currentIndex > 0) onNavigate(currentIndex - 1)\n                        dragX = 0f\n                    },\n                    onDragCancel = { dragX = 0f }\n                )\n            }\n        }) {\n',
    "video swipe detector",
)
# Photo transform: at zoom 1, horizontal movement navigates; above 1 it remains
# a pan gesture. This preserves the existing pinch/pan behaviour.
gallery = replace_once(
    GALLERY, gallery,
    '                        .pointerInput(uri) {\n                                    detectTransformGestures { _, pan, zoom, _ ->\n                                        val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)\n                                        val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f\n                                        val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f\n                                        photoZoom = newZoom\n                                        if (newZoom <= 1f) {\n                                            photoPanX = 0f\n                                            photoPanY = 0f\n                                        } else {\n                                            photoPanX = (photoPanX + pan.x).coerceIn(-maxPanX, maxPanX)\n                                            photoPanY = (photoPanY + pan.y).coerceIn(-maxPanY, maxPanY)\n                                        }\n                                    }\n                                },',
    '                        .pointerInput(uri, currentIndex) {\n                                    var dragX = 0f\n                                    detectTransformGestures { _, pan, zoom, _ ->\n                                        val newZoom = (photoZoom * zoom).coerceIn(1f, 8f)\n                                        val maxPanX = viewportWidth.toFloat() * (newZoom - 1f) / 2f\n                                        val maxPanY = viewportHeight.toFloat() * (newZoom - 1f) / 2f\n                                        photoZoom = newZoom\n                                        if (newZoom <= 1f) {\n                                            photoPanX = 0f\n                                            photoPanY = 0f\n                                            if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {\n                                                dragX += pan.x\n                                                if (dragX <= -80f && currentIndex < items.lastIndex) { onNavigate(currentIndex + 1); dragX = 0f }\n                                                else if (dragX >= 80f && currentIndex > 0) { onNavigate(currentIndex - 1); dragX = 0f }\n                                            }\n                                        } else {\n                                            dragX = 0f\n                                            photoPanX = (photoPanX + pan.x).coerceIn(-maxPanX, maxPanX)\n                                            photoPanY = (photoPanY + pan.y).coerceIn(-maxPanY, maxPanY)\n                                        }\n                                    }\n                                },',
    "photo swipe and pan gesture",
)
GALLERY.write_text(gallery, encoding="utf-8")

# ---------------------------------------------------------------------------
# AUDIT: fail before Gradle if any requested source transformation did not
# happen. No unrelated source files are touched by this script.
# ---------------------------------------------------------------------------
checks = {
    "scanner header removed": 'Text("Scanner", color = ComposeColor.White' not in scanner,
    "scanner shared bottom controls": 'CameraSectionControls(' in scanner and 'CameraSectionBottomNavigation(' in scanner,
    "scanner no capture back button": 'IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ComposeColor.White) }' not in scanner,
    "QR compact back button": 'IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)' in qr,
    "QR header removed": 'Text("QR SCANNER", color = Color.White' not in qr,
    "no camera activity transition": 'overridePendingTransition(0, 0)' in main,
    "gallery viewer index": 'var selectedIndex by rememberSaveable' in gallery and 'selectedIndex = media.indexOfFirst' in gallery,
    "gallery viewer navigation": 'MediaViewer(selectedUri!!, selectedIsVideo, media, selectedIndex' in gallery,
    "gallery video swipe": 'if (isVideo) {' in gallery and 'currentIndex < items.lastIndex' in gallery,
    "gallery photo swipe": 'dragX <= -80f && currentIndex < items.lastIndex' in gallery,
    "gallery photo pan preserved": 'translationX = photoPanX' in gallery and 'translationY = photoPanY' in gallery,
    "video speed icon": 'Icon(Icons.Default.Speed, "Playback speed"' in gallery,
    "video speed choices": '0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f' in gallery,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("REQUESTED UI AUDIT FAILED: " + "; ".join(failed))
print("Requested gallery swipe and scanner/QR navigation changes applied; no unrelated source changes were made.")
