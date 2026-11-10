# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: Every role's turn bound is the one the run's header prints

The run SHALL print one `child-turns-max=` token naming every role's turn bound, in the order `agent-roles=` names the roles, so the bound a `LOOP-TIMEOUT` line applies is one the header already carries. The rework round's value SHALL be 150, a measured raise from 100 (issue #842); no other role's value and no context bound moves with it.

#### Scenario: The header names all six roles' bounds

- **WHEN** a run starts on a project that records a turn bound for every role — the rework round among them, whose 150 is
  a measured raise rather than that role's p95
- **THEN** its header's `child-turns-max=` token names `worker`, `worker-gate`, `worker-tail`, `worker-rework`,
  `reviewer` and `reviewer-pre-gate` in the order `agent-roles=` lists them, each at the figure its key records, so no
  role the run can end at a bound is missing from the line that records the bounds it applies
- **Case**: `run#eachChildIsBoundedByItsOwnRolesTurns`

#### Scenario: The bound a rework transcript names is the bound the header printed

- **WHEN** the round a refusing review bought reaches its role's recorded turn bound and the run ends it there
- **THEN** its transcript carries `LOOP-TIMEOUT: the child reached the recorded turn bound of <n> turns
  (LOOP_CHILD_TURNS_MAX_WORKER_REWORK for the worker-rework role)` and the run's header printed that same figure for
  that role, the two readings being one bound
- **Case**: `state#theReworkRoundIsARoleOfItsOwn`

#### Scenario: The raise leaves the context stop where it was

- **WHEN** the rework round's turn bound is raised, because the item a run cut there died 0.4 % under the token stop
- **THEN** `LOOP_ITEM_CONTEXT_MAX` stays 200 000 — the refusal #833 recorded and #836's re-proposal, closed
  `NOT_PLANNED`, stand — and only turns inside the provider's window are what the raise spends
- **Case**: `run#childPastItsContextBoundIsCut`
