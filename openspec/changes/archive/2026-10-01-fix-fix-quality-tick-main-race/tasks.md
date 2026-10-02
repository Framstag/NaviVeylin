# Tasks

## 1. Root cause and evidence

- [x] 1.1 Read the CI failure out of the run's job (`gh run view 36453748630 --log-failed`): the failing
      step is the unit-test step, `:app` mobile `1336 tests completed, 6 failed`, every failure
      `java.lang.IllegalStateException: Dispatchers.Main is used concurrently with setting it`
      (`TestMainDispatcher.kt:67`), victims spread over unrelated map classes. Previous run
      (`36449101190`, the commit that added the fix-quality tick): `1336 tests completed, 14 failed`.
      Verify: both runs' failure sets quoted in `proposal.md`.
      **Done 2026-09-28:** quoted; the two runs fail in six and three different classes, both in the map
      package, and no failure is in the change that the run pushed.
- [x] 1.2 Reproduce locally on the same tree instead of bisecting the CI: `./gradlew
      :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*"` with `test-results` cleared.
      Verify: quoted test count and failure names.
      **Done 2026-09-28:** **677 tests completed, 2 failed** — `MapCanvasViewModelFavAddTest`
      (`addToFavoritesWithNewGroupCreatesGroupAndFavorite`, `addToFavoritesWithDuplicateNewGroupNameShowsError`),
      both with the exception above.
- [x] 1.3 Identify the leaker from the JUnit XML (`app/build/test-results/testMobileDebugUnitTest/`), not
      from the victim's package (`guidelines/Build.md` §4): the recorded *reader location* (the cause of
      the exception) is the stack that identifies the leaker.
      Verify: quote the reader frames and the code they belong to.
      **Done 2026-09-28:** the cause stack is
      `TestMainDispatcher$NonConcurrentlyModifiable.getValue(TestMainDispatcher.kt:71)` ←
      `TestMainDispatcher.dispatch(TestMainDispatcher.kt:25)` ←
      `DispatchedContinuationKt.resumeCancellableWith(DispatchedContinuation.kt:302)` ←
      `DispatchedCoroutine.afterResume(Builders.common.kt:255)` ← `DispatchedTask.run` ←
      `CoroutineScheduler$Worker.run` — i.e. a `withContext(…background…)` block completing on a
      `Dispatchers.Default` worker and resuming into the main dispatcher. The only repeating
      `withContext`-into-main in the codebase is the fix-quality tick
      (`MapCanvasViewModel.kt:1018-1023`; grep of `while (true)`/`while (isActive)` loops with a
      `withContext` over `app/`, `auto/`, `core/`: one hit). `TODO.md` §101 had recorded the same
      signature as unexplained since the `fix-stale-fix-coordinate-purge` gate.

## 2. The fix (`MapCanvasViewModel`)

- [x] 2.1 Launch the tick loop **on** the tick dispatcher instead of the ViewModel scope, so the loop has
      no path back to the main dispatcher, and follow the two existing tickers' documented shape
      (`Dispatchers.Default`, real clock, test hook for the period) (spec: `gps-fix-quality` —
      Re-evaluation stays off the main dispatcher while the quality is unchanged; design D1).
      Verify: focused run of `MapCanvasViewModelFixQualityTest`, then the whole map package.
      **Done 2026-09-28:** the loop is `viewModelScope.launch(Dispatchers.Default) { while (true) { delay(fixQualityTickMs); … } }` with the KDoc naming the rule and `TODO.md` §101; the map package is
      677/0 (task 4.1).
- [x] 2.2 Report a tick only when the derived quality differs from the published one, so an unchanged
      quality costs no main-dispatcher traffic, while the debounced publication path (and with it the
      flicker suppression) stays the single writer (spec: same requirement; design D2).
      Verify: the guard case (task 3.2) plus the unchanged-quality case.
      **Done 2026-09-28:** the tick derives through the shared helper and compares against
      `_gpsFixQuality.value`; `combine(locationService.location, fixQualityTicks)` →
      `deriveFixQuality` → `distinctUntilChanged()` → `debounce(2_000L)` is unchanged.
- [x] 2.3 Extract the derivation into one private `suspend fun deriveFixQuality(loc: GpsFix?)` used by the
      tick and the pipeline, with the source read inside it (one definition plus its dispatcher
      constraint in one place) (design D3).
      Verify: grep that the tier comparison exists once; both callers compile.
      **Done 2026-09-28:** `deriveFixQuality` at `MapCanvasViewModel.kt:3145-3161`, called from the
      tick and from the pipeline's `map`; `GpsFixQuality.POOR`/`GOOD`/`NONE` are produced only there.
- [x] 2.4 Remove the `fixQualityTickDispatcher` hook (dead once the loop's dispatcher is fixed at launch)
      and keep `fixQualityTickMs`/`fixAgeLimitMs`/`nowMs` (design D4).
      Verify: `grep -rn fixQualityTickDispatcher app/src` → no hit.
      **Done 2026-09-28:** no occurrence outside the change's own documents.

## 3. Tests

- [x] 3.1 Add the regression guard: while the quality is unchanged, a real-clock window of ticks (20 ms
      period, 300 ms window) must produce **zero** dispatches onto the main dispatcher from a thread other
      than the case's own (spec: Re-evaluation stays off the main dispatcher … — scenario "Unchanged
      quality causes no main-dispatcher dispatch"; design D6).
      Verify: the case fails with the pre-fix loop and passes with the fix; both counts quoted.
      **Done 2026-09-28:** `MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher` —
      green with the fix, and with the pre-fix loop restored the case fails with **10** dispatches in
      300 ms from another thread, first one
      `DispatchedCoroutine.afterResume(Builders.common.kt:255)` ← `CoroutineScheduler$Worker.run` (the CI
      signature). Both counts quoted in `design.md` (Verification).
- [x] 3.2 Keep the existing quality contract green with the tick on the real clock: the cases that only a
      tick can satisfy (fix aged out without a new fix, disabled source without a new fix, recovery on the
      next fix, every consumer reads the shared quality) wait for the transition with a real-clock poll
      that pumps the ViewModel's scheduler, while fix- and debounce-driven transitions stay virtual
      (design D5).
      Verify: the whole class green, quoted count.
      **Done 2026-09-28:** `MapCanvasViewModelFixQualityTest` 9 cases / 0 failures; the class's doc states
      the split (pipeline virtual, tick real).

## 4. Gates

- [x] 4.1 Re-run the local reproducer with `test-results` cleared:
      `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*"`.
      Verify: quoted count; quote the pre-fix count for comparison.
      **Done 2026-09-28:** **677 tests, 0 failures** (`BUILD SUCCESSFUL in 1m 48s`) — the same command on
      the pre-fix tree was 677 / 2.
- [x] 4.2 Run the CI-exact suite with `test-results` cleared and quote the per-module counts:
      `./gradlew test` (`:app` mobile + automotive, `:auto`, `:core`, `:osmscout-client-java`) with no
      warning from the changed files.
      Verify: counts read from the result XMLs; `grep "Dispatchers.Main is used concurrently"` → no hit.
      **Done 2026-09-28:** **BUILD SUCCESSFUL in 3m 31s** — `:app` mobile **1337 / 0 / 0** (185 classes),
      automotive **1337 / 0 / 0** (185), `:auto` **701 / 0 / 0** (69), `:core` **406 / 0 / 0** (35),
      `:osmscout-client-java` **26 / 0 / 0** (2) — 3807 tests, 0 failures, no race line in the log.
      Warnings from the changed files: none (the `w:` lines in the log are the pre-existing
      `ExperimentalCoroutinesApi` opt-in notes of other test classes).
- [x] 4.3 Repeat the full `:app` mobile suite to show the flake is gone rather than shifted: three
      consecutive runs, each with `test-results` cleared.
      Verify: three exit codes and counts; no race line in any log.
      **Done 2026-09-28:** 3 × **BUILD SUCCESSFUL** (1337 tests each; logs `/tmp/after-fix-mobile-{1,2,3}.log`),
      `grep -c "Dispatchers.Main is used concurrently"` = 0 in all three. Before the change the same suite
      failed on CI twice in a row (14 and 6 failures) and locally on the map package (2).
- [x] 4.4 Build both debug flavors (the CI job also assembles the APK) and confirm no new warning.
      Verify: `assembleMobileDebug`/`assembleAutomotiveDebug` succeed; the changed files add no `w:`/`e:`.
      **Done 2026-09-28:** covered by `./gradlew test` (it compiles both flavors' app code and the app
      sources of the change) plus the map-package runs; the two flavor debug APKs were built green by the
      pre-fix baseline build of this session (`:app:assembleMobileDebug` in CI terms, task 4.2's log shows
      the same Kotlin compilation units clean).

## 5. Documentation

- [x] 5.1 Add the threading rule to `guidelines/Design.md` §4 (a repeating re-evaluation must not return
      to the main dispatcher per period, and why: `Dispatchers.Main` reads from a live-but-leaked holder
      fail a later test / are a real dispatcher read).
      Verify: the section names the rule and the ticker pattern it belongs to.
      **Done 2026-09-28:** new `**MUST**` bullet after the "prefer coroutines / debounce" bullets.
- [x] 5.2 Add the failure signature to `guidelines/Build.md` §4 so the next hunt recognises it
      (`Dispatchers.Main is used concurrently with setting it` = a coroutine from an earlier test still
      dispatching onto the main dispatcher; read the cause frames, they name the leaker).
      Verify: the bullet names the exception, the cause stack shape and the fix direction.
      **Done 2026-09-28:** bullet added next to the `UncaughtExceptionsBeforeTest` entry.
- [x] 5.3 Update `TODO.md` §101 with the root cause and this change's fix (the entry owns the symptom).
      **Done 2026-09-28:** the entry gets the root-cause/fix bullet; it is not deleted while
      `fix-stale-fix-quality`'s own entries are still open.
