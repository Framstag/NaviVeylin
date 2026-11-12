# Tasks

Reference: `proposal.md` for the root cause and the red run, `specs/agent-issue-loop/spec.md` for the contract, and
`design.md` for the one fix and what the evidence eliminated.

The measure block this change was planned against (`./tools/loop/measure.sh --change fix-a-repeat-ledger-spans-the-item`,
`evidence/measure-block.log`): `LOOP-MEASURE-SUITE cases=418 cmd=tools/loop/tests/loop-selftest.sh --list`,
`LOOP-MEASURE-CHANGE fix-a-repeat-ledger-spans-the-item scenarios=4 cases=4 unnamed=0`, and its `LOOP-MEASURE-CHECK`
lines are the gate's selection — this pass runs none of them but the plan's first line.

## 1. The red cases, before the fix

Requirement: "The repeat rule's ledger is the item's, not the child's"; requirement: "A run counts the reads its
sessions repeated", its first scenario.

- [x] 1.1 `repeat#anItemsLedgerSpansItsChildren` in `tools/loop/tests/loop-cases.sh`: the rule's CLI driven twice with
      one ledger named by `LOOP_TOOL_RESULT_LEDGER`, the first read as `worker-prompt` and the second as
      `worker-gate-prompt` — the second is replaced by the one line, which names `worker-prompt` and turn 3 and is no
      text — then once more after an edit of the path, which comes back whole.
- [x] 1.2 `adapter#theChildIsStartedWithTheItemsLedger`: the real adapter, driven with a recorder child, hands it
      `<dirname events>/tool-result-ledger.tsv` and names it `worker-gate-prompt` from the prompt the run rendered.
- [x] 1.3 `report#anItemRecordsItsRepeatReads` rewritten where the trend's `reads:` line moved: the withheld volume is
      the rule's own figure and no longer "of them", because the item's ledger counts a repeat across two children that
      `metrics.dupChars` does not.
- [x] 1.4 `# files:` declarations: `tools/loop/tests/loop-cases.sh tools/pi-trim-result.mjs` for 1.1,
      `tools/pi-child-events.sh` for 1.2, the rewritten case keeps its own set.
- [x] 1.5 The red run on the base `7e441575`, the cases and nothing else: 1.1 FAILs — *the item's second child is
      handed the line, not text its worker already paid for: 'FIRST-LINE' should not appear in: FIRST-LINE …* — and 1.2
      FAILs — *expected '…/items/858/tool-result-ledger.tsv', got ''*. Both in `.pi/issue-loop/items/858/`
      (`red-on-head.log`, `red-on-head.xml`, 2026-11-12T21:20:56Z).

## 2. The fix

Requirement: the same.

- [x] 2.1 `tools/pi-trim-result.mjs`: `readLedger`/`writeLedger` carry `sha <tab> turn <tab> size <tab> session <tab>
      path` (a four-column line is dropped, so a stale shape costs a read and never a stale one); the hook opens
      `LOOP_TOOL_RESULT_LEDGER` at its first read and writes it after every read; `alreadyReadNotice` names the session
      (`in the <session> session`, the shipped wording when none is named); the CLI takes `--session` and both
      environment names; the header states the item ledger and why.
- [x] 2.2 `tools/pi-child-events.sh`: `LOOP_TOOL_RESULT_LEDGER` named beside the trim record, and `LOOP_CHILD_SESSION`
      named from the prompt the run handed the child.
- [x] 2.3 `tools/loop/report.sh`: the trend's clause is `<w> chars withheld by the repeat rule`, and the block comment
      says why the item ledger makes that figure no share of `dupChars`.
- [x] 2.4 The green run: `--group repeat` 6/6, `--group adapter` 10/10, `--group report` 35/35 — `evidence/green-*.log`
      in this change, the item's copies in `.pi/issue-loop/items/858/`.

## 3. The falsification

- [x] 3.1 `858.1` — the CLI's ledger read from the environment removed → `RED repeat#anItemsLedgerSpansItsChildren`
      (*the item's second child is handed the line …*), restore green.
- [x] 3.2 `858.2` — the notice's session pinned to the shipped wording → `RED repeat#anItemsLedgerSpansItsChildren`
      (*naming the session that read it …*), restore green.
- [x] 3.3 `858.3` and `858.4` — the adapter's two exports removed → `RED adapter#theChildIsStartedWithTheItemsLedger`
      for the ledger and again for the session, restore green. `evidence/mutations-858.log`.
- [x] 3.4 `bash tools/loop/tests/loop-mutations.sh --check` over the whole set: 347 records, every one applies to the
      file it names.

## 4. The prose the change makes false

- [x] 4.1 The delta in `specs/agent-issue-loop/spec.md`: the shipped requirement "A run counts the reads its sessions
      repeated" takes the withheld clause as the rule's own item-wide figure (both its scenarios repeated), and the new
      requirement "The repeat rule's ledger is the item's, not the child's" carries the item ledger, the session the
      line names and the read after an edit.
- [x] 4.2 `guidelines/Backlog.recipes.md` section 7b (the bound and the repeat rule): the ledger the item names, the session
      the line names, the two figures that are no longer one number's halves, and the new cases and mutation records
      beside `96.1`–`96.3`.

## 5. The structural checks and the gate

- [x] 5.1 The plan's first line, `tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach`, run before anything else — `evidence/structural-checks.log`.
- [x] 5.2 `tools/check-portability.sh` and `tools/check-openspec-hygiene.sh` — the two the delta's own files owe
      (`evidence/structural-checks.log`); the rest of the selection is the gate's, cited from `LOOP-MEASURE-CHECK`.
- [ ] 5.3 The gate child runs `./tools/loop/gate.sh --base 7e4415753c16023cabfce3fc041503dc0d45254e` over this diff and
      records the tally; this pass runs no gate, because the run defers it to the child that follows the review round.
