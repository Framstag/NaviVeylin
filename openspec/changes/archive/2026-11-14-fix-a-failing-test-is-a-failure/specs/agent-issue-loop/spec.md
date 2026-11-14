# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A suite that reported no summary invents no number

The gate SHALL take a suite's case count, and the failure count of a suite that wrote a summary, only from that
suite's own summary line. A suite that fails without reporting one SHALL have the failing cases it named itself
counted as its failures — a step's `<class> > <case> FAILED` line is one failing case — and SHALL be recorded as
an error instead where it named none: that tally carries
`tests="0"`, `failures="0"` and `errors="1"`, and the gate's own line carries the same numbers beside its case and
failure counts. A step the gate ended at its bound SHALL keep the error, whichever of those lines it printed before
it was ended. A tally meant to prove what executed SHALL NOT state a case the suite did not report.

#### Scenario: A suite that failed before it reported anything

- **WHEN** a suite exits non-zero without writing either summary line and naming no failing case of its own
- **THEN** its tally carries zero cases, zero failures and one error, the gate's own line carries the same
  three facts and still names the suite as failing, and the gate's exit status stays non-zero
- **Case**: `gate#aFailingSuiteWithNoSummaryInventsNoTest`

#### Scenario: A suite that reported its numbers keeps them

- **WHEN** a suite fails and writes `<name>: M of N cases failed`
- **THEN** its tally carries N cases and M failures with no error, and the gate's line carries the same numbers,
  so a reported count is what the run's own check reads
- **Case**: `gate#aFailingSuiteIsReadFromTheSameStream`

#### Scenario: A step that named its failing cases is not an error

- **WHEN** a step exits non-zero having written one `<class> > <case> FAILED` line per failing case, the error under
  each, and no summary line — the shape a JVM test task prints
- **THEN** its tally carries zero cases, one failure per case it named and no error, the gate's own line carries
  the same three numbers, and the tally keeps the failing cases' own lines as its failure text
- **Case**: `gate#aTestFailureIsAFailureNotAnError`
