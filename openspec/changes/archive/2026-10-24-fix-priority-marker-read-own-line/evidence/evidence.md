# Evidence — fix-priority-marker-read-own-line (issue #293)

Every number below was taken in this tree, at the paths named, on 2026-10-24 (UTC timestamps, the log's own).
Each half is produced by a script kept beside it, so a later reader re-runs it instead of trusting it: the red and
green halves by `red-cases-293.sh`, the falsification by `revert-check-293.sh`.

## The defect, measured on HEAD `f128f320d59dd1763ebb4354ec1a3274ccd895b4`

`red-probe-head.log` — `bash red-probe-head.sh …/evidence/lib-head-293.sh` (`lib-head-293.sh` is
`git show f128f320:tools/issues/lib.sh`, kept here so the run is repeatable), 7 of 8 checks red:

```
RED  an inline quotation carries no marker: expected [] got [<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->]
RED  a fenced quotation carries no marker: expected [] got [<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->]
RED  a real marker beside a quotation is the record: expected [p2] got [p3]
RED  a prose line that ends at the close carries no marker: expected [] got [<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->]
RED  a line that starts at the open but does not close carries no marker: expected [] got [<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->]
RED  the writer keeps the quoted prose: expected [1] got [0]
ok   and the writer adds exactly one marker line
RED  the digest keeps the quotation: expected [different] got [same]
--- the parser's answer for the fenced body: '<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->'
RED: 7 of 8 checks failed
```

`red-cases-head.log` — the three cases this change adds, run against the same HEAD lib (8 failed assertions
across the three cases, three `exit=1`): two assertions of each case fail on the misread, and the two anchor
assertions fail through the predicate being absent (`expected [] got [missing]`) — the branch the case gained in
round 2 on purpose, so a tree without `issues_prio_marker_line` cannot read as green:

```
  FAIL and the missing record is what is reported: [label-without-marker] not found in: #31 band-mismatch: marker=p3 label=prio/p2
  FAIL the quotation is not matched against the label: [band-mismatch] should not appear in: #31 band-mismatch: marker=p3 label=prio/p2
  FAIL the prose line that ends at the close is not the record: expected [] got [missing]
  FAIL and the line that starts at the open but does not close is not one either: expected [] got [<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->]
  FAIL a marker line beside a quotation exits 0: expected [0] got [1]
  FAIL and nothing is reported: [0 violation(s)] not found in: #31 band-mismatch: marker=p3 label=prio/p2
  FAIL the reader takes the marker's own line: expected [p2] got [p3]
  and both quotations survive the write: expected '2', got '1'
FAIL prioritise#theWriteKeepsAQuotation
```

`green-probe-head.log` (`green: all 8 checks passed`) and `green-cases.log` (three `PASS`) are the same script's
other half: `RED-CASES: PASS — red on f128f320…, green on the tree (2026-10-24T22:51:09Z)`.

The two anchor lines the probe and the fixture gained in round 2 are what `check#aQuotationIsNotAMarker` now
asserts directly: on HEAD the predicate is absent, so the case answers `missing` instead of an empty read, and a
prose line that ends at the close is returned as the record.

## The suites

`check-metadata-suite.log` — `bash tools/issues/check-metadata-selftest.sh`:
`check-metadata-selftest: all 9 cases passed (offline, no token, no device)`.

`issue-tooling-suite.log` — `bash tools/issue-tooling-selftest.sh`, run before the gate, every case named on its
own `PASS` line: `all 121 cases passed (JDK 27, offline, no token, HEAD 8d19f7fd, tools 956ae28b1603,
2026-10-24T22:51:00Z)`. The gate then ran the same 121 cases on the tree as archived; its tally,
`gate/tools_issue-tooling-selftest.sh.xml`, carries `tests="121" failures="0" errors="0" time="54"` and a
system-out of `… HEAD 8d19f7fd, tools 14fc57687cd5, 2026-10-24T23:15:02Z` whose stdout is only a tail (`PASS
check#unrankedIsNotAViolation`, `PASS check#selfCheckIsGreen`, the summary line), so the tally proves the count,
the duration and the stamp while the case names come from the pre-gate log. The two `tools` fields differ because
that field hashes `git status --porcelain -- tools` and `git diff -- tools`
(`tools/issue-tooling-selftest.sh:122-123`), not the file contents, so it moves with the index while `HEAD` stays
`8d19f7fd`.

## The gate, run once

`./tools/loop/gate.sh` from the change root exited 0 after the corrections:

```
LOOP-GATE: 524 cases · 0 failures · 0 errors
  ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache (224s)
  tools/loop-selftest.sh --from-git db888dd8b7c509cfba82854a21427ae5c9c8e192 (974s)
  bash tools/loop-mutations.sh --check (1s)
  tools/loop-mutations-selftest.sh (136s)
  tools/issue-tooling-selftest.sh (54s)
  tools/issues/check-metadata.sh (1s)
  tools/issues/metadata-mutations.sh --list (1s)
  tools/check-compose-sandbox-rule.sh (0s)
  tools/check-compose-sandbox-rule-selftest.sh (0s)
  bash tools/gen-feature-list.sh --check-classification (4s)
  tools/check-doc-routes.sh (0s)
  tools/check-doc-routes-selftest.sh (1s)
  tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list (0s)
  tools/check-todo-references.sh (1s)
  tools/check-openspec-hygiene.sh (0s)
  tools/check-openspec-hygiene-selftest.sh (0s)
```

The gate's case count is its own aggregate over the shell suites it ran (`524` = 145 + 6 + 121 + 34 + 218), and
no single tally file carries it; the build's own tally records zero cases because a Gradle run prints no `all N
cases passed` line. One tally per suite sits in `gate/`, among them `gate/tools_issue-tooling-selftest.sh.xml`
(`tests="121" failures="0" errors="0"`, its system-out stamped `2026-10-24T23:15:02Z`), which carries this
change's cases; every changed path is a shell path, so the shell suites are the delta's coverage.

## Falsification — each clause of the shape, mutated once, in the tree

`revert-check.log` — `bash revert-check-293.sh .`, four clauses, each mutated in `tools/issues/lib.sh` and
restored, with the case that holds it named on the line. The log below is the run on the tree as archived
(`2026-10-24T23:18:47Z`–`23:18:56Z`, after the round-2 fixture edit at `22:46:35Z`); it replaces the round-1
two-mutation log an earlier revision of this file shipped, which recorded mutation 1 (`index(t, mo) == 1` →
`index(t, mo) > 0`) as **not** noticed. The script exits 0, and the file it mutated is compared with the copy
taken before mutation 1 at the end:

```
== mutation 1 — the column-0 anchor
   index(t, mo) == 1                                          →  index(t, mo) > 0
  noticed by check#aQuotationIsNotAMarker: the mutated clause makes it fail  (2026-10-24T23:18:47Z)
  quiet again: check#aQuotationIsNotAMarker passes on the restored tree  (2026-10-24T23:18:49Z)
== mutation 2 — the end anchor
   substr(t, length(t) - length(mc) + 1) == mc                →  1 == 1
  noticed by check#aQuotationIsNotAMarker: the mutated clause makes it fail  (2026-10-24T23:18:49Z)
  quiet again: check#aQuotationIsNotAMarker passes on the restored tree  (2026-10-24T23:18:50Z)
== mutation 3 — the fence
   if (infence) next                                          →  if (0) next
  noticed by check#aMarkerLineBesideAQuotationIsTheRecord: the mutated clause makes it fail  (2026-10-24T23:18:50Z)
  quiet again: check#aMarkerLineBesideAQuotationIsTheRecord passes on the restored tree  (2026-10-24T23:18:51Z)
== mutation 4 — the write path's line
   NR == n { print marker; next }                             →  NR == 0 { print marker; next }
  noticed by prioritise#theWriteKeepsAQuotation: the mutated clause makes it fail  (2026-10-24T23:18:51Z)
  quiet again: prioritise#theWriteKeepsAQuotation passes on the restored tree  (2026-10-24T23:18:56Z)
== the tree is the change's own again
  lib.sh restored byte for byte  (2026-10-24T23:18:56Z)
REVERT-CHECK: PASS — four clauses, each noticed by the case that holds it  (2026-10-24T23:18:56Z)
```

`revert-check-anchor.log` is round 1's probe against a copy of `lib.sh` outside the tree. It is kept as history,
not as evidence for a clause: round 1 shipped mutations 1 and 2 held by nothing the gate runs, and round 2
replaced that probe with the fixture line and the assertions the log above shows. No residual is carried from
it.

## The change, after the corrections

`tools/issues/lib.sh` (the predicate `issues_prio_marker_line` added; `issues_prio_marker`, `issues_body_hash`,
`issues_body_with_marker` routed through it),
`tools/issues/check-metadata-selftest.sh` (two cases, the first asserting both anchor clauses),
`tools/issue-tooling-cases.sh` (one case, its append and replace halves),
`tools/issues/fixtures/metadata/quoted-grammar.json` (new: four prose shapes, one per anchor clause and one per
quotation shape), `guidelines/Backlog.md` §4's invariants row (where the marker stands), the spec delta and this
change's artifacts.
