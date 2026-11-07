# Proposal — fix-classification-gate-red-on-head

## Why

Root cause: two **open** changes ship delta specs whose capability ids have no classification line in
`tools/feature-list/specs.json` — `change-readiness` (`openspec/changes/change-readiness-gate/specs/change-readiness/`)
and `shaping-inbox` (`openspec/changes/shaping-inbox/specs/shaping-inbox/`). `guidelines/FeatureList.md` §4 owes
that line to the change that writes the delta ("a change that introduces a capability classifies it in that same
change — when it writes the delta spec, not when it archives"), and the gate's widened check reads open changes
too, so the tree is red for every change that touches an `openspec/*` path. A second, latent defect sits behind
it: both changes' task 6.4 puts the line in the area `specs-and-process`, which `tools/feature-list/areas.json`
does not name, and the gate hard-fails on an unknown area id (`tools/gen-feature-list.sh:199-231`) — so their task
6.4 cannot go green as written, and a session that applies it would redden the gate a second time.

Evidence on HEAD `ead55b39` (clean tree), verbatim:

```
$ bash tools/gen-feature-list.sh --check-classification
gen-feature-list: unclassified spec id(s):
  change-readiness (introduced by the open change change-readiness-gate — classify it in that change, before it archives)
  shaping-inbox (introduced by the open change shaping-inbox — classify it in that change, before it archives)
gen-feature-list: stale classification entry for a spec that no longer exists:
  search-name-matching
  search-result-row-icon
  start-map-selection
specs read: 180
classified: 183
unclassified: 2
delta spec ids: 29
stale: 3
car-only areas: 1
highlights: 19
gen-feature-list: classification gate failed
exit=1
```

Retained as `evidence/classification-gate-red-on-HEAD.txt` (the run, its timestamp and the HEAD it was taken on).
The gate reads the open changes in the working tree, which `.gitignore` keeps out of version control, so this red is
this tree's state rather than a fresh clone's — and the tree is what every loop run and every apply pass gates
against.
The `stale:` lines are **reported, not failed** (`guidelines/FeatureList.md` §4: "A stale entry — an id the
classification names that is no longer shipped — is reported without failing"); they are the pre-archive lines of
three other open changes (`fix-compound-name-matching`, `search-result-row-content`,
`fix-start-map-selection`) and this change leaves them alone.

The defect contradicts spec `spec-feature-index`, requirement "An unclassified spec id stops the run" —
`guidelines/FeatureList.md` §4 owns the rule, §9 what a change owes when it touches this.

**Relation to the reported item.** Issue #128 (§194) was filed as "the full gate is red on HEAD: the shipped
`routing-max-speed` spec is unclassified". That half is already fixed: `routing-max-speed` is classified (area
`routing`, phone + car) since `ca4a5f1c fix: fix-routing-max-speed-classification (§189)`, issue #123. What is
left of the issue's claim — the gate is red on HEAD — is the two delta ids above, and that is what this change
closes.

## What Changes

One fix, in two parts, both data:

1. `tools/feature-list/specs.json` gains the two lines the open changes owe:
   `"change-readiness": { "area": "build-tooling", "userVisible": false, "surfaces": [] }` and
   `"shaping-inbox": { "area": "build-tooling", "userVisible": false, "surfaces": [] }`, each in its alphabetical
   place.
2. The two open changes' task 6.4 names the area it means: `specs-and-process` → `build-tooling`
   (`openspec/changes/change-readiness-gate/tasks.md`, `openspec/changes/shaping-inbox/tasks.md`).

## Impact

- `tools/feature-list/specs.json` — two entries. This is the only **versioned** file the change edits.
- `openspec/changes/change-readiness-gate/tasks.md`, `openspec/changes/shaping-inbox/tasks.md` — one area id each.
  Both live under `openspec/changes/*/`, which `.gitignore` keeps out of version control ("open changes are local
  WIP — only archived changes are versioned"), so this part is a working-tree correction that ships with each
  change's own archive; the change directory of *this* change is versioned by its own archive step.
- No Kotlin/Java/C++ source, no test source, no Gradle script, no resource. No delta spec: this change adds no
  capability, so it owes no classification line of its own.
- Specs whose contract is touched: none. The shipped specs `spec-feature-index` and `feature-list-generation` are
  the rules applied, not changed.
- Additive, and the rollback path is `git revert` of the one commit: the gate returns to the red state recorded in
  `evidence/classification-gate-red-on-HEAD.txt` and nothing else moves.

## Guidelines

`guidelines/FeatureList.md` §4 (the classification gate and its three rules) and §9 (what a change owes when it
touches this). `guidelines/Build.md` §2, the scope block — a delta that touches no compiled file runs no build and
no suite, and names the structural check it ran instead.
