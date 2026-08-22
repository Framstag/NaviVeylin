# Nav Hints Layout — Delta

## MODIFIED Requirements

### Requirement: Navigation hints avoid on-map buttons
The NextTurnOverlay SHALL have its width constrained so it does not extend under the left action button column (menu, search, favorites) or the top-right view column (compass, location options, zoom controls).

#### Scenario: NextTurnOverlay width respects button column
- **WHEN** navigation is active and NextTurnOverlay is displayed
- **THEN** the overlay width SHALL be limited to leave the left action button column and the right view column fully visible and tappable

### Requirement: Navigation hints start right of the toaster button
The NextTurnOverlay SHALL be positioned immediately to the right of the top-left toaster (menu) button, with no gap between the button and the hint overlay.

#### Scenario: NextTurnOverlay starts right of toaster button
- **WHEN** navigation is active and NextTurnOverlay is displayed
- **THEN** the overlay SHALL start directly to the right of the top-left toaster button
- **AND** the overlay SHALL have no left-side gap beyond the toaster button width
