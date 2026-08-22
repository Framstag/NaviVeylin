# Make Menu Material — Design

## Context

Current map screen (see proposal.md — Why): `MapCanvasScreen.kt` switches portrait/landscape via `BoxWithConstraints`. `MapMenu` is a plain M3 `DropdownMenu` with 4 text-only items, anchored in the top-right overlay column. Portrait stacks all controls (menu, compass, search, favorites, location options, zoom, re-center) in one top-right column; landscape puts controls right, hints left. `NextTurnOverlay` is top-start with `widthIn(max = maxWidth - 64.dp)` to clear the top-right column. Compass, hints, and column layout are pinned by specs `map-canvas-screen`, `landscape-layout`, `nav-hints-layout`, `compass-button`.

## Goals / Non-Goals

**Goals:**
- Material 3 animated menu (fade + scale in/out) with a toaster trigger button
- Action/view control split: actions left, views right
- Nav hints start right of the toaster button
- Keep existing menu actions and behavior identical

**Non-Goals:**
- No new menu entries beyond styling (same 4 actions)
- No changes to `LocationOptionsOverlay` internals, zoom behavior, or compass behavior — only placement
- No Auto module changes

## Decisions

### 1. Main-window overlay menu instead of stock DropdownMenu/Popup
Render the menu as an in-window overlay: a full-screen transparent tap scrim (dismiss on outside tap) plus a `Surface` panel anchored below the toaster button, all wrapped in `AnimatedVisibility` with `fadeIn + scaleIn` / `fadeOut + scaleOut`. Dismissal also wired to `BackHandler`.
Rationale: stock `DropdownMenu` gives fade/expand-in but no controllable fade-out, and any exit animation inside a separate `Popup` window runs on that window's frame clock — which stalls in Robolectric and proved unreliable on device, leaving the menu open after item selection (menu items persisted after click; reproduced by `menuClosesAfterSelection`). A main-window overlay animates on the main composition clock, so the exit reliably completes and the menu always closes. Leading icons come free from M3 `DropdownMenuItem` rows.
Menu items get `leadingIcon`s: Download Maps → `Icons.Default.FileDownload` (or Map), Favorites → `Icons.Default.Favorite`, Search POIs → `Icons.Default.Search`, About → `Icons.Default.Info`.
Trigger: `FilledTonalIconButton` with `Icons.Default.Menu` (hamburger), same shadow/shape as existing overlay buttons, shared `MapMenu` composable signature preserved so `MapMenuComposeTest` keeps working.

### 2. Layout split into action column (left) and view column (right)
Extract two shared composables used by both orientation branches:
- `MapActionColumn(menu, search, favorites, recenter)` — `align(TopStart)`, statusBarsPadding
- `MapViewColumn(compass, locationOptions, zoom)` — `align(TopEnd)` portrait / right side landscape
Re-center button: `align(BottomStart)` when `!followMode && gpsFix != NONE`, both orientations (was bottom-right).
Landscape keeps bottom-right view cluster (location options, horizontal zoom) and gains the top-left action column; the "left side reserved for hints" rule is gone.
Why: single source of truth for both orientations, mirroring the existing pattern; keeps diff local to `MapCanvasScreen.kt`.

### 3. Nav hints offset = toaster button width
Replace the fixed `buttonColumnEstimate = 64.dp` (which cleared the top-right column) with a shared constant `ToasterButtonStartInset` = toaster button width + spacing (~64.dp). `NextTurnOverlay` moves from `align(TopStart)` flush-left to `padding(start = ToasterButtonStartInset)`; `widthIn(max = maxWidth - buttonColumnEstimate)` stays but the estimate now accounts for both columns.
Why: hints must start right of the toaster button (spec `nav-hints-layout`) instead of the display edge.

## Risks / Trade-offs

- [Overlay menu lacks stock DropdownMenu keyboard/accessibility handling] → Menu is small (4 items); M3 `DropdownMenuItem` rows keep accessibility semantics; scrim + `BackHandler` give tap-outside/back dismissal
- [Fade-out animation delays action navigation when an item is selected] → Dismissal state flips immediately (exit runs in background while the action fires); fade-out is short (110ms)
- [Two top columns on small portrait screens crowd the map] → Right column holds only compass + location options + zoom; columns are compact icon buttons, and the left column replaces the previous single column rather than adding to it
- [Nav hint inset constant drifts from real toaster button size] → Extract shared constant next to the button composable, both layout and hint use the same source

## Migration Plan

UI-only change; no data migration, no feature flag. Rollback = revert the commit. Existing installs get the new layout on next update with no state changes.

## Open Questions

None.
