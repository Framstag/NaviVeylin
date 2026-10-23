# verification — fix-loop-selftest-compiles-the-core-on-request

Read against the change's own artifacts and evidence, before archive (2026-10-23T10:40Z).

| what is checked | answer | where |
|---|---|---|
| the requirement's scenario 1 has a case, and the case is red on HEAD | `driver#aCaseThatAsksForNoCoreCompilesNothing` — red: `tests="1" failures="1"`, green after the fix | `evidence/red-HEAD.xml`, `evidence/green-fix.xml` |
| the requirement's scenario 2 has a case, and the case is red on HEAD | `driver#aCaseThatAsksForTheCoreGetsItOrRefuses` — red: `tests="1" failures="1"`, green after the fix | `evidence/red-HEAD-core-request.xml`, `evidence/green-fix.xml` |
| the delta merges into one capability | `agent-issue-loop`, one ADDED requirement, 2 scenarios | `specs/agent-issue-loop/spec.md` |
| the invariant is falsified by a mutation | record `48.1`: red-when-mutated 1 · not-noticed 0 · restore-failures 0 | `evidence/revert-check-48.1.log` |
| the declared mutation set still applies | 100 of 100 records | `evidence/gate-tallies/bash_tools_loop-mutations.sh_--check.xml` |
| the gate is green on the tree the change leaves | three suites, none failed; the loop's suite `tests="180" failures="0"` | `evidence/gate.log`, `evidence/gate-tallies/` |
| the first gate run is red, and why | `clean#reportsWithoutApplyAndRemovesWithIt`, `tmp ist nicht gesetzt` — the change's own defect, fixed before the second run | `evidence/gate-attribution.md`, `evidence/gate-first-red/` |
| the affected cases were run focused before the gate | the six cases whose call site the change touched, plus the two new ones, all PASS | `evidence/green-fix.log`, this session |
| the change touches no compiled file | three shell files, the spec delta and the change's own artifacts; no `*.kt`, `*.java`, `*.cpp`, no Gradle file | `git diff --stat` |
| no guideline was rewritten, and none was owed | `guidelines/Build.md` §2a states the iteration rule and quotes no measurement, so it asserts no compile to correct | `proposal.md`, "Owning guideline section" |

**CRITICAL issues found: none.** Two things a reviewer should read rather than take on trust:

1. **The scope is the loop's driver, not the five siblings.** Their case sets are the issue tooling's own, so a
   selection of theirs that asks for no core does not exist; the claim is argued with their helper names in
   `proposal.md` and `design.md`, "What is not changed".
2. **Two gate runs.** The first is kept and attributed (`evidence/gate-first-red/`, `evidence/gate-attribution.md`);
   the second is the verdict. A gate run twice for the same tree would be a defect, and this is not that.
