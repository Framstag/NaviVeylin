# documentation-ownership — deltas

## ADDED Requirements

### Requirement: A section reference a guideline body writes is resolved

Every section reference a guideline body makes to another guideline document SHALL resolve against that
guideline, the way a reference in the routing table does: a reference that does not resolve SHALL be reported
as a defect, and a bare number its document uses twice SHALL be reported as ambiguous. The check
SHALL need no build, no test run, no device and no network access.

#### Scenario: A bare number a guideline body writes is ambiguous

- **WHEN** a guideline body writes a reference to a section whose number its document uses twice, without the
  heading text
- **THEN** the check exits non-zero, reports the reference as ambiguous, and names the file the reference is
  written in
- **Case**: `a bare duplicated number in a guideline body is reported as ambiguous`

#### Scenario: The heading text resolves a body reference

- **WHEN** the same reference carries the heading text of one of the two sections
- **THEN** the check accepts the file and counts that reference among the references it resolved
- **Case**: `a heading text in a guideline body resolves its duplicated number`

#### Scenario: A guideline body names a section its document lacks

- **WHEN** a guideline body writes a reference to a section number the document it names does not carry
- **THEN** the check exits non-zero, reports the reference as a missing section, and names the file the
  reference is written in
- **Case**: `a guideline body naming a section its document lacks is reported`

#### Scenario: A quoted reference is not a reference

- **WHEN** a guideline body quotes a bare ambiguous reference inside a fenced code block, and writes no other
  ambiguous reference
- **THEN** the check accepts the file and its count excludes the block's reference
- **Case**: `a reference inside a fenced block is not a reference`

#### Scenario: A change artifact is history and is not read

- **WHEN** an archived change and an open change artifact each carry a bare ambiguous reference, and no
  guideline body does
- **THEN** the check exits 0, its count excludes both, and neither file is rewritten
- **Case**: `a reference in a change artifact is not checked`
