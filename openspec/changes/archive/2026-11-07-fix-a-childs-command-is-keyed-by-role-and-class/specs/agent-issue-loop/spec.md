# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A child is started on the command its role and its class record

Each child SHALL be started on the command recorded for the role its prompt names. A role with no key of its own
SHALL inherit the key of the role its name qualifies — the word before its first hyphen — and only a role whose head
records nothing SHALL fall back to `LOOP_AGENT_CMD`; so no role the engine starts is left on the project's single
command when the role it belongs to records one. The rework round a refusal buys SHALL be a role of its own, with its
own command and turn-bound keys, rather than the worker's. An item's class SHALL be able to select a command for the
role it is spelled after, and the most specific key SHALL win: a role's own key beats its head's class key. The run's
header SHALL name every role whose command, resolved without the item's class, is not the project's one; a
class-qualified command is named by the child's own transcript.

#### Scenario: A role inherits the command of the role it belongs to

- **WHEN** a project records `LOOP_AGENT_CMD_REVIEWER` and no command of its own for the round before the gate, and a
  run starts that round
- **THEN** the round before the gate is started on the reviewer's recorded command, the round after the gate on the
  same one, and the run's header names both roles — `agent-roles=reviewer,reviewer-pre-gate`
- **Case**: `state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo`

#### Scenario: The rework round is a role of its own

- **WHEN** the round after the gate refuses a change and the run starts the rework round
- **THEN** that child is started on `LOOP_AGENT_CMD_WORKER_REWORK` and ended at `LOOP_CHILD_TURNS_MAX_WORKER_REWORK`,
  which the transcript names with its role — neither key is the worker's
- **Case**: `state#theReworkRoundIsARoleOfItsOwn`

#### Scenario: An item's class selects the worker's command

- **WHEN** one run works two items of different classes and the project records a command for each class beside the
  worker's key
- **THEN** each item's worker child is started on the command its own class records, and a role's own key still wins
  over a head's class key
- **Case**: `state#anItemsClassSelectsTheWorkerCommand`

#### Scenario: A project that records nothing runs every child on its one command

- **WHEN** every per-role key is empty and `LOOP_AGENT_CMD` records the command
- **THEN** every child of the run — the change child, the tail and the reviewer — is started on it, and the header
  names no role
- **Case**: `state#agentCommandIsTheOnlyIndirection`
