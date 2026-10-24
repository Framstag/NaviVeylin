# Tasks — fix-priority-marker-read-own-line

## 1. Red case (fails on HEAD)

- [x] 1.1 Run the probe on HEAD and keep it: `bash red-probe-head.sh lib-head-293.sh` → `RED: 7 of 8 checks
      failed`, saved as `evidence/red-probe-head.log` and copied into the work package's evidence directory.
      Verification: the log's seven `RED` lines, its one `ok` line and its parser-answer line.
- [x] 1.2 Add `tools/issues/fixtures/metadata/quoted-grammar.json`: one item whose body quotes the marker
      grammar in an inline code span, in a fenced span, on a prose line that ends at the close and on a line
      that starts at the open but continues past it, carries `prio/p2` and no marker of its own. Verification:
      on HEAD the check reports `band-mismatch` (the quotation's `p3` read as the record) — the misread this
      change removes — and `check#aQuotationIsNotAMarker`'s two anchor assertions fail on a tree without the
      predicate (`evidence/red-cases-head.log`).
- [x] 1.3 Add `check#aQuotationIsNotAMarker` and `check#aMarkerLineBesideAQuotationIsTheRecord` to
      `tools/issues/check-metadata-selftest.sh` (its `CASES` roster and both dispatch arms included) and
      `prioritise#theWriteKeepsAQuotation` to `tools/issue-tooling-cases.sh` (its shell-case list, its dispatch
      arm, and the check's delegated names). Verification: each case fails on HEAD — `bash
      tools/issue-tooling-selftest.sh --case check#aQuotationIsNotAMarker` and the other two names, with the
      same lib installed from HEAD — two of each case's assertions failing on the misread, and the two anchor
      assertions failing through the absent predicate (`expected [] got [missing]`), which is the branch the
      case was given in round 2 so that a tree without `issues_prio_marker_line` cannot read as green
      (`evidence/red-cases-head.log`, 8 failed assertions over the three cases).

## 2. The parser reads the marker's line

- [x] 2.1 Add `issues_prio_marker_line` to `tools/issues/lib.sh`: the 1-based line number of the marker, the
      line starting at column 0 and ending at the close delimiter, outside a fenced span. Verification:
      `bash red-probe-head.sh tools/issues/lib.sh` goes green on its first three checks.
- [x] 2.2 Route `issues_prio_marker` through it (that line, or nothing). Verification: check 1 and 2 of the
      probe; `check#aQuotationIsNotAMarker` passes.
- [x] 2.3 Route `issues_body_with_marker` through it: replace that line, append when there is none, keep every
      quotation. Verification: check 4 and 5 of the probe; `prioritise#theWriteKeepsAQuotation` passes.
- [x] 2.4 Route `issues_body_hash` through it: drop that line and keep a quotation, so the digest covers what
      the item claims. Verification: the probe's sixth check (the digest differs when the quotation is edited)
      plus `prioritise#secondRunScoresNothing` and `basis#stableAcrossWhitespaceOnlyBodyEdit` still passing.
- [x] 2.5 Extend the grammar comment above the constants with the shape this change fixes: the marker is a line
      of its own at column 0, and a fenced span is prose. Verification: `tools/issues/check-metadata.sh`
      (self mode) stays green — no engine script spells the delimiter outside `lib.sh`.

## 3. The suites

- [x] 3.1 Run the three new cases green: `bash tools/issue-tooling-selftest.sh --case <name>` for each, and
      `bash tools/issues/check-metadata-selftest.sh --case <name>` for the two check cases. Verification: each
      prints `PASS` (`evidence/green-cases.log`).
- [x] 3.2 Run the two suites whole: `bash tools/issue-tooling-selftest.sh` (121 cases) and
      `bash tools/issues/check-metadata-selftest.sh` (9 cases). Verification: `evidence/issue-tooling-suite.log`
      and `evidence/check-metadata-suite.log`, each ending `all N cases passed`.
- [x] 3.3 Falsify each clause of the shape **in the tree**: mutate `lib.sh` once per clause, the named case must
      fail, restore, green again — four clauses, four mutations, each noticed (`evidence/revert-check.log`,
      `evidence/revert-check-293.sh`): the column-0 anchor and the end anchor by
      `check#aQuotationIsNotAMarker`, the fence by `check#aMarkerLineBesideAQuotationIsTheRecord`, and the write
      path's `NR == n` arm by `prioritise#theWriteKeepsAQuotation`. No clause is left to a probe outside the
      tree (round 1's gap, `evidence/review-round-1-requested.md` finding 1), and the script compares the
      restored file byte for byte. The shipped log is the run on the tree as archived
      (`2026-10-24T23:18:47Z`–`23:18:56Z`, four mutations noticed, `REVERT-CHECK: PASS`); it replaced the
      round-1 two-mutation log that recorded mutation 1 as not noticed.

## 4. Gate, evidence, archive

- [x] 4.1 Run the project's gate **once**, from the change root: `./tools/loop/gate.sh` → `524 cases · 0
      failures · 0 errors` over sixteen suites, its tallies copied into `evidence/gate/`.
- [x] 4.2 Check the pointers mechanically:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/archive/2026-10-24-fix-priority-marker-read-own-line --root .`
      → `evidence-check: PASS` (`evidence/evidence-check.log`).
- [x] 4.3 Archive the change; confirm `openspec/changes/archive/2026-10-24-fix-priority-marker-read-own-line/`
      exists and that `openspec/specs/issue-priority/spec.md` carries *The marker is read where the pass writes
      it*. Verification: `openspec validate issue-priority --type spec` and the merged requirement's presence.

## 5. Round 2 — the reviewer's blocking finding (`evidence/review-round-1-requested.md`)

- [x] 5.1 Make the column-0 clause and the end anchor falsifiable by a case the gate runs: the fixture gains a
      prose line that ends at the close and one that starts at the open but continues past it, and
      `check#aQuotationIsNotAMarker` asserts both through the reader (answering `missing` when
      `issues_prio_marker_line` is absent, so a tree without it cannot read as green). Verification:
      `revert-check-293.sh` mutations 1 and 2 are noticed by that case, and the case is green on the tree
      (`evidence/green-cases.log`).
- [x] 5.2 Give the write path's replace arm a case: `prioritise#theWriteKeepsAQuotation` keeps its append half
      and gains the half whose body already carries a stored marker line, so `NR == n` is held by a case.
      Verification: mutation 4 is noticed (`evidence/revert-check.log`); both halves green.
- [x] 5.3 State the bound where a person reads it: `guidelines/Backlog.md` §4's invariants row now says the
      marker is a line of its own at column 0, closing at its end, outside a fenced span. Verification:
      `tools/check-doc-routes.sh` and `tools/check-openspec-hygiene.sh` stay green.
- [x] 5.4 Re-run the evidence and the gate after the corrections: `red-cases-293.sh` (red on the base commit,
      green on the tree), the two suites whole (121 and 9 cases), `./tools/loop/gate.sh` once, and
      `evidence-check.sh` → `PASS`. Verification: `evidence/red-cases-head.log`, `evidence/green-cases.log`,
      `evidence/issue-tooling-suite.log`, `evidence/check-metadata-suite.log`, `evidence/gate/`,
      `evidence/evidence-check.log`.
