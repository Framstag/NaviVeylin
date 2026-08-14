## Purpose

Ensures path text labels (street names rendered along roads) use correct font size every render.

## ADDED Requirements

### Requirement: Font set on Cairo context before drawing glyphs
`MapPainterCairo::DrawGlyphs()` in the non-Pango code path SHALL set the correct scaled font on the Cairo context via `cairo_set_scaled_font()` before calling `cairo_show_text()` for each glyph.

#### Scenario: Path label uses correct font size
- **WHEN** a path text label is rendered via the non-Pango `DrawGlyphs()`
- **THEN** the Cairo context SHALL have the correct scaled font set before each `cairo_show_text()` call

### Requirement: Glyph stores font reference
The non-Pango `CairoNativeGlyph` struct SHALL store a `CairoFont` reference so `DrawGlyphs()` can set it on the Cairo context.

#### Scenario: Font available during glyph rendering
- **WHEN** `ToGlyphs()` creates glyphs from a `CairoLabel`
- **THEN** each glyph SHALL carry a reference to the `CairoFont` used for text extent measurement

### Requirement: Font cache uses full-precision key
The font cache in `MapPainterCairo::GetFont()` SHALL use the full `double` precision of the computed pixel size as its lookup key, not a truncated `size_t`.

#### Scenario: Distinct font sizes produce distinct cache entries
- **WHEN** two path text styles have different computed pixel sizes (e.g., 41.3px and 41.7px)
- **THEN** each SHALL produce a separate cache entry with its own correctly-sized font

### Requirement: Font cache cleared on style change
`MapPainterCairo::StyleSheetChanged()` SHALL clear the font cache alongside the existing image and pattern caches.

#### Scenario: Style reload produces fresh fonts
- **WHEN** a stylesheet change triggers `StyleSheetChanged()`
- **THEN** subsequent `GetFont()` calls SHALL create new fonts using the updated style parameters, not return stale cached fonts
