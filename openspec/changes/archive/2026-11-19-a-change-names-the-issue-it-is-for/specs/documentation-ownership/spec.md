## ADDED Requirements

### Requirement: An open change names the issue it is for

An open change SHALL declare the item it answers in the `x-issue` field of its own `.openspec.yaml`, `none` when no
item is owed, and a tree SHALL NOT hold two open changes that name one issue.

#### Scenario: A change declares the issue it answers

- **WHEN** an open change's `.openspec.yaml` carries `x-issue: 928`
- **THEN** the artifact check reports that directory under #928, reading neither the directory name nor the
  proposal's prose
- **Case**: `artifact-hygiene#theIssueFieldIsReported`

#### Scenario: Two open changes naming one issue are refused

- **WHEN** two open change directories carry `x-issue: 928`, whether one session wrote both or two did
- **THEN** `tools/check-openspec-hygiene.sh` exits 1 and names both directories in its finding, so the collision is
  found when the second change is written rather than by the archive that leaves the first unarchivable
- **Case**: `artifact-hygiene#twoOpenChangesNamingOneIssueAreRefused`

#### Scenario: A change that names no issue is refused

- **WHEN** an open change carries neither an `x-issue` field nor a `fix-issue-<N>-` prefix in its directory name
- **THEN** the artifact check exits 1 and names the directory, and a change whose finding is owed by no tracker
  item declares `x-issue: none`, which is a declaration rather than an absence
- **Case**: `artifact-hygiene#aChangeNamingNoIssueIsRefused` — the three states in one case: the refusal, the
  declared `none` beside it, and the number a `fix-issue-898-…` name supplies for a directory written before the
  field existed
