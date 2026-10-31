# MapStyling — the rationale

This is the rationale layer of `guidelines/MapStyling.md`: the failed-load history and the packaging
failures behind the stylesheet and icon rules. An anchor here names a section number the base document
carries.

## §15a Stylesheet load failure (app side) — why a rejected style is never rendered

A stylesheet that fails to load once is not retried on every frame; the app refuses the style and
keeps the previous one, because a render against a half-loaded stylesheet crashed the app in the
rounds that produced this rule. The rule is stated in `guidelines/MapStyling.md` §15a.

## §16a POI icons — the packaging failures behind the fallback

Icons that the configured directory does not carry fall back to a symbol; the packaging failures that
produced the fallback are the reason the rule exists. The rule is stated in `guidelines/MapStyling.md`
§16a.

## §20 Label wrapping — the pango measurement

- `pkg-config --modversion pangocairo` answers `1.58.0` when pointed at one of the three Android
  vcpkg triplets that install pango, harfbuzz and fribidi (measured 2026-10-26); the module's own
  detection is what does not reach them.
