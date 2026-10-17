# agent-issue-loop Specification

## Purpose
A defect-shaped item in the tracker is closed by an unattended, budgeted run: one fresh agent process works
one item from a prepared package, a separate reviewer checks it, the run's own script owns the budget and
the version control, and nothing closes until evidence exists.

## Requirements

### Requirement: One command starts a budgeted run, with no dialogue

A run SHALL be started by one command whose budget is supplied as arguments and recorded, and SHALL
require no interactive answer, so that the same run can be repeated and audited.

#### Scenario: The budget is arguments, not a prompt

- **WHEN** a run is started
- **THEN** the item cap, the time budget, whether a review is taken and whether closure is allowed are
  arguments whose effective values are recorded and printed in the run's header

#### Scenario: The run stops on its own authority

- **WHEN** the item cap is reached, the time budget is spent, the queue is empty, two items in a row were
  blocked, or the remaining time cannot pay for another item
- **THEN** the run stops without needing a further decision

#### Scenario: A run is recorded

- **WHEN** a run ends
- **THEN** a report exists naming each item, its verdict, its wall time, its review rounds and what it
  closed, so the run justifies itself with numbers

### Requirement: The run acts only on authorised, eligible items

The run SHALL refuse to start when the identity it runs as is not authorised, SHALL select only items that
are eligible for automated work, and SHALL never write the authorisation label itself.

#### Scenario: An unauthorised identity stops the run before any work

- **WHEN** the identity the run acts as is not on the allowlist
- **THEN** it stops before creating anything, and no branch, label or state is written

#### Scenario: An item without the opt-in is not worked

- **WHEN** the eligible set is selected
- **THEN** an item whose owner has not applied the opt-in is never worked, however defect-shaped it is

#### Scenario: The run cannot authorise itself

- **WHEN** the run writes a label
- **THEN** the authorisation label is not among the labels it can write, and no code path of the run
  writes it

#### Scenario: A gated item is recorded, not silently dropped

- **WHEN** a candidate is rejected
- **THEN** its verdict and the one clause of evidence for it are recorded, so the rejection is part of the
  run's output

### Requirement: Version control belongs to the run's script, not to the agent

The run SHALL create and manage the working tree and the branch of each item itself, and no phase of an
item SHALL be able to invoke version control on its own behalf.

#### Scenario: One working tree per run

- **WHEN** a run starts
- **THEN** it prepares one working tree for the whole run and seeds it with what the project's build and
  test need, so the cold cost is paid once

#### Scenario: One branch per item, from the run's base

- **WHEN** an item starts
- **THEN** it is worked on its own branch from the base commit the run recorded

#### Scenario: A stale base stops the next item

- **WHEN** the main branch has advanced past the run's base commit
- **THEN** the next item is refused and both commits are named, rather than building on a tree nobody
  reviewed

#### Scenario: Nothing is pushed or opened by default

- **WHEN** a run does not ask to publish
- **THEN** no push and no change request happens, whatever the items did

### Requirement: Each item is worked by a fresh process, reviewed by a different one

An item SHALL be worked by an agent process that starts fresh, given a prepared package describing the
item as data, and SHALL then be reviewed by a second, fresh process that is not the one that wrote the
change.

#### Scenario: The item arrives as a prepared package

- **WHEN** an agent process is started for an item
- **THEN** it receives a prepared package — the item's claim and evidence, the specification passage and
  the acceptance condition — and the item's own text is passed only inside a delimited data field

#### Scenario: The reviewer is not the writer

- **WHEN** a review is run
- **THEN** it is a separate process from the one that produced the work

#### Scenario: A reviewer that writes is discarded

- **WHEN** the working tree changed while the reviewer ran
- **THEN** that review round is discarded with a reason, because a reviewer that wrote cannot have been
  the reviewer

#### Scenario: Review rounds are bounded

- **WHEN** a change fails its second review round
- **THEN** the item is recorded as needing a decision and no further round is attempted

### Requirement: The run verifies rather than trusts

The run SHALL determine whether an item closed from artifacts it reads itself — the working tree, the
change's archive and the gate's evidence — and SHALL NOT accept an agent's claim of success.

#### Scenario: A claim without a commit is blocked

- **WHEN** an agent process reports success but the working tree shows nothing committed
- **THEN** the item is recorded as blocked and the item is not closed

#### Scenario: The gate's evidence is read by the run

- **WHEN** an item claims a gate result
- **THEN** the run quotes the evidence it found and does not repeat the claim

#### Scenario: Evidence outlives the run

- **WHEN** an item cites a test result
- **THEN** the result is carried into the change's own directory, because the build directory is
  overwritten by the next run

### Requirement: Closure follows a passing review and carries evidence

An item SHALL be closed only after its review passed and its change was archived, with one line of root
cause and the evidence attached as a comment first; the run SHALL be able to be started so that closure is
left to a person instead.

#### Scenario: A passing item is closed with evidence

- **WHEN** the review passed and the change was archived
- **THEN** the item is closed after a comment carrying the root cause and the evidence

#### Scenario: Closure can be left to a person

- **WHEN** the run was started with closure disabled
- **THEN** the item keeps its fixed state, the evidence comment is still posted and the item stays open

#### Scenario: A blocked item is never closed

- **WHEN** an item ended blocked
- **THEN** it is not closed, and the reason is recorded on it

### Requirement: The run survives interruption

The run's state SHALL have exactly one writer, SHALL live where it cannot be committed, and SHALL be
sufficient to continue after an interrupt without repeating work already done.

#### Scenario: State is written only between agent processes

- **WHEN** an agent process is running
- **THEN** no state is written, so a bookkeeping edit cannot be attributed to that process's work

#### Scenario: An interrupted item leaves no item looking in progress

- **WHEN** the run is interrupted while an item is in flight
- **THEN** the in-progress marking is removed, the item is recorded as blocked, and the next run can take
  it up again

#### Scenario: State is not committed

- **WHEN** the run ends
- **THEN** its state file is not part of the repository's history

### Requirement: A pile of open decisions stops the run

The run SHALL refuse to start when more items awaiting a person's decision are open than the configured
cap allows, and SHALL report them instead of producing more work.

#### Scenario: The cap is reached

- **WHEN** the number of open items awaiting a decision reaches the cap
- **THEN** the run refuses to start and prints those items, so nothing new is produced while decisions
  accumulate

#### Scenario: An item needing a decision is escalated, not abandoned

- **WHEN** an item turns out to need a decision while it is being worked
- **THEN** the work already done is preserved and named on the item — the root cause, the failing case and
  the branch — and the item is marked as awaiting a decision, so the work is not thrown away

### Requirement: The run's mechanics are the project's configuration, not its code

The run SHALL take the command it starts an agent process with, the skills it tells that process to use and
the command it runs the project's gate with from configuration, and SHALL name none of them in its phases.

#### Scenario: A project with no gate still runs

- **WHEN** the configured gate command is empty
- **THEN** the phases complete and the report says the gate was skipped, so the phases call the project's
  gate rather than owning one

#### Scenario: The agent command is one indirection

- **WHEN** an agent process is started
- **THEN** it is started through the configured command and through no other, so how an agent is started
  can change without touching a phase

#### Scenario: No phase names a project fact

- **WHEN** the run's phases and prompts are inspected
- **THEN** they name no skill, command, path or project value of their own

### Requirement: Running an agent elsewhere is a configuration, not a rewrite

The run SHALL be able to start its agent processes somewhere other than the host — a sandboxed or isolated
environment — by changing the configured command alone, and SHALL NOT depend on such an environment being
present.

#### Scenario: The run works with no isolation environment installed

- **WHEN** the run is started on a machine with no sandbox tooling
- **THEN** it works, because the sandbox is one possible value of the configured command

#### Scenario: The environment holds no tracker credential

- **WHEN** an agent process runs, wherever it runs
- **THEN** it cannot write to the tracker: the run's script owns every write, and the process is given a
  prepared package rather than credentials

#### Scenario: Work needing a device stays on the host

- **WHEN** an item's remaining evidence needs a device attached to the host
- **THEN** that is recorded as a device verdict and not attempted inside the agent's environment
