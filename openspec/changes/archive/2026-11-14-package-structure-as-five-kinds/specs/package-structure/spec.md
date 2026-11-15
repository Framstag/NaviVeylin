# package-structure

## Purpose

The main sources of `:app`, `:auto` and `:core` are checked against one machine-readable declaration of
their packages, so that a package the declaration does not claim fails the check instead of passing
unnoticed.

## ADDED Requirements

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
