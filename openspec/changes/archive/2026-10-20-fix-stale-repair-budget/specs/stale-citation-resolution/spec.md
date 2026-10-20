# stale-citation-resolution — deltas

## MODIFIED Requirements

### Requirement: The resolver runs on its own, bounded and idempotent

The resolver SHALL run without a person, in the checks' own pipeline under that pipeline's least privilege,
SHALL be bounded per run, SHALL default to a dry run, and SHALL be idempotent. The bound SHALL be spent only
on the items the run acts on — a repair or a park, and in a dry run the items it would act on — so an item
the ladder can only report cannot consume the run's budget, and SHALL never hide the items it did not reach:
the run SHALL report how many wait. The count of unresolved citations SHALL be a declared check row, so a
growing residue is a measurement.

#### Scenario: A scheduled run repairs without a person

- **WHEN** the pipeline runs on its schedule
- **THEN** the citations it can repair are repaired, and the items are eligible again
- **Case**: `pipeline#scheduledRunRepairs`

#### Scenario: The bound holds

- **WHEN** more items are due than the run's bound allows
- **THEN** the run repairs the first ones, reports how many wait, and the next run continues
- **Case**: `resolver#boundHoldsAndReportsTheRest`

#### Scenario: The bound is spent where the tracker changes

- **WHEN** an item's only stale citation is ambiguous, so the ladder can report it but write nothing for it
- **THEN** the item costs no slot of the run's bound, the items behind it in tracker order are still reached,
  and the ambiguity is still counted and still writes nothing
- **Case**: `resolver#ambiguityDoesNotConsumeTheBound`

#### Scenario: A second run changes nothing

- **WHEN** the resolver runs again over items it already processed
- **THEN** no citation is rewritten, no comment is posted, and the run reports an empty working set
- **Case**: `resolver#secondRunChangesNothing`

#### Scenario: The residue is a declared check

- **WHEN** citations remain unresolved
- **THEN** a registry row reports their count as its measurement, so the pile cannot grow unobserved
- **Case**: `pipeline#residueIsADeclaredRow`
