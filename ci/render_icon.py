"""Rasterise a stroke-based SVG with PIL for a quick visual check (dev tool, not part of the build).

Reuses the same path flattener as the centring gate, so what you see here is the same geometry the
gate measures.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

from icon_metrics import _flatten

SS = 4  # supersampling


def render(svg_path: Path, size: int = 512, bg=(0, 0, 0, 255), fg=(255, 255, 255, 255)):
    root = ET.parse(svg_path).getroot()
    _, _, vw, vh = (float(v) for v in root.attrib["viewBox"].split())
    sw = float(root.attrib.get("stroke-width", 2))
    strokes = []
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        a = el.attrib
        if tag == "path":
            strokes.append(_flatten(a["d"]))
        elif tag == "circle":
            cx, cy, r = float(a["cx"]), float(a["cy"]), float(a["r"])
            strokes.append(_flatten(f"M{cx-r} {cy}A{r} {r} 0 1 0 {cx+r} {cy}A{r} {r} 0 1 0 {cx-r} {cy}Z"))
        elif tag == "line":
            strokes.append([(float(a["x1"]), float(a["y1"])), (float(a["x2"]), float(a["y2"]))])
        elif tag in ("polyline", "polygon"):
            v = [float(t) for t in a["points"].replace(",", " ").split()]
            pts = list(zip(v[::2], v[1::2]))
            strokes.append(pts + [pts[0]] if tag == "polygon" else pts)
        elif tag == "rect":
            x, y = float(a.get("x", 0)), float(a.get("y", 0))
            w, h = float(a["width"]), float(a["height"])
            strokes.append([(x, y), (x + w, y), (x + w, y + h), (x, y + h), (x, y)])

    big = size * SS
    img = Image.new("RGBA", (big, big), bg)
    d = ImageDraw.Draw(img)
    scale = big / max(vw, vh)
    w = max(1, round(sw * scale))
    r = w / 2
    for pts in strokes:
        scaled = [(x * scale, y * scale) for x, y in pts]
        if len(scaled) > 1:
            d.line(scaled, fill=fg, width=w, joint="curve")
        # round caps + joins
        for x, y in (scaled[0], scaled[-1]):
            d.ellipse([x - r, y - r, x + r, y + r], fill=fg)
    return img.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    out = render(Path(sys.argv[1]), int(sys.argv[3]) if len(sys.argv) > 3 else 512)
    out.save(sys.argv[2])
    print(f"wrote {sys.argv[2]}")
