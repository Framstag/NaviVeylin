# issue-backlog — deltas

## MODIFIED Requirements

### Requirement: Only an authorised actor's item may be acted on

The tooling SHALL act on an issue only when its author is on a committed allowlist and the issue carries
the project's explicit opt-in label, and SHALL refuse to run at all when the identity it is running as is
not on that allowlist. The opt-in authorises unattended **work**, so a closure that records a person's
decision not to act — the not-planned closure, which writes the settled status a retired item already
carries — SHALL NOT require it: that closure SHALL proceed when the gate's only refusal is
`missing-eligibility-label`, and every other refusal (the author allowlist, a reserved label, a missing
reason or missing evidence) SHALL still stop it. A completed closure of an item that no declared check owns
SHALL still require the opt-in, because that one asserts work which landed; a declared check's closure of its
own issue is authorised by that check's row instead, so the `--sensor` path needs no opt-in either.

#### Scenario: An unauthorised author is not acted on

- **WHEN** an issue whose author is not on the allowlist is considered
- **THEN** it is excluded and the exclusion names the author as the reason

#### Scenario: The opt-in is required and is not the author's decision alone

- **WHEN** an issue by an allowlisted author does not carry the opt-in label
- **THEN** it is not eligible, so filing an item and authorising work on it remain two acts

#### Scenario: The tooling cannot grant the opt-in to itself

- **WHEN** any operation of the tooling is given the opt-in label as an input
- **THEN** it is refused with a reason, so no script, check or automated pass can authorise its own work

#### Scenario: A foreign identity stops the tooling rather than narrowing it

- **WHEN** the identity the tooling runs as is not on the allowlist
- **THEN** it refuses before acting and says so, rather than reporting an empty working set

#### Scenario: A not-planned closure is a record write, not an act of work

- **WHEN** an issue by an allowlisted author carries a settled state (`status/retired`) and no opt-in label,
  and a not-planned closure of it is requested with a reason and its evidence
- **THEN** the closure proceeds — the reason and its evidence are posted and the CLI receives the `not planned`
  state reason — while the completed closure of that same item is still refused with
  `missing-eligibility-label` and posts nothing
- **Case**: `close#notPlannedNeedsNoOptIn`
