## MODIFIED Requirements

### Requirement: The skill a filer is routed to names the type axis

Every skill a filing session leaves through SHALL name `--type` and `--parent` beside the flags it documents, so a
filing that follows the skill can carry the tracker's own classification — the issue type and the parent link are
the tracker's own axis and not labels (`guidelines/Backlog.md` §3). The four a session is routed through SHALL be
`create-issue`, `update-issue`, `file-finding` and `reshape-issue`, and the two that file a child SHALL name the
parent link beside the cause they file.

#### Scenario: A skill that loses the pointer fails the suite

- **WHEN** `.pi/skills/create-issue/SKILL.md`, `.pi/skills/update-issue/SKILL.md`,
  `.pi/skills/file-finding/SKILL.md` or `.pi/skills/reshape-issue/SKILL.md` no longer names both `--type` and
  `--parent`
- **THEN** the issue tooling's suite fails with a case naming the file, not a session discovering it while filing
- **AND** the call the two filing skills document carries both flags on the line itself, so the mention the prose
  below it makes is not what the case is satisfied by
- **AND** the case reads all four files in the tree and needs no token, so it runs in the offline suite
- **Case**: `docs#theFilingSkillsNameTheTypeAxis`
