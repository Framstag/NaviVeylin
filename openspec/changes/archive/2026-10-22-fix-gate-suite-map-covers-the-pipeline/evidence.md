# Evidence — fix-gate-suite-map-covers-the-pipeline (issue #242)

Base: `ee95d7160cb32c65b56979058338004f29db3edc`. All times UTC, 2026-10-22. Change root:
`.pi/worktrees/loop`.

## The red cases, on HEAD, before the profile was changed

`evidence/red-on-HEAD.log` (`04:41:39Z`) — `bash tools/loop-selftest.sh --case <name>`, both exit 1:

    the profile records no LOOP_GATE_PIPELINE, so the map is pinned to nothing and nobody can tell
    FAIL gate#theMapCoversEveryPipelineStep

    the check the pipeline runs over that path is owed: LOOP-GATE: nothing owed — no path this profile gates changed
    FAIL gate#aPipelineCheckIsOwedByItsPaths

The suite is a shell gate: its evidence is the per-case line and the exit status, and there is no JUnit XML to
quote. (The gate's own tally for the suite is `evidence/tools_loop-selftest.sh.xml`.)

## What the map was missing

`evidence/map-vs-pipeline.txt` — the map named **8** of the pipeline's **31** shell check commands:
`comm -13` of the two lists leaves **23** missing, among them `tools/build-inputs.sh --check`,
`tools/check-todo-references.sh`, `tools/resolve-stale-selftest.sh`, `tools/check-compose-sandbox-rule.sh`,
`tools/mutate-selftest.sh` and eight of the ten mutation `--list` lines. After the change the map is 55 pairs
naming all 31, and the same comparison leaves nothing.

## Green

`evidence/green-cases.log` (`04:42:51Z`) — both cases pass, exit 0; the map against the pipeline: 31 commands
named, 0 missing.

## Falsification — one mutation per case

`evidence/mutations.log` (`04:43:04Z`) — `bash tools/loop-mutations.sh --only "40.1 40.2"`:

    RED    40.1  gate#theMapCoversEveryPipelineStep
    restore 40.1 gate#theMapCoversEveryPipelineStep green again
    RED    40.2  gate#aPipelineCheckIsOwedByItsPaths
    restore 40.2 gate#aPipelineCheckIsOwedByItsPaths green again

    red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0

## The gate, once

`evidence/gate.log` (`04:43:46Z`–`04:54:00Z`):

    gate: tools/check-doc-routes.sh
    gate: tools/check-doc-routes-selftest.sh
    gate: tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
    gate: tools/check-todo-references.sh
    gate: tools/loop-selftest.sh
    gate: tools/loop-mutations-selftest.sh
    LOOP-GATE: tests=157 failures=0
    exit=0

The gate was run as `./tools/loop/gate.sh --base ee95d7160cb32c65b56979058338004f29db3edc` with `LOOP_CONF`
pointing at **this tree's** `tools/loop/loop.conf`. The run exports `LOOP_CONF` from the project tree, whose
copy is older than this change, so the bare invocation would have measured the 262 paths between
`origin/main` (`80df3a3`) and this tree — the run's previous landings, Kotlin included — and would have run the
Gradle gate for a change that touches no compiled file, while never exercising the map this change writes.
Both halves of that are recorded here rather than left implicit.

Per-suite tallies: `tools_loop-selftest.sh.xml` (tests=157), plus one XML per other suite, all failures=0.
The suites that print no "all N cases passed" line carry `tests=0` in their tally, which is the parser's reading
of their summary and not a claim that they ran nothing: `check-doc-routes.sh` prints "all 120 section references
resolve", `check-todo-references.sh` prints its citation count, and `mutate.sh --list` prints the record list.

## After the change's last edit

`evidence/after-final-edit.log` (`04:54:36Z`) — the two reading checks re-run over the final tree:
`check-doc-routes: all 120 section references resolve`, `check-todo-references: 63 citation(s) resolve`,
`gen-feature-list --check-classification: classified 177, unclassified 0`.
