# Spec Delta — shaping-inbox

## MODIFIED Requirements

### Requirement: A shaping verdict carries the shaping label

The run SHALL write the project's shaping label, in the same call as the hold and the one line accounting for it,
for every verdict its profile names as a shaping verdict — the answers "this is not a falsifiable acceptance
condition", "this exceeds one change" and "this is not spec-shaped". A verdict the profile names as a shaping
verdict is one the run holds rather than escalates, so a verdict that states a question is never one: `needs-diagnosis` is escalated
with the decision label, writes no shaping label, and the labels that skip leaves are the set the profile names for
it. A verdict the profile does not name SHALL write no such label.

#### Scenario: A shaping verdict writes the label beside the hold

- **WHEN** the run skips an item with a verdict the profile's `roles.shapingVerdicts` names
- **THEN** the item carries the hold state **and** the shaping label, and the comment written in the same call states
  the verdict and the clause of evidence that produced it
- **Case**: `run#aShapingVerdictWritesTheLabel`

#### Scenario: A verdict the profile does not name writes nothing

- **WHEN** the run skips an item with a verdict outside `roles.shapingVerdicts`
- **THEN** the item keeps the bare hold it writes today, and no shaping label appears
- **Case**: `run#aVerdictOutsideTheListWritesNoLabel`

#### Scenario: The labels a skip leaves are the set the profile names

- **WHEN** the run skips an item with `needs-diagnosis`, which this project's `roles.shapingVerdicts` does not name
- **THEN** the write that records the skip adds exactly the labels the profile's list implies for that verdict —
  the decision label and the hold, and a shaping label only if the list names it — so the labels the item carries and
  the declaration are one answer
- **Case**: `run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames`

#### Scenario: A project that names no verdict declares the list empty

- **WHEN** the profile declares `roles.shapingVerdicts` as an empty list
- **THEN** no run writes the label, and the queue verb reports an empty queue rather than an error
- **Case**: `shaping#anEmptyListIsAnEmptyQueue`
