# GPS Render Coalescing — Design

## Context

The existing render pipeline uses an `epoch` counter to discard stale render jobs. Every GPS tick previously incremented `epoch` and cleared the tile cache, which caused frames to be dropped when the native render took longer than the 1 s GPS interval. The front-buffer emission check also required an exact angle match, so small bearing changes during a slow render suppressed the finished frame. The goal is to keep the map visually in sync with the GPS marker without increasing render throughput.

## Goals / Non-Goals

**Goals:**
- Prevent duplicate GPS fixes from perturbing smoothed state or the render pipeline.
- Coalesce GPS-triggered follow-mode renders to at most one per 200 ms window.
- Derive the follow-mode map rotation from course-over-ground instead of the jumpy `Location.bearing`.
- Keep the stored map angle normalized to `[-π, π]` so wrap-around does not accumulate.
- Reset the course history after a sharp turn so old track points do not dominate the new heading.
- Keep the GPS marker on the raw GPS fix; smooth only the camera, not the marker.
- Allow a running render to finish instead of discarding it when a new GPS fix arrives.
- Emit rotated frames despite up to 5° bearing drift during a slow render.

**Non-Goals:**
- No change to native rendering performance or resolution.
- No change to GPS accuracy or navigation-engine filtering.
- No user-visible setting for throttle constants.
- No global filter that rejects backward movement (can be valid for pedestrians).

## Decisions

### Decision 1: Deduplicate by fix timestamp in `LocationService`

**Gewählt:** `LocationService` stores the timestamp of the last emitted fix and drops subsequent fixes with the same `location.time`. Fused and LocationManager deliver the same underlying GPS fix with the same timestamp, so this removes the duplicate emissions without discarding genuinely new fixes.

**Alternativen:**
- Deduplicate by (lat, lon) only — would drop repeated stationary fixes that are intentionally delivered every second.
- Deduplicate in the ViewModel — too late; the smoothed course already saw the duplicate.

### Decision 2: Coalesce in `MapCanvasViewModel`, not in `MapRenderer`

**Gewählt:** The ViewModel decides whether a GPS tick should trigger a follow-mode render and uses a 200 ms throttle. This keeps `MapRenderer` generic and makes the policy explicit at the GPS/ViewModel layer.

**Alternativen:**
- Move throttling entirely into `MapRenderer` — would also affect pan/zoom renders and hide the policy from the follow-mode logic.

### Decision 3: GPS position updates do not increment `epoch`

**Gewählt:** Only zoom, overlay, style, and marker visibility changes increment `epoch`. Pure GPS position updates submit a new render job but leave `epoch` unchanged, so a running job is not treated as stale. The conflated render queue guarantees that the latest position is rendered next.

**Alternativen:**
- Keep epoch bump and accept frame drops — directly causes the observed lag.
- Snapshot viewport on render start instead of enqueue — more invasive and would still race with overlay changes.

### Decision 4: Angle epsilon of 5° for emission

**Gewählt:** `ANGLE_EPSILON_RAD = 0.087` (≈5°) is large enough to absorb typical GPS bearing jitter during a 500–1200 ms render but small enough that a real turn (>10–15°) still triggers a fresh render.

**Alternativen:**
- Exact match — drops frames on every bearing tick, as seen in the logs.
- 10° epsilon — risks accepting a visibly wrong frame during a turn.
- Emit always — would show rotated frames that are noticeably out of sync after a real turn.

### Decision 5: Copy sub-region before recycle

**Gewählt:** `Bitmap.createBitmap(fb, x, y, w, h)` can share the backing pixel storage with `fb`. Recycling that shared view would free the storage while Compose is still drawing `fb` (or an earlier frame derived from it). The sub-region is therefore copied to an independent bitmap before `recycle()` is called.

**Alternativen:**
- Never recycle sub-regions — safe, but leaks the small view objects until GC runs.
- Always assume independent backing storage — unsafe; the Android API explicitly documents that createBitmap may share storage.
- Stop double-buffer swap during pan — would require tracking every outstanding Compose frame, too complex for this fix.

### Decision 6: Course-over-ground for follow rotation

**Gewählt:** Follow-mode map rotation and marker bearing are derived from the recent GPS track (course-over-ground). A 10-point ring buffer keeps positions; the bearing is computed between the newest point and the oldest point that is at least 30 m away. A light low-pass filter (`alpha = 0.3`) smooths small track wiggles. Course-over-ground is stable on straight roads and follows curves, whereas the raw `Location.bearing` on the test GPX replay contained 100°+ outliers.

**Alternativen:**
- Raw GPS bearing with outlier rejection — still too noisy on this track.
- Fixed circular average of raw bearings — rejected because different providers attach different bearings to the same fix, letting duplicates perturb the average.
- Longer position history — delays reaction to turns; 10 points / 30 m is the chosen compromise.

### Decision 7: Course history reset on sharp turns

**Gewählt:** When the latest movement segment diverges by more than 60° from the current smoothed course, the history is reset. Old points from before the turn are discarded so the map does not keep pointing in the previous direction while the vehicle has already turned.

**Alternativen:**
- Fixed 30 m window without reset — after a turn the window still contains pre-turn points and the map lags behind.
- Weighted by distance — more complex and still keeps stale influence.

### Decision 8: Map angle normalization

**Gewählt:** The follow-mode angle is normalized to `[-π, π]` in `MapCanvasViewModel` before storing it, in `MapRenderer.prepareViewport` before setting `currentAngle`, and when storing `frontBufferAngle`. This prevents the angle from growing unbounded (e.g. 14 rad) and producing wrap-around jumps.

**Alternativen:**
- Normalize only in the UI — too late; the deadband and rate-limit math already failed on the unnormalized value.
- Trust the native renderer to normalize — it does for rendering, but the Kotlin-side state still grew unbounded and caused incorrect decisions.

### Decision 9: No center smoothing in follow mode

**Gewählt:** The viewport center is the latest raw (or navigation-filtered) GPS fix. Only jumps larger than 500 m reset directly. The GPS marker is drawn at the same raw fix, so it never drifts away from the visible road/track.

**Alternativen:**
- EMA alpha 0.95 — still created a 9 m lag at 20 m/s, making the vehicle leave the road.
- Interpolate over time — would lag behind even more.

### Decision 10: Marker bearing consistency

**Gewählt:** The same COG-based `markerBearing` is used both when a full render is triggered and when the viewport move is below the 5 m threshold. The code no longer falls back to instantaneous `Location.bearing` in the "no viewport move" branch.

**Alternativen:**
- Use raw bearing for marker orientation — caused sudden marker spin while the map stayed still.

### Decision 11: Auto-zoom hysteresis on raw target

**Gewählt:** Auto-zoom hysteresis compares the raw fractional target with the current magnification. Previously it compared rounded integer targets, which reported `diff=0.0` for a target like 15.666 vs current 16 and caused sub-level pumping.

**Alternativen:**
- Commit on every fractional change — visible zoom pumping.
- Disable auto-zoom — not acceptable for driving UX.

## Risks / Trade-offs

- **User in a slow turn** may see one frame that is slightly rotated relative to the current bearing. The next GPS tick will start a corrected render as soon as the angle change exceeds the threshold.
- **Very fast GPS providers (>5 Hz)** are still throttled to 5 renders/second in follow mode, which is acceptable on devices that can render in <200 ms.
- **Navigation engine still receives every fix**, so route snapping and turn announcements are unaffected.
- **Course-over-ground needs 30 m of movement** before the first valid bearing. During the first 1–2 seconds after starting or teleporting, the map may stay North-Up or keep the previous bearing.
- **Sharp U-turns** reset the course history, so the map follows the new direction quickly but may briefly lack a bearing until 30 m of new movement is accumulated.
