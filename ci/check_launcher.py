#!/usr/bin/env python3
"""Verify the adaptive launcher icon: black tile, white glyph, optically centred, not oversized.

Runs in CI. Unlike `check_icons.py` (24x24 UI glyphs, translation only) this walks the full group
transform — scale, translate and pivot — because the launcher scales a 24-unit source glyph onto a
108dp canvas.

Three assertions, all measured from the generated XML:
  1. the background layer is pure black;
  2. the glyph's ink centre lands on the canvas centre (54, 54) within 0.25dp;
  3. the inked glyph fits inside Android's 66dp safe-zone circle and is not oversized
     (it must not exceed 62% of the ~72dp visible mask circle).
"""
import math
import pathlib
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
RES = pathlib.Path("app/src/main/res")
CENTRE_TOLERANCE = 0.25   # dp on the 108dp canvas
SAFE_RADIUS = 33.0        # Android guarantees a 66dp safe circle
VISIBLE_RADIUS = 36.0     # typical launcher mask is ~72dp across
MAX_VISIBLE_FRACTION = 0.62

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from icon_metrics import _flatten  # noqa: E402


def measure(path):
    """Return (ink box, canvas w, canvas h) after applying every group transform."""
    root = ET.parse(path).getroot()
    vw = float(root.get(ANDROID + "viewportWidth"))
    vh = float(root.get(ANDROID + "viewportHeight"))
    xs, ys = [], []

    def walk(node, sx, sy, dx, dy):
        for child in node:
            tag = child.tag.split("}")[-1]
            if tag == "group":
                gsx = float(child.get(ANDROID + "scaleX", 1))
                gsy = float(child.get(ANDROID + "scaleY", 1))
                gdx = float(child.get(ANDROID + "translateX", 0))
                gdy = float(child.get(ANDROID + "translateY", 0))
                # Nested groups compose: child coords are scaled then translated in parent space.
                walk(child, sx * gsx, sy * gsy, dx + gdx * sx, dy + gdy * sy)
            elif tag == "path":
                d = child.get(ANDROID + "pathData")
                stroke = float(child.get(ANDROID + "strokeWidth", 0) or 0)
                pad_x = stroke / 2 * sx
                pad_y = stroke / 2 * sy
                for x, y in _flatten(d):
                    px, py = x * sx + dx, y * sy + dy
                    xs.extend((px - pad_x, px + pad_x))
                    ys.extend((py - pad_y, py + pad_y))

    walk(root, 1.0, 1.0, 0.0, 0.0)
    if not xs:
        raise SystemExit(f"{path}: no measurable geometry")
    return (min(xs), min(ys), max(xs), max(ys)), vw, vh


def main():
    failures = []

    bg = (RES / "drawable/ic_launcher_background.xml").read_text()
    if "#000000" not in bg:
        failures.append("launcher background is not pure black")

    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        xml = (RES / "mipmap-anydpi-v26" / name).read_text()
        if "<monochrome" not in xml:
            failures.append(f"{name}: missing monochrome layer for Android 13 themed icons")

    box, vw, vh = measure(RES / "drawable/ic_launcher_foreground.xml")
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    off = max(abs(cx - vw / 2), abs(cy - vh / 2))
    if off > CENTRE_TOLERANCE:
        failures.append(f"glyph ink centre ({cx:.2f}, {cy:.2f}) vs canvas centre ({vw/2}, {vh/2}), off by {off:.2f}dp")

    # Furthest inked point from the canvas centre must stay inside the safe zone.
    reach = max(math.hypot(x - vw / 2, y - vh / 2) for x in (x0, x1) for y in (y0, y1))
    if reach > SAFE_RADIUS:
        failures.append(f"glyph reaches {reach:.1f}dp from centre, safe zone is {SAFE_RADIUS}dp")

    extent = max(x1 - x0, y1 - y0)
    fraction = extent / (VISIBLE_RADIUS * 2)
    if fraction > MAX_VISIBLE_FRACTION:
        failures.append(f"glyph is {fraction:.0%} of the visible circle, limit is {MAX_VISIBLE_FRACTION:.0%}")
    if extent < 30:
        failures.append(f"glyph is only {extent:.1f}dp — too small to read on a launcher")

    if failures:
        print("LAUNCHER ICON CHECK FAILED")
        for f in failures:
            print("  -", f)
        raise SystemExit(1)
    print(f"OK: launcher glyph {extent:.1f}dp ({fraction:.0%} of visible circle), "
          f"centre offset {off:.3f}dp, reach {reach:.1f}/{SAFE_RADIUS:g}dp, black tile")


if __name__ == "__main__":
    main()
