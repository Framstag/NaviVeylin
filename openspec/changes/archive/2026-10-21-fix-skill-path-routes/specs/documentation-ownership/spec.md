# documentation-ownership — deltas

## ADDED Requirements

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
