# A guideline glob names the document it owns, not the corpus

## Why

`tools/doc-routes.json` derives a review's reading set as the union of every route whose glob matches a path the
change touched. Twelve of its thirteen `guidelines/` routes carried a glob that does not name what it reaches: six
were the corpus-wide `guidelines/**`, and six were a single document named as a file (`guidelines/Logging.md`) while
the route's own entries are about the concern. So **one edit to one guideline document derived the whole corpus**:

```
guidelines/Logging.md          entries: 270  documents: Build BuildEvidence BuildGate BuildNative BuildTests
                                                Design DesignCrossVariant DesignNative DesignStack Logging
guidelines/Logging.recipes.md  entries: 258  documents: … nine others, and not Logging, because the exact-file glob
                                                does not match a companion layer at all
guidelines/UI.md               entries: 449  (the six corpus-wide routes plus the family glob)
```

(`openspec/changes/fix-guideline-routes-name-their-document/evidence/baseline-derived-sets.txt`, taken on base
`650028bf` with `evidence/derive-probe.sh tools/doc-routes.json <paths>`.)

**The root cause, in one sentence.** A glob that matches every guideline document makes the union in
`tools/check-rule-routes.sh --diff` return every guideline route for any guideline path, so the derivation is a
corpus lookup rather than a route: 270 entries naming 10 of the 25 normative documents for a one-document edit, while
the map's own engine side derives from a glob that names the subtree (`app/src/main/java/**`).

**The red case.** `routes#aGuidelineEditDerivesItsOwnDocument`, added to `tools/check-rule-routes-selftest.sh`: it
copies this tree's own map and guideline corpus into a throwaway git tree, commits one edit to one document at a
time, and derives the set `--diff` prints for it. On base `650028bf` it fails on all three of its edits
(`evidence/red-on-HEAD.log`, 2026-11-02T04:13:36Z, exit 1, `3 of 3 cases failed`):

```
guidelines/Logging.md          derives 270 entries naming [Build BuildEvidence BuildGate BuildNative BuildTests
                               Design DesignCrossVariant DesignNative DesignStack Logging ] — it is one concern,
                               so it must derive [Logging] alone
guidelines/BuildGate.recipes.md derives 258 entries naming [nine other documents] — must derive [BuildGate] alone
guidelines/DesignStack.md      derives 258 entries naming [nine other documents] — must derive [Design
                               DesignCrossVariant DesignNative DesignStack] alone
```

**Owning capability and guideline section.** `documentation-ownership`, "The review's reading set is derived from
the change's paths" (`openspec/specs/documentation-ownership/spec.md:670`). No guideline document owns the map's
globs — the map is the one file a second project adapts, so its rule lives in the file's own `comment`, which this
change extends with the granularity sentence. The gap itself was recorded by
`openspec/changes/archive/2026-10-31-guideline-layers-mechanism/evidence/known-gaps.md` G3 ("the map is coarse
rather than absent") and is the item's subject.

**Surface.** `jq -r '.routes[]|.glob' tools/doc-routes.json | grep -c '^guidelines/'` = 13 guideline routes, of which
12 globs are rewritten in one file; `grep -c '^expect ' tools/check-rule-routes-selftest.sh` = 9 cases (6 before, 3
added under one case name); `jq -r '.mutations|length' tools/check-rule-routes-mutations.json` = 4 records (3 before);
`git diff -U0 tools/doc-routes.json | grep -c '^-      "glob": "guidelines'` = 12.

surface: 1 case added (3 assertions), 0 rewritten, 0 fixtures realigned, 12 globs rewritten in 1 file, 1 mutation
record, 1 rule sentence in the map's `comment` — `jq -r '.routes[]|.glob' tools/doc-routes.json | grep -c
'^guidelines/'` and `git diff -U0 tools/doc-routes.json | grep -c '^-      "glob": "guidelines'`

## What Changes

- **`tools/doc-routes.json`** — every `guidelines/` glob names what its route carries:
  - `<Doc>.*` (the document and its two companion layers) for a route that owns one document —
    `guidelines/MapRendering.*`, `guidelines/BuildGate.*`, `guidelines/BuildEvidence.*`, `guidelines/BuildTests.*`,
    `guidelines/BuildNative.*`, `guidelines/Build.*`, `guidelines/Logging.*`, `guidelines/FeatureList.*`,
    `guidelines/Metrics.*`, `guidelines/Regulatory.*`;
  - the concern family for a route that owns one — `guidelines/Backlog.*` (Backlog and its tooling concern),
    `guidelines/Design*` (the four Design documents), and the existing `guidelines/UI*.md`;
  - the map's `comment` states the rule and the measurement that produced it, so the next project adapting the file
    inherits it.
- **`tools/check-rule-routes-selftest.sh`** — `routes#aGuidelineEditDerivesItsOwnDocument`, the case above. It is
  the only case that reads this tree's own map, so the file's header says why a fixture cannot carry a fact about
  the map's granularity.
- **`tools/check-rule-routes-mutations.json`** — `route-names-its-document`: the `guidelines/Logging.*` glob
  reverted to `guidelines/**` turns the new case red and restoring it turns it green.
- **`openspec/specs/documentation-ownership/spec.md`** (delta) — the requirement gains the granularity sentence and
  the scenario, "One guideline edit derives its own document".

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-ownership`: one requirement extended — "The review's reading set is derived from the change's
  paths" — with the rule that a glob SHALL name the document or concern family it reaches rather than the whole
  corpus, and one added scenario (with its case) asserting what a one-document guideline edit derives. No shipped
  requirement's text changes other than that sentence: the derivation mechanism, the two invariants and the
  `--diff` contract stay as they are.

## Impact

| path | what changes |
|---|---|
| `tools/doc-routes.json` | 12 `guidelines/` globs name their document or concern family; the `comment` states the rule |
| `tools/check-rule-routes-selftest.sh` | one case (3 assertions) that copies this tree's map and corpus into a throwaway git tree |
| `tools/check-rule-routes-mutations.json` | one mutation record for the new case |
| `openspec/specs/documentation-ownership/spec.md` | one requirement extended, one scenario added, after archive |

**Additive for a review.** No derived set shrinks below the routes it already had for the document it is about: a
`guidelines/Logging.md` edit now derives its 12 entries instead of 270, and a `guidelines/Logging.recipes.md` edit
derives 12 instead of 258 — the companion previously derived nine *other* documents and not its own. Rollback path:
revert the change; the new case goes red again and every guideline edit derives the corpus.

Scope: **process tooling only** — no `:app`, `:auto`, `:core`, `buildSrc`, Gradle, manifest, resource, native or
submodule path, so no build and no device run is owed (`LOOP-MEASURE-CHECK` names the three checks the pathspec
owes). No tracker data is touched.

## Not in this change

- **The `tools/**` half of the item's candidate fix.** Measured before planning: a change to `tools/loop/state.sh`
  derives **4** entries, to `tools/issues/lib.sh` **1**, to `tools/sensors/run.sh` **1**
  (`evidence/baseline-derived-sets.txt`). The tooling directories are already narrower than the item assumed
  (`tools/loop/**` and `tools/sensors/**` are routes of their own, and `tools/**` adds the one section the sensors'
  check registry owns). Splitting `tools/**` further buys at most one entry and costs the under-read that the
  `tools/loop/**` route relies on, so it is not this delta.
- **`openspec/**` and `AGENTS.md`.** Each derives **1** entry today; neither is coarse.
- **The two `app/src/main/java/**`-scale families.** `guidelines/Design*` (107 entries) and `guidelines/UI*.md`
  (191) stay concern families: a Design or UI edit genuinely reads its siblings, which is what those routes are for.
- **Per-document splitting of the mixed routes.** `guidelines/Backlog.*` carries the backlogging rules and the
  tooling's own check and portability rules together, by design (`Backlog.md` with `BacklogTooling.md`) — the route's
  own entries name both documents. Re-attributing those entries is a routing decision, not a granularity fix.
