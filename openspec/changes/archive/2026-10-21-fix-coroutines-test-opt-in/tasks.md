# Tasks — fix-coroutines-test-opt-in

## 1. The guard, written first

- [x] 1.1 Write `app/src/test/java/com/naviveylin/test/CoroutinesTestOptInGateTest.kt` with the detector
      (import-level, stable set `{runTest, TestScope, TestDispatcher, StandardTestDispatcher,
      TestCoroutineScheduler}`), the fixture case
      `aTestSourceThatUsesTheExperimentalTimeControlWithoutTheOptInIsNamed` and the real-tree case
      `everyTestSourceThatUsesTheExperimentalTimeControlDeclaresTheOptIn` over `app/src/test/java`,
      `auto/src/test/java`, `core/src/test/java` — spec `unit-test-suite-runtime`, "A test source declares the
      opt-in its experimental time control needs", scenarios 1 and 2.
      *Verification*: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.test.CoroutinesTestOptInGateTest"`
      → the five self-test cases pass, the real-tree case fails naming the nine files (`tests="6" failures="1"`);
      its XML copied to `evidence/` as the red run.

## 2. The declaration

- [x] 2.1 Add `import kotlinx.coroutines.ExperimentalCoroutinesApi` and the class-level
      `@OptIn(ExperimentalCoroutinesApi::class)` to the nine classes the red run names, in the order
      `auto/SearchScreenTest`, `auto/DetailsScreenTest`, `auto/TemplateFaultIsolationTest`,
      `app/ui/map/MapCanvasViewModelSearchHistoryTest`, `app/ui/map/MapCanvasViewModelFreeTextSearchTest`,
      `app/ui/map/MapCanvasViewModelAutoZoomCommitTest`, `app/ui/map/MapCanvasViewModelSingleFollowCenterTest`,
      `app/ui/map/MapCanvasViewModelRoadInfoTest`, `app/ui/route/RoutePanelViewModelSearchRankingTest`.
      *Verification*: the same filtered case turns green; XML to `evidence/`.
- [x] 2.2 Recompile the four unit-test compile tasks and compare the families.
      *Verification*: `./gradlew :app:compileMobileDebugUnitTestKotlin
      :app:compileAutomotiveDebugUnitTestKotlin :auto:compileDebugUnitTestKotlin
      :core:compileDebugUnitTestKotlin --rerun-tasks` → 0 missing-opt-in warnings and 18 warnings in total
      (was 91 of 109), log to `evidence/`.

## 3. Falsification of the new invariant

- [x] 3.1 Revert-check the real-tree case (`revert-check` skill): delete the `@OptIn` line from
      `MapCanvasViewModelRoadInfoTest.kt`, the case must fail and name that file, restore the line, re-run
      forced green.
      *Verification*: the failing and the restored run's XML in `evidence/`, and
      `revert-checked.md` recording the mutation and the case that noticed it.

## 4. Owning documents

- [x] 4.1 Confirm the owning rule needs no edit: `guidelines/Build.md` §4's *Warnings* bullet already states
      "the build must have no warnings", and `unit-test-suite-runtime` is the capability that owns a
      test-source refusal. Structural check, no build: `grep -n "Warnings" guidelines/Build.md` and the delta's
      requirement text must agree; no guideline line is added or rewritten.

## 5. Independent review, then the gate, once

- [x] 5.0 Read-only review by a fresh child against the spec delta, the diff and the evidence XMLs. Two MAJORs:
      the marker scan accepted a comment or a string literal, and one scenario promised a compile observation the
      case cannot make. Both fixed (masking plus two self-test cases; the scenario's THEN narrowed to what the
      case asserts), and the guard file re-verified with the filtered runs.
- [x] 5.1 Run `./gradlew test -PforceTests --no-build-cache` exactly once and copy every cited XML into
      `evidence/`.
      *Verification*: the run's `tests=`/`failures=` per module quoted in the change report; a second attempt
      is not part of this change.
