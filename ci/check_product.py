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
    # ...and the *only* place the pill is composed has to pass that notice, or the parameter is dead
    # and every confirmation silently renders nowhere. This is exactly the regression a parameter
    # rename produced: the pill survived, the notices did not.
    app_source = text(ROOT / "NeptuneApp.kt")
    for call in re.finditer(r"TrackPill\((.*?)\)\s*\n\s*\}", app_source, re.S):
        if "toast = toast" not in call.group(1):
            fail(
                f"{ROOT / 'NeptuneApp.kt'}: TrackPill is composed without `toast = toast` — the pill "
                "renders, but every confirmation in the app has nowhere to appear."
            )
    state = text(ROOT / "state/NeptuneState.kt")
    if "SnackbarHostState" in state or "receiveAsFlow" in state:
        fail(
            f"{ROOT / 'state/NeptuneState.kt'}: the snackbar channel is back. "
            "Every confirmation belongs in the pill so the notice appears next to the track, not under the dock."
        )
    app = text(ROOT / "NeptuneApp.kt")
    if "SnackbarHost" in app:
        fail(f"{ROOT / 'NeptuneApp.kt'}: a snackbar host is rendered again — confirmations must use the pill")


def check_paywall_is_one_opaque_page() -> None:
    """Two overlapping copies of the plan screen smeared their blurred panels together."""
    app = text(ROOT / "NeptuneApp.kt")
    renders = app.count("SubscriptionScreen(")
    if renders != 1:
        fail(
            f"{ROOT / 'NeptuneApp.kt'}: the plan screen is composed {renders} time(s). "
            "It must be one overlay above everything, or two blurred pages stack into a smear."
        )
    # Haze samples the layer *behind* the node, so an overlay that keeps the shared states visible
    # blurs the screen it covers — that is exactly how "Внешний вид" bled through "Подписка".
    if "LocalBackgroundHaze provides null" not in app or "LocalContentHaze provides null" not in app:
        fail(
            f"{ROOT / 'NeptuneApp.kt'}: the plan overlay must isolate the haze locals. "
            "Panels that sample the backdrop blur the screen underneath, which merges the two screens."
        )
    if "Dialog(" not in app or "DialogProperties(" not in app:
        fail(
            f"{ROOT / 'NeptuneApp.kt'}: the plan overlay must be rendered in its own window. In one window "
            "'on top' only means 'painted later', so every transparent pixel of the page reveals the screen "
            "underneath — that is the merged-screens report."
        )
    if "usePlatformDefaultWidth = false" not in app:
        fail(f"{ROOT / 'NeptuneApp.kt'}: the plan window must be full-bleed, not the platform dialog width")
    glass = text(ROOT / "ui/Glass.kt")
    if "val isolated = haze == null" not in glass:
        fail(
            f"{ROOT / 'ui/Glass.kt'}: an isolated panel must be recognisable. Its tint, rim and sheen are "
            "semi-transparent by design, so on top of an overlay they leak whatever is painted under the page."
        )
    for layer in ('if (s.glassEnabled && !isolated) Canvas', 'if (rim && s.glassEnabled && !isolated) Canvas'):
        if layer not in glass:
            fail(f"{ROOT / 'ui/Glass.kt'}: {layer!r} must stay — no alpha above a page that has something to hide")
    if ".background(Color(0xFF121212).copy(alpha" in glass:
        fail(
            f"{ROOT / 'ui/Glass.kt'}: a panel with no backdrop to sample must be opaque, "
            "not 94% black over another screen's blur."
        )
    screen = text(ROOT / "ui/screens/SubscriptionScreen.kt")
    if "background(Ne.Void)" not in screen:
        fail(
            f"{ROOT / 'ui/screens/SubscriptionScreen.kt'}: the plan page must be opaque. "
            "Showing the screen under it through glass is what produced the double exposure."
        )
    state = text(ROOT / "state/NeptuneState.kt")
    if "var paywallOpen" not in state:
        fail(
            f"{ROOT / 'state/NeptuneState.kt'}: the overlay needs its own open flag. With a nullable "
            "reason only, opening the plan page without a specific feature is indistinguishable from "
            "it being closed, and the tap does nothing."
        )
    if "private set" not in state.split("var paywall")[1][:200]:
        fail(
            f"{ROOT / 'state/NeptuneState.kt'}: `paywall` must only be written through openPaywall/gate/"
            "closePaywall, so a second copy cannot be opened by an ad-hoc assignment."
        )
    if "paywallIntent" not in state:
        fail(
            f"{ROOT / 'state/NeptuneState.kt'}: a refused action must be handed back after the upgrade, "
            "otherwise the user pays and then has to find the same button again."
        )
    lyrics = text(ROOT / "ui/screens/LyricsScreen.kt")
    if "container.lyrics" in lyrics or "state.saveLyrics" not in lyrics or "onSubscribe" not in lyrics:
        fail(
            f"{ROOT / 'ui/screens/LyricsScreen.kt'}: the screen must hand the text to state.saveLyrics and "
            "ask the host to open the plan page — not talk to the repository itself."
        )


def check_pill_follows_the_user() -> None:
    """The pill carries every confirmation, so a screen that hides it hides them all."""
    chrome = text(ROOT / "ui/Chrome.kt")
    match = re.search(r"fun showsTrackPill\(route: String\?, hasTrack: Boolean\): Boolean =([^\n]*\n[^\n]*)", chrome)
    if not match:
        fail(f"{ROOT / 'ui/Chrome.kt'}: cannot read showsTrackPill — keep it machine-readable")
        return
    body = match.group(1)
    if "showsNavbar" in body:
        fail(
            f"{ROOT / 'ui/Chrome.kt'}: the pill is limited to tab routes again — it disappeared in "
            "Settings sub-screens, and with it every notice the user could trigger there."
        )
    for route in ('"player"', '"subscription"'):
        if route not in body:
            fail(f"{ROOT / 'ui/Chrome.kt'}: {route} must be the only route that hides the pill")
    # The pill is drawn on every route now, so every scrolling screen must reserve its height or the
    # capsule covers the title. This is the follow-up bug the first version of the gate missed.
    for screen in sorted((ROOT / "ui/screens").glob("*.kt")):
        source = text(screen)
        for line in source.splitlines():
            if "PaddingValues(" in line and "start = NeSpace" in line and "LocalTopInset.current" not in line:
                fail(
                    f"{screen}: content padding without LocalTopInset — the top pill would cover this "
                    "screen's header, because it is drawn on every route except the player and the plan page"
                )
                break
    app = text(ROOT / "NeptuneApp.kt")
    if "!state.paywallOpen" not in app:
        fail(f"{ROOT / 'NeptuneApp.kt'}: the dock must step aside while the plan page covers the screen")


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


def check_streaming_layer() -> None:
    """4.0: the catalogue is free, sandboxed in the data layer, and never stored as a signed link.

    Four promises that are each one careless edit away from breaking silently:

    1. **Streaming is not a plan feature.** The product rule since 3.0 is that nothing which makes the
       player usable is paid. Search, charts and playback of the catalogue are not exceptions — and the
       free tier on the service side is what makes that true. A `PlanFeature.Stream` row would be the
       first step back to selling access to music.
    2. **One file mints links.** The download signature lives in `data/yandex/YandexSign.kt` only; a
       second copy under another name would drift from the constant the service actually expects.
    3. **A queue entry is a handle, not a URL.** Signed links expire in about a minute; storing one in
       the queue would make yesterday's queue unplayable and impossible to debug.
    4. **Downloads live in files, not in the cache.** Android clears the cache first under pressure,
       and a download that vanishes is worse than no download feature at all.
    """
    plan = ROOT / "model/Plan.kt"
    source = text(plan)
    for name in ("Stream", "Yandex", "Catalog", "Download"):
        if re.search(rf"\b{name}\w*\(", source):
            # `Download` is not currently a feature; if one is added, this gate says why it must not be.
            fail(
                f"{plan}: a plan feature named {name}… appeared. Streaming the catalogue is free on the "
                "service's own free tier — gating it here would charge for something the user can already do."
            )
    sign = ROOT / "data/yandex/YandexSign.kt"
    if not sign.is_file():
        fail(f"{sign}: the download signature is missing — nothing can resolve a stream")
        return
    if 'const val SALT = "XGRlBW9FXlekgbPrRHuSiA"' not in text(sign):
        fail(
            f"{sign}: the fixed download salt changed. The service signs links with one constant; "
            "a different string produces URLs that 403 with no way to explain why."
        )
    # The signature exists once, in that file. A copy elsewhere (or a copy of the md5 of the salt
    # concatenation) means two implementations that can disagree.
    for other in sorted(ROOT.rglob("*.kt")):
        if other == sign:
            continue
        body = text(other)
        if "XGRlBW9FXlekgbPrRHuSiA" in body:
            fail(f"{other}: a second copy of the download salt — keep it in {sign.name} only")
    # Network code belongs to the data layer; a screen that opens a socket freezes a frame.
    for path in sorted((ROOT / "ui").rglob("*.kt")) + [ROOT / "state/NeptuneState.kt"]:
        body = text(path)
        for api in ("HttpURLConnection", "java.net.URL("):
            if api in body:
                fail(f"{path}: {api} in the UI/state layer — network work belongs to a repository")
    returns = text(ROOT / "model/Remote.kt")
    if 'const val SCHEME = "neptune://yandex/"' not in returns:
        fail(
            f"{returns}: the stream handle changed. A queue entry must be a stable `neptune://yandex/` id, "
            "not a signed URL: those expire in about a minute."
        )
    source_file = text(ROOT / "model/Track.kt")
    if "enum class Source { DEVICE, IMPORTED, STREAM }" not in source_file:
        fail(f"{source_file}: the STREAM source must stay explicit — it is what stops 'удалить с устройства' "
             "being offered for a track that only exists on a server")
    repository = text(ROOT / "data/yandex/YandexMusicRepository.kt")
    if "File(app.filesDir, \"streams\")" not in repository or "cacheDir" in repository:
        fail(f"{repository}: saved tracks must live under filesDir, not cacheDir — the cache is reclaimed first")
    store = text(ROOT / "data/SettingsStore.kt")
    for key in ("sourceLocal", "sourceYandex"):
        if key not in store:
            fail(f"{store}: the {key} switch must be persisted, or it resets on every launch")
    sources = text(ROOT / "ui/screens/SourcesScreen.kt")
    if "Хотя бы один источник должен остаться" not in text(ROOT / "state/NeptuneState.kt"):
        fail(
            f"{ROOT / 'state/NeptuneState.kt'}: turning off the last source must be refused *and* explained. "
            "A switch that silently springs back reads as a bug."
        )
    if "ToggleRow" not in sources or "sourceLocal" not in text(ROOT / "model/Settings.kt"):
        fail(f"{sources}: the sources screen must offer both sources as real switches")
    # One search field. The tempting shortcut when a second catalogue arrives is a second screen; the
    # design decision is one field whose results are the union.
    search = text(ROOT / "ui/screens/SearchScreen.kt")
    if search.count("BasicTextField(") != 1:
        fail(f"{search}: exactly one search field — files and the catalogue share one query")
    # A preview is explained, never hidden and never hardcoded. The free tier of the service hands over
    # an excerpt for some tracks; the app says so where the track is listed and again when the excerpt
    # ends. Three checks, because each one fails differently: a row without the badge looks like a
    # broken file, a literal "30 сек" goes wrong the day the service sends 45, and a preview that ends
    # in silence is indistinguishable from a crash.
    components = text(ROOT / "ui/Components.kt")
    if "fun PreviewBadge(previewMs: Long" not in components:
        fail(f"{components}: a preview badge must exist — a row that stays silent about a 30-second excerpt reads as a bug")
    for screen in ("ui/Components.kt", "ui/screens/SearchScreen.kt", "ui/screens/ChartScreen.kt", "ui/screens/NowPlaying.kt"):
        body = text(ROOT / screen)
        if '"30 сек"' in body or "'30 сек'" in body:
            fail(f"{ROOT / screen}: the preview length is spelled out — it comes from the service, render `PreviewBadge(previewMs)`")
    for screen in ("ui/Components.kt", "ui/screens/SearchScreen.kt", "ui/screens/ChartScreen.kt", "ui/screens/NowPlaying.kt"):
        if "PreviewBadge(" not in text(ROOT / screen):
            fail(f"{ROOT / screen}: a previewed track must say so here as well — the length and the wording are one product fact")
    backend = text(ROOT / "playback/Media3PlaybackBackend.kt")
    if "announcePreviewEnd" not in backend:
        fail(f"{backend}: a preview that runs out must be explained when it runs out, not silently followed by the next track")


def main() -> int:
    check_no_row_fill()
    check_avatar_has_no_colour_setting()
    check_toasts_live_in_the_pill()
    check_paywall_is_one_opaque_page()
    check_pill_follows_the_user()
    check_deletion_requires_confirmation()
    check_plan_gating_is_central()
    check_lyrics_are_time_driven()
    check_streaming_layer()
    if FAILS:
        print("PRODUCT INVARIANTS FAILED")
        for row in FAILS:
            print("  -", row)
        return 1
    print(
        "OK: product invariants hold (no row plate, no avatar palette, pill notices everywhere, "
        "one opaque paywall, confirmed deletion, free player, timed lyrics, free catalogue, "
        "explained previews)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
