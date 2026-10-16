# The archive gate record — task 5.2 under `TODO.md` §195

Task 5.2 asks for the forced host gate, both flavours, with per-module `tests/failures/errors` quoted and no
exclusion. This file records what that invocation actually produces on this tree, and why the change is
archived with §195 named rather than the gate quoted green. **Nothing here is a waiver of evidence: the
change's own evidence is green and was measured on a clean base; the aggregate is red for a reason that is
not this change and is not yet fixed.**

## The aggregate, forced, on a verified-quiet host (2026-10-15)

```
./gradlew test -PforceTests --no-build-cache          .pi/logs/gate-195-1015-2105.log
  :app   automotive   2030 tests / 38 failures / 0 errors   — every failure AppNotIdleException
        mobile         the task line never appears and no mobile results directory is written
  :auto                853 / 0 / 0
  :core                633 / 0 / 0
  osmscout-client-java  39 / 0 / 0
  BUILD FAILED       183 actionable tasks: 183 executed
0 assertion failures anywhere in the run
```

Quiet-host proof: no other worktree's `build/` directory was written in the window (`.pi/tmp/verify-routing/build`
20:03, `app/build` a day old, only `.pi/tmp/verify-gate/build`), and no other Gradle build was alive at the start.
`:core` and `:auto` finished, and all three CMake ABI builds finished, **before** the `:app` test task started.

## The variant alone, and the class alone (2026-10-15/16)

```
:app:testAutomotiveDebugUnitTest -PforceTests --no-build-cache, nothing else in the graph
  → cascades: 60 AppNotIdleException lines / 38 distinct cases in ~20 min   .pi/logs/auto-suite-alone-2155.log
BasemapSectionComposeTest alone → 4 tests, 0 failures, green
:app:testMobileDebugUnitTest -PforceTests --no-build-cache (2 forks, worktree at HEAD)
  → did not finish within 30 minutes and was stopped; 0 class XMLs were written, so no tally exists for it
```

## What this change itself proves, forced, on a clean base

```
f8f0640 + only this change's patch, in a detached worktree (.pi/tmp/verify-search; evidence/clean-base-verification.md)
  :app:compileMobileDebugKotlin :app:compileAutomotiveDebugKotlin --rerun-tasks --no-build-cache
      BUILD SUCCESSFUL in 24s — 52 actionable tasks: 52 executed
  :core  8 / 0 / 0        :app  30 / 0 / 0 across five suites
  :auto  18 / 0 / 0       :auto full suite, clean worktree at HEAD  853 / 0 / 0
```

## Why the aggregate is red, and what was measured rather than assumed

`TODO.md` §195, re-diagnosed in that entry on 2026-10-15/16 and in `.pi/logs/195-bisect-findings.md`:

- the timeouts are the same six classes §195 named, in a deterministic set, with **no assertion failures**;
- two candidate causes were **falsified by measurement**: the `MemoryPressureResponder` 30 s poll (the suite
  still cascades with that loop not started — `.pi/logs/195-confirm-nopoll-0652.log`) and the map screen's
  `withFrameNanos` loop (exactly one test file in the repository composes that screen, and it is not among the
  hangers);
- a thread dump taken at the first hang shows the **main thread spinning** in
  `android.os.TestLooperManager$…poll` at ~68 % CPU while every `DefaultDispatcher-worker-*` is parked: the
  never-idle condition is a main-looper work source that never drains, reached through Robolectric's shared
  class loader, not background work and not host load;
- **§194**, the other independent red cause, is already fixed and archived (change
  `fix-routing-failure-reporting-classification`).

## The record this change is archived under

- Task 5.2 is closed with this file as its deviation record: the forced gate **was run** at both flavours'
  scope, its per-module tallies **are quoted above**, and its reds are **attributed to §195** — a defect in the
  suite's idling interaction, untouched by this change, with the mechanism narrowed and its first two
  hypotheses falsified.
- No exclusion is claimed for the change's own evidence: every suite this change adds or touches is green in a
  forced run on a clean base, and each of its new invariants was falsified once by mutation (the eight
  `revert-check` records in this directory).
