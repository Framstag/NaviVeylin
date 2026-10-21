# Tasks — fix-test-search-history-dispatcher

Parent specification for every task below: `unit-test-suite-runtime`, requirements "A test that builds the
search-history repository wires its I/O hop" and "An unwired search-history hop is refused from the sources
alone" (this change's ADDED delta). The change touches no production file; its compiled delta is test sources,
so the verification is the touched test classes themselves plus the project's gate (`guidelines/Build.md` §2,
the scope block).

## 1. The red case: the rule becomes mechanical

- [x] 1.1 Add `app/src/test/java/com/naviveylin/test/SearchHistoryDispatcherRuleTest.kt` — the scan over every
      test-bearing module's `src/test/java`, the two allowed forms, the refused form, and the roots control.
      Parent spec: `unit-test-suite-runtime`, the five scenarios of the two ADDED requirements. **Verify**: on the
      base commit `db1fe8e8` the rule case fails —
      `searchHistoryDispatcher roots=3 files=441 findings=96`, XML `tests="5" failures="1"` at
      `timestamp="2026-10-20T19:06:42.161Z"` — recorded in `evidence/red/TEST-com.naviveylin.test.SearchHistoryDispatcherRuleTest.xml`.
      The message text inside that XML still quotes the guideline pointer it was produced with (§6); the case's
      string is the corrected one (task 3.1).
- [x] 1.2 Measure the scale the item states and the scale measured, and record both in `proposal.md`: 96
      construction sites in 65 test classes, against the item's "41 sites in `ui/map/`". **Verify**: the red
      XML's 96 `FINDING` lines, grouped per file.

## 2. The sweep

- [x] 2.1 Wire the seam at all 96 sites in 65 classes: `.also { it.defaultDispatcher = mainDispatcherRule.dispatcher }`
      where the class carries a `MainDispatcherRule`, `Dispatchers.Unconfined` in the seven Compose classes
      that carry none. Parent spec: scenarios "Every construction … is wired", "A chained wiring is accepted".
      **Verify**: 2.1a re-run of the guard reports `findings=0`; 2.1b the diff carries no main source, no build
      script, no manifest and no native file — its non-test files are `guidelines/Build.md` and `openspec/**`;
      2.1c no test method body changed — every hunk is
      a construction expression.
- [x] 2.2 The guard is not vacuous: its two allowed forms and its refused form are each exercised by their own
      control case, and the roots control fails if a module root disappears. Parent spec: scenarios "A
      construction that leaves the hop … is refused", "A chained wiring is accepted", "A property wired later …
      is accepted", "A module whose sources disappear fails the check". **Verify**: the five cases green in
      one run.

## 3. The guideline that owns the rule names the check

- [x] 3.1 Add one sentence to `guidelines/Build.md` §4 "Result evaluation", inside its test-scheduling-traps
      bullet, naming
      `SearchHistoryDispatcherRuleTest` as the check that refuses an unwired hop. **Verify**: `grep -n
      'SearchHistoryDispatcherRuleTest' guidelines/Build.md` resolves (`:471`), the sentence sits under `## 4.`
      (`:318`) and not under `## 6. Test constraints` (`:576`), and it names the seam, not a procedure.

## 4. Verification of the touched classes

- [x] 4.1 Run the touched cases (the guard plus one swept class) in one forced invocation, then the project's
      gate once. **Verify**: `:app:testMobileDebugUnitTest -PforceTests --no-build-cache --tests
      "com.naviveylin.test.SearchHistoryDispatcherRuleTest" --tests
      "com.naviveylin.ui.map.MapCanvasViewModelFollowModeTest"` — 29 cases, 0 failures, ts
      `2026-10-20T19:38:47.027Z` and `19:38:47.700Z` — and the gate `./gradlew test -PforceTests
      --no-build-cache` (round 2, `BUILD SUCCESSFUL in 3m 50s`) — `tests=5 710 failures=0 errors=0
      skipped=0` over 713 classes in five module tasks (`app` mobile 2 077, `app` automotive 2 077, `:auto` 866,
      `:core` 642, `:osmscout-client-java` 48), ts `19:39:02`..`19:42:49` — in
      `evidence/green/gate-summary.txt` with the guard's and three swept classes' XMLs (ts
      `19:39:22`..`19:40:30`).
- [x] 4.2 Revert-check of the new invariant: unwire one swept site
      (`MapCanvasViewModelFollowModeTest:68` loses its `.also { … }`), the guard fails naming that file and
      line (XML `tests=5 failures=1`, ts `2026-10-20T19:38:31.656Z`, `evidence/red/revert-check-mutation.xml`),
      restore the wiring, re-run the same two classes forced: guard `tests=5 failures=0`
      (`19:38:47.027Z`, `evidence/green/restore-check-green.xml`) and the mutated class `tests=24 failures=0`
      (`19:38:47.700Z`, `evidence/green/restore-check-green-followmode.xml`).
- [x] 4.3 Name the exclusion: nothing outside `:app`'s test sources changes, and the gate above runs every
      suite of every module the change could reach (`:app` both flavours, `:auto`, `:core`,
      `:osmscout-client-java`), so no suite is excluded; the seven touched Compose classes are covered by the
      same gate. `buildSrc` has tests of its own that the gate command does not run (`buildSrc/build/` holds no
      `test-results`), and the change touches no file `buildSrc` compiles.

## 5. Evidence and archive

- [x] 5.1 Copy the red and green XMLs into `evidence/red/` and `evidence/green/` and into
      `.pi/issue-loop/items/78/` — `evidence/red/` (`TEST-…SearchHistoryDispatcherRuleTest.xml`,
      `revert-check-mutation.xml`), `evidence/green/` (`gate-summary.txt`, four class XMLs,
      `restore-check-green.xml`, `restore-check-green-followmode.xml`). Every one of them is byte-identical in
      both trees; `items/78/` keeps the original red run under `red-searchhistorydispatcher.xml` because that
      directory holds one file per name and the guard's class name is used by the green copy too.
- [x] 5.2 Validate the spec, then archive the change and confirm
      the delta merged into `openspec/specs/unit-test-suite-runtime/spec.md`. **Verify**: `openspec validate
      unit-test-suite-runtime --type spec --strict` reports one warning, `requirements[7]` ("A Compose case
      starts from a repaired sandbox", pre-existing text long before this change) and none for either added
      requirement; the CLI does not address an archived change by name, so the delta was validated through the
      merged capability. Merged text is identical to the delta's two requirements apart from the header.
