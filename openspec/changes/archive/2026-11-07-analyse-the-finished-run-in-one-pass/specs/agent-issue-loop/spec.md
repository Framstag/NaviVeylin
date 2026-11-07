## ADDED Requirements

### Requirement: A finished run is analysed in one read-only pass that ends in items

A run that has ended SHALL be analysable by one pass that reads its state, its item records and its item streams,
quotes the numbers they already hold rather than recomputing them, and ends with a table of changes worth paying
for. The pass SHALL write nothing — no gate, no build, no edit under `tools/loop/`, `.pi/` or `guidelines/` — and a
defect it finds SHALL become a tracker item, never an inline fix. The entry document SHALL name the skill's path.

#### Scenario: The pass reads the run that ended and never the one in place

- **WHEN** the pass starts and a run's lock owner is alive and its state records no stop
- **THEN** it reads the newest archived state and says in its report which run in place it excluded and when that
  run started
- **AND** with no live run it reads the state in place, including the case where the process was killed: the run
  recorded no stop and its state is still the record
- **Case**: `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`

#### Scenario: The numbers are quoted from what the state and the spend tool printed

- **WHEN** the report carries a run's items, outcomes, wall times, roles, tokens or gate tallies
- **THEN** each figure is the one `tools/loop/report.sh`, `tools/loop/state.sh show` or `tools/loop-spend.py`
  printed for that run, and a figure the state does not hold is printed as not measured rather than as zero
- **AND** any claim about the loop rather than about this run is supported by the per-run table over the archived
  states, naming how many runs it covers
- **Case**: `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`

#### Scenario: The skill the entry document names is the read-only pass, not a name

- **WHEN** a reader follows the entry document's path to the analysis skill
- **THEN** the skill states that the pass is read-only and that a `do` row is filed as an item and never with the
  opt-in label, so the pointer cannot resolve to a text that would authorise a write
- **AND** the case that holds the pointer fails when either sentence is dropped, which is what makes the route a
  contract rather than a mention
- **Case**: `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`
