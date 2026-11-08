#!/usr/bin/env python3
"""Generate the media-session artwork PNG from the same `orbit` glyph as the launcher.

Why a shipped PNG instead of rendering at runtime
------------------------------------------------
The media session has to hand artwork to *other* processes: the notification, the lock screen,
Android Auto and "dynamic island" style overlays. `BitmapLoader.loadBitmapFromMetadata` checks
`artworkData` first, so attaching real bytes guarantees those surfaces have something to draw —
without it they fall back to the playback-state glyph, which is the pause icon users complained
about.

Reading a pre-baked PNG keeps that guarantee without allocating a Bitmap and running a Canvas on the
main thread for every queue entry, and the mark stays exactly the launcher's geometry because both
are generated from `ci/launcher/orbit.svg`.

Output: app/src/main/res/raw/ne_media_art.png (512x512, ~9 KB).

`res/raw` rather than `drawable` on purpose: the bytes are read with `openRawResource` and handed
to the media session untouched, so Android must not rescale or re-encode them for density buckets.
"""
import pathlib
import sys

import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

from icon_metrics import _flatten


def ink_box(image):
    """Pixel-space ink extent of a rendered RGBA image, or None when fully transparent.

    `icon_metrics.ink_box` measures an SVG's geometry; here the glyph is already rasterised, so the
    alpha channel is the ground truth and no path flattening is needed.
    """
    return image.getchannel("A").getbbox()

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "ci/launcher/orbit.svg"
OUT = ROOT / "app/src/main/res/raw/ne_media_art.png"

SIZE = 512
# Ink extent as a fraction of the tile. The launcher keeps its glyph inside a 66dp safe circle
# because a mask can crop it; media surfaces crop to a circle or a rounded square too, so the same
# conservative fraction is used here.
INK_FRACTION = 0.58
BACKGROUND = (10, 10, 10, 255)
FOREGROUND = (245, 245, 247, 255)


SS = 4  # supersampling factor; the tile is downscaled with LANCZOS for clean edges


def strokes():
    """Polyline geometry of every stroked shape in the source glyph, in viewBox units."""
    root = ET.parse(SRC).getroot()
    width = float(root.attrib.get("stroke-width", 2))
    paths = []
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        a = el.attrib
        if tag == "path":
            paths.append(_flatten(a["d"]))
        elif tag == "circle":
            cx, cy, r = float(a["cx"]), float(a["cy"]), float(a["r"])
            paths.append(_flatten(
                f"M{cx - r} {cy}A{r} {r} 0 1 0 {cx + r} {cy}A{r} {r} 0 1 0 {cx - r} {cy}Z"))
        elif tag == "line":
            paths.append([(float(a["x1"]), float(a["y1"])), (float(a["x2"]), float(a["y2"]))])
    return width, paths


def rasterise(side: int) -> Image.Image:
    """Draw the glyph by stamping a disc at every sample along each stroke.

    PIL's `line(joint="curve")` leaves wedge-shaped gaps where a thick stroke turns sharply — on the
    orbit glyph's small rings that showed up as dark radial spokes through the ink. Stamping a full
    disc at each densely sampled point makes the union exact, so joins and caps are genuinely round.
    """
    sw, paths = strokes()
    root = ET.parse(SRC).getroot()
    _, _, vw, vh = (float(v) for v in root.attrib["viewBox"].split())
    big = side * SS
    scale = big / max(vw, vh)
    radius = sw * scale / 2.0
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for pts in paths:
        scaled = [(x * scale, y * scale) for x, y in pts]
        if len(scaled) > 1:
            d.line(scaled, fill=FOREGROUND, width=max(1, round(radius * 2)))
        for x, y in scaled:
            d.ellipse([x - radius, y - radius, x + radius, y + radius], fill=FOREGROUND)
    return img.resize((side, side), Image.LANCZOS)


def build() -> Image.Image:
    # Render the glyph on transparent, then scale by its measured ink box so the optical size is
    # identical to the launcher's regardless of the source viewBox padding.
    probe = rasterise(SIZE)
    box = ink_box(probe)
    if box is None:
        raise SystemExit("FAIL: glyph rendered empty")
    glyph = probe.crop(box)

    target = round(SIZE * INK_FRACTION)
    scale = target / max(glyph.width, glyph.height)
    glyph = glyph.resize(
        (max(1, round(glyph.width * scale)), max(1, round(glyph.height * scale))), Image.LANCZOS,
    )

    tile = Image.new("RGBA", (SIZE, SIZE), BACKGROUND)
    # Centre by ink, not by canvas, so the mark does not sit visually high or left.
    tile.alpha_composite(glyph, ((SIZE - glyph.width) // 2, (SIZE - glyph.height) // 2))
    return tile


def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    # The tile is opaque (black plate + solid glyph), so a small adaptive palette is lossless to the
    # eye and cuts the payload by an order of magnitude. That matters because these bytes ride in
    # every queue item's metadata bundle.
    image = build().convert("RGB").quantize(colors=32, method=Image.MEDIANCUT)
    image.save(OUT, optimize=True)
    written = OUT.stat().st_size
    # A media metadata bundle crosses Binder for every queue item; keep the payload small enough
    # that a long queue stays cheap even though BundleListRetriever chunks the transfer.
    if written > 64 * 1024:
        print(f"FAIL: artwork is {written} bytes, budget is 65536")
        return 1
    # Measure the glyph against the plate: everything brighter than the background counts as ink.
    grey = Image.open(OUT).convert("L")
    box = grey.point(lambda v: 255 if v > 40 else 0).getbbox()
    left, top, right, bottom = box
    cx, cy = (left + right) / 2, (top + bottom) / 2
    offset = max(abs(cx - SIZE / 2), abs(cy - SIZE / 2))
    if offset > 1.5:
        print(f"FAIL: ink centre off by {offset:.2f}px")
        return 1
    print(f"OK: media artwork {SIZE}x{SIZE}, {written} bytes, centre offset {offset:.2f}px")
    return 0


if __name__ == "__main__":
    sys.exit(main())
