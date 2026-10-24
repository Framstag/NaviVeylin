# Tasks — fix-priority-marker-read-own-line

## 1. Red case (fails on HEAD)

- [x] 1.1 Run the probe on HEAD and keep it: `bash red-probe-head.sh lib-head-293.sh` → `RED: 5 of 6 checks
      failed`, saved as `evidence/red-probe-head.log` and copied into the work package's evidence directory.
      Verification: the log's five `RED` and one `ok` line.
- [x] 1.2 Add `tools/issues/fixtures/metadata/quoted-grammar.json`: one item whose body quotes the marker
      grammar in an inline code span **and** in a fenced span, carries `prio/p2` and no marker of its own.
      Verification: on HEAD the check reports `band-mismatch` (the quotation's `p3` read as the record) — the
      misread this change removes.
- [x] 1.3 Add `check#aQuotationIsNotAMarker` and `check#aMarkerLineBesideAQuotationIsTheRecord` to
      `tools/issues/check-metadata-selftest.sh` (its `CASES` roster and both dispatch arms included) and
      `prioritise#theWriteKeepsAQuotation` to `tools/issue-tooling-cases.sh` (its shell-case list, its dispatch
      arm, and the check's delegated names). Verification: each case fails on HEAD — `bash
      tools/issue-tooling-selftest.sh --case check#aQuotationIsNotAMarker` and the other two names, with the
      same lib installed from HEAD — and the failure of each is the misread, not a missing helper
      (`evidence/red-cases-head.log`).

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
- [x] 3.3 Falsify each clause of the shape: mutate `lib.sh` once per clause, the named case must fail, restore,
      green again (`evidence/revert-check.log`). The column-0 clause is falsified on a copy of `lib.sh` outside
      the tree instead (`evidence/revert-check-anchor.log`), because no case covers a prose line that ends at
      the close — recorded in `evidence/evidence.md` as the change's one residual, whose follow-up is one
      fixture line and one case.

## 4. Gate, evidence, archive

- [x] 4.1 Run the project's gate **once**, from the change root: `./tools/loop/gate.sh` → `524 cases · 0
      failures · 0 errors` over sixteen suites, its tallies copied into `evidence/gate/`.
- [x] 4.2 Check the pointers mechanically:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh <change> --root .` → `evidence-check: PASS`.
- [x] 4.3 Archive the change; confirm `openspec/changes/archive/2026-10-24-fix-priority-marker-read-own-line/`
      exists and that `openspec/specs/issue-priority/spec.md` carries *The marker is read where the pass writes
      it*. Verification: `openspec validate issue-priority --type spec` and the merged requirement's presence.
