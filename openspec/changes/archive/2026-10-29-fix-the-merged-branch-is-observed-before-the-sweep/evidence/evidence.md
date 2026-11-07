# Evidence — fix-the-merged-branch-is-observed-before-the-sweep

Item #698, base commit `1e2a4e6f2f483573075d7ad90a4ed6ec47d5166b`. Every log named here is in this directory;
the copies in `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/698/` are the run's, and this directory's are
the versioned half.

## The defect, as the tree carried it

Mutation record `42.2` removes `release-branch`'s deletion (`tools/loop/worktree.sh:698`, `gitr branch -d
"$branch"`), and the case it names stayed green. Taken once, on HEAD, before the change:

```
$ bash tools/loop-mutations.sh --only '42.2'
loop-mutations: selected 1 of 202 records (labels: 42.2)
GREEN  42.2  run#aMergedBranchIsRemoved   <-- the case did not notice the mutation
restore 42.2 run#aMergedBranchIsRemoved green again

red-when-mutated: 0 · not-noticed: 1 · restore-failures: 0        exit=1
```

`red-on-head.log`. A shell gate has no JUnit XML, so this line and the exit status *are* the tally
(`.pi/skills/revert-check`, "When the gate is a shell self-test, not Gradle").

The case (`tools/loop-cases.sh:7228`, `:7229`) asserted the branch after the run ended, and the run deletes it by
two paths — the release at `tools/loop/worktree.sh:698` (from `tools/loop/run.sh:1885`) and the end sweep at
`tools/loop/worktree.sh:664` (`prune-landed --apply`, from `tools/loop/run.sh:344`). One path disabled leaves the
other answering for it.

## Why it stayed green, measured

`probe-42.2-run-output.log` — `42.2`'s expression applied by hand to `tools/loop/worktree.sh`, the case's run
output kept, then the file restored (`git diff --stat` back to the one case body). The report the run prints
before its end sweep still names the item's branch, and the sweep removes it afterwards:

```
branches: fix/issue-21-an-eligible-bug-whose-cited-path-still-e loop-20261029-085718
landed, removable: fix/issue-21-an-eligible-bug-whose-cited-path-still-e loop-20261029-085718
…
removed fix/issue-21-an-eligible-bug-whose-cited-path-still-e (its patches are in the base)
worktree: 2 landed branch(es) removed · 0 kept (carries work the base does not) · 0 kept (checked out) · 0 kept (the reviewer refused it)
```

On the same tree without the mutation the report's `branches:` line names only the run's own
`loop-<timestamp>` branch.

## The record is noticed, and the case still passes without it

`revert-check.log` — `bash tools/loop-mutations.sh --only '42.1 42.2' --out …`, after the change:

```
RED    42.1  run#aMergedBranchIsRemoved   (the worktree released from the branch it holds)
       2026-10-29T09:23:32Z — the run left the branch of the item it closed behind: fix/issue-21-…
restore 42.1 run#aMergedBranchIsRemoved green again
RED    42.2  run#aMergedBranchIsRemoved   (the deletion of the merged branch)
       2026-10-29T09:23:55Z — the branch was removed where the run merged it, not by the end sweep that runs
       after the report: 'fix/issue-' should not appear in: fix/issue-21-… loop-20261029-092346
restore 42.2 run#aMergedBranchIsRemoved green again

red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0        exit=0
```

The failure is the new assertion's own message, not a premise and not a compile error, and both records' restores
are green again — so the two adjacent assertions still belong to their own records (`42.1` fails on the worktree
line, `42.2` on the report line).

`case-green.log` — the case alone, on the unmigrated tree:

```
$ bash tools/loop-selftest.sh --case run#aMergedBranchIsRemoved
PASS run#aMergedBranchIsRemoved        exit=0
```

## The change's own measurement

`measure.txt` — `./tools/loop/measure.sh`, reading this change:

```
LOOP-MEASURE-SUITE cases=277 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=1306 suites=21
LOOP-MEASURE-CHANGE fix-the-merged-branch-is-observed-before-the-sweep scenarios=4 cases=4 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 case rewritten, 0 cases added, 0 fixtures realigned, 0 mutation records added,
0 records rewritten
```

## The gate, run once

`gate.log`, run once from the change root; `evidence/gate-tallies/*.xml` are its per-suite tallies:

```
LOOP-GATE-PROFILE: …/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
LOOP-GATE: tests=676 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 275s,
tools/loop-selftest.sh --from-git 42d3e0004206ebfee39e11942db0bef375e1909e 518s,
bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 384s, …)
```

The step the change is about carried the whole shell suite, 277 cases and no failure
(`tools_loop-selftest.sh_--from-git_42d3e0004206ebfee39e11942db0bef375e1909e.xml`, `tests=277 failures=0`), and the
gate's record check applied every declared record (`bash_tools_loop-mutations.sh_--check.xml`, `tests=202
failures=0`). The gate was not run a second time.

## Delta and validation

```
$ openspec validate fix-the-merged-branch-is-observed-before-the-sweep --strict
Change 'fix-the-merged-branch-is-observed-before-the-sweep' is valid
```

The delta modifies one requirement ("A branch the loop made is removed when its work is in the base") by adding
one scenario, which names `run#aMergedBranchIsRemoved` — the case that exists, is green in the gate's suite, and
goes red under `42.2`.

## Archive

```
$ openspec archive fix-the-merged-branch-is-observed-before-the-sweep --yes
Applying changes to openspec/specs/agent-issue-loop/spec.md:
  ~ 1 modified
Specs updated successfully.
Change 'fix-the-merged-branch-is-observed-before-the-sweep' archived as
'2026-10-29-fix-the-merged-branch-is-observed-before-the-sweep'.
```

The archive moved `openspec/**` into the diff, so the profile's checks for those paths were run again as reading
commands (not a second gate), all exit 0 — `post-archive-checks.log`:

```
check-doc-routes: all 155 section references resolve, all 4 skill references resolve
specs read: 179 · classified: 182 · unclassified: 0 · delta spec ids: 0 · stale: 3 · car-only areas: 1 · highlights: 19
Every capability spec states its Purpose.
OpenSpec artifacts are consistent: 0 open change(s) carry task markers, …
check-todo-references: 62 citation(s) resolve through the recorded table
```

The three `stale` classification entries (`search-name-matching`, `search-result-row-icon`, `start-map-selection`)
are pre-existing: the gate's own `gen-feature-list.sh --check-classification` step printed the same three before
this change was archived (`gate.log:1078`).

`bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-fix-the-merged-branch-is-observed-before-the-sweep
--root .` → `evidence-check: PASS`, all five constraints (`C1` 1 §reference, `C2` 5 tally pairs confirmed, `C3`
no `/tmp` pointer, `C4` no mutation marker, `C5` 406 `file:line` resolved). The merged requirement now carries the
new scenario in `openspec/specs/agent-issue-loop/spec.md` (`:1648`).

`openspec validate agent-issue-loop --type spec` is valid (exit 0); under `--strict` it exits 1 on the spec's 15
pre-existing "Requirement text is very long (>500 characters)" warnings — another spec of this tree
(`navigation-engine`) reports the same under `--strict`, and the requirement this change modifies is not among
them (its 262 bytes — `sed -n '1637,1639p' openspec/specs/agent-issue-loop/spec.md | wc -c` — are carried
unchanged; only a scenario was added).
