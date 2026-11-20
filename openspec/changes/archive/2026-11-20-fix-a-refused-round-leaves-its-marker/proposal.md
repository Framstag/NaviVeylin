# A refused round leaves its marker when the answer arrives

## Why

A review round that answers *no* is recorded on the item's branch only when that round is the **last** one the run
ever runs: `tools/loop/run.sh:4114` writes `review-failed-round-<n>.md` after the review loop, in the arm that knows
the round was final (`run.sh:4106`, `if [ "$review_verdict" = "FAIL" ]`). A round that refuses and is then repaired
writes nothing — `run.sh:3978` writes the *pass* beside the round's announcement and there is no arm for the refusal,
and the comment above it (`run.sh:3970-3975`) stated the design: "The refusal arm is written after the loop
(`record-review-failure`), where the run knows the round was the last one" (the sentence this delta replaces). So a
repaired refusal leaves one announcement per round and no record that an earlier round said no.

The archive is the measurement: **156** `review-round-1-requested.md`, **76** `review-round-1-passed.md`, **62**
`review-round-2-requested.md`, and **2** `review-failed-round-1.md`. Nineteen `review-failed-round-*.md` in the whole
archive against roughly 80 rounds that did not pass at their first round. The refusal rate (62 of 156 = 40 %) is
derivable only by subtracting one marker from another, and only where both survived — which is why the adherence
measurement this item came out of could not answer "did this change fail review, or was it cut?" by reading markers.

The same defect makes `guidelines/Backlog.recipes.md:1617` (section 7e of that file: "a refusal whose **rework** the
bound ended keeps both the announcement and the refusal") untrue for a round-1 refusal: the rework cut writes
`review-round-<n>-rework-cut.md` beside an announcement whose refusal was never written.

**Root cause** — the refusal marker is written by the loop's *exit* arm rather than by the arm that reads the
round's answer, so it exists only for the round the run leaves on.

**Red test** (2026-11-20T00:14Z, `red-on-HEAD-975.log` in `.pi/issue-loop/items/975`, transcribed as this change's
`evidence/red-on-HEAD-*.xml`):
`run#aRefusedRoundLeavesAnAnswerBesideItsAnnouncement` — a fixture whose round 1 refuses and whose round 2 passes —
fails on HEAD with `loop-selftest: 1 of 1 cases failed`, `tests=1 failures=1`: the branch carries
`review-round-1-requested.md` and `review-round-2-passed.md`, and no `review-failed-round-1.md`. A second red case,
`worktree#aRepairedRefusalDoesNotKeepALandedBranch` (the same run), shows the
marker's reader from the other side: `kept fix/issue-21-a-repaired-refusal — the reviewer refused it` for a branch
whose last round passed and whose work the base carries.

**Owning capability**: `agent-issue-loop` — "A review round is a record on the branch, from its announcement to its
answer" (`openspec/specs/agent-issue-loop/spec.md:1959`) and "The run's end sweeps the branches it made"
(`spec.md:3030`).

**Owning guideline section**: section 7d "Refusals, records and the landing" of `guidelines/Backlog.recipes.md`
(the sweep's refusal reading, `guidelines/Backlog.recipes.md:1471`) and its section 7e "Leftovers, the report and
the queue" (the rework cut keeping the refusal, `guidelines/Backlog.recipes.md:1617`).

**Surface** — `tools/look.sh 'review-failed-round'` (79 matches in 42 files, this change's own directory included:
1 in `run.sh`, 8 in `worktree.sh`, 15 in `loop-cases.sh`, 1 in `guidelines/Backlog.recipes.md`, the rest in archived
changes) and `tools/look.sh 'branch_carries_refused_review'` (9 matches in 5 files: 3 in `worktree.sh`, 1 in an
archived design, 5 in this change's own prose):

surface: 2 cases added (run#aRefusedRoundLeavesAnAnswerBesideItsAnnouncement, worktree#aRepairedRefusalDoesNotKeepALandedBranch), 2 rewritten (run#recordsAFailedReviewOnTheBranch, run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted), 2 guideline sentences realigned (sections 7d and 7e) — `tools/look.sh 'review-failed-round'`; `tools/look.sh 'branch_carries_refused_review'`

## What changes

1. `tools/loop/run.sh` — the arm that reads a round's answer writes the refusal too, beside the announcement, for
   every round and not only the last. The write the run already makes when it leaves on a refusal stays where it is.
2. `tools/loop/worktree.sh` — `record-review-failure` is idempotent: a round already recorded writes no second commit
   and does not fail on git's "nothing to commit".
3. `tools/loop/worktree.sh` — the sweep reads a branch's **last** round answer (`carried_review_state`) instead of any
   `review-failed-round-*.md` it can find, so a refusal a later round repaired neither keeps a landed branch for ever
   nor is printed as its verdict.

## What does not change

- `rework-cut` stays an addition: it withdraws neither the announcement nor the refusal, which is the shape
  section 7e of `guidelines/Backlog.recipes.md` already states.
- No round's *cut* semantics move: `review-round-<n>-cut.md` still withdraws its announcement, and a cut round still
  writes no refusal.
- The report's own review line (`report.sh`) is not touched: it reads the run's state, not the archive, and the
  refused-round count it could carry is a separate surface this delta does not own.
