## Context

The `SpeedAgent` in libosmscout's navigation engine computes speed from GPS position differences using a 3-second FIFO buffer. This ignores the GPS-reported speed (`currentSpeed` field in `GPSUpdateMessage`) entirely. See `proposal.md` for motivation.

The Android `Location` object provides `getSpeed()` (m/s) from the GPS receiver, which is typically more accurate than position-difference computation, especially at low speeds and when GPS updates are infrequent.

## Goals / Non-Goals

**Goals:**
- Use GPS-reported speed as primary source in `SpeedAgent`
- Clear FIFO when stationary to prevent lingering speed
- Pass `-1.0` from Kotlin when GPS speed is unavailable

**Non-Goals:**
- No changes to auto-zoom, speed spike filtering, or navigation state display — they already consume `onCurrentSpeed` and benefit automatically
- No changes to the `PositionAgent` or other navigation agents
- No new dependencies

## Decisions

### Decision 1: GPS speed priority in SpeedAgent

**Chosen:** Check `gpsUpdateMsg->currentSpeed >= 0.0` first. If available, convert m/s → km/h (* 3.6) and emit `CurrentSpeedMessage` directly. Fall back to position-diff computation only when `currentSpeed < 0`.

**Alternatives considered:**
- **Always use position-diff** (current behavior): Ignores GPS speed entirely. Causes lag at stops and gaps with infrequent updates.
- **Blend both sources**: Adds complexity with no clear benefit — GPS speed is authoritative when available.
- **Use GPS speed only, no fallback**: Would break when GPS doesn't provide speed (some devices/configs).

**Rationale:** GPS speed is the direct measurement. Position-diff is a derived value that inherently lags and is sensitive to update frequency. Using GPS speed when available gives instant response to speed changes.

### Decision 2: Clear FIFO when stationary

**Chosen:** Clear `segmentFifo` when `currentSpeed < 0.5 m/s` (~1.8 km/h). This prevents the FIFO from holding old movement segments that would produce false speed after stopping.

**Alternatives considered:**
- **Don't clear FIFO** (current behavior): Speed lingers for ~3 seconds after stopping.
- **Clear FIFO on any GPS speed use**: Unnecessary — FIFO is only used in the fallback path.

**Rationale:** The FIFO is only relevant for the position-diff fallback. Clearing it when GPS says we're stationary ensures the fallback also reports zero if it's ever needed.

### Decision 3: Kotlin side passes -1.0 for unknown speed

**Chosen:** Use `if (loc.hasSpeed()) loc.speed.toDouble().coerceAtLeast(0.0) else -1.0` when calling `processLocation()`.

**Alternatives considered:**
- **Always pass `loc.speed`** (current behavior): `Location.getSpeed()` returns 0.0 when speed is not available, indistinguishable from standing still.
- **Pass `loc.speed` with `hasSpeed()` check but coerce to 0**: Same problem — can't distinguish unknown from zero.

**Rationale:** The native `NavigationController.processLocation()` contract specifies "speed in m/s, or negative if unknown". The Kotlin side must honor this contract.

### Decision 4: Red speed on overspeed

**Chosen:** In `NavigationStateOverlay`, compute `speedColor` as `MaterialTheme.colorScheme.error` (red) when both current and max speeds are valid and `currentSpeedKmH > maxSpeedKmH + 5`. Otherwise use `MaterialTheme.colorScheme.onSurface` (normal text color).

**Alternatives considered:**
- **Always show normal color** (current behavior): No visual warning when speeding.
- **Show red at any exceedance (> max)**: Too sensitive — 1 km/h over is often GPS noise.
- **Use threshold of 10 km/h**: Too lax — 6 km/h over is already meaningful.

**Rationale:** 5 km/h threshold avoids false positives from GPS speed jitter while still catching meaningful overspeed. Using `error` color is consistent with Material 3 conventions for warning states.

## Risks / Trade-offs

- **[GPS speed spike]** GPS can report spuriously high speeds briefly. Mitigation: existing 200 km/h cap in `SpeedAgent` and 150 km/h filter in `MapCanvasViewModel.filterSpeed()`.
- **[No GPS speed on some devices]** Some Android devices/configs don't provide GPS speed. Mitigation: position-diff fallback handles this case.
- **[Unit mismatch]** GPS speed is in m/s, position-diff computation produces km/h. Mitigation: explicit `* 3.6` conversion in the GPS path.
