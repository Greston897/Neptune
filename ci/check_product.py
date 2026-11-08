#!/usr/bin/env python3
"""Design invariants that a compiler cannot catch.

Each promise below was made in 3.0 and is one careless edit away from silently regressing:

1. **No fill behind the current track.** The selected row used an accent-tinted rounded rectangle
   inside an already-rounded glass panel, which read as a compressed, misaligned third box. The row
   must express selection through colour only. This gate fails if a `TrackRow`-style row paints a
   background again.

2. **The avatar has no colour of its own.** The mark paints with the accent, and the profile no
   longer offers a separate palette (`avatarTone` is gone from the model). A grid of preset colours
   for the avatar could only disagree with the accent, so its return must be deliberate, not a merge
   accident.

3. **Confirmations borrow the pill, not the snackbar.** Messages about "Добавлено…" are supposed to
   appear inside the now-playing pill for two seconds (`ToastDurationMs`) and then hand the slot
   back. A state helper that quietly posts to the snackbar again would still look fine in code review
   but would put the notice at the wrong end of the screen.

The checks read source text on purpose: they assert *intent that is visible in code*, and they fail
with the file and line so the fix is obvious.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path("app/src/main/kotlin/com/neptune/player")
FAILS: list[str] = []


def fail(message: str) -> None:
    FAILS.append(message)


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check_no_row_fill() -> None:
    """TrackRow must not paint a background: accent colour carries the selection."""
    path = ROOT / "ui/Components.kt"
    source = text(path)
    if "fun TrackRow(" not in source:
        fail(f"{path}: TrackRow is gone — did it move? Update this gate together with the move")
        return
    # Isolate the TrackRow composable body (up to the next top-level @Composable).
    start = source.index("fun TrackRow(")
    tail = source[start:]
    end = tail.find("\n@Composable")
    body = tail if end < 0 else tail[:end]
    for pattern, why in [
        (r"\.background\(", "the row must not paint a tinted plate behind the current track"),
        (r"Surface\(", "the row must not introduce another surface"),
    ]:
        if re.search(pattern, body):
            fail(f"{path}: TrackRow still contains {pattern!r} — {why}")


def check_avatar_has_no_colour_setting() -> None:
    model = ROOT / "model/Settings.kt"
    settings = text(model)
    if "avatarTone" in settings:
        fail(f"{model}: avatarTone is back — the avatar follows the accent, a second palette only disagrees with it")
    artwork = ROOT / "ui/Artwork.kt"
    source = text(artwork)
    if re.search(r"Accent\.of\(s\.avatarTone\)", source):
        fail(f"{artwork}: ProfileAvatar reads a separate tone again")


def check_toasts_live_in_the_pill() -> None:
    toast = ROOT / "ui/Toast.kt"
    if not toast.is_file():
        fail(f"{toast}: toast surface is missing — confirmations have nowhere to appear")
        return
    source = text(toast)
    match = re.search(r"const val ToastDurationMs\s*=\s*(\d+)", source)
    if not match:
        fail(f"{toast}: ToastDurationMs must be an explicit constant so the duration stays reviewable")
    else:
        duration = int(match.group(1))
        if duration != 2000:
            fail(f"{toast}: a confirmation should borrow the pill for 2000 ms, found {duration}")
    chrome = text(ROOT / "ui/Chrome.kt")
    if "toast: ToastNotice?" not in chrome:
        fail(f"{ROOT / 'ui/Chrome.kt'}: TrackPill must accept the notice — otherwise it renders somewhere else")
    state = text(ROOT / "state/NeptuneState.kt")
    if "SnackbarHostState" in state or "receiveAsFlow" in state:
        fail(
            f"{ROOT / 'state/NeptuneState.kt'}: the snackbar channel is back. "
            "Every confirmation belongs in the pill so the notice appears next to the track, not under the dock."
        )
    app = text(ROOT / "NeptuneApp.kt")
    if "SnackbarHost" in app:
        fail(f"{ROOT / 'NeptuneApp.kt'}: a snackbar host is rendered again — confirmations must use the pill")


def check_deletion_requires_confirmation() -> None:
    sheet = text(ROOT / "ui/TrackActions.kt")
    if "ConfirmDestructiveDialog" not in sheet:
        fail(
            f"{ROOT / 'ui/TrackActions.kt'}: removing a track must ask first. "
            "A destructive row that acts immediately on a mis-tap is exactly the bug this guards."
        )
    if "removeTrack = true" not in sheet:
        fail(f"{ROOT / 'ui/TrackActions.kt'}: the removal row must open the confirmation, not act directly")
    if "releasePersistableUriPermission" not in text(ROOT / "data/LibraryRepository.kt"):
        fail(
            f"{ROOT / 'data/LibraryRepository.kt'}: forgetting an import must release the picker grant, "
            "otherwise the entry comes back on the next refresh"
        )


def check_plan_gating_is_central() -> None:
    state = text(ROOT / "state/NeptuneState.kt")
    if "fun gate(feature: PlanFeature" not in state:
        fail(f"{ROOT / 'state/NeptuneState.kt'}: the plan gate must stay in one place")
    plan = ROOT / "model/Plan.kt"
    if not plan.is_file():
        fail(f"{plan}: plan model is missing")
        return
    source = text(plan)
    if 'Playback("Плеер целиком"' not in source or "free: Boolean" not in source:
        fail(f"{plan}: the player must be declared free in the feature table, not gated at a call site")
    # Playback, Lyrics must be free: the contract that nobody pays to hear their own files.
    for feature in ("Playback", "Lyrics"):
        row = re.search(rf"{feature}\(\"[^\"]*\", \"[^\"]*\",\s*(true|false)\)", source)
        if not row:
            fail(f"{plan}: cannot read the free flag of {feature} — keep the table machine-readable")
        elif row.group(1) != "true":
            fail(f"{plan}: {feature} became a paid feature — playback and karaoke must stay free")


def check_lyrics_are_time_driven() -> None:
    screen = text(ROOT / "ui/screens/LyricsScreen.kt")
    if "activeIndex(playback.positionMs)" not in screen:
        fail(f"{ROOT / 'ui/screens/LyricsScreen.kt'}: the active line must be derived from the playback position")
    if "animateScrollToItem" not in screen:
        fail(f"{ROOT / 'ui/screens/LyricsScreen.kt'}: the list must follow the active line")
    if re.search(r"while \(true\)|delay\(", screen):
        fail(f"{ROOT / 'ui/screens/LyricsScreen.kt'}: a second clock would drift out of sync with audio")
    parser = text(ROOT / "model/Lyrics.kt")
    for needed in ("SYLT", "USLT", "parseLrc", "activeIndex"):
        if needed not in parser:
            fail(f"{ROOT / 'model/Lyrics.kt'}: missing {needed} — the extractor lost a source")


def main() -> int:
    check_no_row_fill()
    check_avatar_has_no_colour_setting()
    check_toasts_live_in_the_pill()
    check_deletion_requires_confirmation()
    check_plan_gating_is_central()
    check_lyrics_are_time_driven()
    if FAILS:
        print("PRODUCT INVARIANTS FAILED")
        for row in FAILS:
            print("  -", row)
        return 1
    print("OK: product invariants hold (no row plate, no avatar palette, pill notices, confirmed deletion, free player, timed lyrics)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
