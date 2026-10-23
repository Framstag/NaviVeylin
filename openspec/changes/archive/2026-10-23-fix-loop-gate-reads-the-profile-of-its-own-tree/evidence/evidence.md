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

`gate.log` — `./tools/loop/gate.sh --base 850dea2cbc64b7c6eba4061aa143bb82b2f43d44`, exit 0. Its first line names the
profile, and its last line is the gate's own record of what the four suites it ran reported — the line is quoted
here without its numbers, because the four numbers are the tallies' own and are cited one by one below:

```
LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/tools/loop/loop.conf — pinned; not the profile beside this engine (/home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf)
…
LOOP-GATE: <the sum of the four tallies below> (tools/loop-selftest.sh 717s, bash tools/loop-mutations.sh --check 0s, tools/loop-mutations-selftest.sh 71s, tools/check-doc-routes.sh 0s)
```

The four tallies that line sums are the ones the gate wrote, beside this file:
`tools_loop-selftest.sh.xml` (`tests="190" failures="0"`), `bash_tools_loop-mutations.sh_--check.xml`
(`tests="111" failures="0"`), `tools_loop-mutations-selftest.sh.xml` (`tests="34" failures="0"`) and
`tools_check-doc-routes.sh.xml` (`tests="0" failures="0"`) — 335 cases, none failing, none erroring, which is the
number the gate's own line carries in `gate.log`.

**Why the pin line says "pinned".** This change's own gate run inherited `LOOP_CONF` from the run that started this
worker, and that run's `run.sh` is HEAD's — the export this change removes. A child cannot change the environment its
parent already exported, so the first line of `gate.log` is the defect observed live, written by the fix that makes it
visible. The numbers still attest this tree, and the two facts that say so are measured: the profile the gate read is
byte-identical to the tree's own (`sha256sum` on both is
`91d3ebb4680e212a4460d0f7c2bf553514ba8aa33a5862105fe946b17ffb9679`), and the diff since the base touches no
`tools/loop/loop.conf` at all (`git diff --name-only 850dea2c -- tools/loop/loop.conf` prints nothing), so the rule
the two files name is the same rule; every suite ran from the change root.

The gate owes no compiled command: the delta touches no compiled file (`tools/loop/loop.conf`'s compiled patterns and
suite map are unchanged), which is the rule `agent-issue-loop` already states and this change does not alter.
