from pathlib import Path

p = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/ScannerActivity.kt")
s = p.read_text(encoding="utf-8")

def block_after(marker, open_char="("):
    start = s.find(marker)
    if start < 0:
        return None
    op = s.find(open_char, start)
    if op < 0:
        raise SystemExit(f"Missing opening delimiter for {marker}")
    depth = 0
    for i in range(op, len(s)):
        if s[i] == open_char: depth += 1
        elif s[i] == (')' if open_char == '(' else '}'):
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise SystemExit(f"Unbalanced block for {marker}")

# Remove the scanner-specific top header so the camera preview gets the same usable area as PHOTO/VIDEO.
header_marker = 'Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {'
h = block_after(header_marker, '{')
if h:
    s = s[:h[0]] + s[h[1]:]

# Remove the old page thumbnails/count/shutter/Finish controls above the shared camera chrome.
page_row_marker = 'if (pages.isNotEmpty()) {'
start = s.find(page_row_marker)
if start >= 0:
    # This occurrence is the capture-screen page strip. Find the following shared controls row and remove everything between the two.
    controls_marker = 'CameraSectionControls('
    controls = s.find(controls_marker, start)
    if controls < 0: raise SystemExit("Scanner shared controls marker not found")
    s = s[:start] + s[controls:]

# Replace the generated custom controls with the same shared camera chrome used by PHOTO/VIDEO.
marker = 'CameraSectionControls('
start = s.find(marker)
if start < 0: raise SystemExit("Scanner CameraSectionControls call not found")
op = s.find('(', start); depth = 0; end = None
for i in range(op, len(s)):
    if s[i] == '(': depth += 1
    elif s[i] == ')':
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end is None: raise SystemExit("Unbalanced Scanner CameraSectionControls call")
shared = '''CameraSectionControls(
                    mode = CameraSectionMode.SCAN,
                    onModeSelected = { scannerModeAction(context, it) },
                    onPrimaryAction = onCapture,
                    onFlip = onFinish,
                    onMore = { },
                    primaryEnabled = true
                )'''
s = s[:start] + shared + s[end:]

# The shared CAMERA/GALLERY navigation remains the final bottom row, exactly like the normal camera.
p.write_text(s, encoding="utf-8")
print("Scanner finalized: no duplicate page/shutter controls, normal camera chrome retained, PAGES replaces FLIP.")
