# package-structure Specification

## Purpose
Every main source file of `:app`, `:auto` and `:core` belongs to exactly one of five package kinds, and
the dependency edges those kinds may hold are declared in one machine-readable place rather than
described in prose that no build reads.

## Requirements

### Requirement: A source file belongs to exactly one kind

Every main source file of `:app`, `:auto` and `:core` SHALL belong to exactly one of five kinds:
`concept`, `port`, `feature/<name>`, `platform/<name>` or `shell`. A file that belongs to no kind, or a
kind declaration that claims the same package twice, SHALL be reported as a structure violation.

#### Scenario: An unclassified file is a violation

- **WHEN** a main source file sits in a package that no declaration row claims
- **THEN** the structure check fails and names that file and its package
- **Case**: `StructureDeclarationTest#structureCheckFailsOnUndeclaredPackage`

#### Scenario: A package claimed by two kinds

- **WHEN** two declaration rows claim the same package
- **THEN** the structure check fails and names both rows
- **Case**: `StructureDeclarationTest#structureCheckFailsOnDuplicateRow`

### Requirement: The structure declaration is the single machine-readable description

The structure SHALL be declared in one file, `architecture/structure.tsv`, holding one row per package
with its kind, its feature name where it has one, and the kinds and features it may import. No second
machine-readable copy of the structure SHALL exist, and a documentation section that states the
structure SHALL route to the declaration instead of restating it.

#### Scenario: The declaration is the only machine-readable copy

- **WHEN** the project's documentation is searched for the package structure
- **THEN** the guideline section names the declaration file and does not enumerate packages itself
- **Case**: `StructureDocumentationTest#docNamesDeclarationAndListsNoPackages`

#### Scenario: A declaration row naming a package the tree no longer has

- **WHEN** a declaration row names a package with no file under it
- **THEN** the structure check fails and names that row
- **Case**: `StructureDeclarationTest#structureCheckFailsOnRowWithoutPackage`

### Requirement: An undeclared package fails by default

A package that no declaration row claims SHALL fail the structure check, so that adding structure
requires declaring it rather than writing a new rule. The check SHALL NOT pass a package merely because
no rule mentions it.

#### Scenario: A new package appears without a declaration

- **WHEN** a package is added to any of the three modules and no declaration row claims it
- **THEN** the structure check fails naming the package, and passes once a row claims it
- **Case**: `StructureDeclarationTest#defaultDenyFailsForANewPackage`

### Requirement: concept is self-contained

A file in `concept` SHALL NOT import any other `com.naviveylin` package, and SHALL NOT import
`android.*` or `androidx.*`. A concept holds what things are made of — geometry, units, time,
manoeuvre and route vocabulary — and depends on nothing of ours.

#### Scenario: A concept file importing another kind

- **WHEN** a `concept` file imports a `port`, `feature`, `platform` or `shell` package
- **THEN** the structure check fails naming the file, the import and the concept's "imports nothing" row
- **Case**: `ConceptRuleTest#conceptImportsNothingOfOurs`

#### Scenario: A concept file importing the Android framework

- **WHEN** a `concept` file imports `android.graphics.Canvas`
- **THEN** the structure check fails naming that import
- **Case**: `ConceptRuleTest#conceptImportsNoAndroidType`

### Requirement: port holds abstraction only

A file in `port` SHALL declare an interface or a type used by one, SHALL NOT import any
`com.naviveylin` package other than `concept`, and SHALL NOT import `android.*` or `androidx.*`. A port
is the OS or hardware abstracted, so it cannot name the framework it abstracts.

#### Scenario: A port carrying an Android type

- **WHEN** a `port` file imports `android.content.Context`
- **THEN** the structure check fails naming the file and that import
- **Case**: `PortRuleTest#portImportsNoAndroidType`

#### Scenario: A port reaching into a feature

- **WHEN** a `port` file imports a `feature/<name>` package
- **THEN** the structure check fails naming both
- **Case**: `PortRuleTest#portImportsOnlyConcept`

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

### Requirement: a platform package mirrors a feature

A file in `platform/<name>` SHALL import `feature/<name>`, and SHALL NOT import `platform/<other>`. A
platform package holds everything bound to a framework or the OS for that feature — its storage, its
services and every composable and ViewModel it shows.

#### Scenario: A platform package serving another feature's UI

- **WHEN** a `platform/search` file imports a `platform/map` package
- **THEN** the structure check fails, because hosting belongs to `shell`
- **Case**: `PlatformRuleTest#platformImportingSiblingPlatformFails`

#### Scenario: A platform package with no matching feature

- **WHEN** a declaration row names `platform/<name>` and no `feature/<name>` exists
- **THEN** the structure check fails naming the missing feature
- **Case**: `PlatformRuleTest#platformWithoutFeatureFails`

### Requirement: shell is the only kind that hosts several features

`shell` SHALL be the only kind permitted to import more than one `feature/<name>` and more than one
`platform/<name>`. A file that assembles several features on one screen, wires the process, starts an
activity, declares the navigation graph or selects a port implementation SHALL belong to `shell`.

#### Scenario: A screen assembling several features

- **WHEN** a file imports six `feature/<name>` packages to compose one screen
- **THEN** the structure check requires it to be declared under `shell` and passes
- **Case**: `ShellRuleTest#shellMayHostSeveralFeatures`

#### Scenario: A feature composing several features

- **WHEN** a file declaring two `feature/<name>` imports is declared under `feature/`
- **THEN** the structure check fails, because only `shell` may host
- **Case**: `ShellRuleTest#featureHostingTwoFeaturesFails`
