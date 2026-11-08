# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The reader

- [x] 1.1 Add `tools/loop-quota.sh`: read the balance with the owner's token, compare it against the two floors and
      against a pacing line drawn from the fraction of the window elapsed, and answer with the protocol's statuses
      (`0` go, `3` hold with the reason and `resume-at <epoch>`, anything else "the reading could not be made").
      Verify: against four fixtures (a spent weekly window, a mid-week one, one ahead of the line, one carrying no
      quota fields) the exits are 3, 3, 0 and 0, and the recorded reason names the numbers it compared.

## 2. The engine

- [x] 2.1 Name the two statuses in `tools/loop/lib.sh` — what `state.sh next` returns for a hold (6) and what a
      reader returns for one (3) — so neither half carries the other's literal.
- [x] 2.2 Ask the recorded command in `state.sh cmd_next` after every condition the state holds, passing the three
      thresholds in its environment, turning its hold into exit 6 with its lines kept verbatim, and naming any other
      failure on stderr as no refusal. Verify: tasks 3.1, 3.2 and 3.4 pass, and the candidate is still printed when
      nothing holds.
- [x] 2.3 Add `state.sh hold --seconds N --reason TEXT`: move `run.deadlineEpoch` and `run.deadlineAt` by the wait,
      append it to `run.holds`, and print the new deadline. Verify: task 3.3, and `state.sh show` prints the pause
      total once a hold is recorded.
- [x] 2.4 Pause instead of stopping in `run.sh`'s item loop: split the reader's lines, sleep until the instant (or
      the recorded poll interval when the reader names none), take the wait itself when it is longer than the
      recorded cap, and write the pause **before** it sleeps. Verify: task 3.5.
- [x] 2.5 Record the six keys in `tools/loop/loop.conf` with the measurement each rests on, the command empty in the
      configuration that has no reader. Verify: `bash tools/loop-selftest.sh --group state` is green and
      `bash tools/check-portability.sh` still reports no project value in an engine.
- [x] 2.6 Make the reader switchable for one run: the key is written as a fill-if-unset expansion (a **set-but-empty**
      environment value wins, as `LOOP_BASE_REF` does), `run.sh` gains `--no-quota` which empties it in the run's own
      process, and the run's header carries `quota=<reader>` or `quota=off`. It exists because the reader answers for
      one provider and the engine may not name which provider a run spends. Verify: task 3.10, and
      `bash tools/check-portability.sh` still passes (the engine names no provider).

## 3. The cases

- [x] 3.1 `state#aQuotaWindowIsAHoldNotAStop`: the reader's hold is exit 6, its reason and instant travel verbatim,
      and no stop is recorded.
- [x] 3.2 `state#aQuotaCheckThatCannotBeMadeIsNotARefusal`: a reader that exits 4, and one that is not there, both
      hand out the next candidate.
- [x] 3.3 `state#aHoldMovesTheDeadlineWithIt`: a spent budget stops the run, the same run after a recorded hold
      goes on, and the deadline moved by exactly the seconds it took.
- [x] 3.4 `state#noQuotaCommandAsksNothing`: an empty key starts no process at all.
- [x] 3.5 `run#aQuotaHoldPausesTheRunAndResumesIt`: end to end, a project whose own reader holds once — the run
      prints the pause, records it, works the item, and ends on its cap. The reader is written at the path the
      configuration names and committed, because `run.sh` takes `LOOP_CONF` out of every child's environment
      (issue #277) and an untracked file would make the project dirty (issue #284).
- [x] 3.6 Declare the files each case can reach on its `# files:` line. Verify:
      `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` passes.
- [x] 3.7 Narrow `state#theStreakIsTheRecordedCount` to read stdout only for its two exact-equality assertions.
      The engine now names a quota reader it could not run on **stderr** (a throwaway project carries no reader),
      and that case merged the two streams — so a note about the project's configuration was read as part of the
      queue's answer. Verify: the full suite is green, the case's three reads still assert the streak.

## 3b. The report

- [x] 3.8 `tools/loop/report.sh`: subtract the recorded waits from the summary's spend figure, name them beside it,
      and floor the figure at zero. Verify: the case below, plus a re-read of the rule `BuildTests.md` §6e
      (a case may not spend two hours waiting to read a number, so the case rewinds the state's window by hand).
- [x] 3.10 `run#theQuotaSwitchIsExplicit`: in three projects, each carrying a committed reader that records that it
      was asked — the configuration armed (the header names it and the reader is asked), `--no-quota` (header
      `quota=off`, the reader is not asked), and a set-but-empty `LOOP_QUOTA_CMD` in the environment (the same).
- [x] 3.9 `report#theSpendIsWorkNotWaiting`: two hours of clock with one of them waited must report an hour of work
      and `budget left 3 items / 30 min`, with the wait named. Verify: the mutation below turns it red with
      `spent 120 min · budget left 3 items / -30 min`, which is the defect it guards.

## 4. The falsification

- [x] 4.1 Add six records to `tools/loop-mutations.sh`, one per invariant (the hold's status, a failed reading
      staying no refusal, an empty key, the deadline moving, the run pausing rather than stopping, the spend
      excluding the wait). Verify: `bash tools/loop-mutations.sh --check --only 'quota-hold.1 … quota-hold.6'`
      reports that every record applies.
- [x] 4.2 Run them: each must go red for its own assertion and its restore green again. Verify:
      `bash tools/loop-mutations.sh --only '<the labels>'` reports `red-when-mutated: 8 · not-noticed: 0 ·
      restore-failures: 0`.
- [x] 4.3 Add the two switch records: `quota-hold.7` (the flag emptying the key) on `run.sh` and `quota-hold.8` (a
      set-but-empty environment value winning) on `loop.conf` — the second shows both switches rest on the same
      expansion. Verify: both go red (`quota=off` missing; the skipped reader was still asked) and restore green.

## 5. The gate

- [x] 5.1 `bash tools/loop-selftest.sh` over the whole set, because the delta owns `tools/loop/**`.
- [x] 5.2 `bash tools/check-portability.sh`, `bash tools/check-doc-routes.sh` and
      `bash tools/check-openspec-hygiene.sh`.
