# Design — fix-report-reads-a-filings-marker

## Root cause

One mechanism, and it is a reader looking in half the places its writer writes. The writer has two: a recording
run appends its marker as a comment (`sensors_record`), a filing run embeds the same marker in the new issue's
body (`sensors_issue_body` → `sensors_marker`, `tools/sensors/run.sh:231`). Both readers of "the newest
measurement this row carries" walk `(.comments // [])` and nothing else — `tools/sensors/lib.sh:488`
(`sensors_last_marker`, the baseline at `tools/sensors/run.sh:560`) and `tools/sensors/report.sh:48` (the row's
`last`, `verdict`, `measurement`, trend and `issue=`). After a filing the row's newest marker is in a place
neither reader looks, so both answer "no measurement" — the report prints `last=never` and the
`never run or never filed` line, and the next comparison has an empty baseline.

A second, contributing fact: `sensors_issues_of_row` (`tools/sensors/lib.sh:424`) does not ask `gh issue list`
for `createdAt`, so a body marker would have no instant to be sorted by even if it were read. A filing's marker
carries no timestamp of its own — one write produced the issue and its marker — so the issue's creation time is
the marker's time.

## The single fix

One helper yields the row's markers as one timeline, and both readers use it.

```
sensors_markers_json() { # <issues-json> → [{createdAt, marker, number, body, state, fingerprint}], oldest first
  every comment whose body contains "verdict="  → createdAt = the comment's
  plus the issue body's own `<!-- … verdict=… -->` marker, if it has one → createdAt = the issue's
  | sort_by(.createdAt)
}
```

`sensors_last_marker` becomes `sensors_markers_json | jq -r 'last | .marker // ""'`, and the report's entry is
built from the same array (`last` = the last element, `previous` = the element before it with the same
fingerprint, `measurements` = its length). The report keeps printing what it printed; the fields simply come
from the whole timeline now.

One field the helper must not take from the issue is the marker's `fingerprint`. The report selects `previous`
by it (`select(.fingerprint == $last.fingerprint)`), so it is a *condition's* identity — and one issue can carry
markers of several conditions: the record issue's body names no fingerprint, while each clear posted on it
carries the cleared condition's own. Reading the fingerprint from `$issue.body` stamps every marker of an issue
with one identity, which makes `previous` "the marker before this one on that issue" and the trend a comparison
across conditions; the shipped report drew it between like-for-like conditions. The field is therefore read from
the marker (`.marker | [scan("fingerprint=[^ ]+")] | .[0]`), which is correct for both kinds, because the marker
is what carries the field in both.

Why the body marker is extracted with `scan("<!--[^>]*verdict=[^>]*-->")` rather than by matching `verdict=`
against the whole body: the body is prose plus records, and the report's `sensors_marker_field` reads a field
with a `[[:space:]]name=` pattern — a marker recovered as its own string is what both marker readers then parse
correctly. `[^>]*` is safe for this marker: the engine writes no `>` inside it.

## Blast radius

- `tools/sensors/lib.sh` — the new helper, `sensors_last_marker` re-implemented on it, `createdAt` added to the
  `gh issue list --json` field list.
- `tools/sensors/report.sh` — the entry's jq replaced by the helper's array; the print block is untouched.
- `tools/sensor-cases.sh` — three cases; `issue_record` gains an optional `createdAt` (absent unless a case names
  it, which is why a reader that needs the instant must be given one).
- `tools/sensor-mutations.sh` — three records (8.20, 8.21, 8.22).

What it does not touch: the record issue (its body carries no `verdict=` marker, so it still contributes
nothing), `sensors_conditions_json` (already reads the body), `sensors_recorded_measurement` (no caller), the
`sensors_record` comment path, and every metric row.

Risk: a body that quotes another issue's marker would now be read as the row's own. No writer does that — the
marker is written once, by `sensors_issue_body`, and a filing is the only path that puts it in a body — and the
`verdict=` guard keeps the record issue's marker out.

## Rollback

Revert the change: the helper and its two callers return to the comment-only walk, `createdAt` leaves the field
list, the three cases and the three mutation records are removed. No stored data changes, so no migration: the
tracker's markers are the same today as they were before, and the defect is only ever in how they are read.

## Alternatives the evidence eliminated

- **Add a `last-comment` field beside `last`** (the tracker's second candidate). Eliminated: the defect is that
  the report answers "never ran" for a row that filed; the newest marker of either kind answers when the row
  last acted and with which number, which is the question both readers ask. A second field would be new
  information, not a correction of the wrong answer, and no case in this delta asserts it as missing.
- **A per-row scan of the issue bodies inside `report.sh` only.** Eliminated: `sensors_run_row` takes its
  baseline from the same "newest marker" question, and the filing that leaves the report showing nothing is the
  filing whose number the next comparison needs. Fixing the report alone leaves the baseline empty for the same
  row.
- **Storing the marker beside the issue** (a file, a workflow artifact). Eliminated by the capability's own
  requirement: no measurement is stored in the repository; the issue's timeline is the history.
- **Taking the marker's fingerprint from the issue body**, as the first version of the helper did. Eliminated by
  measurement, not by preference: a record issue carries one clear per condition and no `fingerprint=` in its
  body, so both markers collapse onto the same empty identity and `previous` becomes the neighbouring condition
  (`report#aTrendIsBetweenOneCondition` prints `trend=better` where the shipped report printed `trend=-`). The
  marker carries its own fingerprint in both kinds, so the field is read from the marker.
