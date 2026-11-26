# Design — the title is composed from the finding's own fields

## Root cause

A finding's title is a field (`tools/sensors/lib.sh:601-607`, position 6 of `sensors_emit`) that
`sensors_file` uses **verbatim** (`tools/sensors/run.sh:275`), and that the writer turns into the issue title
(`run.sh:282`). Three rows fill it with one constant sentence each, so the title carries no part of the
condition and two conditions of one row file under one title. The identity is separate and correct: the
fingerprint is `issue`+`code`, `path`, or `job`+`case` (`tools/sensors/sensors.json:250` and the rows' own
declarations), so the pair is two conditions with two identities and one readable title.

## The single fix

Each of the three sites composes the title from the fields the finding already carries, so the title is a
function of the condition and cannot be shared by two conditions of the row:

| site | was | becomes |
|---|---|---|
| `tools/sensors/metrics/metadata-invariant.sh:70` | `an item's own record and its label disagree` | `#${issue}: ${code}` |
| `tools/sensors/metrics/source-size.sh:59` | `the source file is past the size bound` | `${path}: ${lines} lines` |
| `tools/sensors/conditions/broken-main.sh:47` | `the main build is red` | `the build is red: ${job}/${case_name}` |

No fingerprint field, threshold, comparison, measurement or body changes; `sensors_file`'s
`${id}: ${measurement}` fallback stays and is not relied on (it is identical across two conditions of the
drift row, whose measurement is the constant `"drifted"` — which is why the fix names the fields rather than
dropping the title).

The title's *source* is unchanged: a row may pass one, and the case asserts what a `gh issue create` carried,
not what a function returned.

## Blast radius

- Three title literals; the finding JSON's `title` field changes, and with it the issue title the next filing
  of that condition carries. A condition already filed keeps its title — the tracker's items are history, and
  no case or engine path reads a title to find an identity (the reverse lookup matches the body's
  `<!-- fingerprint: … -->` marker, `tools/sensors/lib.sh:466-469`).
- One new test helper (`create_titles`) and three new cases; `SENSOR_CASES` and the dispatch gain three lines
  each.
- The row record's own title (`run.sh:329`, `sensor record: <id>`) and the filing summary's
  (`run.sh:641`) are untouched: those are one item per row and per run, so a constant title cannot collide.

## Rollback

Revert the four files' diff: the rows go back to their constant titles and the three cases go back to red,
which is the state the base commit is in. Nothing persisted depends on the title, so a revert needs no
migration.

## Alternatives the evidence eliminated

- **A — fix only the drift row.** The owner's census (400 filed items, 2026-11-26) found 12 items titled *the
  main build is red* and 2 titled *the source file is past the size bound*, against 2 carrying the drift
  title: the drift row is the smallest of the three occurrences, not the whole finding.
- **B — collapse the break into one item naming both issues.** Reverses
  `openspec/specs/backlog-sensors/spec.md:527-533` and breaks the one-fingerprint→one-issue reverse lookup
  (`tools/sensors/lib.sh:466-469`, used at `run.sh:486` and `:570`), so the second condition becomes
  unfindable and is filed again; and closing a two-condition item on one condition's clear contradicts
  `guidelines/Backlog.md:158` (I6). The fingerprint is right; the title was the defect.
- **C — close as by-design.** The pair recurs (`#1019` closed at fingerprint `c10d41fc…`, `#1086` opened at
  `52ba4e24…` with the identical title), so leaving it files the next pair too.
- **Passing no title at all.** `run.sh:276`'s fallback is `${id}: ${measurement}`, and `metadata-invariant`'s
  measurement is the constant `drifted`, so the fallback is the same collision one line further on.
