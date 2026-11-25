## MODIFIED Requirements

### Requirement: The skill a filer is routed to names the type axis

Every skill the entry document names as the tracker's filing surface SHALL name `--type` and `--parent` beside the
flags it documents, and a skill that shows a `tools/issues/create.sh` call SHALL carry both flags on that call, so a
filing that follows the skill can carry the tracker's own classification without inventing a field the call does not
show.

#### Scenario: A skill that loses the pointer fails the suite

- **WHEN** `.pi/skills/create-issue/SKILL.md`, `.pi/skills/update-issue/SKILL.md`,
  `.pi/skills/file-finding/SKILL.md` or `.pi/skills/reshape-issue/SKILL.md` no longer names both `--type` and
  `--parent`, or the documented `create.sh` call of either filing skill no longer carries both flags
- **THEN** the issue tooling's suite fails with a case naming the file, not a session discovering it while filing
- **AND** the flags are asserted on the call a session copies, so a paragraph that merely mentions them does not
  keep the case green
- **AND** the case reads the four files in the tree and needs no token, so it runs in the offline suite
- **Case**: `docs#theFilingSkillsNameTheTypeAxis`
