from pathlib import Path

p = Path("app/src/main/java/com/devlinguistpro/mediatoolbox/ScannerActivity.kt")
s = p.read_text(encoding="utf-8")

def balanced_brace(text, start):
    op = text.find("{", start)
    if op < 0: raise SystemExit("Scanner layout block opening brace not found")
    depth = 0
    for i in range(op, len(text)):
        if text[i] == "{": depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0: return start, i + 1
    raise SystemExit("Scanner layout block has unbalanced braces")

header = 'Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {'
h = s.find(header)
if h >= 0:
    a, b = balanced_brace(s, h)
    s = s[:a] + s[b:]

page_strip = 'if (pages.isNotEmpty()) {'
ps = s.find(page_strip)
controls_comment = '// ScannerSectionControls: MORE | shutter | PAGES'
cc = s.find(controls_comment, ps)
if ps >= 0 and cc > ps:
    s = s[:ps] + s[cc:]

comment = '// ScannerSectionControls: MORE | shutter | PAGES'
cs = s.find(comment)
if cs >= 0:
    row = s.find('Row(', cs)
    if row < 0: raise SystemExit("Generated scanner controls Row not found")
    a, b = balanced_brace(s, row)
    shared = '''CameraSectionControls(
                    mode = CameraSectionMode.SCAN,
                    onModeSelected = { scannerModeAction(context, it) },
                    onPrimaryAction = onCapture,
                    onFlip = onFinish,
                    onMore = { },
                    primaryEnabled = true
                )'''
    s = s[:cs] + shared + s[b:]

p.write_text(s, encoding="utf-8")
print("Scanner finalized: normal camera layout, shared modes, shared bottom navigation, PAGES replaces FLIP.")
