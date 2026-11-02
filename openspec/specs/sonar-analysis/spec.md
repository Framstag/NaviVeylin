# sonar-analysis Specification

## Purpose
What this repository sends to SonarQube Cloud for the project `Framstag_NaviVeylin`: the workflow that
produces the analysis and the only refs it may run from, the declared source boundary that decides what is
measured as this project's code, the coverage this build already measures that is handed to the scanner, and
the rule that the quality gate reports without gating.

## Requirements

### Requirement: The analysis measures this repository's sources and nothing else

The scan's source boundary SHALL be declared in the repository, not inferred: the Kotlin and Java sources
this project owns are in scope, and the `libosmscout` submodule tree, the vendored client API compiled inside
`:osmscout-client-java`, the tooling and documentation trees, and all build outputs are excluded. A finding,
a line count or a coverage ratio the analysis reports SHALL therefore be attributable to code this repository
owns.

#### Scenario: An upstream file is outside the analysis

- **WHEN** the analysis of a commit is read for the submodule's native tree (`app/src/main/cpp/libosmscout`) and for that submodule's Java client sources
- **THEN** neither appears among the analysed files, and neither contributes to the reported line count
- **Case**: `sonar-workflow#theNativeTreeIsExcluded`

#### Scenario: An owned module is inside the analysis

- **WHEN** the analysis of a commit is read for `:app`, `:auto`, `:core`, `buildSrc` and `:osmscout-client-java`'s own Java sources
- **THEN** each of those modules appears among the analysed files with a non-zero line count
- **Case**: none — no case: the analysed set is only observable in a real scan; read it from that scan's file list (task 9.2)

#### Scenario: Generated code is outside the analysis

- **WHEN** the analysed file list is read for `BuildConfig`, `R` classes and Dagger/Hilt-generated classes
- **THEN** no such class appears, matching the exclusions the coverage reports already apply
- **Case**: `sonar-workflow#generatedCodeIsExcluded`

### Requirement: The scan is produced by CI from the main branch

The analysis SHALL be produced by a workflow in this repository, and that workflow SHALL offer `push` on
`main` and an explicit dispatch of `main` as its only ways to run. No pull-request event SHALL produce a
scan, so no contributor's unchecked-out code is analysed with the scanner credential in scope.

#### Scenario: A push to main produces a scan

- **WHEN** a commit is pushed to `main`
- **THEN** the scan workflow runs and its analysis appears on the project's `main` branch
- **Case**: `sonar-workflow#theTriggerSetIsPushAndDispatch`

#### Scenario: A pull request produces no scan

- **WHEN** a pull request targeting `main` is opened or updated
- **THEN** the scan workflow does not run for that event, and the project's pull-request view gains no analysis from it
- **Case**: `sonar-workflow#aPullRequestTriggerIsReported`

#### Scenario: A dispatch of another branch is refused

- **WHEN** the scan workflow is dispatched for a ref other than `main`
- **THEN** the run refuses before the scanner runs and names the ref it was asked for
- **Case**: `sonar-workflow#theDispatchIsGuardedToMain`

### Requirement: The scan reads the coverage the test run already measures

The analysis SHALL import the per-module coverage this build produces — the Kover XML report of each Kotlin
module the root configuration aggregates, and the JaCoCo report of `:osmscout-client-java`'s own sources — and
SHALL NOT import the root merged Kover report or the module's vendored-provenance report. A covered line the
project's own test suite executed SHALL therefore be attributed to the file that line lives in, per module.

#### Scenario: Coverage reaches the module it was measured in

- **WHEN** the coverage of a Kotlin module is read in the analysis after a scan of a tested commit
- **THEN** the ratio is the one that module's own report states, and not a merged figure applied to every module
- **Case**: none — no case: a per-module ratio is only observable in a real scan; read it against the coverage summary for the same commit (task 9.4)

#### Scenario: Vendored coverage is not attributed to this project

- **WHEN** the coverage of `:osmscout-client-java` is read in the analysis
- **THEN** it reflects that module's own sources only, and the submodule's Java client API contributes neither covered nor uncovered lines
- **Case**: `sonar-workflow#theVendoredReportIsNotListed`

#### Scenario: A missing report is reported rather than ignored

- **WHEN** the scan runs against a commit whose coverage reports the test step did not produce
- **THEN** the run names the report it could not read instead of publishing a zero coverage figure
- **Case**: none — no case: the observable is the coverage step's non-zero exit when a report task did not run (task 2.3)

### Requirement: The quality gate colours the project and does not fail the build

The scan SHALL publish the quality gate's result without waiting for it to pass, so a gate the analysis finds
red leaves the workflow green and colours the project's badge, matching this project's rule that coverage
gates nothing. A scan that the scanner itself could not complete SHALL fail the workflow instead, so an
unreadable or unauthenticated analysis is never reported as a successful one.

#### Scenario: A red gate leaves the workflow green

- **WHEN** a scan completes against a commit whose quality gate result is failing
- **THEN** the workflow's conclusion is success and the project's quality-gate badge shows the failing measure
- **Case**: `sonar-workflow#theGateIsNotWaitedFor`

#### Scenario: A scan that could not run fails the workflow

- **WHEN** the scan cannot be completed, whether through a refused credential, an unreachable server, or an unreadable configuration
- **THEN** the workflow's conclusion is failure and the reason names what could not be read or reached
- **Case**: `sonar-workflow#theScanStepIsNotContinueOnError`

### Requirement: The analysis is refused while the project has automatic analysis enabled

SonarQube Cloud refuses a scanner run while its automatic analysis is enabled for the project, and the failure
message it returns is not otherwise actionable from this repository. The run SHALL therefore fail visibly and
name that setting as the reason, so the project configuration is corrected rather than the workflow.

#### Scenario: The setting reads as the failure's cause

- **WHEN** a scan runs while the project has automatic analysis enabled and the server refuses it
- **THEN** the workflow fails and the reported reason names automatic analysis, not a code or credential fault
- **Case**: none — no case: read from the failure text of the first refused run (task 9.1)

### Requirement: The scanner action's runtime is recorded where every action's is

The workflow SHALL pin the scanner action to a released major whose committed action metadata declares the
Node runtime this project requires, and that floor SHALL be recorded in the check that gates every workflow
pin, so the pin is held to the same rule as the actions `build.yml` already uses.

#### Scenario: The new pin passes the runtime check

- **WHEN** the action-runtime check runs over `.github/workflows/` after this change
- **THEN** it reports the scanner action's pin at or above its recorded floor and exits successfully
- **Case**: `action-runtimes#theRepositorysOwnPinsPass`

#### Scenario: An unrecorded scanner pin is reported

- **WHEN** the pin's row is removed from the check's floor table
- **THEN** the check reports that action as carrying no recorded Node runtime and fails
- **Case**: `action-runtimes#anActionWithNoRecordedRuntimeFails`

### Requirement: The badge the README shows is this project's measure

`README.md` SHALL carry the quality-gate, coverage and lines-of-code badges for the project key this scan
publishes to, so the badge a reader clicks resolves to the same project the workflow analyses and not to
another repository's.

#### Scenario: Every badge names the analysed project

- **WHEN** the three badge links in `README.md` are read
- **THEN** each requests its measure for the project key the scan workflow analyses, and links to that project's summary
- **Case**: none — no case: read the three badge URLs in `README.md` (task 6.4)
