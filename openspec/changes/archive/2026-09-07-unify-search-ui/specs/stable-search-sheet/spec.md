# stable-search-sheet Delta

## REMOVED Requirements

### Requirement: Stable sheet height during search lifecycle

The search bottom sheet SHALL maintain a fixed minimum height from the moment it opens until it is dismissed. The sheet height SHALL NOT change in response to:
- Search query text changes
- Search result list appearing, updating, or disappearing
- Loading spinner appearing or disappearing
- "No results found" text appearing or disappearing

**Reason**: The search surface is now a full-screen Material 3 search dialog (see `search-dialog`); there is no bottom sheet whose height could change.

**Migration**: No user action needed — the full-screen dialog replaces the sheet; the fixed-height guarantee is obsolete.

#### Scenario: Open search — stable height

- **WHEN** the user opens the search panel
- **THEN** the sheet opens to its minimum height immediately
- **AND** the height does not change as results load

#### Scenario: Type query — no resize

- **WHEN** the user types a search query
- **AND** results arrive asynchronously
- **THEN** the sheet height remains constant
- **AND** results appear within the pre-allocated space

#### Scenario: Clear query — no shrink

- **WHEN** the user clears the search query
- **AND** results disappear
- **THEN** the sheet height remains constant
- **AND** does not shrink when results are removed

### Requirement: Content scroll within stable bounds

When the search result list exceeds the available space within the stable sheet height, the list SHALL scroll internally. The sheet itself SHALL NOT expand to fit the full list.

**Reason**: Superseded by the full-screen search dialog — there is no sheet to expand.

**Migration**: No user action needed.

#### Scenario: Many results — internal scroll

- **WHEN** the search returns many results
- **AND** the result list exceeds the available sheet space
- **THEN** the list scrolls internally
- **AND** the sheet does not expand

### Requirement: Minimum height accommodates initial UX

The minimum sheet height SHALL be sufficient to show the search input field plus at least 2-3 result items (or the loading/empty state placeholder) without clipping.

**Reason**: Superseded by the full-screen search dialog — the dialog occupies the full screen, so no minimum height is needed.

**Migration**: No user action needed.

#### Scenario: Minimum height shows input + results

- **WHEN** the search panel opens
- **THEN** the search input field SHALL be visible
- **AND** at least 2-3 result items (or placeholder) SHALL be visible without scrolling
