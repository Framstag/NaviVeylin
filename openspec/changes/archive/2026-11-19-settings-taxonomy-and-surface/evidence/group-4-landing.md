# Group 4 (tasks 4.1–4.5) — where it landed, and what its tallies were

Group 4 was worked by the tracker item #901 ("The Navigation & Route topic holds speeds, avoidances and
the car's own anchors") on the branch `fix/issue-901-the-navigation-route-topic-holds-speeds`. The work was
written from base `4c7202eb` (head `03696710474a08350b99190a33ad8b6aebb59b20`), reviewed read-only before its
gate (`items/901/pre-gate-review-restart.md`, `VERDICT: PASS`, `LOOP-BLOCKING: 0`, eight numbered notes), and
then carried onto today's `main` as the item's recorded decision A: `main` was merged into the
branch twice while the group was measured (`3bd02620`, merge commit `2a7afcac`; then `e4132813`, merge
commit `7f0a249f`), so nothing of the group was replayed by hand and the diff below is the
group's own work standing on today's tree. The boxes are ticked in `tasks.md`, in this copy of the change.

The gate below ran on the final merged head, not on `03696710`; the pre-gate review's fourth note asked for
exactly that, and this page quotes that run.

## What the diff contains, per task

| task | files, and what the task put there |
|---|---|
| 4.1 | `app/src/main/java/com/naviveylin/ui/settings/specs/NavigationTopic.kt` — `speedRow` three times: `car-max-speed` (30–160), `bicycle-max-speed` (5–40), `foot-max-speed` (2–15), the sheet's old `MaxSpeedRange` values, `SettingControlPayload.Slider(step = 1)` and `valueFormatRes = R.string.max_speed_value` so the row cannot show a range its slider does not offer. The sheet's three sliders are deleted from `LocationOptionsOverlay.kt` and their plumbing from both `MapCanvasScreenChromeBand.kt` call sites — 0 insertions and 509 deletions across those two production files. Cases `SettingsSurfaceTest#theThreeSpeedSlidersKeepTheirRanges`, `#aSliderChangePersistsAndSurvivesRestart`; the sheet's speed cases deleted with them |
| 4.2 | `avoidanceRow` three times (`SWITCH`, values `"on"`/`"off"`) in `avoidanceSection`, the section beside the speeds on the one page. Case `SettingsSurfaceTest#theAvoidanceSwitchesSitBesideTheSpeeds` (the section's order on the page, the toggle, and `stored.routingProfileFor(Vehicle.CAR).avoidFerries`); `RoutingProfileBuilderTest` stays the one builder every surface reads |
| 4.3 | `anchorRow` twice (`routing-anchor`, `free-driving-anchor`), `SettingControlPayload.Select(choices = anchorChoices, columns = 5)` over `VehicleAnchorPosition.entries`; `SettingChoice.label` and `SettingControlPayload.Select.columns` added in `SettingSpec.kt`; the select sub-surface (`SelectOptionCell`) in `SettingsSurface.kt`. Cases `SettingsSurfaceTest#theAnchorPickerMarksTheConfiguredValue`, `#theAnchorLabelsMatchTheCarPicker`. The sheet's grid picker and its cases are deleted; the labels still come from the one shared `:core` enum |
| 4.4 | `carAnchorRow` twice (`car-routing-anchor`, `car-free-driving-anchor`), `SettingScope.CAR` over `settings.autoRoutingAnchorId` / `autoFreeDrivingAnchorId`, with `inheritingChoice = SettingChoice(key = "", labelRes = R.string.settings_anchor_inherit)` offered first. New source `app/src/test/java/com/naviveylin/ui/settings/SettingsSurfaceCarAnchorTest.kt`: `#anInheritingAnchorIsShownAsInheriting`, `#returningToThePhoneValueClearsTheCarValue` |
| 4.5 | the same row's `read = { it.orEmpty() }` / `write = { … value.ifEmpty { null } }`, so the stored `null` still means "inherit the phone's value" after an edit and after a return to it. Cases `SettingsSurfaceCarAnchorTest#changingThePhoneAnchorChangesAnInheritingCarAnchor`, `#aSetCarValueSurvivesAPhoneAnchorChange` |

Five strings per locale (`settings_help_max_speed`, `settings_help_avoidance`, `settings_help_anchor`,
`settings_help_car_anchor`, `settings_anchor_inherit`) in `app/src/main/res/values/strings.xml` and
`values-de/strings.xml`; no string is composed in code (`guidelines/UI.md` §10).

The `# files:` figure of the architecture suite moves from 859 to **860** with the one new test source:
`StructureReportTest#greenRunPrintsFigures` reads `rows=33 files=860 exemptions=46 rules=10`.

## The documents this group moved

- `openspec/specs/settings-surface/spec.md` — four `Case: none … (tasks.md groups 2-5)` lines now name the
  cases that ship: `SettingsSurfaceTest#theNavigationTopicCarriesTheRoutingValues`,
  `SettingsSurfaceCarAnchorTest#anInheritingAnchorIsShownAsInheriting`,
  `#returningToThePhoneValueClearsTheCarValue`, `SettingsSurfaceTest#theThreeSpeedSlidersKeepTheirRanges`.
- `openspec/specs/routing-avoidance/spec.md` — two more:
  `SettingsSurfaceTest#theAvoidanceSwitchesSitBesideTheSpeeds`,
  `RoutingProfileBuilderTest#theChoicesApplyToEveryVehicle`.
- `guidelines/UI.md` §8d — the rule "a value a car surface cannot edit is edited on the phone, and a car
  anchor restores from the phone": it now names the car's own two anchor rows as car-owned values the phone
  edits from the topic, and it no longer claims the overspeed delta and the long-press delay live in a sheet
  the change's own `location-options-ui` delta removes them from.
- `guidelines/UIPhone.md` §17 — the phone's anchor rows are presented by the settings surface's select
  sub-surface (five columns, three rows), not by the grid dialog the sheet used to open.
- `AGENTS.md` — the routing row prices `UI.md` §8d at **7.6 kB (measured 2026-11-21)**;
  `tools/check-doc-routes.sh --sizes` reads 7633 bytes for the section.
- `LocationOptionsOverlayComposeTest.kt` — the two parameter doc comments the deleted sheet rows left behind
  are gone, with the imports and comments of every other moved row.

## The gate, and the tallies it produced

`tools/loop/gate.sh` reported, on the tree this group lands (`git rev-parse HEAD^{tree}` after the landing
commits reads `6e152804e66cda61a95eca030cc6e364f87c238e`, the tree the run measured; only this section was
written after it):

```
LOOP-GATE-TREE: head=7f0a249f9b0786004c82a0961aa9ae4bf824b9f7 diff=4597384ad4412ae3
LOOP-GATE: tests=50 failures=1 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 214s,
  tools/check-compose-sandbox-rule.sh 0s, … tools/tests/check-openspec-hygiene-selftest.sh 1s)
  — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
```

A first run on the earlier merge (`head=2a7afcac423fd0e3c354c8eec9c3691728c98dff diff=53387641c5aca5ad`)
reported the same tallies (`tests=50 failures=1`, 216 s of Gradle); the run quoted above supersedes it.

The one failure is the base red below; the compiled step reports `2320 tests completed, 1 failed`. The
per-class reports are copied into this directory (`group4-*.xml`), and the console log is `group4-gate.log`.

```
group4-TEST-com.naviveylin.ui.settings.SettingsSurfaceTest.xml              tests=23 failures=0  2026-11-21T15:59:16Z
group4-TEST-com.naviveylin.ui.settings.SettingsSurfaceCarAnchorTest.xml     tests=4  failures=0  2026-11-21T15:59:15Z
group4-TEST-com.naviveylin.ui.settings.SettingsRegistryTest.xml             tests=4  failures=0  2026-11-21T15:59:15Z
group4-TEST-com.naviveylin.ui.map.LocationOptionsOverlayComposeTest.xml     tests=16 failures=0  2026-11-21T15:57:40Z
group4-TEST-com.naviveylin.data.RoutingProfileBuilderTest.xml               tests=5  failures=0  2026-11-21T15:56:44Z
group4-TEST-com.naviveylin.architecture.StructureReportTest.xml             tests=2  failures=0  2026-11-21T15:56:00Z
group4-TEST-com.naviveylin.ui.map.SymbolIconPreferenceTest.xml              tests=5  failures=1  2026-11-21T15:56:11Z
```

Both flavors were measured on the pre-merge head on 2026-11-20 and are in the item's tree
(`items/901/TEST-…-Mobile.xml`, `-Automotive.xml`): `SettingsSurfaceTest` 23/0, `SettingsSurfaceCarAnchorTest`
4/0, `LocationOptionsOverlayComposeTest` 16/0, `RoutingProfileBuilderTest` 5/0, `SettingsRegistryTest` 4/0,
`StructureReportTest` 2/0.

## The red case

The mutation emptied `navigationTopicRows` — the invariant this group ships — and the group's own cases fail
on it, for the right reason (no row node exists to scroll to):

```
group4-red-mutation-settings-surface.xml   tests=23 failures=6  2026-11-20T15:14:27Z
  theNavigationTopicCarriesTheRoutingValues, theThreeSpeedSlidersKeepTheirRanges,
  aSliderChangePersistsAndSurvivesRestart, theAvoidanceSwitchesSitBesideTheSpeeds,
  theAnchorPickerMarksTheConfiguredValue, theAnchorLabelsMatchTheCarPicker
  java.lang.AssertionError: Action performScrollTo() failed.
  Reason: Expected exactly '1' node but could not find any node that satisfies:
  (TestTag = 'settings-row-car-max-speed' …
group4-red-mutation-car-anchor.xml         tests=4  failures=4  2026-11-20T15:14:26Z
```

The restore is the tree this page records: `NavigationTopic.kt` holds `internal val navigationTopicRows:
List<SettingSpec> = listOf(`, and the green reports above follow the restore.

## The base red the gate carries

`SymbolIconPreferenceTest#aChangeReachesTheClientOnceAndIsPersisted` fails with
`java.lang.AssertionError: expected:<[true]> but was:<[true, false, true]>` — the report names queued main-looper
runnables as the suppressed cause. It is the base's own red, not this group's: it failed at base `4c7202eb`
(`group4-base-red-mobile-SymbolIconPreferenceTest.xml`, `group4-base-red-automotive-…`) and on the delta head
(`group4-delta-red-mobile-…`, `group4-delta-red-automotive-…`), all on 2026-11-20, and again in both gate runs
today on the merged head. No path in this diff touches that test's subject.

## The scenarios these cases name

| scenario | case |
|---|---|
| Navigation & Route carries routing values | `SettingsSurfaceTest#theNavigationTopicCarriesTheRoutingValues` |
| A vehicle speed shows its full range | `SettingsSurfaceTest#theThreeSpeedSlidersKeepTheirRanges` |
| The sheet shows the switches (now: the surface does) | `SettingsSurfaceTest#theAvoidanceSwitchesSitBesideTheSpeeds` |
| The car uses the phone's choices | `RoutingProfileBuilderTest#theChoicesApplyToEveryVehicle` |
| An inheriting anchor is shown as inheriting | `SettingsSurfaceCarAnchorTest#anInheritingAnchorIsShownAsInheriting` |
| A phone anchor change reaches an inheriting car anchor | `SettingsSurfaceCarAnchorTest#changingThePhoneAnchorChangesAnInheritingCarAnchor` |
| Returning to the phone value clears the car's own value | `SettingsSurfaceCarAnchorTest#returningToThePhoneValueClearsTheCarValue` |
| A set car anchor survives a phone change | `SettingsSurfaceCarAnchorTest#aSetCarValueSurvivesAPhoneAnchorChange` |

## What became of the pre-gate review's eight notes

1. the stale `Case: none` lines — named above, in both shipped specs. **fixed**
2. the change's `evidence/` carries no group-4 report — the eight `group4-*` files above are that record. **fixed**
3. `evidence/group-4-verification.md` quotes suite figures no report carries — that page is not in this tree:
   it lives in the loop worktree's open copy of the change (`.pi/worktrees/loop/openspec/changes/`), whose
   `2320 / 2321 / 859 → 860` lines this page replaces with the reports above. **superseded**, the stale page is
   a leftover below, not quoted anywhere here
4. the gate records carried other trees — the gate above names `head=7f0a249f… diff=4597384a…`, this tree. **fixed**
5. §8d did not name the car's two anchor rows the group ships — it does now. **fixed**
6. §8d placed the overspeed delta and the long-press delay in a sheet the change's delta removes them from —
   the rule now states the delta is the other value a car surface edits and the delay is phone-only, and says
   nothing about which surface hosts them. **fixed**
7. the migration sentence and `UIPhone.md` §17 still described the sheet's grid dialog — the shipped guideline
   now names the settings surface's select sub-surface. The archived delta's own sentence is left as landed
   history (`openspec/changes/archive/*` records what was known when it landed). **fixed in the shipped document**
8. the two orphan parameter comments at `LocationOptionsOverlayComposeTest.kt:63-67` — deleted. **fixed**

## What this group does not move, and the leftovers it leaves

- The quick sheet keeps its overspeed delta and long-press delay, and the surface now also carries an overspeed
  row (group 5); group 6 owns reducing the sheet, so the duplication is the change's own plan.
- `guidelines/UIPhone.md` §17 still says "the vehicle-position value is global. Editing on either surface
  changes both." The tree has given the car its own `autoRoutingAnchorId` / `autoFreeDrivingAnchorId`
  (`null` = inherit) since before this diff, so that sentence drifts against `main` as well; it belongs to
  whoever owns `vehicle-position-presets` / `auto-map-layout`, not to group 4.
- This change is archived **twice** — `2026-11-19-settings-taxonomy-and-surface` (archived by #902, carries
  `group-5-landing.md` and this page) and `2026-11-20-settings-taxonomy-and-surface` (archived by #900, carries
  `group-2-landing.md` and `slice-1-landing.md`) — each with part of the `tasks.md` ticks, plus an open copy in
  the loop worktree. Group 4's record and its ticks are in the 2026-11-19 copy; the duplicate archive is a
  separate defect.
- `items/901/leftover-from-killed-run.patch` and `items/901/skipped-needs-diagnosis.md` are the previous
  attempt's leftovers; nothing in this diff depends on them.