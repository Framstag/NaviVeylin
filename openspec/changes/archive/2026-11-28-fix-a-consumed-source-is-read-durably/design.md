# Design — a consumed source is read from the record that outlives the run

LOOP-ROUTE: guidelines/Backlog.md §6

## Root cause

`tools/loop/loop-clean.sh`'s consumed-source arm answered "which source did a landed item consume?" from
`$LOOP_STATE_FILE` — `.changeCopies` joined against the issues `.items` records as `done`. Both keys are one run's:
`tools/loop/state.sh:311` writes `items:[]` and no `.changeCopies` on the next `init`, so after a run ends the answer
is "none", and every source that run consumed is named by nothing while `openspec list` keeps reporting it as
in-progress. The records that survive are the item's own `items/<issue>/change-copy.json` (`tools/loop/run.sh:1179`)
and the item's `item-result` line in the run's event record (`tools/loop/run.sh:2937` → `.pi/issue-loop/run-events.jsonl`).

## The one fix

In the one arm, three readings and two keeps, all in `tools/loop/loop-clean.sh`:

1. **The landed set** is the union of the state's `done` items and the event record's `closed` item results:

   ```sh
   landed=$( { jq -r '(.items // []) | .[] | select((.result // "") == "done") | .issue | tostring' "$LOOP_STATE_FILE"
               jq -r 'select(.kind == "item-result" and (.result // "") == "closed") | .issue | tostring' \
                 "$LOOP_RUN_EVENTS" "$LOOP_RUN_EVENTS.1" ; } | sort -u)
   ```

   `done` is the state's word for a landing; `closed` is the event record's (measured: 37 `closed`, 48 `cut`,
   29 `blocked`, 16 `skipped` in this project's record). The rotated `.1` file is read beside it, so a landing whose
   line was rolled out of the current file is still read.

2. **The copies** are the union of the state's `.changeCopies` and one `items/<issue>/change-copy.json` per item
   directory. The state is kept as a reading rather than dropped: a run that is still going has no item directory
   it has not written, and one record more cannot make a live answer wrong.

3. **The keeps.** A source is only read for a landed item (an unfinished item's source is that item's work, and the
   arm is not a second discard path); a source two records name is reported once (`seen_sources`); and a source whose
   own `tasks.md` carries `- [x]` is kept and named — a change worked in slices has an archived twin for the earlier
   slice, and a prepared change nobody has started carries no checked task.

`items_root` moves up to the arm's own block, because the item-directories arm below it read the same path from a
definition of its own.

## Blast radius

`tools/loop/loop-clean.sh` only: the arm's record reading, one `say` line added, one `items_root` assignment moved.
Nothing else in the command changes: the build-output arm, the scratch-tree arm, the branch and worktree arms, the
item-directory arm and the summary counts are untouched, and `--apply` still removes only paths the same report
named. `$LOOP_RUN_EVENTS` is `lib.sh`'s, already resolved by `loop_resolve` for every caller of this command.

## Rollback

Revert the file. The command removes disk paths only, and the arm's previous reading was a subset of the new one, so
a reverted command under-reports rather than removes: nothing it removed becomes unremovable.

## Alternatives the evidence eliminated

- **Read the archived twin** (`openspec/changes/archive/*-<name>/`) as the proof the change was consumed. Eliminated:
  the run archives inside the item's worktree, so the project's own tree holds no twin for the sources this project
  has (`openspec/changes/archive/` names the item's branch copy, not the source's), and the fixture the case builds
  has none either.
- **Read the item dir alone**, ignoring the state. Eliminated by the same case's second assertion: during a run the
  copy is recorded in the state before the item directory is written, and reading one record where two exist is how
  this arm came to name nothing.
- **Treat every source with no checked task as removable.** Eliminated: that deletes the source of an item that is
  still running, which the arm's own header forbids — the landed filter stays.
