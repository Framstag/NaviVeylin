# Design — a close over a red gate keeps its clause

## The root cause

The `file:line` references here are the base `eb513ca5`'s, except where a hunk of this change is named.

`tools/loop/run.sh:2358`–`:2364` computes the tally an item's gate produced into `gate_field` — the child's own
`LOOP-GATE` line, the count of test-results XMLs the tree holds, `skipped (no gate configured)` — and
`tools/loop/state.sh:519`/`:616` writes it into `metrics.gate` verbatim. The field is one string, so the record cannot
say whether the gate passed: the tally of a gate that printed `failures=2 errors=0 (mobile)` sits beside `result:
done` exactly as
one that printed `failures=0 errors=0 (mobile)` does, and `tools/loop/report.sh:73` counts both as a closed item.

What a closing child did with the red — the clause it waived it with — is written nowhere but its own transcript: the
tail's prose says "file untouched by this diff" or "green in the automotive run" and no reader of the state can find it.
Measured on the run of `2026-11-06T21:19:06Z`, 3 of 7 closed items carry that shape (#806, #759, #799).

## The single fix

One predicate and three writings of it, no new branch in the outcome chain:

1. **`gate_tally_state` in `tools/loop/lib.sh`** reads `red` / `green` / `unknown` out of the tally text. It is in
   `lib.sh` because both `state.sh` and `run.sh` need the same answer, and two readings of "did the gate pass" is the
   defect one layer down. `unknown` is the shapes that carry no count, and it is neither: a project with no gate closes
   without a waiver, and its tally is not a red it closed over.
2. **`tools/loop/state.sh done`** takes `--gate-waived` and `--gate-waived-suite`, writes them into
   `metrics.gateWaived` / `metrics.gateWaivedSuite`, and refuses a `done` over a `red` tally that records neither. The
   refusal is the closed vocabulary this script already keeps: a usage error, before `patch_state`, so the item is still
   queued and nothing was written.
3. **`tools/loop/run.sh`** reads `LOOP-GATE-WAIVED` / `LOOP-GATE-WAIVED-SUITE` from the report the item is decided
   from (`run.sh:2289`–`:2290`, beside `LOOP-GATE`) and passes them to the `closed` arm; and, after the outcome chain
   (`run.sh:2457`), turns a `closed` outcome over a `red` tally with no clause into `blocked` with `stop_reason:
   refused` — before `merge_committed_branch` and before `loop_labels close`, because the state write happens after
   both and a write refused there leaves the tracker saying `closed` about a record that was never made.
4. **`tools/loop/report.sh`** prints `waived: N of M closed items (<suite> ×n, …)`, grouped by suite.
5. **`tools/loop/prompts/worker-tail.md`** is where the clause comes from: the tail writes the verdict, so it writes the
   waiver line.

## Blast radius

- **Two fields of the item record**, both new: `metrics.gateWaived`, `metrics.gateWaivedSuite`. `metrics.gate` is
  untouched, so every reader of the tally the archive already holds reads what it read before.
- **`tools/loop/report.sh`** gains one trend line. Nothing else in it reads the new fields: the gate-rerun action
  (`report.sh:488`), the review line (`:284`–`:295`) and the table's columns are unmoved.
- **`tools/loop/run.sh`** loses no arm: the four non-closed arms are untouched, and the one new branch is read after the
  outcome chain and before the merge.
- **`tools/loop/state.sh`**: `done` gains two flags and one refusal. Every caller in the tree passes the flags only when
  it has them, so no existing call site changes meaning; `state.sh report`'s per-item table is unmoved.
- **The suite**: 3 cases added (2 `state#`, 1 `run#`), 3 mutation records added, no case rewritten and no fixture
  realigned — the fixtures the new assertions need are the ones `run#aRedGateIsBlockedByTheTail` already stages (a
  worker that commits and archives, a stub `gh` that records the closure).

## Rollback

Revert the hunks in the five files this change edits; `metrics.gateWaived` disappears and every case the suite had
before is green again. A record
already written keeps the fields it was written with — they are additive, and `report.sh` reads their absence as the
"written before the field existed" dash it prints for every other new field.

## Alternatives eliminated

- **A `report.sh` filter that re-derives the waiver from the gate's XMLs.** Which red an item waived is not in the XMLs:
  #806 and #759 waived the same `NoClassDefFoundError` and #799 a different one, and nothing in a tally says whether the
  failure was in the change's paths. A second reading would be a guess printed as a count — the thing the report's own
  rule ("a figure nobody measured prints `-`") exists to prevent.
- **A `--gate-waived` that defaults to a sentence the run invents** ("closed without a waiver"). It would make the
  field non-empty for every close and the count meaningless — the state would print `7 of 7 waived` and a reader could
  not tell the three real ones.
- **Recording the waiver on every result, not only `done`.** A blocked or cut item is answered by its decision, not
  waived: `--gate-waived` beside `--result blocked` was refused in the case that drives it, because the field would
  then count items that did not close.
- **Refusing only in `run.sh`, or only in `state.sh`.** The run's refusal alone leaves the record unguarded for any other
  caller; the state's refusal alone fails after the merge and after the tracker was told the item closed. Both cost two
  lines and are the same predicate.
- **A per-suite total across `.pi/issue-loop/runs/*.json`.** `report.sh` reads one state and git, by rule; a reader of
  the archive is a different delta with its own cost. The run-level count is the figure this report can honestly print.

## The guideline section

`guidelines/Backlog.recipes.md`:710 "Refusals, records and the landing" owns what a run records of an item and what it
does with a refusal, and it is where this rule belongs: it already names which refusals write no item record and what a
landing owes the tree, and it said nothing about a gate that did not pass (`./tools/look.sh 'waiv' --path guidelines/`
→ `0 matches in 0 files`). The paragraph this change adds sits beside the commit identity paragraph and names the two
fields, the refusal on both sides, the report's line and the three cases and three mutations that hold it.

## Open decisions

none
