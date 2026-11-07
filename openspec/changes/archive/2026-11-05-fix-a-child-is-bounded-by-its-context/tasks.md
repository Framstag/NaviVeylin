# Tasks — fix-a-child-is-bounded-by-its-context

- [x] 1.1 `tools/loop/run.sh`: `child_context()` added beside `child_turns()` — the largest `input + cacheRead` a
      usage record reports and the greatest `compaction_end`'s `result.tokensBefore`, read from the complete lines
      appended since the last look (`wc -l` for the boundary, `tail -n +N | head -n K` for the new lines), with the
      numeric guards the other bounds have. A stream that is absent, empty or unreadable answers nothing rather than
      an error.
      Verify: `bash -n tools/loop/run.sh`, and the two cases below drive it through the real wait.
- [x] 1.2 `tools/loop/run.sh`: `item_context_warn` and `item_context_max` read at the top like `item_turns_max`,
      validated the same way, default `0`; both named in the header after `item-turns-max=`.
      Verify: `bash -n tools/loop/run.sh`, and `run#theRecordedZeroKeepsTheContextBoundOff` asserts the header field.
- [x] 1.3 `tools/loop/run.sh`: `run_child`'s wait keeps `ctx_max` and takes the checkpoint (`LOOP-CONTEXT:` on the
      run's own output, once) and the stop (`ended_by=context`, `LOOP-TIMEOUT` naming the bound and the reading) in
      the whole-second block beside the deadline, the cap and the turn count; the `LOOP-TIMEOUT` chain gains the
      context arm ahead of the clock's.
      Verify: `run#childPastItsContextBoundIsBlocked`.
- [x] 2.1 `tools/loop/loop.conf`: `LOOP_ITEM_CONTEXT_WARN=143124` and `LOOP_ITEM_CONTEXT_MAX=200000` recorded with
      the archive measurement that derived them, the `0` switch, and the sentence that the session split at the
      checkpoint is the cheaper half this bound does not take.
      Verify: `grep -n '^LOOP_ITEM_CONTEXT' tools/loop/loop.conf` names both keys, and both cases assert the values
      they carry.
- [x] 2.2 `guidelines/Backlog.recipes.md`, section 7b: the paragraph beside the turn bound — the unit, the measurement, both
      numbers, the checkpoint and the stop, the `0` switch, and what it does not replace.
      Verify: `grep -n 'LOOP_ITEM_CONTEXT' guidelines/Backlog.recipes.md`; `tools/check-doc-routes.sh` still resolves.
- [x] 2.3 `guidelines/BacklogTooling.md`, section 11: the two keys in the profile table.
      Verify: `grep -n 'LOOP_ITEM_CONTEXT' guidelines/BacklogTooling.md`.
- [x] 3.1 `tools/loop-cases.sh`: `run#childPastItsContextBoundIsBlocked` and
      `run#theRecordedZeroKeepsTheContextBoundOff` added, listed in `LOOP_SHELL_CASES` and in the dispatcher.
      Verify: `./tools/loop-selftest.sh --list | grep ContextBound` names both, and
      `driver#everyCaseDeclaresTheFilesItCanReach` stays green for both declarations.
- [x] 3.2 The red cases pass on the applied tree, and the two turn-bound cases still pass unchanged.
      Verify: `./tools/loop-selftest.sh --case 'run#childPastItsContextBoundIsBlocked'`,
      `--case 'run#theRecordedZeroKeepsTheContextBoundOff'`, `--case 'run#childPastItsTurnBoundIsBlocked'`,
      `--case 'run#theRecordedZeroKeepsTheTurnBoundOff'` — four `PASS` lines and four 1-of-1 summaries; the red runs
      are in the evidence directory.
- [x] 4.1 The change's pathspec is verified by the checks `./tools/loop/measure.sh --change
      fix-a-child-is-bounded-by-its-context` names — those are the gate's, not this change's, and this session runs
      the focused ones its own tasks name and hands the rest to the gate child. **The marker is the deferral, not a
      result this session measured**: the named checks ran in the gate child, whose log is `evidence/gate.log`.
      Verify: the gate child's log carries each named check, and its one failure is the attribution in
      `evidence/gate-attribution.md`.
- [x] 4.2 The archive-and-gate steps are not this change's: the run defers the gate to a fresh child, so this
      session ends at the diff and the tasks above stop at it.
      Verify: `git status --porcelain` names only the files this change lists, and nothing is archived here.
- [x] 5.1 The pre-gate reviewer's one blocking finding is applied: `blocked_reason` and `pre_gate_refusal` keep the
      words `turn bound` (`at one of the item's spend bounds (its turn bound or its context bound)`), so the shipped
      case that reads the item's note stays green.
      Verify: `./tools/loop-selftest.sh --case 'run#aReviewerEndedAtItsTurnBoundIsNotARefusal'` → `PASS`, recorded in
      `evidence/green-review-case.log`.
- [x] 5.2 The reviewer's notes are applied where they were claims the record could not back: the provider-window
      sentence, the `40 k / 240 k` pair the archive does not carry, the item body's `227 items at 78 k` figures, the
      two missing red-log pointers, the `tests=2 failures=2` roll-up, the `megabytes per item` estimate and the
      §7b index that named two bounds are all gone or stated with the command that produced them.
      Verify: `grep -rn "every provider this project runs sits at\|megabytes per item\|40 k or 240 k\|red-bound-on-head" tools/loop/run.sh tools/loop/loop.conf guidelines/Backlog.recipes.md guidelines/Backlog.md guidelines/BacklogTooling.md` prints nothing.
- [x] 5.3 The delta now carries the MODIFIED blocks the change owed: the two shipped scenarios that enumerated the
      turn bound as the only spend bound are restated with theirs (every other scenario of both requirements kept
      verbatim), and the ADDED requirement's body is under the validator's 500 characters.
      Verify: `openspec validate fix-a-child-is-bounded-by-its-context --strict` → valid.
