# domain-vocabulary

## Purpose

The project's domain words are declared once, in one machine-readable file, carrying the definition, the
spelling, the grammatical surfaces and the proof that each word means what the declaration says — so that a
word has one owner instead of a convention nobody can check.

## ADDED Requirements

### Requirement: A row is one term in one grammatical kind

Each row SHALL be identified by `(<scope>/)<term>#<kind>`, where `kind` is one of `entity`, `value`,
`operation`, `attribute`, `boolean`, `event` or `constant`, and `scope` is written only when one word carries
two meanings in two features. Two rows SHALL NOT share a key.

#### Scenario: A polysemous word carries a scope

- **WHEN** one word carries a different meaning in two features and both are declared
- **THEN** each row's key carries its scope and each names the other row in its `not` field
- **Case**: `glossary#aPolysemousWordCarriesItsScope`

#### Scenario: Two rows share a key

- **WHEN** two rows of the declaration carry the same `(<scope>/)<term>#<kind>` key
- **THEN** the check fails and names both rows and the key
- **Case**: `glossary#aDuplicateKeyIsRefused`

### Requirement: Every row carries an anchor

A row SHALL carry at least one anchor: a `**Case**:` test name a shipped spec declares, or a named
`**Rule:**` a `guidelines/` document declares. A row carrying neither SHALL be refused, and the refusal SHALL
be raised where the row is written rather than by a scheduled measurement.

#### Scenario: A row without an anchor

- **WHEN** a row is added carrying neither a case nor a rule
- **THEN** the check fails with `unanchored`, naming the row and the two absent fields
- **Case**: `glossary#anUnanchoredRowIsRefused`

#### Scenario: An anchor that names nothing

- **WHEN** a row's anchor names a case no shipped spec declares, or a rule no document carries
- **THEN** the check fails with `unanchored`, naming the anchor text and where it was looked for
- **Case**: `glossary#anAnchorThatDoesNotResolveIsRefused`

### Requirement: A definition excludes its neighbours

Every row SHALL carry a `not` field naming the term or rows it is not, and an exclusion SHALL be mutual: when
row A names row B, row B SHALL name row A. Two rows that discriminate nothing SHALL be refused.

#### Scenario: A one-sided exclusion

- **WHEN** row A names row B in `not` and row B's `not` field does not name row A
- **THEN** the check fails with `lopsided-exclusion`, naming both rows
- **Case**: `glossary#aOneSidedExclusionIsRefused`

#### Scenario: Two rows that exclude nothing

- **WHEN** two rows of the same kind carry a `not` field that names neither the other nor anything else
- **THEN** the check fails with `twin-term`, naming both rows
- **Case**: `glossary#twoRowsWithoutAnExclusionAreRefused`

### Requirement: The declaration settles a split spelling

A declared surface SHALL be refused when the code holds at least ten of the opposite form for every one of
it, and a term whose observed spellings split with neither form reaching that majority SHALL carry a declared
canonical form. Both refusals SHALL name the observed counts.

#### Scenario: A declaration the code overrules

- **WHEN** a row declares `Basemap` and the census observes it `core` 39 / `app` 209 / `auto` 8 times with
  no `BaseMap` surface observed
- **THEN** the check passes, because the declaration agrees with the majority
- **Case**: `glossary#aDeclarationTheCodeConfirmsPasses`

#### Scenario: A declaration contradicted by usage

- **WHEN** a row declares a canonical form while the census observes the opposite spelling at least ten times
  for every one of it
- **THEN** the check fails with `overruled`, naming both counts
- **Case**: `glossary#anOverruledDeclarationIsRefused`

#### Scenario: A split with no declaration

- **WHEN** a term is observed as `KmH` 278 times and `Kmh` 144 times and no canonical form is declared
- **THEN** the check fails with `undecided`, naming both counts
- **Case**: `glossary#aSplitWithNoDeclarationIsRefused`

#### Scenario: A split with a declaration

- **WHEN** the same term carries a declared canonical form and both spellings remain in the tree
- **THEN** the check passes and the non-canonical form is reported only as `drift`
- **Case**: `glossary#aDeclaredSplitPasses`

### Requirement: Surfaces are declared per grammatical role

A row SHALL declare one surface per grammatical role it occupies — type, property, operation, event,
constant — together with the forms forbidden in that role. A physical quantity attribute SHALL name its unit,
and a boolean attribute SHALL carry a prefix.

#### Scenario: A forbidden form in its role

- **WHEN** a type's row declares `Basemap` and the code declares `BaseMap`
- **THEN** the check fails with `drift`, naming the row, the file and the line
- **Case**: `glossary#aForbiddenFormInItsRoleIsDrift`

#### Scenario: A declared kind with no surface

- **WHEN** a row declares kind `value` and the term's only observed surface is a type of another kind
- **THEN** the check fails with `kind-mismatch`, naming both kinds
- **Case**: `glossary#aDeclaredKindWithNoSurfaceIsARefusal`

#### Scenario: A boolean with no prefix

- **WHEN** a declared boolean surface is observed as `followMode: Boolean`
- **THEN** the check fails with `bool-prefix`, naming the surface and the accepted prefixes
- **Case**: `glossary#aBareBooleanIsRefused`

#### Scenario: A physical attribute with no unit

- **WHEN** a row declares an attribute carrying a physical quantity and names no unit
- **THEN** the check fails with `unit-missing`, naming the row
- **Case**: `glossary#aPhysicalAttributeWithoutUnitIsRefused`

### Requirement: The concept kind is the only global scope

A term common to the whole app SHALL have the home kind `concept`. A term whose home is a feature SHALL NOT
be declared global, however many features use it. A scope SHALL name a feature the structure declaration
carries.

#### Scenario: A global term outside concept

- **WHEN** a row is declared global and its home kind is a `feature/<name>` package
- **THEN** the check fails, naming the row and its home kind
- **Case**: `glossary#aGlobalTermIsHomedInConcept`

#### Scenario: A scope the structure does not carry

- **WHEN** a row's scope names a feature the structure declaration's `feature` column does not carry
- **THEN** the check fails with `undeclared-context`, naming the scope and the declaration
- **Case**: `glossary#anUndeclaredScopeIsRefused`

### Requirement: Only a coining kind declares a term

A row's home kind SHALL be `concept` or `feature/<name>`. A type declared in `port`, `platform/<name>` or
`shell` whose name is a declared term SHALL be reported, and a vacuous suffix from the declared lexicon SHALL
be refused in a coining kind only.

#### Scenario: A term declared in a non-coining kind

- **WHEN** a `platform/<name>` file declares a type whose name is a declared term
- **THEN** the check fails with `misplaced-term`, naming the file, the kind and the row
- **Case**: `glossary#aTermInANonCoiningKindIsRefused`

#### Scenario: A vacuous suffix in a coining kind

- **WHEN** a `concept` or `feature/<name>` file declares a type whose name ends in a suffix the lexicon
  refuses in that kind
- **THEN** the check fails with `bad-name`, naming the type, the suffix and the lexicon's reason
- **Case**: `glossary#aVacuousSuffixIsRefusedInACoiningKind`

#### Scenario: A role name in a port

- **WHEN** a `port` file declares `LocationProvider` and `Provider` is refused only in the coining kinds
- **THEN** the check passes
- **Case**: `glossary#aPortRoleIsNotAVacuousName`

### Requirement: The vocabulary reads the structure declaration

The declaration SHALL take its scope names from `architecture/structure.tsv` and SHALL declare no field
beyond its closed schema, so that no dependency edge and no package placement is stated twice. A missing
structure declaration SHALL fail the check closed.

#### Scenario: A row carrying an undeclared field

- **WHEN** a row carries a field the closed schema does not declare
- **THEN** the check fails, naming the row and the field
- **Case**: `glossary#aRowWithAnUnknownFieldIsRefused`

#### Scenario: The structure declaration is missing

- **WHEN** `architecture/structure.tsv` is absent or unparsable
- **THEN** the check fails, naming the file, rather than passing with no scopes
- **Case**: `glossary#aMissingDeclarationFailsClosed`

### Requirement: A refusal names the row and the artefact

Every refusal SHALL be one line naming the row's key, the file and line where a surface was found, the
artefact the finding was decided against, and exactly one refusal word. A green run SHALL print what it
verified.

#### Scenario: A refusal names its row and its artefact

- **WHEN** one drift finding is reported
- **THEN** its line carries the key, the file and the line, the census counts and the word `drift`, and
  carries no second finding
- **Case**: `glossary#aRefusalNamesItsRowAndItsArtefact`

#### Scenario: A green run reports figures

- **WHEN** the check passes over the whole tree
- **THEN** it prints the rows declared, the rows anchored, the kinds used and the files scanned
- **Case**: `glossary#aGreenRunPrintsFigures`
