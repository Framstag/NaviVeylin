# Spec Delta — documentation-ownership

## ADDED Requirements

### Requirement: The entry document names the skill a reader needs by its path

The entry document SHALL name, by its path — `.pi/skills/<name>` — the skill that files a finding as one tracker
item and the skill that turns one on-device verification into evidence, so a reader reaches each skill from the
document that names the job instead of by listing `.pi/skills/`. A path named this way SHALL be one the tree
carries, as "A document that names a skill resolves to a skill that exists" requires; this rule adds a pointer, and
no statement either skill makes moves out of the section that owns it.

#### Scenario: The loop's two ends are reachable by path

- **WHEN** a reader follows the entry document to file a finding as a tracker item, or to turn an on-device
  verification into evidence
- **THEN** the entry document names `.pi/skills/create-issue` and `.pi/skills/device-check` by their paths, and the
  route check resolves both, so a removal of either pointer fails the suite instead of a run
- **Case**: `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`
