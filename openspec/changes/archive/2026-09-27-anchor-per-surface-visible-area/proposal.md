## Why

Two defects found while verifying the vehicle-anchor feature on a phone:

1. **A preset can place the vehicle under an overlay.** The anchor grid is defined as a fraction of the
   *surface*, so `bottom-center` (fy = 0.9) sits behind the phone's routing-status card and
   `top-center` (fy = 0.1) behind the next-turn card — 10 of the 15 presets are unusable during phone
   navigation, which is exactly when the feature is meant to help (the earlier change's design called
   overlay clearance a "non-goal"; that was wrong).
2. **One value for two very different surfaces.** The phone and the head unit have different covered
   regions (phone: turn card, routing status, widget column; AA: a host pane that can cover ~40% of
   the width). A single stored anchor cannot be right for both: what clears a phone overlay is
   usually wrong on the head unit and vice versa. The presets describe a *logical* position; the
   value must be per surface.

## What Changes

- **Collision-remap semantics (corrected 2026-09-16)**: a preset fraction SHALL be kept exactly unless
  the marker — its visual footprint plus padding — would land inside a region one of the phone's own
  overlays covers (measured, not assumed: next-turn card at the top, routing-status card at the bottom,
  right widget column, free-driving street-name pill); only then SHALL the preset move per axis to the
  nearest position outside the covering region (region edge + the grid's 10% margin). The default
  center/center never collides, so in portrait and landscape the vehicle stays exactly at the canvas
  center with the navigation overlays present — the device report ("vehicle left of center") is the
  regression this correction fixes; the earlier draft's visible-area *rescale* (map every preset into
  the reduced visible rect) is rejected. Android Auto uses the same band rule for its host pane (passed
  as left/right insets), so the mechanism is shared.
- **Identity unless covered** (browse mode, AA, and any non-covered preset): the resolved fraction
  equals the preset fraction, so the pre-feature framing, the AA behavior, and presets that do not
  collide with an overlay are all unchanged.
- **Per-surface anchor values**: the phone stores its own routing + free-driving anchor and Android
  Auto stores its own; changing one SHALL NOT change the other. The 15 presets, their ids, labels and
  hierarchy stay shared (parity rule) — only the stored *value* is per surface.
- **Migration without loss**: the existing shared `routingAnchorId`/`freeDrivingAnchorId` remain the
  phone's values (no data migration), and the car falls back to them until a driver picks an anchor
  on the car; from then on the car keeps its own value. Absent-field defaults are used, so no write
  or version bump is needed.
- **The resolved fraction is published once** in the phone UI state and used by both the follow
  render target and the marker projection (single source of truth; no second derivation).
- The mapping SHALL keep the resolved fraction inside the `0.1..0.9` overrun-margin band, so the
  anchor-centered framing and the no-uncovered-strip guarantee of `fix-phone-vehicle-anchor-framing`
  stay valid for every overlay geometry.
- Additive; **user-visible behavior change** for users who chose an outer preset (the vehicle moves
  into the visible area). Rollback: revert the mapping (identity) or set both surfaces to
  `center/center`; the per-surface split is a superset of the previous single value.
- Tests: collision table (identity, center-exact under the phone nav insets — the device regression,
  single-axis moves, dual-axis corner move, footprint/padding boundary, invariant clamp, extreme
  insets), resolved fraction used by render target + marker, phone/AA value independence, car-fallback
  migration.

## Capabilities

### New Capabilities

- None — the anchor capability is introduced by the in-flight change `vehicle-position-presets`
  (unarchived); this change refines it.

### Modified Capabilities

- `smooth-follow`: "Vehicle position anchor in follow mode" — the fractions resolve against the
  visible map area and the phone reads the phone's own value.
- `location-options-ui`: "Vehicle anchor position controls" — the rows show and update the phone's
  own value; the shared value wording is replaced.
- `auto-map-layout`: "Settings dialog reachable while driving" — the rows show and update Android
  Auto's own value; the visible-area rule for the car surface (identity today).
- `auto/navigation-view`: "Vehicle anchor during navigation" — fractions relative to the visible
  area of the navigation surface, car's own value.
- `auto/free-driving`: "Follow mode activated" — same for the free-driving surface.

**Archive order (required)**: `vehicle-position-presets` first (it adds the requirements this change
modifies), then this change. `fix-phone-vehicle-anchor-framing` and its `smooth-follow` requirement
("Anchor-centered follow framing") land before both — the resolved fraction is what that requirement
feeds.

## Impact

- **Code**:
  - `core/src/main/java/com/naviveylin/core/VehicleAnchor.kt` — `resolveAnchorFraction(anchor, left, top, right, bottom, screenW, screenH)` (phone: map into the visible rect, identity when all insets are 0, clamped to `0.1..0.9`) and `clampAnchorOutOfPane(anchor, paneLeft, paneRight, screenW, screenH)` (Android Auto: move only the presets covered by the host panel); `anchorCenter` generalized to take a fraction pair (enum overload delegates, so AA call sites stay source-compatible); `ResolvedAnchor` value type.
  - `core/src/main/java/com/naviveylin/core/AutoSettings.kt` — car-facing fields stay routing/free-driving; the *phone* values are no longer shared into the car DTO.
  - `core/src/main/java/com/naviveylin/core/AutoSettingsProvider.kt` — new `saveCarAnchor(routingAnchorId?, freeDrivingAnchorId?)`, the only write that freezes the car's own anchor.
  - `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — `autoRoutingAnchorId`/`autoFreeDrivingAnchorId` (nullable → fall back to the phone value, the migration), phone fields unchanged.
  - `app/src/main/java/com/naviveylin/data/AutoSettingsMapping.kt` — car DTO resolves `autoX ?: phoneX` on read; a generic write-back never touches the car anchor fields (freezing happens only in `saveCarAnchor`).
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — overlay inset setter (`setMapOverlayInsets`), resolved fraction in `MapCanvasUiState`, follow render target and `renderFollowFrameAt` use it; the phone anchor values are read from the phone fields.
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — measure the insets (turn card, routing-status card, street-name pill at their call sites; widget column via `LocalOverlayWidthProbe` since its three call sites are identical) and publish them; the follow marker projects against the resolved fraction.
  - `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — rows keep reading the phone values; the four anchor parameters are now REQUIRED, so a call site (the routing view, 2026-09-14) can no longer compile into a no-op picker.
  - AA: `auto/.../AutoMapRenderer.kt` (host-pane clamp via `clampAnchorOutOfPane`, `setHostPaneRtl`, blit-offset compensation for the vehicle marker and the destination pin), `auto/.../PaneOffset.kt` (`isHostPaneOnRight`), `NavigationScreen.kt`/`FreeDrivingScreen.kt` (pass the pane side), `VehicleAnchorPickerScreen.kt` (writes the car's own value), `PreferencesScreenMapper.kt` (unchanged: it already read the car view).
- **Tests**: `core` collision/round-trip tests (identity, center-exact under the phone nav insets — the
  device regression, single/dual-axis moves, footprint boundary, band clamp, per-surface isolation, car
  fallback), `FollowAnchorFramingTest` (resolved fraction drives render target and marker), `MapCanvasViewModelVehicleAnchorTest` (published fraction, insets update, center regression), `AutoMapRendererTest` (host-pane clamp keeps the default, marker follows the blit offset), `AutoSettingsMappingTest`, `VehicleAnchorPickerScreenTest`, `MapRightWidgetColumnTest` (measured width probe), `LocationOptionsOverlayComposeTest`.
- **Guidelines**: `guidelines/MapRendering.md` §1.1 (the anchor fraction is kept exact unless covered — collision-remap, not a visible-area remap; the resolved fraction is published once and consumed by renderer and marker); `guidelines/UI.md` §8 (same labels/hierarchy, per-surface stored value documented as the platform deviation).
- **Interplay**: `vehicle-position-presets` (contract owner, archive first), `fix-phone-vehicle-anchor-framing` (framing contract this builds on). The wiring fix for the routing-view anchor rows (found 2026-09-14) is recorded in `vehicle-position-presets` task 5.2 and is already implemented. The Android Auto marker/destination offset documented in `TODO.md` §7 is fixed by this change (D7).
- **Native/JNI**: none.
- **Scope**: phone mapping + AA per-surface settings + AA host-pane clamp and marker/content alignment; AA geometry otherwise unchanged (browse/details keep `PaneOffset`).
