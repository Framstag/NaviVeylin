## Context

See `proposal.md` — Why. Relevant current state:

- `compassNeedleTarget(isNorthUp, bearingDegrees, mapAngleRadians)` in `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` returns `compassRotationDegrees(mapAngle)` in north-up mode and `screenBearing(bearing, mapAngle)` in follow-direction mode; `drawCompassNeedle` branches on `isNorthUp` to draw either the needle+"N" or a filled triangle.
- The follow-direction argument comes from `MapCanvasScreen` (`bearingDegrees = state.gpsMarkerBearing`) at three `MapRightWidgetColumn` call sites (free-form, landscape, routing view). `state.gpsMarkerBearing` is the *freshest* per-fix bearing, while the map rotation uses the *smoothed* bearing (`guidelines/MapRendering.md` §7).
- The durable convention for north's screen direction already exists and is test-covered: `ProjectionUtils.compassRotationDegrees(angle)` = map angle in degrees clockwise from screen-up (`ProjectionUtils.screenBearing(0°, angle)`), documented as the single convention for compass needles and roses (`guidelines/MapRendering.md` §6).
- Android Auto's compass rose already uses that convention (`auto/.../SurfaceIndicators.kt`), so the phone is the deviation.
- `guidelines/Design.md` §4 (one source of truth per data signal) and §2 (Material 3 / Compose UI patterns) apply; the change touches no threading, no native code and no persisted state.

## Goals / Non-Goals

**Goals:**

- One needle semantic for the phone in every orientation mode: north's screen direction, derived from the map rotation only.
- Remove the bearing input from the compass entirely, so needle behavior cannot depend on GPS bearing quality (standstill noise, provider differences).
- Keep the widget otherwise untouched: size, shadow, fix-quality fill, short-press re-center, long-press orientation toggle, placement.

**Non-Goals:**

- Changing the map's rotation itself, the follow-mode angle clamping/smoothing, or the "always north" toggle logic. (The known `lastUsedAngle` fallback defect in the north-up branch of the follow angle computation is a map-rotation issue and is fixed by the separate change `fix-north-up-orientation-angle`, so the compass needle can rely on the map angle being correct.)
- Changing the Android Auto compass rose, the vehicle marker arrow, or the orientation settings UI.
- Adding a second indicator (e.g. a small travel-direction tick next to the north needle). Not needed: the marker arrow and the map rotation already carry the travel direction, and a second indicator would reintroduce the bearing dependency on the compass surface.

## Decisions

### D1 — Needle target = north's screen direction in all modes

`compassNeedleTarget(mapAngleRadians)` returns `ProjectionUtils.compassRotationDegrees(mapAngleRadians)`, unconditionally; the `isNorthUp` and `bearingDegrees` parameters are dropped from the needle target, `CompassButton`, `MapCompassBlock` and `MapRightWidgetColumn`. The function is kept (rather than inlining `compassRotationDegrees` at the call site) so the compass keeps a single documented conversion point that the tests exercise.

Alternative A (keep the triangle as an additional needle and add a north tick): needs two visual layers in a 48dp circle to stay legible while driving; the user's expectation is a compass, and guidelines/UI.md sets driver-seat readability minimums (guidelines/UI.md §8). Rejected — a second indicator at this size is noise, and it keeps the bearing dependency the change is removing.

Alternative B (keep the triangle but compute it from the *smoothed* bearing so the swirl disappears): fixes the standstill symptom only; the needle still fails to show north in heading-up mode, which is the actual complaint. Rejected.

Alternative C (switch the whole widget to a rotating map/rose image): pure visual rework with no requirement change; would still need the north convention and a rotation source. Rejected as unnecessary scope.

### D2 — Drawing: one needle shape, no mode branch

`drawCompassNeedle` loses the `isNorthUp` branch and the triangle path; the north half + neutral south half + "N" glyph is drawn in every mode. The needle length stays as today (10dp half-length, 3px stroke, 11sp "N") so the widget size and the Material 3 styling are unchanged.

Alternative (keep both shapes and select by mode, only changing the target angle): leaves a dead-looking triangle for heading-up (always pointing up) or a needle that duplicates the north pointer; keeping the branch without a second semantic has no user value. Rejected.

### D3 — Remove the parameter rather than ignore it

`bearingDegrees` is removed from the public signatures instead of being kept-but-unused: an ignored parameter would let a future caller reintroduce the bearing coupling silently, and the compiler enforces the call-site cleanup.

### D4 — Test strategy

- `CompassNeedleTargetTest`: rewrite for the new contract — north-up 0°; heading-up southbound → 180°; eastbound → 270°; a `bearingDegrees`-free signature is asserted by compilation; the needle value equals `ProjectionUtils.compassRotationDegrees` for a table of angles including negative and >2π inputs (normalization).
- Standstill scenario at the composable/screen level: two states that differ only in `gpsMarkerBearing` produce the same needle angle (bearing independence), which is the regression test for the swirl.
- `CompassButtonComposeTest` / `MapRightWidgetColumnTest`: updated for the removed parameter; a test asserts the "N" glyph is drawn in follow-direction mode (the triangle is gone).
- Threading/lifecycle: no new component, state, coroutine or lifecycle owner; the needle is a pure function of `state.viewport.angle` (already on the Compose main thread, animated via `animateFloatAsState`). `guidelines/Design.md` §4 is unaffected.

## Risks / Trade-offs

- [Users lose the at-a-glance "is follow direction active?" cue the triangle gave] → the map rotation itself is the cue (and the orientation row in the location options sheet); UI.md §8's compass note is updated in the same change so the guideline does not contradict the spec.
- [The needle now depends on the map angle being correct; if a map-rotation defect leaves a stale angle, the compass inherits it] → that is the correct single-source behavior (the needle agrees with the map by construction); the known `lastUsedAngle` north-up fallback is recorded as a separate follow-up so it is not silently assumed fixed.
- [Removing public parameters breaks other callers/tests] → three call sites and their tests are in this change; the compiler flags any missed one, and `run-tests` covers the module.
- [Spec removal (two requirements) could read as losing documented behavior] → both removals carry Reason + Migration text and are covered by the guideline update and the new scenarios in the modified requirements.

## Migration Plan

1. Change the needle target + drawing, remove the parameter and update the three call sites.
2. Rewrite/extend the compass tests and run the suite (`run-tests` skill) with the phone flavors building cleanly.
3. Update `guidelines/UI.md` §8 (compass note) in the same change.
4. On-device check (phone, free driving + navigation, heading-up): needle points at north (down when driving south), does not swirl at a traffic light, and long-press still toggles the orientation; north-up mode unchanged.
5. Rollback: restore the previous `compassNeedleTarget` expression and the triangle branch (single commit revert); no persisted state is touched.

## Open Questions

None.
