# package-structure Specification

## Purpose

The main sources of `:app`, `:auto` and `:core` are checked against one machine-readable declaration of
their packages, so that a package the declaration does not claim fails the check instead of passing
unnoticed.

## Requirements

### Requirement: A package is claimed by exactly one declaration row

Two declaration rows SHALL NOT claim the same package, and the check SHALL fail and name both rows.

#### Scenario: A package claimed by two kinds

- **WHEN** two declaration rows claim the same package
- **THEN** the structure check fails and names both rows
- **Case**: `StructureDeclarationTest#structureCheckFailsOnDuplicateRow`

### Requirement: The declaration is read, and a row the tree lacks is reported

The declaration SHALL be read from `architecture/structure.tsv`, and a row naming a package that no main
source of `:app`, `:auto` or `:core` sits in SHALL fail the check, naming that row by its line and its
package.

#### Scenario: A declaration row naming a package the tree no longer has

- **WHEN** a declaration row names a package with no file under it
- **THEN** the structure check fails and names that row
- **Case**: `StructureDeclarationTest#structureCheckFailsOnRowWithoutPackage`

### Requirement: An undeclared package fails by default

A main source file of `:app`, `:auto` or `:core` in a package no declaration row claims SHALL fail the
structure check, so that adding structure requires declaring it rather than writing a new rule. The check
SHALL NOT pass a package merely because no rule mentions it.

#### Scenario: An unclassified file is a violation

- **WHEN** a main source file sits in a package that no declaration row claims
- **THEN** the structure check fails and names that file and its package
- **Case**: `StructureDeclarationTest#structureCheckFailsOnUndeclaredPackage`

#### Scenario: A new package appears without a declaration

- **WHEN** a package is added to any of the three modules and no declaration row claims it
- **THEN** the structure check fails naming the package, and passes once a row claims it
- **Case**: `StructureDeclarationTest#defaultDenyFailsForANewPackage`

### Requirement: features are mutually disjunct

A file in `feature/<name>` SHALL NOT import `feature/<other>`. A feature MAY import `concept` and
`port` only. Two features communicate through `shell`, which is the only place that knows several of
them at once.

#### Scenario: One feature importing another

- **WHEN** a `feature/navigation` file imports a `feature/route` package
- **THEN** the structure check fails naming the file, the imported feature and the disjunction rule
- **Case**: `FeatureRuleTest#featureDisjunctionViolationNamesTheImport`

#### Scenario: A feature importing shell

- **WHEN** a `feature/<name>` file imports a `shell` package
- **THEN** the structure check fails, because a feature does not reach upward
- **Case**: `FeatureRuleTest#featureImportingShellFails`

### Requirement: a feature is Android-free

No file in `feature/<name>` SHALL import `android.*` or `androidx.*`. Everything a feature needs from
the platform SHALL arrive through a `port`, so that a feature is testable without the Android runtime.

#### Scenario: A feature file importing the Android framework

- **WHEN** a `feature/<name>` file imports `android.content.Context`
- **THEN** the structure check fails naming the file, the import and the feature's purity rule
- **Case**: `FeatureRuleTest#featurePurityViolationNamesTheImport`

#### Scenario: A feature reaching a port instead

- **WHEN** a `feature/<name>` file imports a `port` interface and implements no platform type
- **THEN** the structure check passes
- **Case**: `FeatureRuleTest#featureImportingPortPasses`
