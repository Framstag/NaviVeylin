# Tasks — fix-an-item-over-its-token-band-is-flagged

Each task is verified by the command beside it. The gate was run **once** (`./tools/loop/gate.sh`); the
iteration before it used focused selections only.

- [x] 1. The red case, on the base, before any engine change.
  - Write `report#anItemOverTheTokenBoundIsFlagged` (declaration, dispatch, body) into `tools/loop-cases.sh`.
  - Verify: `bash tools/loop-selftest.sh --case report#anItemOverTheTokenBoundIsFlagged` on the base — one `FAIL`
    line, `'over the item token bound (1000000 tokens): #21 2.5x' not found in: …`, log `evidence/red-on-head.log`,
    tally `evidence/red-on-head-report-anItemOverTheTokenBoundIsFlagged.xml` (`tests="1" failures="1"`).

- [x] 2. The recorded value, with its measurement.
  - `tools/loop/loop.conf`: `LOOP_ITEM_TOKENS_WARN=16858818`, the archive's p75, with the derivation and the
    warning-never-a-stop rule in the comment beside it.
  - Verify: `python3 evidence/token-band.py .pi/issue-loop` prints `LOOP-BAND-P75 tokens=16858818` and
    `ratio_p75_to_median=1.95` — log `evidence/token-band.log`; `grep -n '^LOOP_ITEM_TOKENS_WARN='
    tools/loop/loop.conf` reads the same number.

- [x] 3. The run-level line.
  - `tools/loop/report.sh`: the key read through `loop_conf_value`, `over the item token bound (<n> tokens):
    <#issue> <ratio>x · …` or `: none`, and `over the item token bound: off` when the key is `0`.
  - Verify: task 1's case, green — `PASS report#anItemOverTheTokenBoundIsFlagged`.

- [x] 4. The table's column.
  - `tools/loop/state.sh`: a sixteenth column `over`, appended after `stop`, `2.5x` above the bound and `-`
    otherwise (and for an item that reported no spend).
  - Verify: task 1's case, which reads `table_cell 16` of both rows.

- [x] 5. The case the new column rewrites.
  - `tools/loop-cases.sh`, `report#wallTimeAndReasonInTheTable`: the row's column count (16 pipes → 17) and its
    comment.
  - Verify: `bash tools/loop-selftest.sh --case 'report#wallTimeAndReasonInTheTable'` — `PASS`, as do
    `report#zeroUsagePrintsDashNotZero`, `report#emptyRunSaysNone`, `report#headerAndTableShape` and
    `state#aBlockedItemNamesTheMechanismThatStoppedIt`.

- [x] 6. The two mutation records.
  - `tools/loop-mutations.sh`: `65.1` (the run-level line never fires) and `65.2` (the column never fires).
  - Verify: `bash tools/loop-mutations.sh --only '65.1 65.2' --check` (both apply) and the same command without
    `--check` — `red-when-mutated: 2 · not-noticed: 0`, log `evidence/mutations.log`.

- [x] 7. The documents that own the value.
  - `guidelines/Backlog.md`, the "The loop" section (the report paragraph and "What an item costs"), `PORTING.md` (the key list) and
    `.pi/skills/loop-spend/SKILL.md` (where the *why* lives).
  - Verify: the gate's `tools/check-doc-routes.sh` and `tools/check-todo-references.sh` steps, both green.

- [x] 8. The mutations the touched files own, and the gate, once.
  - Verify: `bash tools/loop-mutations.sh --from-git <base>` — `selected 32 of 180`, `red-when-mutated: 31 ·
    not-noticed: 1`, log `evidence/mutations-from-git.log`; the one unnoticed record (`54.1`) is a defect of the
    base, reproduced against the base's own `loop.conf` and reported in `proposal.md` rather than repaired here.
  - Verify: `./tools/loop/gate.sh` — the gate's own `LOOP-GATE:` line reads 689 tests, 0 failures, 0 errors,
    summed over the 29 tallies in `evidence/gate-tallies/` (the largest, `tools_loop-selftest.sh_--from-git_4a4b3a….xml`,
    carries `tests="241" failures="0"`); log `evidence/gate.log`.

- [x] 9. Evidence and archive.
  - Every cited XML is copied into `evidence/`; `bash tools/loop/evidence-check.sh
    openspec/changes/fix-an-item-over-its-token-band-is-flagged --root .` reports no dangling pointer; the change
    is archived and the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
