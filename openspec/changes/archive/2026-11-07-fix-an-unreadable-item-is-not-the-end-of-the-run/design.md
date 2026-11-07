# Design — One unanswered item read is not the end of a run

## The mechanism

On the base `fc9e0eb0`, `tools/loop/run.sh:1878` read the item the run selected — `loop_issue_json "$issue" --comments`,
the one read per item, asking for the comments a recorded decision arrives in — and its failure arm (`:1878–1882`)
called `stop_run "gh could not read #$issue — the tracker did not answer"` and `break`. So the read's failure was the
run's verdict about itself: one item's socket decided whether the budget behind it was spent.

The seam is not the hole the item names, and the probe is what settles that. `tools/loop/lib.sh:35–36` sources
`tools/issues/lib.sh`, whose `gh` function (`tools/issues/lib.sh:94–125`) retries a transport failure three times at
5/20/60 s and then reports `tracker-unreachable` with exit 75. A probe on the base (`evidence/probe-seam.sh`) calls
`loop_issue_json 21 --comments` with the stub CLI failing every `issue view` transiently and reads the seam's own
numbers: `attempts=4`, `status=75`, `stderr-last=tracker-unreachable: gh issue did not answer after 4 attempt(s)`,
`stdout-bytes=0`. What reaches the run's read (`tools/loop/run.sh:1891` in this tree) is therefore not a bypassed
call: the failure the probe produced is the transport's, so the retry has already happened and the seam reported it as
`tracker-unreachable`; what the run lacked is the step after the seam — the pre-screen's rule for the same unknown
(`tools/loop/prescreen.sh:211`, issue #231) applied to the read that happens once per item.

Root cause, in one sentence: the arm that reads the item a run is about to work treats the read's failure as a verdict
about the whole run, where the pre-screen reads the same seam's same exit status for the same candidates as an unknown
about one item.

## The single fix

The arm keeps its read and replaces its stop with a record and a `continue`: the item is recorded
`loop_state done "$issue" --result blocked --verdict unreadable` — the word the pre-screen writes for the same unknown
— with the CLI's own words in the note, the console prints the item and those words, and the queue moves on. This is
the whole production change: one arm, `tools/loop/run.sh:1890–1899`.

Why a run whose tracker answers nothing still ends: `state.sh next` already stops a run on a streak of consecutive
`blocked` records (`tools/loop/state.sh:338`) — the count it reads is the recorded one (`LOOP_REFUSAL_STREAK_STOP`,
`tools/loop/loop.conf:83`; 4 in this tree), not the pair that line used to hardcode (issue #804). An unreadable record
is such a record, so a dead tracker costs that many item records and stops the run with the CLI's words on all of them,
instead of costing one socket the rest of the budget. That is the pre-screen's own bound one layer up: the screen
continues per candidate and stops with a reason when nothing answered (`prescreen#anUnansweredTrackerIsNotAnEmptyQueue`).

## Alternatives the evidence eliminated

- **Source the seam, or give `loop_issue_json` the same three-retry backoff.** Already true on this base:
  `tools/loop/lib.sh:35–36` sources `tools/issues/lib.sh`, and the probe above reads the seam's four attempts and
  exit 75 out of that exact function. A change here would be a second seam beside the first, which is what
  `lib#onlyTheSeamCallsTheRealCli` exists to forbid.
- **Record the stop as retryable, so whatever started the run can relaunch it.** It does not cure the measured cost:
  the run still ends with its budget unspent, and nothing in this tree relaunches a run — the stop is read by a person.
  The state's `run.stop` is the record of an end, not a scheduling input.
- **Retry the read more times.** The seam's 4 attempts are the contract for every call the tooling makes; a second
  loop beside it is the drift `lib.sh`'s own comment names. Extra attempts against a dead tracker add 85 s each, which
  is the cost this change exists to bound, not to grow.
- **Record the item `skipped` instead of `blocked`.** `skipped` is not a refusal, so it does not count toward the
  refusal streak `next` stops on, and a dead tracker would then walk the whole queue: 4 attempts at 5/20/60 s are 85 s
  per candidate, and 85 s × the 69 candidates that run had left is 97.8 minutes — the 108 minutes it lost, spent one
  candidate at a time. The word has to be one the streak counts, which `blocked` is.
- **Add a fifth result word for "never read".** `state.sh done`'s result vocabulary is closed and every derived figure
  — the four tallies, the budget left, the streak — reads it (`tools/loop/state.sh:543–549`; the paragraph on that one
  list is `guidelines/Backlog.recipes.md:785–790`). The verdict field already carries the distinction (`unreadable`),
  so a result word would be a second place to write the same fact and a change to every reader of the tallies.

## Blast radius

- `tools/loop/run.sh:1890–1899` — the arm, and nothing else in the engine changes.
- The run's record for one item: an item the tracker did not answer for now appears in the state as `blocked` /
  `unreadable` with the CLI's words, consumes one item slot, and counts toward the refusal streak. Nothing is written on
  the tracker for it: no label, no comment, no escalation — nothing was decided about the item.
- One case added (`run#anUnreadableItemIsNotTheEndOfTheRun`, `tools/loop-cases.sh`), its registration in the case list
  and the dispatch table, and one mutation record (`tools/loop-mutations.sh` 100.1) that restores the base's stop *and*
  its `break` and takes the case red at its first assertion, with the failing message in the log.
- `guidelines/Backlog.recipes.md` section 7d gains the paragraph that states the rule; the shipped capability gains the
  requirement and its scenario.
- Nothing else: the seam, the pre-screen, the state's vocabulary and the report are untouched, and the loop suite's
  other 369 cases are unchanged in behaviour (the delta selection `--from-git fc9e0eb0` ran all 370 and passed).

## Rollback

One hunk. Restoring the lines the arm replaced (the base's `printf`, `stop_run` and `break`) returns the tree to the
base's behaviour; the case then fails exactly as `evidence/red-on-HEAD.log` records, and mutation 100.1 is that revert
in the tree's own words — it restores the base's stop *and* its `break`, so the case fails at its first assertion with
the message the log carries. Nothing outside the arm depends on the record, so no other file has to move with it.

## Why the record and not the stop

The run already has a rule for "the tracker answered nothing": it ends, and it names what it read. What it did not have
is the distinction between *this item was not answered* and *nothing is answerable* — the distinction the seam's exit
75 carries and the pre-screen acts on. A run that ends on the first unreadable item spends the difference between those
two facts on the budget it has left; recording the item and letting the queue move spends one slot and one retry cycle
on it, and reaches the same stop once the refusal streak is full (four such records in this tree's count).

## Open decisions

none
