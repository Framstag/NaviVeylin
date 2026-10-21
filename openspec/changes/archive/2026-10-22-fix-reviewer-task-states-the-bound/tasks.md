# Tasks — fix-reviewer-task-states-the-bound

Parent spec: `openspec/changes/fix-reviewer-task-states-the-bound/specs/agent-issue-loop/spec.md`,
"A review's task states what the round may run, and quotes the gate instead of running it".

## 1. Red case — the task names the gate, the suite, the bound and the reading

- [x] 1.1 `tools/loop-cases.sh`: add `prompt#reviewerIsBoundedToReading`, register it in the case list and in
  the dispatch, and run it on HEAD: it fails because the rendered prompt carries none of the clauses.
  Verification: `bash tools/loop-selftest.sh --case 'prompt#reviewerIsBoundedToReading'` → `FAIL`, `EXIT=1`,
  `evidence/red-reviewer-bound.log` (run copy written 2026-10-22T00:07:18+02:00; the log carries the failing
  assertion and the branch marker `loop-20261021-220712`, no timestamp of its own).

## 2. Red case — a session-spawned reviewer is handed the same text

- [x] 2.1 `tools/loop-cases.sh`: add `prompt#iterationProtocolHandsTheReviewerTheSameSkeleton` and run it on
  HEAD: `guidelines/Build.md` §2 does not name the skeleton. Verification:
  `bash tools/loop-selftest.sh --case 'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton'` → `FAIL`,
  `EXIT=1`, `evidence/red-iteration-protocol.log` (run copy written 2026-10-22T00:07:20+02:00).

## 3. The reviewer's task states its inputs and its bound

- [x] 3.1 `tools/loop/prompts/reviewer.md`: new section "Your inputs, and what you may run" — the four inputs,
  the read-only list with the gate, the test suite and the network named, the gate quoted from the evidence,
  the wall-clock bound, the reading-task sentence. Verification: task 1.1's case goes green
  (`evidence/green-reviewer-bound.log`, `PASS`, `EXIT=0`).
- [x] 3.2 `tools/loop/prompts/reviewer.md`: narrow item 3 so it asks whether the evidence shows a real
  execution instead of inviting one — "Read the tally it carries; never reproduce it by running the gate."
  Verification: same case, which asserts that line (`never reproduce it by running the gate`) and
  `prompt#reviewerCarriesTheEightItems` still passes.

## 4. The bound is the number the run enforces

- [x] 4.1 `tools/loop/run.sh`: `render_reviewer_prompt` passes `REVIEW_WALL_CLOCK=$child_max`. Verification:
  task 1.1's case writes `LOOP_CHILD_MAX_SECONDS=8100` into its own `loop.conf` and asserts the rendered
  prompt carries `8100`, so a template that hardcoded the default fails the case and a value the run stops
  rendering fails it too.
- [x] 4.2 The engine still names no project fact — the number is read from the configuration, never written
  into the template. Verification: `grep -c 2700 tools/loop/prompts/reviewer.md` → 0, and the rendered prompt
  carries the value from `loop.conf`. *Excluded and named:* `tools/check-portability-selftest.sh` was not run —
  the gate selects it for `tools/check-portability*`, `tools/portability-*` and `PORTING.md`, and this change
  touches none of them.

## 5. The same text reaches a reviewer a session starts

- [x] 5.1 `guidelines/Build.md` §2: rule 5, "A reviewer is a reader, not a runner", naming
  `tools/loop/prompts/reviewer.md` and the bound it carries. Verification: task 2.1's case goes green
  (`evidence/green-iteration-protocol.log`, `PASS`, `EXIT=0`), and `tools/check-doc-routes.sh` resolves every
  section reference the edit keeps or adds (the gate ran it: `all 120 section references resolve`,
  `evidence/gate-doc-routes.xml`).

## 6. The invariant is falsifiable by a mutation

- [x] 6.1 `tools/loop-mutations.sh`: record `37.1` — delete "the gate" from the read-only list in
  `tools/loop/prompts/reviewer.md` — and `37.2` — restore item 3's "is the count a real execution" in place of
  its narrowed line. Verification: `bash tools/loop-mutations.sh --only '37.1 37.2'` → `RED 37.1`, `RED 37.2`
  on `prompt#reviewerIsBoundedToReading`, each restored green, `red-when-mutated: 2 · not-noticed: 0 ·
  restore-failures: 0` (`evidence/mutation-37.log`, run copy written 2026-10-22T00:27:52+02:00; the log
  carries the RED/restore lines and no clock of its own, so the write time is the timestamp quoted). The
  round-1 run of `37.1` alone is kept as `evidence/mutation-37.1.log`.

## 7. The gate, once

- [x] 7.1 `./tools/loop/gate.sh` — run once for the item (2026-10-21T22:07:57Z → 22:21:49Z):
  `LOOP-GATE: tests=193 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh,
  tools/sensor-selftest.sh, tools/check-doc-routes.sh)`, `EXIT=0` (`evidence/gate.log`, tallies
  `evidence/gate-*.xml`: 148 loop cases + 45 sensor cases + 0 from the build's own tally, which reports its
  count by exit status alone, and 120 resolved references). The build's tally was copied out of
  `app/build/test-results/loop-gate/` into `evidence/gate-gradle.xml`, because a copy that exists only in a
  build directory is not a versioned artefact. No compiled *source* file
  changed, so no build was owed (`guidelines/Build.md` §2, "no compiled file changed"); the build's own gate ran
  because the gate measures the diff from the run's base `80df3a3`, where one matched path is an archived
  `*.xml`. Not re-run.

## 8. Archive

- [x] 8.1 `openspec archive fix-reviewer-task-states-the-bound` — the delta merges into
  `openspec/specs/agent-issue-loop/spec.md` and the change directory moves under `openspec/changes/archive/`.
  Verification: the archived directory exists and the requirement is present in the capability spec.

## 9. Round 2 — the first review's findings

- [x] 9.1 The blocking finding: `prompt#reviewerIsBoundedToReading` did not read item 3's narrowed line, so
  the scenario's THEN clause was asserted by no case. The case now asserts `never reproduce it by running the
  gate`. Verification: case green (`evidence/round2-reviewer-bound.log`, 2026-10-22T00:27:02+02:00), and
  mutation `37.2` puts the old invitation back and turns it red (`evidence/mutation-37.log`).
- [x] 9.2 The wall-clock assertion read the shell's copy of `loop.conf` rather than proving the chain, so a
  template that hardcoded 2700 would have passed. The case now writes `LOOP_CHILD_MAX_SECONDS=8100` into its
  own configuration and asserts `8100` in the rendered text. Verification: same green case; with the value
  unrendered the assertion fails.
- [x] 9.3 The model-selection claim is gone: nothing in the tooling chooses a model by task kind, so the
  requirement and rule 5 now say a diff review is a reading task and nothing more. `reviewer.md`'s "no tool
  call is needed" (a file read *is* a tool call) and "nothing you report may depend on a command you ran"
  (a reading report does depend on reads) were corrected to "you obtain them by reading" and "a suite you
  ran". Verification: `prompt#reviewerCarriesTheEightItems` green
  (`evidence/round2-reviewerCarriesTheEightItems.log`), and the reading-task assertion still passes.
- [x] 9.4 The second scenario claimed the guideline states the wall-clock bound; `Build.md` names the text
  and repeats its prohibitions, which is what the case asserts. The scenario now says "repeats the
  prohibitions that text carries". Verification: `prompt#iterationProtocolHandsTheReviewerTheSameSkeleton`
  green (`evidence/round2-iterationProtocolHandsTheReviewerTheSameSkeleton.log`).
- [x] 9.5 Evidence pointers: the red and mutation logs carry no clock, so the timestamp quoted beside each is
  the write time of the run's copy, and the text now says so. The build's own tally was copied out of
  `app/build/test-results/loop-gate/` into `evidence/gate-gradle.xml`; it carries `tests="0"`, so the 193 is
  148 + 45 + 0.
- [x] 9.6 Verification of the correction itself: the three affected cases green on 2026-10-22 between
  00:27:02 and 00:27:14, mutations `37.1` and `37.2` red-and-restored at 00:27:52
  (`evidence/mutation-37.log`), and `tools/loop-selftest.sh` green over all of its cases
  (`evidence/round2-loop-selftest.log`). The project gate was **not** run again: its one run for this item is
  task 7.1, and a re-run to refresh it would replace that evidence rather than extend it
  (`guidelines/Build.md` §2; the round-2 prompt's "the gate is evidence, not a thing to retry"). No compiled
  file changed; `tools/check-doc-routes.sh` is the structural check for the guideline edit
  (`evidence/round2-doc-routes.log`).
