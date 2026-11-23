# Tasks: a-repeat-read-is-refused-after-its-count-bound

## 1. The rule, before the fix's code

- [x] 1.1 Write `adapter#aPathIsRefusedAfterItsRepeatBound` in `tools/loop/tests/loop-cases.sh`, beside the rule's
  own cases (`:22166`): it reads the bound from `loop.conf`, asserts the adapter exports it, drives a harness hook
  through `bound + 2` reads of one path and two of another, and asserts the reads up to the bound are served, the read
  past it refused with the count and `turn 1` in one line only and none of the text, the read after it still refused,
  and the second path served. Registers the name in the case list and the dispatch table. Verifies: the case's red run
  on the base — `bash tools/loop/tests/loop-cases.sh --case adapter#aPathIsRefusedAfterItsRepeatBound` on HEAD, which
  fails on the missing key (evidence: `evidence/red-on-HEAD.log`). Requirement: scenario "A path is refused after its
  repeat bound".
- [x] 1.2 Narrow the three `sed` expressions of `case_adapter_theChildIsStartedWithTheRepeatReadArm` to the arm key
  (`[A-Za-z]*` in place of the exact name), so the new numeric key's line is not rewritten by a case that only speaks
  of `LOOP_REPEAT_READ`. Verifies: `--case adapter#theChildIsStartedWithTheRepeatReadArm` passes beside the new key.

## 2. The count bound

- [x] 2.1 `recordedRepeatRefusals(raw)` in `tools/pi-trim-result.mjs` — a positive whole number, `0` for anything it
  does not understand, so a value the configuration did not record never breaks a read. Verifies: case 1.1's `0`
  arm and the CLI's unchanged posture.
- [x] 2.2 `entry.count` on the child's own tally in `repeatReadResult`, incremented for every read of the path past
  the first, and the refusal `refusals > 0 && entry.handed && !read.shell && entry.count >= refusals` placed **before**
  the arm branch so a `measure` run is bounded too. Verifies: case 1.1 and the four `repeat#` neighbours.
- [x] 2.3 `repeatReadNotice(path, lines, turn, size, count, bound)` keeps its shipped wording when no count answered
  and appends the count, the bound and the earlier turn when the bound did. Verifies: case 1.1's one-line assertion and
  `repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt`, which pins the shipped wording.
- [x] 2.4 The hook's `read` object gains `refusals: recordedRepeatRefusals(process.env.LOOP_REPEAT_READ_REFUSALS)`;
  the CLI does not read the key (it has no child tally), and its comment names that. Verifies: 2.2's case reaches the
  bound through the hook, which is what `--case adapter#aPathIsRefusedAfterItsRepeatBound` drives.

## 3. The configuration and the adapter

- [x] 3.1 `tools/loop/loop.conf` records `LOOP_REPEAT_READ_REFUSALS=3`, with the comment that prices it (one full
  delivery, the verification read a child's own edit earns, the range a notice sends it to) and states `0` as off.
  Verifies: case 1.1 reads the value from the file rather than pinning it.
- [x] 3.2 `tools/pi-child-events.sh` reads the key with the same `sed` grammar as the arm and exports it, never
  sources it. Verifies: case 1.1's export assertion.
- [x] 3.3 `tools/loop/gate.sh`'s `item_session_vars` gains the name, so a step the gate measures is not handed the
  item's count. Verifies: `bash tools/loop/tests/loop-cases.sh --case gate#aStepIsNotHandedTheItemsSession`.

## 4. Prose and falsification

- [x] 4.1 `tools/loop/prompts/worker.md` — the repeat rule's paragraph states the count bound and what a third ask
  for one file means. Verifies: the file is read, not compiled; `tools/look.sh 'repeat' --path tools/loop/prompts/worker.md`.
- [x] 4.2 `guidelines/Backlog.recipes.md` §7 — the repeat-read paragraph carries #943's 2 372 reads, the recorded 3,
  the case and mutation `989.1`.
- [x] 4.3 Mutation record `989.1` in `tools/loop/tests/loop-mutations.sh` rewrites the recorded value to `0`.
  Verifies: `bash tools/loop/tests/loop-mutations.sh --check --only 989.1` reports the case red and `--only` green on
  the unmutated tree; the red run is `evidence/red-on-mutation.log`.
- [x] 4.4 `openspec validate a-repeat-read-is-refused-after-its-count-bound --strict` — evidence
  `evidence/openspec-validate.log`.
- [x] 4.5 Run the suite over the tree — `bash tools/loop/tests/loop-selftest.sh`: `all 546 cases passed (JDK 21, offline, no
  token, no sandbox) — 545.17 s`, evidence `evidence/green-suite.log` — and the mutation set the delta owns
  (`--from-git HEAD`, 53 of 460 records): 53 red, 0 not-noticed, 0 restore-failures, evidence `evidence/mutations.log`.
  The full 460-record set was started and ended by the caller's 3 600 s ceiling after 280 records (all red on
  restore): the rest is stated as unrun in `evidence/mutations.log`, not claimed green. Its one GREEN record, `48.2`,
  is not this delta's and is filed as issue #1047.
- [x] 4.6 The structural checks the change names: `tools/check-doc-routes.sh`, `tools/check-portability.sh`,
  `tools/check-case-names.sh`, `tools/check-glossary.sh` — evidence `evidence/structural-checks.log`.
- [x] 4.7 Read the change back: the delta adds one requirement beside the shipped one and rewrites no text it does
  not own, its one scenario names its case, and every task above names what proved it.
- [x] 4.8 A finding this pass met and did not cause — mutation record `48.2` mutating a guard its case does not pass
  through — is filed on the tracker as issue #1047, not fixed here.

## 5. Measure after

- [ ] 5.1 The next run of the loop reports `metrics.repeatReads`, `metrics.dupChars` and `withheld.chars`, and no
  item is ended at `LOOP_ITEM_CONTEXT_MAX` with one path as its cause — `tools/loop/report.sh` over the run's
  `state.json` and `items/*/tool-result-trims.log`. This is a reading of a run, not of this change: name it in the
  item's closing comment.