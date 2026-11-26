# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The core is compiled once per source digest, and a missing compiler is one refusal

The core SHALL be compiled on demand with a JDK 17 or newer, into a cache keyed by the digest of its own sources, so a second call with the same sources compiles nothing. A host without a usable compiler SHALL produce one refusal naming the requirement, not a partial run.

#### Scenario: The digest is the cache key

- **WHEN** the core is compiled, then called again with its sources unchanged
- **THEN** the second call compiles nothing and the cache directory the digest names is reused
- **Case**: `core#theDigestIsTheCompileKey`

#### Scenario: One compile serves the whole suite

- **WHEN** a suite of several cases each calls the core
- **THEN** exactly one compile happens, at the first case that asks, and the summary names it once
- **Case**: `core#oneCompilePerDigest`

#### Scenario: No compiler is one refusal

- **WHEN** the host has no usable JDK and a case asks for the core
- **THEN** the engine prints one line naming the JDK it needs and exits 1, and no item is started
- **Case**: `core#aMissingCompilerIsOneRefusal`

### Requirement: A verb answers one JSON document, and a malformed input is a refusal

Each verb SHALL answer one JSON document and nothing else on stdout. An input it cannot parse SHALL be refused with an exit code and a line naming the field and the file, and the verb SHALL NOT answer a partially read value.

#### Scenario: The answer is one document

- **WHEN** a verb is called with a well-formed fixture
- **THEN** stdout is one JSON document and nothing else
- **Case**: `core#theAnswerIsOneJsonDocument`

#### Scenario: A malformed input is a refusal

- **WHEN** a verb is handed an input that is not JSON
- **THEN** it exits non-zero naming the file and writes nothing usable to stdout
- **Case**: `core#aMalformedInputIsARefusal`

#### Scenario: No partial value reaches the caller

- **WHEN** an input parses as far as a field and then fails
- **THEN** the verb answers nothing, so no reader can mistake half a document for a whole one
- **Case**: `core#noPartialParseOnFailure`

### Requirement: The state is one document of declared fields, and a field it does not declare is a refusal

The run's state SHALL be one JSON document whose top-level fields the core declares, and the core SHALL be its writer and its reader: a write records an item's fields under the declaration that names them, and a document carrying a field the core does not declare SHALL be refused with an exit code and a line naming that field, answering nothing on stdout.

#### Scenario: The core writes the state it reads

- **WHEN** an item's verdict and reason are written through the core and the counts are read back
- **THEN** the document holds the item at the path the declaration names, and the count the write changed is the one the read answers
- **Case**: `core#theStateIsWrittenByTheCore`

#### Scenario: An undeclared field is refused

- **WHEN** a state document carries a field the core does not declare
- **THEN** the read exits non-zero naming that field and answers nothing on stdout
- **Case**: `core#theStateAReadCannotParseIsRefused`

### Requirement: The core is an engine file, so the portability check and the case list read it

The core SHALL name no project value and SHALL be read by the portability check as every other engine file is. Its cases SHALL declare it the way a case declares the script it drives.

#### Scenario: A project value in the core is refused

- **WHEN** the portability check reads the core
- **THEN** it names the core as a subject and a core file that names a project value fails the check
- **Case**: `core#thePortabilityCheckReadsTheCore`

#### Scenario: The core's cases declare the core

- **WHEN** the suite reads a `core#` case's declared files
- **THEN** the declaration names the core directory, and a declaration that omits it fails the case-list check
- **Case**: `core#theCoresCasesDeclareTheCore`

### Requirement: The core's cases are the core's own, and the suite lists them with the shell's

A core case SHALL exist in the core's own case list, and the suite SHALL ask that list rather than a second spelling of it. Both lists SHALL be pooled, selected and summarised as one, so a core case is never a case that exists but does not run.

#### Scenario: The core lists its own cases

- **WHEN** the suite starts and asks the core for its case list
- **THEN** every case the list names runs, and a case the core defines but the list omits fails the case-list check
- **Case**: `core#theCoreListsItsOwnCases`

#### Scenario: One summary carries both sources

- **WHEN** a suite run ends
- **THEN** its summary counts the core's cases and the shell's together, and a case selected by name runs whichever source holds it
- **Case**: `core#oneSummaryCarriesBothSources`

#### Scenario: A core case declares the files it can reach

- **WHEN** a core case carries no declared files
- **THEN** the case-list check fails and names the case
- **Case**: `core#aCoreCaseDeclaresItsFiles`
