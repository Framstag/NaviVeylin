# Proposal

## Why

The repository names its domain words in code, in specs and in guidelines, and nothing holds the three
together. There is no place that says what `basemap` means, and the code says it four ways.

Measured on `main`, 2026-11-09, over `app/src/main`, `auto/src/main` and `core/src/main` — 462 Kotlin and
Java files:

| measured | figure |
|---|---|
| `Basemap` / `BaseMap` / `baseMap` as a whole-identifier prefix | 256 / 0 / 0, the 256 split `core` 39, `app` 209, `auto` 8 |
| `speedKmH`-family vs `…Kmh`-family | 278 vs 144 — short of the ten-to-one majority `overruled` requires, so the code cannot settle it |
| `: Boolean` without `is`/`has`/`can` | 0 — the census reports `bare-booleans 0` |
| unit suffix in an identifier | `Ms` 242, `Px` 237, `Meters` 137, **`Deg` 10**, `PerSecond` **0** |
| vacuous suffixes on a declared type | 36 |
| `favourite` vs `favorite` | 16 vs 790 in Kotlin; the open change `package-structure-as-five-kinds` calls its pilot `favourites` while twelve shipped spec ids say `fav-`/`favorite` |
| definitions in the corpus | `"basemap is …"` 0 hits, `"epoch is …"` 0 hits — the guidelines state rules and measurements, never definitions |

Two of those lines are the whole argument. `KmH`/`Kmh` is split 278 to 144 — short of ten to one — so no
census can decide it and no
reviewer can claim the code already settled it — only a declaration can. And the corpus never defines its own
words, so a glossary cannot be extracted from the prose that exists; it has to be written, and a definition
written without proof is a plausible invention.

What the repository does have is proof of a different kind: the shipped specs' `**Case**:` lines and the
named `**Rule:**` lines across `guidelines/*.md`, each already resolvable by an existing
check. A term that names one of those is a term whose meaning something would notice changing.

The structure itself is being declared by the open change `package-structure-as-five-kinds`
(`architecture/structure.tsv`, five kinds, task 1.2). That declaration answers *where a file sits*. It cannot
answer *what a word means* — and the two questions are answered by the same tree, so a second, unrelated
vocabulary would be a second owner.

Filed as issue #839, whose body carries the census and whose seven comments carry the forks this proposal
settled.

## What Changes

- A **declared vocabulary** in `tools/glossary/` — one file per feature plus an `index.json` — holding one row
  per `(<scope>/)<term>#<kind>`, with its one-sentence definition, its `not` field, its grammatical surfaces,
  its unit where it has one, its home kind and the anchor that proves it.
- **`<term>#<kind>` is the key**, following the existing `<spec-id>#<Requirement name>` capability-key
  convention: one key identifies one term in one grammatical role, so a noun and a verb cannot silently share
  a row.
- **A term is anchored or it does not exist.** An anchor is a `**Case**:` test name or a named `**Rule:**` from
  `guidelines/`. A row with neither is refused at write time — the tree can never contain one.
- **Global terms are the `concept` kind, not a third scope.** The five-kinds spec already defines `concept` as
  depending on nothing of ours; that *is* "inherent to the app in whole". Four terms qualify today
  (`diagnostics`, `settings`, `unit`, `locale`); the count is bounded by the concept rows task 1.2 declares.
- **A refusal vocabulary of fourteen words**, fixed in `tools/check-glossary.sh` the way
  `check-change-readiness.sh` fixes its own, so a second spelling cannot appear: `phantom`, `drift`,
  `undecided`, `overruled`, `kind-mismatch`, `unit-missing`, `bool-prefix`, `unanchored`, `twin-term`,
  `lopsided-exclusion`, `unowned-term`, `misplaced-term`, `bad-name`, `undeclared-context`.
- **The declaration is read, never restated.** `structure.tsv` owns where a package sits and which edges are
  legal; the glossary owns which words exist and what they mean. Cross-context term use is impossible by
  construction (features are disjunct), so the glossary declares no edge and duplicates no structure rule.
- **The vocabulary is projected into the code**, so a word cannot live only in prose: the definition is
  generated into the owning type's KDoc, and a `concept` row declaring a type that no file declares yet
  emits an empty skeleton type carrying that KDoc. A `--check` mode verifies every projection is present and
  unmodified, in the shape `gen-feature-list.sh --check-classification` already uses.
- **One sensor row**, `vocabulary-drift`, whose fingerprint field is the **refusal**, not the term: a bad day
  is two or three issues listing every offending term, never fifty. The first scheduled run reports and
  writes nothing; `maxFilings: 2` bounds the rest; escalation after three recurrences hands the class to a
  person.

Nothing moves. No file is renamed, no package is created, no existing test changes. The four candidate drift
findings above become rows and refusals; fixing them is the pilot migration's job, not this change's.

## Capabilities

### New Capabilities

- `domain-vocabulary`: one machine-readable declaration of the domain's words — the row key and its kinds, the
  grammatical surfaces, the definition and its `not` field, the anchor that proves it, the `concept` kind as
  the only global scope, and the fourteen refusals with the artefacts each joins.
- `vocabulary-projection`: the declared vocabulary reaching the code — the KDoc projection, the `concept`
  skeleton emission, the `--check` mode that fails instead of writing, and the sensor row that measures drift
  under a bounded issue count.

### Modified Capabilities

None. `package-structure` is read, not changed: its `architecture/structure.tsv` and its `feature` column are
the glossary's input, and the glossary adds no requirement to it. This change **depends on task 1.2** of
`package-structure-as-five-kinds` (`architecture/structure.tsv` as a 1:1 declaration over today's 27
packages) and on nothing else of that change — not the pilot migration, not the Konsist suite, not the
baseline.

## Impact

**New files.** `tools/glossary/index.json`, one `tools/glossary/<feature>.json` per feature, `tools/glossary/
GLOSSARY.md` (generated), `tools/gen-glossary.sh`, `tools/check-glossary.sh`,
`tools/sensors/metrics/vocabulary-drift.sh`.

**Edited files.** `tools/sensors/sensors.json` (one row), `tools/tooling.json` (`checks.glossary`), `AGENTS.md`
(one routing-table row and the module-structure block), `tools/doc-routes.json` (one route for
`tools/glossary/**`, `guidelines/Structure.md`, `guidelines/Glossary.md`), `guidelines/Design.md` §1 (its
layering rule routes to `structure.tsv` and to the glossary rather than restating either).

**New guideline documents.** `guidelines/Glossary.md` and its companion `guidelines/Glossary.why.md`, following
the `Concern.md` + `Concern.why.md` convention. `Guidelines/Structure.md` is *not* added here — the structure
change owns it; the glossary route points at the section that change will create, and the routing row lands
with whichever change writes it first.

**Unchanged.** No Gradle file, no manifest, no resource, no existing test, no C++ file, no submodule patch. Eight
compiled files are touched, and only additively: one KDoc block in `core/src/main/java/com/naviveylin/core/
DeepLinkParser.kt` and seven skeleton types under `app/src/main/java/com/naviveylin/concept/`. `:osmscout-client-java` and `libosmscout` are **out of scope**: the glossary
describes only this project's own language, so it holds no native row and checks no C++ spelling.

**Scope.** General to all three modules and both variants. It is an additive process and tooling change, not a
feature: nothing a user or the car host does changes. Phone and car are covered by one declaration, and the
cross-variant parity rule is unaffected.

**Additive, and the rollback is deletion.** The glossary, the check, the generator and the sensor row are new
files; deleting them and reverting `sensors.json`, `tooling.json`, `doc-routes.json` and the `AGENTS.md` row
restores the previous tree exactly. Once the KDoc projection has written into production types, reverting it
is a code change: the projection is therefore landed in its own task and its generated text is additive only —
it adds a KDoc block where a type had none, and never rewrites an existing comment.

**Risk — a second vocabulary.** `tools/tooling.json` already owns a classification vocabulary and
`guidelines/` already owns its named rules. The glossary is a third. The mitigation is that it declares only
what those two cannot: word meaning and spelling. It classifies nothing (the structure does), and it names no
rule (`guidelines/` does), so `check-doc-routes.sh` and `check-rule-routes.sh` keep their existing owners.

**Risk — the anchor chain leaks.** A scenario carries a `**Case**:` line or it is not an anchor. The seed is
therefore the intersection of a code surface and either an
anchor or a named rule, measured at ~50–70 rows; a term with a surface and no anchor is not written into the
glossary, and its item is "write the case that would fail if this definition were wrong". That bounds the
change and names the real cost honestly.

**Risk — issue volume.** The drift family can hold fifty terms at once. `fingerprint.fields: ["refusal"]`,
`maxFilings: 2`, `consecutive: 2` and the first-run report keep it at two or three issues; this is the same
choice `stale-citations` already made (`fingerprint: {fields: ["kind"]}`).

surface: 32 scenarios across 2 delta files, 1 new guideline document plus its rationale companion, 2 new tool
scripts, 1 sensor metric, 1 declaration (`index.json` plus one file per feature), 1 generated document, 1
sensor row, 1 routing row, 0 production files moved, 8 compiled files touched additively (1 KDoc projection, 7
concept skeletons) — `grep -c '^#### Scenario:'
openspec/changes/domain-vocabulary-is-declared/specs/*/spec.md`
