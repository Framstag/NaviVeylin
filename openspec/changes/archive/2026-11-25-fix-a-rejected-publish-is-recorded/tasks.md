# Tasks — a rejected publish is recorded on the item

## The case, first

- [x] Add `worktree#aRejectedPushIsRecordedOnTheItem` to `tools/loop/tests/loop-cases.sh`, with the
      `# files:` declaration its first line names: `tools/loop/run.sh tools/loop/leftovers.sh
      tools/loop/state.sh tools/loop/report.sh tools/loop/tests/fixtures/scenarios/
      tools/tooling.json tools/pi-child-events.sh`. Verified by
      `driver#everyCaseDeclaresTheFilesItCanReach` (`evidence/declaration.log`).
- [x] Register it in the case list beside `worktree#publishesTheBaseItLandedOn`.
- [x] Run it against the tree before the production change and keep its failing output as the
      evidence the change owns: `tests="1" failures="1"` (`evidence/red-on-HEAD.xml`,
      `evidence/red-on-HEAD.log`).

## The production change

- [x] `push_branch` (`tools/loop/leftovers.sh:414-421`) returns 1 when the remote refused the push,
      keeping its one stderr line and its `publish-failed` event.
- [x] `state.sh` gains the `publish-refused` verb and its usage line: appends to the item's
      `publishRefused` list, unique by branch, refusing an issue the state does not carry.
- [x] `run.sh` (`tools/loop/run.sh:2840-2844`) reads each push's status and records the refused
      branch on the item's record, with the local head and what the remote's tracking ref names.
- [x] `report.sh` prints one line per refused branch in its branches block.

## The rule the diff owes

- [x] `guidelines/Backlog.recipes.md`, section 7e: a refused publish is a leftover and the report names it;
      the record carries the branch and its local head beside the landing's sha.

## The case that reddens it

- [x] `tools/loop/tests/loop-mutations.sh` `1027.1`: drop the count from the report's refused line
      and the case fails — `RED 1027.1 … worktree#aRejectedPushIsRecordedOnTheItem`, then
      `restore 1027.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutations.log`).

## Then

- [x] `bash tools/loop/measure.sh --change fix-a-rejected-publish-is-recorded` and the change's
      evidence directory under `evidence/` (`evidence/measure.log`: 7 paths, 152 insertions,
      6 deletions, `cases=553`, `scenarios=3 cases=3 unnamed=0`).
- [x] The gate, once, from the change root — `./tools/loop/gate.sh`, this session's closing step.
