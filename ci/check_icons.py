#!/usr/bin/env python3
"""Verify every bundled VectorDrawable is optically centred on its 24x24 viewport.

Runs in CI. This is the check that would have caught "иконки то ниже то выше": upstream Lucide
glyphs share a baseline rather than a centre, so six of them rendered visibly off-axis until the
converter started translating their ink box onto (12, 12).

The check re-measures the *generated* drawable (path data plus any group translation), not the
source SVG, so a regression in either the assets or the converter fails the build.
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
TOLERANCE = 0.06  # viewport units; ~0.06dp at 24dp, well below one physical pixel

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from icon_metrics import _flatten  # noqa: E402


def drawable_ink_centre(path):
    root = ET.parse(path).getroot()
    vw = float(root.get(ANDROID + "viewportWidth"))
    vh = float(root.get(ANDROID + "viewportHeight"))
    xs, ys = [], []

    def walk(node, dx, dy):
        for child in node:
            tag = child.tag.split('}')[-1]
            if tag == "group":
                walk(child,
                     dx + float(child.get(ANDROID + "translateX", 0)),
                     dy + float(child.get(ANDROID + "translateY", 0)))
            elif tag == "path":
                d = child.get(ANDROID + "pathData")
                stroke = float(child.get(ANDROID + "strokeWidth", 0) or 0)
                filled = child.get(ANDROID + "fillColor", "#00000000")
                opaque = filled.lower() not in ("#00000000", "none", "")
                # Ink extends half a stroke beyond the geometry on every side, so a uniform
                # stroke does not move the centre — but it must be included for the bounds check.
                pad = stroke / 2 if stroke else 0
                for x, y in _flatten(d):
                    xs.extend((x + dx - pad, x + dx + pad))
                    ys.extend((y + dy - pad, y + dy + pad))
                if opaque and not stroke:
                    for x, y in _flatten(d):
                        xs.append(x + dx)
                        ys.append(y + dy)
    walk(root, 0.0, 0.0)
    if not xs:
        raise SystemExit(f"{path}: no measurable geometry")
    return (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, vw, vh, (min(xs), min(ys), max(xs), max(ys))


def main():
    files = sorted(pathlib.Path("app/src/main/res/drawable").glob("ne_*.xml"))
    if len(files) < 49:
        raise SystemExit(f"expected at least 49 icons, found {len(files)}")
    failures = []
    worst = 0.0
    for f in files:
        cx, cy, vw, vh, box = drawable_ink_centre(f)
        off = max(abs(cx - vw / 2), abs(cy - vh / 2))
        worst = max(worst, off)
        if off > TOLERANCE:
            failures.append(f"{f.name}: ink centre ({cx:.3f}, {cy:.3f}) vs viewport centre ({vw/2}, {vh/2}), off by {off:.3f}")
        # Ink must also stay inside the viewport, or the glyph gets clipped at the box edge.
        x0, y0, x1, y1 = box
        if x0 < -0.6 or y0 < -0.6 or x1 > vw + 0.6 or y1 > vh + 0.6:
            failures.append(f"{f.name}: ink {tuple(round(v,2) for v in box)} overflows the {vw}x{vh} viewport")
    if failures:
        print("ICON CENTRING FAILED")
        for f in failures:
            print("  -", f)
        raise SystemExit(1)
    print(f"OK: {len(files)} icons centred (worst offset {worst:.4f} of {TOLERANCE} allowed)")


if __name__ == "__main__":
    main()
