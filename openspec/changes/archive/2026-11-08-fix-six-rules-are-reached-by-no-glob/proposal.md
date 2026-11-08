# Proposal

## Why

Every gate tally in the loop currently carries `errors=1`: `tools/check-rule-routes.sh` reports six named rules
under `guidelines/` that no glob in `tools/doc-routes.json` reaches, and exits 1. Measured on #822's own gate
(2026-11-07T19:13Z): `tests=944 failures=0 errors=1 (shell gate, 25 steps; failing: tools/check-rule-routes.sh — 6 rules reached by no glob, in Backlog.md/MapCar.md/UICar.md/UIPhone.md/UI.md, untouched by this diff)` — a tally not retained as one JUnit XML (#822's own gate output, which this item did not re-run). The run attributed it as pre-existing and landed #822, so the error sits in every gate's tally until the map is repaired.

```
$ bash tools/check-rule-routes.sh            # at the change's base 51d7b695
::error file=tools/doc-routes.json::the rule "the queue reads the whole labelled set, and refuses one it could not cover." in Backlog.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the navigation surface's app-drawn elements are the compass rose, the speed badge and speed-limit sign, the attribution, the map-unavailable notice and the calculation strip." in MapCar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "a failed reroute is reported on the map surface." in UICar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "while navigation is active the wait is drawn, never pushed." in UICar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the rows' words come from the surface's resources." in UIPhone.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the car's reroute wait is a surface strip, the phone's is a card tint." in UI.md is reached by no glob — a rule no change is told to obey
check-rule-routes: 0 glob(s) match no tracked path, 6 rule(s) are reached by no glob, 0 rule(s) state no name
check-rule-routes: 0 document(s) are matched by no glob, 0 guideline route(s) carry a document their glob does not match
exit=1
```

(`evidence/red-check-rule-routes.txt`.) Each rule sits in a section the map already reaches — `Backlog.md` §5,
`MapCar.md` §14, `UICar.md` §3c, `UI.md` §8d, `UIPhone.md` §5 — but the check reads a rule's reached-ness as a
document+name fact, so a section entry reaches the section and none of its named rules.

**Root cause, one sentence:** `tools/doc-routes.json` lists a route's section names and its rule names as two
separate arrays, and each change that wrote one of these six `**Rule: …**` headings added it to its guideline
section without adding its name to the `rules` arrays of the routes that carry that section's other rules, so the
check's "a rule no glob reaches is reported" invariant fires.

## What Changes

Six rule names join the `rules` arrays of the routes where their section's other rules already sit — eleven
entries in all, because each name appears in every route that reaches its section:

| rule | section | routes that gain it |
|---|---|---|
| `the queue reads the whole labelled set, and refuses one it could not cover.` | `Backlog.md` §5 | `guidelines/Backlog.*` (1 entry) |
| `the navigation surface's app-drawn elements are the compass rose, the speed badge and speed-limit sign, the attribution, the map-unavailable notice and the calculation strip.` | `MapCar.md` §14 | `app/src/main/java/**`, `guidelines/MapCar.*` (2) |
| `while navigation is active the wait is drawn, never pushed.` | `UICar.md` §3c | `app/src/main/java/**`, `guidelines/UI*.md` (2) |
| `a failed reroute is reported on the map surface.` | `UICar.md` §3c | `app/src/main/java/**`, `guidelines/UI*.md` (2) |
| `the car's reroute wait is a surface strip, the phone's is a card tint.` | `UI.md` §8d | `app/src/main/java/**`, `guidelines/UI*.md` (2) |
| `the rows' words come from the surface's resources.` | `UIPhone.md` §5 | `app/src/main/java/**`, `guidelines/UI*.md` (2) |

`Backlog.md` §5's other five rules sit only under `guidelines/Backlog.*`, so its sixth joins that route alone; the
other five sections carry their rules under both a code route and a guideline route, and each new name joins both.
Every name is inserted after its section's last sibling, so each array keeps its document order. No glob, no
section entry and no other route is touched. The map reaches 585 named rules, up from 579
(`bash tools/check-rule-routes.sh --list`).

**Not changing**: `guidelines/` (all six headings stay byte for byte), `tools/check-rule-routes.sh`, its
self-test, the map's globs and section entries, and the loop's suite map. The requirement already states the
invariant — `documentation-ownership`, "The review's reading set is derived from the change's paths" ("a rule
that no glob reaches SHALL be reported"), with the case `routes#aRuleNoGlobReachesIsReported` — so the capability
is complete and the tree's data was the violation: **this change carries no spec delta** (`.openspec.yaml`,
`skip_specs: true`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The delta this change would need is `documentation-ownership`'s shipped requirement holding, not a change
to it; the fix is the data the refusal reads.

## Impact

**Files**

- `tools/doc-routes.json`: eleven `rules` entries added, six distinct names — route `app/src/main/java/**` (five
  entries), route `guidelines/UI*.md` (four), route `guidelines/MapCar.*` (one), route `guidelines/Backlog.*`
  (one). No glob, no section entry, no other route touched.
- `openspec/changes/fix-six-rules-are-reached-by-no-glob/**`: this change and its evidence.

**Guidelines affected**: `guidelines/BacklogTooling.md` §9 "Checks" is the section the map derives for this
change's own paths (`tools/doc-routes.json` routes to `BacklogTooling`), and the change names it; the six rules'
own sections — `MapCar.md` §14, `UICar.md` §3c, `UI.md` §8d, `UIPhone.md` §5, `Backlog.md` §5 — are unchanged.

**Additive, not breaking.** Nothing reads the map's rule lists except `tools/check-rule-routes.sh`: the check
gains six reached rules (579 → 585, `--list`), and a review of the car map surface, the car wait notices, the
phone details dialog, the cross-variant reroute presentation or `guidelines/Backlog.md` derives one more rule to
read. **Rollback**: delete the eleven entries; the check reports the six rules again, which is the state this
change repairs.

surface: 11 rule entries added to 4 routes, 0 cases added, 0 scenarios — counted by
`bash tools/check-rule-routes.sh --list` (579 named rules reached before the change, 585 after) and by
`tools/look.sh '<the six rule names>' --path guidelines` (6 `**Rule:` headings in 5 documents, each unchanged).
