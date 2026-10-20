## ADDED Requirements

### Requirement: A value flag refuses a missing value before it consumes it

Every engine that parses a flag with a value SHALL check that the value is there before the arm reads and
consumes it, and SHALL refuse with a usage error naming the tool and the flag. A parser SHALL never re-enter
an iteration it has not consumed: `shift 2` on a one-word tail fails, leaves `$#` unchanged and repeats the
same `$1` for ever, so the validation after the loop that would have refused the empty value is never
reached (issue #190).

#### Scenario: A bare value flag at the end of the arguments is a usage error

- **WHEN** an engine is given a value flag as its last argument and nothing after it
- **THEN** it exits 2 with `<tool>: <flag> needs a value` on stderr, and nothing else happens — no header, no
  state write, no child started
- **Case**: `usage#bareValueFlagAtTheEndOfArgvIsAUsageError`, `create#bareValueFlagNeedsAValue`

#### Scenario: A value flag does not take the next flag as its value

- **WHEN** a value flag is followed by another flag
- **THEN** the first flag is reported as the one missing its value, rather than the second flag being read as
  that value and the error surfacing later as the second flag's own validation
- **Case**: `usage#bareValueFlagDoesNotSwallowTheNextFlag`, `create#bareValueFlagNeedsAValue`

#### Scenario: A value flag written without the check is a red case

- **WHEN** an engine's flag arm consumes two words without checking the value first
- **THEN** the tooling's own self-test reports it, so a new flag cannot miss the rule
- **Case**: `usage#noUnguardedValueFlagRemains`
