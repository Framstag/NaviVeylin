## ADDED Requirements

### Requirement: A documented bound states the value the profile records

A guideline section that states a bound the project's profile records SHALL state the value that profile records, beside the measurement that set it, and SHALL name the key it belongs to; a value already stated in the owning section is not restated elsewhere. A check SHALL compare every such value with the profile and report it per role, and SHALL need no build, no device and no network.

#### Scenario: The table and the profile agree

- **WHEN** the check reads the profile's `LOOP_CHILD_TURNS_MAX_<ROLE>` keys and the section's table of them
- **THEN** every role's stated value equals the profile's, and the check exits 0 naming how many roles it compared
- **Case**: `values#proseMatchesTheProfile`

#### Scenario: A value the profile moved is reported

- **WHEN** the profile records a value the section's table does not state
- **THEN** the check exits non-zero and prints `LOOP-CHILD-TURNS-MAX: <role> prose <n> config <n>`, naming both
  figures
- **Case**: `values#aDriftedValueIsReported`

#### Scenario: A role the section omits is reported

- **WHEN** the profile records a `LOOP_CHILD_TURNS_MAX_<ROLE>` key the section's table has no row for
- **THEN** the check exits non-zero and names that role with `prose -` and the figure the profile records
- **Case**: `values#aRoleWithNoProseRow`
