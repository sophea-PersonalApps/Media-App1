from pathlib import Path

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
GALLERY = ROOT / "GalleryActivity.kt"
SCANNER = ROOT / "ScannerActivity.kt"


def replace_once(path: Path, text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        if count == 0 and new in text:
            return text
        raise SystemExit(f"{path.name}: {label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

# Scanner: the recent change removed the header correctly, but left the bottom
# control column inside a full-height SpaceBetween Column. That makes the whole
# scanner control stack occupy the top of the screen. Anchor that stack to the
# bottom instead, while preserving the existing page thumbnails/capture/finish,
# shared scanner controls, and bottom navigation.
scanner = SCANNER.read_text(encoding="utf-8")
scanner = replace_once(
    SCANNER, scanner,
    '        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {\n            Column(Modifier.fillMaxWidth().background(ComposeColor.Black.copy(alpha = 0.82f)).navigationBarsPadding().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {',
    '        Column(\n            Modifier\n                .align(Alignment.BottomCenter)\n                .fillMaxWidth()\n                .background(ComposeColor.Black.copy(alpha = 0.82f))\n                .navigationBarsPadding()\n                .padding(10.dp),\n            horizontalAlignment = Alignment.CenterHorizontally\n        ) {',
    "scanner controls anchored to bottom",
)
SCANNER.write_text(scanner, encoding="utf-8")

# Gallery: put the action row above the VideoView surface. This keeps the exact
# requested order visible: Back | Share, Speed, Delete.
gallery = GALLERY.read_text(encoding="utf-8")
if 'import androidx.compose.ui.zIndex' not in gallery:
    marker = 'import androidx.compose.ui.viewinterop.AndroidView\n'
    gallery = replace_once(GALLERY, gallery, marker, marker + 'import androidx.compose.ui.zIndex\n', "gallery zIndex import")
gallery = replace_once(
    GALLERY, gallery,
    '            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {',
    '            Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {',
    "gallery viewer action row above video",
)

# Gallery photo swipe: expose the neighbouring photo while the finger is still
# moving. Once the drag reaches half the viewport, commit to that adjacent photo;
# until then the current photo follows the finger. Zoomed photos remain pan-only.
gallery = replace_once(
    GALLERY, gallery,
    '                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }',
    '                        var photoPanX by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var photoPanY by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        var swipeOffset by rememberSaveable(uri) { mutableFloatStateOf(0f) }\n                        val adjacentIndex = when { swipeOffset < 0f -> currentIndex + 1; swipeOffset > 0f -> currentIndex - 1; else -> -1 }\n                        val adjacentUri = items.getOrNull(adjacentIndex)?.uri\n                        var adjacentBitmap by remember(adjacentUri) { mutableStateOf<Bitmap?>(null) }',
    "gallery interactive swipe state",
)
gallery = replace_once(
    GALLERY, gallery,
    '                        var viewportHeight by remember(uri) { mutableIntStateOf(0) }\n                        Box(\n',
    '                        var viewportHeight by remember(uri) { mutableIntStateOf(0) }\n                        LaunchedEffect(adjacentUri) {\n                            adjacentBitmap = if (adjacentUri == null) null else withContext(Dispatchers.IO) { loadFullImage(context, adjacentUri) }\n                        }\n                        Box(\n',
    "gallery adjacent photo loading",
)
gallery = replace_once(
    GALLERY, gallery,
    '                                                dragX += pan.x\n                                                if (dragX <= -80f && currentIndex < items.lastIndex) { onNavigate(currentIndex + 1); dragX = 0f }\n                                                else if (dragX >= 80f && currentIndex > 0) { onNavigate(currentIndex - 1); dragX = 0f }',
    '                                                dragX += pan.x\n                                                swipeOffset = (swipeOffset + pan.x).coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())\n                                                val commitDistance = viewportWidth.toFloat() * 0.5f\n                                                if (viewportWidth > 0 && swipeOffset <= -commitDistance && currentIndex < items.lastIndex) { onNavigate(currentIndex + 1); swipeOffset = 0f; dragX = 0f }\n                                                else if (viewportWidth > 0 && swipeOffset >= commitDistance && currentIndex > 0) { onNavigate(currentIndex - 1); swipeOffset = 0f; dragX = 0f }',
    "gallery interactive swipe threshold",
)
gallery = replace_once(
    GALLERY, gallery,
    '                                    }\n                                },\n                            contentAlignment = Alignment.Center\n                        ) {\n                            Image(\n                                it.asImageBitmap(),\n                                "Photo",\n                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {\n                                    scaleX = photoZoom\n                                    scaleY = photoZoom\n                                    translationX = photoPanX\n                                    translationY = photoPanY\n                                },\n                                contentScale = ContentScale.Fit\n                            )\n                        }',
    '                                    }\n                                },\n                            contentAlignment = Alignment.Center\n                        ) {\n                            if (photoZoom <= 1f && swipeOffset != 0f && adjacentBitmap != null) {\n                                Image(\n                                    adjacentBitmap!!.asImageBitmap(),\n                                    "Next photo",\n                                    Modifier.fillMaxSize().padding(8.dp).graphicsLayer {\n                                        translationX = swipeOffset + if (swipeOffset < 0f) viewportWidth.toFloat() else -viewportWidth.toFloat()\n                                    },\n                                    contentScale = ContentScale.Fit\n                                )\n                            }\n                            Image(\n                                it.asImageBitmap(),\n                                "Photo",\n                                Modifier.fillMaxSize().padding(8.dp).graphicsLayer {\n                                    scaleX = photoZoom\n                                    scaleY = photoZoom\n                                    translationX = if (photoZoom <= 1f) photoPanX + swipeOffset else photoPanX\n                                    translationY = photoPanY\n                                },\n                                contentScale = ContentScale.Fit\n                            )\n                        }',
    "gallery adjacent photo rendering",
)
GALLERY.write_text(gallery, encoding="utf-8")

checks = {
    "scanner bottom anchored": '.align(Alignment.BottomCenter)' in scanner and 'CameraSectionBottomNavigation(' in scanner,
    "scanner no top header": 'Text("Scanner", color = ComposeColor.White' not in scanner,
    "gallery action row z-index": '.zIndex(10f)' in gallery,
    "gallery interactive swipe state": 'var swipeOffset by rememberSaveable(uri)' in gallery,
    "gallery adjacent photo": 'adjacentBitmap' in gallery and '"Next photo"' in gallery,
    "gallery half-width commit": 'val commitDistance = viewportWidth.toFloat() * 0.5f' in gallery,
    "gallery pan preserved": 'translationX = if (photoZoom <= 1f) photoPanX + swipeOffset else photoPanX' in gallery and 'translationY = photoPanY' in gallery,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items(): print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("GALLERY/SCANNER LAYOUT AUDIT FAILED: " + "; ".join(failed))
