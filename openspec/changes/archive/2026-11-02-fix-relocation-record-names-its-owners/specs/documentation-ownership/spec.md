# Spec Delta — documentation-ownership

## ADDED Requirements

### Requirement: A relocation record names the owners it cites

When a change proves that a rule it removed from the entry document is stated elsewhere, it SHALL cite each owner
as a path of its own, the way the removed block named them, and SHALL NOT cite a glob — the form a directory's
complete listing takes — in their place: a reader checks the claim by the owners it names. An archived record SHALL
NOT be inspected or rewritten, because it is history rather than a defect in the tree.

#### Scenario: An owner cited as a directory listing is reported

- **WHEN** an open change's record proves its owners with an `owner:` line whose citation is a glob — the form a
  directory's complete listing takes, as `owner: .pi/skills/*/SKILL.md` beside all 35 contracts was
- **THEN** the check exits non-zero and prints the record's file and line, so the citation is replaced by the owners
  the removed block named, one path each — a citation quoted inside a fenced block is a quotation rather than a
  citation, and the same lines under `openspec/changes/archive/` are reported by nothing, because that record is
  history rather than a defect in the tree
- **Case**: `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported` — builds the record above into an open
  change and into an archived one in a throwaway tree, and requires the checker to report the first with its file
  and line (`openspec/changes/demo/evidence/relocation.txt:2`) and to name nothing under `archive/`. On base
  `650028bf` it fails: `an owner cited as a directory listing passed (exit 0)` (tests=1 failures=1,
  `evidence/red-on-HEAD.log`)
