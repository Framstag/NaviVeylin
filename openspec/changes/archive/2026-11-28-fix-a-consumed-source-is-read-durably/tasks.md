# Tasks — #1114, a consumed source is read durably

## 1. The red cases (tools/loop/tests/loop-cases.sh)

- [x] 1.1 Add `case_clean_aConsumedSourceIsReadDurably` and `case_clean_aChangeWorkedInSlicesIsKept`, register
      `clean#aConsumedSourceIsReadDurably` and `clean#aChangeWorkedInSlicesIsKept` in `LOOP_SHELL_CASES` and in
      `loop_run_shell_case`'s dispatcher, and write the `# files:` declaration each case owes.
      Verify: `bash tools/loop/tests/loop-selftest.sh --list` names both cases, and
      `bash tools/loop/tests/loop-selftest.sh --case clean#aConsumedSourceIsReadDurably` reaches the case.
- [x] 1.2 Run both cases on the base commit with only the cases in place and record them red: the arm names nothing
      of the consumed source, and the slice's source is not kept.
      Done: `evidence/red-on-base.log` — `FAIL clean#aConsumedSourceIsReadDurably` and
      `FAIL clean#aChangeWorkedInSlicesIsKept`, exit 1 each, `evidence/red-on-base.xml` tests=2 failures=2
      (2026-11-27T23:16:12Z, base `856269d96fd25c7831cbd489b118251d2563ff2c`).

## 2. The durable reading (tools/loop/loop-clean.sh)

- [x] 2.1 The arm's landed set is the union of the state's `done` items and the event record's `closed` item results,
      read from `$LOOP_RUN_EVENTS` and `$LOOP_RUN_EVENTS.1` beside the state.
      Verify: `tools/look.sh '"result":\s*"closed"'` is not needed — the case's first assertion goes green only when
      the event record is what names the item, because the state's `.changeCopies` is asserted empty before the run.
- [x] 2.2 The arm's copies are the union of the state's `.changeCopies` and every
      `items/<issue>/change-copy.json`; a source is reported once (`seen_sources`), and `items_root` is assigned in
      this arm because the item-directory arm below reads the same path.
      Verify: `clean#aConsumedSourceIsReadDurably`'s second assertion — the source is named once, not once per record.
- [x] 2.3 A source whose `tasks.md` carries a checked task is kept and named with that reason, before any removal.
      Verify: `clean#aChangeWorkedInSlicesIsKept`.

## 3. The rule the tree states

- [x] 3.1 `guidelines/Backlog.md` §6 names the record the clean command reads and the reading that keeps a change
      worked in slices. Verify: `tools/look.sh 'change-copy.json' --path guidelines/Backlog.md` finds the paragraph,
      and `tools/check-doc-routes.sh` passes.
- [x] 3.2 The delta modifies `agent-issue-loop`'s requirement "The consumed change is cleaned from the project's
      tree…", keeping its three scenarios and adding the two the cases name.
      Verify: `openspec validate --changes` on the change directory.

## 4. Evidence, archive, gate

- [x] 4.1 `evidence/green-on-change.log` and `evidence/green-on-change.xml` — both cases pass with the fix in place
      (tests=2 failures=0).
- [x] 4.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-a-consumed-source-is-read-durably --root .`
      reports no FAIL.
- [x] 4.3 `openspec archive fix-a-consumed-source-is-read-durably --yes`, and the gate
      (`./tools/loop/gate.sh`) run once over the archived change.
