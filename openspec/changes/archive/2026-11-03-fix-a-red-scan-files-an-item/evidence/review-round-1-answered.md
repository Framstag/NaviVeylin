# Review round 1 — the answer and what it bought

The run's round-1 reviewer refused the change with four blocking findings (`reviewer-report-1.md` in the run's
item directory) and put the choice to a person: merge as it stood, apply the additive corrections and re-gate,
re-run the change, or abandon it.

**The answer recorded on the issue**: apply the additive corrections and re-gate — the recommendation, because
the first finding decided whether the row could ever read a record, and the other three decided whether the
scenarios the delta ships are exercised by the cases that name them.

What changed, by finding:

1. **The artifact root (blocking).** The record moved from `app/build/scan-result.json` to `scan-result.json` at
   the workspace root: `actions/upload-artifact` roots an archive at the least common ancestor of its `path:`
   list, so a single explicit file is carried under its basename and restored at the workspace root. The
   writer (`tools/build-inputs.sh`), the upload (`sonar.yml`) and the row (`sensors.json`) now agree on one
   string, `inputs#theScanRecordIsTheScanPipelinesOwn` asserts that agreement, and
   `sonar-workflow#theRecordPathIsTheUploadsOwn` holds the root rule on the check's side.
2. **The failed-scan scenario (blocking).** `sensors#aFailedScanIsItsOwnCondition` now asserts that the item
   names the step and the command, and that a record naming a different step is a second item.
3. **The green-with-nothing-recorded half (blocking).** `sensors#aGreenScanClearsItsCondition` exercises it —
   and the assertion found a real defect: the row-level clear carried an empty `step`, which the runner
   refuses as a malformed finding. The clear now carries the record's own `the-run` sentinel.
4. **The readiness branch (blocking).** `workflow#theScanRecordIsFetchedBeforeItsRowRuns` asserts
   `[ "$status" -eq 3 ]` → `scan-ready=false` and that any other status exits non-zero; its base-red run is in
   `red-on-HEAD.log` at last.

The notes the reviewer raised are corrected where they were wrong, not merely reworded: the 544-character
requirement was never this change's (its body is 464; the >500 warning belongs to "A source file's size is a
declared check", unchanged on `main`), the gate's "no `openspec/` path in the diff" sentence is replaced by what
the gate actually ran, the impact list names the shipped spec, and the recipes sentence states the upload-root
rule rather than a script's write semantics. A revert-check is carried in `revert-check.log`.
