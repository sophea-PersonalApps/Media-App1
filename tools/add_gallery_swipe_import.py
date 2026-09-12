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
