# Tasks — the gate's own record travels with the change it measured

Every step is a step of the plan. The change directory and its evidence are complete before the gate runs (issue
#837); the copy this change adds is the run's own landing step, and this change does not touch it in the tree.

## 1. The red case, before the copy exists

- [x] Add `worktree#theGatesTallyTravelsWithTheChangeItMeasured` to `tools/loop/tests/loop-cases.sh` — case list
      (`:130`), dispatch table (`:965`), body (`:4380`) whose first line declares `tools/loop/state.sh
      tools/loop/worktree.sh tools/loop/lib.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json`. Three
      arms in one case: a change whose evidence holds only its red record and whose gate left a green tally and log
      under `LOOP_GATE_TALLY_DIR` is committed **with** both copied in and reads `adoptable`; a gate whose verdict is
      red leaves the change's evidence untouched and the branch reads `archived-not-green`; a change that already
      holds a green record of its own is left as it stands rather than given a second copy.
- [x] Verify it red on the base, with the base commit's own `tools/loop/worktree.sh` and the case the only thing the
      tree did not carry: `bash tools/loop/tests/loop-selftest.sh --case
      'worktree#theGatesTallyTravelsWithTheChangeItMeasured'` → `FAIL`, exit 1, `1 of 1 cases failed`, the message
      `the gate's tally did not travel with the change it measured: red-on-HEAD.xml`. Log: `evidence/red-on-base.log`
      (2026-11-23T08:04:55Z).

## 2. The copy at the landing step

- [x] `carry_gate_record` (`tools/loop/worktree.sh:456`), called first in `cmd_end_item`
      (`tools/loop/worktree.sh:491`): returns unless the profile's tally directory holds a `gate.log` whose last
      `LOOP-GATE:` line `gate_tally_state` reads green; names the archived change this item just made as the one
      directory under `openspec/changes/archive` holding a file git does not track; and, when that change's
      `evidence/` carries no green record, copies the tally's `*.xml` and the gate's `gate.log` beside them, never
      over a file already there. Nothing outside `evidence/` is written.
- [x] Record `gate_tally_state`'s new reader in the symbol index: `tools/loop/shared.tsv:62` gains `worktree.sh`, and
      `tools/loop/layout.tsv:35` names `carry_gate_record` and `verification_record_green` in `worktree.sh`'s entry.

## 3. The one predicate

- [x] Move "this record is green" into `verification_record_green` (`tools/loop/worktree.sh:429`) and call it from
      `carried_verification` (`tools/loop/worktree.sh:559`) in place of the two `case` arms it carried, so the branch
      reader over git objects and the copy over the working tree cannot drift apart. `carried_verification`'s
      shapes, refusals and `branch-state`'s vocabulary are unchanged: a change whose only record is red still reads
      `archived-not-green`, which the case's second arm asserts.

## 4. The guideline passages the convention was never written in

- [x] `guidelines/Backlog.recipes.md` section 7b — the paragraph on what an item costs and the overwritten XML
      evidence gains `end-item`'s copy beside the worker's.
- [x] `guidelines/Backlog.recipes.md` section 7d — the paragraph on the pre-screen's two refusals gains where the
      green record comes from (the run's landing step, not the worker's diligence), the copy's two conditions, and
      the case that holds it: `worktree#theGatesTallyTravelsWithTheChangeItMeasured`.

## 5. The capability delta

- [x] `specs/agent-issue-loop/spec.md` — one ADDED requirement, "The gate's own record travels with the change it
      measured", and the one scenario `theGatesTallyTravelsWithTheChangeItMeasured` names. Its clauses map to the
      case: the copy before the commit (`:4403-4406`), `adoptable` naming the tally (`:4407-4410`), the change that
      answers for its own gate left alone (`:4435-4439`), the red gate leaving evidence untouched and the branch
      `archived-not-green` (`:4420-4425`).

## 6. The check the delta owes

- [x] The focused case green with the change in the tree: `bash tools/loop/tests/loop-selftest.sh --case
      'worktree#theGatesTallyTravelsWithTheChangeItMeasured'` → `PASS`, `1 of 1 cases passed`. Log:
      `evidence/green-case.log`.
- [x] `bash tools/loop/evidence-check.sh openspec/changes/fix-the-gates-tally-travels-with-its-change --root .` → no
      `FAIL` line (C1 §references, C2 tallies, C3 `/tmp` pointers, C4 mutation markers, C5 `file:line`).
- [x] The gate, once, over the whole delta: `./tools/loop/gate.sh` from the change root — the plan it asks for puts
      `driver#everyCaseDeclaresTheFilesItCanReach` first, because this delta adds a case, and its tally is the item's
      own report line.
