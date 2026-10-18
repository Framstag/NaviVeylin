# Spec Delta

## ADDED Requirements

### Requirement: A state transition replaces the item's state label

A call that sets an item's lifecycle state SHALL leave exactly one `status/*` label on that issue: it SHALL
remove every other value of that namespace in the same write that adds the new value, so a single call cannot
leave two states and a caller needs no second call to complete the transition.

#### Scenario: update#statusReplacesTheOldStatus

- **WHEN** a state write names an item that carries a different state
- **THEN** one issue write removes the old state label and adds the new one, and the item carries exactly one
  `status/*` label afterwards

#### Scenario: update#stateNamedAsALabelReplacesTheOldState

- **WHEN** a state is named as the namespaced label itself rather than through the state flag — the spelling a
  declared check's escalation uses — on an item that carries a different state
- **THEN** the same single write removes the state the item had and adds the new one, the other labels of the
  call are kept, and a call naming two states on that spelling is refused with a reason naming both

#### Scenario: update#statusReplacementIsIdempotent

- **WHEN** a state write names the state the item already carries as its only state
- **THEN** the item still carries exactly one `status/*` label, and the write does not both add and remove the
  same label

#### Scenario: update#statusRejectsTwoStatesInOneCall

- **WHEN** a call names the item's state twice with conflicting values, through the state flag and through a
  state-namespace label flag together
- **THEN** it is refused before any write, with a reason naming the two values, and no label on the item changes

### Requirement: A state transition reports what it replaced

A state write SHALL report the state labels it removed, including the ones it removed because they were
displaced by the new state, so a caller can see the whole transition and not only the flags it passed.

#### Scenario: update#statusReportNamesImpliedRemoval

- **WHEN** a state write displaces an existing state and the caller asks for machine-readable output
- **THEN** that output names the displaced state label among the removed labels
