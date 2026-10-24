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
  Verification: `proposal.md`'s Phase A block names `9aac52b`; the case log beside it
  (`evidence/case-close-notPlannedNeedsNoOptIn-on-HEAD.log`) is four lines — timestamp, `PASS`, `EXIT=0`,
  timestamp — and carries no commit, so the proposal block is the record and this task does not point past it.

## 2. The delta

- [x] 2.1 Write `specs/issue-backlog/spec.md`: one MODIFIED requirement, its four existing scenarios carried
  unchanged, one added scenario naming `close#notPlannedNeedsNoOptIn`.
  Verification: `grep -c '^#### Scenario:'` on the delta reads 5, and only the added one names a `Case` line —
  the four carried scenarios keep their original text.
- [x] 2.2 Write `design.md` (root cause, the single fix, the four eliminated alternatives, blast radius,
  rollback) and this task list. Verification: `openspec doctor` and `openspec validate issue-backlog --type
  spec`, captured in `evidence/openspec-validate-doctor.log`; the delta's requirement is byte-identical to the
  merged one (probe check `spec#deltaMatchesTheMergedRequirement`). Round 1 named the change-id form of this
  check and left no artefact for it, which this record corrects.

## 3. Apply and verify

- [x] 3.1 Archive the change so the delta merges into `openspec/specs/issue-backlog/spec.md`.
  Verification: `openspec/changes/archive/<date>-fix-not-planned-closure-opt-in-gate/` exists and
  `grep -q 'close#notPlannedNeedsNoOptIn' openspec/specs/issue-backlog/spec.md` is true.
- [x] 3.2 Re-run the probe green on the archived tree. Verification: `evidence/green-archived.log` —
  `tests=3 failures=0`, `EXIT=0`.
- [x] 3.3 Run the project's gate once: `./tools/loop/gate.sh`. Nothing compiled changes, so the documentation
  suites are the coverage the delta owes — but `tools/loop/loop.conf:124` lists `*.xml` among the compiled
  patterns, and this change keeps its XML tallies inside `openspec/**/evidence/`, so the gate also ran
  `./gradlew test`; the 442 cases are the shell suites' own sum (155 + 117 + 45 + 10 + 115) and the Gradle run
  contributes 0 of them. The tally is honest for the suites, the parenthetical is not a measurement.
  Verification: the gate's `LOOP-GATE:` line, `evidence/gate.txt` and the suite XMLs under `evidence/`.
- [x] 3.4 Copy every cited result into this change's own `evidence/` directory (the versioned half) and leave
  no task marked done on a partial run. Verification: `ls evidence/` names the red log, the red tally, the
  case log, the green log and the gate's XML.

## 4. The review round's two corrections

- [x] 4.1 Correct the completed-closure sentence so it admits the authorisation a declared check's row gives:
  scoped to "an item that no declared check owns", with the `--sensor` path named as authorised by the check's
  row. The counter-evidence is executable and was green before the correction:
  `close#sensorScopeAllowsItsOwnIssue` (`tools/sensor-cases.sh:798-809`: `exit 0`, `--reason completed`, no
  `agent/eligible`), against `tools/issues/close.sh:100-101`. Verification: `evidence/red-correction-HEAD.log`
  — `FAIL spec#completedClosureIsScopedToUnownedItems`, `FAIL spec#sensorPathNamesItsAuthorisation`,
  `tests=6 failures=3`, `EXIT=1`, 2026-10-22T06:59:54Z.
- [x] 4.2 Delete "the settled state is written before the close" from the added scenario: the case it names
  builds item 41, which already carries `status/retired`, so `tools/issues/update.sh:149-151` writes nothing
  and `close#writesNothingWhenTheItemAlreadyCarriesTheState` asserts that `0`; ordering is asserted only on the
  completed path. The THEN now states what the case proves. Verification: `evidence/red-correction-HEAD.log`
  — `FAIL spec#scenarioDropsTheUnassertedWrite`.
- [x] 4.3 Re-run the probe green on the corrected tree, in both copies of the requirement. Verification:
  `evidence/green-correction-archived.log` — six `PASS`, `tests=6 failures=0`, `EXIT=0`.
- [x] 4.4 Run the structural checks the corrected paths owe, since no compiled file and no case changed:
  `tools/check-doc-routes.sh` and `bash tools/gen-feature-list.sh --check-classification`.
  Verification: `evidence/green-correction-archived.log` carries their exit status, and
  `evidence/openspec-validate-doctor.log` carries `openspec doctor` and the spec validation.
