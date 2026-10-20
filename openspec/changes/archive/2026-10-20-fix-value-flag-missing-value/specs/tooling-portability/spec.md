## ADDED Requirements

### Requirement: A value flag refuses a missing value before it consumes it

Every engine that parses a flag with a value SHALL check that the value is there before the arm reads and
consumes it, and SHALL refuse with a usage error naming the tool and the flag. The check SHALL also refuse a
next word that is itself a flag, so the refusal names the flag whose value is missing rather than the flag
that follows it. A parser SHALL never re-enter an iteration it has not consumed.

The self-test SHALL scan every engine for an arm that reads a value without that check, the self-test and
mutation harnesses included. Only the `*-cases.sh` files MAY be excluded, because they are harness.

No flag of this tooling accepts `--flag=value`: a value is the next argv word, and a free-text value that
begins with `--` is refused rather than passed.

#### Scenario: A bare value flag at the end of the arguments is a usage error

- **WHEN** an engine is given a value flag as its last argument and nothing after it
- **THEN** it exits 2 with `<tool>: <flag> needs a value` on stderr
- **Case**: `usage#bareValueFlagAtTheEndOfArgvIsAUsageError`, `create#bareValueFlagNeedsAValue`

#### Scenario: A value flag does not take the next flag as its value

- **WHEN** a value flag is followed by another flag
- **THEN** the first flag is reported as the one missing its value, rather than the second flag being read as
  that value and the error surfacing later as the second flag's own validation
- **Case**: `usage#bareValueFlagDoesNotSwallowTheNextFlag`, `create#bareValueFlagNeedsAValue`

#### Scenario: A single-shot harness refuses a bare flag instead of running its whole set

- **WHEN** a self-test or mutation harness that parses one flag is given `--case` or `--only` with nothing
  after it
- **THEN** it exits 2 naming the flag, and no case runs, no mutation is applied
- **Case**: `usage#bareValueFlagInASingleShotHarnessIsAUsageError`

#### Scenario: A value flag written without the check is a red case

- **WHEN** an engine's flag arm reads a value without checking it first
- **THEN** the tooling's own self-test reports it, so a new flag cannot miss the rule
- **Case**: `usage#noUnguardedValueFlagRemains`
