## Why

During turn-by-turn navigation and free driving the follow-mode map is locked with the vehicle marker at the exact center of the car display. The host's route-status/turn-instruction UI covers part of the surface (on the current head unit ~40% of the width on the left), so the covered side wastes visible map; centered placement also shows as much "way behind" as "road ahead". Drivers need to place the marker where the panels do not cover it and bias the view toward the road ahead — on both Android Auto and the phone.

## What Changes

- New global shared setting: **vehicle anchor position**, one preset per mode (**routing** and **free driving**), each chosen from a **5×3 grid of 15 presets** (horizontal 10/30/50/70/90% of the surface width, vertical 10/50/90% of the height). Default for both modes: **center/center** (50%/50%) — identical to today's behavior.
- Follow-mode positioning change on Android Auto **and** phone: the map refresh logic keeps the vehicle at the selected screen anchor instead of the screen center, so the map content (not just the marker) shifts — more road ahead becomes visible, and the marker moves out from behind host panels. Works in north-up and heading-up (rotation-compensated anchor).
- Setting available in the Android Auto settings dialog and in the phone location-options sheet. Value is global: editing on either surface changes both (same shared storage pattern as the overspeed warning delta).
- The existing automatic pane compensation (`PaneOffset.paneOffsetCenter`, hardcoded 40% left panel) stays unchanged for map browsing / details screens; navigation and free driving use the user-chosen anchor instead.
- Additive; no breaking changes. Rollback: both anchors reset to center/center.

## Capabilities

### New Capabilities

- None — behavior lands in the existing follow-mode and settings-surface capabilities.

### Modified Capabilities

- `auto/navigation-view`: during navigation, the vehicle SHALL be kept at the routing anchor position instead of the screen center (new requirement + changes to the smooth follow-mode scrolling requirement's "stays visually attached" semantics).
- `auto/free-driving`: the "Follow mode activated" requirement changes from "map centered on the GPS position" to "map keeps the vehicle at the free-driving anchor position"; auto-zoom stays centered-on-vehicle-adjusted.
- `auto-map-layout`: the Android Auto settings dialog gains the two vehicle-anchor rows (routing + free driving) with a position picker, mirroring the phone sheet.
- `smooth-follow`: the phone follow-mode positioning changes to keep the vehicle at the anchor (routing anchor while route guidance is active, free-driving anchor otherwise) instead of the screen center.
- `location-options-ui`: the phone location-options bottom sheet gains the two vehicle-anchor pickers.

## Impact

- **Code**:
  - `core/src/main/java/com/naviveylin/core/AutoSettings.kt` — two anchor fields (`routingAnchor`, `freeDrivingAnchor`)
  - `core/.../VehicleAnchor.kt` (new) — preset model (15 positions) + rotation-aware anchor-geo helper (generalization of `paneOffsetCenter` using `ProjectedViewport(angle).screenToGeo`); no change to `ProjectionUtils` itself
  - `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — `AppSettings` fields
  - `app/src/main/java/com/naviveylin/data/AutoSettingsMapping.kt` — both mappers
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` + `MapCanvasViewModel.kt` — follow render target = anchor geo; `recenterInBrowse`/re-engage restore the anchor
  - `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — anchor picker rows
  - `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — follow-mode render target = anchor geo; `reengageFollow` restores anchor-centered viewport; clamp/blit/marker projection stay anchor-agnostic
  - `auto/.../NavigationScreen.kt`, `auto/.../FreeDrivingScreen.kt` — read anchors from shared settings, pass to renderer
  - `auto/.../PreferencesScreen.kt`, `PreferencesScreenMapper.kt` — two rows + position-picker screen
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` overlays unaffected (projection-based)
- **Interplay**: in-flight change `auto-pan-during-navigation` changes pan/re-engage flow; re-engage must restore the *anchor*-centered viewport, not a center-centered one. Coordinate the two renderer touches.
- **Guidelines**: `guidelines/UI.md` settings-parity section gains the anchor control (same labels/hierarchy on phone and AA per its parity rule).
- **Tests**: core anchor-math unit tests (incl. heading-up rotation-compensation, all-grid-position projection math), AA + phone renderer follow-target tests, settings mapping round-trip, picker UI tests.
- **Scope**: phone and Android Auto are both in scope (feature is cross-surface). No native/JNI changes, no new dependencies, car-app 1.7.0 already pinned (no new API surface needed).
