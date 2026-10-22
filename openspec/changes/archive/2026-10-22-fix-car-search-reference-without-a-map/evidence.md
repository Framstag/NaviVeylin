# Evidence — fix-car-search-reference-without-a-map

Issue #42. Every number below is quoted from the file named beside it; nothing is re-derived. Round 1 of
review refused the change over three findings (B1: the publish wiring and the history pass-through had no
asserting case; B2: a green timestamp that no artifact carries; B3: the publish took the pending render target
where the requirement says "the last frame a car map screen drew"). The evidence is grouped by round so a
reader can see which artifact belongs to which tree.

## 1. Review round 1 (the change as first landed)

### The red case, on HEAD

`auto/src/test/java/com/naviveylin/auto/SearchScreenTest.kt`,
`aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter`: no GPS fix, the car surface's last-frame center published
as `52.52, 13.405`, the screen constructed exactly as `RootScreen.kt:132` and `SearchHistoryScreen.kt:142`
construct it, one search typed.

- `red-HEAD-SearchScreenTest.xml` / `red-HEAD-focused.log` — `tests="17" skipped="0" failures="1" errors="0"`,
  timestamp `2026-10-22T03:37:48.362Z`; the failing assertion:
  `[2]: argument: null, matcher: eq(SearchReference(lat=52.52, lon=13.405)), result: -` — the screen handed the
  provider a null reference.
- The tree that run stood on is documented by its own case list: the 17 cases are the 15 baseline cases plus
  the red case and `theMapScreensOwnCenterWinsOverThePublishedOne`, and
  `noFixAndNoDrawnCarMapMeansNoDistance` is absent because it was added after that run. No captured diff of
  that tree survives, so the case list — not a tree-state claim — is what this evidence rests on.

### After the change, first landing

- `green-SearchScreenTest.xml` — `tests="18" failures="0"`, timestamp `2026-10-22T03:41:18.185Z`. This is the
  **post-restore** green run. The `2026-10-22T03:40:25.791Z` execution's XML was overwritten by that run and no
  longer exists in the tree; earlier drafts of this file, of `proposal.md`, of `tasks.md` and of
  `worker-report.md` quoted the lost timestamp and are corrected here.
- `green-CarMapViewportTest.xml` — `tests="4" failures="0"` at that landing (5 after the review round).
- `green-DetailsScreenTest.xml` — `tests="31" failures="0"` at that landing (32 after the review round).
- `green-focused.log` — the one run behind those three files (`BUILD SUCCESSFUL`).

### Falsification of the fallback (`revert-check`, round 1)

- `mutation-SearchScreenTest.xml` / `mutation-focused.log` — `SearchScreen.kt:208`'s
  `val center = viewportCenter() ?: carMapViewport.center.value` mutated to `val center = viewportCenter()`:
  `tests="18" failures="1"`, timestamp `2026-10-22T03:41:03.896Z`, the failure being
  `aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter` alone. `CarMapViewportTest` was green in that same run
  (4/0) but no XML of it survives — the post-restore run overwrote that tally; the run's own summary line
  (`mutation-focused.log`: `22 tests completed, 1 failed` = 18 + 4) is the whole record of it.
- Restored; the three classes were green again (`green-focused.log`).

## 2. Review round 2 (this tree — corrections B1, B2, B3)

### B3 — the publish takes the committed frame, not the pending target

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`: new `committedViewportCenter()` — the center of
  the frame on the surface, `null` while no frame has committed. `markerViewport()` (the overlay projection)
  keeps its documented pending-target fallback and is no longer used by the publish.
- `auto/src/main/java/com/naviveylin/auto/CarMapViewport.kt`: `publishCarViewportCenter` reads it.
- `green-round2-AutoMapRendererTest.xml` — `tests="82" failures="0"` (82 cases; the new
  `committedViewportCenterIsNullBeforeTheFirstFrame` asserts `null` from a renderer that drew nothing while
  `markerViewport()` still answers), timestamp `2026-10-22T04:03:32.940Z`.
- `green-round2-CarMapViewportTest.xml` — `tests="5" failures="0"` (the publish rule, including
  `aScreenWhoseFrameNeverLandedKeepsTheCenterAlreadyPublished`), timestamp `2026-10-22T04:03:40.753Z`.

### B1 — the wiring is asserted, not just described

- `green-round2-DetailsScreenTest.xml` — `tests="32" failures="0"`, timestamp `2026-10-22T04:03:41.321Z`; the
  new `aStoppedMapPublishesTheLastDrawnFrameCenter` drives `ON_CREATE` → `ON_START` → `ON_STOP` with a
  renderer whose committed center is stubbed and asserts the value reached the provider. The class's own two
  `ON_STOP` cases assert observation sets and screen state, not the provider, and the `setUp` comment that used
  to say so now names this case.
- `green-round2-SearchScreenTest.xml` — `tests="18" failures="0"`, timestamp `2026-10-22T04:03:45.350Z`;
  `historyTapPushesScreenWithQuery` now verifies the pushed screen's own search against
  `SearchReference(52.52, 13.405)` while the surface has `51.5136, 7.4653` published, so the history tap's
  pass-through of its live center is what the case measures.

### Falsification of each new assertion

| mutation | case that must fail | artifact |
|---|---|---|
| `DetailsScreen.onStop`'s `publishCarViewportCenter(...)` deleted | `DetailsScreenTest.aStoppedMapPublishesTheLastDrawnFrameCenter` | `mutation-A-DetailsScreenTest.xml` — `tests="32" failures="1"`, `2026-10-22T04:02:36.256Z`, message `expected:<(51.5136, 7.4653)> but was:<null>` |
| `viewportCenter = viewportCenter,` dropped from the history-tap push | `SearchScreenTest.historyTapPushesScreenWithQuery` | `mutation-B-SearchScreenTest.xml` — `tests="18" failures="1"`, `2026-10-22T04:02:52.519Z`, the call made with `SearchReference(lat=51.5136, lon=7.4653)` (the surface's retained center) where `(52.52, 13.405)` was expected |
| `publishCarViewportCenter` reads `markerViewport()` instead of `committedViewportCenter()` | `CarMapViewportTest.aScreenWhoseFrameNeverLandedKeepsTheCenterAlreadyPublished` | `mutation-C-CarMapViewportTest.xml` — `tests="5" failures="2"`, `2026-10-22T04:03:16.421Z` (that case, `expected:<(51.5136, 7.4653)> but was:<(52.52, 13.405)>`, plus the case whose stub the mutated call no longer matches) |
| the accessor falls back to the pending target before the first frame | `AutoMapRendererTest.committedViewportCenterIsNullBeforeTheFirstFrame` | `mutation-D-AutoMapRendererTest.xml` — `tests="1" failures="1"`, `2026-10-22T04:03:25.879Z`, `expected null, but was:<(51.5142273, 7.4652789)>` |

All four mutations were restored; the four classes were then green in one run
(`green-round2-*.xml`, timestamps `04:03:32` … `04:03:45`).

## 3. The gate (one run, review round 2)

`./tools/loop/gate.sh`, one run, exit 0 — `gate-round2.log` (the run's stdout), one tally XML per shell suite
(`gate-round2-tools_*.xml`, `gate-round2-bash_tools_gen-feature-list.sh_--check-classification.xml`) and
`gate-round2-gradle-modules.txt`.

- `LOOP-GATE: tests=325 failures=0` — the six shell suites: 155 (`tools/loop-selftest.sh`) + 45
  (`tools/sensor-selftest.sh`) + 10 (`tools/check-action-runtimes-selftest.sh`) + 115
  (`tools/gen-feature-list-selftest.sh`) + 0 + 0.
- The Gradle half, whose number the gate cannot parse (Gradle prints no `<name>: all N cases passed` line, so
  the gate's own tally for that command reads `tests=0` — `gate-round2-gradle-tally.xml`): 2098 mobile + 2098
  automotive + 876 `:auto` + 642 `:core` + 48 `:osmscout-client-java` = **5762 tests, 0 failures**, every
  module's newest suite timestamp inside the run (`04:04:19Z` … `04:08:28Z`), rows in
  `gate-round2-gradle-modules.txt`.
- **Provenance of the Gradle numbers:** they exist only in each module's `build/test-results/<task>/` today;
  `gate-round2-gradle-modules.txt` is the archived summary composed from those XMLs after the run, not a file
  `gate.sh` wrote. A `clean` deletes the source XMLs, and the row table is then the surviving record.

## 4. The archive

`openspec archive fix-car-search-reference-without-a-map` moved the change to
`openspec/changes/archive/2026-10-22-fix-car-search-reference-without-a-map` and merged the delta into
`openspec/specs/search-result-ranking/spec.md` — "~ 1 modified", the requirement restated with the added clause,
the two added scenarios and all five `- **Case**:` lines; the other requirements of that capability are
untouched. The review round edited this archived copy in place: `tasks.md`, `design.md`, `proposal.md` and this
file carry the corrections, and the case names the merged requirement quotes still exist.
