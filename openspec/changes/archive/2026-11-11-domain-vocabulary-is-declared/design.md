# Design

## Context

This change declares the domain's words. It is the vocabulary half of the pair whose structure half is the
open change `package-structure-as-five-kinds` (`architecture/structure.tsv`, five package kinds, task 1.2).
It depends on that task and on nothing else of that change.

Three facts from the tree shape every decision below.

1. **The corpus never defines its words.** `"basemap is …"` and `"epoch is …"` have zero hits across
   `guidelines/`; the documents state rules and measurements. A glossary therefore cannot be extracted — it
   must be written — and a written definition is a plausible invention unless something proves it.
2. **The code cannot settle a spelling split short of ten to one.** `speedKmH`-family 278 against `…Kmh`-family
   144. Where ten to one exists the code's own usage settles it (`Basemap` 256 observed against `BaseMap` 0);
   short of that only a declaration can.
3. **The structure declaration already owns placement and edges.** Five kinds, default-deny, feature
   disjunction. The vocabulary must read it and must not restate any part of it.

Scope: `app/src/main`, `auto/src/main`, `core/src/main` — 292 + 78 + 92 = 462 Kotlin and Java files.

## Decisions

### D1 — The census reads three source sets and nothing else

| alternative | why not |
|---|---|
| the whole repository | `tools/` and `buildSrc` have their own language, already owned by `tools/tooling.json`; a second owner is the defect |
| include `:osmscout-client-java` and `app/src/main/cpp` | the JNI bridge translates libosmscout's vocabulary; the project does not own those words, and a row for one would claim a meaning it cannot define |
| include the test source sets | 503 files, half the corpus; a test name is prose, not a decision site |

**Chosen:** 462 main-source files. Test sources are scanned by `drift` only, so a test that spells `BaseMap`
propagates the finding, but no test name ranks as a candidate and no test declares a term.

*Risk.* A term whose meaning is decided in the native layer (`tile`, `epoch`) is declared from its Kotlin
surface only. Accepted and stated: the declaration never claims the C++ meaning, and the check never reads
C++.

### D2 — The key is `(<scope>/)<term>#<kind>`

| alternative | why not |
|---|---|
| one row per term, kinds listed as an array | a noun and a verb then share one exclusion rule, and the refusal cannot say which role failed |
| always scope the key | 80 % of terms live in exactly one feature; an unconditional scope makes every key longer for no finding |
| key on the term alone, kind as a field | two rows with the same term and different kinds collide, which is the case the key exists to separate |

**Chosen:** `favorite#type`, and `car-surface/session#type` beside `route/session#type` only where a word
carries two meanings. This follows the repository's existing capability key, `<spec-id>#<Requirement name>`
(`tools/gen-feature-list.sh`): one key identifies one thing.

*Risk.* A word that becomes polysemous later needs a key change. Mitigated by refusing a scope change that
no `not` field accompanies (`lopsided-exclusion`).

### D3 — The declaration is per-feature files under `tools/glossary/`, plus one index

| alternative | why not |
|---|---|
| one JSON file | ~150 rows at twelve lines each is a 1 800-line file no review reads |
| a table inside `guidelines/Glossary.md` | the doc becomes the machine-readable copy, and the doc is the thing that rots |
| per-feature files only, no index | the kind vocabulary, the vacuous lexicon and the allowed projections need one owner each |

**Chosen:** `tools/glossary/index.json` (kind vocabulary, vacuous lexicon, projection switches) and one
`tools/glossary/<feature>.json` per feature, both closed-schema. `GLOSSARY.md` is generated from them and is
never edited, in the shape `tools/gen-feature-list.sh` already uses for `FEATURES.md`.

### D4 — Scopes are the `feature` column of `architecture/structure.tsv`

| alternative | why not |
|---|---|
| the fourteen `category` values of `tools/tooling.json` | cut for filing work, not for design; reusing them makes the backlog taxonomy load-bearing for architecture |
| a new list of ~10 context names in the glossary | a second taxonomy over the same tree, disagreeing with the first |
| the 179 shipped capability ids | a verification cut; `auto-map-renderer` and `map-render` are one context split by surface |

**Chosen:** the structure declaration's `feature` column, read at run time. A scope the declaration does not
carry is `undeclared-context`, and because it reads the column rather than the directory tree, a feature
declared but not yet migrated is legal — the same ratchet shape as the structure change's baseline.

### D5 — An anchor is a declared case or a named rule

| alternative | why not |
|---|---|
| require a test case for every row | not every scenario carries a `**Case**:` line, so requiring one would make the rest unwritable and grow this change into a test-writing change |
| a reviewer's judgement that the definition is right | the repository measured a body-reading rule at 48 wrong verdicts out of 49 (`tools/check-change-readiness.sh`); a model grading prose repeats that |
| no anchor; the definition stands on review alone | nothing then distinguishes a definition from a guess, and the drift refusals still work, so the failure is silent |

**Chosen:** `unanchored` is refused at write time when a row carries neither a resolving `**Case**:` nor a
resolving named `**Rule:**`. The seed is the intersection of a code surface with an anchor — ~50–70 rows —
and a term with a surface and no anchor becomes an item whose
work is "write the case that would fail if this definition were wrong".

*Risk.* The seed under-covers the words the code leans on hardest. Measured and accepted: `overrun` may
arrive with no anchor, and that absence is itself the finding.

### D6 — Global means the `concept` kind, not a third scope

| alternative | why not |
|---|---|
| a `scope: global` value | a fourth axis, and a term that belongs to no feature then has two ways to say so |
| promote a term once it is used in ≥N features | makes popularity a declaration; `Theme` (ui 30, nowhere else) and `Viewport` (data 3, ui 15) would never qualify, while `Log` (five packages) would — the count is a measurement, not a meaning |
| no global terms at all | four words are genuinely app-wide: `diagnostics`, `settings`, `unit`, `locale` |

**Chosen:** a global term's home kind is `concept`, which the five-kinds spec already defines as depending on
nothing of ours. The count is bounded by task 1.2's concept rows, and `aGlobalTermIsHomedInConcept` refuses
the alternative.

### D7 — Bad names are a declared lexicon with a scope per entry

| alternative | why not |
|---|---|
| a global ban on `Manager`, `Helper`, `Util`, `Data`, `Impl` | `Provider` is a legitimate port role (`LocationProvider`, `DrivingModeProviderImpl`); a global ban is a rule the code cannot obey, which is how a check gets muted |
| a reviewer's taste | not reproducible, and it produces an argument per name |
| no bad-name rule at all | 36 types carry a vacuous suffix today, and the structure change already moves those files |

**Chosen:** `index.json` carries `vacuous[]`, each entry a suffix, a reason and the kinds that refuse it.
Refused in `concept` and `feature/<name>` only — the two coining kinds — which keeps the first run at the
coining kinds' sites instead of those plus every legitimate port.

### D8 — Two projections now, the vocabulary types deferred

| alternative | why not |
|---|---|
| glossary only, nothing generated | the definition lives in a file no developer reads while editing; the two copies then drift with no check |
| full generation, including `value class Speed(val kmh: Double)` from the `units` field | touches ~790 call sites; it is a migration, not a declaration, and bundling it makes this change unfinishable |
| generate into `port/` interfaces too | an interface's shape is design, not vocabulary; the projection would be rewriting design decisions |

**Chosen:** (1) the definition is written into the declaring type's KDoc — additive, never rewriting an
existing block; (2) a `concept` row of kind `type` with no declaring file emits a skeleton type carrying the
definition and a generated marker, and only when absent. `gen-glossary.sh --check` verifies both and fails
instead of writing, following `gen-feature-list.sh --check-classification`.

*Risk.* A generated skeleton in git invites hand edits. Guarded by the marker and by `--check`; a modified
marker is a finding, not a regeneration.

### D9 — The drift row files per refusal, bounded

| alternative | why not |
|---|---|
| identity per term | fifty drifting terms become fifty issues in one night |
| one fixed identity for everything | closing becomes meaningless: a fixed issue reopens on every unrelated finding |
| no sensor row; the gate check alone | the check runs when a change runs; drift can appear in a tree no change edits |

**Chosen:** `fingerprint: {fields: ["refusal"]}` — two or three conditions on a bad day, each listing every
offending term. This is the choice `stale-citations` already made (`fields: ["kind"]`). Bounds:
`consecutive: 2`, `maxFilings: 2`, the first scheduled run reports without writing (the registry's #667
behaviour), and escalation after three recurrences (`sensors.json` `escalation.maxRecurrences`).

### D10 — The check is a text-only shell check, not a Konsist test

| alternative | why not |
|---|---|
| extend the Konsist suite the structure change adds | Konsist reads compiled declarations in a JVM test; the glossary's inputs are a census of text, a TSV and a JSON, and it must run on a docs-only delta with no Gradle |
| a Gradle task that writes sources | a build that writes production sources makes every incremental build a possible working-tree change |
| no check; the generator is the check | `--check` verifies projections, not vocabulary; the fourteen refusals are a separate contract |

**Chosen:** `tools/check-glossary.sh`, text-only, no build, no network, no python3 — the same shape as
`tools/check-doc-routes.sh`. `tools/gen-glossary.sh` writes and is run by a person, never by the build. No
threading model applies: no dispatcher, no lifecycle, no Android component is involved, and the two scripts
are single-process bash with no background work.

## Refusals and their inputs

| refusal | joins |
|---|---|
| `phantom` | a row against the census |
| `drift` | a forbidden surface against the census, scoped by path |
| `undecided`, `overruled` | a row's canonical form against the census counts |
| `kind-mismatch`, `unit-missing`, `bool-prefix` | a row against its observed surfaces |
| `unanchored` | a row against the anchors: a resolving `**Case**:` or a resolving named `**Rule:**` |
| `twin-term`, `lopsided-exclusion` | two rows against each other |
| `unowned-term`, `misplaced-term`, `bad-name` | a row against a file's kind, read from `structure.tsv` |
| `undeclared-context` | a row's scope against the declaration's `feature` column |

## Verification

- `tools/tests/check-glossary-selftest.sh` — one case per refusal over a synthetic tree in `/tmp`, plus the
  green-run figures and the fail-closed cases.
- `tools/tests/check-glossary-mutations.json` — one mutation per refusal, run by `tools/mutate.sh`, so the
  case that names a refusal is proven to fail when the refusal is removed.
- `tools/tests/gen-glossary-selftest.sh` — the two projections, the additive-only rule, the absent-only
  emission and `--check` both ways.
- `tools/sensors/tests/sensor-selftest.sh` picks up the new row: cadence, threshold, fingerprint fields and
  `maxFilings` are read from the registry, so an added row is covered without editing the suite.
- No on-device verification: nothing touches a surface, a frame or a template. The one measurement a run
  records is the census itself — rows, anchored rows, kinds, files scanned — printed by a green `--check`.

## Open decisions

none
