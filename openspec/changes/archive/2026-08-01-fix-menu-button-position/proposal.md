## Why

Map canvas stops at bottom of top bar instead of rendering edge-to-edge behind system bars. Menu button has visible gap above it due to double padding (scaffold content padding + `statusBarsPadding`). On Pixel 8 (and other devices with `enableEdgeToEdge()`), this wastes screen space and looks broken.

## What Changes

- Remove `scaffoldPadding` from the Box wrapping Canvas — Canvas fills full screen
- Keep `statusBarsPadding()` on overlay Column (menu/search/fav buttons) — single correct inset
- Map renders behind status bar area (edge-to-edge)
- Menu button position corrects automatically (no double padding)

## Capabilities

No spec-level behavior changes — pure layout fix. Map rendering, gestures, overlays all work identically. Only clipping area changes.

## Impact

- **File changed**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — restructure Scaffold content layout
- **No API changes**: ViewModel, native code, data layer unaffected
- **No dependency changes**
- **Risk**: Overlay buttons (menu, search, favorites, zoom) must remain visible below status bar. `statusBarsPadding()` already handles this.
