from pathlib import Path
import re

ROOT = Path("app/src/main/java/com/devlinguistpro/mediatoolbox")
GALLERY = ROOT / "GalleryActivity.kt"
EDITOR = ROOT / "GalleryEditorActivity.kt"

gallery = GALLERY.read_text(encoding="utf-8")

# This stage is the final owner of gallery gesture cleanup. It never touches
# scanner, QR, camera, or the crop implementation except for the crop gesture
# detector conversion below.
gallery = gallery.replace(
    'var selectedIndex by rememberSaveable { mutableIntStateOf(-1) }\n', ''
)
# Remove the old pager callback from the generated viewer call if an earlier
# gallery patch happened to leave it behind.
old_call = re.compile(
    r'Box\(Modifier\.weight\(1f\)\) \{ MediaViewer\(selectedUri!!, selectedIsVideo, media, selectedIndex, .*?\) \}'
)
gallery = old_call.sub(
    'Box(Modifier.weight(1f)) { MediaViewer(selectedUri!!, selectedIsVideo, { selectedUri = null }, { shareMedia(context, selectedUri!!) }, if (selectedIsVideo) null else ({ onEdit(selectedUri!!) })) { val uriBeingDeleted = selectedUri!!; onDelete(uriBeingDeleted) { deleted -> if (deleted) { selectedUri = null; refreshToken++ } } } }',
    gallery,
    count=1,
)
# Never leave index-based selection in the item click path.
gallery = re.sub(r'\s*selectedIndex\s*=\s*media\.indexOfFirst\s*\{\s*it\.uri\s*==\s*item\.uri\s*\}', '', gallery)
gallery = re.sub(r'\s*selectedIndex\s*=\s*-1', '', gallery)

# The final viewer must be the fixed single-item viewer, not a pager.
if any(token in gallery for token in (
    'currentIndex', 'selectedIndex', 'onNavigate(', 'AdjacentMedia(',
    'swipeOffset', 'videoNavigationStarted', 'detectHorizontalDragGestures',
)):
    raise SystemExit('stale gallery navigation remains')

required_gallery = (
    'detectTransformGestures' in gallery,
    'mediaZoom' in gallery and 'mediaPanX' in gallery and 'mediaPanY' in gallery,
    'val focalX = centroid.x - viewportWidth / 2f' in gallery,
    'val focalY = centroid.y - viewportHeight / 2f' in gallery,
    'coerceIn(0.5f, 2f)' in gallery,
    'VideoPlayer(uri, videoSpeed)' in gallery,
)
if not all(required_gallery):
    raise SystemExit('gallery gesture audit failed')
GALLERY.write_text(gallery, encoding='utf-8')

# Crop handles are one-finger drags. The crop generator previously used
# detectTransformGestures, which overlapped with the final gallery gesture
# audit and caused the short source-stage failures. Convert only that crop
# detector; do not change crop geometry or processing.
editor = EDITOR.read_text(encoding='utf-8')
editor = editor.replace(
    'import androidx.compose.foundation.gestures.detectTransformGestures',
    'import androidx.compose.foundation.gestures.detectDragGestures',
)
editor = editor.replace(
    'detectTransformGestures { centroid, pan, _, _ ->',
    'detectDragGestures { change, dragAmount ->\n            val centroid = change.position\n            val pan = dragAmount',
)
# Also handle whitespace variations in the generated crop overlay.
editor = re.sub(
    r'detectTransformGestures\s*\{\s*centroid\s*,\s*pan\s*,\s*_,\s*_\s*->',
    'detectDragGestures { change, dragAmount ->\n            val centroid = change.position\n            val pan = dragAmount',
    editor,
)
if 'detectTransformGestures' in editor:
    raise SystemExit('crop transform detector remains')
if 'detectDragGestures' not in editor:
    raise SystemExit('crop drag detector missing')
EDITOR.write_text(editor, encoding='utf-8')
print('PASS final gallery no-swipe audit and crop drag conversion')
