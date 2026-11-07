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
`tools/check-doc-routes.sh` and `tools/check-todo-references.sh`, all green. `change-checks-round2.log` — the same
four after the review's corrections, all green (`openspec validate --all`: 189 passed, the four red items other open
deltas, this change not among them). `measure.txt` —
`tools/loop/measure.sh --change fix-a-bound-ended-item-is-recorded-cut`, the figure `proposal.md` quotes.

`mutations-92.log` and `mutations-92.3.log` — the three records this change adds to `tools/loop-mutations.sh`
(92.1–92.3) red-when-mutated and green again on restore, the record ids 23.1/81.1 repointed beside them.

## The gate, once, by the gate child

`gate.log` — the gate child's full output (1764 lines; the 1874-line `gate.log` in the item directory is the
different, earlier 2026-11-06 gate and not this one), written in `.pi/worktrees/loop` after the change was applied.
It ends on the gate line this item is closed on — 871 cases, 0 failures, 0 errors over the loop selftest, the
Gradle unit run, the `--from-git` suite, the mutation checks and the documentation/openspec/portability steps.
Its per-step tallies are copied here from the gate's own tally directory, and the 871 is the sum of the seven that
carry cases: the `--from-git` suite 345
(`tools_loop-selftest.sh_--from-git_6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db.xml`), the mutation set 262
(`bash_tools_loop-mutations.sh_--check.xml`), its self-test 34, the issue-tooling self-test 160, the doc-routes
self-test 34, the openspec-hygiene self-test 7 and the portability self-test 29 — 0 failures, 0 errors, so the gate
line's tally is that sum and no single JUnit file carries it. The `gradlew_test_…` and `tools_mutate.sh_…_list` files
beside them carry `tests="0"` (`--list` steps and the Gradle run in this flavour), and `red-on-HEAD.xml` (3) and
`scoped-suite.xml` (276) are this item's own runs, not gate steps, so they are not in the sum. The three `…IsCut`
cases are inside the 345-case suite, so the gate's own tally carries the red cases this change cites as passing.

`evidence-check.log` — `bash tools/loop/evidence-check.sh openspec/changes/fix-a-bound-ended-item-is-recorded-cut
--root .` from the change root, immediately before the archive; `evidence-check-round2.log` is the same check over
the archived copy after the review's corrections (`… archive/2026-11-07-fix-a-bound-ended-item-is-recorded-cut
--root .`), and it is the one this index quotes: 850 `file:line` references resolved and in range, 2 unresolved or
ambiguous (C5's own count), every quoted tally pair confirmed, no `/tmp` pointer cited and no mutation marker in
the configured path. The two are one referent cited twice — a bare basename: `state.sh:289` matches three
`state.sh` files on this host (`tools/loop/state.sh` and two a self-test left under `.pi/tmp`), which C5 reports
rather than resolves.
