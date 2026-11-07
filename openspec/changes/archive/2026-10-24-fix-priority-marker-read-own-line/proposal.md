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

The probe is the smallest case that exercises the defect: it calls the three helpers the pass reads and writes
through, with a body that quotes the grammar inline and in a fence, one that carries a real marker beside a
quotation, one whose prose line *ends* at the close and one whose line *starts* at the open but continues past
it (the shape #292's own body has), and one whose quotation is edited (the digest must move, because a quotation
is part of the claim).

The same defect in the suites, as the three cases this change adds, run on HEAD against the same lib
(`evidence/red-cases-head.log`): `check#aQuotationIsNotAMarker` fails on `band-mismatch: marker=p3
label=prio/p2`, on the same verdict not being `label-without-marker`, and on both anchor assertions (the reader
missing, and the quotation read as the record); `check#aMarkerLineBesideAQuotationIsTheRecord` fails on the same
verdict and on the reader returning `p3`; and `prioritise#theWriteKeepsAQuotation` fails with one of the two
quotations destroyed.

**Round 2 — the reviewer's blocking finding, and what it changed.** The first round's three cases exercised the
shape through a *fenced* quotation and an *inline* one, so neither anchor clause of `issues_prio_marker_line`
was held by anything the gate runs: mutating `index(t, mo) == 1` to `index(t, mo) > 0` left every case green
(`evidence/review-round-1-requested.md`, finding 1). The fixture now carries one prose line per anchor — a line
that *ends* at the close, and a line that *starts* at the open but continues past it — `check#aQuotationIsNotAMarker`
asserts both through the reader (and answers `missing` rather than nothing when the predicate is absent, so a
tree without it cannot read as green), and `prioritise#theWriteKeepsAQuotation` grew the *replace* half: a body
whose stored marker line is the one line the write may change. All four clauses are now mutated in the tree, one
at a time, and each mutation makes a named case fail (`evidence/revert-check.log`,
`evidence/revert-check-293.sh`). The shipped requirement's wording gained what the code has always done — trailing
whitespace on the marker's line does not stop it being the record.

**The owning requirement.** `openspec/specs/issue-priority/spec.md` — "An item's priority is stored, in a
fixed range", whose scenario *The label and the record agree* is the invariant the parser is the whole
authority for. The guideline section is `guidelines/Backlog.md` §4 "The invariants" (its row *a band label
**and** a body marker, written together by one script* — `guidelines/Backlog.md:740`) and §9 "Checks" (the
check that reports the disagreement, `guidelines/Backlog.md:568`).

**The surface, counted** (`grep -rln 'issues_prio_marker\|issues_body_hash\|issues_body_with_marker' tools .pi
openspec guidelines` → 5 files, all of them the tooling's own):

```
surface: 3 cases added, 3 extended in round 2, 1 fixture added, 1 function added, 3 functions rewritten, 5 roster
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
  grammar inline *and* in a fenced span, carries a prose line that ends at the close and one that starts at the
  open but continues past it, carries the `prio/p2` label and no marker of its own.
- **`tools/issues/check-metadata-selftest.sh`** — two cases: the quotation is not a marker
  (`check#aQuotationIsNotAMarker`, which also asserts both anchor clauses through the reader), and a real marker
  beside a quotation is the record (`check#aMarkerLineBesideAQuotationIsTheRecord`).
- **`tools/issue-tooling-cases.sh`** — one case, `prioritise#theWriteKeepsAQuotation` (the pass composes a
  body for an item that quotes the grammar: the quotation survives and exactly one marker line is written, on
  the append arm and on the replace arm), plus the five roster entries that dispatch the three cases (the
  shell-case list, the dispatch arm, and the check's delegated names).
- **`guidelines/Backlog.md`** §4 — the invariants row now says where the marker stands (a line of its own at
  column 0, closing at its end, outside a fenced span), because that is the place a person reads the rule.

**Additive, no breaking change.** The shipped shape of the marker is unchanged — the pass writes the same
line at column 0, so a body that carries a marker today is read exactly as before and its digest is unchanged:
by construction, because the reader keeps the marker text and drops the same line the old search left behind once
the delimiter span was removed; no live body was recomputed (the corpus needs a token). Rollback: revert this
change's commit — the three helpers return to the delimiter search and the red probe reproduces 7 of 8 red.

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
- `openspec/specs/issue-priority/spec.md` (delta), `guidelines/Backlog.md` §4 (the invariants row, extended
  with where the marker stands) and §9 (read, not edited)

Verification (each task's own step, `tasks.md`)
- the red probe and the three cases before and after (`evidence/red-probe-head.log`,
  `evidence/green-probe-head.log`, `evidence/red-cases-head.log`, `evidence/green-cases.log`), both halves
  produced by one re-runnable script (`evidence/red-cases-293.sh`)
- the shape's four clauses mutated one at a time in the tree, each noticed by a named case, restore verified
  byte for byte (`evidence/revert-check.log`, `evidence/revert-check-293.sh`)
- `./tools/loop/gate.sh` once — the profile maps `tools/issues/*` and `tools/issue-tooling-cases.sh` to
  `tools/issue-tooling-selftest.sh`, `tools/issues/check-metadata.sh`,
  `tools/issues/metadata-mutations.sh --list`, and `openspec/*` to the hygiene, doc-route and
  classification checks; no compiled path changes, so the build is not run
