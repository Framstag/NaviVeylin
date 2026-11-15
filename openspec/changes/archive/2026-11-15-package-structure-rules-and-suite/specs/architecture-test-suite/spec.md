# architecture-test-suite

## Purpose

The package-structure rules are enforced by an automated check that runs with the ordinary test task, so
a violation is a red test naming the file and the rule rather than a review finding, and a structure
declared once stays true as the tree changes.

## ADDED Requirements

### Requirement: A violation names the file, the import and the rule

Every reported violation SHALL name the offending file, the import or declaration that breaks the rule,
the kind the file is declared as, and the declaration row or rule the violation contradicts. A report
that names a rule without the file, or a file without the rule, SHALL be treated as a defect in the
check.

#### Scenario: A disjunction violation is named completely

- **WHEN** a `feature/navigation` file imports a `feature/route` package
- **THEN** the report names the file, the imported package, the file's declared kind and the disjunction rule
- **Case**: `FeatureRuleTest#violationNamesFileImportAndRule`