# spec-feature-index delta

## MODIFIED Requirements

### Requirement: An unclassified spec id stops the run

Every shipped spec id SHALL belong to exactly one feature area and SHALL be classified either user-visible or
internal, and SHALL also be classified for the surface it applies to. A shipped spec id that the classification does
not name SHALL be reported by id, SHALL make the run fail, and SHALL produce no regenerated document.

A capability id that an open change's delta specs introduce SHALL be classified before that change is archived. The
run SHALL read those delta specs from `openspec/changes/<change>/specs/<capability>/spec.md`, SHALL NOT read the
delta specs of a change under `openspec/changes/archive/`, SHALL report an id they name that the classification does
not name together with the open change that owes the line, SHALL make the run fail, and SHALL produce no regenerated
document. A classification entry for such an id SHALL be reported as `pending` — classified ahead of its archive, an
entry for work the archiving move publishes rather than for something gone — and SHALL NOT by itself produce an error.
A classification entry naming an id the tree no longer holds SHALL be reported as `stale`, which is an entry of its
own, and SHALL NOT by itself produce an error either. The run SHALL print the two figures apart, so that an entry for a
capability on its way cannot be read as an entry for one that has left.

#### Scenario: A new spec id fails the run

- **WHEN** a spec exists in the shipped-spec directory that the classification does not name
- **THEN** the run reports that spec id, exits non-zero, and no document is written or overwritten
- **Case**: `gate: an unclassified shipped spec id fails the run`

#### Scenario: A capability an open change introduces fails until it is classified

- **WHEN** an open change carries a delta spec under `openspec/changes/<change>/specs/` for a capability id that the
  classification does not name
- **THEN** the run reports that spec id, exits non-zero, and no document is written or overwritten
- **Case**: `gate: a capability an open change introduces must be classified`

#### Scenario: The failure names the id and the change that owes the line

- **WHEN** an open change introduces a capability the classification does not name
- **THEN** the failure names that capability id and the open change that owes the line, so that the missing entry is
  added in the change that introduced it rather than in a later one
- **Case**: `gate: the unclassified open capability is named`

#### Scenario: A delta classified before it ships passes the run

- **WHEN** an open change introduces a capability that the classification names, and no shipped spec id is
  unclassified
- **THEN** the run proceeds, reports nothing unclassified, and reports that entry as pending — classified ahead of its
  archive — without failing on it
- **Case**: `gate: a classified delta passes before it ships`, `gate: a delta-only classification is reported as
  pending, not as stale`

#### Scenario: An entry that is pending is not reported as stale

- **WHEN** the classification names a capability id an open change's delta introduces, and that change has not been
  archived
- **THEN** the run counts the id in its pending figure and prints no stale figure for it — `stale: 0` where nothing
  else has left the tree — and names it in its report as classified ahead of its archive, never as a stale entry
- **Case**: `gate: a delta-only classification is not a stale entry`, `gate: the pending entry is named as one`,
  `gate: a pending entry is not reported as stale`

#### Scenario: The two figures are printed apart

- **WHEN** the run's classification check prints what it read
- **THEN** it prints `stale: <n>` for the entries whose spec id tree no longer holds and
  `pending (classified ahead of its archive): <n>` for the open changes' deltas, each on a line of its own, and its
  report names the pending id with the sentence `classified ahead of its archive` and the stale one as a `stale
  classification entry`
- **Case**: `gate: a stale entry is reported`, `gate: the stale entry is named`, `gate: the pending entry is named as
  one`

#### Scenario: An archived change is history

- **WHEN** a capability spec exists only under `openspec/changes/archive/` and the classification does not name it
- **THEN** the run reports nothing unclassified for it and does not fail, because the shipped set and the open
  changes are the only two sets it reads
- **Case**: `gate: an archived change's delta is not read`

#### Scenario: A classified spec id passes

- **WHEN** every shipped spec id is named by the classification
- **THEN** the run proceeds and reports no unclassified id
- **Case**: `gate: a delta-only classification leaves nothing unclassified`

#### Scenario: A removed spec id is reported without failing on it

- **WHEN** the classification names a spec id that no longer exists in the shipped-spec directory
- **THEN** the run reports that id as stale and continues, and the stale entry does not by itself produce an error
- **Case**: `gate: a stale classification entry does not fail the run`

#### Scenario: A surface outside the known set fails the run

- **WHEN** a classification assigns a spec id a surface that is not one of the surfaces the catalogue can render
- **THEN** the run reports the spec id and the unknown surface, exits non-zero, and produces no regenerated document
- **Case**: `gate: a surface outside the vocabulary fails the run`

#### Scenario: A capability's surface is reported

- **WHEN** the index reports a capability
- **THEN** it reports the surface its spec is classified for, so that a surface cannot be inferred from a spec id's
  name alone
- **Case**: `keys: the surface a spec is classified for is reported`