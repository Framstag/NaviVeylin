# documentation-ownership Specification

## Purpose

The project's always-read entry document routes a reader from the kind of change they are making to the
guideline sections that own its conventions, so the conventions can be found and followed without reading
whole documents, and a route that points at a section can be checked instead of assumed.

## Requirements

### Requirement: A change is routed to the sections that own its conventions

The always-read entry document SHALL map each kind of change to the guideline sections that own its
conventions, identifying a section by its number and its heading text together. The map SHALL cover the
topics a change routinely touches, and SHALL NOT be required to enumerate every section of every
guideline.

#### Scenario: A phone UI change is routed without reading a document

- **WHEN** a change touches the phone UI
- **THEN** the entry document names the guideline sections that own phone UI conventions, and a reader
  can reach them without reading a whole guideline document

#### Scenario: A topic is owned in more than one document

- **WHEN** a kind of change is governed by conventions that live in more than one guideline
- **THEN** the route names the owning section in each document, so a reader cannot follow one and miss
  the other

#### Scenario: A heading number is ambiguous or absent

- **WHEN** a route points at a section whose number is used twice in its document, or at a section that
  carries no number at all
- **THEN** the route identifies that section by its heading text as well, so the reader lands on the
  intended section

#### Scenario: Sections outside the map stay reachable

- **WHEN** a guideline section is not named by any route
- **THEN** the map states how the complete section list of a document is obtained, and that section is
  reachable by that means

### Requirement: A route resolves to a section that exists

Every section reference a route makes SHALL resolve to a section that exists in the document it names.
A route that points at a section no longer present SHALL be reported as a defect and repaired, and SHALL
NOT be followed.

#### Scenario: The references are checked without a build or a device

- **WHEN** the route check runs
- **THEN** it resolves every section reference in the routing table against the document each reference
  names, and it requires no build, no test run, no device and no network access

#### Scenario: A dangling reference fails the check

- **WHEN** a route names a section that its document does not contain
- **THEN** the check exits non-zero and names the document and the offending reference

#### Scenario: Removing a route is detected

- **WHEN** one route row is deleted, or its section number is changed to one that does not exist in the
  document
- **THEN** the check reports a dangling or missing reference, and restoring the row makes it pass again

#### Scenario: The check is self-tested

- **WHEN** the check's self-test runs
- **THEN** it accepts a fixture route that resolves and rejects a fixture route that does not, without
  reading the project's own documents

### Requirement: The change-artifact guidance reads by section

The change-artifact guidance SHALL direct its reader to the sections that own the change's conventions
rather than to whole guideline documents. It SHALL name where the routing table lives, and SHALL NOT
instruct a reader to read every document under `guidelines/`.

#### Scenario: The served context instruction is section-scoped

- **WHEN** the project context is served while a change artifact is being written
- **THEN** it directs the reader to the sections that own the change's conventions and names where the
  routing table is documented, instead of listing whole documents as required reading

#### Scenario: The apply instruction is section-scoped

- **WHEN** the apply guidance is served
- **THEN** its verification instruction is scoped to the sections that own the conventions the change
  actually touches, and does not require verification against every guideline document

#### Scenario: The guidance survives the edit

- **WHEN** the context and the operation guidance are read back through the OpenSpec CLI
- **THEN** the context is present and both the apply and archive guidance arrays are non-empty, so no
  entry was silently dropped by a quoting change

### Requirement: A modified requirement carries the scenarios its spec still has

A change's MODIFIED block SHALL carry the requirement it modifies whole — the description and every scenario
the shipped spec still has — because that block replaces the requirement when the change archives. A block
that omits a shipped scenario SHALL be reported as an error naming the omitted scenario, and the change SHALL
stay unarchivable until the block is completed.

#### Scenario: The change set is validated by command

- **WHEN** `openspec validate --changes` runs over the open change set and a change's MODIFIED block omits a
  scenario its shipped spec still has
- **THEN** the command exits non-zero and its error names the change, the requirement and the omitted scenario
- **AND** the same command exits 0 once the omitted scenarios are restored into the block

#### Scenario: A completed block keeps the requirement whole

- **WHEN** `openspec validate <change> --type change` runs after a block has been completed from its shipped
  requirement
- **THEN** the command reports the change valid, and comparing the block against the shipped requirement names
  no shipped text that the block dropped

### Requirement: The routing cost is measured

The cost of a routed read SHALL be measured against the cost of reading the documents the route replaces,
and the measurement SHALL be recorded where the route is documented.

#### Scenario: Numbers for representative changes

- **WHEN** a routed read is documented for this project
- **THEN** the record quotes, for at least one phone-UI change, one car change and one rendering change,
  the cost of the sections the route names and the cost of the document set the route replaces

#### Scenario: An unmeasured saving is not claimed

- **WHEN** a saving from routing is stated without those measurements
- **THEN** the statement is treated as unmeasured rather than as evidence

### Requirement: The always-read document states facts and references rules

The always-read entry document SHALL state facts — what exists, where it lives, how to run it — and SHALL
reference the document that owns a normative rule instead of stating the rule's normative text. Where it
summarises a rule, the summary SHALL be one sentence and SHALL name the owning section, and it SHALL carry
no measurement.

#### Scenario: A rule mention names its owning section

- **WHEN** the entry document mentions a rule that governs code, a process or another document
- **THEN** the mention names the owning document and section, and the normative text — its rationale, its
  refusal path and its measurement — is found in that section

#### Scenario: A rule summary carries no measurement

- **WHEN** the entry document summarises a rule in one sentence
- **THEN** that sentence contains no measured number, and the measurement is read from the owning section

#### Scenario: A fact is stated where it is used

- **WHEN** the entry document names a path, a module, a command, a log tag or a distribution fact
- **THEN** that statement remains in the entry document, because it constrains nothing and is needed before
  any other document is read

### Requirement: A rule has one normative home

A normative rule SHALL be stated in exactly one document. A document that needs the rule SHALL reference
the owning section instead of restating it. When the same rule is found in more than one document, the
owning statement SHALL survive and every other occurrence SHALL become a reference.

#### Scenario: Editing a rule touches one document

- **WHEN** a rule is changed
- **THEN** the normative text is edited in its owning document only, and every other document that mentions
  it continues to read correctly

#### Scenario: A duplicate is removed rather than synchronised

- **WHEN** two documents state the same rule
- **THEN** the owning statement is kept, the other is replaced by a reference to it, and the two are not
  maintained in parallel

#### Scenario: A rule that spans documents names one home

- **WHEN** a kind of change is governed by rules that more than one document needs to mention
- **THEN** exactly one of them states the rule normatively and the others reference it, while the routing
  table still names every section a reader must consult

### Requirement: A relocated rule keeps its statement

A rule removed from the entry document SHALL already be stated in the document that receives it. A block
whose statement exists in no other document SHALL NOT be deleted: it is kept in place and recorded as a
rule without an owner, until a document owns it.

#### Scenario: The removal is verified by finding the statement

- **WHEN** a block is removed from the entry document
- **THEN** the statement it carried is found, by search, in the owning document or in the section that
  received it, and that finding is recorded with the block

#### Scenario: The relocation count closes

- **WHEN** the entry document has been slimmed
- **THEN** the number of removed blocks equals the number of statements found in owning documents, and the
  two counts are reported together

#### Scenario: A homeless rule is filed, not dropped

- **WHEN** a block's statement is found in no other document
- **THEN** the block is not removed, and the rule is recorded for a document to own

### Requirement: A concern has one owning document

A normative rule SHALL be added to the document that owns its concern. A recurring concern that has no
owning document SHALL be given one, and the routing table SHALL name that document, so a reader reaches it
from the kind of change they are making rather than from a search.

#### Scenario: A rule is added to the document that owns its concern

- **WHEN** a new rule is written for a concern that a guideline document already owns
- **THEN** it is stated there, and every other document that mentions it references that section

#### Scenario: A recurring concern without a document gets one

- **WHEN** rules for one concern keep being added while no document owns that concern
- **THEN** a document is created for it, the concern's existing rules move into it, and the entry document
  keeps one sentence per rule plus the route

#### Scenario: A new document is reachable

- **WHEN** a guideline document is added to the repository
- **THEN** the routing table names it, the change-artifact guidance lists it, and the route check resolves
  references into it

### Requirement: The entry document is bounded by what a session needs before it reads anything else

The always-read entry document SHALL hold what a reader needs before any other document is read — the facts
that identify the system and how to run it, the routes to the rest, and one sentence per rule it must not
break. Material that a reader needs only while working inside one area SHALL live in that area's document,
however generally useful it appears.

#### Scenario: A fact needed before any other read stays

- **WHEN** a fact is needed to run the project, to name a component, or to choose which document to read
- **THEN** it is stated in the entry document

#### Scenario: A fact needed only inside one area moves

- **WHEN** a block in the entry document is useful only to a reader working inside one area — a rendering
  rule, a test-suite fact, a logging convention, a packaging step
- **THEN** that area's document states it and the entry document keeps the route

#### Scenario: Relocating keeps the entry document readable

- **WHEN** material leaves the entry document
- **THEN** a reader who follows no route still finds the system identified, the commands to run it, and one
  sentence per invariant that must not be broken

### Requirement: Every guideline document is covered by the route check

The route check SHALL resolve references against the documents the repository contains, derived at run
time rather than from a maintained list. A document added to `guidelines/` SHALL be covered without editing
the check, and a document that no route names SHALL be reported.

#### Scenario: The document set is derived

- **WHEN** the route check runs
- **THEN** it takes the documents to check from the repository's guideline directory, not from a list
  maintained inside the check

#### Scenario: A new guideline is covered without a code change

- **WHEN** a guideline document is added and the routing table references one of its sections
- **THEN** the check resolves that reference, and its document list grew with the directory

#### Scenario: An unrouted document is reported

- **WHEN** a guideline document exists that the routing table does not name
- **THEN** the check reports that document as unrouted

#### Scenario: The derivation is self-tested

- **WHEN** the check's self-test runs
- **THEN** a fixture whose extra guideline holds an unresolvable route fails, and a fixture whose extra
  guideline is named by the table passes

### Requirement: A capability spec states what the capability is for

Every capability spec's `## Purpose` section SHALL be a sentence its author wrote, saying what the
capability is for. It SHALL NOT keep the sentence `openspec archive` writes for a capability it creates,
`TBD - created by archiving <change>. Update Purpose after archive.`, because that section is read from a
delta only when the capability is created: no later change can replace it, and `openspec validate <id>
--type spec` reports it as a warning while still exiting 0. A check over every `spec.md` under
`openspec/specs/`, at any depth, SHALL name every spec that still carries that sentence, and SHALL exit
non-zero while one does; when none does, it SHALL print `Every capability spec states its Purpose.` and
exit 0.

#### Scenario: The archive placeholder is reported by a check, not by a warning

- **WHEN** a capability spec's `## Purpose` is still `TBD - created by archiving …`
- **THEN** the artifact-hygiene check prints that spec's path and exits non-zero
- **AND** the case `artifact-hygiene#theArchivePlaceholderIsReported` — the step's `awk` pass over every
  `spec.md` found under `openspec/specs/`, which reads only the lines between a `## Purpose` heading and the
  next `## ` heading, so the sentence may be quoted freely in requirement prose — exercises this: on a tree
  whose Purpose is the placeholder it prints that spec's path and line, and the step fails

#### Scenario: A written Purpose passes the check

- **WHEN** every capability spec's `## Purpose` is a sentence its author wrote
- **THEN** the check prints that every capability spec states its Purpose and exits 0
- **AND** the case `artifact-hygiene#aWrittenPurposePasses` — the same pass over a repaired tree —
  exercises this: no path is printed, the step prints `Every capability spec states its Purpose.`, and it
  exits 0

#### Scenario: A capability spec nested one level deeper is reported too

- **WHEN** a capability spec below a group directory, `openspec/specs/<group>/<capability>/spec.md`, keeps
  `TBD - created by archiving …` as its `## Purpose`
- **THEN** the artifact-hygiene check prints that spec's path and exits non-zero
- **AND** the case `artifact-hygiene#aNestedCapabilitySpecIsReported` — the same `find openspec/specs -name
  spec.md` pass — exercises this: with the placeholder under
  `openspec/specs/share/location-receiving/spec.md`'s `## Purpose` it prints
  `openspec/specs/share/location-receiving/spec.md:4` and the step fails; restored, the step prints
  `Every capability spec states its Purpose.` and exits 0

### Requirement: A document that names a skill resolves to a skill that exists

A document that names a skill by its path — `.pi/skills/<name>` — SHALL name a skill the tree carries, a skill
being the directory under `.pi/skills/` that carries its `SKILL.md`. The skill set SHALL be derived from the
tree at run time, and the check that resolves the routing table's section references SHALL resolve these
paths too, so a renamed or deleted skill fails the check instead of a run. Inside a skill, a rule SHALL be
stated once and referenced elsewhere, as it is across `guidelines/`.

#### Scenario: A skill path the tree carries resolves

- **WHEN** the check runs over a tree whose documentation names `.pi/skills/<name>` and whose `.pi/skills/<name>`
  directory carries a `SKILL.md`
- **THEN** the path resolves and the check exits zero, without the check's own source naming that skill
- **Case**: `routes#aSkillTheTreeCarriesResolvesWithoutEditingTheCheck`

#### Scenario: A renamed or deleted skill is reported

- **WHEN** a document names `.pi/skills/<name>` and no directory of that name under `.pi/skills/` carries a
  `SKILL.md` — the skill was renamed, deleted, or never existed
- **THEN** the check exits non-zero and names the document and the offending path, so the reference is repaired
  at the check rather than discovered by the run that needs the skill
- **Case**: `routes#aSkillPathThatNoLongerResolvesIsReported`

#### Scenario: A directory without its contract is not a skill

- **WHEN** `.pi/skills/<name>` exists as a directory but carries no `SKILL.md`
- **THEN** the check reports the path, because a skill a document can route a reader to is one that states its
  contract
- **Case**: `routes#aSkillDirectoryWithoutItsSkillFileIsReported`

#### Scenario: A skill path is resolved as a whole name

- **WHEN** a document names `.pi/skills/<name>` and the only directory beginning with that name is a different
  skill (`<name>-extra`)
- **THEN** the check reports the path instead of accepting the longer name as a match
- **Case**: `routes#aSkillNameThatOnlyPrefixesAnExistingOneIsReported`

### Requirement: A recorded guardrail is stated in the section that owns it

A guardrail entry that a backlog record states as a rule — rather than as a pointer to a rule that already
exists — SHALL be stated in the guideline section that owns its concern, adjacent to the rest of that
section's rules and tagged with the record entry that produced it. The record's carriage marker SHALL NOT be
cleared on the strength of a cross-reference to a neighbouring rule: where an entry states a rule no section,
skill or CI step states, the owning section gains the rule and the record keeps the cross-reference.

#### Scenario: A rule stated only in the record is carried into its section

- **WHEN** the record's entries are audited against the tree and an entry states a rule that no guideline
  section, skill or CI step states
- **THEN** the guideline section that owns the concern states the rule, and the text-only probe
  `openspec/changes/archive/2026-10-21-fix-uncarried-guardrail-entries/evidence/red-probe.sh` finds its
  distinguishing token there without a build, a device or a network call, so a reader reaches it from the
  section rather than from the tracker
  (case `record-carriage#eachRecordedRuleIsStatedInItsSection`)

### Requirement: A section reference a guideline body writes is resolved

Every section reference a guideline body makes to another guideline document SHALL resolve against that
guideline, the way a reference in the routing table does: a reference that does not resolve SHALL be reported
as a defect, and a bare number its document uses twice SHALL be reported as ambiguous. The check
SHALL need no build, no test run, no device and no network access.

#### Scenario: A bare number a guideline body writes is ambiguous

- **WHEN** a guideline body writes a reference to a section whose number its document uses twice, without the
  heading text
- **THEN** the check exits non-zero, reports the reference as ambiguous, and names the file the reference is
  written in
- **Case**: `a bare duplicated number in a guideline body is reported as ambiguous`

#### Scenario: The heading text resolves a body reference

- **WHEN** the same reference carries the heading text of one of the two sections
- **THEN** the check accepts the file and counts that reference among the references it resolved
- **Case**: `a heading text in a guideline body resolves its duplicated number`

#### Scenario: A guideline body names a section its document lacks

- **WHEN** a guideline body writes a reference to a section number the document it names does not carry
- **THEN** the check exits non-zero, reports the reference as a missing section, and names the file the
  reference is written in
- **Case**: `a guideline body naming a section its document lacks is reported`

#### Scenario: A quoted reference is not a reference

- **WHEN** a guideline body quotes a bare ambiguous reference inside a fenced code block, and writes no other
  ambiguous reference
- **THEN** the check accepts the file and its count excludes the block's reference
- **Case**: `a reference inside a fenced block is not a reference`

#### Scenario: A change artifact is history and is not read

- **WHEN** an archived change and an open change artifact each carry a bare ambiguous reference, and no
  guideline body does
- **THEN** the check exits 0, its count excludes both, and neither file is rewritten
- **Case**: `a reference in a change artifact is not checked`

### Requirement: The entry document states the validator's strict rules

The always-read entry document SHALL state, beside the artifact rules the pipeline gates, the two rules
`openspec validate --strict` fails on: a `MODIFIED` block carries every scenario the shipped spec still has,
the unchanged ones repeated verbatim, and a requirement's body is at most 500 characters, its scenarios
excluded. It SHALL name that strict invocation as the check an author runs before an artifact is finished.

#### Scenario: An author reads both rules before writing an artifact

- **WHEN** a reader takes the artifact rules from the entry document's `OpenSpec Workflow` section
- **THEN** that section states that a `MODIFIED` block repeats every shipped scenario verbatim, and that a
  requirement's body is at most 500 characters, its scenarios excluded
- **Case**: `probe#theEntryDocumentStatesTheValidatorStrictRules` — `evidence/probe.sh` reads the section and
  prints `FAIL` with the absent statement named, exiting 1 while one is missing (`evidence/red-on-head.log`,
  2 of 2 cases failed on HEAD; `evidence/green-after-fix.log`, 0 of 2 after the statement was added)

#### Scenario: The section names the command that catches them

- **WHEN** the section is read for how an artifact is checked before it is finished
- **THEN** it names `openspec validate <change> --strict`, and the `--changes --strict` form for the open set
- **Case**: `probe#theEntryDocumentNamesTheStrictValidationCommand` — the same probe's second case, which
  exits 1 on HEAD and 0 once the command line is in the section
