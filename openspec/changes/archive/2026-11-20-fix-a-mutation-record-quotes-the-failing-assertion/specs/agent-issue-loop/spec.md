## ADDED Requirements

### Requirement: A mutation record quotes the assertion the case failed on

The `RED` line the mutation harness prints for a record SHALL quote the assertion the case failed on, and SHALL name
the line of the case's own log it was read from. The reason SHALL be the message the case failed with — the first
line of the last run of the two-space indented lines the suite prefixes — and SHALL NOT be the first line the case
printed, which is its fixture's own output. That line's number SHALL stand beside the record's timestamp.

#### Scenario: A record whose case prints before it fails quotes the assertion

- **WHEN** a record is run whose case prints its fixture's own lines before it fails, and the harness prints the
  record's `RED` line
- **THEN** the reason beside the timestamp quotes the assertion the case failed on, not the fixture's first line, and
  it names the line of the case's log it was read from, which is not that log's first line
- **Case**: `tools/loop/tests/loop-mutations-selftest.sh` (`a red record quotes the assertion the case failed on`)
