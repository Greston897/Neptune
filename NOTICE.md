# Notices

Neptune uses the following third-party assets and libraries:

- **Haze** by Christopher Banes and contributors — Apache License 2.0.
  https://github.com/chrisbanes/haze (v1.1.0). Used for real backdrop blur on Android 12+.
- **Lucide icons** — ISC License. https://github.com/lucide-icons/lucide
  The 50 `ne_*.xml` vector drawables in `app/src/main/res/drawable/` are converted from Lucide's
  24×24 SVG sources (bundled in `ci/lucide/`, reproducible via `python3 ci/svg2vd.py`).
  Glyphs keep the upstream geometry, 2dp stroke and round caps/joins; the tab bar additionally
  uses heavier 2.6dp twins for the selected state, and transport/heart glyphs are filled.
  ISC requires the copyright and permission notice to be reproduced: see `ci/lucide/LICENSE`.
  The adaptive launcher icon uses Lucide's `orbit` glyph (same ISC licence), scaled onto the
  108dp adaptive canvas over a pure black tile; reproducible via `python3 ci/make_launcher.py`
  and verified in CI by `python3 ci/check_launcher.py`. The in-app brand mark and profile avatar
  render the same glyph (`ne_orbit.xml`), so the avatar and the launcher icon are one artwork.
- **Manrope** font — SIL Open Font License 1.1. https://github.com/sharanda/manrope

The liquid-glass panel treatment (rounded grouped cells, backdrop blur, top/bottom rim strokes,
scroll-aware invalidation) was studied from the official Telegram Android source
(https://github.com/DrKLO/Telegram, GPL-2.0). No Telegram source code is copied into this
repository; the Compose implementation here is original and uses Apache-2.0 Haze for blur.
