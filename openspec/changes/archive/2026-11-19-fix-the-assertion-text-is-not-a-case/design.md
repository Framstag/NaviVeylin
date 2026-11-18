# Design — the eliminated fallback arm is deleted, and the error #268 made it stays

## Root cause

`finish_step` (`tools/loop/gate.sh`, the `f=0` block after the numbers `n`) answers one question — how many cases did
this step's own output name as failing — and at the base it answered with two arms:

| the step's stream | what the first arm reads | what the second arm read |
|---|---|---|
| `M of N cases failed` summary | M (the summary) | not reached |
| one `<class> > <case> FAILED` per failing case | the number of those lines | not reached |
| the task's `FAILED` header and an `AssertionError`, no per-test line | nothing (`0`) | **`1` — the throwable, counted as a case** |
| nothing about a case (configuration failure, a killed step) | nothing (`0`) | nothing (`0`) — the error stays |

The third row is the defect: a step that named no case carries **one error** by the requirement, and the arm turned
its throwable into a failure. Measured on HEAD at `7aea9b5d`: the shape of row three tallies
`LOOP-GATE: tests=0 failures=1 errors=0` (`evidence/red-on-HEAD-lone-assertion.log`), so a change that broke nothing
but its environment reads as one that broke a test — the mirror image of the failure the first round of item #916
fixed, and both are read by the run's own check for a red claim.

## The single fix

Delete the arm. `f` is then the count of the `<class> > <case> FAILED` lines the step named, and nothing else:

```
  if [ "$rc" -ne 0 ] && [ "$f" -eq 0 ] && [ "${st_overran[slot]}" -eq 0 ]; then
    f=$(printf '%s\n' "$out" | grep -c '^[^[:space:]>][^>]* > .* FAILED[[:space:]]*$')
  fi
```

No new branch, no new number, no new shape: the arm was the only thing in the tree that read `AssertionError` as a
case, and its absence is the whole of the production change (`gate.sh` −1 line, the comment above it re-cut to say
why the one reading is the only one).

## Blast radius

- **The four shapes the case counts read:** the summary shape and the per-test shape are untouched (their cases stay
  green — `gate#aFailingSuite*`, `gate#aTestFailureIsAFailureNotAnError`); the third row is the case this change adds;
  the fourth row is `gate#aFailingSuiteWithNoSummaryInventsNoTest`, which the arm never reached.
- **`n`, the case count:** untouched — it comes from the summary shapes only.
- **The bound's error:** untouched — the arm was already guarded by `st_overran`.
- **`tools/gate-attribute.sh`:** untouched. It reads a *log's* failure line and its `file:line`
  (`gate#aFailingCaseCarriesItsFactsAndItsRerun`, its fixture under `loop-cases.sh:19348`–19368), not the gate's
  failure count, and its fixture keeps its `AssertionError` text.
- **The archived first-round change** (`2026-11-14-fix-a-failing-test-is-a-failure`) keeps its own artifacts; this
  change states the deletion in the guideline and in its own delta instead, so no archive is re-cut.

## Rollback

Revert the four hunks: the arm returns, and its case goes red with `failures=1 errors=0` — the state measured at the
base. Nothing else reads the difference, so no revert step beyond `git revert` is owed.

## Alternatives eliminated

- **Keep the arm as a fallback for a task that prints a throwable and no per-test line** — this is the defect: a
  throwable names no case, and a count of them is a number the suite never reported, which the requirement forbids in
  as many words.
- **Count `AssertionError` only when the stream also carries a task's `FAILED` header** — the same number under a
  narrower condition, and the header itself is the evidence that a *task* failed, not that a case did. A step that
  names no case is exactly the row #268's error was written for, so the narrower arm would still lose it.
- **Derive the count from the tally the task wrote** — a second source of the same number, and the tally is what the
  run's own check reads *back*; a count read from it would be a circle. It also reaches a file the step may not
  write.
- **Leave the case out and land the deletion alone** — nothing in the suite would notice the arm's return, which is
  the state the elimination was settled from: the mutation `916.2` needs a case to hold it.

## Open decisions

none
