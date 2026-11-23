# Proposal: a-repeat-read-is-refused-after-its-count-bound

## Why

`repeatReadResult` (`tools/pi-trim-result.mjs:395`) answers the Nth read of a path with the notice the item already
holds and keeps the text out — it never refuses the read itself, and no counter bounds N (issue #989). A child that
does not accept the notice re-asks, and each re-ask is a turn: #943 read `.pi/issue-loop/items/943/gate.log`
**2 372 times** in 245 turns, and the run ended that item at `LOOP_ITEM_CONTEXT_MAX` (264 347 tokens) with one path as
the cause (`items/943/events.jsonl`, `report.sh`'s `dup 2418/2457` — 65 % of the run's duplicate reads). Run-wide
3 197 of 3 715 file reads (86 %) were a second read, 3 713 619 characters withheld while the repeat results still
carried 2 022 919 characters.

The rule's money was paid twice: the characters it withholds are re-asked as turns, so the saving was a saving of
characters and the cost was turns. #976 (flip `LOOP_REPEAT_READ` from `measure` to `refuse`) is the neighbouring half
and neither subsumes the other: this bounds a read that is *already* being refused and retried.

Root cause, one sentence: the repeat-read rule has a character-shaped guard and no count-shaped one.

## What Changes

- **A per-session count bound beside the character bound** — `LOOP_REPEAT_READ_REFUSALS` in the project block of
  `tools/loop/loop.conf`, recorded as `3`, read by `tools/pi-child-events.sh` and never sourced (D16).
- **`repeatReadResult` counts every repeat of a path** on this child's own tally and, past the bound, answers the
  read `refused` **whatever arm the run records** — the arm's existing shape (`refused: true`), so no new outcome and
  no new verdict exists and nothing about an item's grading moves.
- **The notice carries the two facts a child that keeps asking needs** — `repeatReadNotice` keeps its shipped wording
  for an unbounded refusal and adds the count and the earlier turn the child may quote instead when the bound answered.
- **The gate's removed-fact class grows by one** — `item_session_vars` in `tools/loop/gate.sh`, so a step the gate
  measures is not handed the count (`gate#aStepIsNotHandedTheItemsSession` reads the adapter's exports and refuses a
  fact the list does not carry).
- **The prose homes move** — `tools/loop/loop.conf`, `tools/loop/prompts/worker.md` and `guidelines/Backlog.recipes.md` §7.

Additive, not breaking: `0` (or a project that records no key) leaves the count off and the arm alone answers the
read, which is the behaviour that shipped. The rollback is the one recorded value to `0`.

## Capabilities

### New Capabilities

- none — the requirement joins the capability the repeat-read arm already lives in.

### Modified Capabilities

- `agent-issue-loop`: adds the requirement "A path is refused after its repeat bound" beside the shipped
  "A second read of a path a child was handed is counted, and the arm decides its answer" — the shipped requirement's
  body and its three scenarios are untouched, so no text a run already depends on moves.

## Impact

- `tools/pi-trim-result.mjs` — `recordedRepeatRefusals`, `repeatReadNotice`, `repeatReadResult`, the hook's and the
  CLI's read object, and the file's own header comment.
- `tools/pi-child-events.sh` — the export of the recorded key.
- `tools/loop/loop.conf` — the recorded value and its comment block.
- `tools/loop/gate.sh` — `item_session_vars`.
- `tools/loop/prompts/worker.md` — the paragraph that states the repeat rule to a child.
- `tools/loop/tests/loop-cases.sh` — `case_adapter_aPathIsRefusedAfterItsRepeatBound`, and the three `sed`
  expressions of `adapter#theChildIsStartedWithTheRepeatReadArm` narrowed to the arm key so the new key's line is not
  rewritten by them.
- `tools/loop/tests/loop-mutations.sh` — mutation record `989.1`.
- `guidelines/Backlog.recipes.md` §7 — the paragraph that states the rule.
- Related items: #925 (the arm and the count), #976 (the arm's flip), #943 (the measurement), #685 and #886 (the two
  repeats the bound leaves served).

surface: 1 constant read, 1 function added, 2 functions changed, 2 call sites in `tools/pi-trim-result.mjs`, 1 export
in `tools/pi-child-events.sh`, 1 key in `tools/loop/loop.conf`, 1 list in `tools/loop/gate.sh`, 1 case and 1 mutation
record — `tools/look.sh 'LOOP_REPEAT_READ_REFUSALS' --path tools/` and `tools/look.sh 'repeatReadNotice|repeatReadResult'
--path tools/pi-trim-result.mjs`