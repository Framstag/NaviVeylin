# auto-search delta

## ADDED Requirements

### Requirement: Full formatted address resolution on car screen
When the driver types a full formatted address (street with house number, postal code, and city in one query, e.g. "Erbstollenstraße 10, 58454 Witten") in the car search template, the system SHALL resolve it using the same structured search used on the phone: the postal code inside the query SHALL NOT cause an empty result, and structured street/address matches SHALL rank above free-text matches. Free-text POI matches SHALL remain available for queries that do not tokenize like an address.

#### Scenario: Full address with postal code resolves on car screen
- **WHEN** the driver types "Erbstollenstraße 10, 58454 Witten" in the car search template
- **AND** the map's location index contains house number 10 on that street
- **THEN** the results SHALL contain the house-level entry for the address
- **AND** it SHALL be ranked above any street-, region-, or free-text result for the same query

#### Scenario: Postal code inside query does not block
- **WHEN** the driver types an address with the postal code between the house number and the city (e.g. "Erbstollenstraße 10 58454 Witten")
- **THEN** the search SHALL return the matching structured result(s)
- **AND** the result SHALL NOT be empty solely because of the extra postal-code tokens

#### Scenario: Free-text noise does not outrank structured match
- **WHEN** the driver types a query that matches a structured street or address
- **AND** free-text matches exist for the same query
- **THEN** the structured street/address match SHALL appear above the free-text matches

#### Scenario: Free-text still available for non-address queries
- **WHEN** the driver types a query that does not tokenize like an address (e.g. "cafe central")
- **THEN** free-text POI matches SHALL continue to be returned as before
