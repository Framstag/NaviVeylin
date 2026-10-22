# tasks — fix-not-planned-closure-opt-in-gate

## 1. Red on HEAD

- [x] 1.1 Run the delta's probe against HEAD and keep its log: `bash /tmp/probe-251.sh`, whose three checks
  are `grep -qi 'not-planned' openspec/specs/issue-backlog/spec.md`,
  `grep -q 'close#notPlannedNeedsNoOptIn' openspec/specs/issue-backlog/spec.md`, and
  `bash tools/issue-tooling-selftest.sh --case 'close#notPlannedNeedsNoOptIn'`.
  Verification: `evidence/red-HEAD.log` — `FAIL spec#notPlannedExemptionIsStated`,
  `FAIL spec#notPlannedScenarioNamesItsCase`, `PASS close#notPlannedNeedsNoOptIn`,
  `tests=3 failures=2`, `EXIT=1`, 2026-10-22T06:31:40Z; the tally derived from those lines is
  `evidence/red-HEAD.xml`.
- [x] 1.2 Record that the code half of the item is already at HEAD and is not re-edited: `git log --oneline -1
  -- tools/issues/close.sh` → `9aac52b fix: a person's not-planned closure needs no opt-in label (#251)`.
  Verification: the commit is in `evidence/case-close-notPlannedNeedsNoOptIn-on-HEAD.log`'s sibling record and
  in `proposal.md`'s Phase A block.

## 2. The delta

- [x] 2.1 Write `specs/issue-backlog/spec.md`: one MODIFIED requirement, its four existing scenarios carried
  unchanged, one added scenario naming `close#notPlannedNeedsNoOptIn`.
  Verification: `grep -c '^#### Scenario:'` on the delta reads 5, and only the added one names a `Case` line —
  the four carried scenarios keep their original text.
- [x] 2.2 Write `design.md` (root cause, the single fix, the four eliminated alternatives, blast radius,
  rollback) and this task list. Verification: `openspec validate fix-not-planned-closure-opt-in-gate --strict`
  and `openspec doctor`.

## 3. Apply and verify

- [x] 3.1 Archive the change so the delta merges into `openspec/specs/issue-backlog/spec.md`.
  Verification: `openspec/changes/archive/<date>-fix-not-planned-closure-opt-in-gate/` exists and
  `grep -q 'close#notPlannedNeedsNoOptIn' openspec/specs/issue-backlog/spec.md` is true.
- [x] 3.2 Re-run the probe green on the archived tree. Verification: `evidence/green-archived.log` —
  `tests=3 failures=0`, `EXIT=0`.
- [x] 3.3 Run the project's gate once: `./tools/loop/gate.sh`. Nothing compiled changes, so no build is owed
  and the documentation suites stand in; the tally it writes is copied into `evidence/`.
  Verification: the gate's `LOOP-GATE:` line, `evidence/gate.txt` and the suite XML under `evidence/`.
- [x] 3.4 Copy every cited result into this change's own `evidence/` directory (the versioned half) and leave
  no task marked done on a partial run. Verification: `ls evidence/` names the red log, the red tally, the
  case log, the green log and the gate's XML.
