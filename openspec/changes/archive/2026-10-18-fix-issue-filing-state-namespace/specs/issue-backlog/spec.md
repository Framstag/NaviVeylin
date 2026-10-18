# Spec Delta

## MODIFIED Requirements

### Requirement: Labels carry the item's kind, area, state and provenance

The tooling SHALL express an item's kind, area, lifecycle state and provenance as labels in separate
namespaces, with exactly one value per namespace where the item has that fact, and SHALL draw every value
from a closed vocabulary held by the project. On the filing path — where the item is born and there is nothing
to displace — a label set that names two values of one namespace SHALL be refused before any write, with a
reason naming both values.

#### Scenario: Each fact has its own namespace

- **WHEN** an issue is created
- **THEN** its kind, its area, its state and its provenance are each a label in a distinct namespace,
  so selecting by one fact cannot select by another

#### Scenario: A value outside the vocabulary is refused

- **WHEN** a caller supplies a value that the project's vocabulary does not contain
- **THEN** the operation is refused with a reason naming the value, and no label is created, so a typo
  cannot hide an issue from every selector

#### Scenario: A priority namespace is reserved

- **WHEN** the taxonomy is created
- **THEN** it contains a priority namespace whose values are the project's bands in a fixed range, and the
  tooling that computes a band is not the tooling that defines what a band is

#### Scenario: Provenance is distinguishable

- **WHEN** an issue was imported, filed by an automated check, or filed by a person
- **THEN** its provenance label says which, so a reader can tell an imported item from a filed one

#### Scenario: create#refusesTwoValuesOfOneNamespace

- **WHEN** a filing's merged label set names two values of one namespace — the finding's own labels carrying
  one state and `--status` naming another, or a `--label` adding a second value of any other namespace
- **THEN** the filing is refused before any issue write, with exit 2 and a reason naming both values, and an
  item whose state is named only through `--status` or only through the labels still files
