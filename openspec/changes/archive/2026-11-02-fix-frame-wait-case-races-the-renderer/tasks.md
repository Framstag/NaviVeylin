## 1. The red case (no production code)

- [x] 1.1 Reproduce the base case's load-dependent failure on the base commit and record it. The stall
  that reproduces CI's interleaving is a probe (a fixed sleep in test sources is refused by
  `:auto:checkNoWallClockWaits`, hence `-x :auto:checkNoWallClockWaits` on that one run); the probe was
  reverted before anything else. Verify: `evidence/red-case.xml` carries `tests="1" failures="1"`,
  `timestamp="2026-11-02T18:33:56.716Z"`, message
  `java.lang.AssertionError: the armed wait requested the frame again`; the same case is green without the
  probe on an idle host (8 runs) and under a four-spinner CPU load (6 runs), which is what makes it a
  load-dependent red build rather than a wrong expectation.
- [x] 1.2 Confirm the second arm: with only the clock fix applied (design D1) the case failed 4 of 8 runs
  with `the armed wait spent one retry expected:<1> but was:<2>`
  (`evidence/second-watchdog-diagnostic.txt`) — the extra retry is the wait `onSurfaceCreated` armed, its
  `delay` shortened by the bound the case lowers. Instrumentation and the first version of the fix were
  reverted before the change below was written.

## 2. Make the case own the state it decides its expiry from

- [x] 2.1 `auto/src/test/java/com/naviveylin/auto/AutoMapRendererFrameWaitTest.kt`: disarm the
  surface-created wait before lowering `frameWaitBoundMs`, so exactly one watchdog is in play (design D2;
  spec `auto-map-renderer` — The armed watchdog fires and retries the frame).
- [x] 2.2 Advance the injected clock on its own read (`{ if (reads++ == 0) 0L else 5L }`) instead of from
  the test thread after `requestRender()`, so the fire-time decision is a property of the case, not of the
  scheduler (design D1).
- [x] 2.3 Assert the retry's own observables — `frameWaitRetries == 1`, **exactly one** recorded
  diagnostic naming the retry index (`assertEquals(1, reports.size)` and
  `reports.single().contains("retry 1/${AutoMapRenderer.FRAME_WAIT_MAX_RETRIES}")`, the sibling seam case's
  own form at `:89-90`; review round 1, correction 1), and `renderRequestCount` taken before the case's own
  request (≥ +2) — instead of a snapshot taken after it (design D3; `guidelines/BuildTests.md` §6e).
- [x] 2.4 Verify the case is not vacuous: remove `requestRender()` from `frameWaitIteration`'s retry path
  and re-run the case — it fails deterministically (`evidence/mutation.xml`, `tests="1" failures="1"`,
  `timestamp="2026-11-02T18:43:45.515Z"`); restore the production file from a copy and confirm
  `git status` names only the test file.
  - **Done 2026-11-02** — restored file is byte-identical to the base (`diff` clean); the tree carries one
    modified path.

## 3. The spec delta

- [x] 3.1 `specs/auto-map-renderer/spec.md`: the requirement gains "The armed watchdog fires and retries the
  frame", naming `theArmedWaitFiresAndRetriesTheFrame`, and the requirement text says the bound, the armed
  wait and the injected clock are the renderer's own state. Every other scenario keeps its case.

## 4. Verification

- [x] 4.1 The case and the class, eleven runs with the strict report assertion (round 2; no compiled file
  changes, so this is the focused suite the scope owes): five single-case runs, every one
  `tests="1" failures="0"`, and six class runs, every one `tests="7" failures="0" errors="0"` — every run's
  own XML line, with its `tests=`/`failures=` and `timestamp=`, in `evidence/repeat-runs.txt`; one
  representative XML in `evidence/case-repeat.xml` (`timestamp="2026-11-02T18:57:11.419Z"`).
  Command: `./gradlew :auto:testDebugUnitTest --tests 'com.naviveylin.auto.AutoMapRendererFrameWaitTest'`
  (and the same with the single case named), `-PforceTests --no-build-cache`.
- [x] 4.2 The whole class, which is the affected suite: `tests="7" failures="0" errors="0"`,
  `timestamp="2026-11-02T18:55:18.037Z"` — `evidence/class.xml`.
- [x] 4.3 The project's gate, once, from the change root: `./tools/loop/gate.sh` — exit 0, and `LOOP-GATE: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 322s)` — the pair is not retained (the gate's own tally is a build-dir artefact, gone with a later `./gradlew clean`); `evidence/measure.txt` carries the same run's `LOOP-MEASURE-GATE seconds=322 suites=1`. The compiled step is one Gradle
  invocation, so the gate's own summary reads 0 and the counts are the module XMLs it wrote: app mobile 2219,
  app automotive 2219, auto 912, core 649, `failures=0`, all newest timestamps after the gate started
  (`evidence/gate.txt`). Test sources only changed, so this record stands and no second gate was run.
- [x] 4.4 `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-11-02-fix-frame-wait-case-races-the-renderer --root .`
  reports no dangling pointer (round 2, after the archive moved and the strict assertion landed) — recorded in
  `evidence/evidence-check.txt`.
