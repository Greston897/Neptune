#!/usr/bin/env python3
"""Every lazy-list key must be minted by ListKeys, never assembled at the call site.

Why this gate exists: the album key was hand-built as `artist + album` in FOUR screens. Three were
converted to ListKeys and one (HomeScreen) was missed, while the commit message and PR claimed no
manual keys remained. Nothing checked that claim, so it stood. A collision throws
"Key ... was already used" from LazyColumn and takes the whole screen down, so the invariant belongs
in CI rather than in a reviewer's memory.

Scope: `key = ...` arguments in the UI layer. Each must mention ListKeys on the same line. All current
call sites are single-line; if one is ever wrapped, this fails loudly rather than silently passing.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

UI = Path("app/src/main/kotlin/com/neptune/player/ui")
KEY_ARG = re.compile(r"\bkey\s*=")


def main() -> int:
    if not UI.is_dir():
        print(f"FAIL: {UI} not found — wrong working directory?")
        return 1

    files = sorted(UI.rglob("*.kt"))
    offenders: list[str] = []
    checked = 0
    for path in files:
        if path.name == "ListKeys.kt":
            continue  # the minter itself
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if not KEY_ARG.search(line):
                continue
            checked += 1
            if "ListKeys." not in line:
                offenders.append(f"{path}:{lineno}: {stripped}")

    if offenders:
        print(f"FAIL: {len(offenders)} lazy-list key(s) built by hand instead of via ListKeys")
        for row in offenders:
            print(f"  {row}")
        print("  Route them through ListKeys so domain prefixing and the NUL separator stay testable.")
        return 1

    if checked == 0:
        print("FAIL: no key arguments found at all — the screens moved or the pattern is wrong")
        return 1

    print(f"OK: {checked} lazy-list keys all minted by ListKeys across {len(files)} UI sources")
    return 0


if __name__ == "__main__":
    sys.exit(main())
