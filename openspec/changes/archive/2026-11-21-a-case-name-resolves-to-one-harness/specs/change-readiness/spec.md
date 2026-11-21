# Spec Delta — change-readiness

## ADDED Requirements

### Requirement: A scenario's case name resolves to exactly one harness

Every `**Case**:` line in a shipped spec SHALL name a case that exactly one declared harness runs. A name no harness lists SHALL be a refusal naming the spec file, the scenario and the name; a name two harnesses list SHALL be a refusal naming both.

#### Scenario: A name the loop suite runs resolves

- **WHEN** the check reads a shipped scenario whose case is one the loop suite declares
- **THEN** the name resolves to the loop harness and the verdict carries no refusal
- **Case**: `traceability#aNameTheLoopSuiteRunsResolves`

#### Scenario: A name another harness runs resolves

- **WHEN** a shipped scenario names a case the sensors suite, the issue tooling's Java cases or a Gradle suite runs
- **THEN** the name resolves to that harness, so a comparison against one suite alone reports no false dead reference
- **Case**: `traceability#aNameAnotherHarnessRunsResolves`

#### Scenario: A name no harness runs is refused

- **WHEN** a scenario names a case that no declared harness lists
- **THEN** the check refuses, naming the spec file, the scenario's line and the name
- **Case**: `traceability#aNameNoHarnessRunsIsRefused`

### Requirement: A shared group is declared, and a duplicate full name is refused

Each harness's claimed case groups SHALL be declared in one registry, so a group two harnesses share is a declared fact rather than an accident. An identical full name in two harnesses SHALL be refused, naming both, and a registry row whose case list is absent SHALL be refused rather than read as an empty harness.

#### Scenario: A shared group is declared

- **WHEN** two harnesses claim the same group
- **THEN** the registry declares both claims, the check prints the group once, and no refusal is raised for the group itself
- **Case**: `traceability#aSharedGroupIsDeclared`

#### Scenario: A duplicate full name is refused

- **WHEN** two harnesses list the same full case name
- **THEN** the check refuses, naming the name and both harnesses
- **Case**: `traceability#aDuplicateFullNameIsRefused`

#### Scenario: A registry row whose list is absent is refused

- **WHEN** the registry names a harness whose case-list file or command does not exist
- **THEN** the check refuses and names the row, rather than resolving against three harnesses and reporting success
- **Case**: `traceability#aRowWhoseListIsAbsentIsRefused`

#### Scenario: A row that emits a group it never claimed is refused

- **WHEN** a harness prints a case name whose group the registry does not declare for that row
- **THEN** the check refuses and names the row and the name, so a case cannot enter the resolution set unclaimed
- **Case**: `traceability#aRowThatEmitsAnUnclaimedGroupIsRefused`

#### Scenario: An exclusion that hides a name is refused

- **WHEN** a row excludes a name and no other row declares it
- **THEN** the check refuses, because an exclusion is a delegation and not a hiding place
- **Case**: `traceability#anExclusionThatHidesANameIsRefused`

### Requirement: A case no scenario claims is reported as a number, and refuses nothing

The check SHALL count and print the cases the declared harnesses run that no shipped scenario names. It SHALL NOT refuse one, because a case may prove the harness rather than a requirement, and the count SHALL be part of the one verdict line so a reader always sees it.

#### Scenario: The unclaimed cases are counted

- **WHEN** the check runs on a tree where a harness runs a case no scenario names
- **THEN** the verdict line carries the count and `--json` carries the list, and the exit status stays 0
- **Case**: `traceability#theUnclaimedCasesAreCounted`

#### Scenario: An unclaimed case never refuses

- **WHEN** every case of one harness is unclaimed
- **THEN** the check still exits 0 and prints the count, so a harness-level case is not a defect
- **Case**: `traceability#anUnclaimedCaseNeverRefuses`

### Requirement: The check reads names, never meaning

The check SHALL compare identifiers against declared lists and SHALL NOT decide any verdict from the meaning of a sentence. A case-shaped string inside prose, and a scenario the readiness checker already owns, SHALL NOT become this check's subject.

#### Scenario: A name in prose is not a claim

- **WHEN** a spec's body mentions a case-shaped name outside a `**Case**:` line
- **THEN** the check ignores it, and a name that exists nowhere in prose does not cause a refusal
- **Case**: `traceability#aNameInProseIsNotAClaim`

#### Scenario: A scenario with no case line is not this check's subject

- **WHEN** a scenario carries no `**Case**:` line
- **THEN** this check says nothing about it, and the readiness checker's own refusal is where that is reported
- **Case**: `traceability#aScenarioWithoutACaseLineIsNotThisChecksSubject`
