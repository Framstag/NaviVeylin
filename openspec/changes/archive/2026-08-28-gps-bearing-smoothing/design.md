# Design: GPS bearing smoothing in the location layer

## Context

See proposal.md — Why. Today `MapCanvasViewModel` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`) owns all bearing interpretation: a course history ring buffer (40 m window, `COURSE_HISTORY_SIZE`), EMA low-pass (`COURSE_LOW_PASS_ALPHA` 0.3 slow / 0.7 fast), turn reset (`COURSE_TURN_RESET_DEG`), teleport reset (`centerSmoothMaxJumpM`), and segment bearing (`lastSegmentBearing`). `LocationService` (`app/src/main/java/com/naviveylin/location/LocationService.kt`) emits a raw `Location` with only timestamp+position dedup. The ViewModel re-derives course because it does not trust the provider bearing — but Fused already smooths, so on Play Services devices the ViewModel redoes Fused's job, and the ViewModel knows about Fused vs `LocationManager` (comments at lines 601-602, dedup reasoning).

## Goals / Non-Goals

**Goals:**
- Bearing smoothing lives in the location layer; consumers get uniform quality from Fused and `LocationManager` alike.
- Map rotation does not re-render on small bearing changes (deadband + rate clamp + throttle stay in the ViewModel).
- Marker arrow has no added lag (freshest bearing, no EMA).
- Provider knowledge (Fused vs `LocationManager`) stays inside `LocationService`.

**Non-Goals:**
- Changing fix cadence, accuracy thresholds, or the render pipeline itself.
- Dead-reckoning / GPS simulation when no fix is available (tracked in `TODO.md`).
- Any native (libosmscout) changes.

## Decisions

### Decision 1: Bearing smoothing moves into `LocationService` as a `BearingFilter`

`LocationService` gains an internal `BearingFilter` that consumes each incoming fix and produces two bearings. The filter is provider-aware **inside** `LocationService` only:

- **Fused path**: `smoothedBearing` = light EMA on `loc.bearing` (Fused already smooths — do not redo); `markerBearing` = `loc.bearing` as delivered.
- **LocationManager path**: `smoothedBearing` = course-from-track + EMA (the algorithm moved verbatim from the ViewModel); `markerBearing` = latest segment bearing.

**Alternatives considered:**
- **Keep smoothing in the ViewModel (status quo)**: signal-quality logic stays in the render layer; Fused's smoothing is redone; provider knowledge leaks. Rejected — this is the problem being fixed.
- **One provider-agnostic filter for both paths** (course-from-track + EMA always): guaranteed parity but keeps the redundant derivation on Fused that the user explicitly wants to avoid. Rejected.
- **Separate `BearingFilter` class in `core` consumed by both `LocationService` and the ViewModel**: over-engineering; the filter is only ever fed by `LocationService`. Rejected.

### Decision 2: Two bearing outputs — `smoothedBearing` and `markerBearing`

The app needs two different behaviors from one signal: map rotation must be churn-free (heavy smoothing + deadband), the marker must be lag-free (freshest signal). A single bearing cannot satisfy both. `GpsFix` carries both.

**Alternatives considered:**
- **Single smoothed bearing for everything**: marker arrow lags after turns — violates the no-lag requirement. Rejected.
- **Single raw bearing for everything**: map rotation churns on every small change — violates the no-churn requirement. Rejected.
- **Two bearings (chosen)**: matches the existing marker/map decoupling in the ViewModel (lines 683-696), which already splits freshest vs smoothed — the split just moves into the location layer.

### Decision 3: `GpsFix` data class replaces raw `Location` emission

`LocationService.location` becomes `StateFlow<GpsFix?>` where `GpsFix` is a plain data class (lat, lon, accuracy, speedKmH, `smoothedBearing`, `markerBearing`, time). Consumers (`MapCanvasViewModel`, `AutoServiceModule`) map it to their own types.

**Alternatives considered:**
- **Keep `Location` + separate bearing `StateFlow`**: two sources of truth with ordering hazards (bearing can lag the position it belongs to). Rejected.
- **Subclass `Location`**: `Location` is a framework class; adding fields requires a wrapper anyway. Rejected.
- **`GpsFix` (chosen)**: single atomic emission, framework-free, trivially testable, matches the existing `AutoPosition` pattern in `core`.

### Decision 4: Course derived from raw provider positions (option a)

The filter runs on the raw provider positions. Nav-filtered positions (route-matched by the native engine) no longer feed the bearing filter. Accepted tradeoff: on curves the marker bearing may disagree a few degrees with the nav-snapped marker position; the marker arrow is small and Fused positions are smooth anyway.

**Alternatives considered:**
- **Feed nav positions into the filter** (`LocationService.updatePosition(lat, lon)` called by the ViewModel when navigating): leaks ViewModel logic backwards into the location layer and couples the filter to navigation state. Rejected.
- **Keep course derivation in the ViewModel for the nav case**: reintroduces the leak this change removes. Rejected.
- **Raw provider positions (chosen)**: clean boundary; the nav engine still filters *position* (marker placement), the location layer owns *bearing*.

### Decision 5: Render protections stay in `MapCanvasViewModel`

The deadband vs rendered angle (`MIN_BEARING_DELTA_DEG`), per-render rate clamp (`MAX_ANGLE_RATE_DEG_PER_RENDER`), and render throttle/coalescing (`GPS_FOLLOW_RENDER_INTERVAL_MS`) stay in the ViewModel. They compare against the **rendered** angle and render timing — render state the location layer cannot know.

**Alternatives considered:**
- **Move deadband into `LocationService`**: the filter cannot know the rendered angle; would require the ViewModel to feed render state back into the location layer. Rejected.
- **Keep in ViewModel (chosen)**: correct split — location layer smooths the signal, render layer protects the render pipeline.

## Risks / Trade-offs

- [Marker bearing disagrees a few degrees with nav-snapped marker position on curves (option a)] → Accepted; marker arrow is small, Fused positions are smooth, and the nav engine still controls marker *position*.
- [Course now derived from raw positions instead of nav-filtered positions → slightly noisier course on the `LocationManager` path while navigating] → Accepted; the EMA + 40 m window absorbs the noise; Fused path unaffected.
- [Regression in render churn protection during the refactor] → The deadband/rate-clamp/throttle code is untouched, only its bearing input changes; `MapCanvasViewModelFollowModeTest` verifies no-render-on-small-change behavior.
- [Test migration churn] → `FollowModeTest` has minimal bearing coverage today (one `bearing = 45f` line); the moved algorithm is re-tested in `LocationServiceTest` with the same thresholds.

## Migration Plan

Single commit. Rollback = revert the change; no data or persisted state involved. Verify on device: `adb logcat -s NaviVeylin` — follow mode should show the marker arrow turning immediately at corners while the map rotation lags smoothly; no render storm on straight roads.

## Open Questions

None — option (a) (raw provider positions) is confirmed by the user; the two-bearing split and provider-aware filter follow from the no-churn/no-lag requirements.
