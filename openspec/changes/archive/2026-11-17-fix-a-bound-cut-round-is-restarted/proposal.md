# A review round a bound cut is restarted, not handed forward

## Why

**Root cause.** A review round the run's own bound ended — the reviewer's turn bound, or the item's context bound
inside the reviewer phase — is recorded `cut`, and the item is left for the next run. That is right about the
*item*: a bound the run enforces is the run's budget and not a finding. It was wrong about the *round*: the bound
had ended an unfinished **reading**, and the two things a reader needs — that the round read nothing and that a
fresh session could have read it — were recorded nowhere, so the same change was read again from scratch by the
next run or by a person.

**Evidence, from the item (issue #918, read from the run its record cites).** #861's review round ended at
`LOOP_CHILD_TURNS_MAX_REVIEWER=110` turns, and **none of the 110 turns was a refusal**: the item's record reads
`stopReason: bound` with `note` naming the bound, `.pi/issue-loop/items/861/review-1.diff` stands afterwards and no
report does. The round had taken 21.08 M of that run's 65.21 M cache-read (32.3 %) and 70 m 26 s of its 209 item
minutes (33.7 %), on a diff its own worker had spent 329 turns and 461 calls to make. So the engine's most
expensive item of that run was the one it could not finish, and the item became a person's round.

**The single fix, as it lands.** A round a bound ended is **restarted once in the same run**
(`LOOP_REVIEW_RESTART_MAX=1`, `tools/loop/loop.conf`), in a fresh session on the same bounded input — the diff and
inventory `LOOP_REVIEW_INPUT_MAX_BYTES` already capped (#924) — and it is **handed what the cut round had written**:
its `BLOCKING:`/`NOTE:` lines, the path they were written to, and the report of the round before the gate when
there was one. Only a **second** bound is the item's `cut`, so the ceiling that keeps the loop from being a loop is
the restart count and not the bound. The restart is not started past the run's own deadline (a child begun past the
clock is ended on its first second and its `LOOP-DEADLINE` reason would replace the bound's, the distinction #829
and #785 paid for), it does not count a second review round, and it does not re-announce the round on the branch.
The item's record keeps it — `state.sh done --review-restart`, `metrics.reviewRestart`, printed by the report as
`rounds a bound cut and the run restarted:` — because a round that answered after a restart must not read as a round
that answered first time.

**The arm not taken, and why.** The item's other arm was to derive the reviewer's turn bound from the diff it must
read — the files the pre-gate round named and their per-file ceiling. It needs a measured reading rate per turn,
which this archive does not carry, and since the input is capped, a derivation would return the constant it is
derived from in other clothes. Both reasons are recorded beside the key in `loop.conf` and in the change's
`design.md`.

**The red test.** `run#aBoundCutRoundIsRestartedWithItsFindings` (`tools/loop/tests/loop-cases.sh`) ends round 1 at
a recorded bound of 3 turns after the round wrote a `BLOCKING:` line, then asserts: the item **closes** on the
restart's answer, `metrics.reviewRounds` stays 1, no round records `metrics.roundBound`, the restart's prompt exists
and carries the cut round's finding, the bound's line and the pre-gate report, it is not the transport's `-retry`
shape, and `metrics.reviewRestart` and the report name the round, the bound and the file. On the tree before this
change the round is cut and the item never closes. Three mutation records restore the old behaviour:
`918.1` (the guard that starts a restart), `918.2` (the findings the restart is handed), `918.3` (the line the
record keeps).

**Owning requirement.** `agent-issue-loop` gains one requirement — a round a bound ended is restarted once and only
a second bound is the item's cut — and the shipped requirement "The diff is read before the gate, and a fresh child
runs the one gate" is **modified**: its scenario "A round the run cut before the gate is left for the next run"
now names the second bound as the cut and the restart as what happens first, with every other scenario carried
verbatim.

**Owning guideline section.** `guidelines/Backlog.recipes.md` §7b (the rule beside the run's other bounds) and §7e
(what a bound cut is, and what it is not).

## Surface

`tools/loop/run.sh` (the restart: the key, `review_restart_scope`, `review_restart_before_deadline`,
`review_restart_note`, the pre-gate arm and the post-gate round's attempts loop), `tools/loop/loop.conf` (the key and
its derivation), `tools/loop/prompts/reviewer.md` (`{{REVIEW_RESTART}}`), `tools/loop/state.sh` (`--review-restart`),
`tools/loop/report.sh` (one line), `tools/loop/tests/loop-cases.sh` (one case, two cases extended),
`tools/loop/tests/loop-mutations.sh` (three records), the guideline paragraphs, and this delta.

## What Changes

1. **`LOOP_REVIEW_RESTART_MAX`** (`loop.conf`): one figure with the measurement it is priced from, `0` or absent
   meaning no restart — the pre-#918 shape.
2. **`review_restart_scope`** (`run.sh`): the section a restarted round reads — the round, the bound's line, the
   findings the cut round wrote, the round before the gate's report.
3. **The attempts loop** (`run.sh`): the round after the gate and the round before it start a second child when a
   bound ended the first, and the *final* report is what the guards, the arms and the verdict read.
4. **The record and the report**: `metrics.reviewRestart` one line long, and the line the report prints from it.
5. **The case, the three mutation records, the guideline paragraphs and the delta** as above.

## What this does not do

- It does not raise `LOOP_CHILD_TURNS_MAX_REVIEWER`, and it does not derive a bound from the diff: the value stays
  the archive's p95-or-above figure the configuration states beside it.
- It does not restart a round the **transport** cut: that round is asked once more under its `-retry` name, as
  `#224` established, and the two are told apart by which name the prompt carries.
- It does not restart a round the run's **deadline** ended, and it does not restart an item's own child — a worker's
  continuation is `#851`'s rule and stays there.
- It does not make a restarted round cheap in absolute terms: it bounds how many times **one round** may be
  restarted, not what one bounded round costs. That figure is `LOOP_REVIEW_INPUT_MAX_BYTES`'s (#924).
