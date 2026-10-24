# Proposal — fix-priority-marker-read-own-line

## Why

`issues_prio_marker` (`tools/issues/lib.sh:349`) takes the **first substring anywhere in the body** that
looks like the marker:

```sh
printf '%s' "${1:-}" | grep -o "${ISSUES_PRIO_MARKER_OPEN}[^>]*${ISSUES_PRIO_MARKER_CLOSE}" | head -1
```

so an item whose prose *quotes the grammar* — an inline code span, a fenced example, a review comment pasted
in — is read as carrying a marker it does not have, or a different band than the one it records. Item #292 was
refused by `tools/issues/check-metadata.sh --live` with three violations naming itself (`marker-incomplete`,
`marker-without-label`, `score-not-a-number`) because its body documented the grammar to describe the
violation it reported; rewording the prose cleared all three and the same run then printed
`291 issue(s) checked · 0 violation(s)`.

**Root cause (one).** The reader searches the body for the marker's delimiters instead of for the line the
pass writes, so a body's prose decides whether the item carries a marker — and `issues_body_with_marker`
(`tools/issues/lib.sh:618`) makes the same search when it *writes*, so a re-scored item whose body quotes the
grammar has the quotation's line replaced by the marker and loses that prose.

**Red case — taken on HEAD `f128f320` with a clean tree, 2026-10-24** (`evidence/red-probe-head.log`,
`evidence/red-probe-head.sh`, run as `bash red-probe-head.sh tools/issues/lib.sh`):

```
RED  an inline quotation carries no marker: expected [] got [<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->]
RED  a fenced quotation carries no marker: expected [] got [<!-- prio: band=p3 … -->]
RED  a real marker beside a quotation is the record: expected [p2] got [p3]
RED  the writer keeps the quoted prose: expected [1] got [0]
ok   and the writer adds exactly one marker line
RED  the digest keeps the quotation: expected [different] got [same]
RED: 5 of 6 checks failed
```

The probe is the smallest case that exercises the defect: it calls the three helpers the pass reads and writes
through, with a body that quotes the grammar inline and in a fence, one that carries a real marker beside a
quotation, and one whose quotation is edited (the digest must move, because a quotation is part of the claim).

The same defect in the suites, as the three cases this change adds, run on HEAD against the same lib
(`evidence/red-cases-head.log`): `check#aQuotationIsNotAMarker` fails on `band-mismatch: marker=p3
label=prio/p2`, `check#aMarkerLineBesideAQuotationIsTheRecord` fails on the same verdict and on the reader
returning `p3`, and `prioritise#theWriteKeepsAQuotation` fails with one of the two quotations destroyed.

**The owning requirement.** `openspec/specs/issue-priority/spec.md` — "An item's priority is stored, in a
fixed range", whose scenario *The label and the record agree* is the invariant the parser is the whole
authority for. The guideline section is `guidelines/Backlog.md` §4 "The invariants" (its row *a band label
**and** a body marker, written together by one script* — `guidelines/Backlog.md:740`) and §9 "Checks" (the
check that reports the disagreement, `guidelines/Backlog.md:568`).

**The surface, counted** (`grep -rln 'issues_prio_marker\|issues_body_hash\|issues_body_with_marker' tools .pi
openspec guidelines` → 5 files, all of them the tooling's own):

```
surface: 3 cases added, 0 rewritten, 1 fixture added, 1 function added, 3 functions rewritten, 5 roster
entries extended — 5 files hold the shape (tools/issues/{lib.sh,check-metadata.sh,prioritise.sh,
metadata-mutations.sh}, tools/issue-tooling-cases.sh); command: grep -rln 'issues_prio_marker\|issues_body_hash\|issues_body_with_marker'
tools .pi openspec guidelines
```

No skill under `.pi/skills/` states the shape (they name the marker, not where it stands), so no skill text
moves; the rule's one home stays `tools/issues/lib.sh`'s grammar comment, which this change extends.

## What Changes

- **`tools/issues/lib.sh`** — one predicate (`issues_prio_marker_line`) replaces the delimiter search, and the
  three readers of the marker go through it: `issues_prio_marker` returns the marker's own line or nothing,
  `issues_body_hash` strips that line and not a quotation, `issues_body_with_marker` replaces that line (or
  appends when there is none) and leaves every quotation intact.
- **`tools/issues/fixtures/metadata/quoted-grammar.json`** (new) — an item whose body quotes the marker
  grammar inline *and* in a fenced span, carries the `prio/p2` label and no marker of its own.
- **`tools/issues/check-metadata-selftest.sh`** — two cases: the quotation is not a marker
  (`check#aQuotationIsNotAMarker`), and a real marker beside a quotation is the record
  (`check#aMarkerLineBesideAQuotationIsTheRecord`).
- **`tools/issue-tooling-cases.sh`** — one case, `prioritise#theWriteKeepsAQuotation` (the pass composes a
  body for an item that quotes the grammar: the quotation survives and exactly one marker line is written),
  plus the five roster entries that dispatch the three cases (the shell-case list, the dispatch arm, and the
  check's delegated names).

**Additive, no breaking change.** The shipped shape of the marker is unchanged — the pass writes the same
line at column 0, so every body that carries a marker today is read exactly as before, and the digest of a
body that carries one is byte-identical. Rollback: revert this change's commit — the three helpers return to
the delimiter search and the red probe reproduces 5 of 6 red.

**Not a native/JNI change, not an app change**: no `:app` source, no Kotlin, no resource, no build logic.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `issue-priority` — one added requirement, *The marker is read where the pass writes it*, with three
  scenarios (`specs/issue-priority/spec.md`). No shipped scenario is modified or removed, so the delta is
  `ADDED` only.

## Impact

Production sources (tooling)
- `tools/issues/lib.sh` — `issues_prio_marker_line` (new), `issues_prio_marker`, `issues_body_hash`,
  `issues_body_with_marker` (rewritten), the grammar comment extended

Test sources (tooling)
- `tools/issues/check-metadata-selftest.sh` — two cases + their roster
- `tools/issue-tooling-cases.sh` — one case, its roster entry and its dispatch entry
- `tools/issues/fixtures/metadata/quoted-grammar.json` — new fixture

Specs — read, and one delta
- `openspec/specs/issue-priority/spec.md` (delta), `guidelines/Backlog.md` §4 and §9 (read, not edited)

Verification (each task's own step, `tasks.md`)
- the red probe before and after (`evidence/red-probe-head.log`, `evidence/green-probe-head.log`)
- `bash tools/issue-tooling-selftest.sh --case check#aQuotationIsNotAMarker` (and the other two cases)
- `./tools/loop/gate.sh` once — the profile maps `tools/issues/*` and `tools/issue-tooling-cases.sh` to
  `tools/issue-tooling-selftest.sh`, `tools/issues/check-metadata.sh`,
  `tools/issues/metadata-mutations.sh --list`, and `openspec/*` to the hygiene, doc-route and
  classification checks; no compiled path changes, so the build is not run
