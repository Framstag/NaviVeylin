# Tasks — fix-gate-suite-map-covers-the-pipeline

## 1. The two cases, red on HEAD

- [x] `tools/loop-cases.sh`: `gate#theMapCoversEveryPipelineStep` — the profile records `LOOP_GATE_PIPELINE`
      (absent fails; the key present and empty returns early, and that branch is read rather than driven — no
      second case asserts it) and every `bash`/`sh` command of the pipeline's `Check …` steps is a value of
      `LOOP_GATE_SUITES`; the failure message names each missing command.
      Verify on HEAD: red, exit 1, "the profile records no `LOOP_GATE_PIPELINE`, so the map is pinned to nothing
      and nobody can tell" (`evidence/red-on-HEAD.log`, `2026-10-22T04:41:39Z`; a shell suite, so the per-case
      line and the exit status are the evidence — there is no XML).
- [x] `tools/loop-cases.sh`: `gate#aPipelineCheckIsOwedByItsPaths` — the plan for a delta touching
      `tools/build-inputs.sh` names all three commands the `Check the build inputs` step runs
      (`tools/build-inputs-selftest.sh`, `tools/build-inputs.sh --check`,
      `tools/build-inputs-mutations.sh --list`) and names no command of another step
      (`tools/check-portability.sh` absent).
      Verify on HEAD: red, exit 1, the plan answered `LOOP-GATE: nothing owed` (`evidence/red-on-HEAD.log`).

## 2. The profile's map names every script command the pipeline checks

- [x] `tools/loop/loop.conf`: `LOOP_GATE_PIPELINE` recorded, and `LOOP_GATE_SUITES` completed to 55 pairs naming
      all 31 commands — a path mapped to the whole command set of the step whose tools read it.
      Verify: `bash tools/loop-selftest.sh --case gate#theMapCoversEveryPipelineStep` and
      `--case gate#aPipelineCheckIsOwedByItsPaths` green (`evidence/green-cases.log`), and the map compared
      against the pipeline with the parse the case uses (`evidence/map-vs-pipeline.txt`: 31 commands, 0 missing).

## 3. The falsification: one mutation per case

- [x] `tools/loop-mutations.sh`: record 54.1 removes `tools/build-inputs*|tools/build-inputs-selftest.sh;` from
      the map; record 54.2 removes `tools/build-inputs*|tools/build-inputs.sh --check;`; record 54.3 removes the
      mutation-list pair; record 54.4 adds a pair that reaches `tools/check-portability.sh` through a coarse
      glob. One record per assertion, so each assertion of the case is driven by a mutation of the production
      invariant it guards.
      Verify: `bash tools/loop-mutations.sh --only "54.1 54.2 54.3 54.4"` — all four RED on their named case, all
      green after the restore, the tree byte-identical (`evidence/round2-mutations.log`, `04:59:45Z`; round 1's
      two-record `evidence/mutations.log`, `04:43:04Z`, is kept as history); and the two round-2 records fail
      for their own assertion, not merely somewhere in the case (`evidence/round2-probe-assertions.log`).

## 4. The guideline states the bound

- [x] `guidelines/Build.md` §3, the gate row: the shell suite is "the step whose tools read the touched paths",
      every `bash`/`sh` command of the pipeline's `Check …` steps is reachable from a path in the profile's map,
      the map is pinned by `gate#theMapCoversEveryPipelineStep`, and the steps that run their check as inline
      shell or as a Gradle task are named as what the pin cannot see.
      Verify: no compiled file changed, so the gate is the loop's suite; `bash tools/check-doc-routes.sh` resolves
      the section reference (it runs inside the gate).

## 5. The gate, once

- [x] `./tools/loop/gate.sh` from the change root, once, quoted with its tally: `LOOP-GATE: tests=157
      failures=0` over the eight commands the gate ran, with one tally XML per suite
      (`evidence/gate-round3.log`, `evidence/tools_loop-selftest.sh.xml` and the seven beside it in that
      directory; round 2's `evidence/gate.log` ran the same eight and is kept as history). The invocation and
      the `LOOP_CONF` it ran under are recorded in `evidence.md`, because the run exports the project tree's
      older profile.

## 6. Review round 1 — the width the case promised, and an artifact that was not after the last edit

- [x] The behaviour case asserts what its scenario promises: the step's third command
      (`tools/build-inputs-mutations.sh --list`) and the negative (`tools/check-portability.sh` absent), with
      mutation records 54.3 and 54.4 making each of the two new assertions fail on its own
      (`evidence/green-cases.log`, `evidence/round2-probe-assertions.log`).
- [x] `evidence/after-final-edit.log` re-taken over the **post-archive** tree, because the first copy predated
      the archive merge and the `openspec/**` writes that followed it. Re-taken a second time in round 3, and
      this time over all **five** commands the change's own map owes a `.md` under `openspec/`: of the five,
      round 2 ran three and left `tools/check-doc-routes-selftest.sh` and
      `tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list` unrun (review round 2, item 6).
      The other two the map owes are `tools/check-todo-references.sh` (the `*.md` pair) and
      `tools/gen-feature-list.sh --check-classification`.
- [x] The two free corrections: the mutation `--list` count is eleven (eight `tools/mutate.sh … --list` lines and
      three tool-specific ones), and `ci-gate-steps.txt`'s 27 is marked superseded by `map-vs-pipeline.txt`'s 31.

## 7. Review round 2 — the pin's subject narrowed to what it reads, and the artifact over five checks

- [x] The requirement's SHALL, its heading and the two sentences that repeat it name what the pin can see: every
      `bash`/`sh` command of a `Check …` step. The residual is stated in one line rather than claimed away — a
      step whose check is inline shell or a Gradle task is outside the pin, and `Check daemon JVM criteria`
      (over `gradle/gradle-daemon-jvm.properties`, which no compiled pattern matches) is green in the run today.
      Edited in `openspec/specs/agent-issue-loop/spec.md`, this change's delta, `guidelines/Build.md` §3,
      `tools/loop/loop.conf`'s comment and `tools/loop-cases.sh`'s comment (comments only, no behaviour).
      Verify: `bash tools/loop-selftest.sh --case gate#theMapCoversEveryPipelineStep` and
      `--case gate#aPipelineCheckIsOwedByItsPaths` green, `bash tools/loop-mutations-selftest.sh` green, and the
      gate's eight commands green (`evidence/gate-round3.log`).
- [x] `evidence/after-final-edit.log` re-taken over all **five** commands the change's own map owes a `.md` under
      `openspec/`, after the last write of this round, with each check's elapsed time quoted from the run — round
      2's copy carried three and called them the set the map owes.
      Verify: the log names five commands, each with `exit=0`.
- [x] The three free corrections: task 3 and task 6 name the artifacts that exist
      (`evidence/round2-mutations.log`, `evidence/round2-probe-assertions.log`), task 5's "six commands / the five
      beside it" is the eight the gate ran, and the unbacked numbers are gone — the millisecond timings in
      `proposal.md`, `design.md` and `evidence.md` are replaced by the timings the log records, and the
      `paths=279` figure with the reason alone.
- [x] The profile's `openspec/` comment no longer reads as if an open change's own delta were gated: the delta a
      change writes is gitignored (`.gitignore:75`) and a gate delta is `git diff` plus `git status`, so only the
      archive move is in it.
- [x] The gate, once, from the change root, with the profile's own `LOOP_CONF`:
      `evidence/gate-round3.log`, eight commands, `LOOP-GATE: tests=157 failures=0`, `exit=0`. A bare invocation
      ran first, owed four commands under the project tree's pre-change profile, and is kept as
      `evidence/invalid-3a-bare-invocation-old-profile.log` rather than quoted.

## 8. Review round 3 — the claim narrowed to the command side, and the base refreshed

Round 3 refused the change: the map covers the pipeline's commands in one direction, but the change claimed
"every pipeline-gated path maps to every step the pipeline runs for it", and three paths a `Check portability`
step reads (`tools/loop/loop.conf`, `tools/tooling.json`, `tools/sensors/sensors.json`) map to no command of
that step. The owner's recorded answer was **option 1**: narrow the claim, state the path side as a residual,
and file the gap as its own item.

- [x] The claim is narrowed in `guidelines/Build.md` §3, the spec's requirement and scenario, `proposal.md`,
      `design.md` and this file: the pin closes the **command** side — every `bash`/`sh` command a `Check …`
      step runs is a value of the map — and the path side is a named residual (`tools/check-portability.sh`
      reads `tools/loop/loop.conf`'s `LOOP_GATE_CMD` value, `tools/tooling.json` and
      `tools/sensors/sensors.json`, none of which is mapped to a `Check portability` command). The residual is
      filed as **#289**.
- [x] The change was rebased onto the base's later work (#273's compiled-pattern refinement and #276's
      `--from-git {base}` narrowing): the map keeps main's refined pairs and adds the missing commands, and the
      pin strips the gate's `--from-git {base}` suffix before comparing a map command with the pipeline's, so
      the narrowing is not read as part of the command itself.
- [x] The three free corrections of round 3's report: `evidence.md`'s surface grep count, task 5's "the seven
      beside it" (six), and `gate-round3.log`'s missing exit marker, each replaced with what the artifact
      records; `invalid-3a-bare-invocation-old-profile.log` stays as the bare invocation's record.
