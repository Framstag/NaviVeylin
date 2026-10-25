# Design — fix-sensor-green-row-record

## Context

See `proposal.md` — Why. This document records the root cause, the single fix, and the alternatives the
evidence eliminated.

## Root cause

One mechanism explains the symptom: a clear finding whose fingerprint matches no issue takes the
`if [ -z "$issue" ]` branch of `sensors_outcome_clear` (`tools/sensors/run.sh`), which calls `sensors_report`
and writes nothing. The report's every field comes from an issue, so a row whose measurements never landed on
one is indistinguishable from a row that never ran. The `baseline-multiple` rows are second-order victims of the
same branch: `SENSORS_BASELINE` is read from the row's newest issue marker (`sensors_last_marker`), so a green
`package-size` row can never acquire the baseline its bound is a multiple of.

## The single fix

A clear that no **open** condition issue carries is recorded on a **record issue**: one issue per row, filed on
the first such clear.

- **Identity.** The record is found by its own body marker `<!-- sensor-record: <id> -->`, never by a
  fingerprint. `create.sh` is passed the literal `record-<id>` as the finding's fingerprint; the tooling's
  `Fingerprint.digestIn` only recognises a 64-hex digest, so no condition finding can match a record, and
  `sensors_conditions_json` additionally filters records out. A record is therefore never reopened or closed by
  the lifecycle.
- **State.** The record carries `record.status` from the registry (`on-hold`): the profile's value for an item
  no run may take. The record is a history, not a work item, and `duplicates.sh` (a waiting item is OPEN **and**
  `status/open`) and the loop's pre-screen (`list.sh --eligible-only`) both skip it. `resolve-stale.sh` reads
  open items, but the record's body cites no path, so it has nothing to repair.
- **Writes.** Filing and the first measurement are two writes in one run: `create.sh` prints the created issue's
  URL, and the engine reads the number from it (`sensors_file_record`). A per-run cache (`SENSORS_RECORD_STATE`)
  keeps one filing per row, because one row can report more than one clear identity in one run (coverage's
  several modules, source-size's path-`none` clear beside a recorded path). Later clear measurements are
  comments only (`sensors_comment`) — a record's body carries no condition counters.
- **Already-closed conditions.** A clear whose condition issue is closed takes the record path too, so a row
  whose only issue is a long-closed condition does not go stale after the closure.

`report.sh` needs no change: its `entry` already flattens every measurement comment of every issue carrying the
row's label and sorts them by `createdAt`, so the record issue's comments simply join the row's timeline.
`sensors_last_marker` gains the same sort, so the baseline is the newest marker of the row rather than the last
of the newest-first issue list.

## Alternatives the evidence eliminated

| alternative | why it is impossible / rejected |
|---|---|
| let `report.sh` read the Actions run history for a row the tracker cannot answer | contradicts the requirement the defect is against ("reaches the tracker's history"), and the report step holds the repository token, whose declared scope (`guidelines/Backlog.md` §9) names issues and pull requests and no Actions read |
| treat the report's `last=never` as "no evidence" and print "unknown" instead | the bound is "a row that ran must never be reported as never run"; a softer word still leaves a healthy check indistinguishable from a dead one, and a `baseline-multiple` row still has no baseline |
| file an issue under the row-level clear's own fingerprint (`{"path":"none"}`) | it is a condition issue by construction: an OPEN one that says "no condition", which the next clear's hysteresis would close, returning the row to `never` |
| one shared history issue for every row | the report selects a row's issues by its own `sensor/<id>` label (the ownership scope, D7); a shared issue would widen the label-scoped read that bounds what a check may touch |
| post the measurement on the newest closed issue of the row | it is not the row's record: a closure's own markers and the hysteresis counters of a condition issue would be mixed with a green row's history, and the write could not create the issue a never-filed row lacks |
| a committed baseline file or an Actions cache | D8: the issue is the metric history; a committed file is a second writer in git and a cache expires |

## Risks / Trade-offs

- **The tracker gains up to one record issue per row** (eight today), each open under `status/on-hold`. Accepted:
  it is the only tracker place a green verdict can live, the state keeps it out of every work selector, and the
  alternative leaves the pipeline's own health signal wrong for exactly the rows that work.
- **A row's history is split across a record issue and its condition issues.** The report already merges them by
  label and instant; the split is visible in `issue=#N` and is the honest location of each measurement.
- **A record is filed by a run, so a dry-run dispatch still writes nothing** and its report still cannot show a
  measurement. The tracked history is the applied run's, which is the observed case (a scheduled tick runs with
  `--apply`).

## Verification

- The reproducer, red on HEAD and green after the fix: `run#aClearWithNoIssueReachesTheRecord` — the record is
  filed with the row's label, the registry's record state and no authorisation, the measurement is posted, and a
  later run finds the record by its marker and comments on it.
- The report's own distinction: `report#aGreenRowIsNotNever` — a record reads as `verdict=clear` with no `never`
  and no `stale` line, while a row with nothing anywhere still reads `never` and `stale`.
- The record is not a condition: `lib#theRecordIsNotACondition`.
- `bash tools/sensor-selftest.sh` (49 cases), `bash tools/sensor-mutations.sh --only 8.18`,
  `bash tools/check-portability.sh`, `bash tools/portability-cases.sh`.
- No compiled file is touched, so the Gradle gate is not run (`guidelines/Build.md` §2, scope block).
