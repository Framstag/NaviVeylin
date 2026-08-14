## Why

Path labels (street names rendered along roads) sometimes appear at extremely large sizes, making the map look broken. The bug is intermittent — same location, same zoom can produce correct or oversized text depending on render order or state.

Root cause: The Cairo `GetFont()` caches fonts keyed by `size_t` (truncated `double`). When multiple path text styles share the same truncated pixel size but differ in actual size, the wrong cached font is returned. Additionally, the font cache is never invalidated when the stylesheet changes, and the `StyleSheetChanged` callback skips font cache clearing.

## What Changes

- Fix font cache key collision in `MapPainterCairo::GetFont()` by using full `double` precision key instead of truncating to `size_t`
- Clear font cache in `StyleSheetChanged()` alongside image/pattern caches
- Add debug logging to `GetFont()` to trace cache hits/misses and font size values
- Ensure path text labels with no explicit `size` in stylesheet (defaulting to 1.0) render at same scale as regular text labels with equivalent size

## Capabilities

### New Capabilities
- `path-text-font-caching`: Fix font cache key precision and invalidation for path text rendering

### Modified Capabilities
- *(none — no spec-level behavior changes, only rendering correctness)*

## Impact

- `app/src/main/cpp/libosmscout/libosmscout-map-cairo/src/osmscoutmapcairo/MapPainterCairo.cpp` — `GetFont()` cache key type, `StyleSheetChanged()` font cache clearing
- `app/src/main/cpp/libosmscout/libosmscout-map-cairo/include/osmscoutmapcairo/MapPainterCairo.h` — `FontMap` key type from `size_t` to `double`
- No API changes, no new dependencies
