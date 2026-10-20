# Spec Delta — unit-test-suite-runtime

## ADDED Requirements

### Requirement: A test that builds a time-hopped repository wires the hop

Every unit test or test helper that constructs a component whose persisted state is published inside a
dispatcher hop SHALL point that hop at a dispatcher the test drives. The component SHALL keep its production
value and its production ordering: the repair is confined to test sources.

#### Scenario: Every construction in the module's test sources is wired

- **WHEN** the test sources of every test-bearing module are read and the repository's construction is found in
  them
- **THEN** every construction names a dispatcher the test drives and none is reported, with the number of roots
  and files read printed before the verdict
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

### Requirement: An unwired hop is refused from the sources alone

A construction that leaves the hop at the component's production value SHALL be refused by a host check that
names the file and the line and that decides its verdict from the sources, without executing a test.

#### Scenario: A construction that leaves the hop at the production value is refused

- **WHEN** a test source constructs the repository and never names the hop's dispatcher
- **THEN** the check fails and its finding carries the file, the line and the construction, so the site can be
  repaired without running the suite
- **Case**: `SearchHistoryDispatcherRuleTest.anUnwiredConstructionIsReported`

#### Scenario: A module whose sources disappear fails the check

- **WHEN** a declared test-source root no longer exists — a module was renamed or removed
- **THEN** the check fails instead of scanning nothing, so the invariant cannot be lost silently
- **Case**: `SearchHistoryDispatcherRuleTest.everyTestBearingModuleIsScanned`
