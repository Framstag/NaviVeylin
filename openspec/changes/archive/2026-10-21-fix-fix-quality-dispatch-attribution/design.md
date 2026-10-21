# Design — fix-fix-quality-dispatch-attribution

## Root cause

`CountingMainDispatcher` (`MapCanvasViewModelFixQualityTest.kt:345-371` at base `2e732f0`) counts **every**
dispatch a non-test thread issues to `Dispatchers.Main`, and the case (`:315-338`) asserts that count over
a six-tick window and prints it as the fix-quality tick's dispatch. The main dispatcher it instruments is
the process's, not the re-evaluation's, so unrelated traffic — the `NavigationEngine` stale-speed ticker
an earlier case in the same JVM still runs, any other subsystem's `Dispatchers.Main` hop from a real
thread — is counted, blamed on the tick, and fails a case whose subject did nothing. One mechanism
explains both the item's "1 times in the second window while nothing changed" and the reason its stack
named `NavigationEngine.tickStaleness`.

The measured red run (2026-10-21T09:37:19Z, base `2e732f0`, `tests="9" failures="1"`) supplies that traffic
deterministically — one dispatch per window from a `Dispatchers.IO` coroutine — and reproduces the item's
message verbatim. `evidence/red-…xml` holds it.

## The single fix

Keep counting what the requirement names — main-dispatcher traffic issued from a thread that is not the
test's — and **attribute** it. Every counted dispatch is recorded with the stack that issued it; the
dispatch is the re-evaluation's when that stack runs through `com.naviveylin.ui.map.MapCanvasViewModel`'s
own code (`MapCanvasViewModel$…` frames), because coroutine frames carry the enclosing class name. The
limit this attribution has, stated so a later reader does not over-read it: a `Dispatchers.Main` hop a
`MapCanvasViewModel` coroutine itself issues from a real thread carries ViewModel frames and is counted —
that is the ViewModel's own traffic by definition, and the requirement's clause is about **another
subsystem of the process**. The case then:

1. asserts the re-evaluation's count is 0 over **three** six-tick windows ([TICK_WINDOWS]) instead of the
   second of two — a real-time window is a sample; a per-tick pattern is a property of all of them;
2. provokes one dispatch from a thread that is neither the test thread nor the re-evaluation's, per
   window, and asserts the counter attributed it to another subsystem. On a passing run the control closes
   two failure directions: a filter that counted everything would trip the zero assertion, and one that
   counted nothing would trip the control. It does not close a third — a filter that never matches the
   ViewModel passes both — which the recorded falsification run exercises instead;
3. names the window honestly in its message — three windows of six ticks of 20 ms — instead of the
   "300 ms" that described a constant the case no longer measured by.

The guard the case exists for is unchanged and still caught: a regression that hops onto the main
dispatcher per tick issues its dispatch from the re-evaluation's own coroutine, whose `invokeSuspend`
frame is in the stack, so the attributed count rises. Falsified by mutation (below).

## Threading model / lifecycle

No production code is touched. The case runs the tick on the production `Dispatchers.Default` with the
real clock; `Dispatchers.Main` is instrumented with a `CountingMainDispatcher` that delegates to
`MainDispatcherRule`'s `StandardTestDispatcher`. The case's own noise coroutine lives in a
`CoroutineScope(SupervisorJob() + Dispatchers.IO)` that is cancelled **and joined** in a `finally` block
before the case returns (teardown rule, `guidelines/Build.md` §6) — the main blocks it queued are pumped
by the same `pumpUntil` the windows already use. No `Thread.sleep`, no clock-derived deadline, so
`checkNoWallClockWaits` stays satisfied.

## Blast radius

One test file: `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFixQualityTest.kt`. Shared by
both `:app` flavors (`app/src/test/java` is flavor-independent). No production path, no Gradle
configuration, no spec scenario beyond `gps-fix-quality`'s requirement.

## Rollback

Revert the test file. The spec delta is documentation and merges on archive; the app is unaffected either
way.

## Alternatives the evidence eliminated

- **Widen the window only (N ≥ 3), keep the count** — the item's second candidate, and what the
  falsification run shows to be insufficient: it changes how often unrelated traffic is sampled, not
  whether it is attributed to the wrong subject. A deterministic foreign dispatch (the red run) fails it.
- **Inject the tick dispatcher and attribute by `Thread` identity** — `internal var
  fixQualityTickDispatcher: CoroutineDispatcher = Dispatchers.Default`, the shape of the existing
  `defaultDispatcher` / `rendererDispatcher` seams, plus the rule "compare the `Thread`, not its name".
  Correct, but it adds a production threading seam for an observation the dispatch's own stack already
  carries, and it cannot be red on HEAD: the seam does not exist there, so the red discipline would have
  to be spent on a compile step.

## Verification

- Red on HEAD, recorded: `tests="9" failures="1"`, `aTickDoesNotDispatchOnTheMainDispatcher`,
  2026-10-21T09:37:19Z (`evidence/red-TEST-com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest.xml`).
- Green on the fix: the affected class, forced
  (`--tests "com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest"`, `-PforceTests --no-build-cache`),
  `tests="9" failures="0"` at 2026-10-21T09:52:22.263Z — the run after the review corrections, which
  replaced the 09:47:29.265Z XML with the final bytes' result
  (`evidence/green-TEST-com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest.xml`); plus the
  neighbouring-slice run (`--tests "com.naviveylin.ui.map.MapCanvasViewModel*"`), `tests=321 failures=0`,
  run once per pass — the 40 per-class XMLs archived under `evidence/slice/` are the second
  (09:52:51.408Z–09:53:26.795Z), so they cover the final bytes. Class-only repetition from the first pass
  has no archived artifact of its own, so this change claims no repetition count.
- Falsification: reintroduce the pre-fix per-tick `withContext(Dispatchers.Main) { }` hop inside the
  re-evaluation loop → the case fails, attributed `[window 1: 6, window 2: 6, window 3: 6]`
  (`evidence/falsification-…xml`), restore, forced green again.
- Scoped gate note: this delta touches test sources only, so `guidelines/Build.md` §2's scope block
  applies — the red case plus the affected cases, not the full `./gradlew test` gate. The neighbouring
  `MapCanvasViewModel*` slice is run once to show the class still passes in company.
- No on-device check: the change is a host test's measurement, not an observable behaviour of the app.
