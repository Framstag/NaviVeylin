# A blocked branch carries its own red gate, and is not adopted

## Why

**The defect.** A branch whose run ended `blocked` is read `adoptable` by the next run, merged into the base and
closed on it. Measured 2026-11-22/23 on item #1006: `item-result cut`, `item-result blocked`, `item-closed` — and
`main` moved to the blocked attempt's own head `f826cd7a` (`tools/loop/leftovers.sh:66`, the tail of
`adopt_finished_attempt`). Half the `item-closed` events in the whole record came from an adoption of a branch its
own run had refused.

**Root cause, in one sentence:** the gate's verdict is written only into the build output the tree ignores
(`LOOP_GATE_TALLY_DIR`), and the one step that carries a gate's record onto the branch refuses to carry it when the
gate did not pass (`tools/loop/worktree.sh:463`, `[ "$(gate_tally_state …)" = green ] || return 0`), so a branch
whose run blocked carries only the per-case logs its worker copied — and `carried_verification`
(`tools/loop/worktree.sh:554`) reads a `PASS`-carrying log as the change's verification.

**The red case** is `worktree#aBranchWhoseRunEndedBlockedIsNotAdoptable`: one archived change, one case's own log,
and a gate that did not pass; HEAD answers `adoptable` and merges the change onto the base
(`evidence/red-on-HEAD-aBranchWhoseRunEndedBlockedIsNotAdoptable.log`, taken on `7b8c1d13`).

**The owning requirement** is `agent-issue-loop` (the delta this change adds); the owning guideline section is
"Refusals, records and the landing" in `guidelines/Backlog.md`, whose sentence that a gate which did not pass carries
nothing stands at `guidelines/Backlog.recipes.md:1589`.

LOOP-ROUTE: guidelines/Backlog.md §7d

**Surface** (`tools/look.sh 'carry_gate_record|carried_gate_red|gate-red' --files`
— 155 matches in 79 files, of which five are the live owners): the writer, the reader and the state in
`tools/loop/worktree.sh` (15 matches), `tools/loop/tests/loop-cases.sh` (10 — one case added with its name row, its
dispatch arm and its body, and the blocked half of `worktree#theGatesTallyTravelsWithTheChangeItMeasured` rewritten),
`tools/loop/tests/loop-mutations.sh` (1 — the record that drops the new arm), `tools/loop/layout.tsv` (1 — the new
symbol), `guidelines/Backlog.recipes.md` (2 — one sentence rewritten) and the archived changes that name the rule
(read, unchanged).

## What changes

- The change's red gate is **carried** onto the branch as `evidence/gate-red.log` — the gate's own `LOOP-GATE:` and
  `LOOP-GATE-STEP:` lines and nothing else, so it can never read as a verification record.
- `carried_gate_red` reads it, `branch_state_of` answers `gate-red` before `archived-not-green`, `branch-state` prints
  the change and that record, and `adopt-item` refuses the branch naming the red gate instead of merging it.
- The item stays its owner's: `prescreen.sh`'s attempt-state arm already treats every state but `unfinished` and
  `rework-cut` as the pre-screen's decision, and `leftovers.sh` asks `adopt-item`, so neither needs an edit.

## Out of scope

The sibling half — one case's log standing for the change's verification — is issue #1026 and its own change. It
shrinks what this one has to trust and does not remove this cause: a blocked run may copy several case logs.
