# Group 4, slice A (tasks 4.1–4.3) — where it landed, and what its tallies were

Slice A is the phone's routing rows on the settings surface: the three maximum speeds, the three
avoidance switches and the phone's two vehicle anchors. Slice B (tasks 4.4/4.5, the car's own
tri-state anchor rows) is a separate tracker item and stays open here.

The boxes in this change's `tasks.md` are ticked rather than archived, because this change still
carries groups 3–8 and archives once when the last of them lands; this page is the record the ticks
point at.

## What the diff contains, per task

| task | change |
|---|---|
| 4.1 | `ui/settings/specs/NavigationTopic.kt` — the three speed rows as `SLIDER` rows (30–160, 5–40, 2–15 km/h, defaults 120/15/5, `max_speed_value` renders the value and both endpoints); the sheet's three sliders and their `MaxSpeedRange` table removed from `ui/map/LocationOptionsOverlay.kt`, with `threeMaximumSpeedSlidersAreShown`, `theRangesAreTheOnesTheSheetOffers` and the slider half of the sheet's fixtures |
| 4.2 | the same file — the three avoidance rows as `SWITCH` rows over `avoidTolls`/`avoidFerries`/`avoidUnpaved` under their own section; `theAvoidanceSwitchesAreShownAndReportAGesture` moved off the sheet |
| 4.3 | the same file — `routing-anchor` and `free-driving-anchor` as `SELECT` rows over `routingAnchorId`/`freeDrivingAnchorId`, 15 choices laid out in the car picker's 5 columns; `ui/settings/SettingsSurface.kt` renders a `SELECT` row's option page (`SettingsSurfaceState.Options`, `SETTINGS_OPTIONS_TAG`), one cell per value with the current one selected; `ui/settings/SettingsController.kt` gained `openOptions`; `anchorRowsShowCurrentPresets` and `anchorPickerOffersTheFiveByThreeGridWithCurrentMarked` moved off the sheet |

The ranges, defaults and the 15 preset labels are unchanged values: the labels come from
`VehicleAnchorPosition`'s own `label`, which is the source the car's `VehicleAnchorPickerScreen`
draws, so the two pickers cannot drift.

`guidelines/UI.md` §8d carries the rule that named the sheet as the only place these values live —
"the location-options-only settings are phone-only …, the maximum speed per vehicle … in the phone's
location-options sheet only". The diff re-states it: the overspeed delta and the long-press delay stay
in the sheet, the speeds, avoidances and anchors live in the surface's Navigation & Route topic, and
the car still reads without a control.

## The red case

`SettingsSurfaceTest#theNavigationTopicCarriesTheRoutingValues` is the case
`specs/settings-surface/spec.md` ("Navigation & Route carries routing values") names. It is red
without the change: on the base, `specs/NavigationTopic.kt` states
`navigationTopicRows: List<SettingSpec> = emptyList()`, so none of the eight rows exists.

The one-mutation run below puts the base's table back (the registry's row list for the topic is
emptied — one line, `SettingsRegistry.kt`, marked `REVERT-CHECK MUTATION`), runs the class, then
restores the line and re-runs it.

```
$ ./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache \
    --tests "com.naviveylin.ui.settings.SettingsSurfaceTest"
SettingsSurfaceTest > theAnchorLabelsMatchTheCarPicker FAILED
SettingsSurfaceTest > theAvoidanceSwitchesSitBesideTheSpeeds FAILED
SettingsSurfaceTest > theNavigationTopicCarriesTheRoutingValues FAILED
SettingsSurfaceTest > theAnchorPickerMarksTheConfiguredValue FAILED
SettingsSurfaceTest > theThreeSpeedSlidersKeepTheirRanges FAILED
SettingsSurfaceTest > aSliderChangePersistsAndSurvivesRestart FAILED
18 tests completed, 6 failed
```

The failure is the missing row, not a compile error or another premise:

```
java.lang.AssertionError: Action performScrollTo() failed.
Reason: Expected exactly '1' node but could not find any node that satisfies:
(TestTag = 'settings-slider-car-max-speed')
```

Every case that guards the topic's rows fails with the mutation and none of the other twelve does.
After the restore, `grep -c 'REVERT-CHECK MUTATION'` is 0 and the same class runs green (below).

## The tallies, quoted from the XML

Item 901's own record holds the files, copied at production time:

```
TEST-com.naviveylin.ui.settings.SettingsSurfaceTest.xml       tests=18 failures=0 errors=0  2026-11-19T04:46:15.020Z
TEST-com.naviveylin.ui.settings.SettingsRegistryTest.xml      tests=4  failures=0 errors=0  2026-11-19T04:45:57.018Z
TEST-com.naviveylin.ui.map.LocationOptionsOverlayComposeTest.xml  tests=18 failures=0 errors=0  2026-11-19T04:45:57.710Z
TEST-com.naviveylin.data.RoutingProfileBuilderTest.xml        tests=5  failures=0 errors=0  2026-11-19T04:45:56.908Z
red-mutation-mobile.xml                                       tests=18 failures=6 errors=0  2026-11-19T04:44:27.431Z
```

The four green runs are one forced `-PforceTests --no-build-cache` mobile-flavor run
(timestamped after the restore); the red one is the mutation run before it. The avoidance rows still
reach the shared builder — `RoutingProfileBuilderTest` is green beside the cases, and
`theAvoidanceSwitchesSitBesideTheSpeeds` reads the toggled value back through
`AppSettings.routingProfileFor(Vehicle.CAR)`.

## What the gate still owes

The full selection (`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`), the compose
sandbox rule and its self-test are the gate child's, not this page's. This tree is left unarchived at
the diff.
