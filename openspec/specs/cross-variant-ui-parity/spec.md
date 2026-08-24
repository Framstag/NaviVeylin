# Cross-variant UI parity (cross-variant-ui-parity)

## Purpose

Requires that similar user-facing elements (buttons, actions, labels, controls) in the phone and Android Auto variants are labeled and styled identically, so users get a consistent experience across both surfaces.

## Requirements

### Requirement: Cross-variant UI parity
Similar UI elements in the phone and Android Auto variants SHALL use the same labels and the same visual style wherever the platform constraints of both variants allow it. When a platform constraint (e.g. car-host template limitations) forces a difference, the element SHALL deviate only as much as required and SHALL keep the same label.

#### Scenario: Same action label in both variants
- **WHEN** an action (e.g. "Navigate to", "Add to Favorites", "Remove from Favorites", "Show") exists in both the phone and the Android Auto variant
- **THEN** the action SHALL have the same label in both variants

#### Scenario: Same visual hierarchy in both variants
- **WHEN** a set of actions has a primary/secondary hierarchy in one variant
- **THEN** the same hierarchy SHALL be reflected in the other variant's styling where the platform allows (e.g. primary emphasis for the main action)

#### Scenario: Platform-constrained deviation keeps label
- **WHEN** a platform constraint forces a different rendering of an element (e.g. list rows instead of buttons on the car display)
- **THEN** the element SHALL keep the same label
- **AND** the visual deviation SHALL be limited to what the constraint requires
