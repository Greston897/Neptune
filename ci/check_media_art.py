#!/usr/bin/env python3
"""Gate the media-session artwork without third-party dependencies.

The PNG in `res/raw` is what the lock screen, the notification and "dynamic island" style overlays
draw when a track has no embedded cover. If it is missing, oversized or off-centre the media session
silently falls back to the play/pause glyph — the exact defect 2.9 fixes — so it is asserted here.

Decoding uses only the standard library (zlib + struct): CI has no Pillow, and installing one just to
read a header would make the gate depend on the network. Regenerate the asset with
`python3 ci/make_media_art.py`, which does use Pillow and runs on a developer machine.

Checks:
  1. the file exists and is a real PNG with the expected square dimensions;
  2. it stays inside a byte budget, because these bytes ride in every queue item's metadata bundle;
  3. the glyph's ink is optically centred on the tile;
  4. the glyph is present but not oversized, so a circular consumer mask cannot clip it.
"""
from __future__ import annotations

import pathlib
import struct
import sys
import zlib

PATH = pathlib.Path("app/src/main/res/raw/ne_media_art.png")
EXPECTED_SIDE = 512
BYTE_BUDGET = 64 * 1024
CENTRE_TOLERANCE = 2.0          # px on a 512px tile
MIN_INK_FRACTION = 0.35
MAX_INK_FRACTION = 0.72
INK_THRESHOLD = 40              # luminance above the near-black plate


def read_png(path: pathlib.Path) -> tuple[int, int, list[bytes]]:
    """Return (width, height, unfiltered scanlines) for an 8-bit RGB/RGBA/palette PNG."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos = 8
    width = height = bit_depth = color_type = 0
    palette: list[tuple[int, int, int]] = []
    idat = bytearray()
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if interlace:
                raise ValueError("interlaced PNG is not supported by this gate")
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
    if bit_depth != 8:
        raise ValueError(f"expected 8-bit channels, got {bit_depth}")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    rows: list[bytes] = []
    previous = bytearray(stride)
    pos = 0
    for _ in range(height):
        filter_type = raw[pos]
        line = bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = previous[i]
            c = previous[i - channels] if i >= channels else 0
            if filter_type == 1:
                line[i] = (line[i] + a) & 0xFF
            elif filter_type == 2:
                line[i] = (line[i] + b) & 0xFF
            elif filter_type == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 0xFF
            elif filter_type == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"unknown PNG filter {filter_type}")
        rows.append(bytes(line))
        previous = line
    return width, height, [bytes(r) for r in rows], channels, color_type, palette


def luminance(row: bytes, x: int, channels: int, color_type: int, palette) -> int:
    if color_type == 3:
        index = row[x]
        if index >= len(palette):
            return 0
        r, g, b = palette[index]
        return (r * 299 + g * 587 + b * 114) // 1000
    if color_type in (0, 4):
        return row[x * channels]
    r, g, b = row[x * channels], row[x * channels + 1], row[x * channels + 2]
    return (r * 299 + g * 587 + b * 114) // 1000


def main() -> int:
    if not PATH.exists():
        print(f"FAIL: {PATH} is missing — run ci/make_media_art.py")
        return 1
    size = PATH.stat().st_size
    if size > BYTE_BUDGET:
        print(f"FAIL: artwork is {size} bytes, budget is {BYTE_BUDGET}")
        return 1
    try:
        width, height, rows, channels, color_type, palette = read_png(PATH)
    except Exception as exc:                                  # noqa: BLE001 - gate reports any decode failure
        print(f"FAIL: cannot decode artwork: {exc}")
        return 1
    if (width, height) != (EXPECTED_SIDE, EXPECTED_SIDE):
        print(f"FAIL: artwork is {width}x{height}, expected {EXPECTED_SIDE}x{EXPECTED_SIDE}")
        return 1

    xs: list[int] = []
    ys: list[int] = []
    for y, row in enumerate(rows):
        for x in range(width):
            if luminance(row, x, channels, color_type, palette) > INK_THRESHOLD:
                xs.append(x)
                ys.append(y)
    if not xs:
        print("FAIL: artwork has no glyph ink")
        return 1
    left, right, top, bottom = min(xs), max(xs), min(ys), max(ys)
    cx, cy = (left + right) / 2, (top + bottom) / 2
    offset = max(abs(cx - width / 2), abs(cy - height / 2))
    if offset > CENTRE_TOLERANCE:
        print(f"FAIL: ink centre off by {offset:.2f}px (tolerance {CENTRE_TOLERANCE})")
        return 1
    fraction = max(right - left + 1, bottom - top + 1) / width
    if not (MIN_INK_FRACTION <= fraction <= MAX_INK_FRACTION):
        print(f"FAIL: glyph fills {fraction:.0%} of the tile, expected {MIN_INK_FRACTION:.0%}–{MAX_INK_FRACTION:.0%}")
        return 1
    print(f"OK: media artwork {width}x{height}, {size} bytes, ink {fraction:.0%}, centre offset {offset:.2f}px")
    return 0


if __name__ == "__main__":
    sys.exit(main())
