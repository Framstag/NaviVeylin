# A finished attempt carries the tally its own gate wrote

An item's green proof is the XML its gate's steps wrote under the gate's tally directory
(`LOOP_GATE_TALLY_DIR="app/build/test-results/loop-gate"`, `tools/loop/loop.conf:829`). Nothing copies it into the
archived change: `end-item` (`tools/loop/worktree.sh:426`) commits the fix and then the archive with whatever the
change's `evidence/` already held, and the run's start clears the tally before every child
(`clear_stale_test_results`, `tools/loop/run.sh:2006`, the case `run#aChildIsNotBlamedForResultsItsGateNeverWrote`). A worker that cited its gate
in its report and copied no XML leaves the change with the red side only, which is exactly what a blocked attempt
leaves.

**The consequence:** `carried_verification` (`tools/loop/worktree.sh:483`) reads the archive's `evidence/` alone, so
that branch reads `archived-not-green` to `branch-state` (`tools/loop/worktree.sh:573`) — the owner's question —
although its gate ran and passed. Measured 2026-11-19 on #964: `branch-state 964
fix/issue-964-a-mutation-record-quotes-the-fixture-s-f` printed
`archived-not-green	2026-11-19-fix-a-mutation-record-quotes-the-failing-assertion`, while the same change's
`evidence/` held `gate.log` (`LOOP-GATE: tests=780 failures=0 errors=0`, its tally XML not retained) and its green
proof was only console text.
One of the ten decision slots each run's start is capped by went to a change whose gate was green.

**Root cause, in one sentence:** `end-item` commits the archived change without the gate's tally, so a change whose
proof is the tally's XML carries no green verification record and reads as never verified.

**The red test:** the leg this change adds — `tools/loop/tests/loop-cases.sh`,
`worktree#endItemCopiesTheGatesTallyIntoTheEvidence` — is red before the copy exists: with `end-item`'s
`copy_gate_tally_into_evidence` call removed (the base's own behaviour: the case is new, so the base tree carries neither
the case nor the copy), `end-item` leaves the archived change's `evidence/` holding the red XML alone, and the case's first
assertion fails with
`the gate's tally was not copied into the archived change's evidence: TEST-demo-red-on-HEAD.xml`. The suite's own
line for it — `1 of 1 cases failed` — is `evidence/red-on-base.log` (a shell self-test writes no JUnit XML of its
own, so the red record is that log and its tally is the suite's line, not an XML's `failures=`).

**The fix, one step:** `end-item` copies every XML the gate's tally directory holds into
`openspec/changes/archive/<change>/evidence/` when that evidence carries no green record, before the archive commit is
taken — so the branch adoption reads is self-contained and `carried_verification` needs no widening. Widening it to
accept a `LOOP-GATE:` line in an evidence log is rejected: it is the predicate adoption trusts, and a log line is
easier to carry without the run that wrote it.

**The owning requirement:** `agent-issue-loop`, the requirement "Version control belongs to the run's script, not to
the agent" — its scenario `A branch without a green verification is not merged`
(`openspec/specs/agent-issue-loop/spec.md:427`) is the one that fires on this shape; the delta ADDs one requirement
stating the obligation `end-item` owes the evidence it commits, with the one scenario that names the new leg.
**The owning guideline section:** `guidelines/Backlog.recipes.md:315` section 7b "What an item costs, and the
bounds on it",
whose sentence about the gate's own run overwriting the previous run's XML evidence is where the convention that a
cited XML is copied into the change's `evidence/` is stated (line 322). The delta makes that sentence name the step
that does it.

**Surface, measured before the plan was fixed:** `tools/look.sh 'carried_verification|green verification|gate.tally'`
reports 337 matches in 190 files, of which the non-archive files that assert the rule are
`tools/loop/worktree.sh` (8), `tools/loop/run.sh` (21), `tools/loop/tests/loop-cases.sh` (10),
`tools/loop/tests/loop-mutations.sh` (4), `tools/loop/state.sh` (3), `tools/loop/shared.tsv` (1),
`tools/loop/prescreen.sh` (1), `openspec/specs/agent-issue-loop/spec.md` (11) and three guideline documents
(`Backlog.recipes.md`, `Build.recipes.md`, `BuildEvidence.md`, one match each). This change adds 1 case, rewrites 0
and realigns 0 fixtures; the rule it changes is asserted in one place (`carried_verification`) and produced in one
(`end-item`), and only the producer gains the copy.
