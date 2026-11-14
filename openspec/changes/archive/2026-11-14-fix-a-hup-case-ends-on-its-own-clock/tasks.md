# Tasks — a signalled case ends on its own clock

## 1. The case that waits

- [x] 1.1 `tools/loop/tests/loop-cases.sh:1246-1261`: add `CASE_WAIT_TICKS=300` (30 s at 0.1 s per tick, thirty times
  the 1 s `TERM`→`KILL` grace a run's trap gives its child, `guidelines/Backlog.recipes.md:724`) and
  `wait_for_pid_end <pid> [ticks]`, which answers `0` with the process's status in `WAIT_PID_STATUS` or `124` when the
  process outlived its ticks — `wait` runs in the case's own shell, because a subshell cannot reap its parent's child.
  — *spec: "A self-test case is held to a bound of its own"; verified by
  `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` green in `evidence/green-on-change.log` and red, with the
  helper absent, in `evidence/red-on-HEAD.log`.*
- [x] 1.2 `tools/loop/tests/loop-cases.sh:14330-14337`: the rewritten HUP case's wait becomes the case's own clock. A
  run that outlives the ticks is failed with the process it waited on, the `run.stop.reason` it recorded and the
  `items[0].result` it recorded; then its staged child is released, it is told to end with `loop_end_pid`, reaped at
  50 ticks (then `kill -9`), and the case's fifo is closed — so nothing of the run holds the case's stream when the
  case answers. — *spec: same requirement, scenario "A case's own wait ends on the case's own clock"; verified by
  `PASS run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` in `evidence/green-on-change.log`.*
- [x] 1.3 `tools/loop/tests/loop-cases.sh:14324`: before `kill -HUP "$owner"` the case asserts `.items[0].result` is
  empty, so the HUP is the only thing that can end the run — the 16-case pool's `expected '129', got '0'` was a run
  that had already answered its item by the time the signal arrived. Both facts the case reads are stages:
  `run.ownerPid` is written after the traps are installed (`tools/loop/run.sh:718-721`, `:730`), and the child writes
  its pid inside its own action before it blocks on the fifo (`tools/loop/tests/fixtures/pi:106`, `:114-118`). —
  *verified by the same green log, and by the assertion's own message naming the stage it needs.*
- [x] 1.4 `tools/loop/tests/loop-cases.sh:14308`, `:14315`, `:14318`: the case's three early exits ended the run they
  started with a bare `wait "$first"`; each now ends it at a bounded wait (`wait_for_pid_end "$first" 100 || kill -9`),
  so no `wait` on a process this file started is unbounded. — *verified by `bash -n tools/loop/tests/loop-cases.sh`
  and by the affected-cases run below.*
- [x] 1.5 `tools/loop/tests/loop-cases.sh:14290`: the case's `# files:` line names `tools/loop/tests/fixtures/pi`,
  which the case reaches through the stub child's fifo. — *verified by
  `PASS driver#everyCaseDeclaresTheFilesItCanReach` in `evidence/affected-cases.log`.*

## 2. The case that stages the race

- [x] 2.1 `tools/loop/tests/loop-cases.sh:14362`: new case `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` — it
  starts a pid, reaps it, and sends the HUP to the dead pid, so the signal reaches nothing and only the case's own
  clock can answer. It asserts `124` inside its ticks (`:14401`), an empty `run.stop.reason` as read before its own
  cleanup (`:14404`), that the run ends once its child is released and it is told to (`:14412`), and that the item is
  recorded `blocked`, which is the run's own trap doing the ending (`:14415`). Registry entries at `:240` and `:958`.
  — *spec: same requirement, same scenario; verified: `PASS` in `evidence/green-on-change.log`, and the red below.*
- [x] 2.2 The red is the new case on a tree without the helper: `FAIL`, `1 of 1 cases failed — 4.77 s`, the wait
  answering `''` instead of the bound's `124`. `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`
  (`tests="1" failures="1"`). The tree is the base `0848c35d` **with this change's case file**, so the two line
  numbers the log prints (`Zeile 18012`, `Zeile 18021`) are the base file's own (the case appended at its end), not
  the branch's (`wait_for_pid_end` at `:1246`, the case at `:14362`); `wait_for_pid_end: Kommando nicht gefunden` is
  the right reason — the capability, not a clock. — *verified by the two artifacts quoted above.*

## 3. The affected cases

- [x] 3.1 `bash tools/loop/tests/loop-selftest.sh --case 'run#aHup*'` → both cases `PASS`, `all 2 cases passed …
  8.02 s`, `2026-11-13T22:54:12Z` → `22:54:21Z` (`evidence/green-on-change.log`).
- [x] 3.2 The cases around the shape: `run#noProcessOfASignalledRunOutlivesIt` (a signalled run's processes),
  `driver#everyCaseDeclaresTheFilesItCanReach` (the `# files:` line), `driver#theCaseBoundScalesWithThePool` (the
  bound the case now sits below) — three `PASS`, `2026-11-13T22:53:12Z` → `22:53:41Z`
  (`evidence/affected-cases.log`).
- [x] 3.3 The delta touches no engine line, so it adds no mutation record and changes no existing one; `--check` was
  left for the gate child, whose run is recorded under 6.3.

## 4. The rule, where its readers route it

- [x] 4.1 `guidelines/Backlog.recipes.md:746-753` (§7b, the section `Backlog.md` §7 routes the loop's procedure to):
  one paragraph stating that the suite's bound is the backstop and not the clock a case keeps for itself, with the
  2026-11-11 measurement and the `CASE_WAIT_TICKS` figure.
- [x] 4.2 `openspec/specs/agent-issue-loop/spec.md:2621`: one SHALL paragraph in "A self-test case is held to a bound
  of its own" — a case that waits on a process bounds that wait itself — and its scenario at `:2640`. The
  requirement's first paragraph and its two other scenarios are carried verbatim, so the `MODIFIED` block replaces
  the shipped requirement whole. The change's delta is
  `specs/agent-issue-loop/spec.md`, the same three scenarios.

## 5. The change is complete before the gate

- [x] 5.1 The item's evidence is in `evidence/`: the red log and XML, the green log, the affected cases, the gate's
  own tally XML and the retained gate output, and the pre-gate review's diff
  (`head=41c883ffd03c7e49bbf7d77ced409f20e5000afe diff=d546e683b6bdb50b`).
- [x] 5.2 `tools/loop/evidence-check.sh <change>` → `PASS` (C1–C5).
- [x] 5.3 The pre-gate review: `LOOP-BLOCKING: 0`, `VERDICT: PASS`.

## 6. The gate and what it does and does not say

- [x] 6.1 The gate ran once, `run=2374424`, `24 suites tests=1034 failures=2 errors=0` — **RED**; that tally's umbrella XML is not retained. The failing suite is
  the wide selection `tools/loop/tests/loop-selftest.sh --from-git fcbe328dd1ed75c0863bc43ad7de0f8a734f638e`,
  `2 of 428 cases failed — 752.69 s` (`evidence/gate-loop-selftest-from-git.xml`). One of the two is the pre-existing
  `phase#aResultOverTheBoundIsCutAndNamed`, which that selection reddens on the tree this change was branched from.
- [x] 6.2 The failing suite measures exactly the file this change edits, so the tally above is not a waiver: the
  change's own cases were run by name on the change (`evidence/green-on-change.log`) and the new one was run by name
  on the base (`evidence/red-on-HEAD.log`), which is the discrimination the wide selection cannot make.
- [x] 6.3 Recorded, and not claimed: the gate's per-case `FAIL` names sit in its `/tmp` step streams, which were not
  retained (`evidence/gate-console-retained.log` holds the tally line and the gate child's closing answer). The second
  failing case's name is therefore unknown from this change's record — a reader with a fresh selection has to re-run
  it, and until then the count `2` stands as the tree's, not as this change's own red.

## 7. The checks this delta owes

- [x] 7.1 `tools/check-doc-routes.sh`, `tools/check-doc-values.sh`, `bash tools/check-openspec-hygiene.sh`,
  `bash tools/loop/tests/loop-mutations.sh --check` — green; the mutation set's records all still apply and none was
  added, re-pointed or removed by this change.
- [x] 7.2 `bash -n tools/loop/tests/loop-cases.sh` — `ok` (`evidence/affected-cases.log`).
