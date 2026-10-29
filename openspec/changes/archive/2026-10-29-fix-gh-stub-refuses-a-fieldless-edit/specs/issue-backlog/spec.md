# Spec Delta

## ADDED Requirements

### Requirement: The suite's stub refuses what the real CLI refuses

The suite SHALL run the issue tooling against a stub `gh` that refuses a shape the real CLI refuses, so a
malformed invocation is red in a case instead of waiting for a person to run the client by hand. An
`gh issue edit` that carries no field to edit SHALL be refused by the stub — a non-zero exit and a message
naming the flags that would make the call legal — and SHALL NOT be recorded as a write, while a call carrying
one of those flags SHALL be recorded exactly as before.

#### Scenario: A bare `gh issue edit` is refused by the stub

- **WHEN** the stub `gh` is asked for `gh issue edit <n>` with none of `--add-label`, `--remove-label` or
  `--body-file`, with the write log enabled
- **THEN** the stub exits non-zero, prints a message naming those flags, and records nothing
- **Case**: `surface#theStubRefusesAnEditWithNoField`

#### Scenario: A legitimate edit is still recorded

- **WHEN** the stub `gh` is asked for `gh issue edit <n> --add-label <label>`
- **THEN** the call exits 0 and the invocation is recorded in the write log unchanged
- **Case**: `surface#theStubRefusesAnEditWithNoField`
