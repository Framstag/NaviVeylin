# Tasks

Reference: `proposal.md` for the scope and the red run, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The owed case, red first

Requirement: "A truncated spend says so", its three scenarios.

- [x] 1.1 `report#aTruncatedStreamSaysSo` in `tools/loop/tests/loop-cases.sh`: an item whose stream carries a
      `stream_rotation` record and one whose stream does not, both recorded through `state.sh done`, so
      `metrics.streamRotation` holds the record's `droppedTurnEnds` and `droppedBytes` as numbers for the first and
      `null` for the second.
- [x] 1.2 The same case asserts the trend's `truncated:` line names the rotated item with the figure it dropped and
      does not name the item that was not rotated.
- [x] 1.3 The same case deletes the field from the state — the shape a record written before it existed has — and
      asserts the line prints the "predates" form rather than a zero.
- [x] 1.4 The case is declared in `LOOP_SHELL_CASES`, dispatched in the case table, and carries its `# files:` line
      (`tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json`);
      `driver#everyCaseDeclaresTheFilesItCanReach` passes.
- [x] 1.5 The red run on the base before the fix (the fix stashed, the case as committed): FAIL, for the right
      assertion, in `/tmp/red-841.log`.

## 2. The fix

Requirement: the same, scenarios one to three.

- [x] 2.1 `tools/loop/state.sh`'s `child_usage_json`: one more figure read from the stream it already slurps —
      `streamRotation: {droppedTurnEnds, droppedBytes}` from the `stream_rotation` record, `null` where the stream
      holds none — emitted in the function's object and in its no-stream fallback, and recorded in the item's metrics
      beside `dupReads`/`dupChars`.
- [x] 2.2 The function's header comment states the fourth rule beside the three it already keeps: a rotated stream is
      a lower bound and its record says by how much.
- [x] 2.3 `tools/loop/report.sh`: one `truncated:` line in the trend block, after `after the first gate:` — the
      run's items whose streams rotated, the turns and bytes the records state, and the items with the figure each
      dropped, ordered by it; a run with no rotation prints the reason, not a zero.
- [x] 2.4 No existing line's computation changes: the diff adds the field, the line and their comments.
- [x] 2.5 The green run: the case plus `--group report` and `--group state`.

## 3. The falsification

- [x] 3.1 `268.1` — the line printed without the items it names → `RED report#aTruncatedStreamSaysSo`, restore green.
- [x] 3.2 `tools/loop/tests/loop-mutations.sh --check`: every record applies to the file it names, `268.1` included.

## 4. The prose the change makes false

- [x] 4.1 `guidelines/Backlog.recipes.md` §7e, the paragraph that states what the trend prints: the `truncated:` line,
      what it reads, and that it is a warning.
- [x] 4.2 `tools/loop/report.sh`'s header comment and `tools/loop/state.sh`'s `child_usage_json` block, where they
      enumerate what the state holds and what the report prints.
- [x] 4.3 `tools/check-doc-routes.sh` still reports 0.

## 5. The correction round

Requirement: the same, its second scenario and its third.

- [x] 5.1 The line's two figures are read from **two** selections: the turns and the item list from the items that lost
      a turn, `droppedBytes` summed over every item whose `streamRotation` is non-null — so a delta-only rotation is
      counted in the bytes and is no lower bound. The case's fixture gained #23 (rotated, 0 turns, 100 000 B) for that,
      and the two dash forms now say `no item had its stream rotated` and `no figure of this run is a lower bound — …`
      where the first read as "nothing was rotated".
- [x] 5.2 The `max`/`add` split of `state.sh`'s `streamRotation` is asserted: the case's #24 carries two records, so
      `droppedTurnEnds` is 20 (the greatest, not 32) and `droppedBytes` 500 000 (the sum, not 100 000).
- [x] 5.3 `report.sh`'s `--help` range is `sed -n '2,/^set -uo pipefail/p'`, the header's own end, rather than a second
      hard-coded line number.
- [x] 5.4 `proposal.md` quotes the red run's real first failure, states the surface's `grep` counts as they are (3 for
      `stream_rotation` in `state.sh`) and names the requirement as added in this change's delta, and `design.md`
      quotes the two dash forms the change now prints.
- [x] 5.5 Green: `report#aTruncatedStreamSaysSo`, `--group report` (30 cases) and the `--only 268.1` mutation.

## 6. The gate (not run by this change's own pass)

- [ ] 6.1 `tools/loop/gate.sh --base main` on the change, with `LOOP-GATE-TREE` and `LOOP-GATE` quoted into the
      archive.
- [ ] 6.2 The whole loop suite standalone: `tools/loop/tests/loop-selftest.sh --from-git main`.
- [ ] 6.3 The independent read-only review, then `openspec archive 2026-11-10-fix-a-truncated-spend-says-so`.
- [ ] 6.4 The re-read the issue asks for: the run of 2026-11-09T21:42:41Z printed through the new line, so the
      nine items and the 1 697 turns are named rather than inferred.
