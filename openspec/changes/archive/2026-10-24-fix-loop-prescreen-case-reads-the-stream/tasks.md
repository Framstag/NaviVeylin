# Tasks — the pre-screen case reads the phase from the stream

Requirement: `agent-issue-loop` — *The run names its phases and the live view into it*, scenario *The pre-screen
is announced and reported before it returns* (the case that guards it) and the scenario this change adds, *A phase
line is read from the stream, not at a fixed instant*.

## 1. The red case and its measurement

- [x] 1.1 The case the item was filed on, red on the base, reproduced with its own command:
      `bash tools/loop-selftest.sh --case 'run#prescreenAnnouncesItselfBeforeItReturns'` on `c4c727fb` → 1 of 1
      failed, `the phase is announced on the console: 'loop: pre-screen' not found in: agent-issue-loop: …`.
      Verify: `evidence/red-before.log`.
- [x] 1.2 The engine is not the defect: the phase line's arrival time on three trees, polled from the console —
      `ea194c38` 0.628 s, `e12cf971` 0.741 s, `c4c727fb` 0.760 s (header at 0.101 s in all three).
      Verify: `evidence/timing.log`, and the harness that took it is `tools/loop/run.sh` driven by a stand-in
      pre-screen as the case does.
- [x] 1.3 The driver's cost inside the old window: `javac --release 17 -d <classes> tools/issues/core/*.java`
      costs 4.140 s wall for the tooling's core, and `run_loop` calls `core_ensure` inside the subshell the case
      backgrounded.
      Verify: `evidence/compile-cost.log`.

## 2. The fix

- [x] 2.1 `tools/loop-cases.sh`, `case_run_prescreenAnnouncesItselfBeforeItReturns`: the `sleep 1.5` reading is
      replaced by the bounded wait for the stand-in's first line; every other assertion is unchanged. The comment
      above the case states why the reading waits, what a fixed one cost, and that the bound is 30 s.
      Verify: `bash -n tools/loop-cases.sh` parses, and the case is green (2.2).
- [x] 2.2 The case, alone, with the fix: `bash tools/loop-selftest.sh --case
      'run#prescreenAnnouncesItselfBeforeItReturns'` → `PASS`.
      Verify: `evidence/green-after.log`.
- [x] 2.3 The case still refuses a replayed phase — the record the suite already owns:
      `bash tools/loop-mutations.sh --only 34.1` (the run's `| tee "$prescreen_log"` replaced by a capture into
      the log, so the console never carries the pre-screen's text) → `RED 34.1 …`, restore green.
      Verify: `evidence/mutation-34.1.log`.
- [x] 2.4 The case still refuses a *replayed* phase, not only a silent one: a hand probe whose `run.sh` captures
      the pre-screen's whole output and prints it after the call returns, run through
      `LOOP_ENGINE=<probe>/tools/loop` → red with
      `and the line it has not printed yet does not: the text is streamed, not replayed`.
      Verify: `evidence/replay-probe.log` (both records arrive in one reading).

## 3. The documents

- [x] 3.1 The `agent-issue-loop` delta: one `MODIFIED` requirement, the requirement's body and its three other
      scenarios verbatim, one scenario added, each naming `run#prescreenAnnouncesItselfBeforeItReturns`.
      Verify: `openspec validate fix-loop-prescreen-case-reads-the-stream --strict` —
      `evidence/structural-checks.log`.
- [x] 3.2 No guideline states the loop's phase lines, so none is edited: `grep -n 'phase line' guidelines/*.md`
      prints nothing, and `guidelines/Build.md`'s "Wall-clock waits and the conversion measured" states the
      principle this wait complies with while binding no shell case.
      Verify: `tools/check-doc-routes.sh` — `evidence/structural-checks.log`.

## 4. The gate and the record

- [x] 4.1 The delta's gate, from this tree: `bash tools/loop/gate.sh --base c4c727fb`, the tip this change is
      stacked on. A delta to `tools/loop-cases.sh` is a core file of the loop's suite, so the gate owes its whole
      set in one run; no Gradle suite is owed (nothing compiled changed), and the profile gates this path with
      the suite alone — the mutation harness's `--check` is owed for a delta that touches the mutation file, and
      the hand-run record `34.1` is task 2.3 instead.
      Verify: `evidence/gate.log` — `selected 208 of 208 cases`, `LOOP-GATE: tests=208 failures=0 errors=0`.
- [x] 4.2 An independent read-only review of the diff against the delta's scenarios, by a process that did not
      write the change; its findings are NOTES, and each is answered or recorded.
      Verify: `evidence/review.md` — `LOOP-BLOCKING: 0`, `VERDICT: PASS`.
- [x] 4.3 `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-prescreen-case-reads-the-stream --root .` — every pointer resolves.
      Verify: `evidence/structural-checks.log`.
- [x] 4.4 Archive the change and confirm the scenario merged into `openspec/specs/agent-issue-loop/spec.md`.
      Verify: the archived change is `openspec/changes/archive/2026-10-24-fix-loop-prescreen-case-reads-the-stream`,
      and the scenario is in the shipped requirement at `openspec/specs/agent-issue-loop/spec.md:1238`.
- [x] 4.5 The archived delta's own gate, and the one red between the two green runs attributed: the re-run on a
      host carrying load average 50 and five other loop suites reported one red among the 208 cases, that red being
      `gate#theChildReadsTheProfileOfTheTreeItWorksIn`, a case this delta does not touch; it is green alone twice,
      and the gate is green once the host quietens.
      Verify: `evidence/gate-archived.log`, `evidence/flake-alone.log`, `evidence/gate-archived-2.log`,
      `evidence/evidence.md` ("The gate, twice, and the one flake between them").
- [x] 4.6 Rebasing the change onto the landed base, after `fix/issue-288-…` and `fix/issue-295-…` reached `main`: its
      two commits replayed onto `07a24aec` with no content change, and the gate re-run there — 213 cases with the
      landed work — green.
      Verify: `evidence/gate-on-main.log` (`LOOP-GATE: tests=213 failures=0 errors=0`), and "The rebase onto the
      landed base" in `evidence/evidence.md`.
