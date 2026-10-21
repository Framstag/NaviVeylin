## ADDED Requirements

### Requirement: A tracker failure the transport caused is not an answer

Every call the tooling makes to the tracker SHALL pass one seam that classifies the failure. A failure the
transport caused — a TCP or TLS timeout, a DNS failure, a reset or refused connection, a 5xx, a secondary
rate limit — SHALL be retried with a backoff before the caller sees it, and a failure that survives the
retries SHALL be reported as *unknown* through a distinct exit status and a `tracker-unreachable` reason, so
a caller can tell an outage from an answer instead of reading one as the other.

A failure the tracker itself produced — a validation refusal, an authorisation refusal, a not-found — SHALL
NOT be retried, because repeating it spends the run's time to hear the same answer.

An append SHALL NOT be retried either, because the tracker may already have accepted it: posting a comment
and filing a new issue are exempt, and their transport failure reaches the caller unchanged.

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

#### Scenario: An append is attempted once

- **WHEN** a comment or a new issue fails the way a dropped connection fails
- **THEN** the seam attempts it exactly once and hands the failure back, so nothing is posted or filed twice
- **Case**: `lib#doesNotRetryAnAppend`

### Requirement: An identity that could not be read is not a refusal

An identity that could not be read SHALL NOT be reported as `author-not-allowlisted`: an unreachable tracker
SHALL answer `tracker-unreachable` with no verdict, and only an answer that names no identity may be reported
as a policy refusal. The identity SHALL be read once, in the shared preflight every write path calls, so no
call site can fold the distinction back into an empty string; a decision that needs no identity — `check`,
which judges the issue's own author from the record it is handed — SHALL NOT read one.

#### Scenario: An unreachable tracker is not a policy refusal

- **WHEN** the identity read cannot reach the tracker
- **THEN** the preflight reports `tracker-unreachable` with decision `unknown` and exit 75, and neither it nor
  the text form reports `author-not-allowlisted`
- **Case**: `policy#preflightReportsAnUnreachableTracker`

#### Scenario: Every write path reports the outage, not a refusal

- **WHEN** a write path's identity read cannot reach the tracker
- **THEN** it exits 75 with `tracker-unreachable` instead of the `author-not-allowlisted` an empty login
  produced
- **Case**: `update#reportsAnUnreachableTrackerInsteadOfARefusal`

#### Scenario: A decision the issue record answers does not depend on the tracker

- **WHEN** `policy.sh check` judges an issue record while the identity read would fail
- **THEN** it decides from the record — an allowlisted author with the opt-in label allowed, a foreign author
  refused — and reads no identity at all
- **Case**: `policy#checkAnswersWithoutAnIdentityRead`
