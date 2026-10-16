# Spec Delta

## ADDED Requirements

### Requirement: Leaving Places mode abandons its search

When the phone search dialog is dismissed, or its mode changes away from Places, the dialog SHALL abandon the
places search running for its slot (spec: `search-request-lifecycle`), so no result arrives for a dialog that no
longer shows that query.

#### Scenario: Dismissing the dialog abandons the running search

- **WHEN** the user dismisses the open search dialog while a places search is running
- **THEN** that search SHALL be aborted at its next abort check
- **AND** no result of it SHALL be displayed

#### Scenario: Switching mode abandons the places search

- **WHEN** a places search is running and the user switches the dialog to POIs mode
- **THEN** the places search SHALL be aborted
- **AND** no places result of it SHALL be displayed in the POIs mode
