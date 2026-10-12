# Proposal

## Why

Every routing calculation uses a vehicle profile whose speed is hard-coded in the native bridge: the car
profile caps at 120 km/h, the bicycle profile runs flat at 30 km/h and the pedestrian profile flat at
10 km/h (`CreateRoutingProfile`, `libosmscout-client-java/src/OSMScoutClient.cpp:5874`). Those numbers
decide both the chosen route and the displayed duration/ETA, and a user who drives slower, rides a
loaded bike, or walks with children cannot tell the app so — the ETA is systematically wrong for them.
The phone settings sheet is the place the user already configures routing-relevant driving behaviour
(overspeed delta, vehicle anchor), so the usual maximum speed per vehicle belongs there.

## What Changes

- Three new persisted phone settings: the usual maximum speed for **car**, **bicycle** and
  **pedestrian**, in km/h, as sliders in the location options bottom sheet.
  - Defaults: car **120**, bicycle **15**, pedestrian **5** km/h.
  - Ranges: car 30–160, bicycle 5–40, pedestrian 2–15 km/h, 1 km/h precision.
  - Changing a slider persists immediately and applies to the **next** route calculation (route panel,
    car navigation acquisition, reroute). No calculation is restarted by a slider move.
- **BEHAVIOUR CHANGE, intentional**: the bicycle and pedestrian defaults (15 / 5 km/h) differ from the
  library's current hard-coded 30 / 10 km/h. A fresh install and an existing install with a settings
  file written before this change both route bicycles and pedestrians at the new speeds, so bike/walk
  durations roughly double. This is the requested default, not a side effect — but it means the change
  is **not** neutral for existing users. Car keeps 120 km/h and is therefore unchanged out of the box.
- The speeds reach routing as new fields on the `RoutingProfile` bridge type, read by the JNI layer and
  passed to the profile parametrisation. Vehicle semantics: for the car the value is a **ceiling**
  (`vehicleMaxSpeed`; the per-road-type speed table stays as it is, so a value above the fastest road
  type has no effect), for bicycle and pedestrian it is the **flat speed** the library already uses.
- A missing, unreadable or zero speed falls back to the built-in profile, so an old caller keeps
  today's behaviour.
- Scope: **general** — one shared value triple in `AppSettings`, edited on the phone only, consumed by
  phone routing and by car routing (Android Auto / AAOS acquire their route through the same
  `NavigationEngine`, always with `Vehicle.CAR`). No car UI, no `AutoSettingsMapping` change, no
  per-surface split; this is a deliberate deviation from the anchor settings' per-surface pattern.

## Capabilities

### New Capabilities

- `routing-max-speed`: owns the contract between the persisted per-vehicle maximum speed and the
  routing profile — the three fields and their defaults, that every calculation path carries the
  selected vehicle's value into `RoutingProfile`, the ceiling-versus-flat semantics per vehicle, and
  the fallback when the value is missing or unusable.

### Modified Capabilities

- `location-options-ui`: adds the vehicle maximum speed controls to the options bottom sheet (three
  sliders with their ranges, defaults and persistence), next to the existing overspeed delta control.
- `route-panel-ui`: the `Vehicle selector` requirement gains the consequence that the selected
  vehicle's configured maximum speed is what the calculation uses.

The native `routing-profile` capability is specified in the **submodule's own** OpenSpec root
(`app/src/main/cpp/libosmscout/openspec/specs/routing-profile/spec.md`), which this repository does not
own; its delta is a task of this change, not an artifact of it.

## Impact

Affected modules and files:

- `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — three additive `AppSettings` fields
  (`carMaxSpeedKmh`, `bicycleMaxSpeedKmh`, `footMaxSpeedKmh`), defaults 120/15/5.
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — new sheet section with three
  sliders; `MapCanvasViewModel.kt` — setter callbacks, state, `SettingsStorage` write;
  `MapCanvasScreen.kt` — wire the callbacks.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` (line 459) and
  `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` (lines 489 and 575) — the three
  places that construct a `RoutingProfile`; both classes gain a `SettingsStorage` dependency.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-de/strings.xml` — new labels
  (German translation is enforced by `GermanTranslationCompletenessTest`).
- Tests: `app/src/test/java/com/naviveylin/data/AppSettingsTest.kt` (defaults/round-trip),
  `SettingsStorageTest.kt`, new route/navigation profile-construction cases, a Compose case for the
  sliders, and a Java-side `RoutingProfile` field test in `osmscout-client-java`.

Native boundary — **submodule patch (minimal, upstreamable), no local override**:

- `libosmscout-client-java/java/com/framstag/libosmscout/client/RoutingProfile.java` — new
  `maxSpeedKmh` field (`0` = use the built-in profile) plus constructors; the existing constructors stay
  so no call site breaks (bridge Java types are positional-argument only, `guidelines/Design.md` §5).
- `libosmscout-client-java/src/OSMScoutClient.cpp` — read the field in the profile parsing block
  (line 5998), pass it through `CreateRoutingProfile` (line 5874) into
  `ParametrizeForCar/Bicycle/Foot`.
- `RoutingProfile.java` is **not** one of the five Android-port override files, and a maximum speed is
  not an Android-specific deviation, so per `guidelines/Design.md` §5 it belongs in the submodule, not
  in `:osmscout-client-java`. The change therefore carries a submodule commit, a gitlink bump in this
  repository, and a delta to the submodule's `routing-profile` spec; the submodule branch rules
  (`naviveylin-local`, remote check before push, one session owns it) apply.
- Native build: `guidelines/Build.md` §12 (all target ABIs must compile).

Guidelines touched: `guidelines/Design.md` §5 (native boundary, submodule discipline, positional bridge
constructors §40.23), §9 (persistence), `guidelines/UI.md` §7/§8 (options sheet), and the routing-speed
parameter table in `guidelines/MapRendering.md` §19 if it names the defaults.

Additive or breaking: **additive** at the settings-file and API level (new fields with defaults decode
from and to every existing file; no signature is removed), **behaviourally changing** for bicycle and
pedestrian routing as stated above.

Rollback path: set the three sliders back to 120/30/10, or revert the fields to the library values —
the JNI fallback (`0` → built-in profile) is the same path a reverted build takes, so no migration and
no settings-file repair is needed.
