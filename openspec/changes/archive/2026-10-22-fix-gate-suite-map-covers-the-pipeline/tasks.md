# Tasks — fix-gate-suite-map-covers-the-pipeline

## 1. The two cases, red on HEAD

- [x] `tools/loop-cases.sh`: `gate#theMapCoversEveryPipelineStep` — the profile records `LOOP_GATE_PIPELINE`
      (absent fails, empty passes) and every command of the pipeline's `Check …` steps is a value of
      `LOOP_GATE_SUITES`; the failure message names each missing command.
      Verify on HEAD: red, exit 1, "the profile records no `LOOP_GATE_PIPELINE`, so the map is pinned to nothing
      and nobody can tell" (`evidence/red-on-HEAD.log`, `2026-10-22T04:41:39Z`; a shell suite, so the per-case
      line and the exit status are the evidence — there is no XML).
- [x] `tools/loop-cases.sh`: `gate#aPipelineCheckIsOwedByItsPaths` — the plan for a delta touching
      `tools/build-inputs.sh` names `tools/build-inputs.sh --check` and
      `tools/build-inputs-selftest.sh`.
      Verify on HEAD: red, exit 1, the plan answered `LOOP-GATE: nothing owed` (`evidence/red-on-HEAD.log`).

## 2. The profile's map names every command the pipeline checks

- [x] `tools/loop/loop.conf`: `LOOP_GATE_PIPELINE` recorded, and `LOOP_GATE_SUITES` completed to 55 pairs naming
      all 31 commands — a path mapped to the whole command set of the step whose tools read it.
      Verify: `bash tools/loop-selftest.sh --case gate#theMapCoversEveryPipelineStep` and
      `--case gate#aPipelineCheckIsOwedByItsPaths` green (`evidence/green-cases.log`), and the map compared
      against the pipeline with the parse the case uses (`evidence/map-vs-pipeline.txt`: 31 commands, 0 missing).

## 3. The falsification: one mutation per case

- [x] `tools/loop-mutations.sh`: record 40.1 removes `tools/build-inputs*|tools/build-inputs-selftest.sh;` from
      the map; record 40.2 removes `tools/build-inputs*|tools/build-inputs.sh --check;`.
      Verify: `bash tools/loop-mutations.sh --only 40.1 40.2` — both RED on their named case, both green after
      the restore, the tree byte-identical (`evidence/mutations.log`).

## 4. The guideline states the bound

- [x] `guidelines/Build.md` §3, the gate row: the shell suite is "the step whose tools read the touched paths",
      every command of the pipeline's `Check …` steps is reachable from a path in the profile's map, and the map
      is pinned by `gate#theMapCoversEveryPipelineStep`.
      Verify: no compiled file changed, so the gate is the loop's suite; `bash tools/check-doc-routes.sh` resolves
      the section reference (it runs inside the gate).

## 5. The gate, once

- [x] `./tools/loop/gate.sh` from the change root, once, quoted with its tally: `LOOP-GATE: tests=157
      failures=0` over six commands, with one tally XML per suite (`evidence/gate.log`,
      `evidence/tools_loop-selftest.sh.xml` and the five beside it). The invocation and the `LOOP_CONF` it ran
      under are recorded in `evidence.md`, because the run exports the project tree's older profile.
