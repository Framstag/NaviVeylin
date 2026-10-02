# Proposal — fix-fix-quality-tick-main-race

## Why

The fix-quality tick introduced by `fix-stale-fix-quality` runs its delay on the tick dispatcher but
keeps its loop on the **main** dispatcher
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1018-1023`):

```kotlin
viewModelScope.launch {                                   // main dispatcher (the loop's home)
    while (true) {
        withContext(fixQualityTickDispatcher) { delay(fixQualityTickMs) }
        fixQualityTicks.value = fixQualityTicks.value + 1L
    }
}
```

Every tick therefore returns to the main dispatcher, and the increment of `fixQualityTicks` wakes the
combined quality collector, which also lives on the main dispatcher: a ViewModel that is still ticking
reads `Dispatchers.Main` from a real thread pool once per second. Unit tests build one ViewModel per
case and only cancel the *current* instance, so replaced instances (and any class that forgets the
teardown) keep ticking — and `kotlinx-coroutines-test` records such a read as a *concurrent use* of the
main dispatcher while a later test's `MainDispatcherRule` replaces it. The failure surfaces in an
**unrelated** class, in the rule, not in the leaker:

```
java.lang.IllegalStateException: Dispatchers.Main is used concurrently with setting it
	at kotlinx.coroutines.test.internal.TestMainDispatcher$NonConcurrentlyModifiable.concurrentRW(TestMainDispatcher.kt:67)
	at kotlinx.coroutines.test.internal.TestMainDispatcher$NonConcurrentlyModifiable.getValue(TestMainDispatcher.kt:73)
	at kotlinx.coroutines.test.internal.TestMainDispatcher.dispatch(TestMainDispatcher.kt:25)
	at kotlinx.coroutines.DispatchedCoroutine.afterResume(Builders.common.kt:255)   // a withContext completing
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:707)
```

Observed 2026-09-28, upstream `main`:

- CI run `36449101190` ("Make GPS fix quality age out instead of latching at its last tier", the commit
  that added the tick): `:app` mobile `1336 tests completed, 14 failed`, victims spread over six map
  classes. CI run `36453748630` (the submodule bump that follows): `1336 tests completed, 6 failed`
  (`ApproximateLocationMapParityTest`, `MapCanvasViewModelBrowseReCenterTest`,
  `MapCanvasViewModelVehicleAnchorTest`). Both runs: the failing step is the unit-test step of
  `Build Debug APK`.
- Reproduced locally on the commit of run `36453748630` with
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*"` → **677 tests, 2 failed**
  (`MapCanvasViewModelFavAddTest`, both failures this same exception) — the full stack in the JUnit XML
  names the reader (the leaker) as a `withContext` completion resuming into the main dispatcher from a
  `Dispatchers.Default` worker.
- `TODO.md` §101 has recorded the same signature since the gate of
  `fix-diagnostics-stale-coordinate-purge` ("`Dispatchers.Main is used concurrently with setting it`
  thrown in `MainDispatcherRule.starting` … a **new symptom** for this family … one more thing for the
  bisection in the fix candidate below to look for"). This change closes that hunt: the leaker is not a
  sibling test, it is this tick, and it exists in every case that builds a `MapCanvasViewModel`.
- The tick is the **only** repeating `withContext` back onto the main dispatcher in the app, `:auto` or
  `:core` (grep of `while (true)`/`while (isActive)` loops with a `withContext`: one hit, this one). The
  two existing tickers (stale speed in the ViewModel and in `NavigationEngine`) run their whole loop on
  `Dispatchers.Default` with the real clock, exactly as `TODO.md` §40.C.16 prescribes.

Why now: upstream `main` is red — the unit-test step of the CI job is what fails, and every subsequent
push keeps failing for a reason that has nothing to do with the change being pushed.

## What Changes

- The tick loop lives **wholly** on `Dispatchers.Default` (its home dispatcher, like the two existing
  tickers) and never returns to the main dispatcher.
- The tick reports a **change only**: it derives the quality itself (the same derivation the pipeline
  uses, extracted into one private helper) and increments the tick counter only when the derived value
  differs from the published one. While the quality is unchanged the tick therefore costs no
  main-dispatcher traffic at all — which is what makes a leaked ViewModel harmless — while a real
  change (fix aged out, location services switched off) is still detected within one tick and published
  through the existing debounced path.
- The single publication path is unchanged: `combine(locationService.location, fixQualityTicks)` →
  derivation → `distinctUntilChanged()` → `debounce(2_000L)` → `_gpsFixQuality` / `uiState`. The
  debounce that suppresses flicker (a hovering accuracy flipping GOOD/POOR) is untouched, and the tick
  still feeds it rather than publishing around it.
- The `fixQualityTickDispatcher` test hook is **removed**: with the loop's home fixed to the production
  dispatcher, a hook set after construction can no longer move it (the loop captures its dispatcher at
  launch), and dead test surface is worse than none. `fixQualityTickMs`, `fixAgeLimitMs` and `nowMs`
  stay.
- Regression guard: `MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher` counts
  dispatches that reach the main dispatcher **from another thread** while the quality is unchanged and
  requires zero. With the pre-fix loop it fails with 10 of them in 300 ms, from the very
  `DispatchedCoroutine.afterResume` stack the CI failures show.
- No UI, string, resource, permission, manifest, native/JNI or DI change; no behaviour change for a
  user-visible surface (the quality contract and the debounce stay exactly as `fix-stale-fix-quality`
  specified them).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gps-fix-quality` (the capability added by `fix-stale-fix-quality`, not yet archived): the
  re-evaluation requirement gains the threading contract it must satisfy — while the derived quality
  equals the published one, the re-evaluation must not dispatch on the main dispatcher, while a real
  change must still reach consumers through the one debounced publication path. Testable as stated
  (dispatch count on the main dispatcher from another thread while the quality is unchanged).

## Impact

Affected code, by module:

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — the tick loop (`:1018-1023`), the
  `fixQualityTickDispatcher` hook (`:380-387`, removed), the fix-quality pipeline (`:1031-1066`), and a
  new private `suspend fun deriveFixQuality(loc: GpsFix?): GpsFixQuality` shared by the tick and the
  pipeline (`:3143`).
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFixQualityTest.kt` — the case bodies that
  asserted a tick-driven transition now wait for it with a real-clock poll (the tick is on the real
  clock; the pipeline stays on the test scheduler), plus the new guard case and a counting main
  dispatcher.
- Guidelines: `guidelines/Design.md` §4 gains the threading rule (a repeating re-evaluation must not
  return to the main dispatcher per period); `guidelines/Build.md` §4 gains the failure signature so the
  next hunt recognises it. `guidelines/UI.md`, `MapRendering.md`, `Regulatory.md`: unaffected.
- `TODO.md` §101 — the entry that recorded the symptom as unexplained gets its root cause and fix.
- Android components: none. No manifest, permission, resource, navigation graph or Compose change; the
  car surfaces are not involved (`GpsFixQuality` exists only in the phone map screen).
- Native/JNI: none. Kotlin-only change — no libosmscout submodule patch, no `:osmscout-client-java`
  override, no ABI/NDK implication.
- Previous specifications changed: `gps-fix-quality` (added requirement in this change's delta). The
  requirements of `fix-stale-fix-quality` — tiers, age window, consumers — are untouched.

Classification and rollback: **additive** (a threading contract and its guard; no API, storage, UI or
behaviour contract changes). Rollback is a revert of the tick loop shape and the guard case in one
commit — nothing persisted, no migration.

Scope: phone `MapCanvasViewModel` only; the Android Auto / AAOS surfaces and the engine are unchanged.
Explicit non-goal: fixing the general "a leaked ViewModel keeps ticking" test-hygiene question (the
teardown gaps of individual test classes). This change makes the leak *harmless* rather than
enumerating its instances; the `RendererTestRule` pattern (`guidelines/Build.md` §6) stays the tool for
background work that is harmful when leaked.
