# Evidence — fix-gate-suite-map-covers-the-pipeline (issue #242)

Base: `ee95d7160cb32c65b56979058338004f29db3edc` for rounds 1–3; the round-4 artifacts are taken on the
merged base `a06ef5ca`. All times UTC. Change root: `.pi/worktrees/loop` for rounds 1–3, `.pi/worktrees/242`
for round 4.

Three rounds are recorded here: the change as it was archived (round 1, times 04:41–04:54), the corrections
review round 1 asked for (round 2, from 04:59), and review round 2's two blocking corrections (round 3, from
05:21). Round 1's artifacts are kept beside round 2's rather than overwritten — `gate-round1.log` is the gate
that ran before the archive merge, `gate.log` the one that ran over the round-2 tree, and `gate-round3.log` the
one the change is judged by.

## The red cases, on HEAD, before the profile was changed

`evidence/red-on-HEAD.log` (`04:41:39Z`) — `bash tools/loop-selftest.sh --case <name>`, both exit 1:

    the profile records no LOOP_GATE_PIPELINE, so the map is pinned to nothing and nobody can tell
    FAIL gate#theMapCoversEveryPipelineStep

    the check the pipeline runs over that path is owed: LOOP-GATE: nothing owed — no path this profile gates changed
    FAIL gate#aPipelineCheckIsOwedByItsPaths

The suite is a shell gate: its evidence is the per-case line and the exit status, and there is no JUnit XML to
quote. (The gate's own tally for the suite is `evidence/tools_loop-selftest.sh.xml`.)

## What the map was missing

`evidence/map-vs-pipeline.txt` — the map named **8** of the pipeline's **31** shell check commands (the same
comparison on this change's refreshed base names **10**):
`comm -13` of the two lists leaves **23** missing on `ee95d71` (**21** on the refreshed base), among them `tools/build-inputs.sh --check`,
`tools/check-todo-references.sh`, `tools/resolve-stale-selftest.sh`, `tools/check-compose-sandbox-rule.sh`,
`tools/mutate-selftest.sh`, and **all eleven** mutation `--list` lines: the eight
`tools/mutate.sh --mutations <set> --list` lines (`build.yml:176,190,194,198,202,207,283,293`) and the three
tool-specific ones (`tools/issues/metadata-mutations.sh --list` at :219, `tools/sensor-mutations.sh --list` at
:252, `tools/build-inputs-mutations.sh --list` at :265). After the change the map is 58 pairs naming all 31,
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
for a change that touches no compiled file, while never exercising the map this change writes, which is why
the `--base` is passed explicitly.

Per-suite tallies: `tools_loop-selftest.sh.xml` (tests=157), plus one XML per other suite, all failures=0. The
suites that print no "all N cases passed" line carry `tests=0` in their tally, which is the parser's reading of
their summary and not a claim that they ran nothing: `check-doc-routes.sh` prints "all 120 section references
resolve", `check-todo-references.sh` prints its citation count, and `mutate.sh --list` prints the record list.

## After the change's last edit — round 3, over all five commands the map owes

The first copy of this artifact (`04:54:36Z`) was labelled "after the final edit" and was not: `evidence.md`,
`tasks.md` and the archive merge into `openspec/specs/agent-issue-loop/spec.md` followed it. The round-2 copy
(`05:17:17Z`) fixed the timestamp but not the set: the header said "the three checks the change's own map owes a
path under `openspec/`", while that map owes **five** — `tools/gen-feature-list.sh --check-classification`,
`tools/check-doc-routes.sh`, `tools/check-doc-routes-selftest.sh`,
`tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list` (the `openspec/*` pairs) and
`tools/check-todo-references.sh` (the `*.md` pair). Two of the five were therefore unrun over the post-archive
tree, and review round 2 refused the artifact for it.

`evidence/after-final-edit.log` is re-taken in round 3 after the last write of this round, over the post-archive
tree, and every one of the five commands is in it: each prints its summary, exits 0, and carries its own
elapsed time (`date +%s%N` before and after) — the two doc checks and the citation scan under a second each,
the classification pass about two and a half — so the cost is quoted from the artifact rather than asserted in
prose.

## The gate, round 3

`evidence/gate-round3.log` (`05:35:48Z`–`05:50:33Z`) — eight commands, `LOOP-GATE: tests=157 failures=0`,
`exit=0`, over the corrected tree and under **this tree's** profile:

    gate: ./gradlew test -PforceTests --no-build-cache
    gate: tools/gen-feature-list.sh --check-classification
    gate: tools/check-doc-routes.sh
    gate: tools/check-doc-routes-selftest.sh
    gate: tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
    gate: tools/check-todo-references.sh
    gate: tools/loop-selftest.sh
    gate: tools/loop-mutations-selftest.sh
    LOOP-GATE: tests=157 failures=0 (…)

The invocation was `LOOP_CONF=<this tree>/tools/loop/loop.conf ./tools/loop/gate.sh --base ee95d71…`, the same
shape round 2 used and for the same reason. The trap the paragraph above describes was caught again in this
round rather than quoted: a bare `./tools/loop/gate.sh` first owed only **four** commands — the run exports
`LOOP_CONF` from the project tree, whose profile predates this change — so that invocation measured the old
map and is kept as `evidence/invalid-3a-bare-invocation-old-profile.log`, not as the change's gate.

## Review round 3 refused, and the round-4 corrections

The reviewer's round-3 finding is kept whole as `evidence/review-3-refused.md` (moved off the
`review-failed-round-3.md` name so a later run's adoption check does not read the change as refused after the
owner answered the finding). The owner's answer was **option 1**: narrow the *claim* to the command side and
file the path-side gap as **#289**. The round-4 artifacts, all on the merged base `a06ef5ca`:

- `evidence/round4-green-cases.log` (`06:12:56Z`) — both cases pass, exit 0.
- `evidence/round4-mutations.log` (`06:11Z`) — the four records renumbered **54.x** (40.x is
  `run#aCorrectiveRoundIsHandedTheCorrections`) and re-run: all four RED on their named case, each green after
  its restore, `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`, exit 0.
- `evidence/round4-map-vs-pipeline.txt` — the map against the pipeline on the merged base: the map is 58 pairs,
  the `comm -13` leaves nothing, and the pin's strip of the gate's own `--from-git {base}` is what makes the
  loop pairs comparable with the pipeline's commands.
- `evidence/gate-round4.log` — the gate, once, from the change root, `--base a06ef5ca`: eight commands,
  `LOOP-GATE: tests=375 failures=1`, exit 1. The one failure is `run#aWriterTheRunCanPlaceBlocksTheItem`,
  which is **not this change's**: it fails identically on the base (`evidence/round4-baseline-failure.log`,
  the case run on a clean checkout of `a06ef5ca`), and it is already filed as #287/#288. Every other case the
  delta owes is green, the two gate-map cases among them (`evidence/round4-green-cases.log`), and the four
  mutation records are RED-then-restored (`evidence/round4-mutations.log`).

The rounds-1–3 logs keep their original record ids (40.x); the renumbering to 54.x is what a reader of
`tools/loop-mutations.sh` sees, and the round-2 logs are history rather than the current set.

## The portability gate, run by hand because the map does not route this change's own file to it

This change edits `tools/loop/loop.conf`, which is exactly the path the map does not route to `Check
portability` (the residual #289 names), so the gate did not run that step and it was run by hand:

- `bash tools/check-portability.sh` — `OK — 47 engines and 2 templates name no project value`, exit 0.
- `bash tools/check-portability-selftest.sh` — `1 of 27 cases failed`: `foreign#fixtureDeclaresEveryBudgetDefault`
  (`LOOP_STRICT_OUTSIDE_WRITE_DEFAULT` missing from `tools/fixtures/foreign-project/tools/loop/loop.conf`).
  That failure is **not this change's**: the same command on a clean checkout of `main` (`9e77cd69`) fails the
  same case, and it is filed as #290. It is named here because a reader of `main` sees it at `Check portability`
  and could otherwise take it for this change's.
