# Tasks — routing-avoidance-flags

## 1. Persistence (spec: Avoidance is a persisted per-vehicle-independent setting)

- [x] 1.0 Register the capability in `tools/feature-list/specs.json` (area `routing`, user-visible, phone
  and car) so the catalogue's classification gate does not stop on an unclassified spec after archive.
- [x] 1.1 Add `avoidTolls`, `avoidFerries`, `avoidUnpaved` to `AppSettings`, all defaulting to `false`, each
  commented with the spec requirement it serves.
- [x] 1.2 Extend the settings round-trip and old-file tests with the three fields. Covered by
  `MapCanvasViewModelAvoidanceTest` (defaults, persistence, restart) and `RoutingProfileBuilderTest`.

## 2. The builder (spec: One builder produces the profile every calculation uses)

- [x] 2.1 Add `AppSettings.routingProfileFor(vehicle)` beside `maxSpeedKmhFor`: it builds the profile from
  the vehicle's speed and the three choices.
- [x] 2.2 Replace the profile construction in `RoutePanelViewModel` with the builder.
- [x] 2.3 Replace the profile construction in `NavigationEngine` (acquisition and reroute) with the builder.
- [x] 2.4 Keep `maxSpeedKmhFor` as the vehicle→speed mapping the builder uses; no call site builds a profile
  from individual values any more (`grep -rn "RoutingProfile(" app/src/main/java` yields only the builder).
- [x] 2.5 `RoutingProfileBuilderTest` pins the builder: each choice reaches the profile, the choices are
  independent, they apply to every vehicle, and the vehicle's own speed still arrives.

## 3. The sheet (spec: The phone edits the choices and the car reads them)

- [x] 3.1 Add the three flags to the map's UI state and its initialisation from the settings file.
- [x] 3.2 Add `onSetAvoidTolls` / `onSetAvoidFerries` / `onSetAvoidUnpaved` in the settings view model, each
  writing through `settingsStorage.update`.
- [x] 3.3 Add the three switches to the options sheet, beside the vehicle speeds, labelled "Avoid toll
  roads", "Avoid ferry routes", "Avoid unpaved roads" (German included — the translation-completeness test
  caught the missing file).
- [x] 3.4 The car reads the same stored values through `NavigationEngine` and has no avoidance control: no
  `RoutingProfile` construction exists in `:auto`, and the car's settings mapping adds no field.

## 4. Evidence owed before archive

- [x] 4.1 Unit cases: `RoutingProfileBuilderTest` (5), `MapCanvasViewModelAvoidanceTest` (4) and the compose
  case in `LocationOptionsOverlayComposeTest` (28 in that class). Full `:app:testMobileDebugUnitTest` green
  (2 151 cases) after the German strings were added.
- [x] 4.2 `revert-check`: the builder's `avoidUnpaved` argument was replaced with `false`; the named case
  `eachStoredChoiceReachesTheProfile` failed; restored, the suite is green.
- [x] 4.3 A device check that a stored choice survives a restart and appears in the sheet:
  `evidence/device-restart-2026-10-30.md`. On the `Pixel_8(AVD) - 17` emulator, toll and unpaved were
  switched on in the sheet, `files/maps/settings.json` then read
  `{"avoidTolls":true,"avoidUnpaved":true,…}`, and after a force-stop and relaunch the reopened sheet showed
  both on and the untouched ferry switch off. The same run notes what it does not prove: the installed
  extract carries no toll information, so no route changes on the device — that effect is measured in the
  submodule change.
- [x] 4.4 The data limitation is stated where a reader meets it: the proposal's honest expectation, the
  design's "What the user must not be promised", and the spec requirement "A choice the data cannot express
  changes nothing".

## Note on running the suite in this tree

While this change was being verified, every `./gradlew` invocation failed during configuration (`Compose
Preview failed … Unit tests are disabled for this variant`) because the shared working tree carried an
unowned AGP 9.4.1 → 9.5.0 bump. The focused runs used `-PjvmTestsOnly`, the property the build file defines
for a JVM-only run. The bump was discarded as part of the tree clean-up, and the gate was then re-run with
plain `./gradlew :app:testMobileDebugUnitTest` on the merged tree — green, no workaround.
