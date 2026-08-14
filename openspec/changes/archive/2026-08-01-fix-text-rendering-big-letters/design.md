## Context

See `proposal.md` — Why for motivation. The bug is in `MapPainterCairo::GetFont()` which uses `std::unordered_map<size_t, CairoFont>` as font cache. The `size_t` key truncates the `double` pixel size, causing cache collisions when distinct font sizes map to the same integer. `StyleSheetChanged()` clears image/pattern caches but omits the font cache.

Affected file: `app/src/main/cpp/libosmscout/libosmscout-map-cairo/` (header + source). No other backends (Qt, AGG, SVG, DirectX) use the same pattern — each has its own font cache.

## Goals / Non-Goals

**Goals:**
- Eliminate font cache key collisions by using full `double` precision
- Ensure font cache is cleared on stylesheet reload
- Add debug logging to diagnose future font sizing issues
- Verify path text with default `size: 1.0` renders at same scale as regular text with `size: 1.0`

**Non-Goals:**
- No change to font size calculation formula (`fontSize * ConvertWidthToPixel(GetFontSize())`)
- No change to other painter backends
- No API or ABI changes

## Decisions

### 1. Cache key type: `double` instead of `size_t`
**Decision:** Change `FontMap` from `std::unordered_map<size_t, CairoFont>` to `std::unordered_map<double, CairoFont>`.

**Rationale:** The computed pixel size is a `double`. Truncating to `size_t` loses precision and causes collisions (e.g., 41.3px and 41.7px both map to key 41). Using `double` as key preserves full precision.

**Alternatives considered:**
- `std::map<double, ...>` — ordered map works but `unordered_map` with `double` key is fine since we only do exact lookups (no range queries). Need custom hash for `double` — use existing `std::hash<double>`.
- Rounding to nearest 0.5px — reduces collisions but still lossy. Full precision is simpler and correct.

### 2. Font cache clearing in `StyleSheetChanged()`
**Decision:** Add `fonts.clear()` to `StyleSheetChanged()`.

**Rationale:** The method already clears `images`, `patterns`, and `patternImages`. Fonts depend on `parameter.GetFontName()` and `parameter.GetFontSize()` which can change with stylesheet reload. Omitting font cache means stale fonts persist.

### 3. Debug logging in `GetFont()`
**Decision:** Add `log.Debug()` calls on cache hit and cache miss, logging the computed pixel size.

**Rationale:** Helps diagnose future font sizing issues without requiring debugger. Uses existing `osmscout::log` infrastructure.

## Risks / Trade-offs

- [Risk] `std::hash<double>` has known edge cases (NaN, -0.0) — Mitigation: font size is always positive finite `double`, never NaN or negative. Guard with `assert(fontSize > 0 && std::isfinite(fontSize))`.
- [Risk] `unordered_map` with `double` key may have more collisions than integer keys — Mitigation: font cache is per-render (new `MapPainterCairo` per `DrawMap` call), so cache size is small (< 20 entries). Collision overhead is negligible.
- [Risk] `fonts.clear()` in `StyleSheetChanged()` may cause brief re-creation of fonts on next render — Mitigation: same pattern already used for images/patterns. Acceptable one-time cost.
