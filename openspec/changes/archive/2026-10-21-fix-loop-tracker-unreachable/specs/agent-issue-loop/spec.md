## ADDED Requirements

### Requirement: A tracker failure the transport caused is not an answer

Every call the tooling makes to the tracker SHALL pass one seam that classifies the failure. A failure the
transport caused — a TCP or TLS timeout, a DNS failure, a reset or refused connection, a 5xx, a secondary
rate limit — SHALL be retried with a backoff before the caller sees it, and a failure that survives the
retries SHALL be reported as *unknown* through a distinct exit status and a `tracker-unreachable` reason, so
no caller can read an outage as an empty answer, as a refusal or as an absence.

A failure the tracker itself produced — a validation refusal, an authorisation refusal, a not-found — SHALL
NOT be retried, because repeating it spends the run's time to hear the same answer.

An identity that could not be read SHALL NOT be reported as `author-not-allowlisted`: an unreachable tracker
SHALL answer `tracker-unreachable` with no verdict, and only an answer that names no identity may be reported
as a policy refusal. An append (a comment) SHALL NOT be retried, because it may already have landed.

#### Scenario: One timeout is retried instead of becoming an empty answer

- **WHEN** a read fails the way a dropped connection fails, and a later attempt answers
- **THEN** the caller receives that answer, and no caller sees the outage
- **Case**: `lib#retriesATransientFailure`

#### Scenario: A failure that outlasts the retries is unknown, not empty

- **WHEN** every attempt of one call fails transiently
- **THEN** the call reports `tracker-unreachable` and exits 75, so the caller can tell it from an answer
- **Case**: `lib#reportsAnUnreachableTrackerAfterTheLastRetry`

#### Scenario: An authoritative refusal is passed through, not retried

- **WHEN** the tracker refuses a call rather than failing to carry it
- **THEN** the refusal reaches the caller unchanged and at once
- **Case**: `lib#doesNotRetryAnAuthoritativeFailure`

#### Scenario: An unreachable tracker is not a policy refusal

- **WHEN** the identity read cannot reach the tracker
- **THEN** the preflight reports `tracker-unreachable` with decision `unknown` and exit 75, and neither it nor
  the text form reports `author-not-allowlisted`
- **Case**: `policy#preflightReportsAnUnreachableTracker`
