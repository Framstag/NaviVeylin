## ADDED Requirements

### Requirement: The entry document states the validator's strict rules

The always-read entry document SHALL state, beside the artifact rules the pipeline gates, the two rules
`openspec validate --strict` fails on: a `MODIFIED` block carries every scenario the shipped spec still has,
the unchanged ones repeated verbatim, and a requirement's body is at most 500 characters, its scenarios
excluded. It SHALL name that strict invocation as the check an author runs before an artifact is finished.

#### Scenario: An author reads both rules before writing an artifact

- **WHEN** a reader takes the artifact rules from the entry document's `OpenSpec Workflow` section
- **THEN** that section states that a `MODIFIED` block repeats every shipped scenario verbatim, and that a
  requirement's body is at most 500 characters, its scenarios excluded
- **Case**: `probe#theEntryDocumentStatesTheValidatorStrictRules` — `evidence/probe.sh` reads the section and
  prints `FAIL` with the absent statement named, exiting 1 while one is missing (`evidence/red-on-head.log`,
  2 of 2 cases failed on HEAD; `evidence/green-after-fix.log`, 0 of 2 after the statement was added)

#### Scenario: The section names the command that catches them

- **WHEN** the section is read for how an artifact is checked before it is finished
- **THEN** it names `openspec validate <change> --strict`, and the `--changes --strict` form for the open set
- **Case**: `probe#theEntryDocumentNamesTheStrictValidationCommand` — the same probe's second case, which
  exits 1 on HEAD and 0 once the command line is in the section
