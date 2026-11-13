# Design — one arm in the reader that already names the role

## Root cause

`child_usage_json` already folds an item's whole event stream into one object and already reads a session's role out
of the prompt path it was handed. The reading is a two-arm test over the lowercased first user message
(`tools/loop/state.sh:1152`, `:1166`):

```jq
| if test("worker-tail") then "tail"
  elif test("reviewer") then "reviewer"
  else "worker" end
```

A `reviewer-pre-gate-prompt.md` session matches `test("reviewer")`, so the round the gate follows is charged to
`reviewer`. The engine knows better in one place — `child_role` (`tools/loop/run.sh:1551`: `*pre-gate*` before
`reviewer-*`) — and `tools/loop-spend.py` already applies that order, so the recorded split disagrees with both the
run that started the child and the report read over the same archive.

## The single fix

Add the arm, in the engine's order, to the two passes that fold sessions — and declare the key beside the three:

1. `tools/loop/state.sh:1152` (tokens): `elif test("pre-gate") then "reviewer-pre-gate"` between the tail arm and
   the reviewer arm, and `"reviewer-pre-gate": null` in the object literal the reduction starts from (`:1156`).
2. `tools/loop/state.sh:1166` (models): the same arm and the same key in `roleModels` (`:1173`), so a pre-gate session's
   model tally is keyed as its role total is.

No other production code changes. `report.sh` prints the trio and then every bucket the record holds
(`tools/loop/report.sh:478-480`), which is the rule the orphan bucket was added under (issue #832), so the fourth key
prints with no edit there — its header comment, which is also its `--help` text, names the bucket it now prints.

## Blast radius

- Two arms and two keys in one jq program in one file, and one comment line in `report.sh`'s header. Every other figure
  of `child_usage_json` — `tokens`, `turns`, `calls`, `tools`, `phases`, `afterGate`, `reads`, `dupReads`, `withheld` —
  reads the same events through the same passes and is unchanged.
- Streams with no pre-gate session are byte-identical: the new bucket is `null`, the report drops a null bucket from
  its line, and every existing case that asserts `worker` / `reviewer` / `tail` sums keeps its number.
- The state's schema narrows nothing: `metrics.roles` and `metrics.roleModels` gain a key an older reader of the same
  JSON ignores.
- Rollback: delete the two arms and the two keys. A state written by the newer tree then reads `reviewer-pre-gate` as
  a bucket the report still prints — the line reads the record, not a declared vocabulary — so a rollback costs the
  split, never a crash.

## The alternatives the evidence eliminated

- **Splitting all six of the engine's roles in the state** (`worker-gate`, `worker-rework` beside `worker`). Eliminated
  by the requirement's own vocabulary and by the figures it pins: `metrics.roles.worker` is the number the trend prints
  as the worker's share, and `report#theSpendIsSplitByPhaseRoleAndGate` and
  `run#aBlockedItemRecordsTheRoundThatReadIt` read it as one; a six-way reshape moves every one of them, and every
  archived state's meaning, for a question this item does not ask.
- **Deriving the recorded split from `tools/loop-spend.py` instead of the stream.** Eliminated by what `state.sh done`
  is: it already has the stream open, and a second reader of the same file would be a process, a `python3` dependency
  and a second ordering of the arms to keep in step at write time — the cost the item itself names.
- **A shared role reader in `lib.sh`, called by both `run.sh` and the state.** Eliminated by the two inputs: `child_role`
  reads a *file name* with a shell `case`; the state reads the session's first user message, which may be a path or a
  prompt's words, and must keep the words' reading for a prompt that is not a path. One function over two shapes of
  input is a third reading, not one place.
- **Rendering the pre-gate prompt under another name** so the word never matches. Eliminated by the run's own records:
  `run.sh:2585` renders `reviewer-pre-gate-prompt.md` and the retry beside it, every archived session that names the
  phase names it that way, and a rename would move the prompt a reviewer is handed for a bucket label.

## Open decisions

none
