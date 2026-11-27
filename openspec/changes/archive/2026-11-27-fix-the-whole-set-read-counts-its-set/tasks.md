# Tasks

## 1. The red case: a tracker bigger than the window

- [x] 1.1 Add `case_prioritise_readsEveryIssueNotTheNewestPage` to `tools/issues/tests/issue-tooling-cases.sh`,
      register `prioritise#readsEveryIssueNotTheNewestPage` in `ISSUES_SHELL_CASES` and in
      `issues_run_shell_case`, and write the fixture with the existing `metadata_issues` helper: a 405-item
      tracker whose item `#1` sits outside the newest 400.
- [x] 1.2 Run `bash tools/issues/tests/issue-tooling-selftest.sh --case prioritise#readsEveryIssueNotTheNewestPage`
      on the base tree and record it red: `.read` is 400 and `#1` carries no record. Copy the run's log into
      `evidence/`.
      Done: `evidence/red-on-base-commit.log` — `the pass reads the set the tracker holds, not a window of it:
      expected '405', got '400'`, exit 1.

## 2. Count the set before the read

- [x] 2.1 `tools/issues/lib.sh`: add `issues_set_count` (the tracker's own `totalCount` over all states, the
      shape of `tools/issues/decisions.sh:71`), give `issues_issue_list` its window parameter defaulting to
      `count + 1`, and make `issues_page_records` count first, read `count + 1` and refuse
      `issue-set-uncovered` when the read comes back that full — with the refusal on stderr, because the read
      is taken inside a command substitution — and carry that refusal's status out of the two callers
      (`tools/issues/prioritise.sh:103`, `tools/issues/classify.sh:85`) with `|| exit $?`.
      Verify: the case from 1.1 passes its `.read` = 405 half; `tools/look.sh 'limit 400' --path tools/issues/lib.sh`
      finds nothing, and the three reads that still name a constant window
      (`tools/issues/check-metadata.sh:150`, `tools/sensors/lib.sh:476`, `tools/loop/run.sh:674`) are named in
      `design.md` as serving other claims rather than being callers of this read.
- [x] 2.2 `tools/issues/tests/fixtures/gh`: answer a `totalCount` query that carries no label with the count
      of every item the fixture holds, beside the label-scoped arm.
      Verify: the case's second half passes — `GH_STUB_COUNT_LAG=1` yields exit 3 naming `issue-set-uncovered`.

## 3. The owning guideline

- [x] 3.1 `guidelines/Backlog.md` §6: the "selection never samples" bullet gains the counted-set clause.
      Verify: `tools/look.sh 'counted' --path guidelines/Backlog.md` finds it, and `tools/look.sh '^## 6\.'`
      still resolves the section the route names.

## 4. Evidence, archive, gate

- [x] 4.1 Every artifact this change cites was copied into `evidence/` at the moment it was produced: the case's
      red run on the base tree (`red-on-base-commit.log`), the green run on the change
      (`green-on-change.log`), the whole tooling suite green after the change (`green-suite.log`, 189 of 189
      cases), and the mutation that proves the second scenario's clause — the refusal's `>&2` removed, so the
      refusal is captured as the read's number (`red-when-the-refusals-stream-moves.log`), the file restored
      byte for byte (`cmp` clean) and green again (`green-when-restored.log`).
      Verify: `bash tools/loop/evidence-check.sh openspec/changes/fix-the-whole-set-read-counts-its-set --root .`
      reports no FAIL.
- [x] 4.2 `openspec archive --yes` moves the change under `openspec/changes/archive/` with its delta.
- [x] 4.3 `./tools/loop/gate.sh` once, detached, polled until its log ends `LOOP-GATE:`.
      Verify: the report quotes that line, copied from the log, and the XMLs it names are copied into the item's
      evidence directory as they were produced.
