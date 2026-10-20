# Spec Delta — unit-test-suite-runtime

## ADDED Requirements

### Requirement: A test that builds the search-history repository wires its I/O hop

Every unit test or test helper that constructs a `SearchHistoryRepository` SHALL point that repository's I/O hop
(`defaultDispatcher`) at a dispatcher the test drives. The repository SHALL keep its production value
(`Dispatchers.Default`) and its production ordering: the repair is confined to test sources.

#### Scenario: Every construction in the module's test sources is wired

- **WHEN** the test sources of every test-bearing module are read and the repository's construction is found in
  them
- **THEN** every construction names a dispatcher the test drives and none is reported
- **Case**: `SearchHistoryDispatcherRuleTest.noTestLeavesTheSearchHistoryRepositoryIoHopOnAProductionDispatcher`

#### Scenario: A chained wiring is accepted

- **WHEN** a construction points the hop at the test's dispatcher through a chained `.also { … }` or
  `.apply { … }` on the construction itself
- **THEN** the check accepts it, so the form the repair writes is the form the check reads
- **Case**: `SearchHistoryDispatcherRuleTest.aChainedWiringIsAllowed`

#### Scenario: A property wired later in the same file is accepted

- **WHEN** a test assigns the repository to a property and points that property's hop at the test's dispatcher
  later in the same file — the form the class setup methods already use
- **THEN** the check accepts it, so the rule does not force one spelling on every test class
- **Case**: `SearchHistoryDispatcherRuleTest.aNameWiredLaterInTheFileIsAllowed`

### Requirement: An unwired search-history hop is refused from the sources alone

A host check over the test sources SHALL refuse a `SearchHistoryRepository` construction that leaves its I/O hop
at the repository's production value, and its finding SHALL name the file and the line.

#### Scenario: A construction that leaves the hop at the production value is refused

- **WHEN** a test source constructs the repository and never names the hop's dispatcher
- **THEN** the check fails and its finding carries the file, the line and the construction, so the site can be
  repaired without running the suite
- **Case**: `SearchHistoryDispatcherRuleTest.anUnwiredConstructionIsReported`

#### Scenario: A module whose sources disappear fails the check

- **WHEN** a declared test-source root no longer exists — a module was renamed or removed
- **THEN** the check fails instead of scanning nothing, so the invariant cannot be lost silently
- **Case**: `SearchHistoryDispatcherRuleTest.everyTestBearingModuleIsScanned`

Scope of both requirements, so the claim is not wider than the case: the obligation binds
`SearchHistoryRepository`'s hop only. The sibling seams of the same shape — `FavoriteRepository.kt:101`,
`MapCanvasViewModel.kt:431`, `RoutePanelViewModel.kt:228`, `AddressBookViewModel.kt:62` (all named
`defaultDispatcher`) and `MemoryPressureResponder.kt:87` (named `dispatcher`) — are outside it: this change
neither sweeps their tests nor reads them, and extending the guard to them is a separate item. The scan reads `*.kt` under each test-bearing module's
`src/test/java`, so a construction that lives in another module or is reached by reflection is outside it; and
the check lives in `:app`, so a `:auto`-only run enforces nothing on `:auto` sources.
