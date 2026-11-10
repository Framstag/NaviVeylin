# Design — a store listing fits its field

## Root cause

One document is rendered from a version entry. `tools/gen-feature-list.sh` assembles the entry
(`tools/gen-feature-list.sh:811`), gates it, and writes it into `RELEASE-NOTES.md` whole and unbounded
(`:827`–`:905`). A store field carries 500 characters and the item's probe measured a real entry at 25 515
(`items/100/evidence-probe.log`), so the entry cannot be pasted, and the only cut a reader can take is a naive one —
which stops inside a `<!-- cap: … -->` marker.

## The one fix

A renderer over the entry the run has already gated, plus the document it writes.

- `tools/feature-list/listing.awk` takes one version entry and produces that version's listing: the bullets in
  impact order, fitted to a character budget. A bullet that does not fit whole is shortened to its whole leading
  sentences (`fit_sentences`, `:140`); the comments under a bullet are carried as whole lines and are neither counted
  nor split, so a marker is never truncated. What still does not fit is counted into one general bullet
  (`summary_line`, `:155`) which makes room for itself by taking back the last change it can, so the published
  section is never over budget and a change is trimmed or summarised but never dropped in silence.
- `tools/gen-feature-list.sh:908`–`:959` runs it on the entry the release note section just gated, keeps one file per
  version under the work directory, replaces this version's section and re-joins the document newest first — the same
  "derived, not accumulated" shape the release note has. The budget is the constant at `:90`.

Two choices the decision left to the tree, and what settles each:

- **The impact order is the shortlist's.** `tools/feature-list/highlights.json` is the tree's existing record of what
  the product tells a user about — one entry per selling point, the file the catalogue is built from. A bullet whose
  first cited spec a highlight names ranks above one it does not, and the entry's own order settles each group, so the
  order is a stable function of two files and nothing is inferred. The area order is *not* an impact order: it is the
  changelog's grouping, which is the order this document exists to leave behind.
- **The budget is 500 characters, per version.** It is the store field's limit, recorded with the item that asked for
  the document and not measured in this tree; the decision records why the scope is per entry — one entry's twelve
  rendered areas cannot sum to one field. It is declared as `listing_budget` beside `maxpara` so that a change to it
  is one line, and the document states the number it was fitted to in its own header. No per-area share is derived or
  declared, so no composition check is owed: the one figure the tool holds is the field's own.

## Blast radius and rollback

The touched files are `proposal.md`'s list: the new `listing.awk`, one write site and one constant in
`tools/gen-feature-list.sh`, one case section in `tools/tests/gen-feature-list-selftest.sh`, one classification line,
and three prose sites (`guidelines/FeatureList.md` §1, `README.md`, and the `AGENTS.md` row that routes §1 by its
heading). No existing computation changes: the catalogue, the release note, the gate, the classification,
the shortlist and `areas.json` are read exactly as they were.

Rollback is the deletion of section 7a, the constant, `listing.awk`, the listing cases, the classification line and the
three prose paragraphs, plus `STORE-LISTING.md` — nothing else reads the document, and no state under
`tools/feature-list/snapshots/` changes shape because of it.

## What this change does not do

- It does not phrase anything. The listing's prose is the release note's gated prose; a store listing that needed its
  own wording would be a second document no gate has read.
- It does not decide the store field's real limit. The constant carries the figure and its source; a person who
  records a different limit changes one line.
- It does not remove the release note's own unbounded length. The note is a changelog and is meant to be one.

## Alternatives the evidence eliminated

- **Truncate the entry at the budget** (`head -c 500`): the probe shows it ends inside a source comment, mid-token.
  It trims neither at a sentence nor at a marker, and it is the shape that fails the case the item asks for.
- **A budget per feature area** (the recorded option 1's first wording): the entry's twelve rendered areas cannot sum
  to one field; a per-area figure only composes if it is one field's figure, which is a per-entry budget.
- **The advisory check** (the decision's option 2): it prints the number and ships nothing, so the note still cannot
  be pasted and the same question returns every release.
- **Retire the item** (option 3): `openspec/changes/archive/2026-10-09-generate-feature-list/design.md:164` records the
  store-listing trim as deferred, not settled, so the item was not answered by the design.
- **A store-specific phrasing prompt**: a second document no gate has read, and it re-opens the reversal the decision
  already made deliberately.
- **Dropping what does not fit**: the decision requires the general bullet, and a silently shortened list is a claim
  about a release that the release did not make.

## Open decisions

none
