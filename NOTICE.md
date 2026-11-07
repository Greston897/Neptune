# Notices

Neptune uses the following third-party assets and libraries:

- **Haze** by Christopher Banes and contributors — Apache License 2.0.
  https://github.com/chrisbanes/haze (v1.1.0). Used for real backdrop blur on Android 12+.
- **Neptune planet icon** — SVG Repo, CC0 1.0 Universal.
  https://www.svgrepo.com/svg/275984/neptune-planet
  The bundled `neptune_white.png` is a monochrome white adaptation of that CC0 vector preview.
- **Lucide icons** — ISC License. https://github.com/lucide-icons/lucide
  The 49 `ne_*.xml` vector drawables in `app/src/main/res/drawable/` are converted from Lucide's
  24×24 SVG sources (bundled in `ci/lucide/`, reproducible via `python3 ci/svg2vd.py`).
  Glyphs keep the upstream geometry, 2dp stroke and round caps/joins; the tab bar additionally
  uses heavier 2.6dp twins for the selected state, and transport/heart glyphs are filled.
  ISC requires the copyright and permission notice to be reproduced: see `ci/lucide/LICENSE`.
- **Manrope** font — SIL Open Font License 1.1. https://github.com/sharanda/manrope

The liquid-glass panel treatment (rounded grouped cells, backdrop blur, top/bottom rim strokes,
scroll-aware invalidation) was studied from the official Telegram Android source
(https://github.com/DrKLO/Telegram, GPL-2.0). No Telegram source code is copied into this
repository; the Compose implementation here is original and uses Apache-2.0 Haze for blur.
