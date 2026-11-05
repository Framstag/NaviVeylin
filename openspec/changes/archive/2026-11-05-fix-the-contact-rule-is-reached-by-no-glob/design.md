# Design

## Root cause

`tools/doc-routes.json` is the path → rules map: each route carries a `sections` array (the guideline sections a
path's conventions live in) and a `rules` array (the `**Rule: <name>**` headings a reader is told to obey).
`tools/check-rule-routes.sh` reads both as separate facts — "the reached-ness is a document+name fact" — so a
route that carries `UIPhone.md §5` in `sections` reaches the *section* and none of its *rules*.

The change `contact-links-in-details` (issue #768, archived 2026-11-05) added §5's sixth rule to
`guidelines/UIPhone.md:34` and the code that renders the two links, and left both `rules` arrays alone. The five
rules it sits beside are in both arrays; the sixth is in neither. Every change since that touches
`app/src/main/java/**` or `guidelines/UI*.md` therefore derives five of §5's six rules.

## The single fix

Add the one rule name to the two routes that carry its neighbours, in document order:

- `app/src/main/java/**` — the route the details-dialog code derives (it carries all of §5's other rules).
- `guidelines/UI*.md` — the route an edit to the document itself derives.

Why not the alternatives:

- **Add a `guidelines/UIPhone.*` glob.** The rule is reached by `guidelines/UI*.md` already as a *document*; a
  second document glob would make a one-document edit derive two routes for one file, which is the corpus-wide
  shape `documentation-ownership` forbids ("a route whose glob sits under `guidelines/` SHALL carry the documents
  that glob matches and no others" — a second glob for one document is that same widening one level over).
- **Widen `app/src/main/**`'s route to name every rule of every phone document.** The map's own comment rejects it:
  a route wide enough to match every guideline tells a one-document edit to read ten documents (issue #733,
  measured 270 entries for one `Logging.md` edit).
- **Change the check to reach a rule through its section.** That would erase the distinction the requirement is
  written on — a section being reached says nothing about which path derives a rule — and would blind the check to
  the orphan it exists for (`documentation-ownership`, "A document no glob matches is reported": six documents were
  orphaned that way with every one of their rules reached).
- **Change `guidelines/UIPhone.md` to stop naming the rule.** The rule is a normative statement a reader must find
  by name; dropping it is a documentation regression, not a routing fix.

The item's other observation — `gate#theMapCoversEveryPipelineStep` over
`tools/loop-selftest.sh --group "$g"` — is eliminated by measurement, not by taste: the pin already strips a
`--group <selection>` from both sides (commit `3bdd6783`, issue #781) and the case passes on this change's base
(`evidence/base-part-1-already-fixed.txt`).

## Blast radius

- The check: one more reached rule (578 → 579). No glob, no section entry, no refusal changes.
- The derived reading set: a change touching the phone UI or `UIPhone.md` now derives one more rule — the one the
  details-link code is graded against, which is the intent.
- The loop: `tools/loop/gate.sh --plan` is unchanged for a `tools/**` delta (the same three commands), and no
  compiled file is touched, so no build is owed.

## Rollback

Delete the two lines from `tools/doc-routes.json`. The check reports the rule as unreached again, which is exactly
the state this change repairs; nothing persisted, no behaviour, no test changes.

## Test seam

The red case is the tree's own check, not a new case: `bash tools/check-rule-routes.sh` exits 1 on the base and 0
with the fix (`evidence/red-check-rule-routes.txt`, `evidence/green-check-rule-routes.txt`), and the falsification
removes the added line, re-runs the check red, restores it and re-runs it green (`evidence/revert-check.txt`). The
case that owns the invariant over a fixture — `routes#aRuleNoGlobReachesIsReported` — is unchanged and green in the
gate.

## The map derives this change's own reading

The paths this change touches are `tools/doc-routes.json` and this change directory; the map derives
`BacklogTooling.md §9` for them (`bash tools/check-rule-routes.sh --diff origin/main --change
openspec/changes/fix-the-contact-rule-is-reached-by-no-glob`), which the proposal names.

## Open decisions

none
