# Proposal — add-store-listing-rendering

## Why

**The gap.** `tools/gen-feature-list.sh` renders the version entry exactly once, as a changelog grouped by feature
area: one `- **keyword** — paragraph.` bullet per changed user-visible capability, with no field budget, no order by
user impact and no trimming rule (`tools/gen-feature-list.sh:871-928`, the note's assembly; on the base, the only
length bound is `maxpara=320` per bullet paragraph, `:80`). Nothing the run produces can therefore be pasted into a store listing's
"What's new" field. The item's own probe measured it (`evidence/item-probe.log`, version `2026-10-08-9`): a real version
entry is **25 515 characters over 148 bullets**, 3 of those bullets fit 500 characters, and `head -c 500` of the entry
ends inside a capability comment — `<!-- cap: auto/navigation-view#Current and next step in` — which is the
"truncated mid-sentence" shape the item predicts.

**Red test.** `tools/tests/gen-feature-list-selftest.sh` section 10, on the base commit's tool: **9 of 130 cases
fail**, every one of them a `listing:` case, because no run writes `STORE-LISTING.md` at all. XML: `tests="130"
failures="9" errors="0"`, `timestamp="2026-11-09T23:22:06Z"`, retained as `evidence/red-on-HEAD.xml` (the run's
output is `evidence/red-on-HEAD.log`). The case the item's decision names — an over-budget entry is trimmed at a
sentence boundary rather than truncated mid-sentence — is `listing: an over-budget entry is trimmed, never truncated
mid-sentence`, and it is red on the base for the same reason.

**Owning capability.** A new one, `store-listing-rendering` (the recorded decision on the item, option 1: "a new
capability `store-listing-rendering`"). The requirement it renders is the existing `release-notes-generation` "One
entry per version, derived from that version's baseline": the listing is that entry inside one field, not a second
selection over the spec set.

**Owning guideline section.** `guidelines/FeatureList.md` §1 "What the three documents are" — it names the generated
documents and their rules, and it says "the two documents" today.

**Evidence for the two parameters the earlier attempt could not answer.**

- The budget is the store's field: Google Play's "What's new" text, 500 characters per language. The figure is
  reported by the Play Console's own field and by every guide to it and is **not** documented in this tree (the
  decision records that); the change declares it once, as `store_budget`, with that provenance beside it, and the
  run reports the budget it enforced.
- The order is the shortlist: `tools/feature-list/highlights.json` is the tree's existing statement of which
  capabilities a user can see or feel, in its own order. Ranking by it needs no new hand-maintained file, and the
  alternatives are eliminated in `design.md`.

**Why this is `add-` and not `fix-`.** The item's class is `improvement`, but the recorded decision settles the shape
as a new capability with its own spec — the item's own fix candidate calls it "a new capability with its own spec" —
so the change is add-shaped: one ADDED capability, no requirement rewritten.

surface: 11 cases added, 0 cases rewritten, 0 fixtures realigned, 1 capability delta, 5 documents realigned —
`./tools/look.sh 'RELEASE-NOTES'` (57 matches in 20 files: the tool, its self-test, the guideline, the README and
archived history) and `./tools/look.sh 'store listing|store-listing|What.s new'` (6 matches in 5 files, none of them
the tool, its data, the guideline or the README — the gap is that nothing in the tooling renders one).

## What Changes

- `tools/gen-feature-list.sh` renders, beside the note, `STORE-LISTING.md`: one block per version, the entry's bullets
  ranked by the shortlist, trimmed by whole bullets against `store_budget` (500, one declaration with its source), and
  closed by one general line naming the changes the budget could not hold. The versioned-document writer both
  documents use is factored out once, so the note's bytes do not change.
- `tools/feature-list/listing.awk` is the renderer, in the same shape as `gate.awk` and `pair.awk`: readable and
  runnable on its own.
- `tools/feature-list/areas.json` keeps no budget of its own; when it ever declares a per-area `storeShare`, the run
  requires the shares to sum to `store_budget` and fails otherwise.
- `tools/feature-list/specs.json` classifies the new capability (`build-tooling`, internal).
- `guidelines/FeatureList.md` §1 names the three documents and states the listing's rule; the README table and the
  `AGENTS.md` routing row name the third document; `tools/doc-routes.json` carries the new rule name;
  `tools/issues/citations.json` lists `STORE-LISTING.md` among the generated artefacts so a citation of it is not
  classified as a missing path.
- Additive: no existing requirement is rewritten, and `RELEASE-NOTES.md` is byte-identical to what the base produces
  for the same inputs (the suite's `notes: re-running a version replaces its entry byte for byte` case guards it).

## Impact

| path | what the change does |
|---|---|
| `tools/gen-feature-list.sh` | the constant, the per-area composition check, the shared versioned writer, the listing step and its report line |
| `tools/feature-list/listing.awk` | new renderer |
| `tools/tests/gen-feature-list-selftest.sh` | section 10, 11 cases, own fixture |
| `tools/feature-list/specs.json` | one classification line |
| `guidelines/FeatureList.md` §1, §9 | the third document and its rule; the never-edit list |
| `AGENTS.md`, `README.md`, `tools/doc-routes.json`, `tools/issues/citations.json` | the document, its route and its citation class |

No Android, build, native or runtime consequence: nothing in the app or the build reads any of it.

## Rollback

`git rm` the change's `listing.awk`, its self-test section and the listing step in the tool, then revert the guideline,
README, `AGENTS.md`, `doc-routes.json` and `citations.json` hunks. `STORE-LISTING.md` is a generated file and no code
reads it, so no other artefact is coupled.
