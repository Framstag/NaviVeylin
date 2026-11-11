# A round the run's bound cut names the bound and its figure

## Why

Issue **#829**. The reason a cut review round leaves on the item reads `the run ended the reviewer of round 1 at
one of its spend bounds (its role's turn bound or the item's context bound)` (`tools/loop/run.sh:2839` before this
change), and the arm before the gate writes the same sentence (`tools/loop/run.sh:2397`). The child's own
transcript beside it names the bound, the key and the figure — `LOOP-TIMEOUT: the child reached the recorded context
bound of 200000 tokens (LOOP_ITEM_CONTEXT_MAX) at 201981 tokens` (`tools/loop/run.sh:1728`) — so the reason a person
reads is the one place in the item that does not say which bound fired.

**Root cause, in one sentence:** the arm that reads the cut transcript tests the `LOOP-TIMEOUT` line for presence
and throws its text away, writing a fixed sentence that offers both bounds, while the line it discarded names the
bound and the reading.

**The evidence the item carries, verified in the archive** (`.pi/issue-loop/run.json`): #753 (`metrics.context`
200427, `reviewVerdict` `cut-short`) and #819 (200733, `cut-short`) were ended by the **context** bound above
`LOOP_ITEM_CONTEXT_MAX=200000`, and both items' reason offered the two bounds; #719's own record names it exactly
(`stopReason` `bound`, 201981). Verified in the tree before this change: both arms still carried the ambiguous
sentence, and the transcript line that carries the answer is still written one frame away.

**The red case, on the base `d5d2e668`** — `bash tools/loop/tests/loop-selftest.sh --case 'run#aPreGateReviewEndedAtItsBoundNamesIt'`
and the same for `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`, over the base tree with this change's case file
(2026-11-10T15:32:07Z–15:32:21Z). Both cases fail, and both quote the ambiguous sentence they were refused by:

```
run#aPreGateReviewEndedAtItsBoundNamesIt|   the reason on the item names the bound that ended the round and the figure it reached: 'turn bound of 3 turns' not found in: the run ended the pre-gate reviewer at one of its spend bounds (its role's turn bound or the item's context bound) — the round was cut, not refused, so the change is left for the next run
FAIL run#aPreGateReviewEndedAtItsBoundNamesIt
run#aReviewerEndedAtItsTurnBoundIsNotARefusal|   the reason on the item names the bound that ended the round and the figure it reached: 'turn bound of 3 turns' not found in: the run ended the reviewer of round 1 at one of its spend bounds (its role's turn bound or the item's context bound) — the round was cut, not refused, so the change is left for the next run
FAIL run#aReviewerEndedAtItsTurnBoundIsNotARefusal
```

XML: `tests=2 failures=2 errors=0` (`evidence/red-on-HEAD.xml`, the tally the runner printed — the loop suite
writes no JUnit, so the XML carries the printed tally).

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "An item the run's own bound cut is recorded
`cut`, not `blocked`" (the sentence naming what a bound-ended round records, and the scenarios under it).

**Owning guideline section:** `guidelines/Backlog.recipes.md` section 7b "What an item costs, and the bounds on it" — the
section that derives the bounds and states that the transcript line carries the bound and its count so a person
reading the blocked item does not re-derive it.

**Surface, counted before the plan was fixed** (the reading below is the base tree's, before this change; the two
`run.sh` lines are the arms rewritten here). The count this change owes, as the plan's own line carries it:

```
surface: 2 arms rewritten, 1 requirement modified (5 scenarios restored beside 2 new ones), 2 cases (1 strengthened, 1 added), 1 guideline section, 1 mutation record added and 1 re-pointed — ./tools/loop/measure.sh --change fix-a-cut-round-names-its-bound, and tools/look.sh 'one of its spend bounds' for the files that hold the sentence
```

```
# before this change
$ tools/look.sh 'one of its spend bounds'
LOOK: 7 matches in 6 files of /home/tim/projects/NaviVeylin/.pi/worktrees/loop for 'one of its spend bounds'
LOOK-FILE: tools/loop/run.sh 2          # live: :2396 and :2838, the two arms
LOOK-FILE: openspec/specs/agent-issue-loop/spec.md 1
LOOK-FILE: …/2026-11-05-fix-a-child-is-bounded-by-its-context/specs/… 1   # archive
LOOK-FILE: …/2026-11-05-fix-a-child-is-bounded-by-its-context/evidence/… 2  # archive
LOOK-FILE: …/2026-11-07-fix-a-bound-ended-item-is-recorded-cut/specs/… 1  # archive
```

`./tools/loop/measure.sh --change fix-a-cut-round-names-its-bound` echoes that line as its own
`LOOP-MEASURE-SURFACE` row, because the block it prints is the output of a command the record carries and nothing is
recounted by hand. Re-taken over the working tree the `LOOK` line lists the two arms as this change writes them, the
archives that quote the old sentence and this change's own files; the block above is the base tree's reading.

Both live arms are the same sentence and the same discarded line; the case that already pins the review round
(`run#aReviewerEndedAtItsTurnBoundIsNotARefusal`) asserts the note names `turn bound` and is strengthened to the
figure and the key; the round before the gate has no case that reaches this arm, so one is added. No other live
script, prompt or fixture asserts the sentence.
