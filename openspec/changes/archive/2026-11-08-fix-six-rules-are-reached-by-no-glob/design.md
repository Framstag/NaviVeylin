# Design

## Root cause

`tools/doc-routes.json` is the path → rules map. Each route carries a `sections` array — the guideline sections a
path's conventions live in — and a `rules` array — the `**Rule: <name>**` headings a reader is told to obey.
`tools/check-rule-routes.sh` reads the two as separate facts ("the reached-ness is a document+name fact"), so a
route that carries `UICar.md §3c` in `sections` reaches the *section* and none of its *rules*.

Six named rules sit in a reached section and in no route's `rules` array:

- `Backlog.md` §5 — `the queue reads the whole labelled set, and refuses one it could not cover.`
- `MapCar.md` §14 — `the navigation surface's app-drawn elements are the compass rose, the speed badge and
  speed-limit sign, the attribution, the map-unavailable notice and the calculation strip.`
- `UICar.md` §3c — `while navigation is active the wait is drawn, never pushed.`
- `UICar.md` §3c — `a failed reroute is reported on the map surface.`
- `UI.md` §8d — `the car's reroute wait is a surface strip, the phone's is a card tint.`
- `UIPhone.md` §5 — `the rows' words come from the surface's resources.`

Each landed with the change that wrote the heading, which added it to its guideline section and left the map's
`rules` arrays alone. The check's second invariant therefore fires for all six, and every gate tally since #822
carries `errors=1`.

## The single fix

Add each rule name to every route whose `rules` array already names a sibling of the same section, immediately
after that section's last sibling:

- `Backlog.md` §5's fifth rule sits only under `guidelines/Backlog.*` → its sixth joins that route.
- Every other orphan's section (`MapCar.md` §14, `UICar.md` §3c, `UI.md` §8d, `UIPhone.md` §5) carries its rules
  under both `app/src/main/java/**` (the code the rule governs) and its guideline route (`guidelines/MapCar.*`,
  `guidelines/UI*.md`) → each orphan joins both.

Eleven entries; six distinct names; the map reaches 585, up from 579.

Why not the alternatives:

- **Change the check to reach a rule through its section.** That erases the distinction the requirement is
  written on — a route carrying a section the code does not obey says nothing about which path derives the rule —
  and would blind the check to the orphan class it exists for. `documentation-ownership`, "A document no glob
  matches is reported", records six documents orphaned that way with every one of their rules still reached.
- **Add a new glob per document, or widen one.** No glob is unmatched here: `guidelines/MapCar.*`,
  `guidelines/UI*.md` and `guidelines/Backlog.*` already reach the three documents, so the document-coverage
  invariant is satisfied and no glob change is owed.
- **Widen `app/src/main/java/**` to name every rule of every UI document.** The map's own comment rejects it: a
  route wide enough to match every guideline tells a one-document edit to read ten documents (issue #733,
  measured 270 entries derived for one `Logging.md` edit).
- **Add the rules only to their guideline routes and not to the code routes.** The car reroute path, the car wait
  notice, the car navigation surface and the phone details rows would derive the section and not the rule — the
  gap this change exists to close. `#789` (`fix-the-contact-rule-is-reached-by-no-glob`) added its rule to both
  routes for the same reason.
- **Drop the six headings from their guidelines.** They are normative statements a reader must find by name;
  dropping them is a documentation regression, not a routing fix.

## Blast radius

- The check: six more reached rules (579 → 585). No glob, no section entry, no refusal changes.
- The derived reading set: a change touching the car map surface, the car wait notices, the phone details dialog,
  the cross-variant reroute presentation or `guidelines/Backlog.md` now derives the rule it is graded against,
  which is the intent.
- The loop: the map owes the same three checks for this delta — `tools/check-rule-routes.sh`,
  `tools/check-rule-routes-selftest.sh` and `tools/mutate.sh --mutations tools/check-rule-routes-mutations.json
  --list` (`LOOP-MEASURE-CHECK`) — and no compiled file is touched, so no build is owed.

## Rollback

Delete the eleven entries from `tools/doc-routes.json`. The check reports the six rules as unreached again, which
is exactly the state this change repairs; no persisted data, no behaviour, no test changes.

## Test seam

The red case is the tree's own check, not a new case: `bash tools/check-rule-routes.sh` exits 1 on the base and 0
with the fix (`evidence/red-check-rule-routes.txt`, `evidence/green-check-rule-routes.txt`), and the
falsification removes the added entries, re-runs the check red, restores them and re-runs it green
(`evidence/revert-check.txt`). The fixture case that owns the invariant —
`routes#aRuleNoGlobReachesIsReported` in `tools/check-rule-routes-selftest.sh` — is unchanged and green in the
selftest (`evidence/routes-selftest.txt`).

## The map derives this change's own reading

The paths this change touches are `tools/doc-routes.json` and this change directory; the map derives
`BacklogTooling.md` §9 for them (`bash tools/check-rule-routes.sh --diff 51d7b695 --change
openspec/changes/fix-six-rules-are-reached-by-no-glob`), which the proposal names.

## Open decisions

none
