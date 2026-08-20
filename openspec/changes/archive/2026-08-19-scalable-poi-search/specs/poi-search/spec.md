## MODIFIED Requirements

### Requirement: Category and radius selection
The POI search sheet SHALL let the user pick one POI category from the supported set using a searchable dropdown and choose a search radius, then trigger a search around the current map center. The dropdown SHALL accommodate any number of supported categories and SHALL let the user filter the category list by typing.

#### Scenario: Search with selected category and radius
- **WHEN** the user selects a category and a radius and triggers the search
- **THEN** the app searches for POIs of that category within the chosen radius around the current map center

#### Scenario: Changing category or radius before searching
- **WHEN** the user changes the selected category or radius before triggering a search
- **THEN** the previous result list is not reused for the new selection; a new search is required

#### Scenario: Dropdown lists all supported categories
- **WHEN** the user opens the category dropdown without typing a filter
- **THEN** the dropdown lists every supported category

#### Scenario: Category chosen from searchable dropdown
- **WHEN** the user opens the category dropdown, types text that matches a category, and selects it
- **THEN** that category becomes the selected category

#### Scenario: Typing filters the category list
- **WHEN** the user types text into the category filter field
- **THEN** the dropdown shows only categories whose names match the typed text

#### Scenario: No category matches the filter
- **WHEN** the user types text that matches no category
- **THEN** the dropdown shows no selectable categories and no category is selected

#### Scenario: Selecting the chosen category clears it
- **WHEN** the user selects the category that is already selected
- **THEN** the selection is cleared and the search trigger is disabled
