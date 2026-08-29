## Purpose

Moves bearing smoothing out of the render layer into the location layer. `LocationService` emits a uniform `GpsFix` with two bearings — a smoothed one for map rotation (no render churn) and a freshest one for the marker arrow (no lag) — and the `LocationManager` fallback fakes Fused's bearing quality so consumers never branch on provider.

## ADDED Requirements

### Requirement: LocationService emits uniform fix with two bearings
`LocationService` SHALL emit a `GpsFix` containing position, accuracy, speed, a `smoothedBearing` for map rotation, a `markerBearing` for the marker arrow, and the fix timestamp. Consumers SHALL receive the same `GpsFix` shape regardless of which provider (Fused or `LocationManager`) is active.

#### Scenario: Fix carries both bearings
- **WHEN** `LocationService` emits a fix
- **THEN** the fix SHALL carry a `smoothedBearing` (for map rotation) and a `markerBearing` (for the marker arrow)

#### Scenario: Same shape for both providers
- **WHEN** the app runs on a Play Services device (Fused active) or on a GMS-less device (`LocationManager` active)
- **THEN** consumers SHALL receive the same `GpsFix` type with no provider-specific fields or branching

### Requirement: Fused path adds no bearing lag for the marker
When Fused is active and the delivered `Location` has a bearing, `markerBearing` SHALL be the Fused-delivered bearing without additional app-side smoothing, so the marker arrow has zero added lag.

#### Scenario: Marker bearing equals Fused bearing
- **WHEN** Fused delivers a fix with bearing 45°
- **THEN** `markerBearing` SHALL be 45° (no EMA or window applied)

#### Scenario: Smoothed bearing lightly filtered on Fused
- **WHEN** Fused delivers a sequence of bearings with small jitter
- **THEN** `smoothedBearing` SHALL be a lightly low-pass-filtered version of the Fused bearing, not a re-derivation from positions

### Requirement: LocationManager path fakes Fused bearing quality
When `LocationManager` is active (GMS-less devices), `smoothedBearing` SHALL be derived from the recent position track (course-over-ground with EMA low-pass, turn reset, teleport reset) and `markerBearing` SHALL be the latest segment bearing, so the fallback delivers the same quality as Fused.

#### Scenario: Smoothed bearing from track on fallback
- **WHEN** `LocationManager` delivers positions along a straight road
- **THEN** `smoothedBearing` SHALL follow the track direction with EMA smoothing

#### Scenario: Turn reset on fallback
- **WHEN** the position track turns sharply (segment delta above the turn-reset threshold)
- **THEN** the course history SHALL reset so the pre-turn direction does not drag the new course

#### Scenario: Teleport reset on fallback
- **WHEN** the position jumps more than the teleport threshold
- **THEN** the course history SHALL reset and the bearing SHALL start fresh

### Requirement: Map rotation does not re-render on small bearing changes
`MapCanvasViewModel` SHALL keep the deadband against the rendered angle, the per-render angle rate clamp, and the render throttle, so small `smoothedBearing` changes do not trigger a full map re-render.

#### Scenario: Small bearing change does not render
- **WHEN** `smoothedBearing` changes by less than the deadband threshold relative to the rendered angle
- **THEN** the map SHALL NOT re-render

#### Scenario: Large bearing change renders at clamped rate
- **WHEN** `smoothedBearing` changes by more than the deadband threshold
- **THEN** the map SHALL re-render with the angle change clamped to the per-render rate limit

### Requirement: Marker bearing has no added lag
The marker arrow SHALL use `markerBearing` directly — the freshest direction signal — without the smoothing applied to map rotation.

#### Scenario: Marker tracks freshest bearing
- **WHEN** `markerBearing` changes (e.g., after a turn)
- **THEN** the marker arrow SHALL reflect the new bearing immediately, without EMA lag

#### Scenario: Marker and map rotation decoupled
- **WHEN** `markerBearing` and `smoothedBearing` differ (e.g., just after a turn)
- **THEN** the marker arrow SHALL point along `markerBearing` while the map rotates toward `smoothedBearing` at its own pace

### Requirement: Course derived from raw provider positions
The bearing filter SHALL derive course from the raw provider positions, not from navigation-engine-filtered positions.

#### Scenario: Bearing independent of navigation state
- **WHEN** navigation is active and the nav engine snaps the marker position to the route
- **THEN** the bearing filter SHALL still use the raw provider positions for course derivation

### Requirement: Car map uses smoothed bearing
`AutoServiceModule` SHALL map `GpsFix` to `AutoPosition` with bearing = `smoothedBearing`, so the car map rotation does not churn.

#### Scenario: Car position carries smoothed bearing
- **WHEN** the car app receives a `GpsFix`
- **THEN** the `AutoPosition` SHALL carry `smoothedBearing` as its bearing
