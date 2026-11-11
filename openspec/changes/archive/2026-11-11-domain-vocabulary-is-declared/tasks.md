# Tasks

Depends on **task 1.2** of `package-structure-as-five-kinds` (`architecture/structure.tsv` as a 1:1
declaration over today's 27 packages). Nothing else of that change is a prerequisite: not the Konsist suite,
not the baseline, not the pilot migration. If task 1.2 has not landed, group 1 is blocked and groups 2 and 3
are not.

## 1. The declaration and its inputs

- [x] 1.1 Write `tools/glossary/index.json`: the closed kind vocabulary (`entity`, `value`, `operation`,
  `attribute`, `boolean`, `event`, `constant`), the `vacuous[]` lexicon with a reason and a refusing-kind list
  per entry (`Manager`, `Helper`, `Util`, `Info`, `Data`, `Impl`, `Handler`, `Processor`, `Provider`,
  `Bridge`, `Wrapper`), and the projection switches. Parent spec: `domain-vocabulary` — The vocabulary reads
  the structure declaration. Verify: `jq -e . tools/glossary/index.json` parses and
  `jq -r '.vacuous[].suffix'` lists eleven entries — the eleven suffixes measured on 2026-11-09.

- [x] 1.2 Write the reader that loads `index.json`, the per-feature files and the `feature` column of
  `architecture/structure.tsv`, with a closed schema per row. Parent spec: `domain-vocabulary` — The
  vocabulary reads the structure declaration. Case `glossary#aRowWithAnUnknownFieldIsRefused` fails first on a
  row carrying `imports: []`, passes when the field is removed.

- [x] 1.3 Make the check fail closed when `architecture/structure.tsv` is absent, unparsable, or carries no
  `feature` column. Parent spec: `domain-vocabulary` — The vocabulary reads the structure declaration. Case
  `glossary#aMissingDeclarationFailsClosed`. Verify: rename the file in a scratch checkout, check exits 1
  naming the file, not 0 with no scopes.

- [x] 1.4 Write the census: one pass over `app/src/main`, `auto/src/main` and `core/src/main` producing, per
  identifier token, its observed spellings, their counts, the kind it appears as, and the file:line of each.
  Output is a sorted token file, no judgement. Parent spec: `domain-vocabulary` — Surfaces are declared per
  grammatical role. Verify: the file counts 462 source files and reports `prefix Basemap` `core` 39 / `app` 209 /
  `auto` 8 with `BaseMap` and `baseMap` unobserved as a prefix, and `suffix KmH` 35 / 159 / 84 against `suffix
  Kmh` 1 / 121 / 22 — the figures `evidence/census.log` carries.

## 2. The seed

- [x] 2.1 Write the anchor index: the `**Case**:` names across `openspec/specs/**/spec.md` and the named
  `**Rule:**` entries across `guidelines/*.md`, each with the file it came from. Parent spec:
  `domain-vocabulary` — Every row carries an anchor. Verify: `grep -c` over the index equals
  `grep -rh '\*\*Case\*\*:' openspec/specs --include=spec.md | wc -l`, and the rule side equals its own `grep -c`.

- [x] 2.2 Emit the seed: tokens that carry both a code surface and an anchor, one skeleton row each with
  `definition` and `not` empty, and a count per feature. Parent spec: `domain-vocabulary` — Every row carries
  an anchor. Verify: the emitted seed lands in the measured 50–70 rows, and the report names the tokens a
  surface alone would have contributed, so the exclusion is visible.

- [x] 2.3 Fill the seed rows: one sentence, one `not` field, the surfaces per grammatical role, the unit where
  the term carries a physical quantity, and the home kind. Parent spec: `domain-vocabulary` — A definition
  excludes its neighbours. Verify: `tools/check-glossary.sh` passes over the seed with zero `unanchored`,
  zero `twin-term` and zero `lopsided-exclusion`.

- [x] 2.4 Record the census measurement that the drift bound is set from: rows declared, rows anchored, kinds
  used, files scanned. Parent spec: `vocabulary-projection` — The drift measurement files under one identity
  per refusal. Verify: the figures are quoted in `guidelines/Glossary.md` §1 and match a fresh run.

## 3. The refusals

- [x] 3.1 Implement `phantom`, `drift` and the path-scoped forbidden form. Parent spec: `domain-vocabulary` —
  Surfaces are declared per grammatical role. Cases `glossary#aForbiddenFormInItsRoleIsDrift`,
  `glossary#aRefusalNamesItsRowAndItsArtefact`.

- [x] 3.2 Implement `undecided` and `overruled` from the census counts, with both counts on the line. Parent
  spec: `domain-vocabulary` — The declaration settles a split spelling. Cases
  `glossary#anOverruledDeclarationIsRefused`, `glossary#aSplitWithNoDeclarationIsRefused`,
  `glossary#aDeclaredSplitPasses`, `glossary#aDeclarationTheCodeConfirmsPasses`.

- [x] 3.3 Implement `kind-mismatch`, `unit-missing` and `bool-prefix`. Parent spec: `domain-vocabulary` —
  Surfaces are declared per grammatical role. Cases `glossary#aDeclaredKindWithNoSurfaceIsARefusal`,
  `glossary#aBareBooleanIsRefused`, `glossary#aPhysicalAttributeWithoutUnitIsRefused`. Verify: the first run
  reports `bare-booleans 0` over the census, and the figure is quoted in the change's evidence
  (`evidence/census.log`).

- [x] 3.4 Implement `unanchored`, `twin-term`, `lopsided-exclusion`. Parent spec: `domain-vocabulary` — Every
  row carries an anchor; A definition excludes its neighbours. Cases `glossary#anUnanchoredRowIsRefused`,
  `glossary#anAnchorThatDoesNotResolveIsRefused`, `glossary#aOneSidedExclusionIsRefused`,
  `glossary#twoRowsWithoutAnExclusionAreRefused`.

- [x] 3.5 Implement `unowned-term`, `misplaced-term`, `undeclared-context` and `bad-name`, reading kinds from
  `structure.tsv`. Parent spec: `domain-vocabulary` — Only a coining kind declares a term; The concept kind is
  the only global scope. Cases `glossary#aTermInANonCoiningKindIsRefused`,
  `glossary#aVacuousSuffixIsRefusedInACoiningKind`, `glossary#aPortRoleIsNotAVacuousName`,
  `glossary#aGlobalTermIsHomedInConcept`, `glossary#anUndeclaredScopeIsRefused`,
  `glossary#aDuplicateKeyIsRefused`, `glossary#aPolysemousWordCarriesItsScope`.

- [x] 3.6 Implement the green-run figures and the one-finding-per-line rule. Parent spec: `domain-vocabulary` —
  A refusal names the row and the artefact. Cases `glossary#aGreenRunPrintsFigures`,
  `glossary#aRefusalNamesItsRowAndItsArtefact`.

## 4. The projections

- [x] 4.1 Write the KDoc projection: the definition into the declaring type's KDoc, additive only, an
  existing block never rewritten. Parent spec: `vocabulary-projection` — The definition reaches the owning type
  as KDoc. Cases `projection#theDefinitionReachesTheType`, `projection#anExistingKdocIsNotRewritten`.

- [x] 4.2 Write the skeleton projection: a `concept` row of kind `type` with no declaring file emits a type
  carrying the KDoc and the generated marker, absent files only. Parent spec: `vocabulary-projection` — A
  declared concept with no file is emitted as a skeleton. Cases
  `projection#aSkeletonIsEmittedForAMissingConcept`, `projection#anExistingSkeletonIsNotOverwritten`.

- [x] 4.3 Write `--check`: verify every projection is present and unmodified, exit non-zero without writing.
  Parent spec: `vocabulary-projection` — The check mode fails instead of writing. Cases
  `projection#aModifiedProjectionFailsTheCheck`, `projection#aCurrentTreeWritesNothing`. Verify: no Gradle
  task invokes the generator — `grep -rn 'gen-glossary' --include=*.kts .` finds nothing.

## 5. The measurement

- [x] 5.1 Add the `vocabulary-drift` row to `tools/sensors/sensors.json` with
  `fingerprint: {fields: ["refusal"]}`, `threshold: 0`, `consecutive: 2`, `maxFilings: 2`,
  `lifecycle: file+close`. Parent spec: `vocabulary-projection` — The drift measurement files under one
  identity per refusal. Verify: `tools/sensors/tests/sensor-selftest.sh` passes without an edit to the suite,
  and the row's `--json` resolution names `refusal` as its one fingerprint field.

- [x] 5.2 Run the row once with `SENSORS_TRIGGER=schedule` and record that it reports the count and writes
  nothing, and once with fifty injected findings to confirm one condition carrying fifty terms. Parent spec:
  `vocabulary-projection` — The drift measurement files under one identity per refusal. Cases
  `projection#fiftyTermsAreOneCondition`, `projection#theFirstRunReports`.

## 6. Documentation and routing

- [x] 6.1 Write `guidelines/Glossary.md` with the rules that own this change: what a row is, why an anchor is
  required, why `concept` is the only global scope, the refusal vocabulary and the sensor row's bound; and
  `guidelines/Glossary.why.md` carrying the measurements (the `KmH`/`Kmh` split, the `Basemap` prefix counts,
  `bare-booleans 0`, the 36 vacuous suffixes). Parent spec: `domain-vocabulary` — A refusal
  names the row and the artefact. Verify: every `## §N` anchor in the companion resolves into the base
  document, and `tools/check-doc-routes.sh` reports neither document unrouted.

- [x] 6.2 Add the `AGENTS.md` routing row and its `tools/doc-routes.json` glob, and add the
  `checks.glossary` path to `tools/tooling.json`. Parent spec: `vocabulary-projection` — The check mode fails
  instead of writing. Verify: `tools/check-doc-routes.sh` and `tools/check-rule-routes.sh` both exit 0, and a
  `tools/glossary/**` path derives the glossary sections as its reading set.

- [x] 6.3 Point `guidelines/Design.md` §1 at `architecture/structure.tsv` for placement and at the glossary
  for vocabulary, removing whatever that section restates. Parent spec: `domain-vocabulary` — The vocabulary
  reads the structure declaration. Verify: `tools/check-doc-routes.sh` exits 0 and `Design.md` §1 names no
  package.

## 7. Falsification — one mutation per refusal family

- [x] 7.1 Author `tools/tests/check-glossary-mutations.json`: one mutation per refusal (fourteen), each naming
  the case that must fail. Parent spec: `domain-vocabulary` — A refusal names the row and the artefact.
  Verify: `tools/mutate.sh --list` reports fourteen records.

- [x] 7.2 Revert-check the spelling family: declare `BaseMap` canonical over the fixture's ten-to-one
  majority and confirm
  `glossary#anOverruledDeclarationIsRefused` fails naming both counts; restore, forced green with
  `-PforceTests --no-build-cache`. Parent spec: `domain-vocabulary` — The declaration settles a split
  spelling.

- [x] 7.3 Revert-check the anchor family: add a row with no case and no rule, confirm
  `glossary#anUnanchoredRowIsRefused` fails at the point the row is written, restore, forced green. Parent
  spec: `domain-vocabulary` — Every row carries an anchor.

- [x] 7.4 Revert-check the kind family: declare a term homed in `platform/`, confirm
  `glossary#aTermInANonCoiningKindIsRefused` fails naming the file and the kind, restore, forced green. Parent
  spec: `domain-vocabulary` — Only a coining kind declares a term.

- [x] 7.5 Revert-check the projection: hand-edit one generated KDoc block, confirm
  `projection#aModifiedProjectionFailsTheCheck` exits non-zero naming the type and the row, restore, forced
  green. Parent spec: `vocabulary-projection` — The check mode fails instead of writing.

## 8. Final verification

- [x] 8.1 Run `tools/tests/check-glossary-selftest.sh`, `tools/tests/gen-glossary-selftest.sh`,
  `tools/mutate.sh` and `tools/sensors/tests/sensor-selftest.sh` — all offline, no Gradle, no device. Parent
  spec: `vocabulary-projection` — The check mode fails instead of writing. The four revert-checks of tasks 7.1–7.5 are
  discharged by that set rather than by hand: `tools/mutate.sh --mutations tools/tests/check-glossary-mutations.json`
  runs each refusal's case with its guard removed and green again after the restore (14 of 14 red then green),
  and the labels `overruled`, `unanchored` and `unowned-term` are the three of tasks 7.2–7.4; task 7.5's projection is
  `projection#aModifiedProjectionFailsTheCheck` in `tools/tests/gen-glossary-selftest.sh`, which hand-edits a
  generated block and asserts the check exits 1 naming the type and the row.

- [x] 8.2 Name the scope of the verification in the change's evidence. The delta **does** touch compiled files —
  the KDoc projection writes one block into `core/src/main/java/com/naviveylin/core/DeepLinkParser.kt` and the
  skeleton projection emits seven types under `app/src/main/java/com/naviveylin/concept/` — so the build's own
  gate is owed beside the tooling suites, and the structural checks `tools/check-doc-routes.sh`,
  `tools/check-rule-routes.sh`, `tools/check-openspec-hygiene.sh` and `tools/check-portability.sh` are owed with
  it (`guidelines/Build.md` §2, the scope block). Parent spec: `domain-vocabulary` — A refusal names the row and
  the artefact.
