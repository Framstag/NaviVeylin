# Proposal: GPS bearing smoothing in the location layer

## Why

`MapCanvasViewModel` re-derives course-over-ground from recent positions (40 m window, EMA low-pass, turn reset, teleport reset, segment bearing) because it does not trust the provider's bearing. That is signal-quality logic living in the render layer:

- On Play Services devices, Fused already smooths bearing (sensor fusion + Kalman). The ViewModel redoes Fused's job — redundant derivation from the same positions Fused already processed.
- The ViewModel knows about Fused vs `LocationManager` (comments, dedup reasoning, "bearing may differ between Fused and LocationManager"). Provider-specific knowledge leaks outside the location layer.
- The `LocationManager` fallback (GMS-less devices) does not fake Fused's features, so consumers cannot rely on a uniform bearing quality.

The app needs two different bearing behaviors that the current single pipeline cannot express cleanly:
- **Map rotation**: must not re-render the whole map on every small bearing change (render churn).
- **Marker arrow**: must have no lag — the freshest direction signal, no smoothing delay.

## What Changes

- `LocationService` emits a uniform `GpsFix` (lat, lon, accuracy, speedKmH, `smoothedBearing`, `markerBearing`, time) instead of a raw `Location`.
- A new internal `BearingFilter` in `LocationService` produces the two bearings, provider-agnostic to consumers:
  - **Fused path**: `smoothedBearing` = light EMA on `loc.bearing` (Fused already smooths — do not redo); `markerBearing` = `loc.bearing` as delivered (freshest, zero added lag).
  - **LocationManager path**: `smoothedBearing` = course-from-track + EMA (fakes Fused quality); `markerBearing` = latest segment bearing (fresher than the 40 m window, stable enough).
- `MapCanvasViewModel` loses the course history, EMA, turn reset, teleport reset, and segment-bearing logic (~100 lines). It keeps only render-side concerns: deadband vs rendered angle, angle rate clamp, render throttle/coalescing, and marker/map decoupling (marker = `fix.markerBearing`, map rotation = `fix.smoothedBearing`).
- Course is derived from **raw provider positions** (option a): nav-filtered positions no longer feed the bearing filter. Marker bearing may disagree a few degrees with the nav-snapped marker position on curves — accepted.
- `AutoServiceModule` (car) consumes `GpsFix`; car map bearing = `smoothedBearing` (car map rotation also should not churn).
- Provider knowledge (Fused vs `LocationManager`) stays **inside** `LocationService`. No consumer branches on provider.

## Capabilities

### New Capabilities
- `gps-bearing-smoothing`: uniform bearing smoothing in the location layer — `LocationService` emits a smoothed bearing for map rotation and a freshest bearing for the marker, with the `LocationManager` fallback faking Fused's quality.

### Modified Capabilities
- `gps-location-marker`: marker direction arrow now consumes `GpsFix.markerBearing` (freshest signal) instead of ViewModel-derived course bearing.
- `gps-render-coalescing`: bearing input for map angle updates now comes from `GpsFix.smoothedBearing`; the deadband/rate-clamp/throttle render protections stay in `MapCanvasViewModel`.
- `gps-provider-selection`: `LocationService` now also owns bearing smoothing, not just provider selection.

## Impact

- `app/src/main/java/com/naviveylin/location/LocationService.kt` — new `GpsFix` emission + `BearingFilter` (course history, EMA, turn/teleport reset, segment bearing moved from the ViewModel).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — remove course-derivation logic; consume `GpsFix`; keep deadband/rate-clamp/throttle.
- `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` — map `GpsFix` → `AutoPosition` (bearing = `smoothedBearing`).
- `app/src/test/java/com/naviveylin/location/LocationServiceTest.kt` — new `BearingFilter` tests (Fused pass-through, LocationManager fake parity, turn reset, teleport reset).
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFollowModeTest.kt` — update to `GpsFix`; verify deadband still prevents render churn and marker bearing has no added lag.
- No new dependencies, no manifest changes, no native changes.
