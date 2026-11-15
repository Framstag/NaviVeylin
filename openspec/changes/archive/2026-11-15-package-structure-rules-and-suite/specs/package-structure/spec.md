# package-structure

## Purpose

The five package kinds, what each of them may hold, and the one machine-readable declaration that
describes them.

## ADDED Requirements

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