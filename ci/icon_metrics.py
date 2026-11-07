#!/usr/bin/env python3
"""Measure the true ink centre of every Lucide glyph.

A VectorDrawable is laid out on its 24x24 viewport, but the *ink* inside that viewport is rarely
centred: upstream glyphs are drawn to a shared baseline, not to a shared optical centre. Rendering
them verbatim in a square box therefore makes some icons sit high, some low, some left — exactly
the "иконки то ниже то выше" symptom.

This module parses SVG path geometry (including arcs and beziers), computes the tight ink box and
returns the translation that puts its centre on (12, 12). Stroke width is uniform on all sides, so
it does not move the centre and is intentionally ignored.
"""
import math
import re
import xml.etree.ElementTree as ET

_NUM = re.compile(r"[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?")
_CMDS = set("MmLlHhVvCcSsQqTtAaZz")


def _tokenize(d):
    """Yield ('cmd', letter) and ('num', float). Arc flags are single digits, even when packed."""
    i, n = 0, len(d)
    cmd = None
    while i < n:
        c = d[i]
        if c in _CMDS:
            cmd = c
            yield ("cmd", c)
            i += 1
        elif c in " ,\t\r\n":
            i += 1
        else:
            m = _NUM.match(d, i)
            if not m:
                raise ValueError(f"bad path data at {i}: {d[i:i+20]!r}")
            yield ("num", float(m.group()))
            i = m.end()
    return


def _flatten(d, steps=64):
    """Return the list of points traced by the path, sampling curves densely."""
    toks = list(_tokenize(d))
    pts = []
    i = 0
    cmd = None
    cx = cy = 0.0
    sx = sy = 0.0
    prev_c2 = None
    prev_q = None
    prev_cmd = None

    def take():
        nonlocal i
        v = toks[i][1]
        i += 1
        return v

    def flag():
        """Arc flags may be packed against the next number ("01-1 1")."""
        nonlocal i
        t, v = toks[i]
        i += 1
        return int(v)

    while i < len(toks):
        kind, val = toks[i]
        if kind == "cmd":
            cmd = val
            i += 1
            if cmd in "Zz":
                cx, cy = sx, sy
                pts.append((cx, cy))
                prev_c2 = prev_q = None
                prev_cmd = cmd
                continue
        if cmd is None:
            raise ValueError("path data must start with a command")
        rel = cmd.islower()
        c = cmd.upper()

        if c == "M":
            x, y = take(), take()
            cx, cy = (cx + x, cy + y) if rel else (x, y)
            sx, sy = cx, cy
            pts.append((cx, cy))
            cmd = "l" if rel else "L"
            prev_c2 = prev_q = None
        elif c == "L":
            x, y = take(), take()
            nx, ny = (cx + x, cy + y) if rel else (x, y)
            pts.append((nx, ny))
            cx, cy = nx, ny
            prev_c2 = prev_q = None
        elif c == "H":
            x = take()
            cx = cx + x if rel else x
            pts.append((cx, cy))
            prev_c2 = prev_q = None
        elif c == "V":
            y = take()
            cy = cy + y if rel else y
            pts.append((cx, cy))
            prev_c2 = prev_q = None
        elif c in ("C", "S"):
            if c == "C":
                x1, y1, x2, y2, x, y = (take() for _ in range(6))
                x1, y1 = (cx + x1, cy + y1) if rel else (x1, y1)
            else:
                x2, y2, x, y = (take() for _ in range(4))
                if prev_cmd and prev_cmd.upper() in ("C", "S") and prev_c2:
                    x1, y1 = 2 * cx - prev_c2[0], 2 * cy - prev_c2[1]
                else:
                    x1, y1 = cx, cy
            x2, y2 = (cx + x2, cy + y2) if rel else (x2, y2)
            nx, ny = (cx + x, cy + y) if rel else (x, y)
            for s in range(1, steps + 1):
                t = s / steps
                u = 1 - t
                pts.append((
                    u**3 * cx + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * nx,
                    u**3 * cy + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * ny,
                ))
            prev_c2 = (x2, y2)
            prev_q = None
            cx, cy = nx, ny
        elif c in ("Q", "T"):
            if c == "Q":
                x1, y1, x, y = (take() for _ in range(4))
                x1, y1 = (cx + x1, cy + y1) if rel else (x1, y1)
            else:
                x, y = take(), take()
                if prev_cmd and prev_cmd.upper() in ("Q", "T") and prev_q:
                    x1, y1 = 2 * cx - prev_q[0], 2 * cy - prev_q[1]
                else:
                    x1, y1 = cx, cy
            nx, ny = (cx + x, cy + y) if rel else (x, y)
            for s in range(1, steps + 1):
                t = s / steps
                u = 1 - t
                pts.append((u * u * cx + 2 * u * t * x1 + t * t * nx,
                            u * u * cy + 2 * u * t * y1 + t * t * ny))
            prev_q = (x1, y1)
            prev_c2 = None
            cx, cy = nx, ny
        elif c == "A":
            rx, ry, rot = abs(take()), abs(take()), take()
            fa, fs = flag(), flag()
            x, y = take(), take()
            nx, ny = (cx + x, cy + y) if rel else (x, y)
            pts.extend(_arc_points(cx, cy, nx, ny, rx, ry, rot, fa, fs, steps))
            cx, cy = nx, ny
            prev_c2 = prev_q = None
        else:
            raise ValueError(f"unsupported command {cmd}")
        prev_cmd = cmd
    return pts


def _arc_points(x1, y1, x2, y2, rx, ry, rot_deg, fa, fs, steps):
    if rx == 0 or ry == 0 or (x1 == x2 and y1 == y2):
        return [(x2, y2)]
    phi = math.radians(rot_deg)
    cos_p, sin_p = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p = cos_p * dx + sin_p * dy
    y1p = -sin_p * dx + cos_p * dy
    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1:
        s = math.sqrt(lam)
        rx *= s
        ry *= s
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if fa == fs:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    ccx = cos_p * cxp - sin_p * cyp + (x1 + x2) / 2
    ccy = sin_p * cxp + cos_p * cyp + (y1 + y2) / 2

    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        ln = math.hypot(ux, uy) * math.hypot(vx, vy)
        a = math.acos(max(-1.0, min(1.0, dot / ln))) if ln else 0.0
        return -a if ux * vy - uy * vx < 0 else a

    th1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dth = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dth > 0:
        dth -= 2 * math.pi
    elif fs and dth < 0:
        dth += 2 * math.pi
    out = []
    for s in range(1, steps + 1):
        t = th1 + dth * s / steps
        ex, ey = rx * math.cos(t), ry * math.sin(t)
        out.append((cos_p * ex - sin_p * ey + ccx, sin_p * ex + cos_p * ey + ccy))
    return out


def _shape_points(el):
    tag = el.tag.split('}')[-1]
    a = el.attrib
    if tag == "path":
        return _flatten(a["d"])
    if tag == "circle":
        cx, cy, r = float(a["cx"]), float(a["cy"]), float(a["r"])
        return [(cx + r * math.cos(t), cy + r * math.sin(t))
                for t in [i * 2 * math.pi / 720 for i in range(720)]]
    if tag == "ellipse":
        cx, cy = float(a["cx"]), float(a["cy"])
        rx, ry = float(a["rx"]), float(a["ry"])
        return [(cx + rx * math.cos(t), cy + ry * math.sin(t))
                for t in [i * 2 * math.pi / 720 for i in range(720)]]
    if tag == "rect":
        x, y = float(a.get("x", 0)), float(a.get("y", 0))
        w, h = float(a["width"]), float(a["height"])
        return [(x, y), (x + w, y), (x, y + h), (x + w, y + h)]
    if tag == "line":
        return [(float(a["x1"]), float(a["y1"])), (float(a["x2"]), float(a["y2"]))]
    if tag in ("polyline", "polygon"):
        v = [float(t) for t in _NUM.findall(a["points"])]
        return list(zip(v[0::2], v[1::2]))
    raise SystemExit(f"unsupported <{tag}>")


def ink_box(svg_path):
    root = ET.parse(svg_path).getroot()
    assert root.attrib.get("viewBox") == "0 0 24 24", (svg_path, root.attrib)
    xs, ys = [], []
    for el in root:
        if el.tag.split('}')[-1] in ("title", "desc"):
            continue
        for x, y in _shape_points(el):
            xs.append(x)
            ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def centre_offset(svg_path):
    """Translation (dx, dy) that moves the glyph's ink centre onto the viewport centre."""
    x0, y0, x1, y1 = ink_box(svg_path)
    return 12.0 - (x0 + x1) / 2.0, 12.0 - (y0 + y1) / 2.0


if __name__ == "__main__":
    import pathlib
    rows = []
    for p in sorted(pathlib.Path("ci/lucide").glob("*.svg")):
        dx, dy = centre_offset(p)
        rows.append((max(abs(dx), abs(dy)), p.stem, dx, dy))
    rows.sort(reverse=True)
    print(f"{'glyph':16} {'dx':>7} {'dy':>7}")
    for _, name, dx, dy in rows:
        flag = "  <-- off centre" if max(abs(dx), abs(dy)) > 0.25 else ""
        print(f"{name:16} {dx:7.3f} {dy:7.3f}{flag}")
    print(f"\n{sum(1 for r in rows if r[0] > 0.25)} of {len(rows)} glyphs are off centre by > 0.25 units")
