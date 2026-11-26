# The report a refusal hands back is its findings, and the bound weighs that file

## MODIFIED Requirements

### Requirement: A child carries its own route, and not the project's whole document

An agent process SHALL be started without the project's context files, because a context file found in the
project root becomes a system-prompt section charged on every turn of a child whose work is one item. Its phase
prompt SHALL carry the routing it needs — where the rules, capabilities and change artifacts live, and how a
document's section list is read — and the process SHALL still start in the project, so the project's own policy
governs it.

#### Scenario: The child is started without the project's context files

- **WHEN** an agent process is started for an item
- **THEN** its argv disables the agent's context-file discovery and carries no word that moves its trust, replaces
  its system prompt or narrows its tools, so the directory it starts in is still what resolves the project's policy
- **Case**: `adapter#theChildIsStartedWithoutContextFiles`

#### Scenario: The phase prompt carries the route the child needs

- **WHEN** the worker's or the reviewer's prompt is rendered
- **THEN** it names the project's document map, the recipe that prints a document's section list, and where the
  rules, the capabilities and the change artifacts live, and every repository path it names resolves in the project
  root the child is started in
- **Case**: `prompt#thePhasePromptsCarryTheirOwnRoute`

#### Scenario: What the prompt only consults travels as one file it names

- **WHEN** the worker's prompt is rendered for an item
- **THEN** the blocks a worker consults rather than obeys — the project's rule routing, what its own shell and tools
  refuse, and how a measurement is taken and independent calls batched — are one file rendered beside the prompt and
  named in it, which says that the file is read once and is not re-sent with the prompt
- **AND** the prompt keeps what has to change behaviour before any question is asked: the document map, the recipe
  that prints a section list, every verb the refusal policy exports, the read call that answers a refused range, and
  the rule that independent calls belong in one turn
- **AND** the file it names carries those blocks whole, so nothing the child must obey is stated in neither file
- **Case**: `run#theWorkerPromptFitsItsBudget`

#### Scenario: The composed prompt's size is a recorded bound

- **WHEN** the worker's prompt is rendered and the configuration records a byte bound for it
- **THEN** the run measures the composed file, and a prompt over the bound is a line in the run's own record and
  console naming the size and the bound it crossed, so a prompt that grew is paid visibly and not at every turn of
  every item in silence
- **AND** the bound the run read is recorded in its state and printed beside the bounds its other figures are read
  against, and a configuration that records no bound measures nothing and reports nothing
- **AND** the file measured is the file the child is handed: a rework round's prompt is weighed after the reviewer's
  refusal is appended to it, and that append carries the report's `BLOCKING:` lines and the report's own path
  rather than the whole report, so the largest prompt the run composes is the one the bound weighs and the report's
  prose is read at that path instead of re-sent with every turn
- **Case**: `run#theWorkerPromptFitsItsBudget` and `run#theReworkPromptIsTheFileTheChildIsHanded`
