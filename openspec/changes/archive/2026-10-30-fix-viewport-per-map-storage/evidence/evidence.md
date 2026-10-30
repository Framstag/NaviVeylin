# Evidence — fix-viewport-per-map-storage

Base commit `28a174a3`. Every number below is the output of the named run; the XML copies are in this
directory (`tallies.txt` carries the summing command for the module table).

## Red on HEAD

Command:

```
./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.ViewportFileContractTest" \
  --tests "com.naviveylin.ui.map.MapCanvasViewModelViewportPreInitSaveTest"
```

Verdict: `3 tests completed, 3 failed`, `BUILD FAILED in 40s` (`red-on-head.log`).

| file | tally | failure |
|---|---|---|
| `red-on-head-ViewportFileContractTest.xml` | `tests="2" skipped="0" failures="2" errors="0"` | spec does not state `filesDir/maps/viewport-<key>.json`; spec does not state the character rule |
| `red-on-head-ViewportPreInitSaveTest.xml` | `tests="1" skipped="0" failures="1" errors="0"` | `a save with no map key must not write viewport-default.json` |

## Focused green

Four classes, `-PforceTests --no-build-cache` (`green.log`): `BUILD SUCCESSFUL in 11s`.

| file | tally |
|---|---|
| `green-ViewportFileContractTest.xml` | `tests="3" failures="0"` |
| `green-ViewportStorageTest.xml` | `tests="16" failures="0"` |
| `green-ViewportPreInitSaveTest.xml` | `tests="1" failures="0"` |
| `green-NavEndRestoreTest.xml` | `tests="5" failures="0"` |

The contract run's `system-out` carries the three measurements:

```
viewportFile spec=[viewport-<key>.json]
viewportKey spec=[[/data/maps/germany.osm, germany.osm]
viewportEscape spec=[[A-Za-z0-9._-, _]
```

`NavEndRestoreTest`'s new case prints `navEndNoKey files=[]`.

## Falsification (revert-check)

- Mutation `viewportStorageKey() = currentMapKey ?: "default"` (restores the removed fallback) →
  `MapCanvasViewModelViewportPreInitSaveTest` and `MapCanvasViewModelNavEndRestoreTest` red,
  `6 tests completed, 2 failed`, `BUILD FAILED in 14s`
  (`revert-check-skip-ViewportPreInitSaveTest.xml`, `revert-check-skip-NavEndRestoreTest.xml`,
  `revert-check-skip.log`). Restored.
- Mutation `fileNameFor`: `val safe = mapKey` (no escaping) → `ViewportFileContractTest` and
  `ViewportStorageTest` red, `18 tests completed, 2 failed`, `BUILD FAILED in 15s`
  (`revert-check-escape-ViewportFileContractTest.xml`,
  `revert-check-escape-ViewportStorageTest.xml`, `revert-check-escape.log`). Restored.

## Gate

`tools/loop/gate.sh --base HEAD` (`gate.log`):

```
LOOP-GATE: tests=30 failures=0 errors=1 — failing: bash tools/gen-feature-list.sh --check-classification
```

The Gradle gate `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` reports
`BUILD SUCCESSFUL in 3m 28s` (209s), with no `FROM-CACHE` line and no `w:` line.

The ten shell suites report `failures=0`. The one `errors=1` is the classification gate, which exits
non-zero on three **pre-existing** conditions no path of this delta touches (`git status` lists none of
their files): the unclassified spec ids `change-readiness` (open change `change-readiness-gate`) and
`shaping-inbox` (open change `shaping-inbox`), and three stale classification entries
(`search-name-matching`, `search-result-row-icon`, `start-map-selection`). Attributed, not introduced
(`guidelines/Build.md` §4d); `viewport-persist` is already classified and the delta adds no spec id.

Module tallies from the gate's XMLs (`tallies.txt`):

| module / flavor | classes | tests | failures | errors |
|---|---|---|---|---|
| `:app` mobile | 303 | 2171 | 0 | 0 |
| `:app` automotive | 303 | 2171 | 0 | 0 |
| `:core` | 65 | 645 | 0 | 0 |
| `:auto` | 92 | 887 | 0 | 0 |
| `:osmscout-client-java` | 23 | 112 | 0 | 0 |

The four touched classes ran in both app flavors (`gate-mobile-*.xml`, `gate-automotive-*.xml`):
contract `3`, storage `16`, pre-init save `1`, nav-end restore `5`, all `failures="0"`.

## Structural checks

`openspec-validate.log` carries the three commands and their output:

- `openspec validate fix-viewport-per-map-storage --strict` → `Change 'fix-viewport-per-map-storage' is valid`
- `openspec validate viewport-persist --type spec` → `Specification 'viewport-persist' is valid`
- `openspec doctor` → `OpenSpec root: ok`

`tools/check-openspec-hygiene.sh`, `tools/check-doc-routes.sh`, `tools/check-todo-references.sh`,
`tools/check-compose-sandbox-rule.sh` → exit 0 (all four appear in `gate.log`).

The two >500-character warnings on `viewport-persist` pre-exist: on HEAD the Save body measures 948
bytes and the Load body 511 bytes (both already over the limit); the change adds to a body that already
warned, and a MODIFIED block is the validator's exempt case.

## Independent review

`review-round-1.md` records the read-only review round: verdict FAIL on two blocking items — the
premature `[x]` on archive/close (this record and the archive below answer it) and the missing nav-end
skip case (added as `MapCanvasViewModelNavEndRestoreTest#navEndWithoutMapKeyWritesNoViewportFile`, with
the mutation above showing it red for the right reason). Its NOTE-level corrections are applied:
the `Design.md` §9 premise, the pre-init case's prose, the stale-entry attribution and the exact
`system-out` quote above.
