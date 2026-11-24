# Tasks: the-action-list-leads-with-its-counts

## 1. The header and the flag

- [x] 1.1 `tools/loop/report.sh`: count the rows `decisions` prints by class (decide / land / resume / worktree) and
      print one line above the list, built from the same rows.
- [x] 1.2 `tools/loop/report.sh`: accept `--actions` and print the numbered list alone.
- [x] 1.3 Keep the default output's list unchanged, line for line.

## 2. The cases

- [x] 2.1 `report#theActionListLeadsWithItsCounts` — a state with three classes prints three figures that match the
      list's own rows.
- [x] 2.2 `report#theFullActionListIsPrintedOnRequest` — `--actions` prints the list and no header.
- [x] 2.3 Declare both cases' files in the driver's own registry (`driver#everyCaseDeclaresTheFilesItCanReach`).

## 3. The falsification

- [x] 3.1 Mutate the header away and confirm 2.1 goes red; restore, then run
      `tools/loop/tests/loop-selftest.sh --case report#theActionListLeadsWithItsCounts` and the full suite.
      The mutation is the base's own `report.sh` (`git show 3ef5fbdd:tools/loop/report.sh`), which is the whole
      mechanism this change adds: `evidence/red-on-HEAD-counts.log` is 2.1 failing on it
      (`the header's decide figure is the number of decide rows the list prints: expected '1', got ''`) and
      `evidence/red-on-HEAD-actions.log` is 2.2 failing on it (`loop: report: unknown argument --actions`); with the
      change restored, `evidence/green-counts.log` and `evidence/green-actions.log` are the two cases passing.
- [x] 3.2 No guideline states the report's shape, so neither doc check owes an edit: the clause this change edits is
      the spec `agent-issue-loop`'s — `tools/look.sh 'actions the caller owes'` names
      `openspec/specs/agent-issue-loop/spec.md` and no file under `guidelines/`. Recorded in `design.md`,
      "The document this change owes".
