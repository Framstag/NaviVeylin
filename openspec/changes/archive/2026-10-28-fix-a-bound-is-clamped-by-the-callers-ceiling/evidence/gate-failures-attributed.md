# The two failures the gate found, and which of them is this change's

The gate ran once, on this change (`LOOP-CALL-CEILING` named at 1 500 s so the gate would end itself rather than be
killed):

```
LOOP-GATE: tests=665 failures=2 errors=0 (tools/loop-selftest.sh --from-git c2969186… 691s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 462s, … tools/issue-tooling-selftest.sh 71s, …)
LOOP-MEASURE-GATE seconds=1256 suites=17
```

`loop-selftest: 2 of 276 cases failed`.

## 1. `driver#everyCaseDeclaresTheFilesItCanReach` — this change's, and repaired

```
a case does not declare what it can be reached by: gate#theStepBoundIsClampedByTheCeilingTheCallerNamed:tools/pi-child-events.sh(new_project) gate#theStepBoundIsClampedByTheCeilingTheCallerNamed:tools/tooling.json(new_project)
```

The new gate case builds its project with `new_project`, which reaches `tools/pi-child-events.sh` and
`tools/tooling.json`, and its `# files:` line named neither. The declaration now carries both, the shape
`gate#theSuitesOutputReachesTheConsoleWhileItRuns` already uses.

Verified: `bash tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` → `PASS`
(`postfix-driver_everyCaseDeclaresTheFilesItCanReach.log`), and
`bash tools/loop-selftest.sh --case gate#theStepBoundIsClampedByTheCeilingTheCallerNamed` → `PASS`
(`postfix-gate_theStepBoundIsClampedByTheCeilingTheCallerNamed.log`).

## 2. `gate#theMapCoversEveryPipelineStep` — red on the base, not this change's

```
the suite map carries none of these commands the pipeline's Check steps run: tools/check-dex-gate-graph-selftest.sh
```

Both sides of the comparison on the base commit `c2969186`, read with `git show`:

- `.github/workflows/build.yml:394` — `run: bash tools/check-dex-gate-graph-selftest.sh`, i.e. a pipeline `Check …`
  step that runs this command;
- `git show c2969186:tools/loop/loop.conf | grep -c 'check-dex-gate-graph'` → `0`, i.e. the base's suite map carries
  no value for that command, which is exactly what the case refuses.

This change edits neither file's map, its pipeline, nor that script: `git status --short .github/workflows/build.yml
tools/loop/loop.conf` names only `tools/loop/loop.conf`, and the one hunk it adds there is the new
`LOOP_CALL_CEILING_SECONDS` key and its comment — `LOOP_GATE_SUITES` is untouched. The failure is the base's, and it
is left alone here: repairing it is a different delta (a map entry for a pipeline step), and a change that touched it
would be two mechanisms under one item (issue #692's gate 2).

Repair it separately: add `tools/check-dex-gate-graph*|tools/check-dex-gate-graph-selftest.sh` and whatever commands
that step runs to `LOOP_GATE_SUITES` in `tools/loop/loop.conf`.
