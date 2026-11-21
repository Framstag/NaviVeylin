# Review round 1 — answered

The round is `evidence/review-round-1-report.md` (an independent, read-only reviewer over commit `55589fc1`, not the
author's own pass). Verdict: **PASS with findings** — 3 should-fix, 4 nit.

## Applied

**1 (should-fix) — the clause placed the difference on the child.** Finding 1 is right and is the one that mattered:
the reading it names is normally the one the run declined to place (`attributed=none`, which item #894's own record
carries), and `agent-issue-loop`'s requirement "A difference the run cannot attribute does not decide the item" says a
difference nobody can place is not evidence about one child. `run.sh`'s clause is now conditional — `if that
difference is this item's work it is in the caller's tree and not on the item's branch` — and its comment states why.
The case's asserted substring moved with it.

**2 (should-fix) — the delta contradicted a shipped requirement.** The requirement now says the clause SHALL place
the difference on no child, and the scenario says the verdict and the placement stay what the unattributable-difference
requirement makes them. It stays under the 500-character body bound and `openspec validate --strict` is clean.

**3 (should-fix) — `guidelines/Backlog.recipes.md` was stale.** The paragraph that owns this behaviour no longer
claims the reason is "written twice" (the outcome block's arm writes the same sentence as the pre-gate reading), and a
new paragraph states the clause, its conditionality, the two cases and record 983.1.

## Not applied, and why

**4 (nit) — the `# files:` token.** `tools/loop/tests/fixtures/` would be the wider, correct token; the case reaches
`fixtures/pi` for `LOOP_CHILD_ROOT` and `fixtures/scenarios/` for its issue. Recorded here rather than folded in: the
declaration is a selection hint and `driver#everyCaseDeclaresTheFilesItCanReach` passes either way, so widening it
belongs with whoever next touches the fixture.

**5 (nit) — the stop arm's reading.** At `run.sh`'s "could not start" stop the sentence names the child that exited
while `item_outside_write_clause` prefers the change child's reading. A real narrowness (it needs an empty tree, no
verdict and an exhausted retry budget), and no case exercises that arm; it is filed as a follow-up rather than widened
here, because a second reading source with no case is the shape this loop refuses.

**6 (nit) — the `falsified` arm.** Same shape one arm over, and it writes no sentence to append to; a second change.

**7 (nit) — the double append at `run.sh:3354` + `:3878`.** Deliberate, as the reviewer prefers: the pre-gate step is
where a reader looks for the reading. It is now stated in `Backlog.recipes.md`.
