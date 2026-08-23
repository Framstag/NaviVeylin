## Context

See proposal.md - Why. Current state:

- `MapScreen` builds a deprecated `MapTemplate` with a single `ActionStrip` (Menu, Search, Zoom in, Zoom out), every action wrapped in `ParkedOnlyOnClickListener`. The map is rendered to a `Surface` via `AutoMapRenderer` with gesture handling (scroll/scale/tap).
- `NavigationScreen` builds a `NavigationTemplate` with one `ActionStrip` (BACK, Stop). No map surface; hints are the host-rendered `RoutingInfo` panel (top, host-positioned) plus `TravelEstimate`.
- Phone UI (source of truth for the layout): `MapCanvasScreen` uses `ActionColumnInset = 64.dp` (left column width) and `ViewColumnReserve = 64.dp` (right column width); `NextTurnOverlay` is top-start, padded left by `ActionColumnInset`, width capped to `maxWidth - ViewColumnReserve` so it never covers the right view column.
- Settings already exist for Auto: `PreferencesScreen` (`SectionedItemTemplate` with clickable toggle rows) driven by `PreferencesScreenMapper` over `AutoSettings`, persisted via `AutoSettingsProvider` to the same storage the phone uses. Content = phone's location-options dialog minus phone-only `keepScreenOn`.
- Car App Library 1.7.0: `NavigationTemplate` and `MapWithContentTemplate` both expose `setMapActionStrip` (renders along the **left edge** over the map) and `setActionStrip` (renders along the **right edge**). Host renders the `RoutingInfo` instruction panel top-center with no app-side positioning control.

## Goals / Non-Goals

**Goals:**
- Left-edge vertical strip for action buttons on both map and navigation screens.
- Right-edge vertical strip for visualisation (zoom) buttons on both screens.
- Driving-safe settings entry on the map screen, reusing the existing settings UI.
- Navigation hints left-aligned, inset right of the left strip, width-capped to never overlap the right strip — mirroring the phone's `NextTurnOverlay` geometry.
- Drop deprecated `MapTemplate` usage on the map screen.

**Non-Goals:**
- No phone-side UI changes.
- No new settings beyond what `PreferencesScreenMapper` already exposes.
- No pan/rotate gestures on the navigation surface (follow-mode only).
- No surface-drawn interactive controls (the AAOS template host does not forward surface gestures — device-log verified).

## Decisions

### D1. Map browsing screen: `MapWithContentTemplate` + host strips + content menu

`MapTemplate` is deprecated (superseded by `MapWithContentTemplate`, API 7). Final shipped layout (evolved over device rounds 1–7):

- **No map action strip** — the left-edge strip idea was dropped; the app menu lives in the required content slot.
- **Content slot = app menu** (`MapTemplateFactory.buildMenuContent`): `ListTemplate` content (PaneTemplate rows cannot take click listeners) with an app-icon + name header and seven rows: Free driving (stops navigation + re-centers), Starred favorites, All favorites, Search for POIs, Search history, Diagnostics, About. The content slot is mandatory in the host API, so it doubles as the menu.
- **Right template action strip** (`setActionStrip`): Search (parked-only), Settings (driving-safe), Zoom in/out (parked-only). Host strips are always tappable; parked-only is host-enforced (`ParkedOnlyOnClickListener`).
- **Compass**: rotating rose drawn on the navigation surface only (host strips cannot rotate; browse mode shows no compass).
- **Settings observation**: `MapScreen` re-reads shared settings periodically (~5 s) applying follow mode + north-up live; `NavigationScreen` re-reads on state change for lane hints + orientation.

History: surface-drawn button columns (phone-style) were tried first but failed on real AAOS hosts (host crops the surface, overlays chrome, and never forwards surface gestures — verified via device logcat: zero `onClick`/`onScroll`/`onScale` events). `ParkingState`/`ParkingHeuristic`/`MapSurfaceButtons`/`MapZoomControls` were removed. `MapController`-rendered zoom was not shown by this host either.

Alternative considered: keep `MapTemplate` — rejected (deprecated, single strip).

### D2. Strip split on NavigationScreen

`NavigationTemplate` exposes both strips (verified in the 1.7.0 AAR):

- Map action strip (left): BACK, Stop.
- Action strip (right): Zoom in, Zoom out.

Zoom actions drive the navigation surface viewport zoom (D4) instead of a `MapController` (NavigationTemplate has none). No parked-only guards on this screen — stopping navigation mid-drive is essential and zoom is a visualisation control.

### D3. Settings reachable while driving

The map Settings action is **not** wrapped in `ParkedOnlyOnClickListener` and pushes the existing `PreferencesScreen` (SectionedItemTemplate) onto the screen stack. NaviVeylin is a NAVIGATION-category app; item templates and screen pushes are permitted while the vehicle is moving, and every row toggle is a single tap with immediate persisted effect.

Alternatives considered:
- `Alert` — rejected: limited to title/message + at most two actions, cannot host seven toggle rows.
- New `PaneTemplate` settings screen — rejected: `PaneTemplate` rows do not support click listeners (car-app constraint `ROW_CONSTRAINTS_PANE`, already documented in `PreferencesScreen`).

The settings content deliberately mirrors the phone dialog minus `keepScreenOn` (phone-only, meaningless on an always-on car display).

### D4. Navigation hints drawn on the map surface

Host-rendered `RoutingInfo` is top-center with no positioning control — it cannot satisfy "left-oriented, right of the action strip, never overlapping the right strip". Therefore `NavigationScreen` registers a `SurfaceCallback` and:

- Renders the map behind via the existing `AutoMapRenderer` (GPS follow, heading-up; reuse the MapScreen renderer wiring pattern).
- Draws the hint panel itself on the surface canvas, mirroring the phone geometry: left-aligned panel at top, x-offset = left strip inset (phone `ActionColumnInset` equivalent, `ACTION_STRIP_INSET`), width capped to `surfaceWidth - ACTION_STRIP_INSET - VIEW_STRIP_RESERVE` (phone `ViewColumnReserve` equivalent). Content: turn arrow (port of `NavigationArrowRenderer`), distance, description, lane guidance. `TravelEstimate` stays host-rendered at the bottom (no conflict with either strip).
- Re-renders only when the relevant `NavigationState` fields change, reusing the existing `hasStateChanged` throttle.

Alternatives considered:
- Keep host `RoutingInfo` panel — rejected: position not controllable, would overlap right strip region on narrow hosts.
- Draw hints on the map screen's surface while keeping `NavigationTemplate` — same thing; there is no template-side position control, so the surface is the only deterministic path.

### D5. Shared strip geometry constants

Introduce `ACTION_STRIP_INSET` and `VIEW_STRIP_RESERVE` (px at surface DPI, derived from the phone's 64.dp values) in the auto package so the hint panel geometry tracks the strip layout in one place.

## Risks / Trade-offs

- [Host-specific strip placement (AA phone vs AAOS)] → NavigationScreen uses the documented left/right strip positions (verified in the 1.7.0 AAR); verify on emulator and document host differences in the task.
- [Surface buttons bypass host parked-only enforcement] → Resolved: controls moved to host action strips; parked-only is host-enforced (`ParkedOnlyOnClickListener`); the custom `ParkingState`/`ParkingHeuristic` were removed.
- [Surface rendering during navigation adds GPU/CPU load] → Reuse the existing render-coalescing/throttle pattern (`hasStateChanged`), render only on meaningful state changes, keep follow-mode (no gesture churn).
- [Settings screen pushed while driving rejected by some host] → NAVIGATION category allows item templates in motion; if a host still blocks it, the settings button falls back to parked-only on that host (menu/search already behave that way) — no crash, degraded but safe.
- [Surface buttons lack host accessibility/voice integration] → Host strips are used wherever the API supports the layout (NavigationScreen); the browsing screen's custom columns are a deliberate trade-off for the required positions, matching the phone's custom overlay buttons.
- [Text/icon rendering on Canvas differs from Compose overlays] → Port `NavigationArrowRenderer` drawing primitives; unit-test the mapping layer; visual check on emulator.

## Migration Plan

- Single change, `:auto` module only; no data migration (settings storage untouched).
- Land behind the normal `./gradlew :auto:assembleDebug` + `./gradlew test` gates.
- Rollback: revert commit; phone UI and shared settings unaffected.

## Open Questions

- Whether zoom should become driving-safe on Auto (currently parked-only, unchanged here). Deferrable; would be a separate change with its own spec delta if requested.
- Whether the map screen should also expose settings while parked-only hosts reject the push mid-drive (fallback noted in Risks, no spec impact).
