# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A change names its surface before its plan is fixed

A change that rewrites a rule rather than an artifact SHALL measure the rule's surface before its plan is
fixed — the cases, fixtures and prompt or engine files that assert the behaviour being changed — and SHALL
carry that count in its proposal under a `surface:` line, with the command that found it. The count, not the
first file the plan names, is what the estimate is set against; a plan made before it exists SHALL NOT be read
as the change's cost.

#### Scenario: The worker counts the surface before it plans

- **WHEN** a run renders the worker's task for an item whose change rewrites a rule
- **THEN** the task asks for the surface count before the plan is fixed, names the search that produces it, and
  names the `surface:` record the change's proposal carries
- **Case**: `prompt#workerNamesTheChangeSurfaceBeforeThePlan`

#### Scenario: The protocol states the pass a session outside the loop owes

- **WHEN** a session that changes this project reads the iteration protocol
- **THEN** the protocol states the surface measurement beside the symptom measurement it states first, and
  states the record it leaves, so a session without the loop's task meets the same rule
- **Case**: `prompt#iterationProtocolNamesTheChangeSurface`
