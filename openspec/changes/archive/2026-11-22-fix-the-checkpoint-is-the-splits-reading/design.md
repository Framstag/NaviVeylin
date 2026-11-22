# Design: fix-the-checkpoint-is-the-splits-reading

## Root cause

`LOOP_ITEM_CONTEXT_WARN` records the archive's p75 of the largest context per item (`tools/loop/loop.conf:233-235`,
taken 2026-11-05), and that derivation predates #856, which turned the key from a printed line into the reading at
which the item's first worker is **ended and continued** (`tools/loop/child.sh:501`). A split is not free: it pays the
session's baseline again and saves only the turns after the crossing. A checkpoint at the p75 is therefore placed
where the population has already stopped, so the fraction of items it helps is small and what it saves in each is
the part the same distribution counted as cheap.

## The single fix

Record 90 000 at `tools/loop/loop.conf:267`, with the derivation beside it re-taken over the archive that exists now,
and carry the value to the one case that pins it and to the seven cases that write it into their own copied profile.

No engine change. `tools/loop/run.sh:199` reads the key, `tools/loop/child.sh:501` takes the split, and the
continuation is the one #851 landed. The three edits in those two files are comments: they are the pointers that called
the checkpoint the p75, and a comment that states a derivation the next reader can check is part of the delta that
moves the number.

## Why 90 000, and not the p75, and not 85 000

The model is `tools/loop-spend.py`'s reader and its own arithmetic, applied to one question: what does an item's
first worker cost when the run splits it at the crossing and starts a fresh child from the turn after it? A split is
modelled as what it is — the baseline paid again, nothing carried over. Over the 53 items of the archive that carry a
worker session (2026-11-22, `.pi/issue-loop/items/1003/split-model.log`):

| reading | item total | items split |
|---|---|---|
| as-is, no split | 1 055 Mtok | 0 |
| 143 124 (today's record) | 989 Mtok | 28 |
| 110 000 | 887 Mtok | 36 |
| 100 000 | 871 Mtok | 38 |
| 95 000 | 868 Mtok | 39 |
| **90 000** | **866 Mtok** | **41** |
| 85 000 | 865 Mtok | 42 |
| 80 000 | 867 Mtok | 42 |
| 70 000 | 875 Mtok | 44 |
| 50 000 | 907 Mtok | 46 |

The curve has a flat bottom between 85 000 and 90 000 and rises on both sides of it: below it the split count grows
faster than the window shrinks, and the baseline each split pays again is what turns the corner. 90 000 is the
highest reading on that bottom, so it splits the fewest items for the same spend — the reading the item asked for,
arrived at by the measurement rather than by the p75's position.

## Blast radius

- `tools/loop/loop.conf`: one value and the derivation paragraph beside it.
- `tools/loop/tests/loop-cases.sh`: one case added, seven rewritten (their copied profile), one child reading moved
  from 100 000 to 60 000 so a below-the-checkpoint case stays below the reading it is about.
- `tools/loop/run.sh`, `tools/loop/child.sh`, `tools/loop/tests/loop-mutations.sh`: comments only.
- `guidelines/BacklogTooling.md`, `guidelines/Backlog.recipes.md`: the value's row and the paragraph that records its
  measurement.
- `openspec/specs/agent-issue-loop/spec.md`: one requirement, one scenario added to it.

Nothing reads the number as an equality: `run.sh` reads it as a bound, `state.sh` records whatever the profile says
(`state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst` compares the record with the file and keeps passing),
and `tools/check-doc-values.sh` does not carry this key.

## Rollback

Revert the value and its derivation paragraph: the engine is unchanged, so a tree that records 143 124 behaves
exactly as the tree before this change did. The added case is the only artifact that would go red, and it names the
value it pins.

## Alternatives the evidence eliminated

- **Keep the p75 and pay nothing** — the measurement prices it: 989 Mtok against 866 at 90 000, over the same items
  and the same model, so the p75 leaves 14 % of the item spend in the window the split exists to shorten.
- **60 000, "half the stop"** — eliminated by the same table: below 85 000 the split count grows to 42 and beyond and
  the total rises (80 000: 867; 70 000: 875; 50 000: 907), so the extra splits cost more than the smaller window
  saves.
- **Lower the stop instead** — refused before this item: `LOOP_ITEM_CONTEXT_MAX` stays 200 000 (#833 recorded the
  refusal, #836 re-proposed it and was closed `NOT_PLANNED`), and the checkpoint is the cheaper lever precisely
  because it is below it.

## Open decisions

none
