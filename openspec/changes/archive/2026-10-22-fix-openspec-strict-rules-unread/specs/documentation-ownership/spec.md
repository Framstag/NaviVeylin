## ADDED Requirements

### Requirement: The validator's own strict rules are read before the first write

A change artifact SHALL satisfy the two rules `openspec validate --strict` enforces — a `MODIFIED` block
carries every scenario the shipped spec still has, its unchanged ones repeated verbatim, and a requirement's
body stays at most 500 characters, its scenarios excluded — and the always-read entry document SHALL name
this requirement, their owning statement, beside the gated artifact rules and the strict invocation an author
runs before an artifact is finished.

#### Scenario: An author reads both rules before writing an artifact

- **WHEN** a reader takes the validator's rules from the entry document's `OpenSpec Workflow` section
- **THEN** that section names `openspec/specs/documentation-ownership/spec.md` and this requirement as owning
  both rules, and summarises each in one sentence
- **Case**: `probe#theEntryDocumentStatesTheValidatorStrictRules` — `evidence/probe.sh` reads the section's
  strict-rules passage and prints `FAIL` with the absent statement named, exiting 1 while one is missing
  (`evidence/red-on-head.log`, 2 of 2 cases failed on the pre-change tree; `evidence/red-on-correction.log`,
  1 of 2 while the owning name was absent; `evidence/green-after-fix.log`, 0 of 2 once both were added)

#### Scenario: The section names the command that catches them

- **WHEN** the section is read for how an artifact is checked before it is finished
- **THEN** it names `openspec validate <change> --strict` and, for the open set, `openspec validate --changes --strict`
- **Case**: `probe#theEntryDocumentNamesTheStrictValidationCommand` — the same probe's second case, which
  exits 1 while either form is missing (`evidence/revert-check.log`, the `changes` mutation removed that
  clause and left `1 of 2 cases failed`)
