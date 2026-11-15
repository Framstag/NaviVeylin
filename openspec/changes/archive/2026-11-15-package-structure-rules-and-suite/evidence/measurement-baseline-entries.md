# Why the baseline grew by 23 entries, not by the 13 the item's answer predicted

Measured 2026-11-15 on the tree at `1295939c` (item #868's slice) by the feature rules of task 1.6, whose
`feature` rows are the six of `architecture/structure.tsv`: `com.naviveylin.navigation`, `com.naviveylin.share`
and `com.naviveylin.ui.route` in `app`, and `com.naviveylin.core.details`, `com.naviveylin.core.mapsource` and
`com.naviveylin.core.search` in `core`.

The reproducible recipe is the one the concept half of the file records: empty `architecture/baseline.tsv` of
its entries, run `./gradlew :architecture:test -PforceTests --no-build-cache`, and read every violation the
report names. It reports **37** violations — the 14 the concept/port rules of #868 already held, and **23**
below. The census behind the split:

| rule | entries | files |
|---|---|---|
| `feature imports no Android type` | 11 | 11 |
| `feature imports another feature` | 4 | 4 |
| `feature imports nothing upward` | 8 | 8 |
| **total** | **23** | **11 distinct** |

All 11 files sit under `app/src/main/java/com/naviveylin/`; the three `core` feature packages carry no
violation today.

## Where the item's figure came from, and which three facts it missed

The item's answering comment predicted 13 entries: 11 purity and 2 disjunction. Three measurements move it:

1. **15 purity files became 11.** The comment counted `com.naviveylin.ui.navigation` among the features that
   import Android types. That package is a `platform` row in `architecture/structure.tsv` — it holds the
   Android-backed renderer and follow glue, so the purity rule does not apply to it and its 4 Android
   importers are legal. The 11 that remain are the Android importers of the three `app` feature packages.
2. **2 disjunction files became 4.** The comment named the two `:app` edges the item's text quotes,
   `NavigationEngine` → `com.naviveylin.ui.route.routeLengthMeters` and `NavigationViewModel` →
   `com.naviveylin.ui.route.RoutePanelViewModel`. The rule also finds the two `ui.route` files that reach into
   `com.naviveylin.core.search` (`RoutePanel`, `RoutePanelViewModel`, 7 imports each) and
   `RoutePanelViewModel` → `com.naviveylin.navigation`.
3. **The upward-import refusal landed here too and was counted by nothing.** Task 1.6's third rule fires on
   8 files: `NavGraph`, `VoiceGuidance`, `FavoritePickerDialog`, `RoutePanel`, `RoutePanelViewModel` and
   `RouteSummary` reach a `shell` row, and `NavGraph`, `NavigationEngine`, `NavigationNotificationController`,
   `FavoritePickerDialog`, `RoutePanel`, `RoutePanelViewModel` and `RouteSummary` reach a `platform` row.

The item's 17 (15 + 2) is therefore 23 (11 + 4 + 8), and no entry is a file the rules do not name: the green
run's summary line reads `rows=27 files=338 exemptions=37 rules=7`, which the check refuses if an entry names
no change or a file that no longer violates the rule (`evidence/green-StructureReportTest.xml`).

## What the falsification cannot show

The 11 purity files are the reason the rule exists, so a check that passes on them proves nothing by itself.
`evidence/revert-check-3.2-red.xml` and `evidence/revert-check-3.3-red.xml` add one legal-looking import to a
`feature` file that has none — `core.search`'s `PoiNameFilter.kt`, which imports only the libosmscout client —
and the report names the file, the import and the rule, before the file is restored and the run is forced
green.