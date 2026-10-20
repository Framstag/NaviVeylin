# issue-backlog — deltas

## MODIFIED Requirements

### Requirement: One issue per distinct finding

The tooling SHALL create exactly one issue per distinct finding, and SHALL refuse to create a second
issue for a finding whose computed fingerprint already matches one, naming the issue that already exists. A
finding's required `class` and `category` SHALL each be filed as a label of its namespace, so a finding cannot
state a kind or an area the tracker does not carry, and a value the vocabulary does not hold SHALL be refused
rather than dropped.

#### Scenario: A new finding is filed

- **WHEN** a finding is submitted with its kind, its area and its claim
- **THEN** one issue exists carrying that claim, and its labels record the kind and the area
- **Case**: `create#carriesTheFindingsClassAndCategory`

#### Scenario: The required fields are labels, not decoration

- **WHEN** a finding states its kind and its area only in its `class` and `category` fields — its `labels` array
  empty or absent — or states them in both places
- **THEN** the issue is created carrying `class/<value>` and `category/<value>`, each named once, and a value
  outside the closed vocabulary is refused before any write
- **Case**: `create#carriesTheFindingsClassAndCategory`

#### Scenario: A repeated finding is refused, not duplicated

- **WHEN** a finding whose fingerprint matches an existing issue is submitted
- **THEN** no issue is created, the call reports the outcome as a duplicate rather than as a failure,
  and it names the existing issue

#### Scenario: A deliberate duplicate is possible and recorded

- **WHEN** a caller asserts that a matching fingerprint describes a genuinely different finding
- **THEN** the issue is created and the override is recorded in the output

### Requirement: Labels carry the item's kind, area, state and provenance

The tooling SHALL express an item's kind, area, lifecycle state and provenance as labels in separate
namespaces, with exactly one value per namespace where the item has that fact, and SHALL draw every value
from a closed vocabulary held by the project. On the filing path — where the item is born and there is nothing
to displace — a label set that names two values of one namespace SHALL be refused before any write, with a
reason naming both values. The finding's `class` and `category` fields are one of the routes a value arrives
through, so a field that names a different value than the item's own labels for that namespace SHALL be refused
like any other conflicting route, never resolved in silence.

#### Scenario: Each fact has its own namespace

- **WHEN** an issue is created
- **THEN** its kind, its area, its state and its provenance are each a label in a distinct namespace,
  so selecting by one fact cannot select by another

#### Scenario: A value outside the vocabulary is refused

- **WHEN** a caller supplies a value that the project's vocabulary does not contain
- **THEN** the operation is refused with a reason naming the value, and no label is created, so a typo
  cannot hide an issue from every selector

#### Scenario: A field cannot contradict the labels it duplicates

- **WHEN** a finding's `labels` array names one class (or one category) and the finding's own `class` (or
  `category`) field names another
- **THEN** the filing is refused before any issue write, with exit 2 and a reason naming both values
- **Case**: `create#refusesAClassItsLabelsContradict`

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
