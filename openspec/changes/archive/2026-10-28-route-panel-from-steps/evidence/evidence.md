# Evidence — `route-panel-from-steps`

The route list is derived from the route's structured steps instead of being parsed back out of the
native English description lines. Everything below was measured on this tree; the three items that
need a device are named at the end and are **not** claimed.

## Scope line (task 4.5)

**The car surfaces are untouched by this change.** The car route-description screen reads the live
`RouteInstruction` list (`:auto`), which this change neither feeds nor changes; no `:auto` file was
edited (`git status` touches no path under `auto/`), and no device run covers the car. The phone rows
being green therefore proves nothing about the car path — that path belongs to
`route-steps-live-guidance`, and a car-side regression from this change is impossible by construction
rather than by test.

## Build and suites

- `build-both-flavors-warnings.log` — `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug
  --rerun-tasks`: **BUILD SUCCESSFUL**, no `w:`/`e:` line, i.e. both flavors build without warnings
  (task 7.1).
- `gate.log` — the change's gate, `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
  --continue` over the whole tree: **BUILD SUCCESSFUL**, and the executed test tasks are
  `:osmscout-client-java:test`, `:core:testDebugUnitTest`, `:auto:testDebugUnitTest`,
  `:app:testAutomotiveDebugUnitTest`, `:app:testMobileDebugUnitTest` — both app flavors, both other
  modules, all forced (task 7.2). One pre-existing warning is in the log
  (`app/src/test/java/com/naviveylin/data/StartMapResolverTest.kt:131`, `String?` vs `String`); that
  file is unmodified by this change (`git diff` on the path is empty).
- `route-suite.log` — `:app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.*"`, forced, no
  build cache: green.
- `core-suite.log` — the `:core` cases this change adds (`ManeuverFromStepTest`, `ManeuverLabelTest`)
  plus the two that constrain them (`TurnInstructionLocalizerTest`, `GermanTranslationCompletenessTest`,
  the latter because `nav_continue_on` gains a German translation): green.

No exclusion: the touched suites are the route panel's, the two `:core` label suites, the map tests
that consume the panel's segments (`MapCanvasViewModelStepFocusTest`,
`MapCanvasViewModelSessionLeaseTest`) and `NavigationEngineTest`'s route-length case — all of them run
in the gate above. The instrumented suite is excluded from the gate by construction (it needs a device)
and is named under "Open" below.

## Revert-checks (tasks 2.6, 2.7, 2.8, 5.4)

One mutation per invariant, the named case must go red, restore, forced green. The runs are in
`revert-*.log`; each line below is the case the task named:

| mutation | rule mutated | case that went red |
|---|---|---|
| `revert-tiling-invariant.log` | the boundary rule (`from = startIndices[step - 1]` → `startIndices[step]`): a merged row keeps its own boundary | `RouteRowDerivationTest` "rows tile the polyline without a gap or an overlap" — **1 of 11 red** |
| `revert-sum-invariant.log` | the fold (`distanceMeters = last + distance` → `last`): a merged step's leg is dropped | "row distances sum to the route length after merging" — **4 of 11 red** (the four leg-counting cases) |
| `revert-informative-rule.log` | `informativeEarnsRow()` `false` → `true`: an informative step is let through as a row | "an informative step is not a row and its leg moves to the row above" — **3 of 11 red** |
| `revert-name-change-window.log` | `CLOSE_MANOEUVRE_WINDOW_METERS` `200.0` → `0.0` | "a street-name change restated by the next manoeuvre is merged into it" — **1 of 11 red** |
| `revert-checks-restored-green.log` | none (both files restored byte-identically, checked with `diff -q`) | green, forced |

The mutation script was a throwaway outside the tree; the four sed expressions it used are the ones
named in the table, so each check can be re-run by hand.

## Gate for the app's own sources (task 4.3)

`grep -rn "descriptions\|parseTurnType\|parseStepDisplay\|isInstructionLine" app/src/main/java app/src/main/res`
→ **no match**. The literal task text greps `app/src/main`, which also contains the untouched
libosmscout submodule; its C++ test comments match the word "descriptions" and are not this app's
sources. Excluded deliberately, not silently: the submodule is untouched below.

## No bridge change (task 6.2)

- `git diff -- app/src/main/cpp/libosmscout` → empty; `git status --porcelain | grep -c app/src/main/cpp`
  → `0`.
- `git submodule status app/src/main/cpp/libosmscout` → `fcfd3efcae88fe5ec41c25138a0d54fc7923d6d5` (the
  pin this change found; it is not moved).

## The census, on device (task 5.2) and the policy it settled (task 5.3)

`device-census.log` — the `ROUTE` tag's census line, written by the production `routeCensus` over the
`RouteEntry.steps` the bridge published for real calculated routes (`RouteRowCensusDeviceTest`, run on
`emulator-5554` with `am instrument`):

```
Andorra la Vella -> Escaldes-Engordany (city), 08:39:53:
  steps=330 rows=24 turn=4 straightOn=0 nameChange=5 roundabout=16 motorway=1 info=302 start=1 target=1
  dist_km turn=0.1 straightOn=0.0 nameChange=0.1 info=4.4  leg_m median=7 p90=33 under20=272
  so_runs=0 so_len_median=0 so_len_max=0  nc_total=5 nc_restated=5 nc_runs2plus=0 nc_blank=0 nc_short=4
  nc_leg_median=8 info_name_only=0 info_poi=18 info_via=0
Route · Escaldes-Engordany (the same route through the UI), 08:42:57:
  steps=501 rows=34 turn=4 straightOn=0 nameChange=1 roundabout=26 motorway=2 info=466 start=1 target=1
  dist_km turn=0.1 straightOn=0.0 nameChange=0.0 info=8.8  leg_m median=10 p90=39 under20=353
  so_runs=0 so_len_median=0 so_len_max=0  nc_total=1 nc_restated=1 nc_runs2plus=0 nc_blank=0 nc_short=1
  nc_leg_median=2 info_name_only=0 info_poi=11 info_via=0
  route analysis: steps=34 withValues=33 sumM=9100 totalM=9100 sumS=558 totalS=655 maxErrM=0
```

Two longer routes on the North Rhine-Westphalia map were measured in the same session before that map was
lost (see "What went wrong" below), with the census field set before `nc_blank`/`nc_short`/`info_poi`/
`info_via` were split out:

```
Dortmund Hbf -> Essen Hbf (motorway), 08:35:50:
  steps=835 rows=21 turn=14 straightOn=0 nameChange=6 roundabout=0 motorway=2 info=811
  dist_km turn=0.3 straightOn=0.0 nameChange=0.4 info=36.1  leg_m median=33 p90=82 under20=234
Dortmund Hbf -> Cologne Hbf (motorway), 08:36:01:
  steps=1663 rows=27 turn=10 straightOn=0 nameChange=13 roundabout=2 motorway=5 info=1631
  dist_km turn=0.2 straightOn=0.0 nameChange=0.8 info=96.1  leg_m median=43 p90=107 under20=319
```

**What the numbers decided** (recorded in `informativeEarnsRow()` and `MIN_ROW_LEG_METERS`):

- `informative` steps never earn a row — 302/330, 466/501, 811/835 and 1631/1663 of the measured routes'
  steps are informative. A row for each would turn a 21–34-row list into an 800–1600-row one.
- `POIAtRoute` never earns a row — 18 and 11 steps carry a POI record on the two Andorran routes; the
  spec's rule puts a POI record in the same class as the other informational values.
- `via` never earns a row — `info_via=0` on both measured routes, so nothing measured argues for one.
- `MinRowLegMeters` stays at 20 m — `leg_m median` is 7–43 m and `under20` covers 234–353 steps per route,
  while every measured name change was restated by the next manoeuvre (`nc_restated = nc_total`), so the
  floor removed no decision on the routes measured.
- **Not measured, and it shows**: `straightOn=0` and `so_runs=0` on every route — the bridge's `TURN` steps
  never carry a straight-on move on this map data, so the straight-on collapse rule fires only in the
  fixtures (`RouteRowDerivationTest`); the device run neither confirms nor contradicts it.
- The roundabout counts are the change's premise, measured: 16 and 26 roundabout steps on the two
  Andorran routes, which the prose list showed as nothing at all.

## The bridge's projection, on device (task 6.1)

`adb shell am instrument -w -e class com.naviveylin.route.RouteStepModelBridgeTest` (Andorra map data):
**OK (5 tests)**, `routed=5/5`, and the two conditional cases saw real data — `roundabout leave steps: 16`
and `motorway change steps: 6`. The falsification the task asks for: the exit-ordinal write in the bridge
(`OSMScoutClient.cpp:1224`, `exitCountTaken` → `0L`) was mutated, the APK rebuilt
(`-Pandroid.injected.build.abi=x86_64`), reinstalled and re-run:

```
1) aRoundaboutLeaveStepCarriesOrdinalAndDirection(...)
java.lang.AssertionError: Andorra la Vella centre -> Escaldes-Engordany: the exit ordinal is the
record's, never recovered from text
Tests run: 5,  Failures: 1
```

restored byte-identically (`diff -q` against the pre-mutation copy), rebuilt, reinstalled, re-run: `OK (5
tests)`. One case was wrong on its first version — it asserted `steps.size <= descriptions.size`, which the
real data contradicts (330 steps against 7 instruction lines: a step is a described *node*, not an
instruction). It now asserts route order by the published `distanceFromStart`.

## The list on the device (task 7.3)

Route: current location (Andorra la Vella) → Escaldes-Engordany, 9,1 km · 10 min.

- `device-route-list-1.png`, looked at in a throwaway child session: header `Route · Escaldes-Engordany`,
  `9,1 km · 10 min`, `Aktueller Standort → Escaldes-Engordany`, `Schritte`, then rows `Start` (9 m),
  `Leicht rechts abbiegen` (200 m / 15 s) — German text, app-formatted values, the start row carrying the
  start glyph and the turn row a turn arrow.
- `device-route-list-2.png` (scrolled), same look: `Kreisverkehr` (24 m), **`Ausfahrt 2` (8 m)**, `Leicht
  rechts abbiegen in Carrer Doctor Vilanova` (150 m / 10 s), `Kreisverkehr` (50 m). The exit row's glyph is
  the ring with the digit `2` inside — the row icon from the step's own ordinal. **No English row text**;
  the only non-German strings in the frame are map toponyms.
- `device-route-list-rows.txt` — the same rows from the `uiautomator` dump of that frame (state evidence
  beside the pixels).
- `device-census.log`'s `route analysis` line is the numeric half of the same frame: 34 rows, sum 9100 m
  against the route's 9100 m, `maxErrM=0`.

The property is producible on a stationary emulator, so no "cannot be measured" note applies.

## What went wrong on the device, recorded

1. **`connectedAndroidTest` deleted the app's map data.** The first census run used
   `./gradlew :app:connectedMobileDebugAndroidTest`, which uninstalls the app afterwards — the 783 MB NRW
   map and the basemap went with it (`provision-phone-emulator`, "Never"). The four long-route numbers above
   are the pre-wipe measurements; the run was repeated after re-provisioning Andorra (5.6 MB) + the minimal
   basemap through the app's own map manager, with `am instrument` from then on. The lesson is in
   `ki_processing_failures.log`.
2. **The app did not start at all at first — issue #691.** Every install crashed in Hilt injection with
   `ClassNotFoundException: com.framstag.libosmscout.client.MapProvider`; it reproduces on a clean HEAD
   (`git stash push -u`, rebuilt, reinstalled) and comes from `:osmscout-client-java`'s two source sets
   (#678): the app packages the module's `main` classes, not the `jar` that carries both halves. The device
   evidence above was produced with a **temporary, uncommitted** workaround (the submodule's Java client
   sources copied into `app/src/main/java/com/framstag/libosmscout/client/`), removed again afterwards; the
   defect and its proper fix are the issue's, not this change's.

