# documentation-ownership — the served context is a pointer

## ADDED Requirements

### Requirement: The project context has one owner

The `openspec/config.yaml` `context:` block SHALL be a pointer to the document that owns the project's facts, and
SHALL NOT restate them. A block that carries more than three non-empty lines, or that does not name `AGENTS.md`,
SHALL be reported by the artifact-hygiene check.

#### Scenario: A copied context block is reported

- **WHEN** `openspec/config.yaml` carries a `context:` block longer than three non-empty lines
- **THEN** `tools/check-openspec-hygiene.sh` exits 1 and its finding names the number of lines the block carries
- **AND** the case `artifact-hygiene#aContextBlockThatCopiesTheFactsIsRefused` — the checker's own `awk` pass over
  the block, which reads only the indented lines between `context:` and the next unindented key — exercises this:
  on a tree whose block is the six-line stack restatement the finding reads
  `the 'context:' block is 6 lines`, and the pass fails

#### Scenario: A block that names no owner is reported

- **WHEN** the `context:` block is within three lines but names no document
- **THEN** the artifact-hygiene check exits 1 and its finding says the block does not name `AGENTS.md`, because a
  reader of it cannot tell where the facts live
- **AND** the same case's second arm builds a three-line block that names no owner and asserts that finding

#### Scenario: A pointer passes and an absent block is not a finding

- **WHEN** the block names `AGENTS.md` in three lines or fewer, or `config.yaml` carries no `context:` block
- **THEN** the check reports nothing for it and exits 0 on that class, so a project that serves no context is not
  refused, and the case `artifact-hygiene#aWrittenPurposePasses` runs the clean tree — which carries the passing
  pointer — for exactly that reading

#### Scenario: The served context still routes by section

- **WHEN** the context is read back through
  `openspec instructions apply --change a-served-context-is-a-pointer --json | jq -r .context`
- **THEN** it names `AGENTS.md`, names that its "Documentation Map" routes a topic to a section of
  `guidelines/<concern>.md`, and lists no whole guideline document as required reading — so the shipped
  requirement "The change-artifact guidance reads by section" still holds of the block that replaces it
