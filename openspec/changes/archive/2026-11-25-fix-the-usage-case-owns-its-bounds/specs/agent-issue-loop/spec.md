## ADDED Requirements

### Requirement: A case that counts a phase's children owns the bounds that decide them

A case whose count of a phase's children a bound can decide SHALL record that bound in a profile copy of its own and
point the run at it (`LOOP_CONF`), keeping the reading it stages below the end the run would take. A case that asserts
how many children a phase started SHALL name each one where the run records a name for it, so a child the case did not
stage is named and not counted.

#### Scenario: A usage case's child count is the phase's, not a bound's

- **WHEN** a case stages a child whose own stream reports a context the run's profile would end that child at, and
  the case asserts the children the phase started
- **THEN** the case runs under a profile it records itself, the reading it stages stands below the end that profile
  records, and its assertion prints the prompt of every child it saw
- **Case**: `run#childUsageIsRecordedPerItem`

#### Scenario: A child the case did not stage is named in its failure

- **WHEN** the run ends a child the case staged at one of its bounds and continues it in a child of its own, so the
  case sees one child more than it staged
- **THEN** the case fails with the prompt of every child it saw — `worker-prompt-continuation.md` among them — rather
  than with a count that names nothing
- **Case**: `run#childUsageIsRecordedPerItem`
