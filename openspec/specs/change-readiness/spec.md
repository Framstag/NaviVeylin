# change-readiness Specification

## Purpose
What it means for a change to be ready to be worked by an unattended process: the decisions it declares open, the
case each of its scenarios owes, the surface it records before its plan is fixed, and the rule that its prose is
never judged — so a worker that may not ask can only be handed a change that leaves it nothing to decide.

## Requirements

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

### Requirement: A scenario's case name resolves to exactly one harness

Every `**Case**:` line in a shipped spec SHALL name a case that exactly one declared harness runs. A name no harness lists SHALL be a refusal naming the spec file, the scenario and the name; a name two harnesses list SHALL be a refusal naming both.

#### Scenario: A name the loop suite runs resolves

- **WHEN** the check reads a shipped scenario whose case is one the loop suite declares
- **THEN** the name resolves to the loop harness and the verdict carries no refusal
- **Case**: `traceability#aNameTheLoopSuiteRunsResolves`

#### Scenario: A name another harness runs resolves

- **WHEN** a shipped scenario names a case the sensors suite, the issue tooling's Java cases or a Gradle suite runs
- **THEN** the name resolves to that harness, so a comparison against one suite alone reports no false dead reference
- **Case**: `traceability#aNameAnotherHarnessRunsResolves`

#### Scenario: A name no harness runs is refused

- **WHEN** a scenario names a case that no declared harness lists
- **THEN** the check refuses, naming the spec file, the scenario's line and the name
- **Case**: `traceability#aNameNoHarnessRunsIsRefused`

### Requirement: A shared group is declared, and a duplicate full name is refused

Each harness's claimed case groups SHALL be declared in one registry, so a group two harnesses share is a declared fact rather than an accident. An identical full name in two harnesses SHALL be refused, naming both, and a registry row whose case list is absent SHALL be refused rather than read as an empty harness.

#### Scenario: A shared group is declared

- **WHEN** two harnesses claim the same group
- **THEN** the registry declares both claims, the check prints the group once, and no refusal is raised for the group itself
- **Case**: `traceability#aSharedGroupIsDeclared`

#### Scenario: A duplicate full name is refused

- **WHEN** two harnesses list the same full case name
- **THEN** the check refuses, naming the name and both harnesses
- **Case**: `traceability#aDuplicateFullNameIsRefused`

#### Scenario: A registry row whose list is absent is refused

- **WHEN** the registry names a harness whose case-list file or command does not exist
- **THEN** the check refuses and names the row, rather than resolving against three harnesses and reporting success
- **Case**: `traceability#aRowWhoseListIsAbsentIsRefused`

#### Scenario: A row that emits a group it never claimed is refused

- **WHEN** a harness prints a case name whose group the registry does not declare for that row
- **THEN** the check refuses and names the row and the name, so a case cannot enter the resolution set unclaimed
- **Case**: `traceability#aRowThatEmitsAnUnclaimedGroupIsRefused`

#### Scenario: An exclusion that hides a name is refused

- **WHEN** a row excludes a name and no other row declares it
- **THEN** the check refuses, because an exclusion is a delegation and not a hiding place
- **Case**: `traceability#anExclusionThatHidesANameIsRefused`

### Requirement: A case no scenario claims is reported as a number, and refuses nothing

The check SHALL count and print the cases the declared harnesses run that no shipped scenario names. It SHALL NOT refuse one, because a case may prove the harness rather than a requirement, and the count SHALL be part of the one verdict line so a reader always sees it.

#### Scenario: The unclaimed cases are counted

- **WHEN** the check runs on a tree where a harness runs a case no scenario names
- **THEN** the verdict line carries the count and `--json` carries the list, and the exit status stays 0
- **Case**: `traceability#theUnclaimedCasesAreCounted`

#### Scenario: An unclaimed case never refuses

- **WHEN** every case of one harness is unclaimed
- **THEN** the check still exits 0 and prints the count, so a harness-level case is not a defect
- **Case**: `traceability#anUnclaimedCaseNeverRefuses`

### Requirement: The check reads names, never meaning

The check SHALL compare identifiers against declared lists and SHALL NOT decide any verdict from the meaning of a sentence. A case-shaped string inside prose, and a scenario the readiness checker already owns, SHALL NOT become this check's subject.

#### Scenario: A name in prose is not a claim

- **WHEN** a spec's body mentions a case-shaped name outside a `**Case**:` line
- **THEN** the check ignores it, and a name that exists nowhere in prose does not cause a refusal
- **Case**: `traceability#aNameInProseIsNotAClaim`

#### Scenario: A scenario with no case line is not this check's subject

- **WHEN** a scenario carries no `**Case**:` line
- **THEN** this check says nothing about it, and the readiness checker's own refusal is where that is reported
- **Case**: `traceability#aScenarioWithoutACaseLineIsNotThisChecksSubject`
