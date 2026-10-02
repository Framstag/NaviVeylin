# Proposal — fix-stale-fix-quality

## Why

GPS fix quality is only re-evaluated when a new fix arrives: the freshness test lives inside
`locationService.location.map { … }` followed by `distinctUntilChanged()`
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:983-992`), so the last computed
value sticks forever once the flow goes quiet. Spec `compass-button` requires the fill to show the
no-fix tone when there is no fix, but after the GPS is switched off (or in a tunnel, a garage, a
cold-start gap) the compass keeps the GOOD fill indefinitely and no `GPS fix quality: NONE` line is
ever emitted. Every consumer of `gpsFixQuality` then inherits a position that has not existed for
minutes: the re-center button stays governed by `map-recenter-button` / `map-modes`, the speed
widget keeps `gpsAvailable = true`, and search scoping (`location-search`) keeps resolving an admin
region from a dead fix. Device-observed 2026-09-27 on `emulator-5554` (`TODO.md` §86): 30 s after
`cmd location set-location-enabled false` the compass still showed the GOOD fill `#1B4A24`.

Why now: no in-flight change owns this path, the defect is reached in the ordinary
tunnel/garage/permission-revocation case, and the planned `PositionSimulator` (`TODO.md` §8) needs
an honest "no fix" baseline before it can add estimation on top of it.

## What Changes

- Fix quality is re-derived on a slow tick as well as on fix emission, so it stops depending on a
  new fix arriving: the reported quality becomes `NONE` when the last fix is older than a long age
  limit (`FIX_AGE_LIMIT_MS`, 60 s), when the device's location services are switched off, or when no
  fix has ever arrived.
- The age limit is deliberately **not** `GPS_FIX_FRESHNESS_MS`: the Fused request uses
  `MIN_DISTANCE_M = 5.0f` (`LocationService.kt:828`) and the provider goes silent at standstill —
  the project documents the same throttling in `core/SpeedStaleness.kt`, which decays speed after
  just 3 s for that reason. A 5 s age-out would turn the compass red, hide both re-center buttons
  and drop the speed widget's GPS at every longer stop, which is a normal driving state, not a
  lost-fix state. The device-location switch is the signal that distinguishes the two, and no code
  reads it today (grep for `isLocationEnabled`: no hit).
- The derived value is not latched: the next fix restores the reported tier (GOOD at
  accuracy ≤ 50 m, POOR above it), and the tick publishes nothing while the derived value is
  unchanged, so the tick cannot drive needless recomposition.
- The age rule moves into a pure, unit-testable predicate in `:core`, mirroring
  `core/SpeedStaleness.kt` (framework-free, shared by the app's state holders) — the predicate
  `LocationService`, `NavigationEngine` and this ViewModel already share for the same "the last fix
  is too old" decision — so the tick and the fix-emission path evaluate exactly the same thing.
  `LocationService` gains the source check behind it (`LocationManager.isLocationEnabled`, API 28+,
  min SDK 29), exposed to the quality path without leaking a `Context` into the rule.
- The tick reuses the existing stale-speed ticker pattern (`MapCanvasViewModel.kt:1043-1049`):
  `Dispatchers.Default` with the real clock and a test hook, never the test scheduler
  (`TODO.md` §40.C.16). It reads the last fix, the current time and the location-source state, so a
  disabled source is reported within one tick instead of waiting out the age limit.
- No new UI, string, resource, permission or manifest change; no native/JNI change.
- Inherited behaviour, with no requirement text change: the consumers of "a GPS fix is available"
  (`map-recenter-button`, `map-modes` browse re-center, `map-speed-widget` `gpsAvailable`,
  `location-search` / `search-result-ranking` scoping and distance reference,
  `enhanced-details-sheet`, `poi-search`) finally get the condition they already specify. The last
  known marker position is deliberately *not* changed — `LocationMarkerOverlay`
  (`MapCanvasScreen.kt:1518`) keeps drawing the last fix; a visually distinct stale/estimated
  position is `PositionSimulator`'s scope, not this change's.

## Capabilities

### New Capabilities

- `gps-fix-quality`: defines what "a GPS fix is available" means for the app — the accuracy tiers
  (GOOD / POOR / NONE) and their **availability**: no fix received, location services switched off,
  or a fix older than the age limit all count as no fix; the derived value is re-evaluated on a slow
  tick without new input, and it returns to the reported tier on the next fix. Names the surfaces
  that read the quality (compass fill, re-center buttons, speed widget, search scoping), so each can
  reference one definition instead of restating it.

### Modified Capabilities

- `compass-button`: the "GPS fix status fill color" requirement names the shared `gps-fix-quality`
  definition and gains the unavailable-fix cases — the fill SHALL move to the no-fix presentation
  tone when the last fix ages past the age limit with no new fix arriving, or when the device's
  location services are switched off, and back to the reported tier's tone on the next fix.

Considered alternative: keep the definition inside `compass-button` (smallest diff, but seven
specs' conditions resolve through it and `PositionSimulator` will extend the same concept). The
project already carries cross-cutting GPS capabilities (`gps-bearing-smoothing`,
`speed-spike-filtering`, `location-permissions`), so a dedicated capability is the consistent home;
`compass-button` keeps the fill contract and references it.

## Impact

Affected code, by module:

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — the quality collector
  (`:980-1009`), `_gpsFixQuality` / `uiState.gpsFixQuality` (`:250`, `:335`),
  `GPS_FIX_FRESHNESS_MS` (`:3947`), `GPS_FIX_MAX_ACCURACY_M`, the new ticker beside the
  stale-speed ticker (`:1043`), and the `GpsFixQuality.NONE` branch of
  `refreshBrowseReCenter` (`:3095`).
- `core/src/main/java/com/naviveylin/core/` — one new pure predicate object for the age rule
  (precedent: `core/SpeedStaleness.kt`, used by `LocationService.kt:390`, `NavigationEngine.kt:202`
  and `MapCanvasViewModel.kt:1046`). No Android type crosses into the rule.
- `app/src/main/java/com/naviveylin/location/LocationService.kt` — the location-source signal
  (`LocationManager.isLocationEnabled`, API 28+; the service already owns the `LocationManager`),
  read by the quality path on the tick.
- Behaviour-only consumers (no code change expected, behaviour becomes correct):
  `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` (`compassFillColor`),
  `MapCanvasScreen.kt` (re-center gates `:1686`/`:1823`/`:2318`, speed widget `:1891`), and the
  marker overlay `:1518` (unchanged by design).
- Tests: a new pure test for the age predicate (boundary cases, in the shape of
  `app/src/test/java/com/naviveylin/location/SpeedStalenessTest.kt`); a `MapCanvasViewModel` case
  that ages a fix out **without** emitting a new fix and asserts `NONE`, then the reported tier again
  on the next fix (the existing tests all emit a fix before asserting quality); a `LocationService`
  case for the source signal (enabled → disabled → enabled); no regression in
  `MapCanvasViewModelFollowModeTest`, `MapReCenterButtonOverlayTest`, `MapRightWidgetColumnTest`,
  `CompassButtonComposeTest`, `CompassPaletteTest`.
- Android components: none. No manifest entry, permission, resource, navigation graph or Compose
  layout is touched; no Car App Library surface is involved (`GpsFixQuality` appears nowhere in
  `:auto` or `:core` — the quality surface is the phone map screen only).
- Native/JNI: none. This is a Kotlin-only change — no libosmscout submodule patch, no
  `:osmscout-client-java` bridge override, no new JNI entry point, no ABI/NDK implication.
- Guidelines affected: `guidelines/Design.md` §4 (the ticker's dispatcher, clock and test hook) and
  the single-source-of-truth rule for the moved predicate (§12). `guidelines/UI.md` needs no
  change: the compass already has the three fill tones and no new control or label is introduced.
  `guidelines/Build.md` is unaffected (no new test or build recipe beyond the existing suites).
- Previous specifications changed: `compass-button` (its fill requirement), plus the new
  `gps-fix-quality` capability that replaces the implicit definition.

Classification and rollback: **additive** — no public API, storage format, wire format or UI
contract changes. The only user-visible deltas are the intended ones: when location services are off
(within one tick) or the last fix is older than the age limit, the compass shows the no-fix tone, the
re-center buttons hide and the speed widget reports no GPS — while a stationary vehicle keeps its
tier. Rollback is a revert of the ticker, the predicate and the source signal in one commit —
nothing persisted, no migration, no cleanup.

Scope: phone map screen only; the Android Auto / AAOS surfaces are unchanged. Explicit non-goals:
no dead reckoning or estimated positions (`PositionSimulator`, `TODO.md` §8), no change to the
accuracy thresholds, no new diagnostics or settings, no change to the car.

Consequence accepted by this change (design decides the detail): while a fix is aged out or its
source is off, the fix-quality consumers report "no fix" but the location marker stays drawn at the
last known position. The alternative — keeping the last quality until a new fix arrives — is the
defect being fixed; rendering a stale or estimated position distinctly is `PositionSimulator`'s work
(`TODO.md` §8), which is also where the remaining in-tunnel case (provider live, no signal, fix not
yet aged out) gets its ESTIMATED/LOST states.
