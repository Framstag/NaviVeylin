# Tasks

## 1. The red cases on HEAD

- [x] 1.1 `FavoritesScreenObservations.anUnreadyStoreIsInitialisedOnceAndItsRowsRender` — an unready
      provider fake with an `init` recorder; on HEAD `init` is never called and nothing is published.
      Red on HEAD: `java.lang.AssertionError: the observation must initialise the store it reads, exactly
      once per started period: [] expected:<1> but was:<0>` (XML `tests="10" failures="2"`,
      `BUILD FAILED in 54s`, 10 tests completed, 2 failed).
- [x] 1.2 `FavoritesScreenObservations.aReadyStoreIsNotInitialisedAgain` — the guard: a warm store is
      observed, not re-initialised.
- [x] 1.3 `FavoritesScreenObservations.anObservationFaultIsRetriedOnceAndTheListPublishes` — the
      faulting accessor throws once; on HEAD the hop is attempted once and nothing is published. Red on
      HEAD: `java.lang.AssertionError: the faulting hop must be attempted twice: [storeReady]
      expected:<2> but was:<1>`.
- [x] 1.4 Recorded both red runs in `evidence/red-on-HEAD-995.log` (the two cases, their messages, the
      XML tallies).

## 2. The fix — the observation owns what it depends on

- [x] 2.1 `FavoritesScreenObservations`: `favoritesPath` parameter; the hop reads `storeReady()` first
      and calls `init(favoritesPath)` once for the started period while it is false, inside the existing
      `withContext(backgroundDispatcher)` (design D1).
- [x] 2.2 `resolveSources`/`attemptResolution`: one retry after a fault, `CancellationException`
      rethrown, both faults logged with the attempt's identity and their durations (design D3,
      `guidelines/Logging.md` §3).
- [x] 2.3 `FavoritesListState(failed = …)`: the failure state the observation publishes when both attempts
      fault (design D4).
- [x] 2.4 `FavoritesScreenObservationsTest.aStoreThatFailsTwiceIsReportedAsUnavailable` — the failure
      state reaches the screen exactly once and invents no rows.

## 3. The screen reports it

- [x] 3.1 `FavoritesScreen`: hands the observation its own `filesDir/favorites.json`, applies the one
      state object, and renders the unavailable row before the loading row (design D3/D4).
- [x] 3.2 Strings `favorites_unavailable` / `favorites_unavailable_hint` in `values` and `values-de`.
- [x] 3.3 `FavoritesScreenTest.anUnavailableStoreRendersTheUnavailableRow`.
- [x] 3.4 `FAVORITES_FILE` declared once in `:auto` (the observation's file), `NavigationSession` uses it:
      the warmup and the screen cannot read different files (design D2).

## 4. Documentation

- [x] 4.1 `guidelines/UICar.md` §3a: the row's second deadline — the observation establishes the store
      it reads, retries once, and reports unavailable rather than loading.
- [x] 4.2 `guidelines/UICar.why.md` §3a: the root cause, the two red cases by name.
- [x] 4.3 `AGENTS.md`'s routing row: §3a's stated cost re-measured (5.4 kB → 6.0 kB);
      `tools/check-doc-routes.sh` green (356 references, 8 skill references).

## 5. Revert-checks — one mutation per invariant

- [x] 5.1 **Invariant**: an unready store is initialised by the observation. **Mutation**: the `if
      (!ready.value)` branch disabled (`&& false`). **Case that must fail**:
      `anUnreadyStoreIsInitialisedOnceAndItsRowsRender`. Ran 2026-11-21: the case failed with
      `the observation must initialise the store it reads, exactly once per started period: []
      expected:<1> but was:<0>`; XML `FavoritesScreenObservationsTest tests="11" failures="1"`,
      `FavoritesScreenTest tests="38" failures="0"`, `49 tests completed, 1 failed`, `BUILD FAILED in
      21s`.
- [x] 5.2 **Invariant**: a faulting hop is retried once. **Mutation**: `resolveSources` returns the first
      attempt unconditionally. **Case that must fail**:
      `anObservationFaultIsRetriedOnceAndTheListPublishes`. Ran 2026-11-21: failed with
      `the faulting hop must be attempted twice: [storeReady] expected:<2> but was:<1>`; XML
      `tests="11" failures="1"` / `tests="38" failures="0"`, `49 tests completed, 1 failed`, `BUILD
      FAILED in 21s`.
- [x] 5.3 **Invariant**: two faults are reported, not left loading. **Mutation**: the failure state's
      publish replaced by a bare `return@observe`. **Cases that must fail**:
      `aStoreThatFailsTwiceIsReportedAsUnavailable` **and** `anUnavailableStoreRendersTheUnavailableRow`.
      Ran 2026-11-21: both failed — `the failure state must reach the screen exactly once: []
      expected:<1> but was:<0>` and `expected:<[Favorites unavailable]> but was:<[Loading...]>`; XML
      `FavoritesScreenObservationsTest tests="11" failures="1"`, `FavoritesScreenTest tests="38"
      failures="1"`, `49 tests completed, 2 failed`, `BUILD FAILED in 22s`.
- [x] 5.4 Restored all three mutations (`grep -rn 'REVERT-CHECK MUTATION'` → no match), then forced the
      green re-run: `--rerun-tasks` → `65 actionable tasks: 65 executed`, `BUILD SUCCESSFUL in 1m 19s`,
      XML `FavoritesScreenObservationsTest tests="11" failures="0" timestamp="2026-11-21T12:02:45.696Z"`,
      `FavoritesScreenTest tests="38" failures="0" timestamp="2026-11-21T12:02:52.797Z"`. Transcript:
      `evidence/revert-checks.log`.

## 6. The gate

- [x] 6.1 `./tools/loop/gate.sh` — ran 2026-11-21 on the change's tree: `LOOP-GATE: tests=39 failures=1
      errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 310s, tools/check-doc-values.sh
      0s, tools/tests/check-doc-values-selftest.sh 0s, tools/check-doc-routes.sh 1s,
      tools/tests/check-doc-routes-selftest.sh 10s, tools/mutate.sh --mutations
      tools/tests/check-doc-routes-mutations.json --list 1s, tools/check-todo-references.sh 2s)`.
      Transcript: `evidence/gate.log`.
- [x] 6.2 **The one red is the base's, not this delta's** (`guidelines/BuildEvidence.md` §4d, issue #921).
      The failing case is `com.naviveylin.ui.map.SymbolIconPreferenceTest >
      aChangeReachesTheClientOnceAndIsPersisted` (`expected:<[true]> but was:<[true, false, true]>`), in
      `:app`'s map view model — a path this change does not touch (`git status` names `auto/**`, two
      guidelines, `AGENTS.md` and the change directory only). Measured in a clean worktree at the base
      `66d7618f` (`.pi/worktrees/995-base`, `122 actionable tasks: 122 executed`, `BUILD FAILED in 13m
      8s`): the same case fails there with the same message, XML `tests="5" failures="1"` — recorded as
      `evidence/base-red-SymbolIconPreferenceTest.xml`. The delta's own gate steps are all green; the
      base's red case is carried, not caused.
- [x] 6.3 Archived into `openspec/changes/archive/2026-11-21-fix-car-favorites-observation-owns-readiness/`
      (`openspec archive`, the delta merged into `openspec/specs/auto-favorites/spec.md`).