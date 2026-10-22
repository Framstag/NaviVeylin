# Evidence — fix-car-search-reference-without-a-map

Issue #42. Every number below is quoted from the file named beside it; nothing is re-derived.

## The red case, on HEAD

`auto/src/test/java/com/naviveylin/auto/SearchScreenTest.kt`,
`aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter`: no GPS fix, the car surface's last-frame center published
as `52.52, 13.405`, the screen constructed exactly as `RootScreen.kt:132` and `SearchHistoryScreen.kt:142`
construct it, one search typed.

- `red-HEAD-SearchScreenTest.xml` / `red-HEAD-focused.log` — `tests="17" skipped="0" failures="1" errors="0"`,
  timestamp `2026-10-22T03:37:48.362Z`; the failing assertion:
  `[2]: argument: null, matcher: eq(SearchReference(lat=52.52, lon=13.405)), result: -` — the screen handed the
  provider a null reference.
- Taken on a tree carrying only `CarMapViewportProvider`, its `AutoEntryPoint` method and its binding: the two
  behaviour lines (the fallback in `searchReference()`, the four `onStop` publishes) were absent.

## After the change

- `green-SearchScreenTest.xml` — `tests="18" failures="0"`, timestamp `2026-10-22T03:40:25.791Z`
  (the 18th case is the boundary case added after the red run).
- `green-CarMapViewportTest.xml` — `tests="4" failures="0"`, the publish rule: the last drawn frame's center is
  published, a screen that drew nothing keeps the previous one, a non-finite coordinate is dropped, and no car
  map yet means no center.
- `green-DetailsScreenTest.xml` — `tests="31" failures="0"`, the fixture realigned: that screen now resolves the
  provider on stop, and its two `ON_STOP` cases run through the new call.
- `green-focused.log` — the one run behind those three files (`BUILD SUCCESSFUL`).

## Falsification (`revert-check`)

- `mutation-SearchScreenTest.xml` / `mutation-focused.log` — `SearchScreen.kt:208`'s
  `val center = viewportCenter() ?: carMapViewport.center.value` mutated to `val center = viewportCenter()`:
  `tests="18" failures="1"`, timestamp `2026-10-22T03:41:03.896Z`, the failure being
  `aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter` alone, while `CarMapViewportTest` stayed green
  (`tests="4" failures="0"`) in the same run.
- Restored; `git diff --stat` on `SearchScreen.kt` reports the 20/8 line delta the change intends, and the three
  classes are green again (`green-focused.log`).

## The gate

`./tools/loop/gate.sh`, one run, exit 0 — `gate.log`, `gate-tally.txt`, and one tally XML per shell suite
(`tools_loop-selftest.sh.xml`, `tools_sensor-selftest.sh.xml`, `tools_check-action-runtimes-selftest.sh.xml`,
`tools_gen-feature-list-selftest.sh.xml`, `tools_check-doc-routes.sh.xml`,
`bash_tools_gen-feature-list.sh_--check-classification.xml`).

- `LOOP-GATE: tests=325 failures=0` (the shell suites).
- The Gradle half, whose number the gate cannot parse (Gradle prints no `<name>: all N cases passed` line, so
  its own tally reads `tests=0` for that command): 2 098 mobile + 2 098 automotive + 873 `:auto` + 642 `:core` +
  48 `:osmscout-client-java` = **5 759 tests, 0 failures**, every module's newest suite timestamp inside the run
  (03:41:59Z … 03:45:41Z).

## One edit after the gate

The `setUp` comments of `SearchScreenTest` were corrected after the gate ran (the old text called the car viewport
center "absent" where the fixture now stubs it) — a comment-only change in a test file, made rather than
re-running the gate, whose evidence a second run would replace. The class was re-run alone to prove the tree
still compiles and passes: `after-comment-edit-SearchScreenTest.xml`, `tests="18" failures="0"`, timestamp
`2026-10-22T03:57:22.413Z`.

## The archive

`openspec archive fix-car-search-reference-without-a-map` moved the change to
`openspec/changes/archive/2026-10-22-fix-car-search-reference-without-a-map` and merged the delta into
`openspec/specs/search-result-ranking/spec.md` — "~ 1 modified", the requirement restated with the new clause,
the two added scenarios and the four `- **Case**:` lines; the other requirements of that capability are
untouched.
