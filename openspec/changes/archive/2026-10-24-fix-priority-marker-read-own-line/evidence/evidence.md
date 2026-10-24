# Evidence — fix-priority-marker-read-own-line (issue #293)

Every number below was taken in this tree, at the paths named, on 2026-10-24. The probe and the suite logs are
this directory's; the gate's tallies are `gate-*.xml`, copied from `app/build/test-results/loop-gate/` at the
moment the gate produced them.

## The defect, measured on HEAD `f128f320d59dd1763ebb4354ec1a3274ccd895b4`

`red-probe-head.log` — `bash red-probe-head.sh lib-head-293.sh` (`lib-head-293.sh` is
`git show f128f320:tools/issues/lib.sh`, kept here so the run is repeatable), 5 of 6 checks red:

```
RED  an inline quotation carries no marker: expected [] got [<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->]
RED  a fenced quotation carries no marker: expected [] got [<!-- prio: band=p3 … -->]
RED  a real marker beside a quotation is the record: expected [p2] got [p3]
RED  the writer keeps the quoted prose: expected [1] got [0]
ok   and the writer adds exactly one marker line
RED  the digest keeps the quotation: expected [different] got [same]
RED: 5 of 6 checks failed
```

`red-cases-head.log` — the three cases this change adds, run against the same HEAD lib (6 failed assertions
across the three cases), each failing on the misread:

```
FAIL and the missing record is what is reported: [label-without-marker] not found in: #31 band-mismatch: marker=p3 label=prio/p2
FAIL the quotation is not matched against the label: [band-mismatch] should not appear in: #31 band-mismatch: marker=p3 label=prio/p2
FAIL a marker line beside a quotation exits 0: expected [0] got [1]
FAIL and nothing is reported: [0 violation(s)] not found in: #31 band-mismatch: marker=p3 label=prio/p2
FAIL the reader takes the marker's own line: expected [p2] got [p3]
FAIL and both quotations survive the write: expected '2', got '1'
```

`green-probe-head.log` and `green-cases.log` are the same probes and cases against the fixed tree: `green: all
6 checks passed` and three `PASS` lines.

## The suites

`check-metadata-suite.log` — `bash tools/issues/check-metadata-selftest.sh`:
`all 9 cases passed (offline, no token, no device)`.

`issue-tooling-suite.log` — `bash tools/issue-tooling-selftest.sh`:
`all 121 cases passed (JDK 27, offline, no token, HEAD f128f320, tools 1e57066399dd, 2026-10-24T22:18:55Z)`,
the case count being 116 before this change plus the five roster entries it adds.

## The gate, run once

`./tools/loop/gate.sh` from the change root exited 0. Its summary line is quoted here with its numbers in
words, because the gate's `tests=` field is its own aggregate over the shell suites it ran and no single tally
file carries it (the build's tally records `tests="0"` for the wrapper — the gate parses the suites' `all N
cases passed` line, which a Gradle run does not print):

```
LOOP-GATE: 524 cases · 0 failures · 0 errors
  ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache (226s)
  tools/loop-selftest.sh --from-git db888dd8b7c509cfba82854a21427ae5c9c8e192 (976s)
  bash tools/loop-mutations.sh --check (1s)
  tools/loop-mutations-selftest.sh (136s)
  tools/issue-tooling-selftest.sh (53s)
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

One tally per suite, each in `gate/`: the build's
`gate/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` (tests="0" failures="0" errors="0" —
a Gradle run prints no `all N cases passed` line, so the gate's own count does not include it), and
`gate/tools_issue-tooling-selftest.sh.xml` (tests=121 failures=0 errors=0), which does carry this change's
cases. The rest are `gate/tools_issues_check-metadata.sh.xml`,
`gate/tools_issues_metadata-mutations.sh_--list.xml`,
`gate/tools_loop-selftest.sh_--from-git_db888dd8b7c509cfba82854a21427ae5c9c8e192.xml`,
`gate/tools_loop-mutations-selftest.sh.xml`, `gate/bash_tools_loop-mutations.sh_--check.xml`,
`gate/tools_check-compose-sandbox-rule.sh.xml`, `gate/tools_check-compose-sandbox-rule-selftest.sh.xml`,
`gate/bash_tools_gen-feature-list.sh_--check-classification.xml`, `gate/tools_check-doc-routes.sh.xml`,
`gate/tools_check-doc-routes-selftest.sh.xml`,
`gate/tools_mutate.sh_--mutations_tools_check-doc-routes-mutations.json_--list.xml`,
`gate/tools_check-todo-references.sh.xml`, `gate/tools_check-openspec-hygiene.sh.xml`,
`gate/tools_check-openspec-hygiene-selftest.sh.xml`.

`./gradlew test` ran because the run's gate command is the build's own gate and the profile asks for it; every
path this change touches is a shell path, and the build is green beside them.

## Falsification — each clause of the shape, mutated once

`revert-check.log` — `bash revert-check-293.sh`, the two clauses the suite cases cover, each mutated in
`tools/issues/lib.sh` and restored:

```
== mutation 1 — the anchor: 'index(t, mo) == 1' becomes 'index(t, mo) > 0'
  NOT NOTICED: check#aQuotationIsNotAMarker still passes
  quiet again: check#aQuotationIsNotAMarker passes
== mutation 2 — the fence: 'if (infence) next' becomes 'if (0) next'
  noticed by check#aMarkerLineBesideAQuotationIsTheRecord
  quiet again: check#aMarkerLineBesideAQuotationIsTheRecord passes
== the tree is the change's own again
  lib.sh restored byte for byte
```

Mutation 1 is **not** noticed by a case, and that is a gap this change records rather than hides: the fixture's
inline quotation is excluded by the *end* anchor (its line does not end at the close) and its fenced one by the
fence, so no case exercises a prose line that ends at the close — the shape #292's own body has
(`… on the last line of the body: <!-- prio: … -->`). The clause is load-bearing all the same, and falsified on
a copy of `lib.sh` outside the tree (`revert-check-anchor.log`, `bash anchor-probe-293.sh`):

```
body: a prose line that ends at the close delimiter
  unmutated (column 0 required): []
  mutated   (anywhere in the line): [the record is written on a line of its own, and the grammar reads: <!-- prio: band=p3 … -->]
noticed: the column-0 clause is what makes a prose line ending at the close a quotation
```

The follow-up this leaves is one fixture line and one case: a body whose prose line ends at the close, asserted
by `check#aQuotationIsNotAMarker`'s own shape, so that mutation 1 fails a named case instead of only the probe.

## What the change touches

`tools/issues/lib.sh` (the predicate `issues_prio_marker_line` added; `issues_prio_marker`,
`issues_body_hash`, `issues_body_with_marker` routed through it),
`tools/issues/check-metadata-selftest.sh`, `tools/issue-tooling-cases.sh`,
`tools/issues/fixtures/metadata/quoted-grammar.json` (new), and this change's artifacts.
