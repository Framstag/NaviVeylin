# Spec Delta

## ADDED Requirements

### Requirement: Analysed step in the route summary component
The route summary component SHALL accept an analysed step index in addition to the navigation step index, and SHALL render the analysed step as selected. When both indices are present, the two markings SHALL be visually distinguishable from each other; when the analysed index is absent, no step SHALL be shown as selected.

#### Scenario: Analysed step marked in the list
- **WHEN** the route summary component is displayed with an analysed step index
- **THEN** the step at that index SHALL be shown as selected
- **AND** the other steps SHALL keep their normal appearance

#### Scenario: Analysed and navigation step are distinguishable
- **WHEN** the route summary component is displayed with both an analysed step index and a navigation step index
- **THEN** both steps SHALL be marked
- **AND** the analysed marking SHALL be distinguishable from the navigation marking without relying on colour alone

#### Scenario: No analysed index means no selection
- **WHEN** the route summary component is displayed without an analysed step index
- **THEN** no step SHALL be shown as selected
