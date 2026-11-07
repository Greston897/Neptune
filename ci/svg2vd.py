#!/usr/bin/env python3
"""Convert Lucide 24x24 stroke SVGs into Android VectorDrawable XML.

Lucide is ISC-licensed; see NOTICE.md. Shapes are converted verbatim so every glyph
keeps the upstream optical grid: 24x24 viewport, 2dp stroke, round caps/joins.
"""
import pathlib, re, xml.etree.ElementTree as ET

SRC = pathlib.Path("ci/lucide")
DST = pathlib.Path("app/src/main/res/drawable")
DST.mkdir(parents=True, exist_ok=True)

def num(v):
    f = float(v)
    return str(int(f)) if f == int(f) else ("%g" % f)

def circle_path(cx, cy, r):
    cx, cy, r = float(cx), float(cy), float(r)
    return (f"M{num(cx-r)},{num(cy)}a{num(r)},{num(r)} 0 1,0 {num(2*r)},0"
            f"a{num(r)},{num(r)} 0 1,0 {num(-2*r)},0")

def rect_path(x, y, w, h, rx):
    x, y, w, h = float(x), float(y), float(w), float(h)
    rx = float(rx or 0)
    if rx <= 0:
        return f"M{num(x)},{num(y)}h{num(w)}v{num(h)}h{num(-w)}z"
    rx = min(rx, w/2, h/2)
    return (f"M{num(x+rx)},{num(y)}h{num(w-2*rx)}a{num(rx)},{num(rx)} 0 0 1 {num(rx)},{num(rx)}"
            f"v{num(h-2*rx)}a{num(rx)},{num(rx)} 0 0 1 {num(-rx)},{num(rx)}"
            f"h{num(-(w-2*rx))}a{num(rx)},{num(rx)} 0 0 1 {num(-rx)},{num(-rx)}"
            f"v{num(-(h-2*rx))}a{num(rx)},{num(rx)} 0 0 1 {num(rx)},{num(-rx)}z")

def line_path(x1, y1, x2, y2):
    return f"M{num(x1)},{num(y1)}L{num(x2)},{num(y2)}"

def convert(svg: pathlib.Path, name: str, filled: bool, stroke: str = "2"):
    root = ET.parse(svg).getroot()
    assert root.attrib.get("viewBox") == "0 0 24 24", (svg, root.attrib)
    body = []
    for el in root:
        tag = el.tag.split('}')[-1]
        a = el.attrib
        local_fill = a.get("fill", "none") != "none"
        if tag == "path":
            d = a["d"]
        elif tag == "circle":
            d = circle_path(a["cx"], a["cy"], a["r"])
        elif tag == "rect":
            d = rect_path(a.get("x", 0), a.get("y", 0), a["width"], a["height"], a.get("rx", 0))
        elif tag == "line":
            d = line_path(a["x1"], a["y1"], a["x2"], a["y2"])
        else:
            raise SystemExit(f"unsupported <{tag}> in {svg}")
        solid = filled or local_fill
        attrs = [f'android:pathData="{d}"']
        if solid:
            # A solid glyph keeps a hairline stroke so corners stay optically round.
            attrs += ['android:fillColor="#FFFFFFFF"', 'android:strokeColor="#FFFFFFFF"',
                      'android:strokeWidth="1"','android:strokeLineJoin="round"','android:strokeLineCap="round"']
        else:
            attrs += ['android:strokeColor="#FFFFFFFF"', f'android:strokeWidth="{stroke}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"',
                      'android:fillColor="#00000000"']
        body.append("    <path\n        " + "\n        ".join(attrs) + " />")
    xml = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="24dp"\n    android:height="24dp"\n'
           '    android:viewportWidth="24"\n    android:viewportHeight="24">\n' + "\n".join(body) + "\n</vector>\n")
    (DST / f"ne_{name}.xml").write_text(xml)

# Transport/selection glyphs read better solid; everything else stays stroke-only.
SOLID = {"play", "pause", "next", "prev", "heart_fill"}
count = 0
for svg in sorted(SRC.glob("*.svg")):
    base = svg.stem
    convert(svg, base, filled=base in SOLID)
    count += 1
    if base == "heart":
        convert(svg, "heart_fill", filled=True); count += 1
    if base in {"home", "search", "library", "settings"}:
        # Optically heavier twin: the selected tab reads as "filled" without a colour-only cue.
        convert(svg, base + "_bold", filled=False, stroke="2.6"); count += 1
print("wrote", count, "vector drawables")
