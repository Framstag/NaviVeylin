## ADDED Requirements

### Requirement: A suite that reported no summary invents no number

The gate SHALL take a suite's case and failure counts only from that suite's own summary line. A suite that
fails without reporting one SHALL be recorded as an error instead: its tally carries `tests="0"`,
`failures="0"` and `errors="1"`, and the gate's own line carries the error count beside its case and failure
counts. A tally meant to prove what executed SHALL NOT state a case or a failure the suite did not report.

#### Scenario: A suite that failed before it reported anything

- **WHEN** a suite exits non-zero without writing either summary line
- **THEN** its tally carries zero cases, zero failures and one error, the gate's own line carries the same
  three facts and still names the suite as failing, and the gate's exit status stays non-zero
- **Case**: `gate#aFailingSuiteWithNoSummaryInventsNoTest`

#### Scenario: A suite that reported its numbers keeps them

- **WHEN** a suite fails and writes `<name>: M of N cases failed`
- **THEN** its tally carries N cases and M failures with no error, and the gate's line carries the same numbers,
  so a reported count is what the run's own check reads
- **Case**: `gate#aFailingSuiteIsReadFromTheSameStream`
