## ADDED Requirements

### Requirement: Result distance display
Each search result entry SHALL display the straight-line (haversine) distance from the current map center to the result location, in kilometers, right-aligned in a smaller font than the entry's primary label text.

#### Scenario: Distance shown for each result
- **WHEN** search results are displayed in the result list
- **THEN** each entry SHALL show the distance from the current map center to the result location in kilometers
- **AND** the distance SHALL be right-aligned within the entry
- **AND** the distance SHALL be rendered in a smaller font than the entry's primary label text

#### Scenario: Distance shown in route panel search
- **WHEN** search results are displayed in the route panel start or destination search
- **THEN** each entry SHALL show the distance from the current map center, formatted and positioned identically to the map search panel

#### Scenario: Distance formatting
- **WHEN** a result is less than 10 km from the map center
- **THEN** the distance SHALL be shown with one decimal place (e.g. "0.5 km")
- **AND** the unit "km" SHALL be included in the displayed value
- **AND** when a result is 10 km or farther, the distance SHALL be shown as whole kilometers (e.g. "12 km")

#### Scenario: Distance follows current map center
- **WHEN** the map is panned or recentered before or during a search
- **THEN** the displayed distances SHALL be computed against the current map center at display time
