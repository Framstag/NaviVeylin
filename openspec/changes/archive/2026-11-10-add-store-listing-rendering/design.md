# Design — add-store-listing-rendering

## The gap

One mechanism: the run renders a version's entry as a changelog and has no second renderer, so a document whose
whole purpose is to be pasted into a store field is produced by nobody. `tools/gen-feature-list.sh` writes
`FEATURES.md` (the catalogue) and `RELEASE-NOTES.md` (one entry per version, grouped by area), and on the base the
only bound on either is the per-bullet paragraph length — a bound that keeps a bullet short and says nothing about a
total.

## The fix

One renderer over the entry the note already carries, and the four decisions it needs:

1. **The document.** `STORE-LISTING.md`, one block per version, written by the same "replace in place, newest first"
   rule the note uses. The rule is factored into `write_versioned_doc` and both documents call it, because a second
   copy of the splitting rule is how two documents of the same entry drift apart.
2. **The line.** One published bullet is one line: `- <keyword> — <paragraph>`, the note's own keyword and paragraph
   with the markdown and the capability comment dropped. A store field renders no markdown, and a comment is not text
   a reader sees; carrying it would spend 30–60 of 500 characters on provenance no store shows.
3. **The budget.** `store_budget=500`, declared once, with the figure's provenance in the comment beside it: Google
   Play's "What's new" field, 500 characters per language. The figure is not documented in this tree — the recorded
   decision says so — so the source is named rather than cited, and the run prints the budget it enforced. A per-area
   `storeShare` in `areas.json`, if one is ever declared, must sum to it: one field is one block, so twelve 500-char
   area shares cannot each be the field.
4. **The order.** A bullet whose spec the shortlist names ranks above one whose spec it does not, the shortlist's own
   order breaking ties, and the entry's order after that. The shortlist is the tree's existing statement of what a
   user can see or feel — "the catalogue is a pitch, not an inventory" (`guidelines/FeatureList.md` §1) — so the
   ranking adds no file to maintain.
5. **The trim.** The summary line is reserved first, then bullets are placed whole while they fit; the first that does
   not fit ends the selection, and every change it left out is named by that one line. Nothing is cut: not a
   paragraph, not a comment, not a sentence.

## Alternatives the evidence eliminated

- **Cut the entry at 500 characters.** This is what the item measured: `head -c 500` of a real entry ends inside
  `<!-- cap: auto/navigation-view#Current and next step in` — a published line that is half a comment and half a
  token. Eliminated by the item's own probe.
- **Keep the first sentence of each bullet and drop the rest.** The note's paragraph is one or two sentences the prose
  gate approved as a whole; keeping the first sentence publishes a claim the gate never checked, and the sentence is
  not the unit the entry is composed in. Eliminated by the spec's "trimmed at a sentence boundary" reading of a
  whole-bullet trim.
- **A per-area budget, as the item's fix candidate first proposed.** Twelve fields of 500 characters is not one store
  field, and the decision records that the per-area wording cannot sum to one entry. Eliminated by the recorded
  decision; the composition check is what remains of it.
- **A per-spec `impact` field in `specs.json`.** 186 shipped specs, one hand judgement each, decided at classification time
  for a release that may never touch them, and stale the moment a capability changes character. Eliminated: the
  shortlist is the same judgement, already curated, already ordered, and already what the catalogue publishes.
- **Order by area order, or by change kind (added, changed, removed).** `areas.json`'s `order` declares render order,
  not impact, and a removal is as visible to a user as an addition — the kind says what the delta did, not what a
  reader would care about. Eliminated by the "see or feel" test the decision states.
- **Let the model choose what fits.** The run is model-free by design (`release-notes-generation` "The note is
  grouped by feature area and derived without a model"); a fit decided by prose would make two runs of one version
  differ, and the listing carries the note's byte-identity rule. Eliminated by that requirement.
- **A flag that renders the listing only when asked (`--store-listing`).** Its default reproduces the item's complaint
  — a tool that writes no listing unless told is the state this item is about — and the decision records the flag as
  option 4, rejected at a higher cost than rendering always.

## Blast radius

| path | what the diff does |
|---|---|
| `tools/gen-feature-list.sh` | `store_budget`/`store_summary` constants, the `storeShare` composition check, `write_versioned_doc` (the note's inline reassembly moved into it unchanged), and the listing step with its report line |
| `tools/feature-list/listing.awk` | new: rank, render, trim; writes its counts to a file the tool reads |
| `tools/tests/gen-feature-list-selftest.sh` | section 10 with its own fixture (six changed capabilities, one of them off the shortlist) and 11 cases |
| `tools/feature-list/specs.json` | `store-listing-rendering` classified `build-tooling`, internal |
| `guidelines/FeatureList.md` | §1 renamed to the three documents, the listing's bullet, rule and cases; §9's never-edit line |
| `AGENTS.md`, `README.md` | the third document in the routing row and the document table; the row's prices re-measured |
| `tools/doc-routes.json`, `tools/issues/citations.json` | the new rule name; `STORE-LISTING.md` as a generated artefact |

The note's bytes are unchanged: its reassembly is the same awk, moved into a function, and the suite's
`notes: re-running a version replaces its entry byte for byte` case plus the 119 cases around it stay green.

## Risks / Trade-offs

- **A listing publishes prose the note's gate approved, into a field nobody reviews.** The listing is derived from
  gated prose only — no new sentences and no model — so the worst a reader sees is a claim the note already made. The
  budget makes the opposite risk real: a 228-change release is summarised by one general line, and 223 changes are
  invisible to a store reader. That is what a 500-character field is, and the note keeps the detail.
- **The ranking is a proxy.** A capability the shortlist does not name can be the most important thing in a release
  and is withheld first. The alternative is a hand-maintained impact field that nobody updates (eliminated above);
  the shortlist at least has an owner and a review.
- **`store_budget` is an unverified published figure.** If Play changes its limit the block is wrong by that delta,
  silently. The constant is one line with its provenance, and a wrong figure is one edit.
- **A store field is per language, and the listing is English only.** The note is English only too; translating the
  block is a separate capability, and the 500-character figure is the figure Play states *per language*.

## Verification

Everything is verified on the host, by `tools/tests/gen-feature-list-selftest.sh` (no build, no device, no network,
no model), which is the suite a `tools/gen-feature-list*.sh`, `tools/feature-list/*` or `openspec/*` change pulls into
the gate (`tools/loop/loop.conf` `LOOP_GATE_SUITES`):

| claim | case |
|---|---|
| a run writes the block | `listing: a run writes the listing for the version` |
| a repeated version replaces it byte for byte | `listing: re-running a version replaces its block byte for byte` |
| the block fits the declared budget | `listing: the block fits the declared budget` |
| the budget is reported | `listing: the run reports the budget it enforced` |
| no line is a cut bullet or a cut comment | `listing: an over-budget entry is trimmed, never truncated mid-sentence` |
| the withheld changes are named | `listing: the overflow is summarised in one general bullet` |
| no comment is published | `listing: no block carries a capability comment` |
| the shortlist decides the order | `listing: a shortlisted capability ranks above an unlisted one` |
| an internal-only version writes nothing | `listing: nothing user-visible writes no block` |
| per-area shares must compose | `listing: a per-area share that does not compose fails the run` |

The real-entry numbers are a probe, not a case: a copy of `tools/feature-list/` rendered for `2026-10-08-9` writes
`STORE-LISTING.md` at **443 characters, 5 of 229 changes shown** (that release's baseline differs from the one the
item's probe used: 228 changed and 1 removed capability). No device or logcat check applies — no runtime code and no
rendering path is touched.

## Open decisions

none
