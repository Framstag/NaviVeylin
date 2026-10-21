## ADDED Requirements

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
