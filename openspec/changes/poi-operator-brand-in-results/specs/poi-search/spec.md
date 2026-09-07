## MODIFIED Requirements

### Requirement: POI results list
The app SHALL display POI search results in a list showing the POI name, its
operator or brand when available, its object type, and its distance from the
search center, styled consistently with the app's other result lists. When a POI
has a name and an operator or brand, the entry SHALL show the name with the brand
(preferred) or operator in parentheses; when the POI has no name, the brand or
operator SHALL be shown alone; "(unnamed)" SHALL be shown only when the POI has no
name, operator, or brand.

#### Scenario: Results displayed
- **WHEN** a POI search returns entries
- **THEN** each entry is shown with its name, object type, and distance from the search center

#### Scenario: Name and brand shown
- **WHEN** a POI search returns an entry that has both a name and a brand
- **THEN** the entry shows the name with the brand in parentheses (e.g. "Tankstelle (Shell)")

#### Scenario: Name and operator shown without brand
- **WHEN** a POI search returns an entry that has a name and an operator but no brand
- **THEN** the entry shows the name with the operator in parentheses (e.g. "Filiale Mitte (Sparkasse)")

#### Scenario: Brand preferred over operator
- **WHEN** a POI search returns an entry that has a name, a brand, and an operator
- **THEN** the entry shows the name with the brand in parentheses, not the operator

#### Scenario: No name, brand or operator shown alone
- **WHEN** a POI search returns an entry that has no name but has a brand or operator
- **THEN** the entry shows the brand or operator as the primary text (e.g. "McDonald's")

#### Scenario: Name equals brand or operator
- **WHEN** a POI search returns an entry whose name equals its brand or operator
- **THEN** the entry shows the name once, without a duplicated parenthetical

#### Scenario: Completely unnamed entry
- **WHEN** a POI search returns an entry that has no name, operator, or brand
- **THEN** the entry shows "(unnamed)" as the primary text

#### Scenario: Empty results
- **WHEN** a POI search returns no entries
- **THEN** the app shows an empty-results state instead of a blank list

#### Scenario: Search failure
- **WHEN** a POI search fails
- **THEN** the app keeps the sheet usable, shows an error, and does not crash
