# Tasks — the live read covers the whole tracker

## 1. The case, red on the base

- [x] 1.1 Add `metadata#theLiveReadDoesNotTruncateTheTracker` to `tools/issues/tests/issue-tooling-cases.sh` — the
      label in `ISSUES_SHELL_CASES` and the arm in `issues_run_shell_case()` — and its function: a stub tracker of
      401 items numbered 1..401 with `createdAt` rising with the number, every item carrying `class/bug` +
      `prio/p2` and a complete `band=p2` marker except **#1**, whose marker says `band=p1`; `--live` must exit 1
      with `#1 band-mismatch` in its report and `401 issue(s) checked`.
      *Verification:* `bash tools/issues/tests/issue-tooling-selftest.sh --case
      'metadata#theLiveReadDoesNotTruncateTheTracker'` failed on the base for the right reason — the report carried
      `check-metadata: 400 issue(s) checked · 0 violation(s) · 0 untyped: expected '1', got '0'`, exit 1 — in
      `evidence/red-on-HEAD.log`.

## 2. The one read

- [x] 2.1 Add `issues_issue_list_all <json fields>` to `tools/issues/lib.sh`, beside `issues_issue_list`: the same
      `gh issue list --state all` call, window 1000, doubling while a page comes back full and printed only when it
      comes back short; the comment states that a short page is the proof and that a tracker inside the first window
      costs the one call it always cost. `issues_issue_list` then delegates to it — the dedup and selection queries
      read the same tracker, and the item a window drops is the one their search cannot find.
      *Verification:* `bash -n tools/issues/lib.sh` and `bash tools/issues/lib.sh --help 2>&1 | head -1` — no change;
      the function is exercised by task 1.1's case, and by the whole suite behind task 3.1's green log.

## 3. The two call sites

- [x] 3.1 `tools/issues/check-metadata.sh:150` reads through `issues_issue_list_all
      number,title,labels,body,issueType,parent,state`.
      *Verification:* the case of task 1.1 turns green — `PASS metadata#theLiveReadDoesNotTruncateTheTracker`, exit 0
      — in `evidence/green-after-fix.log`, and the tool's whole suite passes: `issue-tooling-selftest: all 189 cases
      passed` in `evidence/green-issues-selftest.log`.
- [x] 3.2 `tools/sensors/lib.sh:476` (`sensors_issues_of_row`) reads through
      `issues_issue_list_all number,title,body,labels,state,stateReason,author,comments,createdAt`, keeping its
      `jq` selection by the row's label.
      *Verification:* `bash tools/sensors/tests/sensor-selftest.sh` — `sensor-selftest: all 98 cases passed`, exit 0,
      in `evidence/green-sensors-selftest.log`.

## 4. The falsification

- [x] 4.1 Revert-check: restore the fixed window in `tools/issues/check-metadata.sh` (`--limit 400` through the
      `gh` call, the helper unused) and confirm the case of task 1.1 fails again for the same reason; restore the
      file byte-for-byte and confirm green.
      *Verification:* both runs in `evidence/revert-check.md`, with the failing report line
      (`400 issue(s) checked · 0 violation(s) · 0 untyped: expected '1', got '0'`) and the restored hash
      (`sha256sum tools/issues/check-metadata.sh`: `1714e0e7…2cf4` before and after the mutation).

## 5. The gate

- [x] 5.1 `./tools/loop/gate.sh` over the change, once, and its tally copied into `evidence/` at the moment it is
      produced.
      *Verified*: `./tools/loop/gate.sh`, its tally copied to `evidence/gate.log` after the archive. The change
      names no compiled file, so no build runs and the structural check stands in for it.
- [x] 5.2 `./tools/loop/measure.sh --change fix-the-live-read-covers-the-whole-tracker` once, quoted into
      `evidence/` rather than re-derived.
      *Verified*: `evidence/measure.log`, the run's own `LOOP-MEASURE` line.

## 6. The record

- [x] 6.1 The change's markdown carries `LOOP-ROUTE: guidelines/Backlog.md §5` on one line and every quoted number
      carries the command that produced it (`tools/loop/evidence-check.sh`'s C6 resolves the route against the
      document's own headings).
      *Verified*: `proposal.md`'s "Owning guideline section", and the commands beside every number quoted there.
