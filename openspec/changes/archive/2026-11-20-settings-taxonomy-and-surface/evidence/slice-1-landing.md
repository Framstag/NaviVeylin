# Slice 1 of group 3 — the Karte topic's presentation rows (#900)

The decision recorded on #900 re-cut group 3 into four bounded slices. This is slice 1: tasks.md
**3.1** (map style and the POI-symbol switch in the registry and on the surface, style as a `SELECT`
whose options open on a surface of their own) and **3.5's** switch rule and surface-wide
no-save-action rule. The slider half of 3.5 (labelled endpoints) belongs to slice 4, so 3.5 stays
unticked; 3.1, 3.2, 3.3 and 3.4 are other slices' boxes and only 3.1 is ticked here.

## What landed

- `app/src/main/java/com/naviveylin/ui/settings/specs/MapTopic.kt` — `mapTopicRows` holds
  `map-style` (`SELECT`, options from `BundledMapStyles.USER_SELECTABLE`) and `poi-symbols`
  (`SWITCH`), both under the new "Presentation"/"Darstellung" section.
- `app/src/main/java/com/naviveylin/ui/settings/SettingsSurface.kt` — a `SELECT` row opens a
  `ModalBottomSheet` of its choices with the current one marked
  (`SETTINGS_SELECT_TAG`, `SETTINGS_SELECT_OPTION_TAG_PREFIX`), the row itself is the control's
  target (a switch toggles from anywhere on the row, a `SELECT` row opens from anywhere on it), and
  a choice with no string resource shows its own key.
- `app/src/main/java/com/naviveylin/ui/settings/SettingSpec.kt` — `SettingChoice.labelRes` is
  nullable, so a value that carries its own name (a stylesheet's file name) needs no translation.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt` — `followSurfaceWrites()`
  makes the map follow the one writer's document (`SettingsController.settings`) after the start-up
  read, so a style or symbol change made on the surface reaches the running map without a restart.
- `app/src/main/res/values/strings.xml`, `values-de/strings.xml` — the section title and the two
  short help lines.
- `guidelines/UIPhoneMap.md` section 7 — the row behaviour (whole-row toggle, `SELECT` sub-surface,
  no save action) and the case names.

## The red, and what it took

`red-on-HEAD.log` — the three cases compiled against HEAD's production sources (all four Kotlin
files of the delta taken from `fcdc7ea8`): the run fails in `:app:compileMobileDebugUnitTestKotlin`,
because the cases name `SETTINGS_SELECT_TAG` / `SETTINGS_SELECT_OPTION_TAG_PREFIX`, symbols the
delta adds.

`red-without-map-rows.log` and `red-without-map-rows-TEST-SettingsSurfaceTest.xml` — the revert
check: only `MapTopic.kt` and `MapCanvasViewModelSettings.kt` taken from HEAD, so the surface compiles
and the cases run: JUnit XML `tests="3" failures="2" errors="0"`
(`2026-11-20T03:46:42.431Z`), the two failures being
`theStyleRowOpensItsSelectionSurface` (no `settings-row-map-style` node) and
`aChangePersistsWithoutASaveAction` (no `settings-row-poi-symbols` node).
`aSwitchTogglesFromTheWholeRow` passes there by construction: its subject is the row-target rule in
`SettingsSurface.kt`, which that measurement deliberately left in the tree.

## The green

`green-focused-cases.log`, `green-TEST-com.naviveylin.ui.settings.SettingsSurfaceTest.xml` —
`tests="17" skipped="0" failures="0" errors="0"`, timestamp `2026-11-20T03:51:36.561Z`, from

    ./gradlew :app:testMobileDebugUnitTest \
      --tests com.naviveylin.ui.settings.SettingsSurfaceTest \
      --tests com.naviveylin.ui.settings.SettingsRegistryTest \
      --tests com.naviveylin.ui.map.MapCanvasViewModelStyleTest \
      --tests com.naviveylin.ui.settings.SettingsControllerTest

The other three XMLs of that run are beside it (`green-TEST-...SettingsRegistryTest.xml`,
`green-TEST-...MapCanvasViewModelStyleTest.xml`, `green-TEST-...SettingsControllerTest.xml`), all
`failures="0"`.

## The killed process's work, finished

The handover tree did not compile: `MapCanvasViewModelSettings.kt` called
`applyPreferSymbolIcons(settings.preferSymbolPoiIcons)` while the function takes a second,
mandatory `invalidate` parameter. The call now passes `invalidate = mapRenderer != null`, the same
argument the start-up read two blocks above passes.

## Why the old quick-sheet rows stay

`design.md`'s risk note and migration step 3 say a topic lands "behind the old sheet, keeping each
topic's rows available on both until the sheet is cut down in its own task"; step 4 is that cut.
`LocationOptionsOverlay` therefore still carries its style picker and symbol switch in this slice,
and the delta's "The quick sheet holds only the current mode's live controls" scenarios stay
`Case: none` until that task lands.

## Numbers quoted

`measure.log` (`./tools/loop/measure.sh`, run once from the change root):
`LOOP-MEASURE-SUITE cases=495 cmd=tools/loop/tests/loop-selftest.sh --list`,
`LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time`, and
`LOOP-MEASURE-CHANGE ambiguous` naming the nine open changes. The gate was not run by this session:
the run deferred it to the gate child, so no gate tally exists to quote.
