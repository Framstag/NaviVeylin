# Design — fix-stale-fix-quality

## Context

See `proposal.md` — Why. The current state that shapes the approach:

- The quality is computed once per location emission inside `MapCanvasViewModel`
  (`:980-1009`): `locationService.location.map { … }.distinctUntilChanged().debounce(2_000L)`
  publishes `_gpsFixQuality` and `uiState.gpsFixQuality`. `GPS_FIX_FRESHNESS_MS = 5_000L`
  (`:3947`) is only ever consulted when a fix *arrives*, and `GPS_FIX_MAX_ACCURACY_M` splits
  GOOD from POOR.
- `LocationService` (`app/src/main/java/com/naviveylin/location/LocationService.kt`) owns the
  provider lifecycle: one `MutableStateFlow<GpsFix?> location` (`:430`), leases driving
  `startFusedUpdates`/`startManagerUpdates`, and the request config
  `UPDATE_INTERVAL_MS = 1_000L`, `FASTEST_INTERVAL_MS = 500L`, `MIN_DISTANCE_M = 5.0f` (`:826-828`).
  Nothing in the app reads `LocationManager.isLocationEnabled` (verified: no hit in
  `app/`, `auto/`, `core/`).
- The project has one precedent for "the last fix is too old": `core/SpeedStaleness.kt`
  (`STALE_SPEED_MS = 3_000L`) with a per-fix timestamp, a pure predicate, a separate pure test
  (`app/src/test/java/com/naviveylin/location/SpeedStalenessTest.kt`), and a **real-clock ticker** in
  the ViewModel (`:1043-1049`, `Dispatchers.Default`, `delay(SPEED_STALE_TICK_MS)`), whose tests poll
  real time (`MapCanvasViewModelSpeedWidgetTest.awaitSpeedCondition`). Its KDoc records the fact this
  design turns on: *"The location provider goes silent at standstill (min-distance throttling)."*
- No `:auto`/`:core` surface reads `GpsFixQuality`; the quality is phone-only.

## Goals / Non-Goals

**Goals**

- The quality stops being a latch: it is re-derived while the location source is silent, and it goes
  to NONE when the source is off or the fix has aged out.
- One definition of "a fix is available" for every consumer, so the compass, the re-center buttons,
  the speed widget and the search scoping cannot disagree.
- No new user-visible UI, no new state field, no change to the marker or to the car.

**Non-Goals**

- Estimating movement without a fix, or rendering a stale/estimated position distinctly
  (`PositionSimulator`, `TODO.md` §8).
- An "in tunnel" state distinct from "no fix": a live provider with no signal is covered only by the
  age limit here.
- Any change to `GPS_FIX_MAX_ACCURACY_M`, to the accuracy tiers, or to the provider request config
  (notably `MIN_DISTANCE_M` stays 5 m).

## Decisions

### D1 — Availability rule: source-aware plus a long age bound

Quality is NONE when: no fix has ever been received, the platform reports location services
disabled, or the last fix is older than `FIX_AGE_LIMIT_MS = 60_000L`. Otherwise it is POOR/GOOD by
accuracy as today.

Why: the recorded defect (`TODO.md` §86, `cmd location set-location-enabled false`) is the "no
source" case, and the source state answers it within one tick. The age bound is the fallback for a
live-but-silent provider, and it must be long, because `MIN_DISTANCE_M = 5.0f` silences the provider
at standstill — a short bound would turn the compass red, hide both re-center buttons and drop the
speed widget's GPS at every longer stop, i.e. while driving is perfectly normal. The spec pins the
window at 30-60 s; 60 s is chosen so the standstill case is unambiguous.

Alternatives considered:

- **Short bound only (`GPS_FIX_FRESHNESS_MS`, 5 s)** — the smallest diff and symmetric with the
  existing constant, but it mislabels every standstill as a lost fix: with a 5 m minimum distance a
  stationary vehicle can go silent for minutes. Rejected as a user-visible regression.
- **Source state only, no age bound** — never mislabels standstill and fixes the recorded repro, but
  a tunnel or a garage keeps the GOOD fill indefinitely, which is the other half of the reported
  symptom ("in a tunnel … the compass keeps claiming a good fix"). Rejected as incomplete; the bound
  caps the lie at 60 s until §8 adds proper ESTIMATED/LOST states.
- **Movement-aware aging (age out only when the app believes the vehicle is moving)** — needs speed
  and heading history and still guesses in traffic; rejected as more state for a worse answer.

### D2 — One combined pipeline, not a second writer

The existing collector becomes
`combine(locationService.location, fixQualityTicks) { fix, _ -> qualityOf(fix, now, sourceEnabled) }`,
keeping `distinctUntilChanged()` and `debounce(2_000L)` unchanged. `fixQualityTicks` is a
`MutableStateFlow<Long>` counter incremented by a ticker loop that delays on
`fixQualityTickDispatcher` (`withContext` per iteration, so the hook is read every time). A
`StateFlow` rather than a cold tick flow is deliberate: the combined value then exists as soon as the
location flow has one, so the first quality never waits for a tick to be delivered from another
thread — with a cold tick flow the first value arrives asynchronously, which is invisible on a device
and a race in a virtual-time test.

Alternatives considered:

- **Second ticker coroutine writing `_gpsFixQuality`** (the stale-speed shape) — two writers to one
  state field, so the tick and an emission can race and the last writer wins on a stale sample.
  Rejected: the quality has two inputs (fix, source) and belongs in one place.
- **A cold `flow { emit(Unit); delay(…) }.flowOn(dispatcher)` tick** — one less field, but its first
  emission is a cross-thread delivery when the tick dispatcher is real, which makes the first derived
  value non-deterministic under a virtual clock. Rejected after it flaked a suite case.
- **A scheduled timer per fix (`delay` until `fix.time + limit`, cancelled on each fix)** — fewest
  wake-ups, but adds per-fix job bookkeeping, and the source check still needs a periodic poll.
  Rejected as more state for no measurable gain at a 1 s tick.

### D3 — The age predicate is a pure `:core` object; the source check stays in `LocationService`

`core/FixFreshness.kt` (framework-free, in the shape of `core/SpeedStaleness.kt`):
`FIX_AGE_LIMIT_MS = 60_000L` and `isAgedOut(fixTimeMs, nowMs, limitMs = FIX_AGE_LIMIT_MS)`, with the
"never had a fix" case expressed as `fixTimeMs <= 0L` → not aged out (the caller's `fix == null`
branch owns NONE). The accuracy split stays where the enum lives (`:app`), so no `GpsFixQuality`
type has to move.

Alternatives considered: keep the arithmetic inline in the ViewModel (untestable without the whole
ViewModel) and promote the enum plus the rule into `:core` (moves a type used by `CompassButton`,
`MapCanvasScreen` and several test files for no second consumer — `:auto` has no fix-quality
surface).

The source signal is `LocationService.isLocationSourceEnabled()`, a plain read of
`LocationManager.isLocationEnabled` (API 28+, min SDK 29) with `runCatching` → `true` on failure
(never report a lost fix because a platform call threw), marked `internal`/`@VisibleForTesting` and
backed by the `setLocationSourceReadForTest` seam so tests drive it through the existing service seam
instead of depending on Robolectric's shadow support. It is **polled** by the quality tick, not
published as a flow: it is one line, it needs no listener lifecycle, and `LocationManager`'s own
`onProviderDisabled` callback exists only on the non-Fused fallback path, so listening would be
unreliable on Play-Services devices.

It is deliberately **not** `suspend`: the read is a binder call, so the caller runs it off the main
thread, and `MapCanvasViewModel` wraps it in `withContext(defaultDispatcher)` — the dispatcher hook
those tests already share with `runTest`. A service-internal `withContext(Dispatchers.Default)` was
tried first and made a case pass alone but fail in a combined run, because the resume landed outside
the virtual clock (the hazard `MainDispatcherRule` documents).

### D4 — Tick period 1 s, reusing the stale-speed precedent

`FIX_QUALITY_TICK_MS = 1_000L`, matching `SPEED_STALE_TICK_MS`. Worst-case detection delay is one
tick plus the existing 2 s debounce (≈3 s), which the spec's "bounded delay" allows. A 5 s tick
would add up to 5 s of stale green; a sub-second tick buys nothing for a 60 s window.

### D5 — Threading and lifecycle

- The ticker runs in `viewModelScope` on `Dispatchers.Default` with the **real** clock, exactly like
  the stale-speed ticker, so it never feeds the virtual test scheduler (`TODO.md` §40.C.16 — an
  endless `delay` loop on the test scheduler hangs unrelated `runTest`s).
- The combined collector stays on the ViewModel's main dispatcher; the source poll runs through
  `withContext(defaultDispatcher)` (the existing `internal var defaultDispatcher` hook) so the main
  thread performs no system-server binder call (`guidelines/Design.md` §4).
- No new scope, no lifecycle callback, no `Context` held: the ticker dies with the ViewModel, which
  the map screen owns.

### D6 — Observable surface unchanged

`_gpsFixQuality` / `uiState.gpsFixQuality` stay the only published value; no new field, no new
diagnostics tag, no UI change. The marker (`MapCanvasScreen.kt:1518`) keeps drawing the last
position; that inconsistency is deliberate and belongs to §8.

## Risks / Trade-offs

- **[A >60 s standstill flips to NONE]** → Accepted and spec-bounded: 60 s of provider silence with
  the vehicle stationary is rare, and the consumers' fallbacks are all correct-but-quiet (hidden
  re-center button, no GPS on the speed widget). Verify on device with a stopped session; if it
  proves too eager, the single `FIX_AGE_LIMIT_MS` constant moves inside the allowed 30-60 s window.
- **[A device or emulator with location services off now reports NONE, and so does every test that
  does not model the source]** → Intended (that is the defect's headline case), and the full suite
  showed the cost: an existing case that installs a fix through `LocationService.setGpsFixForTest`
  and asserts the browse re-center button now sees NONE, because Robolectric starts with location off
  and the source read is truthful. Mitigation: such a test states the device reality explicitly
  (`locationService.setLocationSourceReadForTest { true }`), which is one line per class and keeps
  the production read unweakened.
- **[A tunnel still shows GOOD for up to 60 s]** → Bounded lie instead of an unbounded one; handed to
  `PositionSimulator` (§8) for a real ESTIMATED/LOST state.
- **[Marker and compass disagree while a fix is unavailable]** → Documented (proposal + here); the
  marker intentionally keeps the last position, the compass reports the truth about the fix.
- **[`debounce(2_000L)` delays the red fill by up to 2 s]** → Accepted: the debounce is what keeps
  boundary flicker away, and the spec allows a bounded delay. On-device verification asserts ≤3 s.
- **[`isLocationEnabled` shadow support in Robolectric]** → The check sits behind a
  `LocationService` seam the tests can drive directly, so no test depends on shadow behaviour.
- **[One more 1 Hz loop per live ViewModel]** → The tick body is a counter increment and the
  evaluation is a comparison; the loop ends with the ViewModel's scope. The stale-speed ticker already
  accepts the same shape, and gating it on "a quality consumer is visible" would add state for a cost
  that did not show up in the suite's runtime.
- **[A throw from the platform call could look like "location off"]** → `runCatching` defaults to
  available; a failed read can never fabricate a lost fix.

## Migration Plan

None needed: no persisted state, no data format, no API. Ships in a normal release (the phone path
only). Rollback is a revert of the ticker, the combined collector, `core/FixFreshness.kt` and the
`LocationService` accessor in one commit; no cleanup, no user-visible residue beyond the fill
returning to its previous (latched) behaviour.

## Open Questions

- The exact value of `FIX_AGE_LIMIT_MS` inside the spec's 30-60 s window (60 s proposed) — tunable by
  one constant after the on-device standstill check, without touching the specs.
- Whether the app should eventually show "signal lost" separately from "no fix" — deferred to
  `PositionSimulator` (`TODO.md` §8); it would change the specs, so it is explicitly not part of
  this change.
