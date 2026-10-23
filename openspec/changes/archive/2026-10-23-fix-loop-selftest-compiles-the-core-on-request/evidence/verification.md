# verification — fix-loop-selftest-compiles-the-core-on-request

Read against the change's own artifacts and evidence, after the round-1 review and the corrections it asked for
(2026-10-23T10:45Z). Every number below is quoted from the artifact it names.

| what is checked | answer | where |
|---|---|---|
| the requirement's scenario 1 has a case, and the case is red on HEAD | `driver#aCaseThatAsksForNoCoreCompilesNothing` — red: `tests="1" failures="1"`, green after the fix | `evidence/red-HEAD.xml`, `evidence/green-fix.xml` |
| the requirement's scenario 2 has a case, and the case is red on HEAD | `driver#aCaseThatAsksForTheCoreGetsItOrRefuses` — red: `tests="1" failures="1"`, green after the fix | `evidence/red-HEAD-core-request.xml`, `evidence/green-fix.xml` |
| the requirement's scenario 3 has a case, and it is falsified | `driver#theCoreIsCompiledOncePerSuite` — a nested two-case suite whose cases both ask, counting the probe's invocations; green on this tree, red under record `48.2` | `evidence/green-correction.log`, `evidence/revert-check-48.2.log` |
| the invariant is falsified by a mutation | record `48.1` (the eager compile restored): red-when-mutated 1 · not-noticed 0 · restore-failures 0; record `48.2` (the marker dropped): the same three numbers | `evidence/revert-check-48.1.log`, `evidence/revert-check-48.2.log` |
| the declared mutation set still applies | 107 of 107 records | `evidence/gate-tallies-final/bash_tools_loop-mutations.sh_--check.xml` |
| the gate is green on the tree the change leaves | five suites, none failed — 328 cases over the three shell suites, which sum to the gate's own line in `evidence/gate-final-tree.log`: 187 + 107 + 34, the two doc suites printing no case count | `evidence/gate-final-tree.log`, `evidence/gate-tallies-final/` |
| the suites the pathspec owes ran | the loop's driver (`tests="187" failures="0"`, 774 s), the record check (`tests="107"`, 1 s), the harness's own suite (`tests="34"`, 76 s), the classification gate (3 s) and the section-reference check (0 s) — and no compiled step: since #273 a compiled path is read by the tree it sits in, so an archived evidence tally and a `tools/**` shell file owe no build | `evidence/gate-final-tree.log`, `evidence/gate-tallies-final/` |
| the first gate run is red, and why | `clean#reportsWithoutApplyAndRemovesWithIt`, `tmp ist nicht gesetzt` — the change's own defect, fixed before the second run | `evidence/gate-attribution.md`, `evidence/gate-first-red/` |
| the affected cases were run focused | six cases — the two new ones, the once-per-suite one, `state#initAndShowRoundTrips`, `run#oneItemOrderIsFixed`, `clean#reportsWithoutApplyAndRemovesWithIt` — all PASS; the six whose call site the change touched are in the gate's own transcript | `evidence/green-correction.log`; `evidence/gate-round-1/gate.log` (`:6`, `:7`, `:148`, `:149`, `:278`, `:589` of that transcript's numbering) |
| the compile the selection pays for | 0.904 s and 0.869 s of wall clock over two runs, 500–504 % CPU; the re-runnable command is in the file | `evidence/compile-measurement.log` |
| the change touches no compiled file | three shell files, the spec delta and the change's own artifacts; no `*.kt`, `*.java`, `*.cpp`, no Gradle file | `git diff --stat` |
| the two doc suites were re-run over the paths this prose touches | `gen-feature-list.sh --check-classification` and `check-doc-routes.sh`, both exit 0 — a focused re-run, not a second gate. The order is stated rather than assumed: this file and `tasks.md` are edited after that record, and neither suite reads them (`tools/check-doc-routes.sh:15`–`:16`: an archived change artifact is history, neither checked nor rewritten) | `evidence/post-gate-doc-checks.log` |
| no guideline was rewritten, and none was owed | `guidelines/Build.md` §2a states the iteration rule and quotes no measurement, so it asserts no compile to correct | `proposal.md`, "Owning guideline section" |
| the record names the tree it measured | `2284a998` — the merge that resolved this branch against `main`; the gate ran after every artifact that precedes it, and the prose after it is the doc suites' focused re-run | `evidence/gate-final-tree.log` (first line `gate: tools/loop-selftest.sh`), `evidence/post-gate-doc-checks.log` |

**CRITICAL issues found: none.** Five things a reviewer should read rather than take on trust:

1. **The scope is the loop's driver, and two siblings are owed the same measurement.** Round 1's sentence ("their
   case sets are the issue tooling's own … so a selection of theirs that asks for no core does not exist") was
   false: `tools/check-portability-selftest.sh:71` and `tools/resolve-stale-selftest.sh:50` precompile the core for
   case sets (`tools/portability-cases.sh`, `tools/resolve-stale-cases.sh`, 27 shell cases each) that never invoke
   Java, so a `--case` run of theirs pays the same fixed second. `proposal.md` and `design.md`, "What is not
   changed", now say exactly that, with the command that shows it (`grep -c 'java' tools/portability-cases.sh` →
   `1`). Leaving them unchanged is a bound, not an exemption.
2. **Three gate runs, and the last is the one that counts.** The round-1 run is kept and attributed
   (`evidence/gate-round-1/`, `evidence/gate-first-red/`, `evidence/gate-attribution.md`); its record named three
   suites, which round 2 answered with six. Round 2's own record then predated the last write of `tools/loop-cases.sh`
   by 96 s (13:01:38 against 13:03:14), and that write was a case's repair, not prose — so the record of the tree
   this change leaves is `evidence/gate-final-tree.log`, taken after the merge with `main` and after every pointer
   the review named. That run owes no compiled step, because #273 landed in between and now reads a compiled path by
   the tree it sits in.
3. **`evidence/revert-check-48.1.log` states no reason.** The harness prints the record, the case and the verdict
   (`RED`/`restore`) and neither a timestamp nor the case's own output (`tools/loop-mutations.sh:576`, `:583`). The "right
   reason" is therefore read from the case's assertions — a poisoned `javac` reachable above `source` makes
   `assert_eq "0" "$status"` fail — and not quoted from that log. `evidence/revert-check-48.2.log` has the same
   shape: the case it names fails on `assert_eq "1" "${invocations}"`, because the mutation makes the probe record
   two invocations.
4. **`evidence/green-fix.log` holds two cases, not eight.** The affected-cases row above therefore cites the gate's
   transcript and, for the focused re-run, `evidence/green-correction.log`.
5. **Round 2's record carried a compiled step; this one does not.** Gradle printed `BUILD SUCCESSFUL in 4m 6s` and no
   `all N cases passed` line, so the gate counted no case for it — the shape every archived gate record in this
   repository shows. The final run owes no compiled step at all: #273 landed in between and reads a compiled path by
   the tree it sits in, so the two `tests="0"` tallies under `gate-tallies-final/` are the doc suites.
6. **The mutation label was renumbered at the merge with `main`.** The marker record was `49.1` until #273's merge
   took `49.1`–`49.4` for its own invariants; it is `48.2` in the tree, and the pre-merge run is kept as
   `evidence/revert-check-49.1-before-the-merge.log` — a dated log quotes the label it ran under.
