# Proposal

## Why

`main` is red in the `Offline gates` job: `tools/check-rule-routes.sh` reports one named rule that no glob in
`tools/doc-routes.json` reaches, so a change to the rule is told to obey nothing.

```
$ bash tools/check-rule-routes.sh            # at the change's base feff2198
::error file=tools/doc-routes.json::the rule "the two `Contact` values are links on the phone." in UIPhone.md is reached by no glob — a rule no change is told to obey
check-rule-routes: 0 glob(s) match no tracked path, 1 rule(s) are reached by no glob, 0 rule(s) state no name
exit=1
```

(`evidence/red-check-rule-routes.txt`.) The rule is `guidelines/UIPhone.md` §5 line 34, landed by the change
`contact-links-in-details` (issue #768) — the change added the rule to the guideline and the code that renders the
links, and did not add the rule name to the map's `rules` list. The map's section entry `UIPhone.md §5` is present
under both `app/src/main/java/**` and `guidelines/UI*.md`, so the *section* is reached and the *named rule* is not:
the two facts the check reads are separate (`tools/check-rule-routes.sh`, "the reached-ness is a document+name
fact").

**Root cause, one sentence:** the map carries a rule name per `**Rule: …**` heading it means to route, and
`contact-links-in-details` added a named rule without adding its name to the two routes that carry every other
`UIPhone.md` §5 rule (`tools/doc-routes.json`, route `app/src/main/java/**` and route `guidelines/UI*.md`), so the
check's "a rule no glob reaches is reported" invariant fires.

The item files a second observation, that `gate#theMapCoversEveryPipelineStep` fails over
`tools/loop-selftest.sh --group "$g"`. That half no longer holds: it was closed by `3bdd6783` (issue #781), which
made the pin strip a `--group <selection>` from both sides before comparing, and the case passes on this change's
base (`evidence/red-pin-on-base.txt`). One invariant is left holding, so the item stays one change.

## What Changes

Two lines in `tools/doc-routes.json`: the rule name
`UIPhone.md §5 "the two `Contact` values are links on the phone."` joins the `rules` array of the route
`app/src/main/java/**` (where the code it governs lives) and of the route `guidelines/UI*.md` (where the document
it sits in lives), in the document order the neighbouring §5 rules already keep.

**Not changing**: `guidelines/UIPhone.md`, the details dialog code, the check, the map's globs, the pin case, and
the loop's suite map. The requirement already states the invariant — `documentation-ownership`, "The review's
reading set is derived from the change's paths" ("a rule that no glob reaches SHALL be reported"), with the case
`routes#aRuleNoGlobReachesIsReported`. The capability is complete; the tree's data was the violation, so this
change carries no spec delta.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The delta this change would need is a violation of `documentation-ownership`'s shipped requirement, not a
change to it: the refusal `::error … reached by no glob` is the requirement holding, and the fix is the data it
reads.

## Impact

**Files**

- `tools/doc-routes.json`: two `rules` entries added — route `app/src/main/java/**` (line 165) and route
  `guidelines/UI*.md` (line 927). No glob, no section entry, no other route touched.
- `openspec/changes/fix-the-contact-rule-is-reached-by-no-glob/**`: this change and its evidence.

**Guidelines affected**: `guidelines/Build.md` §3 states the routing check a landing runs;
`guidelines/BacklogTooling.md` §9 owns what the map is checked against — the section the map derives for this
change's own paths; `guidelines/UIPhone.md` §5 owns the rule, unchanged.

**Additive, not breaking.** Nothing reads the map's rule list except `tools/check-rule-routes.sh`: the check gains
one reached rule (578 → 579 reached, `--list`) and a review of the details code or of `UIPhone.md` derives one more
rule to read. **Rollback**: delete the two lines; the check reports the rule again, which is the state this change repairs.

surface: 2 lines added in `tools/doc-routes.json`, 0 cases added, 0 scenarios — counted by
`bash tools/check-rule-routes.sh --list` (578 named rules reached before the change, 579 after) and by
`bash tools/loop/gate.sh --plan --base origin/main` (3 commands owed: the routing check, its self-test and its
mutation set).
