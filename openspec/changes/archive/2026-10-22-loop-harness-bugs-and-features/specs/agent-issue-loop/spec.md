## MODIFIED Requirements

### Requirement: The queue repairs the tooling the run depends on first

The pre-screen SHALL rank a defect in a category the profile names as leading ahead of every other item, SHALL
mix the leading categories' other classes in one after every N other items (N the profile's
`firstCategoryEvery`), and SHALL let the profile's class order and the issue number decide each stream. The
category order SHALL be a rank, never a gate: it SHALL NOT change a verdict, a label or a count.

#### Scenario: Harness items lead the queue, bugs before improvements inside a group

A defect in the tooling that runs, measures, gates or reports the loop is paid again by every item after it and
can make the run's own report false, so it is worked before a defect in the shipping app. A harness improvement
is not a defect: it is mixed in at the profile's interval rather than leading.

- **WHEN** the pre-screen ranks a backlog whose eligible items are a harness bug, a harness improvement, an app
  bug and an app improvement
- **THEN** the harness bug leads, the harness improvement is mixed in after every `firstCategoryEvery` other
  item, and the app bug and app improvement follow in the profile's class order — so a leading-category
  improvement neither leads the queue nor disappears from it
- **Case**: `prescreen#harnessItemsLeadTheQueue`, `prescreen#oneHarnessImprovementPerFourAppItems`

#### Scenario: The leading categories are the profile's, and an empty list is no priority

- **WHEN** a project's profile names its own leading categories, or names none
- **THEN** the order uses exactly the declared values — a renamed set is ordered on, not merely scanned — and an
  empty list leaves the class order alone, so a project that wants no category priority says so in its profile
  rather than in the engine
- **AND** a profile that omits any role the pre-screen reads, including `firstCategoryClasses` and
  `firstCategoryEvery`, stops the pre-screen with a reason naming it, so a half-adapted profile cannot rank
  silently
- **Case**: `prescreen#aRenamedLeadingCategoryLeads`, `prescreen#priorityCategoriesAreReadFromTheProfile`,
  `prescreen#anUndeclaredRoleStopsTheScreen`, `prescreen#anUndeclaredLeadingIntervalStopsTheScreen`

#### Scenario: A priority category is a rank, not a gate

- **WHEN** an item in a leading category is on hold, or its author is not authorised
- **THEN** it is excluded exactly as any other item would be, and no verdict, label or count changes because of
  its category
- **Case**: `prescreen#priorityDoesNotBypassTheGate`

#### Scenario: The class order still decides inside a group

- **WHEN** two candidates share a leading category, or share a category that does not lead
- **THEN** the profile's class order decides first and the issue number second, so the category key never
  reverses a bug against an improvement of the same group
- **Case**: `prescreen#harnessItemsLeadTheQueue`, `prescreen#ranksBugsFirstAndKeepsFeatures`

## ADDED Requirements

### Requirement: A feature is planned before it is applied

A `class/feature` item the profile works SHALL be queued like a defect. It SHALL be worked by the
propose-first path: the worker SHALL write an OpenSpec change named `add-<slug>` whose proposal, capability
delta, design and tasks exist before production code, and whose acceptance scenario is the red case that fails
on HEAD because the capability is absent. A feature SHALL NOT be refused merely because it is a feature.

#### Scenario: A feature is queued, not refused

- **WHEN** the profile's `workedClasses` names `feature` and an eligible feature is in the backlog
- **THEN** the pre-screen gives it an eligible verdict and a rank in the profile's class order, instead of a
  `class-feature` refusal
- **Case**: `prescreen#ranksBugsFirstAndKeepsFeatures`

#### Scenario: A feature's plan precedes its apply

- **WHEN** the worker takes a `class/feature` item
- **THEN** its prompt carries the item's class, names the `add-<slug>` change and the acceptance scenario it
  owes as the red case, and the proposal, capability delta, design and tasks are written before production code
- **Case**: `prompt#workerCarriesTheFeaturePath`

#### Scenario: A profile may still refuse features

- **WHEN** the profile's `refusedClasses` names `feature`
- **THEN** the item is refused with `class-feature` and the profile's refusal label, exactly as before
- **Case**: `prescreen#aRefusedClassStillStopsTheScreen`
