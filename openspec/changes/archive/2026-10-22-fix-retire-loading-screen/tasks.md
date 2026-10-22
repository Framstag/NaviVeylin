# Tasks

Ordered. Each task names the verification that closes it; nothing is ticked on a cached or partial run.

- [x] 1. Root cause and the red test (Phase A). Verified the entry against the tree: the only constructor site
  of `LoadingScreen` outside `SafeScreen.kt:165` is `StartupScreensTest.kt:66`, its strings have no other
  consumer, and `NavigationSession.kt:369-373` records that the root screen is deliberately not a transient
  one. Two cases written first and run against the base commit `9aac52b`:
  `StartupScreensTest.retiredLoadingScreenClassIsGone` and
  `GermanTranslationCompletenessTest.retiredWaitNoticeStringsAreGone`, both `FAILED` —
  `tests="7" failures="1"` and `tests="3" failures="1"`, `EXIT=1`
  (`evidence/red-HEAD.log`, `evidence/TEST-com.naviveylin.auto.StartupScreensTest.xml`,
  `evidence/TEST-com.naviveylin.auto.GermanTranslationCompletenessTest.xml`, 2026-10-22T10:33:39Z).
  Verification: the two XMLs above; the other 8 cases in the same run are green.

- [x] 2. `auto/src/main/java/com/naviveylin/auto/SafeScreen.kt` — `LoadingScreen` and its KDoc deleted.
  Verification: `retiredLoadingScreenClassIsGone` green.

- [x] 3. `auto/src/main/res/values/strings.xml` and `values-de/strings.xml` — `loading_map_data` and
  `preparing_navigation` deleted. Verification: `retiredWaitNoticeStringsAreGone` green, and
  `GermanTranslationCompletenessTest.everyEnglishStringHasGermanTranslation` still green (the completeness
  direction it checks is unaffected by deleting a key on both sides).

- [x] 4. `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — the three comments that named the
  removed class (`:66`, `:369`, `:1176`) describe the mechanism instead.
  Verification: `grep -rn "LoadingScreen" auto/src` matches only the case that asserts its absence.

- [x] 5. `auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt` — `loadingScreenShowsLoadingState`
  removed (it constructed the deleted class) and its KDoc reference dropped.
  Verification: the whole `:auto` module suite compiles and is green in task 6.

- [x] 6. The affected cases, focused, then the project's gate once — this delta touches compiled files
  (`*.kt`, `*.xml`), so it owes the build gate.
  Verification: `./gradlew :auto:testDebugUnitTest` → `tests=877 failures=0 errors=0`, `EXIT=0`
  (`evidence/focused-green-module.log`); then `./tools/loop/gate.sh` →
  `LOOP-GATE: tests=481 failures=0 (…)`, `EXIT=0` (`evidence/gate.log`, started 2026-10-22T10:35:20Z), with
  the gradle half of the same gate at 5 769 tests / 0 failures across `app` (574 XMLs), `auto` (86),
  `core` (60) and `osmscout-client-java` (8).

- [x] 7. Falsification (`revert-check`): `LoadingScreen` restored alone in `SafeScreen.kt` —
  `retiredLoadingScreenClassIsGone` went red (`FAILED`, `EXIT=1`, `evidence/falsification-mutant-red.log`) —
  then the tree restored and the two cited classes green again (`BUILD SUCCESSFUL`, `EXIT=0`,
  `evidence/falsification-restored-green.log`).

- [x] 8. Evidence and archive: both cited XMLs copied into the change's `evidence/`, the change archived, the
  delta confirmed merged into `openspec/specs/route-calculation-feedback/spec.md`.
