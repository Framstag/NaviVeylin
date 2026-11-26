# One case's own log is not the change's gate

## Why

The `.log` arm of `verification_record_green` (`tools/loop/worktree.sh:436`) accepts any file under an archived
change's `evidence/` that holds one `^PASS <group>#<case>` line and no `^FAIL ` line, so a branch whose run's
whole-suite gate was red is read as verified on the strength of the one case it happened to pass — and the record the
adoption hands the closure prints the directory's whole file list (`carried_verification`,
`tools/loop/worktree.sh:568`), so the red proofs are named beside the green one as the evidence checked.

**The evidence.** Measured 2026-11-22 on item #1006's branch
`fix/issue-1006-the-refusal-leaves-sed-out-and-sed-is-no` (head `f826cd7a`, now an ancestor of `main`): 9 evidence
files, three accepted by the arm with one `PASS` line each — `green-policy-case.log`
(`PASS policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot`), `green-prompt-case.log`
(`PASS prompt#thePhasePromptNamesWhatItsShellRefuses`), `structural.log`
(`PASS driver#everyCaseDeclaresTheFilesItCanReach`) — while the same change carried `gate-red-policy-case.log`
(`FAIL` ×2) and `red-on-HEAD.log` (`FAIL`), and the same run's tail read `LOOP-VERDICT: blocked` and
`LOOP-GATE: tests=1261 failures=1 errors=0` (`.pi/issue-loop/items/1006/worker-tail-report.md:8,:12`, not retained — the
report itself is the record). The next run
adopted it, and the adoption line (`evidence checked:` in `.pi/issue-loop/one-item-runs.log:9695`) lists all 9 files,
the red proofs named beside the green ones.

**Root cause, in one sentence:** the `.log` arm asks whether a log holds *a* passing case rather than whether it holds
the suite the change touches, so one case's own log stands for the change's gate.

**The red case**, on the base `852983bf6fa4a89c3b3f365ffc9db8895cacfbbd` with the case added and the `.log` arm's
narrowing reverted (the mutation the falsification step runs):

```
LOOP-RED-TEST: worktree#aSingleCasesLogIsNotTheChangesGate fails on HEAD — XML tests=1 failures=1 (2026-11-26T00:33:52Z)
```

It failed on the assertion that is the item: `one case's own log is not the change's verification: adoptable
2026-01-01-fix-a-single-cases-log green-policy-case.log: expected 'archived-not-green', got 'adoptable'` — the state
the arm's one-line acceptance answered, on a branch whose evidence also held `gate-red-policy-case.log` and
`red-on-HEAD.log` (`evidence/red-on-HEAD-aSingleCasesLogIsNotTheChangesGate.xml`, `.log`).

**The owning requirement.** `agent-issue-loop`'s "A finished attempt's branch is merged and its item closed"
(`openspec/specs/agent-issue-loop/spec.md:415`) is the scenario that reads the branch's verification record; the red
gate's verdict has its own requirement since issue #1035, and this is the arm beside it. **The owning guideline
section** is "Refusals, records and the landing" in `guidelines/Backlog.recipes.md`: the paragraph that states which
branch "own evidence does not admit" stated the two record shapes nowhere, and this change adds the one paragraph that
states them, so the predicate the fix narrows is written down once.

LOOP-ROUTE: guidelines/Backlog.recipes.md §7d

**Scope.** `tools/loop/worktree.sh` (the `.log` arm of `verification_record_green` at `:437`–`:441`,
`carried_verification` at `:573`–`:583` and the comment above it) and `tools/loop/tests/loop-cases.sh` (the re-scoped
case `worktree#aShellSuiteLogIsAVerificationRecord` at `:4446`, the new case
`worktree#aSingleCasesLogIsNotTheChangesGate`, two registry rows) — plus one paragraph of the guideline's "Refusals,
records and the landing" section. The gate is the shell suite
`tools/loop/tests/loop-selftest.sh --files tools/loop/worktree.sh` and the structural checks of the files edited;
nothing compiled changes, so no Gradle build is owed.

**Not in scope.** The residue the item names: a branch carrying a genuine multi-case suite log from an earlier green
round is still adoptable after a later red gate — that is #1035, which makes the run's verdict itself travel onto the
branch, and it landed already (`evidence/gate-red.log` is read before the verification).
