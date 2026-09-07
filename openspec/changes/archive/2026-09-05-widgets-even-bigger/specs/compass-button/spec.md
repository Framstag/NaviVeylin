## MODIFIED Requirements

### Requirement: Compass button matches overlay button sizing

The compass button SHALL be larger than the other map overlay buttons (menu, search, location options): 56dp layout / 48dp visual vs the 48dp layout / 40dp visual of the other buttons, so it reads at a glance while driving. It SHALL use the same shadow as those buttons. The follow-direction triangle needle SHALL be sized to about 70% of the button.

#### Scenario: Compass button same size as other overlay buttons

- **WHEN** the map screen is displayed
- **THEN** the compass button SHALL be 56dp layout / 48dp visual
- **AND** the other overlay buttons (menu, search, location options) SHALL remain 48dp layout / 40dp visual
- **AND** the compass button SHALL use the same shadow as the other overlay buttons
- **AND** the follow-direction triangle SHALL be sized to about 70% of the button
