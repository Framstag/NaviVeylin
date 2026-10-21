## ADDED Requirements

### Requirement: A capability spec states what the capability is for

Every capability spec's `## Purpose` section SHALL be a sentence its author wrote, saying what the
capability is for. It SHALL NOT keep the sentence `openspec archive` writes for a capability it creates,
`TBD - created by archiving <change>. Update Purpose after archive.`, because that section is read from a
delta only when the capability is created: no later change can replace it, and `openspec validate <id>
--type spec` reports it as a warning while still exiting 0. A check over `openspec/specs/*/spec.md` SHALL
name every spec that still carries that sentence, and SHALL exit non-zero while one does.

#### Scenario: The archive placeholder is reported by a check, not by a warning

- **WHEN** a capability spec's `## Purpose` is still `TBD - created by archiving …`
- **THEN** the artifact-hygiene check prints that spec's path and exits non-zero
- **AND** the case `artifact-hygiene#theArchivePlaceholderIsReported` — the step's `awk` pass over
  `openspec/specs/*/spec.md`, which reads only the lines between a `## Purpose` heading and the next `## `
  heading, so the sentence may be quoted freely in requirement prose — exercises this: on a tree whose
  Purpose is the placeholder it prints that spec's path and line, and the step fails

#### Scenario: A written Purpose passes the check

- **WHEN** every capability spec's `## Purpose` is a sentence its author wrote
- **THEN** the check prints that every capability spec states its Purpose and exits 0
- **AND** the case `artifact-hygiene#aWrittenPurposePasses` — the same pass over a repaired tree —
  exercises this: no path is printed and the step passes
