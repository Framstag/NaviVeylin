# Proposal

## Why

On the phone, starting the app in BROWSE shows the last persisted viewport, which is usually not
where the vehicle is — and there is then **no way to get to the vehicle**: the re-center button only
appears after the user happens to drag the map more than the 12 px gesture slop, because BROWSE
re-center visibility is driven by a sticky interaction flag (`browseDrifted`) rather than by whether
the map actually shows the vehicle. The same flag is also wrong in the other direction: after
panning back onto the vehicle the button stays visible. Users report the symptom as "browse does not
read the vehicle position, and I have to touch the display to get a re-center button".

## What Changes

- **BROWSE re-center visibility becomes a derived condition, not a flag.** The button is shown when
  a GPS fix exists **and** the vehicle's projected screen position is off the browse center by more
  than a threshold; it is hidden again once the offset falls below a smaller threshold
  (hysteresis), and the appear edge additionally requires the offset to persist briefly (dwell) so
  that GPS noise at high zoom cannot toggle it.
- **`browseDrifted` is removed** from `MapCanvasUiState`, together with its two writers
  (`disengageFollowMode()`, `updateMagnification()`); the BROWSE branch of `disengageFollowMode()`
  disappears. The FREE_DRIVE and NAVIGATION suspension behavior is unchanged.
- **BROWSE re-center stops using the vehicle anchor machinery.** It sets the viewport center to the
  current GPS position (one geo point, no `followRenderTarget`, no `resolvedAnchor`), stays in
  BROWSE, and hides the button. The configured "Vehicle position (free driving)" / "(navigation)"
  presets keep applying to the driving modes only. This is required for self-consistency: with a
  non-center preset, an anchor-framed browse re-center would leave the vehicle off center, the new
  condition would immediately re-show the button, and the button could never be dismissed.
- **BROWSE start keeps the last persisted viewport** (unchanged), and no GPS fix means no button
  (already true via the `gpsFixQuality != GpsFixQuality.NONE` guard at the call sites).
- **Spec ownership is de-duplicated**: `map-modes` owns *when* the button is visible per mode;
  `map-recenter-button` keeps *what* the control is (icon, hidden without a fix, placement).
  Today both specs state a browse visibility rule and they disagree.
- **`guidelines/UI.md` §7a is updated** in the same change: it documents the `browseDrifted`
  behavior and names `map-modes` as its source, so it would contradict the new spec.

**Additive, not breaking.** No persistence or wire format changes (`browseDrifted` is runtime UI
state; the settings file is untouched), no native/JNI change. One deliberate behavior reversal:
BROWSE re-center no longer honors the free-driving anchor preset
(`MapCanvasViewModelVehicleAnchorTest.browseRecenterCommitsTheAnchorCenteredFrame` asserts the old
behavior and is rewritten). Rollback: revert the change; browse returns to the `browseDrifted` flag
and the anchor-centered browse re-center, no data migration needed.

## Capabilities

### New Capabilities

None — this changes behavior the project already claims.

### Modified Capabilities

- `map-modes`: the "Browse re-center" requirement changes from "the user panned or zoomed away"
  (a remembered interaction) to "the vehicle is not at the browse center by more than a threshold"
  (a measured offset, with hysteresis and an appear dwell), and its re-center scenario states the
  plain "viewport center becomes the GPS position, staying in BROWSE, anchor presets not applied".
  The mode model, mode presets, mode toggle and drive-suspension requirements are unchanged.
- `map-recenter-button`: the visibility requirement loses its browse clause ("follow mode off" —
  BROWSE is the mode where follow is off, so it currently double-owns the same rule); it is scoped
  to the driving modes (FREE_DRIVE / NAVIGATION suspension) and to "no GPS fix → hidden". The icon
  requirement is unchanged.

## Impact

| Area | Files |
|------|-------|
| State / rule | `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — remove `browseDrifted` (`MapCanvasUiState` ~L211, writers ~L2900, ~L3443), rewrite `shouldShowReCenterButton` (~L3523) around the derived offset, simplify `recenterInBrowse()` (~L3014), add the threshold/dwell constants |
| Offset source | `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (viewport center + `gpsMarkerLat/Lon`), `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` (projected px distance — the same projection `LocationMarkerOverlay` uses) |
| Marker projection | `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — the 3 button call sites (~L1340 portrait, ~L1476 landscape, ~L1947 navigation branch) pass the derived value; placements and the `gpsFixQuality` guard unchanged |
| Guidelines | `guidelines/UI.md` §7a (BROWSE bullet: derived rule, no `browseDrifted`, no anchor preset in browse) |
| Tests | `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelVehicleAnchorTest.kt` (browse-recenter anchor assertion is reversed), `MapCanvasViewModelAutoZoomPauseTest.kt`, `MapCanvasViewModelFollowModeTest.kt`, `MapReCenterButtonOverlayTest.kt`, `MapCanvasViewModelNavEndRestoreTest.kt`, plus new tests for the offset/dwell/hysteresis rule |
| Specs | `openspec/specs/map-modes/spec.md`, `openspec/specs/map-recenter-button/spec.md` (deltas in this change) |

**Scope: phone / mobile variant only.** Android Auto has no phone-style browse mode — the car app
separates browse (`MapScreen`), free driving (`FreeDrivingScreen`) and navigation
(`NavigationScreen`) as distinct screens and drives follow through the car `MapController`. No `:auto`
source, no car screen, no car-anchor behavior changes; `auto/browse`, `auto/free-driving`,
`auto/map-pan` and `auto-map-layout` are untouched.

**Not in scope** (found while investigating, left for their own changes): `MapCanvasScreen.kt` ~L1806
still moves the free-driving street pill to the top of the screen in BROWSE when the
*free-driving* anchor preset is in the bottom row — harmless but conceptually the same
anchor-in-browse mix-up; and there is no off-screen direction indicator for a vehicle outside the
viewport (the derived button is the only cue).
