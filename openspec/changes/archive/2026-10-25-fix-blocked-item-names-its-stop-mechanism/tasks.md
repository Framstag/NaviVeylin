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
      `.items[].stopReason` on a new record (`null` when the block has no mechanism). Verification:
      `state#aBlockedItemNamesTheMechanismThatStoppedIt` passes, including its two usage-error assertions, the
      byte comparison that the state is unchanged after each, and the second account that fills no mechanism.
- [x] 2.2 `tools/loop/state.sh`: the item table gains the `stop` column after `reason`, printing `-` for a
      record that carries no mechanism. Verification: `report#wallTimeAndReasonInTheTable` passes with sixteen
      separators and reads column 15 itself — `refused` on the blocked row, `-` on the closure and on a block no
      mechanism caused; `report#headerAndTableShape` stays green.
- [x] 2.3 `tools/loop/run.sh`: `stop_reason="refused"` at the last-round refusal and `stop_reason="unmergeable"`
      at the merge refusal, handed to the blocked `done` call; no other block sets it. Verification:
      `run#reviewRoundsCapAtTwo` and `run#doesNotCloseOnAMergeTheBaseCannotTake` pass, and
      `run#childClaimWithoutCommitIsBlocked` still records a block with no mechanism.

## 3. The cases green

- [x] 3.1 Run the four cases: `bash evidence/red-cases-280.sh` → four `PASS`
      (`evidence/green-cases.log`, against `evidence/red-cases-head.log`).
- [x] 3.2 The delta's own case set is the gate's `tools/loop-selftest.sh --from-git <base>` step, because the four
      files this change touches declare the cases that name them; it is quoted in 5.1 rather than run twice.
- [x] 3.3 Falsify the new invariants in the tree, one at a time — the refusal site, the merge site, the state
      write, the mechanism in the report's column and the `-` a row with none prints — each noticed by its named
      case, restore green again: `bash tools/loop-mutations.sh --only "58.1 58.2 58.3 58.4 58.5"` →
      `red-when-mutated: 5 · not-noticed: 0 · restore-failures: 0` (`evidence/revert-check.log`), and the
      assertion each record breaks quoted in `evidence/revert-check-round2-messages.log`.

## 4. The rule where a person reads it

- [x] 4.1 `guidelines/Backlog.md` §7 — the report paragraph names the mechanism column beside the spend.
      Verification: `tools/check-doc-routes.sh` green in the gate.
- [x] 4.2 `openspec validate fix-blocked-item-names-its-stop-mechanism --strict` →
      `Change 'fix-blocked-item-names-its-stop-mechanism' is valid`; the requirement body is 454 characters
      against the validator's 500 (`awk 'NR>=2107 && NR<=2111' openspec/specs/agent-issue-loop/spec.md | tr '\n'
      ' ' | wc -c` → 455 including the trailing space; the round-1 figure of 499 counted something else).

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

## 6. Correction round 1 (the review of round 1 refused the change)

Two BLOCKING findings, both a scenario whose THEN no case read: the report's `stop` column printed an empty cell
for every record written after the change (so "or `-` when it carries none" was false in the code while its case
passed by matching the reason column), and `the state is not written` was asserted nowhere.

- [x] 6.1 The corrected assertions red on the pre-correction tree — the round-1 write and the round-1 column put
      back, the two cases run, the tree restored: `report#wallTimeAndReasonInTheTable` `while a closure's stop
      column prints -: expected '-', got ''`, exit 1, 2026-10-25T00:06:09Z (`evidence/red-cases-head-round2.log`,
      re-runnable as `evidence/red-cases-280-round2.sh`). `state#aBlockedItemNamesTheMechanismThatStoppedIt`
      passes there too: its clause was already true and only unasserted, so what proves the new assertions
      load-bearing is record `58.3`, not a red case.
- [x] 6.2 `state.sh`: the record writes `null` when no mechanism is given (`stopReason:(if $sr == "" then null else
      $sr end)`), and the column renders `-` for `null` and for `""`. Verification: `report#wallTimeAndReasonInTheTable`
      and `state#aBlockedItemNamesTheMechanismThatStoppedIt` green (2026-10-25T00:06:20Z–00:06:23Z,
      `evidence/green-cases-round2.log`), and the `report` and `state` groups green (15 and 18 cases).
- [x] 6.3 `tools/loop-cases.sh`: `table_cell` reads one column of a row, and the three stop-column clauses are
      read through it (mechanism, closure, block without a mechanism); the state case compares the state file
      byte for byte after each usage error and records a second account to show it fills no mechanism.
      Verification: 6.2.
- [x] 6.4 `tools/loop-mutations.sh`: `58.3` nulls the record's mechanism, `58.4` makes the column print `-` always,
      `58.5` makes it print an empty cell. Verification: `bash tools/loop-mutations.sh --only "58.1 58.2 58.3
      58.4 58.5"` → `red-when-mutated: 5 · not-noticed: 0 · restore-failures: 0`
      (2026-10-25T00:07:46Z–00:08:46Z, `evidence/revert-check.log`), each record's failing assertion quoted
      (`evidence/revert-check-round2-messages.log`): `expected 'refused', got 'null'` (58.1–58.3), `expected
      'refused', got '-'` (58.4), `expected '-', got ''` (58.5).
- [x] 6.5 The archived artifacts corrected where round 1's counts and clauses were stale: `proposal.md` (5 records,
      6 files, the correction's red block), `design.md` (`null`, not `absent`, and the correction section),
      `tasks.md` 2.1/2.2/3.3/4.2 (the requirement body is 454 characters, not 499), and `evidence.md` copied into
      the change's own `evidence/`. Verification: `evidence-check.sh` → `evidence-check: PASS`
      (`evidence/evidence-check.log`).
- [x] 6.6 Ran the project's gate **once**, from the change root, after 6.2–6.4: `./tools/loop/gate.sh` →
      `LOOP-GATE: 530 cases · 0 failures · 0 errors` over sixteen suites (started after the mutation run ended at
      00:08:46Z, its log last written 00:34Z; build 227 s, `tools/loop-selftest.sh --from-git db888dd8…` 971 s /
      219 cases, `bash tools/loop-mutations.sh --check` 150 records, `tools/loop-mutations-selftest.sh` 157 s /
      34 cases), tallies in `evidence/gate-round2/`, log in `evidence/gate-round2.log`. The edits after it are
      this file, `proposal.md`, `design.md` and `evidence/evidence.md` — documentation only: no engine script, no
      case file and no compiled path changed after the gate, so there is nothing to re-run and the gate is not
      re-run.
