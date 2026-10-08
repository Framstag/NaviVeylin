# Spec Delta

## MODIFIED Requirements

### Requirement: Local and CI gate recipes agree

Every site that prescribes the gate — the documented procedure, the CI unit-test step, and the change
artifact guidance a change's `tasks.md` is generated from — SHALL run the same task set and the same
forcing rule. The reduced, affected-flavor form SHALL be a local iteration form and SHALL NOT be
reported as a full gate. A difference between what two such sites exercise SHALL be treated as a defect
in the recipe rather than explained away for the individual change.

#### Scenario: CI runs the same task set as the documented gate

- **WHEN** the CI unit-test step runs
- **THEN** it invokes the same task set as the documented full gate

#### Scenario: CI reports which flavors it exercised

- **WHEN** the CI unit-test step completes
- **THEN** its log names the flavors whose suites executed

#### Scenario: A recipe divergence is a defect

- **WHEN** the local full gate and CI exercise different task sets for the same content
- **THEN** the divergence is fixed in the recipe rather than explained away for the individual change

#### Scenario: Generated change tasks prescribe the documented forcing rule

- **WHEN** a change's `tasks.md` is generated from the task guidance and one of its tasks is a
  revert-check's green half
- **THEN** the invocation that guidance prescribes forces the test tasks exactly as the documented gate
  does, and it does not force compilation, packaging or native work that is unchanged

#### Scenario: A third prescription site is covered by this requirement

- **WHEN** the gate is prescribed somewhere other than the documented procedure and the CI step — the
  change-artifact guidance, a skill, or a helper script
- **THEN** its task set and forcing rule are compared with the documented gate's, and a divergence is
  repaired in that site

## ADDED Requirements

### Requirement: A gate rule or measurement has one documented home

Each gate rule and each gate measurement SHALL be stated once, in the document that owns the gate
procedure. Any other document that mentions such a rule or quotes such a measurement SHALL reference the
owning section instead of restating it, so that comparing two gate results never depends on which
document was read.

#### Scenario: A suite measurement is quoted in exactly one document

- **WHEN** a suite's class count, test count, wall time, or a gate's total duration is written into the
  repository's documents
- **THEN** it appears only in the owning document, and every other document that needs it names the
  owning section

#### Scenario: A restated rule is removed rather than maintained

- **WHEN** the same rule text or the same measurement is found in more than one document
- **THEN** the surviving copy is the one in the owning document and the other copies are replaced by a
  section reference, including inside files that are not tracked by the repository

#### Scenario: A document that needs a rule can reach the owner

- **WHEN** an agent or a contributor reads the entry document, the change-artifact guidance, or a
  build/test skill for a gate rule
- **THEN** that document names the owning section, so the rule can be read in full without searching for
  which copy is current
