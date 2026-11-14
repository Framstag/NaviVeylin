# architecture-test-suite

## Purpose

The package-structure rules are enforced by an automated check that runs with the ordinary test task, so
a violation is a red test naming the file and the rule rather than a review finding, and a structure
declared once stays true as the tree changes.

## ADDED Requirements

### Requirement: The structure rules run as a unit test

The structure rules SHALL be verified by an automated check that runs as part of the project's unit-test
task on the JVM, with no Android device, no emulator, no native build and no application build required.
A run of the check alone SHALL complete without compiling the app.

#### Scenario: The check runs without a device or an app build

- **WHEN** the structure check is invoked on a clean checkout with no Android device attached
- **THEN** it completes and reports a verdict
- **Case**: `ArchitectureSuiteTest#suiteRunsWithoutDeviceOrAppBuild`

#### Scenario: A violation fails the check

- **WHEN** a source file violates one of the declared rules
- **THEN** the check reports failure and the project's test task fails
- **Case**: `ArchitectureSuiteTest#violationFailsTheTestTask`

### Requirement: The check reads every source set of the three modules

The check SHALL evaluate the main and the test source files of `:app`, `:auto` and `:core`, including
files that belong to no compiled artifact reachable from a test classpath. A violation in a test source
SHALL be reported like a violation in a main source.

#### Scenario: A violation in a test source

- **WHEN** a file under `:app`'s test sources violates a declared rule
- **THEN** the check fails naming that file
- **Case**: `StructureScopeTest#violationInTestSourceIsReported`

#### Scenario: An application module is covered

- **WHEN** a file in `:app`, which is an application module and not a library, violates a declared rule
- **THEN** the check fails naming that file
- **Case**: `StructureScopeTest#violationInAppModuleIsReported`

### Requirement: A violation names the file, the import and the rule

Every reported violation SHALL name the offending file, the import or declaration that breaks the rule,
the kind the file is declared as, and the declaration row or rule the violation contradicts. A report
that names a rule without the file, or a file without the rule, SHALL be treated as a defect in the
check.

#### Scenario: A disjunction violation is named completely

- **WHEN** a `feature/navigation` file imports a `feature/route` package
- **THEN** the report names the file, the imported package, the file's declared kind and the disjunction rule
- **Case**: `ViolationReportTest#violationNamesFileImportAndRule`

### Requirement: The baseline is shrink-only

Files that do not yet satisfy a rule SHALL be exempted through a named baseline entry that records the
file and the change that will move it. A baseline entry whose file no longer violates the rule it is
exempted from SHALL fail the check, so the baseline can only shrink and never becomes a record of
accepted structure.

#### Scenario: A stale exemption fails

- **WHEN** a file listed in the baseline no longer violates the rule it was exempted from
- **THEN** the check fails naming that entry, and passes once the entry is removed
- **Case**: `StructureBaselineTest#staleExemptionFails`

#### Scenario: An exemption without an owning change

- **WHEN** a baseline entry names no change that will remove it
- **THEN** the check fails naming that entry
- **Case**: `StructureBaselineTest#exemptionWithoutChangeFails`

### Requirement: A green run reports what it verified

A passing run SHALL report the number of declaration rows read, the number of source files classified,
the number of exemptions still outstanding and the number of rules evaluated, so that a run which
verified nothing cannot pass silently.

#### Scenario: The figures are reported

- **WHEN** the check passes
- **THEN** its output states the declaration row count, the classified file count, the outstanding
  exemption count and the rule count
- **Case**: `StructureReportTest#greenRunPrintsFigures`

#### Scenario: An empty declaration cannot pass

- **WHEN** the declaration holds no rows while source files exist
- **THEN** the check fails rather than reporting zero violations
- **Case**: `StructureDeclarationTest#emptyDeclarationFails`

### Requirement: The check is part of a gate run

The structure check SHALL be part of the project's verification gate, so that a change cannot reach the
main branch with a structure violation, and the gate's record SHALL name it among the suites it ran.

#### Scenario: The gate names the check

- **WHEN** a gate run is recorded
- **THEN** the structure check appears among the suites the run covered
- **Case**: `GateSuiteListTest#gateSuiteListIncludesArchitecture`

#### Scenario: The gate fails on a violation

- **WHEN** a branch carries a structure violation
- **THEN** the gate run fails at the structure check
- **Case**: `GateSuiteListTest#gateFailsAtStructureCheck`

### Requirement: Running a module's tests runs the structure check

Every unit-test task of `:app`, `:auto` and `:core` SHALL run the structure check first, so that a focused
test run cannot succeed on a tree that violates a structure rule. A module's test task SHALL NOT be able
to report success in an invocation where the structure check did not run.

#### Scenario: A focused test run on a violating tree fails

- **WHEN** a contributor runs one test class of `:app` on a tree that violates the disjunction rule
- **THEN** the invocation fails at the structure check and the test class never reports a pass
- **Case**: `StructureHookTest#focusedTestRunOnViolatingTreeFails`

#### Scenario: A satisfying tree runs its tests normally

- **WHEN** the tree satisfies every rule and one test class of `:core` is run
- **THEN** the structure check passes and that test class's own result stands
- **Case**: `StructureHookTest#satisfyingTreeRunsTestsNormally`

### Requirement: The baseline is the only exemption

The check SHALL NOT honour a suppression annotation, a comment directive or any per-file opt-out in the
checked source. The named baseline SHALL be the only way to exempt a file, so that every exemption is
enumerable in one place and carries the change that removes it.

#### Scenario: An in-code suppression does not exempt

- **WHEN** a source file carries a suppression annotation naming the structure check and violates a rule
- **THEN** the violation is still reported
- **Case**: `StructureBaselineTest#suppressionAnnotationDoesNotExempt`

#### Scenario: A baselined file is counted, not reported

- **WHEN** a file is exempted through the baseline
- **THEN** it is not reported as a violation and it appears in the outstanding-exemption count
- **Case**: `StructureBaselineTest#baselinedFileIsCountedNotReported`

### Requirement: The check fails closed

When the structure declaration is missing, unreadable, unparsable, or holds no rows while source files
exist, the check SHALL fail. A check that cannot determine the structure SHALL NOT report a pass.

#### Scenario: The declaration is deleted

- **WHEN** the declaration file is removed from the tree
- **THEN** the check fails naming the missing file rather than reporting no violations
- **Case**: `StructureDeclarationTest#missingDeclarationFails`

#### Scenario: The declaration is unparsable

- **WHEN** a declaration row carries a kind that is not one of the five
- **THEN** the check fails naming that row
- **Case**: `StructureDeclarationTest#unknownKindFails`
