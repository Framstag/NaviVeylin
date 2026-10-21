# Tasks — fix-reviewer-task-states-the-bound

Parent spec: `openspec/changes/fix-reviewer-task-states-the-bound/specs/agent-issue-loop/spec.md`,
"A review's task states what the round may run, and quotes the gate instead of running it".

## 1. Red case — the task names the gate, the suite, the bound and the reading

- [x] 1.1 `tools/loop-cases.sh`: add `prompt#reviewerIsBoundedToReading`, register it in the case list and in
  the dispatch, and run it on HEAD: it fails because the rendered prompt carries none of the clauses.
  Verification: `bash tools/loop-selftest.sh --case 'prompt#reviewerIsBoundedToReading'` → `FAIL`, `EXIT=1`,
  `evidence/red-reviewer-bound.log` (2026-10-21T22:07:10Z).

## 2. Red case — a session-spawned reviewer is handed the same text

- [x] 2.1 `tools/loop-cases.sh`: add `prompt#iterationProtocolHandsTheReviewerTheSameSkeleton` and run it on
  HEAD: `guidelines/Build.md` §2 does not name the skeleton. Verification:
  `bash tools/loop-selftest.sh --case 'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton'` → `FAIL`,
  `EXIT=1`, `evidence/red-iteration-protocol.log` (2026-10-21T22:07:21Z).

## 3. The reviewer's task states its inputs and its bound

- [x] 3.1 `tools/loop/prompts/reviewer.md`: new section "Your inputs, and what you may run" — the four inputs,
  the read-only list with the gate, the test suite and the network named, the gate quoted from the evidence,
  the wall-clock bound, the reading-task sentence. Verification: task 1.1's case goes green
  (`evidence/green-reviewer-bound.log`, `PASS`, `EXIT=0`).
- [x] 3.2 `tools/loop/prompts/reviewer.md`: narrow item 3 so it asks whether the evidence shows a real
  execution instead of inviting one. Verification: same case (it asserts item 3's text), and
  `prompt#reviewerCarriesTheEightItems` still passes.

## 4. The bound is the number the run enforces

- [x] 4.1 `tools/loop/run.sh`: `render_reviewer_prompt` passes `REVIEW_WALL_CLOCK=$child_max`. Verification:
  task 1.1's case asserts the rendered prompt carries the configured `LOOP_CHILD_MAX_SECONDS` (2700), so a
  value that stops being rendered fails the case.
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
  `tools/loop/prompts/reviewer.md`. Verification: `bash tools/loop-mutations.sh --only 37.1` → `RED 37.1
  prompt#reviewerIsBoundedToReading`, `restore 37.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 ·
  restore-failures: 0` (`evidence/mutation-37.1.log`).

## 7. The gate, once

- [x] 7.1 `./tools/loop/gate.sh` — run once for the item (2026-10-21T22:07:57Z → 22:21:49Z):
  `LOOP-GATE: tests=193 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh,
  tools/sensor-selftest.sh, tools/check-doc-routes.sh)`, `EXIT=0` (`evidence/gate.log`, tallies
  `evidence/gate-*.xml`: 148 loop cases, 45 sensor cases, 120 resolved references). No compiled *source* file
  changed, so no build was owed (`guidelines/Build.md` §2, "no compiled file changed"); the build's own gate ran
  because the gate measures the diff from the run's base `80df3a3`, where one matched path is an archived
  `*.xml`. Not re-run.

## 8. Archive

- [x] 8.1 `openspec archive fix-reviewer-task-states-the-bound` — the delta merges into
  `openspec/specs/agent-issue-loop/spec.md` and the change directory moves under `openspec/changes/archive/`.
  Verification: the archived directory exists and the requirement is present in the capability spec.
