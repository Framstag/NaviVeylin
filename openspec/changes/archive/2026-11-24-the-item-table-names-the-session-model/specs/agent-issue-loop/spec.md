## ADDED Requirements

### Requirement: The per-item table names the model that spent the item

The report's per-item row SHALL name the model its worker ran in, read from the state's `metrics.roleModels`, and
SHALL print `-` for an item whose state records none.

#### Scenario: A row names its worker's model

- **WHEN** an item's `metrics.roleModels.worker` carries two models
- **THEN** the row names the one with the most tokens and the count of the rest, and the header names the column
- **Case**: `report#theItemTableNamesTheSessionModel`

#### Scenario: A state that records no model prints a dash

- **WHEN** the item carries no `roleModels` for its worker
- **THEN** the row prints `-`, which is the report's spelling for a figure it did not measure
- **Case**: `report#aStateWithNoModelPrintsADash`
