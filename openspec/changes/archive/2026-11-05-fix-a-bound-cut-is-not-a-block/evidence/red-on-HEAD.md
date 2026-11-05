# The red case on `main`, before the change

Both cases were run against the engine of `main` at `caf44b92` with only `tools/loop-cases.sh` and
`tools/loop-mutations.sh` taken from the change — the case bodies are the new assertion, the engine is the tree
the case is owed against.

## `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

```
$ git stash push -- tools/loop/run.sh tools/loop/state.sh tools/loop/worktree.sh tools/loop/report.sh
$ bash tools/loop-selftest.sh --case 'run#aReviewerEndedAtItsTurnBoundIsNotARefusal'
  the item is recorded cut, not blocked: its work finished and the round the run ended is nobody's question: expected 'cut', got 'blocked'
FAIL run#aReviewerEndedAtItsTurnBoundIsNotARefusal
```

The item on `main` was recorded `blocked` and escalated with `agent/needs-decision`, and the branch carried
`review-round-1-requested.md` — so `branch-state` read `review-unanswered`, a question only the item's owner could
answer, for a round the run itself had ended.

## `state#nextHonoursEveryStopCondition`

```
$ bash tools/loop-selftest.sh --case 'state#nextHonoursEveryStopCondition'
  the next candidate stands behind them: expected '23', got '21'
FAIL state#nextHonoursEveryStopCondition
```

`state.sh done --result cut` is refused on `main` (`done: --result must be done|skipped|blocked|falsified`), so the
two items the case records as cut were never recorded and the queue handed out the first candidate again. The
fifth result is what `next` has to be able to read as "not a block".

## Green again on the change

```
$ bash tools/loop-selftest.sh --case 'run#aReviewerEndedAtItsTurnBoundIsNotARefusal'
PASS run#aReviewerEndedAtItsTurnBoundIsNotARefusal
$ bash tools/loop-selftest.sh --case 'state#nextHonoursEveryStopCondition'
PASS state#nextHonoursEveryStopCondition
```

## The mutations the new invariants are held by

```
$ bash tools/loop-mutations.sh --only '80.1 80.2 80.3'
RED    80.1  run#aReviewerEndedAtItsTurnBoundIsNotARefusal
       2026-11-05T20:21:16Z — the item is recorded cut, not blocked: its work finished and the round the run ended is nobody's question: expected 'cut', got 'blocked'
restore 80.1 run#aReviewerEndedAtItsTurnBoundIsNotARefusal green again
RED    80.2  run#aReviewerEndedAtItsTurnBoundIsNotARefusal
       2026-11-05T20:21:40Z — the round the bound ended is recorded on the branch as a cut: 'review-round-1-cut.md' not found in: .gitignore
restore 80.2 run#aReviewerEndedAtItsTurnBoundIsNotARefusal green again
RED    80.3  run#aReviewerEndedAtItsTurnBoundIsNotARefusal
       2026-11-05T20:22:08Z — and the report asks the owner no question about it: 'decisions the owner owes: none' not found in: ...
restore 80.3 run#aReviewerEndedAtItsTurnBoundIsNotARefusal green again

red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0
```

The six records the change repoints or depends on (`68.2`, `68.4`, `68.5`, `68.6`, `73.1`, `79.4`) went red and
restored green in the same pass, and `--check` over the four touched engine scripts reports that all 140 selected
records apply to the file they name.
