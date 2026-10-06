# style-load-diagnostics Specification

## Purpose
Defines how a native map-style load reports the type names it cannot resolve against a database's type
configuration: one condensed, deduplicated report per load that keeps the finding visible while keeping
the log usable, with the complete per-occurrence findings still available to callers. The report is
produced by the shared native library, so phone and Android Auto sessions receive identical output.

## Requirements

### Requirement: A style load reports unresolved type names in one condensed line

WHEN a stylesheet is loaded and references type names that the database's type configuration does not
carry — node/area types and way types alike — the load SHALL emit exactly one warning line for that
load, naming the style file, the exact number of distinct unresolved type names, and a bounded sample
of them. However often the stylesheet references one unresolved name, that name SHALL contribute at
most one entry to the count and at most one mention to the sample.

#### Scenario: Many references to few names

- **WHEN** a stylesheet references 3 names the type configuration lacks, across 500 rule occurrences
- **THEN** exactly one line is emitted for that load
- **AND** it states 3 distinct unresolved names

#### Scenario: Every referenced name resolves

- **WHEN** every type name a stylesheet references is carried by the type configuration
- **THEN** no such line is emitted for that load

#### Scenario: Sample is bounded

- **WHEN** a stylesheet references 200 distinct unknown names
- **THEN** the emitted line states 200
- **AND** it lists no more names than the configured sample bound
- **AND** it ends by naming how many names it left out

#### Scenario: Sample order is deterministic

- **WHEN** the same stylesheet is loaded twice
- **THEN** both lines list the same names in the same order

#### Scenario: Both kinds of type land in one report

- **WHEN** one load leaves both unknown node/area types and unknown way types
- **THEN** a single line reports them together
- **AND** its count is the total number of distinct unresolved names of both kinds

#### Scenario: An included module reports its own line

- **WHEN** a stylesheet includes a module whose own load references unknown type names
- **THEN** the module's load emits one line reporting its own distinct unresolved names
- **AND** the including load emits its own line only for the unresolved names it saw itself

### Requirement: The report does not change the load outcome or the recorded findings

The condensed report SHALL NOT change whether a style load succeeds, SHALL NOT change the findings the
load records for its callers (every unresolved reference stays recorded with its own position), and
SHALL NOT demote or suppress the finding: it remains a warning.

#### Scenario: A stylesheet with unresolved names still loads

- **WHEN** a stylesheet references unknown type names and is otherwise valid
- **THEN** the load reports success
- **AND** the stylesheet becomes the active style

#### Scenario: Findings stay per reference

- **WHEN** a stylesheet references the same unknown name at three positions
- **THEN** the findings available to the caller still contain three entries
- **AND** each entry keeps its own position

#### Scenario: The finding stays a warning

- **WHEN** the report is emitted
- **THEN** it is emitted at the warning level
- **AND** it is not moved to a debug or lower level

#### Scenario: A stylesheet with a hard error still fails

- **WHEN** a stylesheet references unknown type names and also carries a hard error
- **THEN** the load still reports failure
- **AND** no style is adopted from it

### Requirement: The complete unresolved-name list stays obtainable

The complete list of unresolved type names SHALL stay obtainable from the loaded style configuration
through the findings it records, and a load SHALL additionally emit the full list of distinct
unresolved names when debug logging is enabled, and SHALL NOT emit that full list when debug logging is
disabled.

#### Scenario: Debug logging disabled

- **WHEN** a load with unresolved names runs with debug logging disabled
- **THEN** the one condensed line is emitted
- **AND** the full per-name list is not emitted

#### Scenario: Debug logging enabled

- **WHEN** a load with unresolved names runs with debug logging enabled
- **THEN** every distinct unresolved name is emitted in addition to the condensed line

### Requirement: Log-line count does not grow with references to an already-reported name

The number of log lines a load emits for unresolved type names SHALL NOT grow with how often a name is
referenced: it SHALL stay at a bounded constant per load, independent of the reference count.

#### Scenario: One name referenced a thousand times

- **WHEN** a stylesheet references one unknown name 1000 times
- **THEN** the load emits one line for it, not 1000

#### Scenario: Fifty distinct names

- **WHEN** a stylesheet references 50 distinct unknown names once each
- **THEN** the load still emits one line

### Requirement: Consecutive loads report independently

Each style load SHALL report the unresolved names it encountered itself: it SHALL NOT suppress a name
because an earlier load in the same process reported it, and it SHALL NOT carry a report over from an
earlier load.

#### Scenario: The same name in two consecutive loads

- **WHEN** two consecutive loads both reference the same unknown name
- **THEN** each load emits its own report
- **AND** each states one distinct unresolved name

#### Scenario: A clean load after an unclean one

- **WHEN** a load that reported unresolved names is followed by a load whose names all resolve
- **THEN** the second load emits no report
