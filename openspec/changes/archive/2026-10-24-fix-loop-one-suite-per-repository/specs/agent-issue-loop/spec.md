## ADDED Requirements

### Requirement: One suite at a time runs in a repository

The loop's self-test SHALL hold a lease beside the scratch root its repository resolves, and SHALL refuse — exit 2,
naming the lock's owner and the moment it was taken — a second invocation in that repository that may run more than
one case. A lease whose owner is not running SHALL be taken over and named. An invocation that runs no case, or
exactly one named case, SHALL take no lease.

#### Scenario: A second suite in one repository is refused before it runs a case

- **WHEN** a whole suite is running in a repository and a second invocation that may run more than one case starts
  in the same repository
- **THEN** it exits 2, names the rule and the process that holds the lock, runs no case, and leaves that lock in
  place
- **Case**: `driver#aSecondSuiteInOneRepositoryIsRefused`

#### Scenario: One named case is not a suite, and a pattern is

- **WHEN** the lock is held and an invocation runs one named `--case`, then a `--case` pattern, then a `--group`
- **THEN** the named case runs, and the pattern and the group are refused with exit 2 having run no case
- **Case**: `driver#aSecondSuiteInOneRepositoryIsRefused`

#### Scenario: A lease whose owner is gone is taken over and released

- **WHEN** the lock stands with the pid of a process that is not running any more
- **THEN** the next suite takes it over in one line naming the process that is gone, runs its cases, and releases
  the lock so the repository is free for the suite after it
- **Case**: `driver#aSecondSuiteInOneRepositoryIsRefused`
