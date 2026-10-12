# Design

## Context

See `proposal.md` — Why. The constraints that shape the approach:

- The routing profile is built in the native bridge, once per call:
  `CreateRoutingProfile(tc, vehicle, ...)` at `libosmscout-client-java/src/OSMScoutClient.cpp:5874`
  calls `ParametrizeForCar(*tc, speedMap, 120.0)`, `ParametrizeForBicycle(*tc, 30.0)` or
  `ParametrizeForFoot(*tc, 10.0)`. There is no route from Kotlin to those numbers today.
- `libosmscout` interprets the parameter differently per vehicle (`libosmscout/src/osmscout/routing/RoutingProfile.cpp:107-160`):
  the car path stores it as `vehicleMaxSpeed`, a ceiling applied on top of the per-road-type speed table;
  the bicycle and foot paths store it as the speed of **every** routable type as well as the ceiling.
- The Java side of the bridge is `libosmscout-client-java/java/…/RoutingProfile.java`, which lives in the
  **submodule** and is not one of the five Android-port files that `:osmscout-client-java` overrides
  (`guidelines/Design.md` §5, "The bridge's two sides"). `Vehicle` is a sibling enum in the same directory.
- Bridge Java types are positional-argument-only from Kotlin, and their constructors are read at
  `guidelines/Design.md` §5 (the §40.23 rule).
- Three places build a profile: `RoutePanelViewModel.kt:459`, `NavigationEngine.kt:489` (reroute) and
  `NavigationEngine.kt:575 → acquire(..., Vehicle.CAR)` (car destination). Neither class has
  `SettingsStorage` today.
- `SettingsStorage` is a `@Singleton` whose `update {}` is a serialized read-modify-write
  (`guidelines/Design.md` §9, spec `settings-persistence`).

## Goals / Non-Goals

**Goals:**

- One persisted value per vehicle, edited on the phone, reaching every calculation path through the
  existing `RoutingProfile` type — no new JNI method, no second settings store.
- Behaviour with no value stored, or with an old settings file, is defined and harmless.

**Non-Goals:**

- Scaling the car's per-road-type speed table; the value is a ceiling (decision D1).
- A car-side editor, a per-surface split of the values, or an `AutoSettingsMapping` change (decision D2).
- Changing the vehicle selection, the avoid-flags, or `route_ferry` handling in the native profile.
- Automatic reroute when a slider moves.

## Decisions

### D1 — The car value is a ceiling, not a scale factor

`vehicleMaxSpeed` is set to the configured value and the per-road-type table is left untouched; for
bicycle and pedestrian the value is passed to the existing parametrisation, which already treats it as
the flat speed.

*Alternative — scale the type table proportionally* (`speed * value / 120`): rejected. It makes a value
above 120 raise motorway speed past the table, invents a mapping the library does not model, and changes
twice as much routing behaviour for the same UI. Consequence to accept and to state in the UI copy: a car
value above the fastest road type (110 km/h) changes nothing.

### D2 — One shared value triple, no per-surface split

`AppSettings` gains `carMaxSpeedKmh = 120`, `bicycleMaxSpeedKmh = 15`, `footMaxSpeedKmh = 5`. The car
reads the same values and exposes no editor.

*Alternative — per-surface fields with an inheritance fallback*, as the vehicle anchors do
(`autoRoutingAnchorId ?: routingAnchorId`): rejected. The car has no vehicle picker and no speed editor,
so the extra fields would be unreachable state, two more `AutoSettingsMapping` directions and a second
divergence source, for no behaviour the user can reach.

### D3 — The consumers read the store, they do not mirror the value

`RoutePanelViewModel` and `NavigationEngine` take a `SettingsStorage` dependency and read the value inside
the coroutine that already wraps the native call, immediately before building the profile. Writing stays
where the other phone settings write: the sheet callbacks on `MapCanvasViewModel`.

*Alternative — mirror the three values into `MapCanvasUiState` (and `NavigationState`) and pass them
through the call sites*: rejected. The sheet writes and the routing read would then be two state paths
that can disagree after a write fails (spec `settings-persistence` keeps the in-memory value on a failed
write); the store is the single source of truth and a read per calculation is one file read on a
background dispatcher.

*Alternative — a small `RoutingSpeeds` provider interface in `:core`*: rejected for now as an abstraction
with one implementation; it can be extracted when a second consumer appears.

**Refinement found while applying it (the vehicle→value resolution, and one added accessor).** Two details
differ from the paragraph above, both measured rather than preferred:

- The per-vehicle mapping is one function, `AppSettings.maxSpeedKmhFor` (`app/.../data/VehicleMaxSpeed.kt`),
  not a `when` at each of the three call sites. A per-site `when` is three places to get the same mapping
  wrong; the function is also what the per-vehicle revert-check mutates.
- The read uses a new `SettingsStorage.loadOnCallerDispatcher()` instead of `load()`. `load()` hops to
  `Dispatchers.IO`; inside the engine's and the panel's calculation coroutine that hop is a suspension point
  the unit tests' virtual scheduler never advances, and 23 engine/panel cases failed with "State condition not
  met after draining the test scheduler" before the hop was dropped. The accessor keeps `load()`'s lock (so
  "never read a half-written file" is unchanged) and drops only the dispatcher hop; both callers already run on
  a background dispatcher (the engine's compute dispatcher, the panel's `defaultDispatcher`). Callers on the
  main thread still use `load()`.

### D4 — Native change as a submodule patch, not a bridge override

The field and the JNI read land in the submodule (`RoutingProfile.java`, `OSMScoutClient.cpp`) and the app
repository bumps the gitlink.

*Alternative — add `RoutingProfile.java` to the five Android-port overrides in `:osmscout-client-java`*:
rejected. The override set exists for Android-specific deviations (no `java.net.http` desugaring, debug
library suffix); a maximum speed is a general client feature and belongs upstream. `guidelines/Design.md`
§5 states the rule and the submodule's `routing-profile` spec is the contract to extend.

### D5 — `maxSpeedKmh`, with `0` as the "built-in profile" sentinel

`RoutingProfile` gains `public final double maxSpeedKmh` and a `(Vehicle, double)` constructor; the three
existing constructors stay and set it to `0`. The JNI read uses `GetFieldID`, checks for `null` (clearing
a pending exception), and passes `0` for a missing field, a non-positive value or anything above 300.
`CreateRoutingProfile` takes the value and only calls `ParametrizeFor*` with it when it is usable,
otherwise with the current literals.

*Alternative — a separate JNI entry point or a new `RoutingSettings` type*: rejected, it doubles the
surface for one number.

*Alternative — default in Java to 120/30/10 and drop the sentinel*: rejected, it would make an old caller
(un-updated test, stale bytecode) silently route at a value the caller never asked for, and the fallback
would no longer be observable in one place.

*Alternative — change the meaning of an existing constructor*: rejected by the positional-argument rule —
every call site has to be updated in the same change and stale test bytecode fails as
`NoSuchMethodError` rather than a compile error.

### D6 — Sliders, matching the overspeed control

Three `Slider`s with `valueRange` 30f..160f / 5f..40f / 2f..15f and 1 km/h steps (integer values, no
coarser steps), in a new labelled sheet section.

*Alternative — numeric text fields*: rejected, they need validation, an IME and error states for a
bounded integer; the sheet already establishes sliders as the control for a numeric preference.

*Alternative — dropdowns in coarse steps*: rejected, the user's value is a driving preference, not a
category.

### D7 — Sheet section, rendered in every map state

A `Routentempo`/`Routing speed` section with the three sliders, composed next to the overspeed control and
outside the mode-conditional branches, so it is reachable in BROWSE, FREE_DRIVE and NAVIGATION like the
anchor rows.

*Alternative — only in NAVIGATION mode*: rejected, the value is set before a trip at least as often as
during one, and the spec makes the sheet a settings surface, not a driving-only HUD.

## Risks / Trade-offs

- **Bicycle/pedestrian routes and durations change on upgrade** (30 → 15, 10 → 5 km/h) → intentional and
  on the record in `proposal.md`; the fallback sentinel keeps the change reversible without migration.
- **A car value above 110 km/h looks broken to the user** → the sheet labels the value as the vehicle's
  usual maximum and the native log line reports the value actually used, so the on-device evidence shows
  which ceiling was applied.
- **A slider drag writes many settings transactions** → accepted; the overspeed slider does the same and
  `SettingsStorage.update` is serialized and mutex-guarded.
- **A settings read per calculation on the compute path** → a small file read on a background dispatcher,
  off the main thread; no new hot path.
- **The fallback path is easy to leave untested** → the JNI fallback is proven by a unit case that builds
  a profile without a value, plus a revert-check that mutates the sentinel branch.
- **Submodule drift** → the change carries the submodule commit, the gitlink bump and the submodule spec
  delta; the branch rules of `guidelines/Design.md` §5 (remote check before push, one owner) apply.

## Verification

- Java/unit: `RoutingProfileFieldsTest` (new field, sentinel default on the old constructors),
  `AppSettingsTest` (defaults 120/15/5, round-trip), `SettingsStorageTest` (old file decodes to defaults),
  profile-construction cases in `RoutePanelViewModel*Test` and `NavigationEngineRerouteTest` (each path
  carries its own vehicle's value), a Compose case for the three sliders
  (`LocationOptionsOverlayTest`-style) and `GermanTranslationCompletenessTest` for the new labels.
- Native: `guidelines/Build.md` §12 — all target ABIs compile; the created-profile log line carries the
  applied speed (coordinate-free).
- On device: `adb logcat -s NaviVeylin` shows the applied speed for the chosen vehicle after a
  calculation, and once per configured value (120/15/5 and a changed value) — the measurement that
  settles whether the value reached the profile.
- UI geometry: the three sliders and their values are asserted by the Compose test, so the sheet layout
  claim does not rest on a reviewer's eye (`compose-geometry` skill); no pixel verdict is claimed for a
  control whose property is presence and value.
- Gate: one `--rerun-tasks` both-flavor run before the change is complete
  (`guidelines/Build.md` §2/§3); no exclusion.

## Migration Plan

1. Submodule: field + JNI + submodule spec, commit on `naviveylin-local`, push, bump the gitlink.
2. App: settings fields, sheet section, both consumers, strings (both locales).
3. Tests as listed; forced green gate.
4. Rollback: revert the gitlink and the app commit. Settings files keep the three keys; the reverted build
   ignores them (unknown-key tolerance, spec `settings-persistence`) and returns to 120/30/10.

## Open Questions

None.
