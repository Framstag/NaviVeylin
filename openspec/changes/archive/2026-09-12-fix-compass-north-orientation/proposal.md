## Why

The compass widgets render north at the wrong screen direction whenever the map is rotated: the sign convention is flipped 180° against the map projection. Driving east or west (heading 90°/270°, the only headings where a flip is visible — 0°/180° map rotations look identical), the compass shows south where north actually is. Affected: the phone `CompassButton` and the Android Auto compass rose (`SurfaceIndicators`).

## What Changes

- **Fix phone compass needle**: north needle (and follow-direction triangle) rotate by the map rotation with the correct sign, matching where the map projection actually renders north.
- **Fix phone follow-direction triangle**: points at the travel direction on screen (`bearing + mapAngle`), i.e. straight up while heading-up follow is active, instead of 180° off.
- **Fix Android Auto compass rose** (free driving + navigation): north pointer renders at the screen direction where true north sits under the heading-up rotation.
- **Consolidate the rotation convention in `:core`**: single `ProjectionUtils` function encodes "screen direction of north for a given viewport angle"; both UIs (phone Compose canvas, AA android.graphics canvas) consume it. Reuses the existing `screenBearing(bearing, angle)` for the follow triangle. Removes the duplicated local sign math that drifted apart.
- **Pin the convention with tests**: unit tests for the core function, direction assertions for both needles/rose, and a test that reproduces the east/west-heading scenario.

No breaking changes; UI-only behavior fix + internal refactor. Rollback: revert the change.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `compass-button`: pinning the needle rotation convention — the needle SHALL point at the screen position where the map renders north (currently unspecified/vague, and the implementation is 180° off for rotated maps), plus the follow-direction triangle direction.
- `auto-map-layout`: the navigation compass-rose requirement ("north pointer faces true north") gains concrete acceptance scenarios for heading-up rotation — currently unverifiable, and the rose renders 180° off while navigating east/west.
- `auto/free-driving`: the free-driving compass rose gains the same north-direction requirement/scenarios (positioning is specced, direction is not).

## Impact

Affected code (no native/JNI changes):

- `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` — add `compassRotationDegrees(angleRadians)` and/or `compassNeedleDegrees(...)`; existing `screenBearing` reused.
- `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` — replace local negation with core function; follow branch uses `screenBearing(bearing, angle)`; takes the bearing as a parameter.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — pass marker bearing into the compass block (call sites at L1128/L1252/L1694).
- `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt` — `drawRose` uses core rotation function instead of `canvas.rotate(-deg(angle))`.
- Tests: `core` unit tests; `CompassButtonComposeTest`/`SurfaceIndicatorsTest` direction cases (via extracted pure helpers); fix wording of the misleading "rotated 30 degrees CCW" comment in `ProjectionUtilsTest.kt`.

Guidelines: `guidelines/UI.md` parity rule (phone vs auto compass) is respected — both views get the same convention. No `guidelines/` doc changes needed.

Affected specs (`compass-button`, `auto-map-layout`, `auto/free-driving`) get delta files under this change. Existing spec text that stays true ("north pointer faces true north") is kept and made verifiable via scenarios.
