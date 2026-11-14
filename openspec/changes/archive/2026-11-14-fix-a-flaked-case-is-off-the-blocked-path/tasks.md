# Tasks — a case that flaked once is off the blocked path

Ordered; each task names its own verification. The case was written first, so its red on HEAD is a measurement and not
a claim. This round took the plan over from a worker the run ended at its context checkpoint — the plan and the change
directory were in the tree, the code was not — so every verification below is this round's own run, and the evidence
files are the ones it wrote.

- [x] 1.1 Add `run#aFlakedCaseIsOffTheBlockedPath` to `tools/loop/tests/loop-cases.sh`, registered in the case list and
      the dispatcher and declaring its files, with its two runs: a red tally whose case passes on the second look in the
      item's own worktree records `flaked` and the item closes; the same red whose case fails again on that look
      records `delta` and blocks. Verified: red on the base with `expected 'flaked', got 'delta'`
      (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, tests=1 failures=1, 2026-11-14T13:50:54Z), and green on
      this tree (`evidence/red-case-green.log`, `evidence/red-case-green.xml`, tests=1 failures=0,
      2026-11-14T13:54:58Z).
- [x] 1.2 The case's declaration the driver's own check found short: it names `tools/gate-attribute.sh` in prose, so
      that path is declared. Verified: `driver#everyCaseDeclaresTheFilesItCanReach` passes
      (`evidence/declaration-case.log`), after failing with `a case does not declare what it can be reached by:
      run#aFlakedCaseIsOffTheBlockedPath:tools/gate-attribute.sh`.
- [x] 2.1 `tools/loop/run.sh`: `flake_probe` and the two arms that answer through it — the red test's own case and the
      case whose base rerun exited `0` — with the profile's case bound read once for both reruns. Verified: the case's
      two halves, and `run#aRedGateIsAttributedToTheTreeThatFailsIt` (which asserts `delta` for a case that fails again)
      in `evidence/red-gate-attribution-case.log`.
- [x] 2.2 `tools/loop/run.sh`: the attribution taken before the #811 waiver refusal, and that refusal's exception for
      `flaked` — the one answer that stands in the waiver's place. Verified: the case's `result: done` arm, where a red
      tally and no `LOOP-GATE-WAIVED` line took the blocked path before this change.
- [x] 2.3 `tools/loop/state.sh`: `flaked` in the closed vocabulary, the vocabulary settled before the two checks that
      read it, and the same exception in `cmd_done`'s red-close backstop. Verified: the case's `flaked` arm and
      `metrics.gate` assertion — the tally's prose is not rewritten by the field.
- [x] 2.4 Realign `run#aRedGateIsAttributedToTheTreeThatFailsIt`'s "no base tree is paid" assertion: its fake suite now
      records the tree each run of it happened in, and the assertion reads that no `loop-attribute` tree was made —
      the probe re-runs the case in the item's own worktree, so "the case ran nowhere" is no longer the fact it was
      written for. Verified: the case passes (`evidence/red-gate-attribution-case.log`), and its three answers
      (`delta`, `base`, `unattributable`) are unchanged.
- [x] 2.5 Realign `state#aCloseOverARedGateWithoutAWaiverIsRefused`: its refusal message assertion quoted the
      vocabulary, so it names `delta|flaked|base|unattributable` and gains the close a `flaked` answer makes possible.
      Verified: `state#aCloseOverARedGateWithoutAWaiverIsRefused`, `run#aCloseOverARedGateIsWaivedOrBlocked` and
      `run#aRedGateIsBlockedByTheTail` all pass on this tree. `state#aCloseOverARedGateWithoutAWaiverIsRefused` is the
      one that failed before the realignment, on the quoted message alone.
- [x] 3.1 Add the mutation records `861.1`–`861.3` to `tools/loop/tests/loop-mutations.sh`: the probe's second look, the
      run's waiver exception and the state's backstop. Verified: `evidence/mutations.log` — `red-when-mutated: 3 ·
      not-noticed: 0 · restore-failures: 0`, each record red on the case for its own assertion and green again after
      its restore; and `evidence/mutations-check.log` — every selected record still applies to the file it names
      (`3 of 371 records`).
- [x] 3.2 `guidelines/BuildEvidence.md` §4d gains the rule the engine implements: the fourth answer, the one re-run in
      the warm tree, and what `flaked` does to an item. Verified: `bash tools/check-doc-routes.sh` (`all 341 section
      references resolve`), `bash tools/check-doc-values.sh`, `bash tools/check-todo-references.sh` and
      `bash tools/check-portability.sh` pass (`evidence/evidence-check.txt`).
- [x] 3.3 The delta in `openspec/changes/fix-a-flaked-case-is-off-the-blocked-path/specs/agent-issue-loop/spec.md`,
      kind MODIFIED on the requirement "An item's record keeps what the run measured to decide it": the vocabulary
      `delta | flaked | base | unattributable`, the one re-run and its limit, what a `flaked` answer does to a close,
      and one scenario per case the record's tests name. Verified: `tools/loop/evidence-check.sh` over this change
      reports C1 and C5 clean (`evidence/evidence-check.txt`).
- [x] 4.1 The change's own pointers, checked mechanically rather than by reading: `tools/loop/evidence-check.sh
      openspec/changes/fix-a-flaked-case-is-off-the-blocked-path` — C2's quoted tallies are carried by
      `evidence/red-on-HEAD.xml` and `evidence/red-case-green.xml`, and #849's `tests=1016` pair is quoted with its
      "not retained", which is the policy's exemption.
