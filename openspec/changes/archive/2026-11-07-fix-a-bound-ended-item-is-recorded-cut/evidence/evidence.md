# Evidence — fix-a-bound-ended-item-is-recorded-cut

Every artifact below is a copy of what the run's own processes wrote (the planning/apply worker, the scoped
suite, the mutations run and the gate child); the archive pass wrote only this index and `evidence-check.log`.

## The red run, on the base `6e9319f1`

`red-on-HEAD.log` is the run, `red-on-HEAD.xml` its tally — three cases, three failures, all
`expected 'cut', got 'blocked'`, 2026-11-07T00:37:14Z: `run#childPastItsTurnBoundIsCut`,
`run#childPastItsContextBoundIsCut`, `run#childOutlastingTheWallClockIsCut`. The shell runner writes no JUnit file
of its own, so the XML was transcribed beside the log and carries the quoted pair.

## Green on the change

`scoped-suite.log` and `scoped-suite.xml` — `tools/loop-selftest.sh --files "tools/loop/run.sh
tools/loop/state.sh tools/loop/report.sh"` (276 of the 345-case set), 0 failures, 0 errors, 363.02 s,
2026-11-07T00:53–00:59Z. `change-checks.log` — `openspec validate`, `tools/check-openspec-hygiene.sh`,
`tools/check-doc-routes.sh` and `tools/check-todo-references.sh`, all green. `measure.txt` —
`tools/loop/measure.sh --change fix-a-bound-ended-item-is-recorded-cut`, the figure `proposal.md` quotes.

`mutations-92.log` and `mutations-92.3.log` — the three records this change adds to `tools/loop-mutations.sh`
(92.1–92.3) red-when-mutated and green again on restore, the record ids 23.1/81.1 repointed beside them.

## The gate, once, by the gate child

`gate.log` — the gate's full output (1764 lines), rendered in `.pi/worktrees/loop` after the change was applied.
It ends on the gate line this item is closed on — 871 cases, 0 failures, 0 errors over the loop selftest, the
Gradle unit run, the `--from-git` suite, the mutation checks and the documentation/openspec/portability steps.
Its per-step tallies are copied here from the gate's own tally directory: `tools/loop-selftest.sh --from-git`
is `tools_loop-selftest.sh_--from-git_6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db.xml` (345 cases, 0 failures,
0 errors), the rest are the `gradlew_test_…`, `tools_…` and `tools_mutate.sh_…` files beside it. Summed over the
tallies in this directory, 871 cases, 0 failures, 0 errors — the gate line's tally is that sum, and no single JUnit
file carries it. The three `…IsCut` cases are inside the 345-case suite, so the gate's own tally carries the red
cases this change cites as passing.

`evidence-check.log` — `bash tools/loop/evidence-check.sh openspec/changes/fix-a-bound-ended-item-is-recorded-cut
--root .` from the change root, immediately before the archive: every `file:line`, every quoted tally pair, every
`/tmp` pointer and every `§N` resolves.
