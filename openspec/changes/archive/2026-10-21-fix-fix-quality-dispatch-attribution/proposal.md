# fix-fix-quality-dispatch-attribution

## Why

**Symptom (issue #87).** In a forced focused run on 2026-10-06 the case
`MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher` failed with

> the fix-quality tick dispatched on the main dispatcher from another thread **1** times in the second
> 300 ms window while nothing changed (first window: 1)

while the dispatch it printed came from `NavigationEngine.tickStaleness` → `runStaleSpeedTicker` — a
ticker of a different subsystem. The case was green on its immediate rerun and in both-flavor gates
(1717 tests, 0 failures each); the observed rate was 2/4 focused runs. A reader of a red focused run
takes that message for a regression of the change being gated, which is the silent-retry case rule §38
forbids.

**Root cause (one mechanism).** `CountingMainDispatcher.dispatch`
(`app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFixQualityTest.kt:364-370` at base `2e732f0`)
increments its counter for **every** dispatch any non-test thread sends to `Dispatchers.Main` — the whole
process's main dispatcher — and `aTickDoesNotDispatchOnTheMainDispatcher` (`:315-338`) asserts that count
over a six-tick window is zero and prints it as *the fix-quality tick's* dispatch. The count is therefore
not the quantity the requirement names: another subsystem's main-dispatcher traffic (a live
`NavigationEngine` stale-speed ticker, a `viewModelScope` collector another thread writes) lands in the
same number, and the case fails while blaming the re-evaluation.

**Red test on HEAD** (`2e732f0`). On HEAD the count and the assertion are the same number, so the defect is
made deterministic by supplying the unrelated traffic the case is not about: one dispatch onto the
instrumented main dispatcher per window, issued from a coroutine on `Dispatchers.IO` (neither the test
thread nor the re-evaluation's thread). Run
`./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache -PnoCoverage --tests
"com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest"` (2026-10-21T09:37:19Z, `BUILD FAILED`) →
`tests="9" failures="1"`, case `aTickDoesNotDispatchOnTheMainDispatcher`, message
`java.lang.AssertionError: the fix-quality tick dispatched on the main dispatcher from another thread 1
times in the second 300ms window while nothing changed (first window: 1) …` with the dispatch's stack
rooted in the case's own noise coroutine. That is the item's message, reproduced deterministically.

**The single fix.** Attribute every counted dispatch by the stack that issued it: frames of
`com.naviveylin.ui.map.MapCanvasViewModel`'s own code are the re-evaluation's traffic (asserted zero,
over three tick windows rather than two), everything else is another subsystem's and is never blamed on
the re-evaluation. The case supplies its own unrelated traffic so the control runs on every pass and
asserts the counter attributed it to another subsystem; the positive direction — a dispatch the ViewModel
issues is attributed to it — is recorded by the falsification run, not by a passing run.

**Owning requirement.** `gps-fix-quality` — "Re-evaluation stays off the main dispatcher while the quality
is unchanged" (`openspec/specs/gps-fix-quality/spec.md:12-26`), refined by this change with the
attribution scenario and the bounded sequence (N ≥ 3 windows).

**Owning guideline section.** `guidelines/Build.md` §6 "Test constraints" (a red run is diagnosed from the
failure's message; wall-clock waits are refused; a thread-identity assertion compares the `Thread`, not
its name) and `Design.md` §11 "Testing".

## What changes

- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFixQualityTest.kt` only: the counting
  dispatcher attributes each counted dispatch (stack-based), the case asserts over three windows and
  supplies its own unrelated-traffic control. No production file changes.
- `openspec/specs/gps-fix-quality/spec.md` gains one scenario carrying the attribution (via this
  change's delta).

Additive, no behaviour change in the app. Rollback: revert the one test file; the delta is documentation.

## Impact

- files: `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFixQualityTest.kt`,
  `openspec/specs/gps-fix-quality/spec.md` (through the delta).
- modules: `:app` unit tests (mobile and automotive flavors — the two flavors share the source set).
- no Android component, manifest, resource, native or Gradle configuration is touched. No parity
  question: the case is a host test, not a surface.

## Alternatives the evidence eliminated

- **Widen the window to N ≥ 3 only** (the item's second candidate): keeps asserting a count that is not
  the re-evaluation's. It lowers the false-positive *rate* and leaves the mechanism — the assertion's
  subject — wrong; a deterministic foreign dispatch still fails the case, as the red run shows.
- **Inject the tick dispatcher as a seam** (`internal var fixQualityTickDispatcher`) and attribute by
  `Thread` identity: correct, but it adds a production threading seam for an observation the dispatch's
  own stack already carries, and it cannot produce a red-on-HEAD case (the seam does not exist there).
