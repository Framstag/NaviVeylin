# Make Menu Material

## Why

The phone map screen menu is a plain ⋮ `DropdownMenu` pinned top-right — it does not match modern Material 3 design, and every overlay control is stacked into one right-side column. The menu should be a real Material 3 menu that fades/scales in and out, popped by a dedicated "toaster" button, and the on-map controls should be rebalanced: action buttons on the left, view-only controls on the right, with the menu button top-left and navigation hints starting right of it.

## What Changes

- Replace `MapMenu` (plain `DropdownMenu`) with a Material 3 menu that animates in/out (fade + scale) when toggled, with leading icons on menu items.
- Introduce a real toaster button (hamburger, top-left) that pops the animated menu — a genuine feature, not a test-only stub.
- Move the menu button from the top-right overlay column to the top-left of the map screen, in both portrait and landscape.
- Move action-like buttons to the left side under the menu button: search, favorites, and the re-center (MyLocation) button.
- Keep view-like controls on the right side: compass, location options overlay, and zoom controls.
- Navigation hints (`NextTurnOverlay`) start immediately to the right of the top-left menu button instead of flush against the left display edge; their width cap accounts for the left action column instead of the top-right button column.

## Capabilities

### New Capabilities
- `map-menu`: Material 3 fade in/out menu behavior with a toaster trigger button, anchored popup, and menu items with leading icons.

### Modified Capabilities
- `map-canvas-screen`: top-right overlay column requirement changes — the menu button is no longer the top item of the right column; portrait gains a left action column (menu, search, favorites, re-center) and a right view column (compass, location options, zoom).
- `landscape-layout`: control cluster arrangement changes — action cluster moves to the left, view cluster stays on the right; the left side is no longer reserved entirely for nav hints.
- `nav-hints-layout`: navigation hints are no longer flush to the left display edge; they start right of the top-left menu button, and the width constraint references the left action column.
- `compass-button`: the "Compass positioned between menu and search" requirement changes — the compass stays in the right view column; its exact position relative to the (moved) menu button is redefined.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — `MapMenu` composable rewrite, toaster button, left action column, right view column, `NextTurnOverlay` offset/width.
- `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — hint start offset respects the top-left menu button.
- `app/src/test/java/com/naviveylin/ui/map/MapMenuComposeTest.kt` — updated for the new animated menu + toaster button.
- Specs: `openspec/specs/map-canvas-screen`, `landscape-layout`, `nav-hints-layout`, `compass-button` get delta specs; new `map-menu` spec.
