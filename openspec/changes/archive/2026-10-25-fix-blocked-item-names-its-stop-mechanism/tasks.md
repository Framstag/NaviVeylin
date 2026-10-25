# Tasks — fix-blocked-item-names-its-stop-mechanism

## 1. Red cases (fail on HEAD)

- [x] 1.1 Add `state#aBlockedItemNamesTheMechanismThatStoppedIt` to `tools/loop-cases.sh` (its list entry, its
      dispatch arm and its body, declaring `tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json`).
      Verification: on HEAD it failed with `loop: done: unknown argument --stop-reason`
      (`evidence/red-cases-head.log`).
- [x] 1.2 Extend the three cases whose own block is one of the two mechanisms: `run#reviewRoundsCapAtTwo`
      (`expected 'refused', got 'null'`), `run#doesNotCloseOnAMergeTheBaseCannotTake`
      (`expected 'unmergeable', got 'null'`) and `report#wallTimeAndReasonInTheTable` (no `| stop |` header).
      Verification: each `1 of 1 cases failed` on HEAD, recorded by `evidence/red-cases-280.sh`
      (`evidence/red-cases-head.log`, 2026-10-24T23:34Z).

## 2. The record carries the mechanism

- [x] 2.1 `tools/loop/state.sh`: `done` parses `--stop-reason`, refuses a value outside `refused|unmergeable` and
      any stop reason on a result that is not `blocked` (exit 2, state untouched), and writes the value as
      `.items[].stopReason` on a new record. Verification: `state#aBlockedItemNamesTheMechanismThatStoppedIt`
      passes, including its two usage-error assertions.
- [x] 2.2 `tools/loop/state.sh`: the item table gains the `stop` column after `reason`. Verification:
      `report#wallTimeAndReasonInTheTable` passes with sixteen separators; `report#headerAndTableShape` stays
      green.
- [x] 2.3 `tools/loop/run.sh`: `stop_reason="refused"` at the last-round refusal and `stop_reason="unmergeable"`
      at the merge refusal, handed to the blocked `done` call; no other block sets it. Verification:
      `run#reviewRoundsCapAtTwo` and `run#doesNotCloseOnAMergeTheBaseCannotTake` pass, and
      `run#childClaimWithoutCommitIsBlocked` still records a block with no mechanism.

## 3. The cases green

- [x] 3.1 Run the four cases: `bash evidence/red-cases-280.sh` → four `PASS`
      (`evidence/green-cases.log`, against `evidence/red-cases-head.log`).
- [x] 3.2 The delta's own case set is the gate's `tools/loop-selftest.sh --from-git <base>` step, because the four
      files this change touches declare the cases that name them; it is quoted in 5.1 rather than run twice.
- [x] 3.3 Falsify the four new invariants in the tree, one at a time — the refusal site, the merge site, the
      state write and the report column — each noticed by its named case, restore green again:
      `bash tools/loop-mutations.sh --only "58.1 58.2 58.3 58.4"` → `red-when-mutated: 4 · not-noticed: 0 ·
      restore-failures: 0` (`evidence/revert-check.log`, 23:36:37Z–23:37:33Z).

## 4. The rule where a person reads it

- [x] 4.1 `guidelines/Backlog.md` §7 — the report paragraph names the mechanism column beside the spend.
      Verification: `tools/check-doc-routes.sh` green in the gate.
- [x] 4.2 `openspec validate fix-blocked-item-names-its-stop-mechanism --strict` →
      `Change 'fix-blocked-item-names-its-stop-mechanism' is valid`; the requirement body is 499 characters
      against the validator's 500.

## 5. Gate, evidence, archive

- [x] 5.1 Ran the project's gate **once**, from the change root: `./tools/loop/gate.sh` → `LOOP-GATE: 529 cases ·
      0 failures · 0 errors` over sixteen suites (2026-10-24T23:38:23Z–2026-10-25T00:01:56Z; the loop's own suite
      ran 219 cases by `--from-git`, the build 229 s), tallies copied into `evidence/gate/`, the log into
      `evidence/gate.log`.
- [x] 5.2 `bash .pi/skills/fix-loop/scripts/evidence-check.sh <the archived change> --root .` →
      `evidence-check: PASS` (`evidence/evidence-check.log`).
- [x] 5.3 Archived as `openspec/changes/archive/2026-10-25-fix-blocked-item-names-its-stop-mechanism`; the delta
      merged into `openspec/specs/agent-issue-loop/spec.md` as `+ 1 added`, and the requirement
      *A block records the mechanism that stopped it* is there.
