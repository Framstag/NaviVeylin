# Evidence — item #846, task 1.2 of `package-structure-as-five-kinds`

Change root: `/home/tim/projects/NaviVeylin/.pi/worktrees/loop`, base `6cf7ba501582a7494db71cb379cc1484806ee37a`.
Delta: new `architecture/structure.tsv` (25 declaration rows), `openspec/changes/package-structure-as-five-kinds/tasks.md`
task 1.2 checked and its verify command corrected. No compiled file, no production source file touched.

## The measurement (2026-11-11, change root)

```
$ date -I
2026-11-11
$ find app/src/main/java auto/src/main/java core/src/main/java -name '*.kt' | sed 's#/[^/]*$##' | sort -u | wc -l
25
$ find app/src/main/java auto/src/main/java core/src/main/java -name '*.kt' | wc -l
320
$ find app/src/main/java auto/src/main/java core/src/main/java -type d | grep -v -E 'java$|java/com/naviveylin$' | wc -l
27
```

The item's 27 is a directory count, not a package count: it counts the three intermediate `com`
directories (app, auto, core — no source file in any of them) and, because the pattern excludes
`java/com/naviveylin$`, it drops the app module's root package `com.naviveylin`, which holds six files
(`NaviVeylinApp.kt`, `MainActivity.kt`, `DeepLinkActivity.kt`, `NaviVeylinCarAppService.kt`,
`AutomotiveDevice.kt`, `NativeLogBridge.kt`). 25 packages hold source, measured per module (2026-11-11):

```
$ find app/src/main/java -name '*.kt' | sed 's#/[^/]*$##' | sort -u | wc -l
19
$ find auto/src/main/java -name '*.kt' | sed 's#/[^/]*$##' | sort -u | wc -l
1
$ find core/src/main/java -name '*.kt' | sed 's#/[^/]*$##' | sort -u | wc -l
5
```

— 19 in `:app`, 1 in `:auto`, 5 in `:core` (sums to 25). The row set is the tree's, and a row for `com` would name a directory that holds no source and is
no package, while a declaration without a row for `com.naviveylin` would leave six files in an undeclared
package, which task 1.4's `structureCheckFailsOnUndeclaredPackage` refuses. So the declaration carries 25 rows and task 1.2's verify
now names the command that prints 25.

## Row set verified against the tree

```
$ find app/src/main/java auto/src/main/java core/src/main/java -name '*.kt' \
  | sed 's#.*/src/main/java/##; s#/[^/]*$##; s#/#.#g' | sort -u > scratch/pkg-tree.txt
$ sed -n '/^[a-z]/p' architecture/structure.tsv | cut -f2 | sort -u > scratch/pkg-rows.txt
$ diff scratch/pkg-tree.txt scratch/pkg-rows.txt
(no output — the two sets are equal)
$ rows=25 packages=25
```

The check is green post-change: `rows=25 packages=25` (2026-11-11). On HEAD, where
`architecture/structure.tsv` does not exist, the row set is 0 **by construction, not by a run** — no
failing execution of this check was captured and no XML test figure exists for it (this delta adds no
test source and no production code, so the declared suite is not its proof).

Every non-comment line carries exactly five tab-separated fields (`awk -F'\t' 'NF!=5'` flags only the six
comment lines, each of which is a single field).

## Kind assignment — what each row is today

The five kinds and their edges are the change's, not this item's: `concept` (imports nothing of ours,
Android-free), `port` (interfaces, imports `concept` only), `feature/<name>` (Android-free, may import
`concept` and `port`), `platform/<name>` (bound to a framework, mirrors its feature by name), `shell`
(wires the process, assembles several features). Per the item's recorded decision the `may_import` column
is the permission those rules give, not a census of today's imports.

The five rows that carry `-` for `feature` are the platform packages that serve several features or none
(`data`, `location`, `memory`, `service`, `ui.theme`): the capability spec declares the row's feature name
"where it has one" (`package-structure` — The structure declaration is the single machine-readable
description), and splitting those packages per feature is a later migration, not this item's move.

| row | kind | why |
|---|---|---|
| `com.naviveylin` | shell | `NaviVeylinApp`, `MainActivity`, `CarAppService`, `AutomotiveDevice`: starts the process and its activities |
| `...data` | platform, no feature | 29 repositories and storages serving several features (15 of them import an `android.*`/`androidx.*` type today); it becomes per-feature `platform/<name>` packages as each feature migrates |
| `...di` | shell | wires the process and selects the implementations (D9's "selects a port implementation") |
| `...location`, `...memory`, `...service`, `...ui.theme` | platform, no feature | Android `Service`/`ComponentCallbacks`/notification/theming surfaces that serve every feature |
| `...navigation` | feature/navigation | `NavigationEngine`, the gates, voice guidance, route geometry — 6 of its 18 files are Android-bound and are baseline debt |
| `...share` | feature/share | the share-a-location capability; `SharedLocationHandler` is its one Android-bound file |
| `...ui` | shell | `MainScreen` assembles the map with the feature surfaces |
| `...ui.about`, `...ui.addressbook`, `...ui.attribution`, `...ui.favorites`, `...ui.map`, `...ui.navigation` | platform/<name> | every composable and ViewModel is `platform` (D5); none of these six imports its mirror feature today — `about`, `addressbook`, `attribution`, `favourites` and `map` have no feature row at all, and `ui/navigation` reads its state from `core` (no `com.naviveylin.navigation.*` import), so the mirror edge is debt on all six |
| `...ui.mapmanager` | platform/mapsource | 4 of its 6 files import `core.mapsource` — the mirror edge exists today, so the kind and the feature name are read off the import rather than invented (`BasemapSection`, `NotificationPermission` import nothing of ours) |
| `...ui.route` | feature/route | task 1.7 names `NavigationEngine` → `ui.route.routeLengthMeters` and `NavigationViewModel` → `ui.route.RoutePanelViewModel` as *feature disjunctions*, which is only true if `ui.route` is `feature/route` and `...navigation` is `feature/navigation`; its 4 compose files are purity debt |
| `...util` | concept | `DistanceFormat`: unit vocabulary, imports nothing of ours, no Android type |
| `...auto` | shell | the car host: activities, templates, session and renderer assembly for every feature |
| `core` | concept | 59 files of geometry, units, time, manoeuvre and route vocabulary, plus the port interfaces the modules share; 11 files carry an `android.*`/`androidx.*` import and 3 import another package, which is the concept row's baseline debt (`ManeuverSymbols`, `CarSurfaceHost` are the two task 1.5 names as platform in the target, not concept) |
| `core.addressbook` | port | two `…Provider` interfaces plus the `ContactPostalAddress` value they carry; no Android type, no import of ours |
| `core.details` | feature/details | `DetailsResolver`/`DetailsInput`/`DetailsData`/`ContactLinks`: Android-free logic, imports nothing of ours |
| `core.mapsource` | feature/mapsource | `MapSource`, the repository/download/verification services, Android-free, imports `concept` only |
| `core.search` | feature/search | search criteria, folding, ranking, row text and the search client, Android-free, imports `concept` only |

The commands behind the debt numbers below (change root, 2026-11-11; recorded in `events.jsonl` as
`call_m78da0oe`, `call_2n98iiig`, `call_uoxvx5ul`):

```
$ for m in app auto core; do echo "== $m"; find $m/src/main/java -name '*.kt' -o -name '*.java' \
  | sed 's#/[^/]*$##' | sort | uniq -c | sed "s#^#  #"; done
  # per-directory file counts: navigation 18 files, data 29 files, …
$ tools/look.sh '^import (android|androidx)\.' --path app/src/main/java 2>&1 | grep '^LOOK-FILE' \
  | sed 's#.*/java/com/naviveylin/##; s#\(.*\)/[^/]*$#\1#' | sort | uniq -c | sort -rn | head -30
  # android-importing files per dir: navigation 6, ui/route 4, share 1, data 15, ui/about 4, …
$ tools/look.sh '^import com\.naviveylin\.' --path app/src/main/java 2>&1 | grep '^LOOK-FILE' \
  | sed 's#.*/java/com/naviveylin/##; s#\(.*\)/[^/]*$#\1#' | sort | uniq -c | sort -rn | head -30
  # intra-tree-importing files per dir: ui/map 37, data 16, navigation 9, ui/route 8, …
$ tools/look.sh '^import (android|androidx)\.' --path core/src/main/java 2>&1 | grep '^LOOK-FILE' \
  | sed 's#.*/java/com/naviveylin/##; s#\(.*\)/[^/]*$#\1#' | sort | uniq -c
  # 11 core
$ tools/look.sh '^import com\.naviveylin\.' --path core/src/main/java 2>&1 | grep '^LOOK-FILE' \
  | sed 's#.*/java/com/naviveylin/##; s#\(.*\)/[^/]*$#\1#' | sort | uniq -c
  # 3 core, 1 core/mapsource, 2 core/search
```

Debt this declaration hands to task 1.7 beyond the two `:app` disjunctions it names, from the enforced
rules only (concept self-contained, feature pure/disjunct, platform mirror):

- `concept`: 14 files of `core.core` (11 with an `android.*` import, 3 importing `core/search` or
  `core/addressbook`) and 2 of `core/search` (importing `core.core`).
- `feature`: 6 Android-bound files of `...navigation`, 4 of `...ui.route`, 1 of `...share`, and one
  further disjunction the change did not measure: `ui/route/RoutePanel.kt` imports `core.search`.
- `platform`: every file of a platform row whose feature package does not exist yet, or whose files do not
  import it (`ui/about`, `ui/addressbook`, `ui/attribution`, `ui/favorites`, `ui/map`, `ui/navigation`),
  fails the mirror rule until the feature migrates; the five featureless platform rows fail it by
  definition, and `ui/about` also imports `ui/attribution`, a sibling platform (2 files).

The **file-level** list is what the ratchet's baseline is for (design D6: "every file that has not
migrated is a baseline entry naming the change that moves it"). The row-level failures are not baseline
material — no file-named entry can exempt a row: the five featureless platform rows (`data`, `location`,
`memory`, `service`, `ui.theme`) fail `platformWithoutFeatureFails` by definition, and the rows with no
feature package today (`ui.about`, `ui.addressbook`, `ui.attribution`, `ui.favorites`, `ui.map`) cannot
satisfy the mirror rule by any file-level entry. How those rows are handled — deferred migration or a
row-level exemption — is an open question for task 1.7, which records it there rather than settling it here.
This item writes the declaration and derives nothing from it, which is exactly the scope the item names.
