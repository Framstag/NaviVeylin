# Design — an item the tracker does not answer for ends no run

## Root cause

`tools/loop/run.sh:1878-1882` (the base's) reads the record of the candidate it just took from the queue and treats
a failure of that read as a failure of the run: the `||` arm calls `stop_run` and breaks out of the item loop. The read is
one candidate's record, needed only to start that candidate — nothing in the tree has been touched, no child has
been started, and the item itself is unknown rather than refused — so the failure is the wrong unit. The seam
underneath it (`tools/issues/lib.sh:94-125`) has already spent the recorded retries (5 s, 20 s, 60 s) and reported
`tracker-unreachable`, which is why the run's arm sees a settled failure rather than a flaky one: the cost of
answering "read it again" is one more read, and the cost of answering "end the window" is a run's remaining budget
(1 h 9 min of 480 spent, 12 candidates unread, measured 2026-11-03, issue #753).

## The one fix

1. `tools/loop/state.sh` gains `unreadable <issue> --reason TEXT`: it refuses an issue that is not one candidate of
   this run's queue, removes that candidate's queue entry, and appends the candidate to `.excluded` as
   `{issue, class:"", verdict:"unreadable", reason, evidence:"", alreadyEscalated:false}`. `.excluded` is the
   bucket the run already keeps for a candidate it did not work and decided nothing about — the pre-screen writes
   the same fact for the same candidate at `tools/loop/prescreen.sh:217-228`, with the same verdict word and the
   same reason shape, so the run reads one vocabulary for one fact.
2. `tools/loop/state.sh`'s `cmd_queue` carries that record over the next pre-screen's rewrite of `.excluded` and
   keeps its issue out of the queue: `kept` already exists to hold what the run owns across a re-screen (the
   adoption's `unmergeable`), and a candidate the run could not read is the second such record — the screen's own
   answer is newer than the record but not the same fact, and the run must not read the same candidate again in
   the same window.
3. `tools/loop/run.sh:1888-1900` reads through a temporary stderr file (the seam's own words are the record's
   note), and on failure prints the line the pre-screen prints one level up, records the candidate through the new
   subcommand, and `continue`s the item loop: the next turn's `loop_state next` answers the next candidate, and the
   run's stop stays what `state.sh:324-381` decides — the queue, the clock, the item cap and the refusal streak.

Nothing else moves: the item's `items[]` record, the decision queue, the labels, the report and the refusal streak
are untouched, because nothing about the item was decided — the candidate loses its queue entry and gains an
exclusion, which is the two fields the run already owns.

## Blast radius

- `tools/loop/run.sh` — one arm of the item loop (`:1878-1900`), plus the temporary file it reads the seam's
  message from.
- `tools/loop/state.sh` — one new subcommand and its dispatch entry, plus `cmd_queue`'s carry-over of the record
  (`state.sh:396-415`); no existing command changes shape and no other key of the state is read.
- `report.sh` and `state.sh show` already print `.excluded` (`report.sh:189`, `state.sh:316`), so the record is
  visible in the run's own report as `excluded before any work: #21 unreadable` with no change to either.
- A later pre-screen pass in the same run rewrites `.excluded` from its own records (`state.sh:396-415`). The
  run's record survives that rewrite and its candidate stays out of the queue, which is what the case's own
  second pass exercises: the run's screen runs again after its item and answers `2 eligible` for the same two
  candidates, and the state still holds `#21 unreadable` with `#21` absent from the queue.
- `tools/loop-cases.sh` — one case, `run#oneUnreadableItemDoesNotEndTheRun`, in the list, the dispatch and the body.

## Alternatives the evidence eliminated

- **`done --result skipped --verdict unreadable`.** `state.sh:336-345` reads the item cap as `.items | length`, so
  recording this candidate there spends a slot of `run.items` on an item the run never worked — the stop would no
  longer be "the queue and the clock" but the cap, and `report.sh:81` would count a verdict about an item nobody
  knows anything about. The pre-screen records the same fact *outside* `.items` for that reason.
- **Leaving the entry in the queue and reading it again next turn.** The seam has already made four attempts; a
  turn that retries a settled answer is a loop that ends on the run's bound instead of on its queue.
- **Ending the run only when every remaining candidate is unreadable.** That is a statement about the whole queue,
  which the run does not hold — it holds one candidate at a time — and the pre-screen's own refusal
  (`prescreen.sh:599-601`) is the arm that already speaks for an unanswered tracker.

## Rollback

Revert the two hunks: the `if` at `run.sh:1888` returns to the `||` arm that stops the run — the read the base made
at `:1878-1882` — and the `unreadable` subcommand becomes dead code with nothing calling it. No state outlives a run
(`.pi/issue-loop/run.json` is
rewritten by the next run, `state.sh:193-283`) — and the case is the only assertion of the new behaviour, so a
revert is red in exactly one case.

## Open decisions

none
