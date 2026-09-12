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

finalizer = Path("tools/fix_gallery_scanner_layout.py")
final_text = finalizer.read_text(encoding="utf-8")
marker = "# LAST_MILE_FINALIZERS\n"
if marker not in final_text:
    final_text += "\n" + marker + "exec(Path(\"tools/fix_gallery_pager.py\").read_text(encoding=\"utf-8\"), globals())\nexec(Path(\"tools/finalize_scanner_layout.py\").read_text(encoding=\"utf-8\"), globals())\n"
    finalizer.write_text(final_text, encoding="utf-8")
    print("Injected last-mile gallery pager and scanner layout finalizers.")
else:
    print("Last-mile finalizers already injected.")
