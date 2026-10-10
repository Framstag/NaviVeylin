# Proposal

## Why

Root cause: `drawCompassNeedle` strokes both needle halves with `strokeWidth = 3f`
(`app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` — HEAD lines `:229` and `:237`; the fix moves them to
`:233`/`:241`) — a raw device-pixel count — while every other length in the same drawing call is density-aware
(`needleLength = 10.dp.toPx()` at `:218`, the rim's `Stroke(width = 1.dp.toPx())` at `:144`), so the needle's
drawn width in dp shrinks as screen density grows: 3 px is 3 dp on a 1x screen and 0.86 dp at 3.5x.

Evidence: `com.naviveylin.ui.map.CompassNeedleStrokeTest#needle stroke keeps its width in dp at 1x and 4x` fails
on HEAD — XML `tests="1" skipped="0" failures="1" errors="0"`, `timestamp="2026-10-09T18:38:26.091Z"`,
`java.lang.AssertionError: the needle stroke must be the same width in dp at 1x and 4x (1x=4px=4.0dp,
4x=4px=1.0dp)` — the same pixel count at both densities, i.e. a 4x difference in dp. The case printed
`CompassNeedleStroke 1x=4px (4.0dp) 4x=4px (1.0dp)` before it asserted. That red XML is **not retained**: the
later green runs (and the final forced gate, task 4.2) overwrote it, and Gradle copies no test stdout into its
console log. The pre-fix row is re-creatable on demand — task 4.1's revert-check re-runs exactly this mutation
and reproduces the same failure message and row — and the *after* row is retained in the gate's XML:
`CompassNeedleStroke 1x=4px (4.0dp) 4x=12px (3.0dp)` in
`app/build/test-results/testMobileDebugUnitTest/TEST-com.naviveylin.ui.map.CompassNeedleStrokeTest.xml`
(ts `2026-10-09T18:53:43.465Z`) and the automotive sibling (ts `2026-10-09T18:51:28.811Z`).

Repro: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.CompassNeedleStrokeTest"`.

Spec: `compass-button` / Compass needle stroke is density-independent (added by this change)   Guideline:
`guidelines/UI.md` §8 (phone navigation overlay sizing — "density-aware (same visual size on every screen)")

## What Changes

- **`CompassButton.kt`**: the needle's two strokes take their width from a density-independent value — a
  `needleStrokeWidth = 3.dp.toPx()` local next to `needleLength` — instead of the raw `3f`. The needle keeps the
  width it renders at today on a 1x screen (3 px there); on a 4x screen it becomes 12 px instead of 3 px, i.e.
  the same 3 dp visual width. Nothing else in the widget moves: `needleLength`, the rim, the canvas and layout
  sizes, the palette and the rotation convention are untouched.
- **A host case measures the drawn stroke** (`CompassNeedleStrokeTest`): it composes the production
  `CompassButton` twice — `LocalDensity provides Density(1f)` and `Density(4f)` in one tree — rasterizes the
  view hierarchy under Robolectric native graphics, and measures the needle-colored pixel run across the
  needle. It asserts the drawn width is the same in dp at both densities and that it grows with density, and it
  prints both measurements, so the numbers this change quotes stay in the JUnit XML.
- **`guidelines/UI.md` §8** records the rule the bug broke: a drawn dimension (stroke width, radius, marker
  geometry) is density-independent — expressed in dp — never a raw device-pixel count.
- **`compass-button` gains one requirement** ("Compass needle stroke is density-independent") with the one
  scenario the case above exercises. No existing requirement is modified or removed.
- Additive, not breaking: no API, no persisted state, no layout change. Rollback is
  `git revert` of the two-line drawing call plus the test.

## Capabilities

### New Capabilities
<!-- none: the delta lands in the existing compass-button capability -->

### Modified Capabilities
- `compass-button`: a requirement is ADDED — the needle's stroke is a density-independent size (same visual
  width on every screen), asserted by the pixel-measured host case. Every existing requirement of the
  capability is unchanged.

## Impact

- **Code**: `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` (the two `strokeWidth` arguments in
  `drawCompassNeedle`), `app/src/test/java/com/naviveylin/ui/map/CompassNeedleStrokeTest.kt` (new).
- **Guideline**: `guidelines/UI.md` §8 — one bullet stating the density-independence rule with this
  measurement.
- **Spec**: `openspec/specs/compass-button/spec.md` gains the new requirement through this change's delta; the
  existing "Compass button matches overlay button sizing" and colour requirements are untouched.
- **Not affected**: no module boundary, DI, manifest, resource, native/JNI or persistence change; the compass is
  phone-only and the Android Auto compass rose is a separate renderer.
