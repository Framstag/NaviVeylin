# verification — fix-loop-selftest-compiles-the-core-on-request

Read against the change's own artifacts and evidence, after the round-1 review and the corrections it asked for
(2026-10-23T10:45Z). Every number below is quoted from the artifact it names.

| what is checked | answer | where |
|---|---|---|
| the requirement's scenario 1 has a case, and the case is red on HEAD | `driver#aCaseThatAsksForNoCoreCompilesNothing` — red: `tests="1" failures="1"`, green after the fix | `evidence/red-HEAD.xml`, `evidence/green-fix.xml` |
| the requirement's scenario 2 has a case, and the case is red on HEAD | `driver#aCaseThatAsksForTheCoreGetsItOrRefuses` — red: `tests="1" failures="1"`, green after the fix | `evidence/red-HEAD-core-request.xml`, `evidence/green-fix.xml` |
| the requirement's scenario 3 has a case, and it is falsified | `driver#theCoreIsCompiledOncePerSuite` — a nested two-case suite whose cases both ask, counting the probe's invocations; green on this tree, red under record `48.2` | `evidence/green-correction.log`, `evidence/revert-check-48.2.log` |
| the invariant is falsified by a mutation | record `48.1` (the eager compile restored): red-when-mutated 1 · not-noticed 0 · restore-failures 0; record `48.2` (the marker dropped): the same three numbers | `evidence/revert-check-48.1.log`, `evidence/revert-check-48.2.log` |
| the declared mutation set still applies | 101 of 101 records | `evidence/gate-tallies/bash_tools_loop-mutations.sh_--check.xml` |
| the gate is green on the tree the change leaves | six suites, none failed — the gate's own `LOOP-GATE` line is in `evidence/gate.log`, and the three shell suites' counts sum to it: 181 + 101 + 34 = 316 cases | `evidence/gate.log`, `evidence/gate-tallies/` |
| the suites the pathspec owes ran | the loop's driver (`tests="181" failures="0"`), the record check (`tests="101"`), the harness's own suite (`tests="34"`), the classification gate and the section-reference check (both `tests="0"`, neither prints a case count), and the compiled step — `BUILD SUCCESSFUL in 4m 6s`, tallied `tests="0"` because Gradle prints no `all N cases passed` | `evidence/gate-tallies/`, `evidence/gate-tallies/._gradlew_test_-PforceTests_--no-build-cache.xml` |
| the first gate run is red, and why | `clean#reportsWithoutApplyAndRemovesWithIt`, `tmp ist nicht gesetzt` — the change's own defect, fixed before the second run | `evidence/gate-attribution.md`, `evidence/gate-first-red/` |
| the affected cases were run focused | six cases — the two new ones, the once-per-suite one, `state#initAndShowRoundTrips`, `run#oneItemOrderIsFixed`, `clean#reportsWithoutApplyAndRemovesWithIt` — all PASS; the six whose call site the change touched are in the gate's own transcript | `evidence/green-correction.log`; `evidence/gate.log` (`:6`, `:7`, `:148`, `:149`, `:278`, `:589` of the round-1 transcript numbering) |
| the compile the selection pays for | 0.904 s and 0.869 s of wall clock over two runs, 500–504 % CPU; the re-runnable command is in the file | `evidence/compile-measurement.log` |
| the change touches no compiled file | three shell files, the spec delta and the change's own artifacts; no `*.kt`, `*.java`, `*.cpp`, no Gradle file | `git diff --stat` |
| the two doc suites were re-run after this file was finalised | `gen-feature-list.sh --check-classification` and `check-doc-routes.sh`, both exit 0 — a focused re-run of the paths this prose edit touches, not a second gate | `evidence/post-gate-doc-checks.log` |
| no guideline was rewritten, and none was owed | `guidelines/Build.md` §2a states the iteration rule and quotes no measurement, so it asserts no compile to correct | `proposal.md`, "Owning guideline section" |

**CRITICAL issues found: none.** Five things a reviewer should read rather than take on trust:

1. **The scope is the loop's driver, and two siblings are owed the same measurement.** Round 1's sentence ("their
   case sets are the issue tooling's own … so a selection of theirs that asks for no core does not exist") was
   false: `tools/check-portability-selftest.sh:71` and `tools/resolve-stale-selftest.sh:50` precompile the core for
   case sets (`tools/portability-cases.sh`, `tools/resolve-stale-cases.sh`, 27 shell cases each) that never invoke
   Java, so a `--case` run of theirs pays the same fixed second. `proposal.md` and `design.md`, "What is not
   changed", now say exactly that, with the command that shows it (`grep -c 'java' tools/portability-cases.sh` →
   `1`). Leaving them unchanged is a bound, not an exemption.
2. **Two gate runs, and the second is the one that counts.** The round-1 run is kept and attributed
   (`evidence/gate-round-1/`, `evidence/gate-first-red/`, `evidence/gate-attribution.md`); its record named three
   suites, which is the finding this round answers — `evidence/gate.log` names six, including the two the
   `openspec/` and `*.md` paths own and the compiled step the change's own `*.xml` evidence pulls in. A gate run
   twice for the same tree would be a defect, and this is not that.
3. **`evidence/revert-check-48.1.log` states no reason.** The harness prints the record, the case and the verdict
   (`RED`/`restore`) and neither a timestamp nor the case's own output (`tools/loop-mutations.sh:550`). The "right
   reason" is therefore read from the case's assertions — a poisoned `javac` reachable above `source` makes
   `assert_eq "0" "$status"` fail — and not quoted from that log. `evidence/revert-check-48.2.log` has the same
   shape: the case it names fails on `assert_eq "1" "${invocations}"`, because the mutation makes the probe record
   two invocations.
4. **`evidence/green-fix.log` holds two cases, not eight.** The affected-cases row above therefore cites the gate's
   transcript and, for the focused re-run, `evidence/green-correction.log`.
5. **The compiled step's tally carries `tests="0"`.** Gradle prints `BUILD SUCCESSFUL in 4m 6s` and no
   `all N cases passed` line, so the gate counts no case for it — the shape every archived gate record in this
   repository shows, and the reason the gate's sum is the three shell suites' 181 + 101 + 34 = 316.
