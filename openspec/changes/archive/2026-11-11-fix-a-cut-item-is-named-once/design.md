# Design — a cut item is one line, and its cause is the one its record states

## Root cause

`report.sh`'s action list is a list of arms over the run's state, and the last two arms are not disjoint:

```
# report.sh:696 (HEAD)
done < <(jq -r '.items[] | select(.result == "cut") | [(.issue | tostring), (.note // "")] | @tsv' "$state_file")

# report.sh:705 (HEAD)
done < <(jq -r '.items[] | select((.leftover // "") != "") | [(.issue | tostring), .leftover] | @tsv' "$state_file")
```

The second arm's guard is a *field*, not a result: any record that carries a `leftover`. The first arm's guard is the
result `cut`. `run.sh` writes the two facts together — a child the wait ended is recorded `cut` (`run.sh:2848`) and the
work it left is written to a patch whose path the record carries (`keep_leftovers`, `run.sh:1832`, `state.sh done
--leftover`) — so every cut child that left work is selected by both arms, and the recovery line's sentence, *the run's
clock ended it*, is printed over a record whose own note says the child reached the recorded context bound.

## The fix

One guard, and one field on the line that already names the item:

| arm | guard (HEAD) | guard (now) | line |
|---|---|---|---|
| resume | `.result == "cut"` | unchanged | gains `${patch:+ · its work is kept at $patch}` |
| recover | `(.leftover // "") != ""` | `.result != "cut"` **and** `(.leftover // "") != ""` | unchanged |

The two arms are then a partition of the records that carry a kept patch, by the result the record states, and the
action list's own `none` line still covers both.

## Why not a second mechanism for the deadline

The issue names the split the two lines exist to make — *the run ended the child at a bound (resume it)* versus *the run's
own clock ran out (recover its kept work)* — and the same one-word cause cannot name both. The obvious second word does
not exist and cannot be added here:

- `state.sh:666` refuses every `--stop-reason` outside `refused|unmergeable|bound`, and `state.sh:668-671` refuses a
  mechanism on anything but a block, except `bound` on a `cut`. The requirement behind both is
  `openspec/specs/agent-issue-loop/spec.md:2486`, whose text says *`bound` is also the one mechanism an item recorded
  `cut` carries*. A `deadline` value is that requirement reversed, and it is one change wider than this item: the
  vocabulary, `state.sh`'s validation, the spec sentence, the report's `bound_hit_mechanism` classifier
  (`report.sh:239`) and the case that reads the deadline's record (`loop-cases.sh:6915`) all move with it.
- The engine does not hold the fact per item either: `run.sh:2808` turns every child the wait ended into `bound`, the
  deadline arm included (`run.sh:1839`), which is #790's recorded decision — the run's own bound is work the next run
  resumes, never a question, and the deadline is one of those bounds.
- Read from the run instead — `.run.stop.reason` names the deadline — the selector would be positional: the deadline is
  the run's last item, which the report would have to infer.

What *is* recorded is which bound it was, in the item's own note (`LOOP-TIMEOUT`'s sentence, `run.sh:1789-1817`), and the
resume line already prints that note. So the caller reads one line that says the item is work, names the phase and the
bound, and carries the patch — the distinction survives in the text the record holds, and no line asserts a cause the
record does not.

## Blast radius

`tools/loop/report.sh` (one arm's guard, one arm's line, two comments); `tools/loop/tests/loop-cases.sh` (one case
added, one rewritten: three report assertions and the comment above them); `guidelines/Backlog.recipes.md`:1369 "a
named action list" (the
action-list paragraph); the change's spec delta (one scenario). No compiled file, no Gradle suite, no build: the gate
for this delta is the loop's own suite over the paths it changes.

## Rollback

The two hunks are the revert: restore `.items[] | select((.leftover // "") != "")` as the recovery arm's guard and drop
the `${patch:+ …}` suffix from the resume line. The case returns to red on the first assertion (`'recover #21' should
not appear in`), and the rewritten case's `resume #21` assertion returns to red as well — both are the shape this item
found, so nothing is left behind by reverting.

## Alternatives the evidence eliminated
- **Split by the run's own stop reason** (`.run.stop.reason` naming the deadline, `report.sh:207`'s `stop_reason`)
  — eliminated: it is the run's last item that the deadline ended, so the selector would infer the item from its
  position, and a run that ends between items would attribute nothing.
- **Split by the note's prose** — eliminated by the rule the vocabulary itself exists for
  (`spec.md:2486`: a mechanism's spend is totalled *without the note's prose*), and a report that greps a child's
  sentence is the reading #280 replaced.
- **Drop the recovery arm entirely** — eliminated: a state a tree wrote before `cut` existed carries the same end as
  `blocked` with a leftover, that record names no bound, and #674's line is where its work is named.

## Open decisions

none

The one fork this item could have opened — a second mechanism word for the run's deadline — is closed by the record:
`state.sh:666` refuses every value outside `refused|unmergeable|bound`, and the requirement behind it
(`spec.md:2486`) states that `bound` is the one mechanism a `cut` carries, with the run's own deadline among the bounds
it names.
