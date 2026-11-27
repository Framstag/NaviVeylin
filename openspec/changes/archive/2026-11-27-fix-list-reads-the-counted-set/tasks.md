# Tasks

## 1. The red case: a tracker bigger than the window

- [x] 1.1 Add `case_list_readsTheWholeSetNotTheNewestPage` to `tools/issues/tests/issue-tooling-cases.sh`, register
      `list#readsTheWholeSetNotTheNewestPage` in `ISSUES_SHELL_CASES` and in `issues_run_shell_case`, and write the
      tracker with a `list_tracker` helper beside the case: 405 items numbered 1..405, so the stub `gh`'s
      newest-created slice puts `#1` outside the newest 400.
      Verify: `bash tools/issues/tests/issue-tooling-selftest.sh --list` names the case.
- [x] 1.2 Run the case on the tree as it stood (the base plus the first process's `lib.sh` helper) and record it
      red: `--id 1` answers `list: no issue carries <!-- nvid: 1 -->` and exits 4 — the tool's "this entry does not
      exist" answer over an item the fixture holds.
      Done: `evidence/red-on-HEAD.log` — `list --id 1 failed on a tracker larger than the newest page`,
      `FAIL list#readsTheWholeSetNotTheNewestPage`, exit 1 (2026-11-27T14:29:54Z).

## 2. Both reads over the counted set

- [x] 2.1 `tools/issues/lib.sh`: `issues_issue_list` asks for `state` and `issueType` beside the fields the dedup
      reads (so one answer serves the filter read too), and `issues_issue_list_whole` counts first, reads
      `count + 1` and refuses a read that comes back that full as `issue-set-uncovered`, on stderr, because the
      read is taken inside a command substitution.
      Verify: `tools/look.sh '^issues_issue_list_whole' --path tools/issues/lib.sh` finds the function, and the
      `state,issueType` fields are still in the one `gh issue list` line.
- [x] 2.2 `tools/issues/list.sh`: `limit` defaults to empty and a helper beside the two branches takes the one read
      each of them answers from — `issues_issue_list "$limit"` when the caller named a window,
      `issues_issue_list_whole` otherwise; the doc header and the `--help` range account for it.
      Verify: `tools/look.sh 'gh issue list' --path tools/issues/list.sh` finds no read in the tool itself.
- [x] 2.3 `tools/loop/prescreen.sh` and `tools/loop/run.sh`: the three `--limit 400` pins and prescreen's default
      are dropped, so those callers inherit the counted set; a `--limit` a caller names is passed through.
      Verify: the case from 1.1 passes, and `driver#noEngineFileExceedsItsBound` stays green — prescreen.sh is 859
      lines against its recorded bound of 860 (`tools/loop/layout.tsv`).
      Done: `evidence/green-on-change.log` — `PASS list#readsTheWholeSetNotTheNewestPage`, exit 0
      (2026-11-27T14:30:20Z).

## 3. The owning guideline section

- [x] 3.1 No edit is owed: `guidelines/Backlog.md` §6 already states that selection examines the tracker's own
      counted set and that a read coming back as full as its window is refused, and this change makes the listing
      read carry that sentence rather than adding a second one for it.
      Verify: `tools/look.sh '^## 6\.' --path guidelines/Backlog.md` resolves the section the route names.

## 4. Evidence, archive, gate

- [x] 4.1 Every artifact this change cites was copied into `evidence/` at the moment it was produced: the case's
      red run (`red-on-HEAD.log`), its green run on the change (`green-on-change.log`), the tooling suite green with
      the change (`tooling-suite.log` — all 192 cases), and the loop suite run the change was checked against
      (`loop-selftest.log`).
      Verify: `bash tools/loop/evidence-check.sh openspec/changes/fix-list-reads-the-counted-set --root .` reports
      no FAIL.
- [x] 4.2 `openspec archive --yes` moves the change under `openspec/changes/archive/` with its delta.
- [x] 4.3 `./tools/loop/gate.sh` once, detached, polled until its log ends `LOOP-GATE:`.
      Verify: the report quotes that line, copied from the log, and the XMLs it names are copied into the item's
      evidence directory as they were produced.
