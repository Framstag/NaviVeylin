# Evidence — fix-gate-suite-map-covers-the-pipeline (issue #242)

Base: `ee95d7160cb32c65b56979058338004f29db3edc`. All times UTC, 2026-10-22. Change root:
`.pi/worktrees/loop`.

Two rounds are recorded here: the change as it was archived (round 1, times 04:41–04:54) and the
corrections the review asked for (round 2, from 04:59). Round 1's artifacts are kept beside round 2's
rather than overwritten — `gate-round1.log` is the gate that ran before the archive merge, `gate.log` the
one that ran over the corrected tree.

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
`tools/mutate-selftest.sh`, and **all eleven** mutation `--list` lines: the eight
`tools/mutate.sh --mutations <set> --list` lines (`build.yml:176,190,194,198,202,207,283,293`) and the three
tool-specific ones (`tools/issues/metadata-mutations.sh --list` at :219, `tools/sensor-mutations.sh --list` at
:252, `tools/build-inputs-mutations.sh --list` at :265). After the change the map is 55 pairs naming all 31,
and the same comparison leaves nothing.

`ci-gate-steps.txt` (`phase-a-verification.md`'s 27 commands) is **superseded** by that comparison: it is a
partial listing that omits `tools/gen-feature-list-selftest.sh` (:159), `tools/check-todo-references.sh` (:239)
and `tools/check-action-runtimes.sh` with its self-test (:304–305), and its line numbers are a commit behind.
Only `map-vs-pipeline.txt`'s 31 is quoted from here on.

## Green

`evidence/green-cases.log` (`04:42:51Z`, round 1) — both cases pass, exit 0; the map against the pipeline: 31
commands named, 0 missing.

`evidence/round2-green-cases.log` (`04:59:42Z`, round 2) — `gate#aPipelineCheckIsOwedByItsPaths` passes with the
two assertions the review added (the step's mutation `--list` command, and no command of another step), exit 0.

## Falsification — one mutation per assertion

`evidence/mutations.log` (`04:43:04Z`, round 1) — `bash tools/loop-mutations.sh --only "40.1 40.2"`:

    RED    40.1  gate#theMapCoversEveryPipelineStep
    restore 40.1 gate#theMapCoversEveryPipelineStep green again
    RED    40.2  gate#aPipelineCheckIsOwedByItsPaths
    restore 40.2 gate#aPipelineCheckIsOwedByItsPaths green again

    red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0

`evidence/round2-mutations.log` (`04:59:45Z`, round 2) — `bash tools/loop-mutations.sh --only "40.1 40.2 40.3
40.4"`: all four RED on their named case, each green after its restore, `red-when-mutated: 4 · not-noticed: 0 ·
restore-failures: 0`, exit 0. The harness prints a record's description, not the failure message its case
raised, so the reason is shown separately:

`evidence/round2-probe-assertions.log` (`05:00:19Z`) — the two round-2 records applied by hand to a copy of the
engine (`sed -e <the record's expression>` on the copied `loop.conf`, `LOOP_ENGINE` pointed at the copy), so
each failure message is quoted:

    $ mutation 40.3  (the mutation-list pair removed)
      and the mutation set the same step runs: tools/build-inputs-selftest.sh
    tools/build-inputs.sh --check: 'tools/build-inputs-mutations.sh --list' not found in: …
    FAIL gate#aPipelineCheckIsOwedByItsPaths      exit=1

    $ mutation 40.4  (a coarse pair reaching the portability check added)
      and names no command of another step: tools/build-inputs-selftest.sh
    tools/build-inputs.sh --check
    tools/check-portability.sh
    tools/build-inputs-mutations.sh --list: 'tools/check-portability.sh' should not appear in: …
    FAIL gate#aPipelineCheckIsOwedByItsPaths      exit=1

Each new assertion therefore fails for itself: 40.3 reddens the third positive assertion while the two older
ones still pass, and 40.4 reddens only the negative one. The restore is byte-identical by construction:
`restore_file` compares with `cmp -s` and exits 2 on a mismatch (`tools/loop-mutations.sh:262`).

## The gate

`evidence/gate-round1.log` (`04:43:46Z`–`04:54:00Z`, round 1) — five reading checks plus the loop's suite:
`LOOP-GATE: tests=157 failures=0`. It ran **before** the change's evidence was copied into the archive and
before the archive merge, which is the defect the review found; it is kept as history, not quoted as the
change's gate.

`evidence/gate.log` (`05:00:55Z`–`05:16:18Z`, round 2, over the corrected tree):

    gate: ./gradlew test -PforceTests --no-build-cache
    gate: tools/gen-feature-list.sh --check-classification
    gate: tools/check-doc-routes.sh
    gate: tools/check-doc-routes-selftest.sh
    gate: tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
    gate: tools/check-todo-references.sh
    gate: tools/loop-selftest.sh
    gate: tools/loop-mutations-selftest.sh
    LOOP-GATE: tests=157 failures=0 (…)
    exit=0

The compiled gate ran because this change's own evidence directory carries `.xml` tally files and
`LOOP_GATE_COMPILED_PATTERNS` includes `*.xml` — a change that ships JUnit evidence owes the build's gate by
the profile's own rule. It exited 0; its result contributes no parsed count to the tally (the Gradle task does
not print the `<name>: all N cases passed` line the parser reads), so `tests=157` is the loop's suite alone.
The reading checks are owed because the change touches `tools/loop/**` and `openspec/**`.

Both invocations are `./tools/loop/gate.sh --base ee95d7160cb32c65b56979058338004f29db3edc` with `LOOP_CONF`
pointing at **this tree's** `tools/loop/loop.conf`. The run exports `LOOP_CONF` from the project tree, whose
copy predates this change, so the bare invocation would have measured the paths between `origin/main`
(`80df3a3`) and this tree — the run's previous landings, Kotlin included — and would have run the Gradle gate
for a change that touches no compiled file, while never exercising the map this change writes. `measure.sh`
over that base prints `LOOP-MEASURE base=80df3a3… paths=279` for the post-change tree (it was 262 while the
change's own files were still untracked), which is why the `--base` is passed explicitly.

Per-suite tallies: `tools_loop-selftest.sh.xml` (tests=157), plus one XML per other suite, all failures=0. The
suites that print no "all N cases passed" line carry `tests=0` in their tally, which is the parser's reading of
their summary and not a claim that they ran nothing: `check-doc-routes.sh` prints "all 120 section references
resolve", `check-todo-references.sh` prints its citation count, and `mutate.sh --list` prints the record list.

## After the change's last edit

The first copy of this artifact (`04:54:36Z`) was labelled "after the final edit" and was not: `evidence.md`,
`tasks.md` and the archive merge into `openspec/specs/agent-issue-loop/spec.md` followed it, and those
`openspec/**` paths are exactly what the change's own map owes the three reading checks for. The review was
right to refuse it.

`evidence/after-final-edit.log` is re-taken after the last write of this round, over the post-archive tree:

    $ bash tools/check-doc-routes.sh
    check-doc-routes: all 120 section references resolve, all 5 skill references resolve
    $ bash tools/check-todo-references.sh
    check-todo-references: 198 line(s) scanned, 63 distinct citation(s), 162 in the recorded table
    check-todo-references: 63 citation(s) resolve through the recorded table; …
    $ bash tools/gen-feature-list.sh --check-classification
    specs read: 177
    classified: 177
    unclassified: 0
    delta spec ids: 1

Each exits 0. Measured wall time on this tree with `date +%s%N` around each: `check-doc-routes.sh` 141 ms,
`check-todo-references.sh` 482 ms, `gen-feature-list.sh --check-classification` 2437 ms.
