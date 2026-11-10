# Tasks

Reference: `proposal.md` for the scope and the red run, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The three owed cases, red first

Requirement: "A rotation keeps the record lines the spend is read from", its three scenarios.

- [x] 1.1 `adapter#aRotatedStreamKeepsEveryTurnEnd` in `tools/loop/tests/loop-cases.sh`, beside the existing rotation
      case: a 30-turn delta-heavy stream at a 16 KiB bound keeps every record line — compared line-for-line against the
      stream the child wrote, not counted per type — with `dropped_turn_ends` at 0 and the record's `dropped_lines` no
      larger than the stream's delta lines. Its second half hands the child the first prefix of that stream that
      crosses the bound and nothing after it, so exactly one rotation runs and the record's `dropped_bytes` is checkable
      to the byte: bytes dropped + record lines kept = the file it was handed.
- [x] 1.2 `state#theSpendSurvivesTheLossOfItsDeltaLines`: two items, one recorded with a three-turn stream and one with
      the same stream minus its `message_update` lines — what the delta pass leaves — assert `metrics.turns`, the input,
      output and cache-read tokens and `metrics.context` as values (3, 600, 80, 3 000, 2 300), and then that the two
      items' figures are equal, so the case cannot pass on two nulls.
- [x] 1.3 `adapter#aStreamStillOverItsBoundAfterTheDeltaPassIsCutAndRecorded`: the records alone outgrow a 4 KiB bound,
      so the delta pass runs first (no delta line survives), the oldest half is cut, `dropped_turn_ends` is greater than
      0 and `dropped + kept` is every turn the child took — the fallback this change keeps, and the residue #841 owns.
- [x] 1.4 Each of the three is declared in `LOOP_SHELL_CASES`, dispatched in the case table, and carries its `# files:`
      line (`tools/pi-child-events.sh`; `tools/loop/state.sh tools/loop/report.sh tools/pi-child-events.sh
      tools/tooling.json`); `driver#everyCaseDeclaresTheFilesItCanReach` passes.
- [x] 1.5 The red run on the base before the fix (`c0ddb97c`, the fix stashed, the cases as committed): all three FAIL,
      for the right assertion — *every turn_end the child wrote survives the rotation: expected '30', got '7'*; *and the
      input tokens of the turns whose updates are gone: expected '600', got 'null'*; *no delta line survives a rotation
      of this stream: expected '0', got '4'*.
- [x] 1.6 `adapter#anUpdateOnlyStreamKeepsItsUpdates`, the correction round's case: a 40-update stream at a 4 KiB bound
      with no `turn_end` at all, so the "every `turn_end` carries a usage" precondition is vacuously true and took every
      update a 57-line stream of that shape had (`turns` 1 → 0). Asserted: the stream rotates, no `turn_end` is there,
      every update survives, `tool_execution_update` goes, `dropped_turn_ends` is 0 — and, through `state.sh`, the one
      turn the surviving update reports (1 turn, 1 000 input tokens, 21 000 context) instead of none.

## 2. The fix

Requirement: the same, scenarios one to three.

- [x] 2.1 `tools/pi-child-events.sh`'s `rotate_events`: a delta pass that drops `message_update` and
      `tool_execution_update` and keeps every other line — the previous rotation record included, whose cumulative turn
      count the new one carries — followed by the unchanged oldest-half cut, which runs only when the delta pass left the
      file over `LOOP_CHILD_EVENTS_MAX_BYTES`. The record's shape and one-record invariant are unchanged;
      `dropped_lines`/`dropped_bytes` are what that rotation removed, both passes together.
- [x] 2.2 The precondition, read from the stream being rotated and not assumed: `message_update` goes only when the
      file holds a `turn_end` and every one of them carries a `usage`; otherwise the pass drops `tool_execution_update`
      only and a still-over stream falls through to the whole-line cut. An update-only stream therefore keeps its
      updates — the correction round's `1.6`.
- [x] 2.3 `tools/loop/state.sh`'s `turns`: a turn's usage is `.cur // $e.message.usage` — the record its own `turn_end`
      carries when no update survives it — with the block comment's first rule extended by the measurement.
- [x] 2.4 The green run: the three cases plus `--group adapter` (8), `--group state` (38), `--group report` (29),
      `run#aRotatedStreamStillCountsTheTurnsItDropped` and `run#childPastItsTurnBoundIsCut` all PASS.
- [x] 2.5 The measured byte effect on a real stream, not only on a fixture: copies of
      `.pi/issue-loop/items/833/events.jsonl` at a 4 MiB and a 2 MiB bound, against the base adapter — 4 MiB:
      8 593 950 B → 3 590 953 B, 90 of 90 `turn_end` kept, 2 of 2 `session` kept, no whole-line cut (base: 57 and 1);
      2 MiB: 3 590 953 B, 47 of 90 kept, record 320 = 277 carried + 43 newly dropped (base: 57, 310) — the residue #841
      names, and on it the cut can take more turns than the line-position cut did.
- [x] 2.6 `child_context`'s shrink guard, observed rather than assumed: 40 lines with the reader at 40, the file cut to
      6 → the next look resets its count, re-reads all 6 and reports the file's own maximum.

## 3. The falsification

- [x] 3.1 `267.1` — the delta pass removed → `RED adapter#aRotatedStreamKeepsEveryTurnEnd` (*every turn_end … expected
      '30', got '7'*), restore green.
- [x] 3.2 `267.2` — the `turns` fallback removed → `RED state#theSpendSurvivesTheLossOfItsDeltaLines` (*input tokens …
      expected '600', got 'null'*), restore green.
- [x] 3.3 `tools/loop/tests/loop-mutations.sh --check` over the whole set: 314 records apply to the files they name.

## 4. The prose the change makes false

- [x] 4.1 `tools/loop/loop.conf`'s `LOOP_CHILD_EVENTS_MAX_BYTES` block: what a rotation drops is the deltas, with the
      1 697-against-1 372 measurement, the per-type byte shares of `items/833`, the precondition, and the residue #841.
- [x] 4.2 `guidelines/Backlog.recipes.md` §7b, which owns the rotation's behaviour: the same facts routed through the
      existing paragraph, plus the 833 measurement at a 4 MiB bound.

## 5. The gate (not run by this change's own pass)

- [ ] 5.1 `tools/loop/gate.sh --base main` on the change, with `LOOP-GATE-TREE` and `LOOP-GATE` quoted into the archive.
- [ ] 5.2 The whole loop suite standalone: `tools/loop/tests/loop-selftest.sh --from-git main`.
- [ ] 5.3 The independent read-only review, then `openspec archive 2026-11-10-fix-a-rotation-keeps-the-record-lines`.
- [ ] 5.4 The re-read the issue asks for once the record is whole: whether the phases share rises from 52 % on an
      archived run whose streams the delta pass keeps whole (issue #840, "What it owes").
