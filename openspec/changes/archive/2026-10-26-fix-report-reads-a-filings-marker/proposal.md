# Proposal — fix-report-reads-a-filings-marker

## Why

One defect, verified on this tree, issue #668: **the report reads measurement markers out of comments only, so a
row whose newest event is a filing reports that it never ran — and the baseline the next run is handed is empty
for exactly that row.**

**Root cause (one).** The marker a run posts lives in two places, and only one of them is read. A run that
records a finding on an existing item writes it as a comment (`sensors_record`), and a run that *files* writes
the same marker into the new issue's body (`tools/sensors/run.sh:231`, `sensors_issue_body` →
`sensors_marker`). Both readers of "the newest measurement a row carries" walk `(.comments // [])` and nothing
else: `sensors_last_marker` (`tools/sensors/lib.sh:488` on the base), which `sensors_run_row` takes the baseline
from (`tools/sensors/run.sh:560`), and the report's own inline jq (`tools/sensors/report.sh:48`), which produces
the row's `last`, `verdict` and `measurement`. A filing therefore leaves no marker in the place either reader
looks, and the row is reported — and compared — as one with no measurement at all.

**Verified, not quoted from the tracker.** The two new cases fail on the base commit `a0b2a92f` with the
symptom the tracker describes (`evidence/red-on-head-report.log`, `evidence/red-on-head-run.log`):

```
$ bash tools/sensor-selftest.sh --case 'report#aFiledRowIsReported'
row=fixture-emit last=never verdict=- measurement=- issue=- clear=0 violation=0 occurrences=0 trend=-
stale row=fixture-emit no measurement on record — the row has never run or never filed
FAIL report#aFiledRowIsReported

$ bash tools/sensor-selftest.sh --case 'run#aFilingSetsTheBaseline'
build-duration: … verdict=clear measurement=542000 issue=121 action=commented (hysteresis)
FAIL run#aFilingSetsTheBaseline
```

The first is the report's `last=never` line printed for a row whose only issue was filed with a marker in its
body; the second is the row comparing 542000 ms against *no* baseline (a clear) instead of against the 100000
the filing's marker carries (a violation at 1.5×).

**The report's own field list drops the instant.** `sensors_issues_of_row` asks `gh issue list` for
`number,title,body,labels,state,stateReason,author,comments` — no `createdAt` — so even a reader that looked in
the body would have no instant to sort it by. A filing's marker has no `createdAt` of its own: the issue and its
marker are one write, so the issue's `createdAt` is the marker's.

**Surface, counted before the plan (`grep -rn 'comments // \[\]' tools/sensors`,
`grep -cE "^  '8\.[0-9]+@" tools/sensor-mutations.sh`).** Two readers walk comments only
(`tools/sensors/lib.sh:488`, `tools/sensors/report.sh:48`); `sensors_last_marker` has one caller
(`tools/sensors/run.sh:560`); five existing cases name a `last=` field and three of them build their fixtures
from `marker_comment`; the mutation harness holds 19 records. The plan this count sets the estimate against is
the `surface:` line below.

surface: 2 readers rewritten (tools/sensors/lib.sh, tools/sensors/report.sh), 2 cases added, 0 cases rewritten, 3 existing scenarios given their case, 1 fixture helper extended (issue_record, no fixture file), 2 mutation records added (8.20, 8.21) — `grep -rn 'comments // \[\]' tools/sensors` (2 on the base), `grep -cE "^  '8\.[0-9]+@" tools/sensor-mutations.sh` (19 on the base)

**The two candidate repairs the tracker offers.** Reading body markers as well as comments makes `last` mean
"the newest marker anywhere", which is what the report and the baseline both ask (first candidate). Splitting the
field into `last` and `last-comment` (second) adds a distinction the defect does not need: a report that prints
the newest marker of either kind already separates "ran" from "never ran", and the reader's question — when did
this row last run or file, and with which number — is answered by that one field. The first candidate is taken;
the second is not a fork left open but an addition nothing in the evidence asks for.

## What changes

- `tools/sensors/lib.sh` — one helper, `sensors_markers_json`, yields every marker a row's issues carry (each
  comment's, and the marker in a filing's own body dated by the issue's `createdAt`), oldest first;
  `sensors_last_marker` returns the newest of them.
- `tools/sensors/report.sh` — its inline marker walk is replaced by that helper, so the report and the baseline
  read one timeline instead of two.
- `tools/sensors/lib.sh` — `sensors_issues_of_row` asks for `createdAt`, without which a body marker has no
  instant.
- `tools/sensor-cases.sh`, `tools/sensor-mutations.sh` — the two cases and the two falsification records.

Non-goals: the report's `last-comment` field (nothing asks for it), the age-based `stale` line, and every other
row's fixture.
