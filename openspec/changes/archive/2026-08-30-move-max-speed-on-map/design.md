# Design — move-max-speed-on-map

## Context

See proposal.md — Why. Phone currently shows current/max speed only inside the routing status card (`NavigationStateOverlay` → `NavigationStatsRow`, shared with the expanded `NavigationDetailsOverlay`). Android Auto already draws the speed badge + round limit sign on the map surface via `SurfaceIndicators` (Canvas, right visualisation region below the compass rose). Phone map UI is Compose-based; overlays like `CompassButton`/`LocationMarkerOverlay` are Compose composables positioned in `MapCanvasScreen`'s `BoxWithConstraints`.

Speed data sources on phone:
- Navigation: `NavigationViewModel.state` → `currentSpeedKmH`, `maxSpeedKmH` (engine callbacks `onCurrentSpeed`/`onMaxAllowedSpeed`).
- Follow mode: GPS fix `speedKmH` already flows through `MapCanvasViewModel`'s location collection; max speed needs `client.getMaxSpeedAt(lat, lon)` (JNI, used by AA free driving only today).

## Goals / Non-Goals

Goals:
- Compose speed widget on phone map, mirroring AA visualisation (badge + round sign), in follow mode and during navigation.
- Remove speed from routing status card and expanded details view.
- Keep overspeed red at +5 km/h (existing phone convention, per `navigation-state-display` spec).

Non-Goals:
- No change to AA rendering (`SurfaceIndicators` untouched).
- No change to speed data plumbing in `NavigationViewModel`/engine.
- No native/JNI changes.

## Decisions

### 1. Compose overlay, not Canvas-on-bitmap

Phone renders the widget as a Compose composable positioned in `MapCanvasScreen`'s overlay `BoxWithConstraints`, like `CompassButton`. AA uses Canvas because the car host only exposes a surface; the phone has full Compose overlay support.

Alternative considered: drawing into the map bitmap via the renderer's overlay hook (AA pattern). Rejected — phone has no such hook and Compose overlays already exist; bitmap drawing would fight the existing overlay architecture and hurt testability.

### 2. Placement: right visualisation region, below compass

Widget sits in the same top-right `Column` as `MapCompassBlock` (both orientations), below the compass — mirroring AA's badge-below-rose layout. Keeps the right edge free of the bottom-center routing status card and matches `auto-map-layout`'s "right visualisation region" convention.

Alternative considered: bottom, above the status card. Rejected — crowds the card and deviates from AA parity.

### 3. Speed sources

- **Navigation**: read `navState.currentSpeedKmH` / `navState.maxSpeedKmH` directly in `MapCanvasScreen` (already passed to the overlay today).
- **Follow mode**: expose `currentSpeedKmH` (from GPS fix `speedKmH`) and `maxSpeedKmH` (from `getMaxSpeedAt`) in `MapCanvasUiState`. `MapCanvasViewModel` resolves max speed on GPS position change with a throttle (e.g. every ~5 s or after moving > ~50 m), off the main thread (viewModel coroutine scope), mirroring AA's `resolveStreetName` pattern. NaN/negative = unknown → widget hidden.

### 4. Overspeed rule

Keep the phone convention: red when `currentSpeedKmH > maxSpeedKmH + 5` (existing `NavigationStatsRow` logic, spec'd in `navigation-state-display`). AA uses rounded-value comparison; the phone spec pins the +5 km/h rule, so the widget follows the phone rule.

### 5. Widget visibility

Show when (follow mode active OR navigating) AND speed data available. Hide when browsing (not following, not navigating), when speed unknown, and when navigation stops. Follow mode off → widget hidden even if GPS speed exists.

### 6. `NavigationStatsRow` simplification

Remove `currentSpeedKmH`/`maxSpeedKmH` params from `NavigationStatsRow`; drop the speed column (icon + value + "max" line). `NavigationStateOverlay` and `NavigationDetailsOverlay` signatures lose the speed params; `MapCanvasScreen` call sites updated. ETA / remaining time / distance / road name / stop button unchanged.

## Risks / Trade-offs

- [JNI `getMaxSpeedAt` on main thread janks UI] → Resolve in viewModel coroutine off main thread, throttled; NaN on failure.
- [Widget overlaps compass in landscape] → Same scrollable top-right Column; widget below compass, `verticalScroll` already handles overflow.
- [Speed flicker at 0 / stale values] → Show only when speed ≥ 0 and not NaN; hide on unknown; follow AA's "never render NaN/negative" rule.
- [AA vs phone overspeed threshold divergence] → Accepted: phone spec pins +5 km/h; AA keeps rounded comparison. Documented in specs.

## Migration Plan

Pure UI change, no data migration. Rollback: revert the widget composable and restore speed params in `NavigationStatsRow`. No versioning impact.

## Open Questions

None — all decisions resolved above; specs unchanged.
