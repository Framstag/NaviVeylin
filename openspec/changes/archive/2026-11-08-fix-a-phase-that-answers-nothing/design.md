# Design — the phase's answer is read where the phase ends

## Open decisions

none

## Root cause

The gate phase is a child of its own since #758, and the run reads its report in exactly one place: `tail_after_gate`
(`tools/loop/run.sh:1734`), which reads `LOOP-GATE` out of it and returns 1 when the line is absent. That return is the
right answer for the case it was written for — a report that answered *for the item* (a skip, a falsification, a child
the run ended) has no gate line and must start no tail — but it is the same return an empty file takes, and nothing on
the path asks whether the phase answered at all:

- on the base, `run.sh:2339` (the phase's step) writes from `gate_exit` alone, so a 0-byte report is recorded `ok`;
- `final_report` stays that empty gate report (`run.sh:2369`), so `verdict_claim`, `change_claim` and `root_cause` are
  all empty;
- `head_now` differs from the base, because the change child's tree was committed at the diff, so the "nothing was
  committed" arm declines and the "no archived change appears in the run's diff" arm fires (`run.sh:2541`).

Item #755 is that path with its numbers: exit 0, a 0-byte `worker-gate-report.md`, `11 m 49 s` and `13.0 Mtok` charged,
the record naming the item's tree. Two other shapes reach the same arm through the same silence — a child killed
mid-report, and a report written to a path the run does not read — so the mechanism is the missing reading, not the
empty file.

## The single fix

Five sites in `tools/loop/run.sh`, no schema, no prompt text and no function signature changed:

1. `gate_answered` (`:1056`) — the completeness reading, on `answer_complete` (the same rule the review rounds follow,
   #224) with the gate prompt's own closing lines `LOOP-GATE` / `LOOP-VERDICT`. A report carrying `LOOP-TIMEOUT` or
   `LOOP-DEADLINE` is excluded from the reading, and the retry's own guard is the exit status: a child the run ended
   at a bound never returns 0 (`LOOP_CHILD_TIMEOUT`, `tools/loop/lib.sh:27`), so it is never retried and the bound is
   not spent twice.
2. `phase_failure=""` (`:2195`) beside `empty_tree_reading`, so the guards and the outcome block read a variable that
   has a value in every shape.
3. The phase's own block (`:2291`–`:2341`): the prompt's values are built once into `gate_prompt_values`, so the retry
   is rendered from the same list and cannot drift from the first rendering; an answer that is not an answer is asked
   once more (same prompt contract, `worker-gate-prompt-retry.md` → `worker-gate-report-retry.md`); the second silence
   sets `phase_failure` and the step's note to `exit 0 and no report`, and the step's status follows the reading rather
   than the exit status.
4. The tail's guard (`:2355`) carries `[ -z "$phase_failure" ]`: a phase whose answer never arrived has neither a gate
   line nor an item verdict, and a tail started on it would archive nothing.
5. The outcome block's arm (`:2532`) — after the tail's own verdict arms and before the tree arms — records the item
   `blocked` with `blocked_reason="$phase_failure"`, so the reason a person reads names the phase.

## Alternatives the evidence eliminated

- **Reading the empty file as the run's own "could not start" stop** (`run.sh:2452`, #742). Rejected by the item's own
  evidence: that stop requires a tree at the base, and #755's branch carries a commit. Making the stop unconditional
  would discard the tree record for work that was done and kept, which is the opposite of what #742 records.
- **Treating a missing gate line as a red gate** (`LOOP-GATE: not run`). Rejected: the run would claim a gate it never
  ran, and `LOOP-GATE` is the one line a tail is started from — the false claim is exactly what the gate prompt refuses
  to print.
- **Retrying the whole item.** Rejected: the change child's work is in the tree and committed; only the phase's answer is
  missing, and a second item would pay for that work twice.
- **Two retries, or a retry of a phase the run ended.** Rejected on the same rule the rounds already record: one retry is
  what #224 measured as the transport's shape, and a bound-ended child never returns 0
  (`LOOP_CHILD_TIMEOUT`, `tools/loop/lib.sh:27`), so the retry's exit-status guard leaves it alone.

## Blast radius

- `tools/loop/run.sh` — the five sites above, in one file.
- `tools/loop-cases.sh` — one case (list, dispatch, body).
- `tools/loop-mutations.sh` — one record added (101.1) and one re-pointed (76.1, which quotes the tail's guard line and
  would otherwise report `MUTATION-PATTERN-STALE`).
- `openspec/specs/agent-issue-loop/spec.md` — one requirement modified: one added scenario, and the four it already
  carries given the case each already has.
- `guidelines/Backlog.recipes.md:719` "Refusals, records and the landing" gains one paragraph.

## Rollback

`git revert` of the change's commit restores the base's behaviour exactly: the five sites are additive except the
tail's guard, the phase step's status and its note, and both of those are read by one case each (76.1's tail case
and the new case). Nothing written by a run — state, evidence, archive — is read differently after the revert,
because `phase_failure` is a variable of one item's own turn.
