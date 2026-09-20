# Design

## Context

The defect and its cause are in `proposal.md`; what shapes the *solution*:

- `AutoMapRenderer` (`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`) owns a
  `CoroutineScope(SupervisorJob() + Dispatchers.Default)` (:83) and starts three loops in `init`
  (:283-285): `startRenderLoop` (:730), `startExtrapolationLoop` (:755), `startZoomWalkLoop` (:797).
  `shutdown()` (:677-692) cancels all three, cancels the scope, releases the surface and clears the
  overrun buffer; it is already idempotent (`surface?.release()`, `clearOverrunBuffer()` are
  null-safe) and is already called by production call sites (the gate, screen teardown) and by six
  tests inside `AutoMapRendererTest`.
- The test classes construct renderers in `@Before` or inline (`spyk(...)`) and have no `@After`.
  All are JUnit4 + Robolectric; `AutoMapRendererTest` and the others touch `FakeAutoRenderClient`
  (a subclass of `OSMScoutClient`), so per `AGENTS.md` they must keep the **default** Robolectric
  sandbox (no `@Config(sdk=…)`, no `@GraphicsMode`), or the committed JNI stub fails to load in a
  second classloader.
- The file already exposes `internal` test accessors (`renderFrame` :1056, `advanceZoomWalk` :829,
  `extrapolationTick` :888, `overrunSize` :1834, `setBlitOffsetForTest` :1696) — an added test
  accessor follows the existing convention instead of introducing a new one.
- Machine constraints: ~15 GB RAM with ~5 GB free during development; `:auto` has two unit-test
  variants (`testDebugUnitTest`, `testReleaseUnitTest`) and `:app` has two flavors, so several test
  forks can be alive at once. `./gradlew test` is the gate; coverage (`:koverHtmlReport`,
  `:koverXmlReport`, `:osmscout-client-java:jacocoTestReport`) must run in a separate invocation
  (§40.8). Evidence rules: quote executed-task count + elapsed time and verify counts from the result
  XMLs, never from `BUILD SUCCESSFUL` (§17).
- `TODO.md` §33, §40.8 and the local (gitignored) `run-tests` skill all encode the batching
  workaround this change retires.

## Goals / Non-Goals

**Goals:**

- Remove the leak at its source so the `:auto` suite completes in one JVM/one invocation at the
  default heap, with one result XML per class.
- Make the no-leak rule enforced, not remembered: a test class that forgets fails loudly and per
  class, never as an unattributable OOM of unrelated classes.
- Declare and bound the `:auto` fork budget so the suite cannot silently drift back over a limit, and
  so the same budget applies on a fresh checkout and in CI.
- Restore the coverage invocations to a single invocation and retire the mandatory class batching
  from the documented test procedure.
- Record the numbers (`guidelines/Build.md`, `TODO.md`) that justify the declared budget.

**Non-Goals:**

- No production renderer behaviour change (`AutoMapRenderer` keeps starting its loops in `init`;
  lazy start or auto-shutdown was considered and rejected, see D1).
- No change to test *semantics*: no assertion is relaxed and no class is skipped/excluded. The 70
  `AutoMapRendererTest` cases and the six in-test `shutdown()` calls stay as they are.
- No change to `:app`, `:core`, `buildSrc`, the license tasks, the flavors, the ABIs or the coverage
  aggregation setup.
- No new flake-guard for §18 beyond re-running the affected class; the leak hypothesis is checked,
  and any surviving flake stays a separate item in `TODO.md`.
- Not a fix for a shared-Robolectric-sandbox leak class: if `:app`/`:core` turn out to leak too, that
  is recorded in `TODO.md`, not fixed here (they are green in one JVM today).

## Decisions

### D1 — Where the leak is fixed: tracker rule in test sources

**Chosen:** a JUnit4 rule (plus a small tracker) in `:auto` test sources. The rule hands out renderers
(and accepts any instance a test created inline), shuts every tracked instance down when the test
finishes, and asserts that none of them reports active background work afterwards. Four test classes
adopt it; `AutoMapRenderer` gains one `internal` accessor for the active-job count.

*Alternatives:*

- **(a) One `@After` per test class** — smallest diff, but it fixes four classes only; the next test
  class that creates a renderer leaks exactly the same way, which is the regression this change
  exists to prevent. Rejected, except that the rule is the mechanism those four classes use.
- **(b) Production-side auto-shutdown** — e.g. start the loops lazily on first surface/follow
  engagement, make `AutoMapRenderer` `AutoCloseable`, or add a leak detector (`LeakCanary`/
  `finalize`-based) so an un-closed instance closes itself. Rejected: it changes production lifecycle
  behaviour for a test-only defect, and lazy loop start is exactly the contract `aa-entry-zoom-animation`
  and `smooth-follow` rely on (extrapolation must run while a parked vehicle is in follow mode); a
  GC-triggered shutdown would be non-deterministic in the app.
- **(c) A JUnit `TestRule` (chosen)** — no inheritance constraint, works with the existing class
  structure, keeps the assertion next to the teardown, and is testable on its own without touching
  production code paths.
- **(d) An abstract base class for the four test classes** — equivalent in intent, but forces a class
  hierarchy on tests that also extend/implement other things, and makes the tracking state visible to
  every test method. Rejected in favour of the rule; if a future test class needs both, the rule can
  be reused without changing its superclass.

**Rationale:** fix the cause, keep the fix mechanical, and make the requirement enforceable for tests
that do not exist yet. The only production edit (one `internal` accessor) matches the existing
test-accessor convention in the same file and carries no runtime behaviour.

### D2 — Fork budget values: bound first, relax on evidence

**Chosen:** declare `maxHeapSize = "1024m"` and `forkEvery = 24` in `auto/build.gradle.kts`
(48 test classes → at most 2 forks), then apply the measurement rule below in the same change.

*Measurement rule (part of the decision):* run the suite in one invocation at the declared budget. If
the suite also completes in a **single** fork at the declared ceiling (i.e. `forkEvery` never
triggers), drop `forkEvery` and record both measurements. Never go the other way (start unbounded,
then add a bound after an OOM): an unbounded run that dies leaves no result XMLs, which is the
attributability failure this change removes.

*Alternatives:*

- **(a) `maxHeapSize = "2g"`, no `forkEvery`** — the ceiling AGP projects commonly use, but two
  parallel `:auto` forks plus `:app`'s forks on a machine with ~5 GB free risks swapping; it also
  masks accumulation instead of bounding it. Rejected as the default; acceptable only if the
  measurement shows a single fork genuinely needs > 1g.
- **(b) Keep 512 MB and rely on `forkEvery`** — with 48 classes this means many forks (and a
  Robolectric/JVM start each), the slowest option, and it leaves no headroom for the leak-fixed
  suite's peak. Rejected.
- **(c) Do nothing in the build, keep the manual four-batch procedure** — keeps the defect in the
  procedure: every `:auto` verification stays expensive, needs XML copying, and never exercises the
  suite as a developer runs it. Rejected.
- **(d) `forkEvery` only, very small (e.g. 4)** — bounds the peak hardest but multiplies JVM
  startup; with the leak fixed there is no reason to pay that. Rejected.

**Rationale:** `forkEvery = 24` is exactly the manual batching the project performs today, executed
by Gradle instead of by hand; 1024 MB is a bound that fits the machine while absorbing the Robolectric
sandbox cost of 48 classes, and the measurement rule stops the value from being folklore.

### D3 — The coverage invocation is covered by the same budget

**Chosen:** treat the coverage invocations as part of this change's acceptance: the declared budget
must make them complete in one invocation, in a separate invocation from the plain test gate (§40.8).

*Alternatives:*

- **(a) Leave coverage as-is with the batching workaround** — the gate stays blocked and the "suite
  runs in one invocation" contract is not really met. Rejected.
- **(b) Give the coverage run its own larger heap** (Kover's own test-task configuration) — the Kover
  agent is attached to the *same* `Test` task, so the fork arguments are shared; a second budget pair
  would only hide a difference in instrumentation overhead. Rejected as the first move; if the
  measurement shows the instrumented run genuinely needs more headroom, that number is recorded and
  documented in the same place as the plain budget.
- **(c) Exclude `:auto` from coverage** — the module carries the car-side logic the project tracks
  (`test-coverage`, `kover-aggregate-report`). Rejected.

**Rationale:** the requirement added by this change is about the suite completing, not about the
plain task only; the same fork settings govern both, so verifying both costs one extra invocation.

### D4 — How the no-leak rule is verified (revert-check)

**Chosen:** the rule's teardown asserts, per test, that every tracked component reports zero active
background jobs after shutdown; the rule and its tracker are additionally covered by their own unit
test, whose revert-check is a single mutation (make the tracker skip `shutdown()` → that test fails,
§40.12 one mutation per check).

*Alternatives:*

- **(a) Rely on "the OOM does not come back"** — non-deterministic, and it is the evidence style
  §33/§17 warn about. Accepted only as a secondary confirmation (the single-invocation run) next to
  the deterministic assertion.
- **(b) Assert free-heap thresholds** — flaky by nature (GC timing, machine load). Rejected.
- **(c) Inspect coroutine jobs via the scope** — the scope is private; the accessor approach (chosen)
  is the file's existing pattern and reads synchronously: `Job.isActive` is false immediately after
  `cancel()`, so no waiting or retry is needed.

**Rationale:** a leak must fail the class that causes it, deterministically, in the same run.

### D5 — Tooling and documentation end state

**Chosen:** demote the manual batching in the local `run-tests` skill to a diagnostic fallback (keep
the script), and make `guidelines/Build.md` the durable home of the teardown rule and the declared
`:auto` fork budget.

*Alternatives:*

- **(a) Delete `run-auto-batches.sh`** — it remains useful when a fork budget is hit on a smaller
  machine (or when a single class must be isolated); deleting it would push the operator back to
  improvising. Rejected.
- **(b) Leave the skill guidance unchanged** — the guidance is what an agent follows; leaving the
  mandatory batching in place would keep the workaround as the normal path. Rejected.
- **(c) Put the rule only in the (gitignored) skill** — `.pi/` is in `.gitignore`, so the skill is not
  in the repository and a fresh checkout/CI would not see it. Rejected: the durable rule lives in
  `guidelines/Build.md`; the skill is updated locally to match.

**Rationale:** the documented path must be the one the project actually wants taken, with the
fallback available but not normative.

### D6 — `:app` gets a declared budget as well (added during apply)

**Chosen:** `FavoritesSheetReorderComposeTest`'s deterministic in-suite failure (`TODO.md` §43) is fixed by
declaring a measured `:app` fork budget (`maxHeapSize = "1024m"`), not by changing the test.

*Evidence that the test is not the defect:* the class passes 11/11 alone and fails in any full `:app` suite
run; the same failure occurs with this change stashed; no `:auto`/`:core` test task runs in the failing
command. *Evidence for the mechanism:* at 512 MB the whole `:app` suite (146 classes, 1054 tests, one fork)
fails with the class's `ComposeTimeoutException` after exactly 5000 ms; at 1024 MB it is green (1m49s) and at
2048 MB likewise (2m06s) — i.e. the wait expires because the fork is under memory pressure (GC), which the
ceiling removes. `:app`'s own renderer tests already release their buffers (`shutdown()` in `@After`), and
the `:app` scope scan found no comparable leak, so no teardown fix is needed there.

*Alternatives:*

- **(a) Raise the test's `waitUntil` timeout** — hides a resource defect behind a longer wait, keeps the suite
  one GC pause away from the next failure, and changes a test's semantics instead of its environment.
  Rejected.
- **(b) Treat it as a `:app` test-isolation bug and hunt cross-class state** — investigated: the class passes
  with its own package (3 classes) and with the whole suite at 1024 MB, and the failure appears at the *start*
  of the suite's class order (XML timestamps), so no preceding class can be blamed. Rejected as the mechanism
  (kept as the `TODO.md` §43 analysis).
- **(c) Leave 512 MB and split `:app` into batches** — the workaround this change retires for `:auto`; it
  would make the failure disappear for the wrong reason and keep the suite untestable as it really runs.
  Rejected.
- **(d) Declare the `:app` budget (chosen)** — same shape as D2, same measurement rule, one line of Gradle
  configuration, and the test keeps its 5 s wait (which is generous once the GC pressure is gone).

**Rationale:** the module's suite needs more headroom than the plugin default for 146 Robolectric classes in
one fork; declaring the measured bound keeps the requirement ("suite completes in one invocation") honest
instead of tightening a test until it fits a too-small heap. The `:app` budget is documented with the same
numbers as `:auto`'s in `guidelines/Build.md` §6.

## Risks / Trade-offs

- **`forkEvery` costs JVM + Robolectric startup per fork.** With `forkEvery = 24` and 48 classes that
  is one extra start for `:auto`, plus one per extra `.so`-loading class group if Robolectric
  re-initialises. Measured in task 2.2/3.4; per D2's rule the cadence is dropped if a single fork
  suffices — the trade-off is bounded and reversible in the same change.
- **A production regression in loop cancellation now fails `:auto` tests.** That is the point, but it
  means the rule's assertion must be robust: `Job.isActive` flips synchronously on `cancel()`, so the
  assertion is deterministic; `shutdown()` is idempotent, so the six in-test `shutdown()` calls stay
  valid (verified by a dedicated test case).
- **MockK spies.** `RendererGateTest` and `FreeDrivingScreenTest` use `spyk(AutoMapRenderer(...))`,
  which calls through to the real implementation, so the rule and the accessor work; if a future test
  passes a fully mocked renderer, the rule still calls `shutdown()` and simply records the call
  (assertion on job state is then only meaningful for real instances) — documented on the rule.
- **Robolectric sandbox rule (`AGENTS.md`).** The rule and the tracker must not introduce a sandbox
  config or change the runner of the adopting classes; the four classes keep their current
  `@RunWith(RobolectricTestRunner::class)` with the default sandbox, otherwise the committed JNI stub
  fails with "already loaded in another classloader".
- **A ceiling is not a fix.** B alone would have left ~108 renderers ticking at ~30 Hz; the change
  deliberately fixes A first and keeps B as the bound. If the measurement shows a single fork still
  needs > 1g *after* A, that is a signal the leak fix is incomplete — investigate before raising the
  ceiling, and record the finding in `ki_processing_failures.log`/`TODO.md`.
- **Memory pressure of several budgets at once.** A full `./gradlew test` now holds up to two `:app` forks at
  1024 MB, one `:auto` fork at 1024 MB and one `:core` fork at the plugin default. That is the reason the
  declared ceilings are the *measured* minima and not 2g (D2), and the reason the fork cadences stay available
  as the next lever if a machine cannot hold them (D2/D6).
- **Attributability could still be lost for another reason** (e.g. a future class that OOMs on its
  own). The requirement's "failures stay attributable" scenario is checked by the presence of
  per-class XMLs in the verification run, not by a mechanism.
- **Coverage numbers may shift** because the instrumented run now executes the suite as one
  invocation (per-class execution order changes). No thresholds exist (report-only), so this is
  informational; the run is still separate from the test gate.
