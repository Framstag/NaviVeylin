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

**The same cause, one level down, found by the first review round.** Renaming a glob does not re-home the entries
behind it: the twelve rewritten routes kept their entries, and the six documents whose entries lived in a *code*
route had no glob left that matched them at all — `MapCar`, `MapFollow`, `MapFrames`, `MapRoute`, `MapStyling`,
`BacklogTooling` derived **nothing** (measured: `evidence/checks-on-HEAD.log`, six documents matched by no glob with
every one of their rules still reached; `guidelines/Backlog.*` carried `BacklogTooling.md`'s nine entries while
matching only `Backlog.md`). The check could not see it, because its reached-ness invariant reads the map's rule
list and not the glob that matches a document. Replacing six 258-entry over-reads with six 0-entry under-reads is
the defect family this change exists to remove, so the correction round makes the net real: six routes named for the
six documents, and the two invariants that would have caught them (`tools/check-rule-routes.sh`).

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

**Surface.** `jq -r '.routes[]|.glob' tools/doc-routes.json | grep -c '^guidelines/'` = 19 guideline routes (13
before the correction round, 6 added); `grep -c '^expect ' tools/check-rule-routes-selftest.sh` = 13 assertions (9
before, 4 added: two documents in the map-reading case and two refusals); `jq -r '.mutations|length'
tools/check-rule-routes-mutations.json` = 6 records (4 before); `git diff -U0 tools/doc-routes.json | grep -c
'^+      "glob"'` = 6 globs added and 12 rewritten in one file.

surface: 2 cases added (3 assertions) and 2 assertions added to the existing case, 0 rewritten, 0 fixtures
realigned, 12 globs rewritten and 6 added in 1 file, 2 check invariants, 3 mutation records, 1 rule sentence in the
map's `comment` — `jq -r '.routes[]|.glob' tools/doc-routes.json | grep -c '^guidelines/'` and `git diff -U0
tools/doc-routes.json | grep -c '^+      "glob"'`

## What Changes

- **`tools/doc-routes.json`** — every `guidelines/` glob names what its route carries:
  - `<Doc>.*` (the document and its two companion layers) for a route that owns one document —
    `guidelines/MapRendering.*`, `guidelines/BuildGate.*`, `guidelines/BuildEvidence.*`, `guidelines/BuildTests.*`,
    `guidelines/BuildNative.*`, `guidelines/Build.*`, `guidelines/Logging.*`, `guidelines/FeatureList.*`,
    `guidelines/Metrics.*`, `guidelines/Regulatory.*`;
  - the concern family for a route that owns one — `guidelines/Design*` (the four Design documents), and the
    existing `guidelines/UI*.md`;
  - **six routes the first round left with no glob at all**, one per orphaned document, each carrying the entries
    that name its document and no others — `guidelines/MapCar.*`, `guidelines/MapFollow.*`, `guidelines/MapFrames.*`,
    `guidelines/MapRoute.*`, `guidelines/MapStyling.*`, `guidelines/BacklogTooling.*` — and `guidelines/Backlog.*`
    keeping only the entries naming `Backlog.md` (the tooling document's nine entries were reaching it through a
    glob that never matched it);
  - the map's `comment` states the rule and the measurement that produced it, so the next project adapting the file
    inherits it.
- **`tools/check-rule-routes.sh`** — two more invariants, both refusals: a normative document under `guidelines/`
  that no glob matches, and a route whose glob sits under `guidelines/` carrying a document its glob does not match.
  The reached-ness invariant cannot see either: a rule reached through `app/src/main/java/**` says nothing about
  whether a `MapCar.md` edit derives anything.
- **`tools/check-rule-routes-selftest.sh`** — `routes#aGuidelineEditDerivesItsOwnDocument` (five assertions: one
  document, one companion layer, one concern family, one of the orphaned `Map*` documents, and the tooling document
  of the backlog concern) plus `routes#aDocumentNoGlobNamesIsReported` and
  `routes#aGuidelineRouteCarryingAnotherDocumentIsReported` for the two new refusals. The map-reading case is the
  only one that reads this tree's own map, so the file's header says why a fixture cannot carry a fact about the
  map's granularity.
- **`tools/check-rule-routes-mutations.json`** — `route-names-its-document` now reverts the `guidelines/UI*.md` glob
  to `guidelines/**` (the previous search reverted `guidelines/Logging.*`, which under a `Logging.md` edit matched
  only the Logging route, so the case stayed green and the record could not go red), and two records for the new
  refusals.
- **`openspec/specs/documentation-ownership/spec.md`** (delta) — the requirement gains the granularity sentence, the
  route-coherence sentence, the document-coverage refusal, and three scenarios.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-ownership`: one requirement extended — "The review's reading set is derived from the change's
  paths" — with the rule that a glob SHALL name the document or concern family it reaches rather than the whole
  corpus, the rule that a `guidelines/` route SHALL carry the documents its glob matches, the refusal for a
  normative document no glob matches, and three added scenarios (with their cases). No shipped requirement's text
  changes other than those two sentences: the derivation mechanism, the `--diff` contract and the first two
  invariants' wording stay as they are, and the two invariants this change adds are stated in the same sentence
  that already carries the other two.

## Impact

| path | what changes |
|---|---|
| `tools/doc-routes.json` | 12 `guidelines/` globs name their document or concern family, 6 routes name the documents no glob had matched, and the `comment` states the rule |
| `tools/check-rule-routes.sh` | two invariants added: a document no glob matches, and a `guidelines/` route carrying a document its glob does not match |
| `tools/check-rule-routes-selftest.sh` | one map-reading case (5 assertions) and two cases for the new refusals |
| `tools/check-rule-routes-mutations.json` | one record made falsifiable, two records for the new refusals |
| `openspec/specs/documentation-ownership/spec.md` | two requirement sentences, three scenarios, after archive |

**Additive for a review.** After the correction round every derived set is the document's own: a
`guidelines/Logging.md` edit derives its 12 entries instead of 270, a `guidelines/Logging.recipes.md` edit 12 instead
of 258, and the six documents the first round had left at zero derive 18, 31, 14, 1, 27 and 9 entries of their own
(`evidence/derived-sets-after.txt`, `evidence/checks-on-HEAD.log`). Nothing a code path derived is removed: the
documents' entries stay in the code routes that carry them, so `app/src/main/java/**` derives what it derived
before. Rollback path: revert the change; the map-reading case goes red again (measured:
`evidence/red-on-HEAD.log`) and both new invariants fire on the old map (`evidence/checks-on-HEAD.log`).

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
- **Per-document splitting of the two family routes.** `guidelines/Design*` and `guidelines/UI*.md` stay families:
  a Design or UI edit genuinely reads its siblings, which is what those routes are for. A *family* glob is not a
  corpus glob — it names four documents a route carries, not twenty-five it does not.
- **A `guidelines/Map*` family glob for the five orphaned Map documents.** Tried on paper and rejected: the glob
  would also match `MapRendering.md`, which has a route of its own, and a `MapCar.md` edit would then be told to
  read `MapStyling`'s and `MapFollow`'s rules — the over-read this item exists to remove, one size down. A family
  glob is right where the documents are one concern (`Design`, `UI`); these five are five concerns that happen to
  share a name prefix, and `AGENTS.md` routes each of them separately.
- **Multiple globs per route (one route, `globs: ["app/src/main/java/**", "guidelines/MapCar.*"]`).** It would
  remove the duplication below, but it is a schema change to a 33-route, 25 kB data file and to every reader of it,
  inside a correction round. Recorded as the alternative, not taken here.
