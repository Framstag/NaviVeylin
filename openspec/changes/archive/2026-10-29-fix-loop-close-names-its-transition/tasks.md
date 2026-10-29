# Tasks — fix-loop-close-names-its-transition

## 1. The red case (first, before any production code)

- [x] 1.1 `tools/loop/fixtures/scenarios/retired/issues.json` — one item (21) carrying `status/retired`, the
      other settled value of `roles.settledStatuses` (`tools/tooling.json:53`). *Verify*: the case below reads
      it as the item the closure acts on.
- [x] 1.2 `labels#closeNamesTheSettledTransition` in `tools/loop-cases.sh` (declared list, dispatch, body):
      `labels.sh close 21` over that item must land, record `status/fixed-by`, displace `status/retired`, name
      the displaced value in its own report, and close the item. *Verify*:
      `bash tools/loop-selftest.sh --case labels#closeNamesTheSettledTransition` FAILs on HEAD's write path —
      `a closure over the other settled state was refused: status-settled: #21 carries status/retired, … pass
      --displace-settled to name the transition`, `loop-selftest: 1 of 1 cases failed`, exit 1
      (`evidence/red-on-head.log`, XML `tests="1" failures="1"` at `2026-10-29T08:26:49Z`,
      `evidence/red-on-head.xml`).

## 2. The fix

- [x] 2.1 `tools/loop/labels.sh`: the `close` verdict's state write passes `--displace-settled`
      (`tools/loop/labels.sh:144`), with the comment that says why that verdict is the transition; the header's
      transition table says it too. *Verify*: `bash tools/loop-selftest.sh --group labels` green, all 7 cases
      (`evidence/green-labels.log`), including the matrix case whose close row is unchanged.
- [x] 2.2 The guard is untouched, so the routine writers still refuse: `tools/issues/lib.sh` and
      `tools/issues/update.sh` carry no edit in this change. *Verify*: `git diff --stat` over the change lists
      no path under `tools/issues/`; `tools/issue-tooling-selftest.sh` is in the gate's step list and green.

## 3. The rule where a reader looks for it

- [x] 3.1 `guidelines/Backlog.md` §4, I18: the row's "held by" names the loop's `close` verdict beside the
      closure and the reopen, and the new case. *Verify*: `bash tools/check-doc-routes.sh` and
      `bash tools/check-todo-references.sh` green (both are in the gate's step list).

## 4. The revert-check

- [x] 4.1 `tools/loop-mutations.sh` record `70.1`: the closure's flag replaced by `: # the closure names no
      transition`; `labels#closeNamesTheSettledTransition` must go red. *Verify*:
      `bash tools/loop-mutations.sh --only 70.1` → `RED 70.1 …` with the guard's refusal, then
      `restore 70.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`, exit 0
      (`evidence/revert-check.log`, XML `tests="1" failures="1"` at `2026-10-29T08:27:08Z`,
      `evidence/revert-check-mutated.xml`); `bash tools/loop-mutations.sh --check` applies every selected
      record, `70.1` included (in the gate's step list).

## 5. The gate, once

- [x] 5.1 `./tools/loop/gate.sh`, run once from the change root after the archive moved, its `LOOP-GATE:` line
      and every per-suite tally quoted into `evidence/gate.md` beside `evidence/gate-summary.xml`. No compiled
      file is touched (`tools/**`, `guidelines/**`, `openspec/**` — `guidelines/Build.md` §2, the scope block),
      so the map selects the shell suites and the documentation checks and no build.

## 6. Evidence and archive

- [x] 6.1 Every cited XML copied into `<change>/evidence/` and into
      `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/699/`; `evidence/measure.txt` holds the one
      measurement of this change's own delta.
- [x] 6.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-loop-close-names-its-transition --root .`
      reports no dangling pointer, before and after the archive.
- [x] 6.3 Archived; the directory exists under `openspec/changes/archive/` and the delta merged into
      `openspec/specs/agent-issue-loop/spec.md`.
- [x] 6.4 The delta's every scenario names one case, and that case is green in the recorded run — the gate's
      loop-selftest tally is the green.
- [x] 6.5 Left the tree as the change leaves it: no commit, no push, no version control, no tracker write.
