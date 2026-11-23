# Proposal — the gate's tally travels with the change it measured

## Why

**Root cause.** The gate writes its record under `LOOP_GATE_TALLY_DIR` — `app/build/test-results/loop-gate`, the
project's build output — and that directory is not the change. `end_item` commits the change, `carried_verification`
(`tools/loop/worktree.sh:554`) reads the change's verification out of `openspec/changes/archive/<change>/evidence/`,
and nothing joined the two: the convention that a worker copies the tally into its own `evidence/` is written in
neither the worker's prompt nor any check. So a finished attempt whose gate passed and whose worker copied nothing
carried the red-on-HEAD record and no green one, and the next run read that branch `archived-not-green`,
escalated it as a decision and spent a slot on a question nobody owed.

**Measured 2026-11-21, item #964.** Its archived change held
`evidence/red-on-HEAD.xml` and nothing else, while its own gate's log read
`LOOP-GATE: tests=342 failures=0 errors=0` — a tally not retained (the next item's start cleared it); the tally
that would have verified it was in the worktree's build
output, which the next item's `start-item` clears. The item's decision — whether to have workers copy the tally by
convention, to widen the predicate to read the gate's build output from the tree, or to carry the record from
`end-item` — was answered the third way, because it is the only one whose copy lands in `evidence/`, the one
place a write after the gate is allowed.

## What changes

- `tools/loop/worktree.sh`: `end-item` copies the gate's tally (`*.xml`) and its own log into the archived change's
  `evidence/` when that change carries no green record of its own, and never over a file the worker wrote. A gate
  whose verdict is not green carries nothing — its red tally stays the record of the item it blocks.
- One predicate for "this record is green" (`verification_record_green`), read by both the branch reader that walked
  git objects and the copy that walks the working tree, so a change the run wrote itself cannot read back as
  unverified.
- The delta's guideline passages (sections 7b and 7d) say where the record comes from, since the convention they
  implied was the thing nobody enforced.

## Surface

**The requirement the item extends.** `openspec/specs/agent-issue-loop/spec.md:306`, "Version control belongs to the
run's script, not to the agent" — its scenarios at `:405-435` merge a branch whose archived change "whose
verification record is green" and refuse one "without a green verification", while nothing said how a green record
reaches that directory. This change adds the requirement the landing step was missing.

surface: 1 case added, 0 rewritten, 1 engine function added and 1 rewritten beside it, 0 fixtures retired —
`tools/look.sh 'carried_verification|archived-not-green|green verification record' --path tools` (9 matches in 2
files: `tools/loop/worktree.sh` 8, `tools/loop/layout.tsv` 1), plus the one case and the two guideline paragraphs
this change adds. Nothing else asserts the rule: `carried_verification` keeps its two shapes and `branch-state` its
vocabulary, so no other case is rewritten.

**The guideline.** `guidelines/Backlog.recipes.md` section 7d "Refusals, records and the landing", the paragraph on
the pre-screen's two refusals, gains the record's origin and the case it names; section 7b, the paragraph on what an
item costs, says `end-item` copies the gate's tally as well as the worker.

## What does not change

- `carried_verification`'s shapes, its refusals and `branch-state`'s vocabulary: a change whose only record is red
  still reads `archived-not-green`, and a change that already answers for its own gate is left exactly as it stands.
- No worker, reviewer or prompt phase learns a new step: the copy is the run's, made after the gate, in the one
  directory a write after the gate is allowed.
