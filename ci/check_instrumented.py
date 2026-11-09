#!/usr/bin/env python3
"""Gate the instrumented run: every declared test must have actually run, and none may fail.

Three distinct failure modes, three distinct messages:

1. A test asserted and failed          -> report the test names and their messages.
2. The emulator died mid-suite        -> results exist but fewer than the source declares. This
                                         used to slip through as a truncated-but-green run.
3. No results at all                  -> the build/install never reached the device.

The expected count is read from the @Test annotations in the sources rather than hardcoded, so
adding a test raises the bar automatically instead of leaving a stale floor behind.
"""
from __future__ import annotations

import glob
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SRC = Path("app/src/androidTest/kotlin")

# Flows that must never silently disappear from the run: each one guards a regression that shipped
# broken once. Named explicitly so a truncated suite cannot pass by happening to skip them.
REQUIRED = {
    "likedAndRecentCollectionsOpenAndReturn": "collections crashed on open (negative padding)",
    "importedAudioActuallyPlaysAndBackReturnsHome": "playback must advance from a user-imported file",
    "pillArtworkIsInsetFromTheLeftEdge": "pill cover was pressed against the left curve",
    "albumShelfActionOpensTheLibraryAlbumsTab": "shelf action opened an empty collection route",
    "sleepTimerOpensOnlyFromItsRowAndAlwaysCloses": "dock used to swallow the row tap",
    "trackWithoutArtworkRendersTheBrandFallback": "missing cover must show the app avatar",
    "removeFromPlaylistWorksAndSurvivesASecondTap": "playlist id was dereferenced at click time",
    "currentTrackRowKeepsTheSameGeometryAsAnyOtherRow": "the current row regained a tinted plate and shifted",
    "pillKeepsItsGeometryWhileAShortNoticeIsShown": "a pill notice resized or moved the now-playing capsule",
    "subscriptionScreenShowsWhatIsFreeAndCanBeOpened": "the free tier and the way into Plus must both be reachable",
    "planPageIsOnePageAndClosesInOneTap": "the plan page stacked copies of itself into a blurred smear",
    "customAccentOpensThePlanPageWithoutMergingWithTheOneUnderIt": "the plan page showed the appearance screen through itself",
    "pillAndItsNoticesFollowTheUserIntoSubScreens": "the pill (and every toast) vanished in Settings sub-screens",
    "sourcesSwitchBetweenThePhoneAndTheCatalogue": "4.0: the phone and the catalogue must both be addressable",
    "aCatalogueTrackIsFoundAndPlayedFromTheSameSearch": "4.0: one search field must find and play a catalogue track",
    "aSavedCatalogueTrackAppearsInItsOwnLibraryTab": "4.0: a saved stream must survive the screen it was saved on",
    "theChartScreenOpensFromHomeAndReportsItsState": "4.0: charts must open, and must fail visibly without a network",
    "theNextTrackStartsByItselfWhenOneEnds": "the queue must advance when a track ends, without a tap",
    "aBrokenTrackIsSkippedAndTheQueueCarriesOn": "an unplayable row must be skipped, not end the queue",
}


def declared_tests() -> dict[str, int]:
    counts: dict[str, int] = {}
    for path in sorted(SRC.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        n = len(re.findall(r"@Test\b", text))
        if n:
            counts[path.stem] = n
    return counts


def main() -> int:
    # The build tree lives on the CI runner, but a local checkout has the copied XML that the
    # pipeline committed as diagnostics. Falling back to it lets the gate be re-run and reviewed
    # away from the device instead of always answering "no results".
    files = sorted(glob.glob("app/build/outputs/androidTest-results/**/*.xml", recursive=True))
    if not files:
        files = sorted(glob.glob("ci/TEST-*.xml"))
    if not files:
        print("FAIL: no instrumented test results — the suite never reached a device")
        return 1

    suites = []
    for path in files:
        try:
            suites.append(ET.parse(path).getroot())
        except ET.ParseError as exc:
            print(f"FAIL: unreadable results in {path}: {exc}")
            return 1

    cases = [case for suite in suites for case in suite.iter("testcase")]
    ran = {case.get("name", "").split("[")[0] for case in cases}

    failures = []
    for case in cases:
        bad = case.find("failure") if case.find("failure") is not None else case.find("error")
        if bad is not None:
            detail = (bad.get("message") or bad.text or "").strip()
            failures.append((case.get("name", "?"), detail))
    if failures:
        print(f"FAIL: {len(failures)} instrumented test(s) failed")
        for name, detail in failures:
            print(f"  {name}")
            # An empty message is itself a signal: it usually means the device went away.
            print(f"    {detail[:1500] if detail else '(no message — suspect device/emulator loss)'}")
        return 1

    declared = declared_tests()
    expected = sum(declared.values())
    if expected == 0:
        print("FAIL: no @Test annotations found in sources — wrong working directory?")
        return 1

    missing_required = sorted(set(REQUIRED) - ran)
    if missing_required:
        print("FAIL: required regression tests did not run:")
        for name in missing_required:
            print(f"  {name} — guards: {REQUIRED[name]}")

    if len(cases) < expected:
        print(
            f"FAIL: suite truncated — {len(cases)} of {expected} declared tests reported.\n"
            f"  Declared per class: {declared}\n"
            "  The emulator most likely died mid-run; this is not a pass."
        )
        return 1

    if missing_required:
        return 1

    print(f"OK: {len(cases)} instrumented tests ran, 0 failures (declared {expected})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
