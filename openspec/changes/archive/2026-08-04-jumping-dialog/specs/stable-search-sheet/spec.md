# Stable Search Sheet

## Requirements

### R1: Stable sheet height during search lifecycle
The search bottom sheet SHALL maintain a fixed minimum height from the moment it opens until it is dismissed. The sheet height SHALL NOT change in response to:
- Search query text changes
- Search result list appearing, updating, or disappearing
- Loading spinner appearing or disappearing
- "No results found" text appearing or disappearing

### R2: Content scroll within stable bounds
When the search result list exceeds the available space within the stable sheet height, the list SHALL scroll internally. The sheet itself SHALL NOT expand to fit the full list.

### R3: Minimum height accommodates initial UX
The minimum sheet height SHALL be sufficient to show the search input field plus at least 2-3 result items (or the loading/empty state placeholder) without clipping.

## Scenarios

### S1: Open search — stable height
**WHEN** the user opens the search panel
**THEN** the sheet opens to its minimum height immediately
**AND** the height does not change as results load

### S2: Type query — no resize
**WHEN** the user types a search query
**AND** results arrive asynchronously
**THEN** the sheet height remains constant
**AND** results appear within the pre-allocated space

### S3: Clear query — no shrink
**WHEN** the user clears the search query
**AND** results disappear
**THEN** the sheet height remains constant
**AND** does not shrink when results are removed

### S4: Many results — internal scroll
**WHEN** the search returns many results
**AND** the result list exceeds the available sheet space
**THEN** the list scrolls internally
**AND** the sheet does not expand
