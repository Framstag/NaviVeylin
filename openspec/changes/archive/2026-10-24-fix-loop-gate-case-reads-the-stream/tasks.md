# Tasks — the gate case reads its suite's stream

Requirement: `agent-issue-loop` — *The gate streams a suite's output while it runs*, scenario *A suite's output
reaches the console before the suite exits* (the case that guards it) and the scenario this change adds, *A
suite's line is read from the stream, not at an instant*.

## 1. The red case and its measurement

- [x] 1.1 The case is green on the base when the host is left alone — the defect is a bound, not an observed
      flake — and red on the base when the host wears the shape the item names:
      `bash contend.sh .pi/worktrees/i296-base 24` (24 spinning shells at nice 0, the case at nice 19, 16 CPUs) →
      `FAIL`, `'gate: bash' not found in: LOOP-GATE-PROFILE: …`, `1 of 1 cases failed`, while the same tree
      uncontented prints `PASS`.
      Verify: `evidence/red-before.log` (both readings, and the harness `evidence/contend.sh`).
- [x] 1.2 The gate's own start-up, polled rather than sampled: `bash timing-probe.sh 4` → 43–45 ms, and the same
      probe with 20 spinning shells holding the host → 115–141 ms, both against the case's 1500 ms window, so
      the start-up moves with the host while the window does not.
      Verify: `evidence/timing.log`, harness `evidence/timing-probe.sh` and `evidence/timing-loaded.sh`.
- [x] 1.3 The item's shape without contention: a host whose `git` costs 0.6 s per call (three calls precede the
      announcement) — the base reading at the fixed instant holds the gate's first line and nothing else.
      Verify: `evidence/host-bound-probe.log`, run 1, harness `evidence/host-bound-probe.sh`.

## 2. The fix

- [x] 2.1 `tools/loop-cases.sh`, `case_gate_theSuitesOutputReachesTheConsoleWhileItRuns`: the `sleep 1.5` reading
      is replaced by the bounded wait for the stand-in suite's first line; every other assertion is unchanged.
      The comment above the case states why the reading waits, what a fixed one cost, and that the bound is 30 s.
      Verify: `bash -n tools/loop-cases.sh` parses, and the case is green (2.2).
- [x] 2.2 The case, alone, with the fix: `bash tools/loop-selftest.sh --case
      'gate#theSuitesOutputReachesTheConsoleWhileItRuns'` → `PASS`; three more runs → `PASS`.
      Verify: `evidence/green-after.log`, `evidence/green-repeat.log`.
- [x] 2.3 The case under the *same* contention the base failed under → `PASS` (the reading no longer depends on
      the start-up).
      Verify: `evidence/green-contended.log`.
- [x] 2.4 The case still refuses a gate whose stream is silent — the record the suite already owns:
      `bash tools/loop-mutations.sh --only 46.1` (the gate's `| tee "$stream"` replaced by a capture into the log)
      → `RED 46.1 …`, restore green; and the same substitution applied by hand so the assertion it fires for is
      quoted (`the suite's own first line reaches the console while the suite runs: … 'case: a note is read' not
      found in: …`), the file restored and the restore proved.
      Verify: `evidence/mutation-46.1.log`, harness `evidence/handrun46.sh`.
- [x] 2.5 The case still refuses a *replayed* stream, not only a silent one: a hand probe whose `gate.sh` captures
      the whole output and prints it after the command returns, read by the fixed form → red with
      `the output is streamed, not replayed`.
      Verify: `evidence/host-bound-probe.log`, run 3.

## 3. The documents

- [x] 3.1 The `agent-issue-loop` delta: one `MODIFIED` requirement, the requirement's body and its two other
      scenarios verbatim, one scenario added, each naming `gate#theSuitesOutputReachesTheConsoleWhileItRuns`.
      Verify: `openspec validate fix-loop-gate-case-reads-the-stream --strict` —
      `evidence/structural-checks.log`.
- [x] 3.2 No guideline states the loop's shell cases, so none is edited: `guidelines/Build.md` §6e states the
      principle this wait complies with while binding no shell case, and `guidelines/Build.md` names none of the
      cases.
      Verify: `tools/check-doc-routes.sh` — `evidence/structural-checks.log`.

## 4. The gate and the record

- [x] 4.1 The delta's gate, from this tree: `bash tools/loop/gate.sh --base 0f366176`, the tip this change starts
      from (main). A delta to `tools/loop-cases.sh` is a core file of the loop's suite, so the gate owes its whole
      set in one run; no Gradle suite is owed (nothing compiled changed).
      Verify: `evidence/gate.log` — `selected 209 of 209 cases`,
      `LOOP-GATE: tests=209 failures=1 errors=0 (… 1111s) — failing: tools/loop-selftest.sh --from-git 0f366176`,
      the single `FAIL` being `run#aWriterTheRunCanPlaceBlocksTheItem`.
- [x] 4.2 That one red is the base's, not this delta's: the same case alone on `0f366176` fails with the
      byte-identical message, and the same red is already on record on the base in the archive (#288 owns the
      fix — the diff here touches no engine file, no mutation record, and not that case).
      Verify: `evidence/base-red-attribution.log`; the full-suite baseline on the base is deliberately not
      re-measured, and `evidence/evidence.md` names that exclusion and why (§4d's step 1 is what attribution
      needs once a case fails alone).
- [x] 4.3 The checks the profile owes for the *archived* delta (the `openspec/**` and `*.md` paths it then
      carries): the classification check, the doc routes, `openspec validate`, `evidence-check.sh` — the loop
      suite is not re-run for them, because `tools/loop-cases.sh` is byte-identical to the file `gate.log`
      measured.
      Verify: `evidence/post-archive-checks.log`.
- [x] 4.4 An independent read-only review of the diff against the delta's scenarios, by a process that did not
      write the change; its findings are NOTES, and each is answered or recorded.
      Verify: `evidence/review.md`.
- [x] 4.5 Archive the change and confirm the scenario merged into `openspec/specs/agent-issue-loop/spec.md`.
