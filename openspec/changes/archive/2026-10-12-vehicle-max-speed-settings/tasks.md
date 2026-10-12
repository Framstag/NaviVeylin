# Tasks

## 1. Native bridge — submodule patch (spec `routing-profile`, submodule root)

- [x] 1.1 Read `libosmscout-client-java/java/com/framstag/libosmscout/client/RoutingProfile.java` in full before editing
      (`guidelines/Design.md` §5, the positional-argument rule §40.23): add `public final double maxSpeedKmh`
      with KDoc stating the unit (km/h) and that `0` means "use the library's built-in profile", add the
      `(Vehicle, double)` constructor, and set `0` in the three existing constructors. Verify the file still
      reads as one coherent constructor chain.
- [x] 1.2 In `libosmscout-client-java/src/OSMScoutClient.cpp` read the new field in the profile-parsing block
      (line 5998): `GetFieldID(profileCls, "maxSpeedKmh", "D")`, `env->ExceptionClear()` on a missing field,
      and a value of `0` when the field is absent, non-finite, below 1 or above 300. Pass the value through
      `CreateRoutingProfile` (line 5874) into `ParametrizeForCar` / `ParametrizeForBicycle` /
      `ParametrizeForFoot`, falling back to the current literals (120 / 30 / 10) when the value is unusable.
      Extend the existing `created <vehicle> routing profile` log line with the applied speed (numbers only,
      no coordinates — `guidelines/Logging.md`).
- [x] 1.3 Assert the passed value where the bridge is observable: in `osmscout-client-java/src/test/java/…`
      add a case that constructs `RoutingProfile(Vehicle.BICYCLE, 20.0)` and reads `maxSpeedKmh` back, and a
      case that the three legacy constructors report `0` — covers `routing-max-speed` "A per-vehicle maximum
      speed is a persisted setting" (bridge side) and "A missing or unusable maximum speed falls back to the
      built-in profile" at the value level.
- [x] 1.4 Update the **submodule's** spec `openspec/specs/routing-profile/spec.md` there: the JNI
      requirement gains the maximum speed field, its per-vehicle semantics and the unusable-value fallback,
      with one scenario per behaviour. Run the submodule's own `openspec validate`.
- [x] 1.5 Commit the submodule on `naviveylin-local` and push — `git ls-remote origin naviveylin-local`
      immediately before the push (`guidelines/Design.md` §5, one session owns the submodule and its pushes) —
      then bump the gitlink in this repository in the same change.
- [x] 1.6 Compile the native build for every target ABI (`guidelines/Build.md` §12) so arm64-v8a,
      armeabi-v7a and x86_64 all build with the new JNI field read.

## 2. Settings persistence (spec `routing-max-speed`)

- [x] 2.1 Add to `AppSettings` (`app/src/main/java/com/naviveylin/data/SettingsStorage.kt`):
      `carMaxSpeedKmh: Int = 120`, `bicycleMaxSpeedKmh: Int = 15`, `footMaxSpeedKmh: Int = 5`, each with the
      KDoc the neighbouring fields carry (default, unit, which surfaces read it, and why an older settings
      file decodes to the default).
- [x] 2.2 `AppSettingsTest.kt`: `defaultsAreCar120Bicycle15Foot5`,
      `settingsWithoutMaxSpeedFieldsDecodeToDefaults`,
      `maxSpeedsRoundTripThroughTheSettingsFile` — covers `routing-max-speed` "A per-vehicle maximum speed is
      a persisted setting", all three scenarios.
- [x] 2.3 `SettingsStorageTest.kt`: `unknownAndMissingMaximumSpeedKeysDoNotBreakALoadOrWrite` — covers the
      same requirement's old-file scenario through the file path, and `settings-persistence`
      ("unknown keys do not break a write").

## 3. Phone sheet (spec `location-options-ui`)

- [x] 3.1 Add the section to `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt`:
      three labelled rows with a `Slider` each (`valueRange` 30f..160f, 5f..40f, 2f..15f, integer values,
      no coarser steps), a current value in km/h, stable `testTag`s, rendered outside the mode-conditional
      branches so it appears in BROWSE, FREE_DRIVE and NAVIGATION (decisions D6, D7). Add a Compose preview
      for the new section.
- [x] 3.2 Wire it: `MapCanvasViewModel.kt` gains the three setters (UI state + `settingsStorage.update {}` on
      the existing settings-write path) and `MapCanvasScreen.kt` passes them into the sheet — mirroring the
      overspeed control.
- [x] 3.3 Add the labels and value formats to `app/src/main/res/values/strings.xml` (English base) and
      `app/src/main/res/values-de/strings.xml` (German), then run `GermanTranslationCompletenessTest`.
- [x] 3.4 Compose cases in `app/src/test/java/com/naviveylin/ui/map/LocationOptionsOverlayTest.kt` (new or
      extended; the file is `LocationOptionsOverlayComposeTest.kt`): `threeMaximumSpeedSlidersAreShown` (three sliders + values),
      `slidersShowTheStoredValues`, `slidersAreReachableInEveryMapState` (one case per mode),
      `eachSliderCoversEveryWholeValueOfItsRange` (assert the offered value set per slider) — covers the
      "Vehicle maximum speed sliders" requirement's four scenarios.
- [x] 3.5 Persistence cases (in the new `MapCanvasViewModelVehicleSpeedTest.kt`): `movingABicycleSliderPersistsTheValue` (asserts the stored value and the
      reopened label) and `aSliderChangeSurvivesARestart` — covers "A slider change persists immediately".
- [x] 3.6 Revert-check for the slider bounds (a new bound this change introduces): TWO mutations, because
      the bounds live in one table (`MaxSpeedRange`) that both the sheet and the bounds case read, so a
      table mutation moves the expectation with it and cannot fail the wiring case.
      (a) wiring — `valueRange = range.minKmh.toFloat()..(range.maxKmh - 1).toFloat()`:
      `eachSliderCoversEveryWholeValueOfItsRange` FAILED, tally tests=23 failures=1, 2026-10-12T07:09:35+02:00.
      (b) table — `MaxSpeedRange.BICYCLE = MaxSpeedRange(15, 40)`: `theRangesAreTheOnesTheSheetOffers` FAILED
      (the case added for exactly this; it writes the spec's bounds out instead of reading the table),
      tally tests=23 failures=1, 2026-10-12T07:10:21+02:00.
      Both restored, then forced green: 23 tests, 0 failures, `-PforceTests --no-build-cache --rerun-tasks`,
      2026-10-12T07:13:33+02:00.

## 4. Routing consumers (specs `routing-max-speed`, `route-panel-ui`)

- [x] 4.1 `RoutePanelViewModel.kt`: injected `SettingsStorage` and read the stored value for `s.vehicle`
      inside the existing calculation coroutine before building the profile; the panel constructs
      `RoutingProfile(vehicle, maxSpeedKmh)`. Case `routePanelBicycleCalculationCarriesTheStoredBicycleSpeed`
      (asserts the profile handed to the client, in the new `RoutePanelViewModelVehicleSpeedTest.kt`) —
      covers `routing-max-speed` "Every calculation path carries the vehicle's maximum speed" (route panel)
      and `route-panel-ui` "The selected vehicle's stored maximum speed reaches the profile".

      Deviation, recorded: the vehicle→value resolution is one function for every path
      (`AppSettings.maxSpeedKmhFor` in the new `app/src/main/java/com/naviveylin/data/VehicleMaxSpeed.kt`),
      not a `when` per call site — its pure cases are `app/src/test/java/com/naviveylin/data/VehicleMaxSpeedTest.kt`.

      The read uses `SettingsStorage.loadOnCallerDispatcher()` (added for this change, KDoc there): a plain
      `load()` hops to `Dispatchers.IO`, which left every route waiting for a dispatcher the tests' virtual
      scheduler never advances — 23 engine/panel cases failed with "State condition not met after draining
      the test scheduler" until the hop was dropped. The lock ("never read a half-written file") is unchanged.
- [x] 4.2 `NavigationEngine.kt`: inject `SettingsStorage`; the acquisition path (line 489, reached from
      `navigateTo`/`acquire`) and the reroute path use the calculation vehicle's stored value. Cases
      `navigationEngineAcquisitionCarriesTheStoredCarSpeed` and
      `navigationEngineRerouteUsesTheStoredPedestrianSpeed` — covers the acquisition and reroute scenarios of
      the same requirement.
- [x] 4.3 Per-vehicle separation case `eachVehicleReadsItsOwnStoredValue` with the values 90 / 20 / 4 —
      covers `route-panel-ui` "Each vehicle reads its own stored value" and `routing-max-speed` "The setting
      is shared by the phone and the car" (phone side).
- [x] 4.4 Revert-check for the per-vehicle mapping: mutated `maxSpeedKmhFor` so every vehicle reads
      `carMaxSpeedKmh` — `eachVehicleReadsItsOwnStoredValue` FAILED in both `VehicleMaxSpeedTest`
      (tests=2 failures=2) and `RoutePanelViewModelVehicleSpeedTest` (tests=3 failures=3),
      2026-10-12T07:29:35+02:00. Restored, then forced green: 2 and 3 tests, 0 failures,
      `-PforceTests --no-build-cache --rerun-tasks`, 2026-10-12T07:31:55+02:00.
- [x] 4.5 Case `movingASpeedSliderDoesNotAlterARunningRoute` (a change while a session is running leaves the
      route and its duration alone; a change before a calculation is used) — covers `routing-max-speed`
      "A speed change does not restart a calculation", both scenarios.

## 5. Verification

- [x] 5.1 Run the changed suites through their `--tests` filters while iterating
      (`run-tests` skill), then the full both-flavor gate once with `--rerun-tasks`
      (`guidelines/Build.md` §2/§3).
      DONE: the gate ran as the reference command of `guidelines/Build.md` §3 rather than the two `:app`
      tasks named above — `./gradlew test -PforceTests --no-build-cache` (it covers every module and both
      `:app` flavors, and `-PforceTests` is what the reference uses to force, so no `--rerun-tasks` was
      needed). `BUILD SUCCESSFUL in 8m`, 189 actionable tasks, 2026-10-12. No exclusion.
- [x] 5.2 Confirm every pre-existing case in the four modules still passes and that the build reports no
      warnings (`guidelines/Build.md` §4).
      DONE: the gate is green over every module and both flavors. Warnings —
      `:app:compileMobileDebugKotlin :app:compileMobileDebugUnitTestKotlin --no-build-cache --rerun-tasks`
      reports 28 `w:` lines and **none of them in a file this change touches**: all are the pre-existing
      `ExperimentalCoroutinesApi` opt-in debt plus `StartMapResolverTest.kt:118` (the `String?`/`String`
      mismatch), which `TODO.md` §1133 already tracks as counted, project-wide debt. No new warning.
- [x] 5.3 On-device measurement (phone AVD `emulator-5554`, sdk_gphone64_x86_64, 1080x2400/420,
      routable region `europe-germany-bremen`, fix Bremen 53.0793/8.8017, 2026-10-12). App installed with
      `adb install -r -t` (signature matched, the map data survived) and driven by the `device-check`
      recipe (dump → tap by node text/content-desc). (`device-check` skill, `adb logcat -s NaviVeylin`): after a calculation with
      each vehicle, read the applied speed from the profile log line — record the values for the defaults
      (120 car / 15 bicycle / 5 foot) and for one changed value (e.g. car 90) with the log excerpt. This is
      the measurement that settles that the value reached the native profile; `routing-max-speed`'s
      "Vehicle semantics of the maximum speed" scenarios (car ceiling below a road type's speed, car value
      above the fastest type, flat bicycle speed) have **no unit case in this repository's gate** — the
      submodule ships no `RoutingProfile` unit test (`Tests/src/` holds CLI programs needing map databases) —
      so record that limitation explicitly and cite the library's parametrisation plus this measurement
      instead of implying a green case proves them.

      MEASURED (`adb logcat -d -s NaviVeylin`, one clean stream per run), the applied speed from the
      profile log line:

      | vehicle | value | log line |
      |---|---|---|
      | car | default | `created car routing profile (max speed 120.00000 km/h)` |
      | bicycle | default | `created bicycle routing profile (max speed 15.00000 km/h)` |
      | pedestrian | default | `created pedestrian routing profile (max speed 5.00000 km/h)` |
      | car | **changed to 90 via the sheet's slider** | `created car routing profile (max speed 90.00000 km/h)` |

      The changed-value run is end-to-end: the slider (Standortoptionen → Routentempo → Auto) moved the
      readout to `90 km/h`, the device's own `files/maps/settings.json` then held `"carMaxSpeedKmh":90`,
      and after an app restart the calculation logged 90. The bicycle and pedestrian lines are the app's
      shipped defaults (15 / 5), **not** the library's built-in 30 / 10 — a bridge that ignored the field
      would have logged those instead. Fault-like lines (`fatal|exception|crash|assert`): **0** in every run.

      Sheet geometry as measured on the device (dump, same session): `Routentempo` [63,1284][283,1337];
      row `Auto` + `90 km/h` [63,1400][1017,1463]; `Fahrrad` + `15 km/h` [63,1673][1017,1736];
      `Fußgänger` + `5 km/h` [63,1946][1017,2009]; the car slider as `android.widget.SeekBar`
      [63,1510][1017,1626]. Screenshot: `.pi/logs/vehicle-max-speed/sheet-routing-speed.png`.

      LIMITATION, recorded rather than papered over: this proves the stored value **reaches** the native
      profile; it does **not** prove the three semantics scenarios. The measured city route
      (`Bürgermeister-Smidt-Straße`, `highway_secondary`) has every road type below the changed 90 km/h
      ceiling, so the ceiling never bound — the log line names the applied value, not a resulting duration,
      and no assertion here quantifies a cap or the flat bicycle speed. Those rest on the library's
      `ParametrizeFor*` behaviour and the submodule's `routing-profile` spec, as stated above.
- [x] 5.4 Car surface (device-gated): record the host-substitute note for `location-options-ui` "The car
      exposes no maximum speed control" and `routing-max-speed`'s car scenario — car routing runs in the
      shared, host-free `NavigationEngine` (case 4.2), and the car preferences surface is the host template
      inspected by `PreferencesScreenMapper`; state in the report whether a head unit or emulator run was
      available, rather than claiming a car-screen case.
      DONE, with the limitation named instead of a car-screen case claimed: the device used was a **phone AVD**
      (`emulator-5554`); **no head unit or Android Auto host run happened** in this session, so no car screen
      was exercised. Both car-facing claims stand on host substitutes — car routing is the shared, host-free `NavigationEngine` path proven by case 4.2
      (`navigationEngineAcquisitionCarriesTheStoredCarSpeed`), and "no car-side editor" is a template-space
      fact (`PreferencesScreenMapper` builds the car preference list and this change adds no row to it).
- [x] 5.5 Record the slider section's geometry from the Compose case (testTags and offered values) as the
      measurement for the sheet-layout claim; no pixel verdict is claimed, since the property is presence and
      value, not appearance (`compose-geometry` skill).
      DONE: `LocationOptionsOverlayComposeTest` (23 tests, 0 failures) asserts presence by the tags
      `carMaxSpeedSlider` / `bicycleMaxSpeedSlider` / `footMaxSpeedSlider`, the shown values (120 / 15 / 5 and
      90 / 20 / 4), reachability in BROWSE, FREE_DRIVE and NAVIGATION, and — by clicking each track's ends and
      dragging across it — that each slider reports every whole km/h of its range. No pixel verdict is claimed:
      the property is presence and value, not appearance.

## 6. Documentation

- [x] 6.1 `guidelines/UI.md` §7/§8 (the "Phone-only …" list next to the overspeed line, ~line 841): add the
      maximum speed sliders as a phone-only sheet control with their ranges and defaults, and state that the
      car consumes the same stored values without an editor.
- [x] 6.2 Check `guidelines/Design.md` §9 and `AGENTS.md` for a settings-field inventory that would now be
      incomplete; if either enumerates the sheet's controls, update it in this change, otherwise record the
      check as not applicable.
      Checked, **not applicable**: neither `Design.md` §9 (persistence mechanics, no field list) nor `AGENTS.md`
      enumerates `AppSettings` fields or the sheet's controls. The one place that listed the sheet's phone-only
      controls is `UI.md` §7/§8, which 6.1 updates.
- [x] 6.3 Record in `TODO.md` any problem this change detects but does not own (per the apply guidance), and
      delete the entry again if the change closes it.
      Nothing to add: the only observation is the pre-existing warning debt, which `TODO.md` §1133 already
      counts and names (`StartMapResolverTest.kt:118`, the `ExperimentalCoroutinesApi` opt-ins), so a second
      entry would duplicate it.
