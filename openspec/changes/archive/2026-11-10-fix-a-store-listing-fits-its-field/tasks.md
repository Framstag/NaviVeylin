# Tasks

Reference: `proposal.md` for the surface and the red run, `specs/store-listing-rendering/spec.md` for the contract,
`design.md` for the two choices the tree settled.

## 1. The cases, red first

Requirement: "A store listing fits the field it is pasted into", its five scenarios.

- [x] 1.1 `tools/tests/gen-feature-list-selftest.sh` §9: a section of 16 cases over five versions (10-1-1-1 to
      10-1-1-5), a fresh fixture and the budget read from the tool's own `listing_budget` line, as the paragraph
      bound is read.
- [x] 1.2 The trim: one bullet of one long sentence fills the field, the next bullet's second sentence does not fit,
      and the case asserts the published bullet, the absent sentence, that every published bullet ends at a sentence
      boundary, and that every `<!-- cap: … -->` line is whole.
- [x] 1.3 The order: a change the shortlist does not name, listed first by the entry, is published after the
      shortlisted one.
- [x] 1.4 The overflow: two bullets that cannot both fit, and the general bullet with the count it stands for; the
      section still fits the budget.
- [x] 1.5 Re-running a version replaces its section byte for byte; a version whose changes are all internal adds no
      section and leaves the document as it was, which is a red case on HEAD because the file is absent there.
- [x] 1.6 The red run on HEAD, the production change reverted (`git show HEAD:tools/gen-feature-list.sh`): 14 of 135
      cases fail, all of them `listing:`; XML `tests="135" failures="14"` in the item's evidence directory
      (`red-on-HEAD-gen-feature-list-selftest.xml`, log beside it).

## 2. The renderer

Requirement: the same, scenarios one to four.

- [x] 2.1 `tools/feature-list/listing.awk`: reads a version entry, keeps one bullet per line with its comment lines
      under it, orders by the shortlist and then by the entry's own order, and fits the budget whole-bullet first and
      whole-sentence second.
- [x] 2.2 The summary: one general bullet naming the count it stands for, making room for itself from the end of the
      list, so a section is never over budget and a change is never dropped in silence.
- [x] 2.3 `fit_sentences` never returns a prefix that ends inside a line: the cut is a `.` that ends the text or is
      followed by a space, and a comment is carried as a whole line rather than as text.
- [x] 2.4 The renderer reports `chars`, `published`, `trimmed` and `summarised` to a status file, so the run's report
      line is read from the run and not recomputed.

## 3. The write site and the budget

Requirement: the same, its fifth scenario.

- [x] 3.1 `tools/gen-feature-list.sh`: `listing_budget=500` beside `maxpara`, with its source in the comment; the
      budget is per version.
- [x] 3.2 Section 7a after the release note is written: the shortlist specs, the renderer, one file per version under
      the work directory, this version's section replaced, the document re-joined newest first, and the document's
      header stating the budget.
- [x] 3.3 The header's trailing blanks are dropped per section, so a section a later run does not touch does not grow
      a blank line per run — the re-run case asserts the byte-identity that this keeps.
- [x] 3.4 One report line, `store listing: <path> (<n> published, <n> trimmed at a sentence boundary, <n> summarised,
      <chars>/<budget> chars)`.

## 4. What the change owes the tree

Requirement: the same.

- [x] 4.1 `tools/feature-list/specs.json`: the classification line for `store-listing-rendering` (internal area, no
      surface), which §9 of the guideline owes in the change that introduces the capability.
- [x] 4.2 `guidelines/FeatureList.md` §1: the heading, the third document, its order, its budget and its trim rule,
      with every case that enforces them named.
- [x] 4.3 `README.md`: the Features table row and the paragraph naming which documents may be absent; `AGENTS.md`:
      the Documentation Map row that routes §1, which names that section by its heading.
- [x] 4.4 `tools/check-change-readiness.sh` reports `ok` for the change; `tools/check-openspec-hygiene.sh`,
      `tools/check-doc-routes.sh` and `tools/check-doc-values.sh` report no finding over the changed paths.

## 5. The gate (not run by this change's own pass)

- [ ] 5.1 The gate child runs `tools/loop/gate.sh --base 40cc58ff332952b6aca8b19deecd41e9aff81d23` over the diff and
      records `LOOP-GATE-TREE` and `LOOP-GATE`.
- [ ] 5.2 The independent review, then `openspec archive fix-a-store-listing-fits-its-field`.
