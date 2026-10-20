# Fix: test sources that use the experimental coroutines-test time control declare the opt-in

## Why

Recount on base `3211d4d`, the four unit-test compile tasks of the issue's own evidence command
(`./gradlew :app:compileMobileDebugUnitTestKotlin :app:compileAutomotiveDebugUnitTestKotlin
:auto:compileDebugUnitTestKotlin :core:compileDebugUnitTestKotlin --rerun-tasks`, exit 0, log
`logs/head-compile-warnings.log`):

- 109 lines start with `w: ` — the issue's number, unchanged.
- 91 of them are one family: `This declaration needs opt-in. Its usage should be marked with
  '@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)'` — the issue's 91, unchanged.
- The per-file counts are exactly the issue's table, `uniq -c` over that log: `SearchScreenTest.kt` 39,
  `MapCanvasViewModelSearchHistoryTest.kt` 12, `RoutePanelViewModelSearchRankingTest.kt` 10,
  `DetailsScreenTest.kt` 8, `MapCanvasViewModelFreeTextSearchTest.kt` 8,
  `MapCanvasViewModelAutoZoomCommitTest.kt` 6, `MapCanvasViewModelSingleFollowCenterTest.kt` 4,
  `TemplateFaultIsolationTest.kt` 2, `MapCanvasViewModelRoadInfoTest.kt` 2.

**Root cause, one sentence.** At the pinned `kotlinx-coroutines-test:1.9.0`
(`app/build.gradle.kts:1542`, `auto/build.gradle.kts:170`, `core/build.gradle.kts:158`) the `TestScope`
extensions `advanceUntilIdle`, `advanceTimeBy` and `runCurrent` are `@ExperimentalCoroutinesApi`, and nine
test classes import and call them without declaring the opt-in, so the compiler reports one warning per call
site — 91 sites in nine files.

The rule the warnings break is `guidelines/Build.md` §4 "Result evaluation", the bullet *"Warnings: the build
must have no warnings — report them, don't ignore"* (the item's citation of `§7`, "Code coverage", does not
resolve to that rule). The defect contradicts no shipped-capability requirement — it is build hygiene — so the
requirement this change adds is a test-source rule in the capability that already owns "a check refuses a test
source pattern": `unit-test-suite-runtime`, requirement *"Wall-clock waits in test sources are refused by a
build check"* (`openspec/specs/unit-test-suite-runtime/spec.md:182`).

The issue asks for two things, and both are in this change: the declaration in the nine files, and a case that
refuses the tenth, because "a warning nobody reads is what let 91 of them accumulate".

## What changes

- Nine test classes gain the class-level `@OptIn(ExperimentalCoroutinesApi::class)` they need, matching the 70
  test files in the same three source sets that already carry it.
- A new JUnit case, `app/src/test/java/com/naviveylin/test/CoroutinesTestOptInGateTest.kt`, refuses a test
  source that imports a `kotlinx.coroutines.test` declaration outside the stable set without naming
  `ExperimentalCoroutinesApi`. It reads the three test-source trees the way
  `app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt` already reads the repo —
  private helpers over `File("..")`, no shell, no production code — so its red case can exist on HEAD.
- Spec delta: one ADDED requirement and two scenarios in `unit-test-suite-runtime`.

## Impact

Affected files (no production code, no build file, no native source, no manifest):

- `auto/src/test/java/com/naviveylin/auto/SearchScreenTest.kt`
- `auto/src/test/java/com/naviveylin/auto/DetailsScreenTest.kt`
- `auto/src/test/java/com/naviveylin/auto/TemplateFaultIsolationTest.kt`
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelSearchHistoryTest.kt`
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFreeTextSearchTest.kt`
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelAutoZoomCommitTest.kt`
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelSingleFollowCenterTest.kt`
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelRoadInfoTest.kt`
- `app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelSearchRankingTest.kt`
- `app/src/test/java/com/naviveylin/test/CoroutinesTestOptInGateTest.kt` (new)
- `openspec/specs/unit-test-suite-runtime/spec.md` (via the delta)

No behaviour changes: an opt-in declaration changes no bytecode of the annotated class beyond an annotation,
and the guard only reads files. Additive, not breaking. Rollback: `git revert` of the change commit — the
spec delta is the only requirement touched, and it is ADDED, so removing it restores the previous contract.

## Previous specifications affected

`unit-test-suite-runtime` gains a requirement; no existing requirement is modified, and the wall-clock
requirement beside it is untouched.
