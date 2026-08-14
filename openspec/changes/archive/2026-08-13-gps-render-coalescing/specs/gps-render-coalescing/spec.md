# GPS Render Coalescing

## Purpose

Reduce the number of full native map renders triggered by GPS updates while keeping the navigation engine and the visible marker position in sync.

## ADDED Requirements

### Requirement: GPS fix deduplication

The system SHALL emit a GPS fix only once even if multiple location providers deliver the same underlying fix. Deduplication SHALL use timestamp and coarse coordinates; it SHALL NOT require matching bearing because different providers may attach different bearing values to the same fix.

#### Scenario: Fused and LocationManager deliver the same fix

- **WHEN** `FusedLocationProviderClient` and `LocationManager` both receive a fix with the same timestamp and coordinates within ~10 cm
- **THEN** `LocationService` emits only one `StateFlow` value for that timestamp

#### Scenario: A duplicate fix arrives 20 ms after the original

- **WHEN** the second fix has the same time and nearly the same coordinates
- **THEN** it is discarded before it updates the smoothed center or submits a render
- **AND** it does not push the viewport back toward the previous position

### Requirement: Follow-mode render throttling

The system SHALL not start a new follow-mode map render more often than every 200 ms because of GPS ticks.

#### Scenario: Multiple GPS ticks arrive within 200 ms

- **WHEN** two or more distinct GPS fixes arrive within 200 ms
- **THEN** only one follow-mode render is initiated
- **AND** the navigation engine still receives every fix


### Requirement: Map rotation derived from course-over-ground

The system SHALL derive the follow-mode map rotation from the recent GPS track (course-over-ground), not from the instantaneous `Location.bearing`. The course is computed between the newest history point and the oldest point that is at least 30 m away.

#### Scenario: GPS bearing jumps while the track stays straight

- **WHEN** the reported GPS bearing flips by 140° but the last 30 m of track continue straight
- **THEN** the map rotation follows the track direction
- **AND** it does not snap to the noisy single-fix bearing

### Requirement: Course-over-ground history

The system SHALL keep the last 10 distinct GPS positions in a ring buffer and use them to compute the course-over-ground bearing.

#### Scenario: Vehicle moves 30 m in a straight line

- **WHEN** 10 consecutive fixes form a straight 30 m segment
- **THEN** the course bearing equals the segment direction
- **AND** the map rotates so the segment points up

### Requirement: Course bearing low-pass filter

The system SHALL apply a light low-pass filter with alpha 0.3 to the course-over-ground bearing so small wiggles in the track do not jitter the map.

#### Scenario: Track wiggles 3° left and right

- **WHEN** the computed course alternates between 88° and 92°
- **THEN** the filtered bearing stays near 90°
- **AND** the map rotation does not oscillate

### Requirement: Bearing jitter suppression

The system SHALL ignore course bearing changes smaller than 2° relative to the already rendered angle when deciding whether to rotate the follow-mode map.

#### Scenario: Filtered course fluctuates by 1–2° between fixes

- **WHEN** the rendered map angle is -12° and the new course implies -13°
- **THEN** the map angle stays at -12°
- **AND** no render is triggered solely by this small angle change

### Requirement: Map rotation rate limit

The system SHALL rotate the follow-mode map by at most 90° per rendered frame toward the target angle so the map can follow normal turns quickly (renders run at the GPS fix cadence, ~1/s; 30°/frame took ~13 s for a 92° turn).

#### Scenario: Course bearing changes by 80° after a turn

- **WHEN** the rendered map angle is -12° and the new course implies -92°
- **THEN** the next rendered frame uses -92°
- **AND** the map completes the turn within 1–2 frames

### Requirement: Course unavailable

The system SHALL keep the previous smoothed course bearing and the previous used map angle when there is not enough recent movement to compute a new course-over-ground bearing. It SHALL NOT snap back to North-Up or to an unrotated marker.

#### Scenario: Vehicle is crawling or stationary

- **WHEN** the last 10 fixes span less than 40 m
- **THEN** the map keeps the previous course bearing
- **AND** the map does not spin from missing or noisy bearing data

### Requirement: Course-over-ground minimum distance

The system SHALL compute course-over-ground using the oldest history point that is at least 40 m away from the newest point. A longer baseline averages out per-fix GPS jitter so the follow rotation does not oscillate.

#### Scenario: Vehicle moves 50 m in a straight line

- **WHEN** consecutive fixes form a 50 m straight segment
- **THEN** the course bearing is available after 40 m
- **AND** the map rotation follows the segment direction without per-fix jitter

### Requirement: Keep last valid course bearing

The system SHALL keep the last valid course bearing and the last used map angle when no new course-over-ground bearing is available. It SHALL NOT fall back to North-Up (`angle=0`) or to an unrotated marker (`bearing=-1`) in follow mode just because the latest history window is too short.

#### Scenario: GPS jitter briefly breaks the 40 m course window

- **WHEN** a new fix does not yet provide a 40 m course segment
- **THEN** the map keeps the previous follow-direction angle
- **AND** the marker keeps the previous course bearing so the arrow still points up

### Requirement: Marker bearing persistence

The system SHALL use the last known course bearing for the GPS marker in follow mode when the current course bearing is unavailable. The marker arrow must always point toward the top of the screen in follow-direction mode.

#### Scenario: Vehicle is still accelerating from a standstill

- **WHEN** fewer than 40 m of movement have been recorded
- **THEN** the marker is drawn with the last known course bearing
- **AND** the arrow points up as the map rotates


### Requirement: Map angle normalization

The system SHALL keep the follow-mode map angle normalized to the range `[-π, π]` at all storage points (`viewport state`, `MapRenderer.currentAngle`, `MapRenderer.frontBufferAngle`) so the angle cannot accumulate unbounded rotations and cause visible wrap-around jumps.

#### Scenario: Follow mode receives a bearing near ±180°

- **WHEN** the smoothed course bearing crosses the 0°/360° boundary
- **THEN** the stored and rendered angles stay within `[-π, π]`
- **AND** the map does not spin through multiple full turns

### Requirement: Course bearing turn detection

The system SHALL reset the course-over-ground history when the latest movement segment (at least 8 m long) turns more than 45° from the current smoothed course. Old history points from before a turn must not keep the map pointing in the previous direction. The reset also clears the smoothed bearing so the new direction is established from fresh post-turn points.

#### Scenario: Vehicle turns 90° at an intersection

- **WHEN** the latest segment diverges by more than 45° from the previous smoothed course
- **THEN** the history resets, discarding pre-turn points
- **AND** the map begins following the new direction within a few fixes

### Requirement: Fast course reconvergence after reset

The system SHALL use a short course distance (10 m) and a fast low-pass alpha (0.7) while the course history is not yet stable (after a start or a turn reset). Once 40 m of track is available, it SHALL switch to the stable 40 m window with alpha 0.3.

#### Scenario: Vehicle just completed a 90° turn

- **WHEN** post-turn history accumulates 10 m
- **THEN** the course bearing reflects the new direction with fast alpha
- **AND** the marker/map align with the new driving direction within ~20–30 m instead of 60–80 m

### Requirement: Course bearing NaN instead of sentinel

The system SHALL return NaN (not -1.0) when no valid course bearing is available. The caller falls back to the last used bearing; a -1.0 sentinel would be treated as a valid ~1° bearing and slowly rotate the map toward North-Up after a reset.

#### Scenario: Course unavailable after a history reset

- **WHEN** the course history is empty but the previous bearing was 41°
- **THEN** the map keeps the previous 41° bearing
- **AND** it does not drift toward 0°

### Requirement: Marker bearing consistency

The system SHALL use the same course-over-ground bearing for the GPS marker in follow mode, even when the viewport does not move enough to trigger a full render. It SHALL NOT fall back to the instantaneous `Location.bearing` in that branch.

#### Scenario: Viewport move below the render threshold

- **WHEN** a new fix is within 5 m of the last rendered position
- **THEN** the marker is still updated at the raw GPS fix
- **AND** the marker orientation stays based on the freshest course/segment bearing

### Requirement: Marker arrow uses freshest direction

The system SHALL orient the GPS marker arrow from the freshest available direction signal, independent of the map rotation smoothing: window course bearing first, then the latest two-point segment bearing, then the last used bearing. The map rotation may lag after a turn (low-pass + rate limit), but the marker arrow must point along the new road immediately.

#### Scenario: Vehicle turns 90° at an intersection

- **WHEN** the vehicle has completed the turn but the map rotation still lags
- **THEN** the marker arrow uses the new course/segment bearing
- **AND** the arrow points along the new road on screen while the map catches up

#### Scenario: GPS position jitters but does not trigger a render

- **WHEN** a new fix is within 5 m of the last rendered position
- **THEN** the marker is still updated at the raw GPS fix
- **AND** the marker orientation stays based on the freshest course/segment bearing

### Requirement: Center position smoothing

The system SHALL NOT apply exponential smoothing to the follow-mode map center. The viewport center SHALL be the latest raw (or navigation-filtered) GPS fix. A jump larger than 500 m resets the center directly to handle teleports.

#### Scenario: GPS position jitters by 3–5 m while the vehicle is stationary

- **WHEN** consecutive fixes differ by a few meters only
- **THEN** the map center updates to the new fix
- **AND** the marker remains exactly on the vehicle position

#### Scenario: A GPS fix arrives while a render is still coalesced

- **WHEN** the latest fix is stored as the viewport center even though the render is throttled
- **THEN** the next rendered frame is centered on the latest position
- **AND** the camera does not catch up in one big jump

### Requirement: GPS marker stays on the raw GPS fix in follow mode

The system SHALL draw the GPS marker at the raw (or navigation-filtered) GPS fix in follow mode. The marker represents the current vehicle position and must sit on the road/track. The viewport center is the same raw fix, so the marker stays at screen center.

#### Scenario: Vehicle moves forward in follow mode

- **WHEN** a new GPS fix arrives
- **THEN** both the viewport center and the marker are at that fix
- **AND** the marker screen offset stays near (0, 0)

### Requirement: Auto-zoom hysteresis

The system SHALL change auto-zoom magnification only when the raw computed target differs from the current magnification by at least one full level. Rounding the target to the nearest integer SHALL NOT be used for the hysteresis comparison.

#### Scenario: Computed zoom target oscillates between 15.6 and 16.0

- **WHEN** the current auto-zoom magnification is 16
- **WHEN** the computed target is 15.7
- **THEN** no zoom change is committed (raw diff 0.3 < 1.0)
- **AND** no full render is triggered solely by this sub-level fluctuation

### Requirement: Render job uses snapshotted viewport

The system SHALL evaluate the completed native render against the viewport values stored in the render job, not against the renderer's mutable `currentLat`/`currentLon`/`currentAngle`. This prevents a GPS tick that updates the target viewport during a slow render from discarding or mis-aligning the completed frame.

#### Scenario: A GPS tick updates the target center while a render is running

- **WHEN** a new fix calls `prepareViewport` during `executeRender`
- **THEN** the completed frame is emitted if it matches the job's epoch, magnification, and angle
- **AND** the next render job uses the new `prepareViewport` values

### Requirement: Running render not discarded by GPS epoch bump

The system SHALL NOT increment the renderer epoch for a pure GPS position update.

#### Scenario: New GPS fix arrives while native render is still running

- **WHEN** a GPS position update is received while a previous render is executing
- **THEN** the running render completes and its result is evaluated for emission normally
- **AND** the next render job uses the latest GPS position

### Requirement: Completed render emission

The system SHALL emit a completed rotated front buffer regardless of how much the current target angle drifted during the render. Only epoch and magnification mismatches may suppress a finished frame.

#### Scenario: Bearing drifts during a slow render

- **WHEN** a rotated native render job starts at angle A
- **WHEN** the current target angle changes to A + 20° before the job finishes
- **THEN** the completed frame is still emitted to the front buffer
- **AND** the next render job uses the new target angle

### Requirement: Independent front-buffer frame

The system SHALL emit an independent copy of every frame shown to Compose. `Bitmap.createBitmap(fb, x, y, w, h)` and `frontBuffer` share backing pixel storage with `fb`; the next render overwrites that storage via `backBuffer.setPixels()`, which would show the new map content at a stale crop offset (visible left/right jumps without rotation).

#### Scenario: Rotated render followed by another render

- **WHEN** render B swaps buffers while Compose still draws render A's extracted frame
- **THEN** the displayed frame is an independent copy of A's pixels
- **AND** render B's `setPixels()` cannot mutate the displayed image

### Requirement: Rotation-aware sub-region blit

The system SHALL rotate the sub-region blit delta by the viewport angle. In a rotated viewport the screen-space shift of a geo delta is the unrotated shift rotated by the angle; without this the blit shifts the map content by up to `sin(angle) × move` horizontally.

#### Scenario: Small GPS move at -40° rotation

- **WHEN** the viewport angle is -40° and the vehicle moves 30 m north
- **THEN** the sub-region blit shifts the content by the rotated delta, not the unrotated one
- **AND** the map does not jump sideways until the next full render

