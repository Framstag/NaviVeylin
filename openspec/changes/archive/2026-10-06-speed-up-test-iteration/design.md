# Design — Speed up test iteration

**Change:** `speed-up-test-iteration`
**Motivation:** see `proposal.md` — Why (measured bucket table, 22 files with `Thread.sleep`, the two
decisions confirmed by the owner: outright ban, pre-gate bounded by the completion rule).

## Context

Current state this design has to fit, verified in this tree:

- The suite is one Gradle test task per `:app` flavor holding plain JVM, Robolectric logic and Compose
  UI tests together; the cheap tier is 46 classes / 1 s of 220 classes / 252 s.
- `unit-test-suite-runtime` already requires controlled time and load-independent failure sets, and
  `guidelines/Build.md` §4/§6 plus `guidelines/Design.md` §11 and §40.31 already state the rule
  ("never pace a unit test off a timer or a debounce", "a test may not compute a deadline from the wall
  clock"). Nothing enforces either, and 22 test files violate them.
- The seam exists as uncommitted work of an in-flight change:
  `core/src/main/java/com/naviveylin/core/EngineTimeSource.kt` (a `fun interface` whose production
  value is `java.lang.System::currentTimeMillis`) and `EngineDispatchers.kt`
  (`Production = Dispatchers.Default`/`IO`), with `EngineTimeSourceTest` /
  `EngineDispatchersTest` / `RouteCalculationTest` untracked and 57 test files modified in this tree.
- `build-test-gate` already fixes the evidence rules: a run proves itself by executed-task count,
  per-module tallies and fresh result-XML timestamps, and completion requires the unfiltered gate.

## Goals / Non-Goals

Goals: a documented iteration pass that runs the cases a change declares; a build check that refuses
wall-clock waits in test sources, enabled per module as that module's sources are converted; the
measured conversion of the sites whose class time is wait-bound.

Non-goals: no selection map, no co-change miner and no always-run set (the pre-gate is bounded by the
completion rule instead); no change to fork count, heap, coverage instrumentation or the CI recipe; no
attempt to fix the aggregate four-suite contention (`TODO.md` §132) or the ~22 s per-fork Robolectric
bootstrap; no device/UI verification (no rendering or car-surface behaviour changes).

## Decisions

### D1 — No allowlist: the check refuses every wall-clock wait

Chosen: the scan fails on `Thread.sleep`, on a teardown/await loop whose deadline comes from
`System.currentTimeMillis()`, and on sleep-based pacing in any test source, with no exception list.

Alternatives: an allowlist whose entries carry a reason (rejected — the two candidate entries are both
convertible: `FakeOSMScoutClient.renderWithRouteAndPoisDelayMs` has exactly one user,
`RenderModeSwitchTest:145`, and `DiagnosticsLogTest:91` sleeps inside `DiagnosticsLog.time { }`, which
is the subject of the measurement); an allowlist with a no-growth budget (rejected — same maintenance
burden without removing the temptation). Rationale: with no exemption the rule is a single predicate a
reviewer can check by reading the check's output, and every exemption would otherwise be the place the
next load-dependent flake enters.

### D2 — Reuse `EngineTimeSource` / `EngineDispatchers`; extend, do not add a second clock

Chosen: the converted view-model windows read the existing `EngineTimeSource`, and a subject whose work
must land on the test scheduler takes `EngineDispatchers`. Test fakes stay local: because
`EngineTimeSource` is a `fun interface`, a module's test source needs only
`var fakeNow = 0L; val source = EngineTimeSource { fakeNow }` — no Gradle test-fixture wiring.

Alternatives: a new `:core` clock abstraction (rejected — two seams for one decision, and the in-flight
`navigation-engine` work already cites this one); Robolectric `ShadowSystemClock` (rejected — it only
exists inside Robolectric sandboxes, and a component that captured a value at construction is not
reached by it; `:core` and plain-JVM tests get nothing); `kotlinx-coroutines-test`'s `TestScope`
scheduler alone (insufficient on its own — it moves coroutine time, not the timestamps the windows
compare, which is why the seam is needed too).

### D3 — The pre-gate is a documented procedure plus one tool, not a new Gradle task

Chosen: `tools/` holds a device-free script that takes a change name plus the working-tree diff and
prints the selected classes and their last measured class time; `guidelines/Build.md` §4 documents the
procedure and `AGENTS.md` iteration rule 2 points at it; the local skill wraps it.

Alternatives: a `:app:declaredCasesTest` Gradle task whose filter is computed from the change (rejected
— the filter would depend on the change name and the diff, so the task stops being a stable, cacheable
gate task and `build-test-gate`'s "local and CI recipes agree" is harder to keep true); JUnit4
`@Category` annotations (rejected — an annotation cannot express "declared by this change", and every
new case would need a class edit plus an enforcement test); filtering the existing test task by
documented package conventions only (kept, but as one of three rules rather than the whole rule).

### D4 — Selection is artifacts ∪ diff ∪ symbol mention

Chosen: a class is selected when the change's own artifacts name it, when the change's diff touches it,
or when its source mentions a type the diff changes. Measured on the in-flight changes this yields the
cases they declare (1–9 classes); backtested over 8 archived changes it misses 14 of 51 declared
classes (27 %).

**The pass is run in two stages, measured 2026-10-06 while implementing task 1.1.** `mention` is the
wide rule — the app's central types are named across the suite — so the flat union approaches half the
suite, and the fast signal is the author-declared stage:

| change (own file list) | declared · touched · mention | stage 1 `--rules declared,touched` | full union |
|---|---|---|---|
| `fix-route-session-stop-path` | 9 · 4 · 80 | 9 classes / 11.1 s | 82 classes / 91.1 s |
| `show-route-calculation-progress` | 9 · 2 · 69 | 9 classes / 1.0 s | — |
| `fix-step-leg-distance-and-time` | 8 · 0 · 25 | 8 classes / 3.9 s | — |
| `:app` suite (reference) | — | — | 220 classes / 252 s (135 s wall) |

Stage 1 is the declared cases only (`tools/declared-cases.sh --rules declared,touched`), stage 2 is the
full union, and the module suite is still owed before the change is complete. The spec delta needs no
edit for this: it requires the union to run before the suite, and the stages only order it. The
documented recipe names the stages so the fast pass is not mistaken for the whole pre-gate.

Alternatives: a package-tree rule (measured coarser: a `MapCanvasViewModel` change selects 99 classes
against 49 by symbol mention, i.e. it mostly re-runs the suite); a full map with a mined co-change layer
(still missing the other-module and first-time couplings, and it needs a miner plus a completeness
guard whose maintenance is unbounded — rejected for a tool that is never evidence); ordering/tiering
alone (measured cheap tier is 1 s, rejected as no gain).

### D5 — The check is enabled per module as that module's sources are converted

Chosen: the `preBuild` wiring lands for `:app` when `:app`'s sources are clean, then `:auto`, then
`:core`, so no task in this change leaves the build red. The scan reads source roots only, never
`build/` outputs or generated sources.

Alternatives: landing the wiring first with the suite red until the last conversion (rejected — it
breaks the rule that no task is completed while the build fails and it makes every intermediate run
unattributable); a global toggle that turns the check on for all modules at once (rejected — the three
modules' conversions have different risk: `:app` carries the Compose and dwell-window cases, `:core`
carries the timing helper itself).

### D6 — A converted case proves both sides of its window

Chosen: every converted window case asserts the negative and the positive side from the seam — time set
just before the window produces no trigger, time advanced past it produces the trigger — so a
conversion cannot pass by doing nothing. Each such case is the target of the change's `revert-check`
task (revert the conversion, the named case must fail).

Alternatives: asserting only the positive side (rejected — a case that advances time and asserts a
value the subject would publish anyway stays green when the window logic is deleted); asserting the
window constants (rejected — pins numbers instead of behaviour, the `guidelines/Build.md` §4 lesson).

## Risks / Trade-offs

- **A conversion weakens a case silently** → D6's two-sided assertion per converted case, plus one
  `revert-check` per new invariant (the check itself, and one window per converted subject).
- **A deadline loop whose subject runs on a real dispatcher cannot be driven by the scheduler** →
  the subject takes `EngineDispatchers` and the case awaits the observable state on the test scheduler;
  where a case awaits work that is genuinely off-JVM (a native callback), the subject must publish its
  completion through the seam before the wait is removed — with no allowlist this is the only path, so
  a case that cannot be converted blocks its module's check enablement rather than being excepted.
- **The wait sites in `:auto` (`MapScreenTest`, `AutoMapRendererTest`, `TemplateFaultIsolationTest`)
  are Robolectric screen tests with real render loops** → convert after `:app`, with the same
  two-sided assertion; measure their class time before and after instead of assuming a saving.
- **The pre-gate can become a substitute for the suite in practice** → the spec scenario "A pre-gate run
  is not gate evidence" plus the unchanged completion rule; the tool prints the classes it selected so
  a reviewer sees the scope instead of trusting it.
- **The pre-gate cannot go below ~30 s** (22 s fork bootstrap + daemon + test compile) → the change
  promises a 30–45 s first pass for a declared set, never a 10 s loop, and the design leaves tiering
  out because the cheap tier measures 1 s.
- **This tree carries in-flight work** (seam files untracked, 57 test files modified) → conversions
  start only after the changes that own those files have landed or have been confirmed finished, and
  each conversion checks `git status` / `find -newermt` for a peer edit first (`AGENTS.md`, one builder
  per working tree).

## Migration Plan

1. Pre-gate tool + its self-test + `guidelines/Build.md` §4 + `AGENTS.md` rule 2 — touches no
   production or test file, so it can land while the in-flight work is still open.
2. Extend the seam to the view-model windows (`MapCanvasViewModel` recenter dwell, fix-quality tick,
   follow throttle, viewport restore), production value unchanged.
3. Convert `:app`'s wait sites, then enable the check for `:app`.
4. Convert `:auto`'s, enable for `:auto`. Convert `:core`'s, enable for `:core`.
5. Record the measured per-class class time before/after, run the unfiltered gate (both flavors, all
   shipped ABIs), and record `build/gate-timings.json`.

Rollback: revert the commits. The check is one `preBuild` wiring line per module, the pre-gate is a
documented procedure plus a tool with no runtime artifact, and every converted subject keeps its
production default (`EngineTimeSource.System`, `EngineDispatchers.Production`), so no runtime, data or
user-visible state is involved and the spec deltas revert with the change.

## Verification

- **Check behaviour**: buildSrc unit tests over fixtures — a fixed sleep, a system-clock deadline loop,
  a clean file — plus a revert-check that plants `Thread.sleep(10)` in a test source and requires the
  check to fail; `./gradlew -p buildSrc test`.
- **Tool behaviour**: the pre-gate's self-test needs no device (the `tools/measure-highlight.py`
  precedent) and covers the three rules, the empty selection and the printed cost line.
- **Measured conversion**: per-class `time=` from each module's result XML, before/after, per converted
  class, recorded as a table — no device and no screenshot is involved, and the change claims no
  pixel-level property.
- **Suites**: forced runs with `-PforceTests --no-build-cache -PnoCoverage` per module, then the
  unfiltered gate with both flavors and every shipped ABI, quoting per-module tallies and fresh
  timestamps (`build-test-gate`).
- **Gate timing**: one instrumented gate run (`tools/gate-timings.init.gradle.kts`) compared against the
  record in `guidelines/Build.md` §2.
- **Not verified on a device, deliberately**: no UI, rendering or car-surface behaviour changes, so no
  emulator or head unit step is claimed; the `:auto` tests affected are JVM Robolectric tests, and the
  change says so instead of implying device proof.

## Open Questions

- Whether the recenter dwell window should read `EngineTimeSource` directly or the ViewModel's existing
  scheduler injection point — resolvable while implementing step 2 without touching specs or tasks.
- Whether `FakeOSMScoutClient`'s simulated latency becomes a `CountDownLatch` or a
  `CompletableDeferred` gate — both are test-controlled and satisfy the ban; the choice is local.
