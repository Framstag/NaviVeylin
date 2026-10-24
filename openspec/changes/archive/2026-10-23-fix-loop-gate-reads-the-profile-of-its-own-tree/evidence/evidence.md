# Evidence — the gate reads the profile of the tree it measures (issue #277)

## The red cases, on HEAD

`red-on-head.log` and `red-on-head.xml` — `bash tools/loop-selftest.sh --case 'gate#the*Profile*'` at
2026-10-23T14:18:02Z, with only `tools/loop-cases.sh` changed, so the engine was the one
`850dea2cbc64b7c6eba4061aa143bb82b2f43d44` carries. `tests="2" failures="2"`, exit 1:

```
  the log names the profile the gate read: 'LOOP-GATE-PROFILE: …/pinned.conf' not found in: LOOP-GATE: nothing owed …
FAIL gate#theGateNamesTheProfileItRead
  the gate of the tree the child works in reads the profile beside that tree's engine:
  handed=/home/tim/projects/NaviVeylin/tools/loop/loop.conf … not found in: …
FAIL gate#theChildReadsTheProfileOfTheTreeItWorksIn
loop-selftest: 2 of 2 cases failed
```

## The cases on the tree this change leaves

`green-focused.log` / `green-focused.xml` — the same two cases, `tests="2" failures="0"`, and the focused suites
the delta touches: `green-gate-group.log` / `.xml` (`tests="11"`), `green-measure-group.log` / `.xml`
(`tests="4"`, the case that asserts the block is the whole output included), `green-state-group.log` / `.xml`
(`tests="14"`, the cases that pin a profile for a run).

## The run with no profile in its environment

`unpinned-probe.log` — the self-test's driver exports `LOOP_CONF` for every case, so the state an operator's shell
has is probed directly: `loop_resolve` then `export -n LOOP_CONF` under `set -uo pipefail` survives a variable that
was never set, and `LOOP_CONF_PATH` names the profile beside the engine of the tree the process is in.

## The falsification

`mutation-check.log` — `bash tools/loop-mutations.sh --check --files "tools/loop/gate.sh tools/loop/run.sh"`: all 56
selected records apply (the set is 111 now; `52.1`–`52.3` are this change's).

`revert-check-52.1.log`, `revert-check-52.2.log`, `revert-check-52.3.log` — each record mutated alone:
`red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`, the named case red under the mutation and green after
the restore.

## The gate

`gate.log` — `./tools/loop/gate.sh --base 850dea2cbc64b7c6eba4061aa143bb82b2f43d44`, exit 0, run on the tree this
change ships, its `openspec` artifacts included. The pathspec reaches `tools/loop/**` and `openspec/**`, so the
profile owes five checks: the three shell suites, `bash tools/gen-feature-list.sh --check-classification` and
`tools/check-doc-routes.sh`. Its first line names the profile, its last line sums the five tallies written beside
this file:

```
LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/tools/loop/loop.conf — pinned; not the profile beside this engine (/home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf)
…
LOOP-GATE: <the sum of the five tallies below> (tools/loop-selftest.sh 742s, bash tools/loop-mutations.sh --check 1s, tools/loop-mutations-selftest.sh 90s, bash tools/gen-feature-list.sh --check-classification 3s, tools/check-doc-routes.sh 0s)
```

The five tallies that line sums are the ones the gate wrote, beside this file: `tools_loop-selftest.sh.xml`
(`tests="190" failures="0"`), `bash_tools_loop-mutations.sh_--check.xml` (`tests="111" failures="0"`),
`tools_loop-mutations-selftest.sh.xml` (`tests="34" failures="0"`),
`bash_tools_gen-feature-list.sh_--check-classification.xml` (`tests="0" failures="0"`, its verdict on stdout in the
transcript) and `tools_check-doc-routes.sh.xml` (`tests="0" failures="0"`) — 335 cases, none failing, none erroring.

**Correction, round 2.** Round 1's gate ran before this change's `openspec` artifacts landed, so its plan named the
`tools/` and `guidelines/` paths only and the fifth check never entered its log; `tasks.md` 5.1 read as full
coverage of a delta it covered by half. This run is the same command over the final tree, and its last line names
the fifth check.

**Why the pin line says "pinned".** This change's own gate run inherited `LOOP_CONF` from the run that started this
worker, and that run's `run.sh` is HEAD's — the export this change removes. A child cannot change the environment its
parent already exported, so the first line of `gate.log` is the defect observed live, written by the fix that makes it
visible. The numbers still attest this tree, and the two facts that say so are measured:
`profile-digest.log` — `sha256sum tools/loop/loop.conf /home/tim/projects/NaviVeylin/tools/loop/loop.conf` is
`91d3ebb4680e212a4460d0f7c2bf553514ba8aa33a5862105fe946b17ffb9679` for both, `equal=yes` — and
`git diff --name-only 850dea2c -- tools/loop/loop.conf` prints nothing, so the rule the two files name is the same
rule; every suite ran from the change root.

The gate owes no compiled command: the delta touches no compiled file (`tools/loop/loop.conf`'s compiled patterns and
suite map are unchanged), which is the rule `agent-issue-loop` already states and this change does not alter.

## The pointers

`evidence-check.log` — the mechanical check, run **after** the archive against the path the change lives at now:
`bash .pi/skills/fix-loop/scripts/evidence-check.sh
openspec/changes/archive/2026-10-23-fix-loop-gate-reads-the-profile-of-its-own-tree --root .` — `C1` 0 references
checked (archived pointers exempted), `C2` every quoted tally confirmed with no drift, `C3` no `/tmp` pointer, `C4`
no mutation marker left in `app/src`, `C5` all 340 `file:line` pointers resolved and in range, `evidence-check:
PASS`, exit 0.

`archive.log` — the archive's own record: `Task status: ✓ Complete`, `agent-issue-loop: update`, `+ 1 added`,
`Specs updated successfully.`, and the change directory the archive created. It also carries the validator's one
warning — the proposal's `Why` section exceeds 1000 characters — left as written.
