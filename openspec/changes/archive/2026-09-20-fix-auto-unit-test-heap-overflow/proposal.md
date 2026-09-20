# Proposal

## Why

The `:auto` unit-test suite dies with `java.lang.OutOfMemoryError` when it runs as one JVM
(`TODO.md` §33, observed twice: 2026-09-19 during `aa-entry-zoom-animation` and again during
`map-marker-route-contrast`), and the failure is unattributable: dozens of unrelated classes fail
with bare `OutOfMemoryError`, **no per-class result XML is written**, and the run looks like a mass
regression instead of a memory limit. Today's workaround is to run the module in four manual class
batches, which also blocks the coverage invocations (`:koverHtmlReport :koverXmlReport` re-run the
same tests, §40.8).

**Root cause found by analysis (2026-09-20) — it is a test-side leak, not the heap ceiling:**

- `AutoMapRenderer` starts its background work in `init`
  (`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt:283-285` → `startRenderLoop()`,
  `startExtrapolationLoop()`), and only `shutdown()` (`:677-692`) ends it (`scope.cancel()`, all
  three jobs cancelled, surface released, overrun buffer cleared).
- The `:auto` renderer tests construct one renderer per test method and **never shut it down**:
  `AutoMapRendererTest.kt` 70 tests (`@Before` :35-39) with **0** `@After`, `MapPanHandlerTest.kt`
  13 tests (`@Before` :24-26) with 0, `FreeDrivingScreenTest.kt` (:134) and `RendererGateTest.kt`
  (:32, :92) with 0. ≈108 leaked renderers per single-JVM run.
- Each leaked renderer keeps alive two coroutines on `Dispatchers.Default` — the extrapolation loop
  is `while (isActive) { if (isShutdown) break; …; delay(EXTRAPOLATION_FRAME_MS) }` (`:758-780`), so
  it ticks at ~30 Hz for the rest of the JVM run — plus the `Surface`, the state flows and, for every
  test that rendered, the 1296×720 `overrunBitmap` created in `MapRenderUtil.renderToBitmap`
  (`:1175`, 3.73 MB) with a further transient `int[1296*720]` per render.
- `asyncLoopsEnabled = false`, which many of those tests set (e.g. `:234`, `:255`, …), does **not**
  stop it: that branch delays and `continue`s without ever breaking out of the loop.
- **Contrast that proves the ceiling is not the defect:** `:app` runs 1054 tests in one JVM at the
  default AGP fork heap of 512 MB and is green, because its map tests release their components in
  `@After` (e.g. `MapCanvasViewModelDarkModeTest.kt:69-72` → `cancelScopeForTest()`). `:core` (302)
  is green as well. Only `:auto` leaks.

**Side effect of the leak beyond the OOM:** dead renderers keep doing viewport math and render/blit
work on `Dispatchers.Default` while later `runTest` classes execute, which is a plausible cause of
the intermittent `/auto` flakes recorded in the same module (§18
`AutoMapRendererTest.marginRenderRequestHonoursThrottleParity`, the one-off
`ComposeTimeoutException`s in §33). This is a hypothesis to re-check in this change, not a claim.

**Why now:** the test gate itself is degraded — §17 requires the result XMLs as evidence, and the
batching workaround makes every `:auto` verification expensive, error-prone (Gradle cleans
`test-results` on each invocation, so per-batch XMLs must be copied out) and unrepresentative
(the suite is never exercised as it runs for a developer).

## What Changes

- **A — Test-side teardown (the fix).** Every `:auto` test that constructs a component owning its own
  scope, timers, render loop, native surface or retained bitmap releases it before the test method
  returns. A shared JUnit4 rule/tracker in `:auto` test sources creates and tracks those instances,
  shuts them down after the test, and **fails the test** if any of the component's background jobs is
  still active, so a forgetting test cannot leak silently or come back.
- **B — Declared, bounded fork budget.** `:auto` declares its unit-test fork heap and fork cadence in
  `auto/build.gradle.kts` (`testOptions { unitTests { … } }`) instead of inheriting the AGP default of
  512 MB, with a measurement rule that decides whether the fork cadence can be dropped. **`:app` declares
  the same kind of budget for the same reason** (see the expanded scope below).
- **C — Verification.** The `:auto` suite runs in **one** invocation at the declared budget with one
  result XML per class and no `OutOfMemoryError`; the coverage invocations complete in one invocation
  as well; the manual class batching is retired from the test procedure and kept only as a
  diagnostic fallback. The previously flaky classes are re-run to check the leak hypothesis.
- **Documentation.** `guidelines/Build.md` gains the teardown rule and the declared `:auto`/`:app` fork
  budgets; `TODO.md` §33 is closed with the measured numbers, §43 (found while verifying this change) with
  its measured mechanism, and §40.8's mandatory batching is demoted.
- **Not BREAKING.** No production API, resource, manifest, template or Gradle task surface changes.
  The single production edit is one `internal` accessor reporting whether the component's background
  jobs are active, following the file's existing test-accessor convention (`renderFrame`,
  `advanceZoomWalk`, `setBlitOffsetForTest`, `overrunSize`).
- **Rollback:** remove the rule/teardown wiring and the `testOptions` block → the previous behaviour
  (512 MB default, manual batching) returns. The `internal` accessor can remain or be removed
  independently; nothing else depends on it.

## Capabilities

### New Capabilities

- `unit-test-suite-runtime`: a module's JVM unit-test suite runs to completion in a single invocation
  inside a declared, bounded fork budget, with one result XML per executed class; and no background
  work, retained bitmap or native surface created by a test stays alive after that test.

### Modified Capabilities

None. `test-coverage`, `kover-aggregate-report` and `ci-unit-test-jni` keep their requirements
unchanged: the new capability only adds that the *same* suite completes in one invocation when the
coverage agent is attached, it does not change what is measured, how it is aggregated, or how the
committed JNI stub is resolved. No requirement is invented elsewhere to satisfy validation.

## Impact

**`:auto` test sources**

- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt` — 70 tests, `@Before` :35-39, six
  in-test `renderer.shutdown()` calls (:86, :271, :297, :1578, :1696) that must stay valid, so the
  teardown must be idempotent.
- `auto/src/test/java/com/naviveylin/auto/MapPanHandlerTest.kt` (`@Before` :24-26).
- `auto/src/test/java/com/naviveylin/auto/RendererGateTest.kt` (`spyk(AutoMapRenderer(...))` :32, :92).
- `auto/src/test/java/com/naviveylin/auto/FreeDrivingScreenTest.kt` (`spyk(AutoMapRenderer(...))` :134).
- New: `auto/src/test/java/com/naviveylin/auto/RendererTestRule.kt` (tracker + rule) and its own test.

**`:auto` production source**

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — one `internal` accessor (active
  background job count) next to the existing test accessors. No behaviour change.

**Build**

- `auto/build.gradle.kts` — `testOptions { unitTests { isIncludeAndroidResources = true; all { … } } }`
  with the declared heap ceiling and fork cadence. No change to `:app`, `:core`, `buildSrc`, the
  license tasks, the flavors or the ABIs.

**Documentation / TODO**

- `guidelines/Build.md` §6 "Test constraints" — new subsection: the test-teardown rule, why it exists
  (this defect), and the declared `:auto` fork budget with the measurement that justifies it.
- `TODO.md` §33 (close with root cause, fix and measured numbers), §40.8 (batching becomes
  diagnostic-only), §18 (record the flake re-check result).
- Local, gitignored (`.pi/` is in `.gitignore`, so these are not committed):
  `.pi/skills/run-tests/SKILL.md` and `.pi/skills/run-tests/scripts/run-auto-batches.sh`.

**Guidelines:** `guidelines/Build.md` (test execution + evidence rules) is the only affected
document. `UI.md`, `MapRendering.md` and `Design.md` are not affected — no UI, render-pipeline or
architecture change; the threading model is unchanged (the test teardown runs on the test thread and
merely cancels the component's own scope).

**Scope:** build/test infrastructure, all modules and both flavors. `:auto` needed the teardown fix;
`:app` needed only the declared budget (its renderer tests already release their components — measured,
see the design D6 measurement). Whether other modules share the leak class was checked and, where one was
found outside the fixed modules, recorded in `TODO.md` (§42) rather than fixed here.

## Expanded during apply (2026-09-20, owner decision)

Verifying this change (task 3.3) surfaced a **pre-existing, now deterministic `:app` failure**:
`FavoritesSheetReorderComposeTest > chips follow the reordered favorites and a new star appends` fails with
`ComposeTimeoutException: Condition still not satisfied after 5000 ms` inside any full `:app` suite run while
passing 11/11 alone. It is *not* caused by this change (the same `:app`-alone command fails identically with
the change stashed, and no `:auto`/`:core` test task runs in that command).

The owner chose to fix it in this change. Measurement: with the fork heap at the plugin default (512 MB) the
class fails deterministically (146 classes / 1054 tests in one fork); at 1024 MB the whole `:app` suite is
green (`:app:testAutomotiveDebugUnitTest --rerun`: 146 classes, 1054 tests, 0 failures, 1m49s), and at
2048 MB likewise (2m06s). So the flake is a **resource-budget defect of the module suite** — the test's 5 s
Compose wait expires while the JVM is under memory pressure — not cross-class state and not a leaked
component in `:app` (its `MapRenderer` tests already call `shutdown()`; the `:app` scope scan found no
comparable leak, `TODO.md` §42 is the one remaining hygiene item). The fix is therefore the same shape as B:
declare a measured `:app` fork budget.

This expands the change's scope to `app/build.gradle.kts` (+ the `:app` suite verification and the
`TODO.md` §43 record) and adds capability scenarios for `:app`; it does not change `:app` test semantics and
does not touch `:app` production code.
