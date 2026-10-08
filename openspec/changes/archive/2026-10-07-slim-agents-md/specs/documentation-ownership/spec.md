# Spec Delta

## ADDED Requirements

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
