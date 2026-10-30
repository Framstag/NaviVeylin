# Tasks — fix-viewport-per-map-storage

## 1. Red on HEAD

- [x] 1.1 Add `app/src/test/java/com/naviveylin/data/ViewportFileContractTest.kt`: reads
  `openspec/specs/viewport-persist/spec.md` and compares its stated per-map file pattern and character
  rule with what `ViewportStorage.save` actually writes (the `FavAutoZoomClampRangeTest` /
  `MapMenuBackOrderComposeTest` precedent; the key-derivation case is added in 2.3 because it names
  `ViewportStorage.keyFor`, which this change introduces). *(spec: viewport-persist — Save viewport
  state, scenarios "Save under the map's key", "Characters outside the safe set are escaped")*
- [x] 1.2 Add `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelViewportPreInitSaveTest.kt`:
  with the restore guard open and no map key, `saveViewport()` must leave `filesDir/maps` empty.
  *(spec: viewport-persist — Save viewport state, scenario "Save without a map key is skipped")*
- [x] 1.3 Red run on HEAD:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.ViewportFileContractTest" --tests "com.naviveylin.ui.map.MapCanvasViewModelViewportPreInitSaveTest"`
  → `3 tests completed, 3 failed`, `BUILD FAILED in 40s`.
  `evidence/red-on-head-ViewportFileContractTest.xml` `timestamp="2026-10-30T…"`,
  `tests="2" skipped="0" failures="2" errors="0"`, failures
  `the spec must state the character rule for the key ("outside `…` SHALL be replaced by `…`")` and
  `the spec must state the per-map file `filesDir/maps/viewport-<key>.json``;
  `evidence/red-on-head-ViewportPreInitSaveTest.xml` `tests="1" skipped="0" failures="1" errors="0"`,
  failure `a save with no map key must not write viewport-default.json`; console log
  `evidence/red-on-head.log`. Every red is an assertion failure, not a compile or parse failure.
  *(spec: viewport-persist — Save viewport state / Load viewport state)*

## 2. The fix

- [x] 2.1 `app/src/main/java/com/naviveylin/data/ViewportStorage.kt`: `keyFor(mapPath)`
  (`substringAfterLast('/')`) and `fileNameFor(key)` (`viewport-<escaped key>.json`, characters outside
  `A-Za-z0-9._-` → `_`) own the one derivation and the one name; `fileFor` uses `fileNameFor`.
  *(spec: viewport-persist — Save viewport state + Load viewport state)*
- [x] 2.2 `MapCanvasViewModel.initMap`: derives the map key through `ViewportStorage.keyFor` and uses it
  for both the display key and the load; the view-change listener saves under the key it captured before
  its first suspension, not the mutable `currentMapKey`.
  *(spec: viewport-persist — Re-entry supersedes an in-flight map init, carried unchanged)*
- [x] 2.3 `MapCanvasViewModel.viewportStorageKey()` is the one accessor for the save sites:
  `saveViewport` (`MapCanvasViewModelCamera.kt`) and the navigation-end save
  (`MapCanvasViewModelRoute.kt`) skip while it is null and log `saveViewport: skipped, no map key yet`;
  the `"default"` literal and its unreadable `viewport-default.json` are gone.
  *(spec: viewport-persist — Save viewport state, scenario "Save without a map key is skipped";
  decision (a) of issue #54)*
- [x] 2.4 `openspec/specs/viewport-persist/spec.md`: the two storage requirements state the per-map
  layout, the key derivation (with `/data/maps/germany.osm` → `germany.osm`), the escaping (with
  `Europe map.osm` → `viewport-Europe_map.osm.json`) and the skip; every existing scenario is carried,
  three are added. The change's delta (`specs/viewport-persist/spec.md`) carries the same two blocks.
  *(spec: viewport-persist — Save viewport state / Load viewport state)*
- [x] 2.5 The key-derivation conformance case (naming `ViewportStorage.keyFor`) is added to
  `ViewportFileContractTest`, and `ViewportStorageTest` gains `save writes the file named by the map
  key`, `characters outside the safe set are escaped in the file name` and `the key is the map path's
  last segment`. `MapCanvasViewModelNavEndRestoreTest` names the map it simulates
  (`currentMapKey = "testmap"`, `load("testmap")`) instead of relying on the orphaned bucket.
  *(spec: viewport-persist — Save viewport state / Load viewport state)*
- [x] 2.6 Focused green run of the four touched classes (`-PforceTests --no-build-cache`):
  `BUILD SUCCESSFUL in 11s`, `ViewportFileContractTest` `tests="3" failures="0"`,
  `ViewportStorageTest` `tests="16" failures="0"`, `ViewportPreInitSaveTest` `tests="1" failures="0"`,
  `NavEndRestoreTest` `tests="5" failures="0"` (`evidence/green*.xml`, `evidence/green.log`); the
  contract run's `system-out` carries `viewportFile spec=[viewport-<key>.json]`,
  `viewportKey spec=[[/data/maps/germany.osm, germany.osm]`,
  `viewportEscape spec=[[A-Za-z0-9._-, _]`.

## 3. Every delta scenario and the case that exercises it

- [x] 3.1 "Save under the map's key" → `com.naviveylin.data.ViewportFileContractTest#the spec states
  the per-map file the storage writes` and `com.naviveylin.data.ViewportStorageTest#save writes the file
  named by the map key`. *(spec: viewport-persist — Save viewport state)*
- [x] 3.2 "Characters outside the safe set are escaped" → `ViewportFileContractTest#the spec states the
  escaping the storage applies` and `ViewportStorageTest#characters outside the safe set are escaped in
  the file name`. *(spec: viewport-persist — Save viewport state)*
- [x] 3.3 "Save without a map key is skipped" → `MapCanvasViewModelViewportPreInitSaveTest#a lifecycle
  save without a map key writes no viewport file` (red on HEAD) **and**
  `MapCanvasViewModelNavEndRestoreTest#navEndWithoutMapKeyWritesNoViewportFile` (added after the review
  round; red under the 4.1 mutation, which is the case the removed navigation-end fallback needs).
  *(spec: viewport-persist — Save viewport state)*
- [x] 3.4 The key derivation the two requirements state → `ViewportFileContractTest#the spec states the
  key derivation the storage applies` and `ViewportStorageTest#the key is the map path's last segment`.
  *(spec: viewport-persist — Save/Load viewport state)*
- [x] 3.5 Carried scenarios → their existing cases, green in the gate: "Save on pause", "Save after
  gesture", "Save fractional magnification after pinch", "Pause during restore does not clobber",
  "Load saved state on startup", "Load legacy integer magnification", "No saved state uses default",
  "Corrupted file uses default" → `ViewportStorageTest`, `MapCanvasViewModelViewportRestoreTest`
  (baseline, not delivered here).

## 4. Falsification (revert-check)

- [x] 4.1 Mutation: `viewportStorageKey()` returns `currentMapKey ?: "default"` (restores the removed
  fallback). Forced red: `MapCanvasViewModelViewportPreInitSaveTest` and
  `MapCanvasViewModelNavEndRestoreTest` — `6 tests completed, 2 failed`, `BUILD FAILED in 14s`
  (`evidence/revert-check-skip-ViewportPreInitSaveTest.xml`,
  `evidence/revert-check-skip-NavEndRestoreTest.xml`, `evidence/revert-check-skip.log`). Restored;
  2.6's green is from the restored source.
- [x] 4.2 Mutation: `fileNameFor` keeps the key verbatim (`val safe = mapKey`). Forced red:
  `ViewportFileContractTest#the spec states the escaping the storage applies` and
  `ViewportStorageTest#characters outside the safe set are escaped in the file name` — `2 failed`,
  `BUILD FAILED in 15s` (`evidence/revert-check-escape-*.xml`, `evidence/revert-check-escape.log`).
  Restored.

## 5. The gate

- [x] 5.1 `tools/loop/gate.sh --base HEAD` over the tree carrying the change:
  `LOOP-GATE: tests=30 failures=0 errors=1` — the Gradle gate
  `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` reports `BUILD SUCCESSFUL in 3m 28s`
  (209s), and the ten shell suites report `failures=0`; the single `errors=1` is
  `bash tools/gen-feature-list.sh --check-classification`, which fails on three **pre-existing**
  conditions (`change-readiness` from open change `change-readiness-gate`, `shaping-inbox` from
  `shaping-inbox`, and three stale classification entries) — no path of this delta is among them
  (`git status`). No `w:` line and no `FROM-CACHE` line in the gate log.
  Module tallies from the gate's own XMLs (`evidence/tallies.txt` carries the summing command):
  `:app` mobile 303 classes / 2171 tests / 0 failures, `:app` automotive 303 / 2171 / 0,
  `:core` 65 / 645 / 0, `:auto` 92 / 887 / 0, `:osmscout-client-java` 23 / 112 / 0. The four touched
  classes ran in both app flavors (`evidence/gate-mobile-*.xml`, `evidence/gate-automotive-*.xml`,
  `tests="3"`, `"16"`, `"1"`, `"5"`, all green). Full log `evidence/gate.log`.
- [x] 5.2 Structural checks the delta also owes, with their output in `evidence/openspec-validate.log`:
  `openspec validate fix-viewport-per-map-storage --strict` → `Change 'fix-viewport-per-map-storage' is
  valid`; `openspec validate viewport-persist --type spec` → valid; `openspec doctor` → `OpenSpec root:
  ok`; `check-openspec-hygiene.sh`, `check-doc-routes.sh`, `check-todo-references.sh` and
  `check-compose-sandbox-rule.sh` exit 0. The two >500-character warnings on `viewport-persist`
  pre-exist (HEAD bodies measure 948 and 511 bytes; the change adds to a body already over the limit,
  and a MODIFIED block is the validator's exempt case).
- [x] 5.3 No device measurement is owed: no pixel, gesture or on-device behaviour changes — the delta
  alters the storage file name contract and a pre-init save that is unreachable on a device.

## 6. Review, archive and close

- [x] 6.1 Independent read-only review of the diff against the delta and the gate XMLs
  (`evidence/review-round-1.md`). Verdict: FAIL on two blocking items — the archive/close markers were
  set before the work (this task and 6.2/6.3 close that) and the navigation-end skip had no case (added
  in 3.3 and shown red by 4.1). All NOTE-level corrections are applied: the `Design.md` §9 premise, the
  pre-init case's prose, the stale-entry attribution, and the exact `system-out` quote.
- [x] 6.2 `openspec archive fix-viewport-per-map-storage`, confirm the directory exists under
  `openspec/changes/archive/` and that the two MODIFIED requirements merged into
  `openspec/specs/viewport-persist/spec.md` (they are byte-identical, so the archive sync is a textual
  no-op). Archived as `2026-10-30-fix-viewport-per-map-storage`; the tool reported `Specs already in
  sync; no files changed`.
- [x] 6.3 Close issue #9 with the change name and the evidence paths; record the residual of #54
  (decision (a) landed here) on that item. #9 closed (completed) on 2026-10-30; #54 carries a
  comment-only record of decision (a).
