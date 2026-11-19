# Group 5 (tasks 5.1–5.4) — where it landed, and what its tallies were

Group 5 was worked by the tracker item #902 ("The phone cannot edit the car's voice guidance;
announcements live in the gear sheet") on the branch `fix/issue-902-the-phone-cannot-edit-the-car-s-voice-gu`
from base `20d4bbaf`. The boxes are ticked in `tasks.md`; the change still carries groups 6–8 and archives
once when the last of them lands, so this page is the record the group-5 ticks point at.

## What the diff contains, per task

| task | files |
|---|---|
| 5.1 | `app/src/main/java/com/naviveylin/ui/settings/specs/HintsTopic.kt` — the phone's voice switch (`voice-guidance-phone`) and the shared GPS-announcement switch (`gps-announcements`); `SettingsSurface.kt` renders them from the registry; `SettingsSurfaceVoiceSwitchesTest` |
| 5.2 | `HintsTopic.kt` — the car's switch (`voice-guidance-car`, `SettingScope.CAR`, `settings_scope_car`) writes only `autoVoiceGuidanceEnabled`; `SettingsSurfaceCarVoiceRowTest`; `NavigationEngineVoiceGuidanceTest#theCarSessionReadsAValueSetOnThePhone` |
| 5.3 | `HintsTopic.kt` — `lane-hints` and the overspeed slider (`Slider(min = 0, max = 30, step = 1)`); `SettingSpec.valueFormatRes` added so the unit is a string resource; `SliderControl` and labelled endpoints in `SettingsSurface.kt`; `SettingsSurfaceTest#theOverspeedSliderOffersOneKilometreSteps`; the two duplicated sheet cases deleted from `LocationOptionsOverlayComposeTest` |
| 5.4 | `SettingSpec.helpLongRes` rendered as an `IconButton` opening a `ModalBottomSheet` (design D7) in `SettingsSurface.kt`; `SettingsSurfaceTest#aLongHelpOpensWithoutChangingTheValue` |

Both flavors' strings are in `app/src/main/res/values/strings.xml` and `values-de/strings.xml`; no
string is composed in code (`guidelines/UI.md` §10).

The `# files:` figure of the architecture suite moves from 857 to **859** with the two new test
sources: `StructureReportTest#greenRunPrintsFigures` reads `rows=33 files=859 exemptions=46 rules=10`.

Group 5's one guideline edit is `guidelines/UIPhoneMap.md` section 7: its "one settings surface" rule names the
renderer's features, and it now names the info button that opens a row's long help on its own sheet
and the slider drawn under its label row with both endpoints named. The *behaviour* rules of those
rows (whole-row toggle, labelled endpoints, live apply, no save action) stay task 3.5's, and the quick
sheet's own rule in `guidelines/UI.md` §9 stays task 6.4's.

## What group 5 does not move

The quick sheet keeps rendering its own voice switch, GPS switch and overspeed slider until group 6
reduces `LocationOptionsSheetContent`; group 5 adds the rows to the surface beside them, as the item's
recorded decision states. One sheet case stays for that reason —
`LocationOptionsOverlayComposeTest#overspeedSliderCanSelectZero` — because it covers behaviour the
surface case does not (the sheet's slider is still live and still snaps to 0). The other two sheet
overspeed cases were duplicates of what the surface case now asserts and are gone.

## The cases, and their tallies

The XMLs are on the item's tree, `.pi/issue-loop/items/902/`, copied there at production time:

```
TEST-com.naviveylin.ui.settings.SettingsSurfaceVoiceSwitchesTest.xml   tests=1  failures=0
TEST-com.naviveylin.ui.settings.SettingsSurfaceCarVoiceRowTest.xml     tests=2  failures=0
TEST-com.naviveylin.ui.settings.SettingsSurfaceTest.xml                tests=14 failures=0
TEST-com.naviveylin.navigation.NavigationEngineVoiceGuidanceTest.xml   tests=14 failures=0
TEST-com.naviveylin.ui.map.LocationOptionsOverlayComposeTest.xml       tests=27 failures=0
TEST-com.naviveylin.ui.map.SpeedWidgetTest.xml                         tests=29 failures=0
TEST-com.naviveylin.ui.settings.SettingsRegistryTest.xml               tests=4  failures=0
TEST-com.naviveylin.architecture.StructureReportTest.xml               tests=2  failures=0
```

The run that produced the app tallies above, from the change root:

```
$ ./gradlew :app:testMobileDebugUnitTest -PforceTests \
    --tests "com.naviveylin.ui.settings.SettingsSurfaceTest" \
    --tests "com.naviveylin.ui.settings.SettingsSurfaceVoiceSwitchesTest" \
    --tests "com.naviveylin.ui.settings.SettingsSurfaceCarVoiceRowTest" \
    --tests "com.naviveylin.navigation.NavigationEngineVoiceGuidanceTest" \
    --tests "com.naviveylin.ui.map.LocationOptionsOverlayComposeTest"
```

and, beside it, `--tests com.naviveylin.ui.map.SpeedWidgetTest --tests
com.naviveylin.ui.settings.SettingsRegistryTest` (the speed widget's warning case that must stay green)
and `:architecture:test --tests com.naviveylin.architecture.StructureReportTest`.

## The red case

`SettingsSurfaceVoiceSwitchesTest#bothSwitchesAreShownAndPersisted` fails on the base — the topic's row
list is empty there, so the phone's settings surface shows no voice row at all:

```
red-on-base-voice-switches.xml   tests=1 failures=1   (2026-11-19T05:12:07.656Z)
java.lang.AssertionError: Action performScrollTo() failed.
Reason: Expected exactly '1' node but could not find any node that satisfies:
(Text + EditableText contains 'Voice guidance' (ignoreCase: false))
```

The other group-5 cases are new sources and stand or fall with it: the car row and the overspeed
slider have no row on HEAD either.

## The scenarios these cases name

`specs/voice-guidance/spec.md` says the announcement rows' cases are written in `tasks.md` group 5; they
are the ones above, one per scenario:

| scenario | case |
|---|---|
| The phone offers both switches in its settings sheet | `SettingsSurfaceVoiceSwitchesTest#bothSwitchesAreShownAndPersisted` |
| The car's switch is edited on the phone | `SettingsSurfaceCarVoiceRowTest#theCarRowWritesOnlyTheCarValue` |
| The next car session reads the phone's edit | `NavigationEngineVoiceGuidanceTest#theCarSessionReadsAValueSetOnThePhone` |
| A car change is visible on the phone | `SettingsSurfaceCarVoiceRowTest#theCarRowShowsTheCarValue` |
| The overspeed delta is a bounded slider | `SettingsSurfaceTest#theOverspeedSliderOffersOneKilometreSteps` |
| A long explanation opens on its own surface | `SettingsSurfaceTest#aLongHelpOpensWithoutChangingTheValue` |
