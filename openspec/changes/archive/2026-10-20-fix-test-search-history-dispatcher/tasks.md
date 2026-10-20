# Tasks — fix-test-search-history-dispatcher

Parent specification for every task below: `unit-test-suite-runtime`, requirement "A test that builds a
time-hopped repository wires the hop" (this change's ADDED delta). The change touches no compiled file, so the
structural check of the files it edits stands in for a build (`guidelines/Build.md` §2, the scope block); the
verification that remains is the touched test classes themselves.

## 1. The red case: the rule becomes mechanical

- [x] 1.1 Add `app/src/test/java/com/naviveylin/test/SearchHistoryDispatcherRuleTest.kt` — the scan over every
      test-bearing module's `src/test/java`, the two allowed forms, the refused form, and the roots control.
      Parent spec: `unit-test-suite-runtime`, all five scenarios of the ADDED requirement. **Verify**: on the
      base commit `db1fe8e8` the rule case fails —
      `searchHistoryDispatcher roots=3 files=441 findings=96`, XML `tests="5" failures="1"` at
      `timestamp="2026-10-20T19:06:42.161Z"` — recorded in `evidence/red/TEST-com.naviveylin.test.SearchHistoryDispatcherRuleTest.xml`.
- [x] 1.2 Measure the scale the item states and the scale measured, and record both in `proposal.md`: 96
      construction sites in 65 test classes, against the item's "41 sites in `ui/map/`". **Verify**: the red
      XML's 96 `FINDING` lines, grouped per file.

## 2. The sweep

- [x] 2.1 Wire the seam at all 96 sites in 65 classes: `.also { it.defaultDispatcher = mainDispatcherRule.dispatcher }`
      where the class carries a `MainDispatcherRule`, `Dispatchers.Unconfined` in the seven Compose classes
      that carry none. Parent spec: scenarios "Every construction … is wired", "A chained wiring is accepted".
      **Verify**: 2.1a re-run of the guard reports `findings=0`; 2.1b `git diff --stat` shows only
      `app/src/test/java/**` (no main source, no build script); 2.1c no test method body changed — every hunk is
      a construction expression.
- [x] 2.2 The guard is not vacuous: its two allowed forms and its refused form are each exercised by their own
      control case, and the roots control fails if a module root disappears. Parent spec: scenarios "A
      construction that leaves the hop … is refused", "A chained wiring is accepted", "A property wired later …
      is accepted", "A module whose sources disappear fails the check". **Verify**: the five cases green in
      one run.

## 3. The guideline that owns the rule names the check

- [x] 3.1 Add one sentence to `guidelines/Build.md` §6 "Test-scheduling traps" naming
      `SearchHistoryDispatcherRuleTest` as the check that refuses an unwired hop. **Verify**: `grep -n
      'SearchHistoryDispatcherRuleTest' guidelines/Build.md` resolves, and the sentence names the seam, not a
      procedure.

## 4. Verification of the touched classes

- [x] 4.1 Run the touched test classes (the guard plus every swept class) in one forced invocation, then the
      project's gate once. **Verify**: focused run `:app:testMobileDebugUnitTest -PforceTests
      --no-build-cache` over `com.naviveylin.test.*`, `ui.map.*`, `ui.route.*`, `data.SearchHistoryRepositoryTest`,
      `navigation.*`, `di.CrossSurfaceSearchOrderTest` — 168 classes, 1 230 cases, 0 failures, ts
      `2026-10-20T19:15:35.016Z`..`19:16:45.894Z` — and the gate `./gradlew test -PforceTests --no-build-cache`
      — `tests=5 710 failures=0 errors=0 skipped=0` over 713 classes in four suites (`app` mobile 2 077,
      `app` automotive 2 077, `:auto` 1 508, `buildSrc` 48), ts `19:17:40`..`19:21:58` — in
      `evidence/green/gate-summary.txt` with the guard's and three swept classes' XMLs.
- [x] 4.2 Revert-check of the new invariant: unwire one swept site
      (`MapCanvasViewModelFollowModeTest:68` loses its `.also { … }`), the guard fails naming that file and
      line (XML `tests=5 failures=1`, ts `2026-10-20T19:28:01.465Z`, `evidence/red/revert-check-mutation.xml`),
      restore the wiring, re-run forced green (ts `2026-10-20T19:28:14.209Z`) together with the mutated class
      (`tests=24 failures=0`).
- [x] 4.3 Name the exclusion: nothing outside `:app`'s test sources changes, and the gate above runs every
      suite of every module anyway (`:auto`, `:core`, `osmscout-client-java`, `buildSrc`), so no suite is
      excluded; the seven touched Compose classes are covered by the same gate.

## 5. Evidence and archive

- [x] 5.1 Copy the red and green XMLs into `evidence/red/` and `evidence/green/` and into
      `.pi/issue-loop/items/78/`.
- [x] 5.2 `openspec validate fix-test-search-history-dispatcher --strict`, then archive the change and confirm
      the delta merged into `openspec/specs/unit-test-suite-runtime/spec.md`.
