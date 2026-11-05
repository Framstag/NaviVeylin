# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The suite runs as legs on separate runners, and a leg is its own verdict

The self-test SHALL be runnable as several independent legs on separate runners, each leg the cases of one group
selection, so the suite's wall clock is bounded by its slowest leg rather than by one runner's core count. A leg
SHALL state the selection it ran against the declared set in its own summary and SHALL keep its repository and its
scratch root to itself, and the workflow SHALL take a leg's own exit status as a verdict of the run.

#### Scenario: A leg runs the cases of its own selection

- **WHEN** the suite is invoked with a group selection and a declared set larger than that group
- **THEN** only that group's cases run, the summary counts them against the declared set, and no case of another
  group prints a line
- **Case**: `driver#aGroupFilterRunsOnlyItsOwnCases`

#### Scenario: Two legs do not contend for the suite's lease

- **WHEN** two legs of one suite run at the same moment, each in its own checkout
- **THEN** neither refuses the other, because the lease is per repository and a leg's repository is its own — the
  state `driver#aSecondSuiteInOneRepositoryIsRefused` separates
- **Case**: none — the property is a leg's checkout, which is the workflow's; the lease's own case states the rule
  it rests on

#### Scenario: A red leg fails the run

- **WHEN** one leg fails a case and the other legs pass
- **THEN** the run is red and names the leg, exactly as one red case in a single-leg suite does today
- **Case**: none — a workflow's verdict is the host's; it is read from the dispatch run that recorded the leg split
