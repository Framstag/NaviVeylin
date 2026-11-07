# change-readiness Specification

## Purpose

What it means for a change to be ready to be worked by an unattended process: the decisions it declares open, the
case each of its scenarios owes, the surface it records before its plan is fixed, and the rule that its prose is
never judged — so a worker that may not ask can only be handed a change that leaves it nothing to decide.

## ADDED Requirements

### Requirement: A change declares the decisions it leaves open

A change SHALL carry a `## Open decisions` section in its `design.md` whose first non-empty line is either `none` or
the first decision it leaves to a person. The section SHALL be required even when it names nothing, because an
omitted declaration and a declaration of `none` are different claims and only one of them can be checked.

#### Scenario: A change that leaves nothing open declares none

- **WHEN** a change's `design.md` carries `## Open decisions` and the first non-empty line under it is `none`
- **THEN** the checker reports no open decision for that change and the change is not refused for this requirement
- **Case**: `readiness#declaredNone`

#### Scenario: A missing declaration is a refusal, not a pass

- **WHEN** a change's `design.md` carries no `## Open decisions` section at all
- **THEN** the checker reports `declaration-missing`, naming `design.md` and the heading it expected, and exits 1,
  because a reader cannot tell an omitted declaration from a change that was never asked
- **Case**: `readiness#missingDeclaration`

#### Scenario: A declared decision is refused with its own words

- **WHEN** the section's first non-empty line names a decision rather than `none`
- **THEN** the checker reports `open-decision` with that line quoted, and the change is not ready
- **Case**: `readiness#openDecision`

#### Scenario: A deferral is not a fork

- **WHEN** a change's `design.md` carries a `## Open Questions` entry that defers work and names no choice a worker
  would have to make — the shape 8 of the 15 open changes carried on 2026-10-30
- **THEN** the checker says nothing about it: it reads the declaration and never the section, so a legitimate
  deferral is not a refusal
- **Case**: `readiness#deferralIsNotAFork`

### Requirement: Every scenario names the case that exercises it

The checker SHALL require each `#### Scenario:` in a change's spec delta to carry a `**Case**:` line naming at least
one case, and SHALL accept `none` only when it is followed by the reason the case cannot exist, because a scenario
with no case is a plan for a test that does not exist.

#### Scenario: A scenario with no case is refused

- **WHEN** a scenario in the change's spec delta carries no `**Case**:` line
- **THEN** the checker reports `scenario-without-case` naming the scenario, and exits 1
- **Case**: `readiness#scenarioWithoutCase`

#### Scenario: A scenario marked none states why

- **WHEN** a scenario carries `**Case**: none — <reason>` in the shipped style of
  `openspec/specs/agent-issue-loop/spec.md` ("the separation is structural")
- **THEN** the checker accepts it, because the reason is what makes an absent case a claim rather than an omission
- **Case**: `readiness#caseNoneWithReason`

#### Scenario: A named case passes

- **WHEN** a scenario carries a `**Case**:` line with one or more case names
- **THEN** the checker reports nothing for that scenario
- **Case**: `readiness#scenarioWithCase`

### Requirement: The proposal records the surface it will touch, with the command

A change's `proposal.md` SHALL carry a line beginning `surface:` that states the counts it expects — cases added and
rewritten, code sites, guideline paragraphs, fixtures — and the command that produced the count, because an
estimate made before the surface is counted is the defect the count exists to prevent.

#### Scenario: A quoted count without its command is refused

- **WHEN** a proposal carries a `surface:` line that states no command
- **THEN** the checker reports `surface-command-missing`, naming the line, because a count a reader cannot re-take is
  a number with no measurement behind it
- **Case**: `readiness#surfaceCommandMissing`

#### Scenario: A proposal with no surface line is refused

- **WHEN** a proposal carries no line beginning `surface:`
- **THEN** the checker reports `surface-missing` and exits 1
- **Case**: `readiness#surfaceMissing`

#### Scenario: A counted surface passes

- **WHEN** a proposal carries a `surface:` line stating its counts and the command in backticks beside them
- **THEN** the checker reports nothing for this requirement
- **Case**: `readiness#surfacePresent`

### Requirement: One verdict line, and no judgement of prose

The checker SHALL print exactly one `LOOP-CHANGE-READY:` line stating `ok` or each refusal by name with the artifact
and line it came from, SHALL exit 0 when it is `ok` and 1 when it is not, and SHALL NOT decide any finding from the
meaning of a sentence, because a body-reading rule the project trusted once excluded two thirds of an authorised
set over prose.

#### Scenario: The verdict is one machine-readable line

- **WHEN** the checker is run over a change directory
- **THEN** its output carries exactly one line beginning `LOOP-CHANGE-READY:`, every refusal appears on that line by
  name, and the exit status is 0 only for `ok`
- **Case**: `readiness#oneVerdictLine`

#### Scenario: Wording that reads like a fork is not a refusal

- **WHEN** a change's prose contains a question, an option list, or the word `or` in a sentence a reader could call
  a choice, while its `## Open decisions` declaration says `none`
- **THEN** the checker reports `ok`: the declaration is what it reads, and a gate whose verdict depends on wording
  is the failure the project measured 48 times in 49
- **Case**: `readiness#wordingIsNotAVerdict`

#### Scenario: A change with no design document is refused, not crashed

- **WHEN** the change directory carries no `design.md`, or the path names no change
- **THEN** the checker reports `change-unreadable` naming the path it read and exits 1
- **Case**: `readiness#changeUnreadable`

### Requirement: Every refusal the checker can state is falsified once

Each refusal the checker can print SHALL have one record in its mutation list whose mutation makes a named case
fail, and the records SHALL be reachable from the suite the gate's pathspec names, so a refusal that stops firing
is reported by the tree's own harness rather than by a reader's memory.

#### Scenario: A mutation removes one refusal and a case goes red

- **WHEN** a mutation record deletes or inverts one refusal's condition and the mutation list is run
- **THEN** the case that names that refusal fails, and the harness restores the tree afterwards
- **Case**: `readiness#everyRefusalHasAMutation`
