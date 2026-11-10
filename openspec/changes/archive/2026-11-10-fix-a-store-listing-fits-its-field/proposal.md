# Proposal — a store listing fits its field

## Why

**Root cause.** `tools/gen-feature-list.sh` derives exactly one document from a version entry: the changelog-shaped
release note (`tools/gen-feature-list.sh:811`), written whole and unbounded (`:827`–`:905`, the entry assembled and
gated, then written per version). Nothing in the tool renders the same entry as store copy, so the only way to paste
"What's new" into a store field is to cut the note by hand — and the note is 25 515 characters against the field's 500.

**Evidence, measured on the release entry `2026-10-08-9`** (`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/100/evidence-probe.log`,
the item's own probe; the number is the probe's, not re-taken here): the entry is **25 515 characters over 148
bullets**, its largest area block 7 585 characters, and **3 of the 148 bullets** fit a 500-character field. `head -c
500` of the entry ends inside its own source comment, `…<!-- cap: auto/navigation-view#Current and next step in` —
the item's "truncated mid-sentence" is a truncation *inside a marker*, which is also the reason a naive cut is not
the fix.

**The recorded decision** (`ITEM_DECISION`, 2026-11-08T15:59:16Z) settles the fork the probe raised: option **1** — a
new capability `store-listing-rendering`, a third generated document derived from the same version entry, bullets
ranked by user impact, whole-bullet trimming at sentence boundaries, never splitting a `<!-- cap: … -->` comment, and
the changes that do not fit summarised under one general bullet (*General improvements and optimisations*). The
budget is per entry and is the store field's limit, recorded as a named constant with its source — the decision
records why a per-area budget cannot work: one entry's twelve rendered areas cannot sum to one field. It accepts
reversing `openspec/changes/archive/2026-10-09-generate-feature-list/design.md:152` ("store copy is written by a human
from the catalogue, not published from it").

**The red test on the base** — `tools/tests/gen-feature-list-selftest.sh` with the listing cases added and the
production change reverted (`git show HEAD:tools/gen-feature-list.sh`): **14 of 135 cases fail**, every one of them a
`listing:` case, because HEAD writes no `STORE-LISTING.md` and declares no `listing_budget`; XML
`tests="135" failures="14"` (`red-on-HEAD-gen-feature-list-selftest.xml`, log beside it in the item's evidence
directory). The first and smallest is `listing: the budget is declared by the tool`. No file in the tree renders a
store listing document: the sweep in `evidence/look-store-listing.log` (`tools/look.sh 'store listing'` over the
tree) hits prose only — this change's own additions, the decision this change reverses at
`openspec/changes/archive/2026-10-09-generate-feature-list/design.md:152`, and the Android store listing at
`openspec/specs/android-automotive-os/spec.md:78`.

## Blast radius

- **`tools/feature-list/listing.awk`** — new, one renderer: reads a version entry, orders its bullets by impact, fits
  them to the budget whole-bullet-first and sentence-second, summarises the rest, and reports
  `chars/published/trimmed/summarised` to a status file. It holds no project path and can be read and run on its own,
  as `tools/feature-list/gate.awk` can.
- **`tools/gen-feature-list.sh`** — the `listing_budget` constant beside `maxpara` (`:90`), one section 7a after the
  release note is written (`:908`–`:959`), and the `store listing:` report line (`:959`). No existing line's
  computation changes; the entry that section 7a reads is the one the run has just gated.
- **`tools/tests/gen-feature-list-selftest.sh`** — section 9 (`:743`), 16 cases over five versions: the trim, the
  source comment kept whole, the impact order, the summary, the budget, byte-identity on a re-run, and a version with
  no entry adding no section.
- **`tools/feature-list/specs.json`** — one classification line for the new capability (internal, no surface), which
  `guidelines/FeatureList.md` §9 owes in the change that introduces it.
- **`guidelines/FeatureList.md`** §1 — the section's own heading ("the two documents") and its rules; the store
  listing's order, its budget and its trim rule, with the cases that enforce them.
- **`README.md`** — the Features table (one row) and the paragraph that says which documents may be absent.
- **`tools/issues/citations.json`** — `STORE-LISTING.md` under `"generated"`, so a citation of it in an issue body is
  not read as a path the tree has lost before a release has produced one.
- **`AGENTS.md`** — the Documentation Map row that routes the catalogue, the release notes and the store listing to
  `FeatureList.md` §1, because that row names §1 by its heading.
- **Not touched:** the catalogue and the release note they already publish, `tools/feature-list/gate.awk`, the
  classification or the shortlist, `areas.json` (the budget is not a per-area figure, by the decision), `FEATURES.md`
  and `RELEASE-NOTES.md` themselves, `openspec/specs/**` while the change is open.

surface: 1 renderer added (`tools/feature-list/listing.awk`), 1 write site in `tools/gen-feature-list.sh`
(`tools/look.sh 'listing' --path tools/gen-feature-list.sh` → 28 matches, the constant, section 7a and the report
line), 1 case section of 16
(`tools/look.sh 'listing:' --path tools/tests/gen-feature-list-selftest.sh` → 33 matches, the 16 declarations and
their comments), 1 classification line (`tools/look.sh 'store-listing-rendering' --path
 tools/feature-list/specs.json` → 1), 3 prose sites (`tools/look.sh 'listing' --path guidelines/FeatureList.md` →
21 matches, `tools/look.sh 'STORE-LISTING' --path README.md` → 2, `tools/look.sh 'store listing' --path AGENTS.md`
→ 1), 1 entry in the generated-path list (`tools/look.sh 'STORE-LISTING' --path tools/issues/citations.json` → 1),
and 1 requirement added in this change's delta (`specs/store-listing-rendering/spec.md`).
