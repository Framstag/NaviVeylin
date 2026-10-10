# Spec Delta

## Purpose

States the project's size and its architecture-relevant distribution — per language, module, package, source
set and file — as a complete printed report for manual inspection, so a size or share claim about this project
can be read from a run instead of estimated, and so a figure is attributed to the tree it describes.

## ADDED Requirements

### Requirement: A report run measures the project on disk

One report run SHALL measure the files present in the scanned scope on disk. Its figures SHALL NOT depend on
version-control state or on the availability of version-control tooling, so the same tree measured in a local
working state, in a clean checkout, and in an export without version-control metadata yields the same figures.

#### Scenario: Uncommitted files are measured

- **WHEN** a source file exists in the scope but is not known to version control
- **THEN** its lines appear in the report

#### Scenario: No version-control tooling is needed

- **WHEN** the scope holds no version-control metadata and no version-control command is available
- **THEN** the report is produced and the run exits 0

### Requirement: A report states the run it describes

Every report SHALL open with a header naming the run's date, the scanned scope, the number of files measured,
and the excluded classes. A figure SHALL never be readable without the header it belongs to.

#### Scenario: Header names the subject

- **WHEN** a report is printed
- **THEN** its header names date, scope, files measured and excluded classes before any metric section

#### Scenario: Files measured tracks the scope

- **WHEN** one in-scope file is added to the tree
- **THEN** the files-measured figure is exactly one higher

### Requirement: Exclusions are decisions, not silence

Generated trees — build outputs, caches, dependency install trees and version-control internals — SHALL be
excluded from the figures, and vendored third-party source SHALL be classified as external instead of counted
with project figures. Every exclusion SHALL be stated in the header with its class and path, and no excluded
file SHALL contribute a figure or appear in a list.

#### Scenario: An excluded class is stated

- **WHEN** a report is printed for a scope that holds an excluded class
- **THEN** the header names that class and the path it removed

#### Scenario: Vendored source is external

- **WHEN** a vendored source tree is present in the scope
- **THEN** its files contribute no project figure and the header names it as external

#### Scenario: Excluded files are never listed

- **WHEN** an excluded tree holds a file that would otherwise be counted
- **THEN** no path inside that tree appears in any list of the report

### Requirement: Unrecognized layout is reported and refused

A source file whose path does not match the expected module, source-root and package layout SHALL be listed
with its path in an unclassified bucket and SHALL NOT be attributed to a module, package or layer figure. A
scan scope whose shape is not recognized at all SHALL be refused with exit status 2 and a message naming the
unrecognized path, and no figures SHALL be printed.

#### Scenario: An unexpected file stays visible

- **WHEN** a source file sits outside every recognized source root
- **THEN** the report lists its path in an unclassified bucket and no module, package or layer row claims its lines

#### Scenario: An unrecognizable root is refused

- **WHEN** the scan scope does not hold the expected source layout
- **THEN** the run refuses with exit status 2, names the offending path, and prints no figures

### Requirement: The report is complete

A run SHALL list every package and every source file within the scanned scope. Documentation and OpenSpec
store files SHALL be counted and aggregated rather than listed one by one, and the report SHALL state how many
of them it aggregated. No list SHALL be truncated and no option SHALL reduce the report's content; beside scan
scope paths, the run's only interface SHALL be a help output.

#### Scenario: Every source file appears

- **WHEN** a scope holds N files, of which D are documentation or OpenSpec store files
- **THEN** the source file list holds N minus D rows and the report states how many files it aggregated

#### Scenario: Documentation is aggregated, not listed

- **WHEN** the scope holds documents and OpenSpec store files
- **THEN** no path of either appears in a per-file list and each aggregate is printed with its own file and line count

#### Scenario: No truncation

- **WHEN** the scope holds more packages than fit on one screen
- **THEN** all of them are listed

#### Scenario: An unsupported option is refused

- **WHEN** a run is given an option the tool does not define
- **THEN** it exits 2 with usage output and prints no report

### Requirement: Figures are per dimension and additive

Every size figure SHALL be reported per language, per module, per package path, per first-level layer and per
file. A child dimension's figures SHALL sum to its parent's figure, and every figure SHALL separate code,
comment and blank lines whose sum is that file's total line count.

#### Scenario: Every figure column is headed

- **WHEN** a per-dimension table is printed
- **THEN** every column of figures carries a header, including the blank-line column

#### Scenario: Dimension sums agree

- **WHEN** a module's per-package figures are summed
- **THEN** the sum equals the module's figure

#### Scenario: Line kinds sum to the total

- **WHEN** a file's code, comment and blank figures are summed
- **THEN** the sum equals that file's total line count

### Requirement: Source sets are separated

Figures SHALL distinguish the source set they come from — production sources, unit tests, instrumented tests
and documentation — so that a test-share claim can be read from the report alone.

#### Scenario: Test and production are split

- **WHEN** a scope holds production sources and test sources
- **THEN** their figures are reported separately and are not merged into one row

#### Scenario: Documentation is its own kind

- **WHEN** a scope holds Markdown documents
- **THEN** they are reported as documentation rather than as source code

### Requirement: Derived ratios are defined

The report SHALL state each derived ratio it prints together with the figures it is computed from — comment
share, test share, documentation share, and average lines per file — so that a printed ratio can be
reproduced by hand from the same report.

#### Scenario: Test share excludes documentation

- **WHEN** the test share is printed
- **THEN** its printed denominator excludes documentation code lines

#### Scenario: A printed ratio is reproducible

- **WHEN** a printed ratio is recomputed from the printed figures
- **THEN** the recomputed value agrees with the printed one

#### Scenario: The operands are named

- **WHEN** a ratio is printed
- **THEN** the report names the figures that ratio was computed from

### Requirement: Platform share is reported

The report SHALL report the share of code serving the phone surface and the share serving the car surface,
computed from the recognized layout rather than from a hand-maintained list of paths.

#### Scenario: Both surfaces are named

- **WHEN** a scope holds phone code and car code
- **THEN** both shares are printed together with the figures they were computed from

#### Scenario: An empty surface is not omitted

- **WHEN** one of the two surfaces holds no code in the scope
- **THEN** its share is printed as zero rather than left out

### Requirement: The OpenSpec inventory is read from the CLI

The report SHALL state the count of open changes, archived changes and feature directories, and SHALL obtain
those counts from the OpenSpec CLI's own JSON output rather than by walking or parsing a store. When the CLI
is unavailable, the section SHALL state that it is unavailable instead of printing a count.

#### Scenario: The CLI is the source

- **WHEN** the store's directories and the CLI's reported inventory disagree
- **THEN** the printed counts are the CLI's

#### Scenario: A missing CLI does not fail the run

- **WHEN** the OpenSpec CLI is not available in the environment
- **THEN** the report marks the section as unavailable and the run exits 0

#### Scenario: A nested store is not this project's inventory

- **WHEN** the scope contains another OpenSpec store
- **THEN** the printed counts describe only this project's store

### Requirement: Repeated runs are stable

Two runs over an unchanged tree SHALL print the same report apart from the header's date. Every list SHALL be
ordered by its figure with ties ordered by name, and that order SHALL NOT depend on the environment's locale.

#### Scenario: Byte-identical apart from the date

- **WHEN** two runs follow each other with no change to the tree
- **THEN** their output is identical except for the header date

#### Scenario: Ties are ordered by name

- **WHEN** two entries hold the same figure
- **THEN** they are printed in a stable name order

#### Scenario: Locale does not reorder

- **WHEN** the run's environment locale changes
- **THEN** the order of every printed list is unchanged

### Requirement: The exit status is an outcome, not a verdict

A successful run SHALL exit 0. A usage error or an environment error — an unrecognized scope, a missing
required tool — SHALL exit 2. The run SHALL NOT exit 1: the report is an observation and SHALL NOT behave as a
threshold gate.

#### Scenario: A printed report exits 0

- **WHEN** a report is printed
- **THEN** the run exits 0

#### Scenario: A bad scope exits 2

- **WHEN** a scan scope path does not exist
- **THEN** the run exits 2 with a message naming that path

#### Scenario: Figures never decide the status

- **WHEN** the figures differ between two runs
- **THEN** both runs exit 0

### Requirement: The counting rules are covered without a device

The report's counting rules SHALL be covered by a self-test that runs without a connected device, emulator,
head unit or network access, over a fixture tree that exercises the layout variants, the exclusion classes and
the refusal paths. Each rule SHALL be named by the case that fails when it is broken.

#### Scenario: No device and no network

- **WHEN** the self-test runs
- **THEN** it requires no connected device, emulator or head unit, and no network access

#### Scenario: Layout variants are covered

- **WHEN** the fixture tree holds a nested package, a file with no package path, a foreign package and a
  Kotlin source root
- **THEN** a named case asserts each of those outcomes

#### Scenario: Refusal paths are covered

- **WHEN** a fixture scope holds an unrecognizable layout
- **THEN** a case asserts the refusal and its exit status

#### Scenario: A broken rule fails a named case

- **WHEN** one counting rule is mutated
- **THEN** the case that names that rule fails
