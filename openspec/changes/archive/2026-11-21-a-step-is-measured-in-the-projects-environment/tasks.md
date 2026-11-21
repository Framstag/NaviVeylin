# Tasks

## 1. The environment a step is measured in

- [x] 1.1 `tools/loop/gate.sh`: `item_session_vars` declares the seven facts a child of the item is handed and the
      base-red switch, and the step launch removes them from the step's environment; the gate keeps them for its own
      reading. — done: `gate#aStepIsNotHandedTheItemsSession` asserts it.
- [x] 1.2 Measure the two causes #980 named before fixing either: `LOOP_CONF` handed to the measured step is *not* the
      cause (both cases pass at the base with it exported); the cause is the child's session environment —
      `LOOP_BASE_RED=off` and the item's `LOOP_TOOL_RESULT_LEDGER` among the seven. — done:
      `evidence/measurement-980.txt`, and both arms reproduced on HEAD.

## 2. The case

- [x] 2.1 `tools/loop/tests/loop-cases.sh`: `gate#aStepIsNotHandedTheItemsSession` starts a gate in a child's full
      environment and asserts the step saw `<unset>` for each of the seven. — done: red on HEAD
      (`evidence/red-on-HEAD-980.log`), green on the change (`evidence/focused-cases.log`).
- [x] 2.2 The leg's own two names, driven through the patched gate in the same child environment: `tests=0
      failures=0`, both green; through HEAD's gate, `tests=2 failures=2` with the recorded messages. — done:
      `evidence/red-on-HEAD-980.log`, `evidence/green-with-the-change-980.log`.

## 3. The mutation

- [x] 3.1 `tools/loop/tests/loop-mutations.sh`: record `980.1` drops the removal, so the step is handed the ledger
      again and the case must fail on what it saw. — done: red → restore → green (`evidence/mutation-980.1.log`).
- [x] 3.2 The record an earlier change left on the same line — `63.1`, which quotes the step launch — was rewritten to
      the launch's new shape, so the set stays applicable *and* the record still pins its own invariant: `--only 63.1`
      red on `the step ran on after its command exited`, then green (`evidence/mutation-63.1.log`).

## 4. The gate

- [x] 4.1 `tools/loop/tests/loop-selftest.sh --jobs 14` over the **whole** set — `tools/loop/tests/loop-cases.sh` is a
      core file, so the declaration list's own hunks owe every case: 519 of 519 green (`evidence/full-suite-2.log`,
      586.40 s). The first run recorded two reds (`evidence/full-suite.log`, `2 of 519 cases failed — 568.05 s`):
      `mutation#everyDeclaredRecordApplies`, `MUTATION-PATTERN-STALE 63.1` — this change's own doing, and the reason the
      record was rewritten (task 3.2) — and `run#childUsageIsRecordedPerItem` (`the worker phase is two children, not
      one: expected '2', got '3'`), which passes alone and passes in the second run: a scheduling flake under 14 jobs,
      in a case whose declared files this change does not touch. The second run's 519/519 also covers the rewritten
      `63.1`.
- [x] 4.2 `tools/loop/tests/loop-mutations.sh --check` over every record: 440 of 440 apply
      (`evidence/mutation-check-full.log`); over the two files whose launch lines this change touches, 28 of 28
      (`evidence/mutation-check.log`).
- [x] 4.3 `openspec validate a-step-is-measured-in-the-projects-environment --strict`: valid.
- [ ] 4.4 The measurement #980 asks for: the next run's `.pi/issue-loop/base-red.log` carries no entry for the leg
      `tools/loop/tests/loop-selftest.sh --from-git {base}`, and a gate's verdict carries no `— base-red:` line for
      it.

## 5. The review round

- [x] 5.1 An independent read-only reviewer read the diff, the seven-fact list and the two arms around them:
      `evidence/review-round-1-report.md` — FAIL, 4 should-fix and 6 nit, all in the claim layer; the code, the case's
      reach and the two records were found sound.
- [x] 5.2 Findings applied (`evidence/review-round-1-answered.md`): the case now derives its names from the gate's own
      declaration and checks the adapter's exports against it, the six-base figure and the two log citations are
      corrected, `63.1`'s red run is evidenced, the first run's two reds are both named, and the class is stated as
      closed. One finding is deferred as a second change (`LOOP_STATE_FILE`/`LOOP_WORKTREE`), with the measurement it
      would owe.
- [x] 5.3 The gate re-run over the **reviewed** tree, whole set: 519 of 519 cases green
      (`evidence/full-suite-review.log`, 564.84 s), which covers both records' applicability;
      `openspec validate --strict` valid.
