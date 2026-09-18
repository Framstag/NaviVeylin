## ADDED Requirements

### Requirement: Transliteration-consistent name matching

Search SHALL match a query against location names modulo transliteration *and* letter case, in both directions: an index name spelled with the sharp s (`ß`) SHALL match a query spelling the same word with `ss`, and an index name spelled with `ss` SHALL match a query spelling it with `ß`. Diacritic folding SHALL keep working as before (`ü`→`u`, `ö`→`o`, `ä`→`a`, and the accented-letter equivalents). A query SHALL NOT return an empty result set when its only difference from an existing index name is the sharp-s spelling or the letter case.

#### Scenario: ss-spelled query finds sharp-s street

- **WHEN** the user types a street name spelled with `ss` (e.g. "Erbstollenstrasse")
- **AND** the map's location index contains that street spelled with `ß` (e.g. "Erbstollenstraße")
- **THEN** the search results SHALL include that street
- **AND** the result set SHALL NOT be empty

#### Scenario: sharp-s query finds sharp-s street

- **WHEN** the user types a street name spelled with `ß` (e.g. "Erbstollenstraße")
- **AND** the map's location index contains that street spelled with `ß`
- **THEN** the search results SHALL include that street

#### Scenario: sharp-s query finds ss-spelled index name

- **WHEN** the user types a street name spelled with `ß`
- **AND** the map's location index contains that street spelled with `ss` (e.g. transliterated map data)
- **THEN** the search results SHALL include that street

#### Scenario: Diacritic folding unchanged

- **WHEN** the user types a name without its diacritics (e.g. "Gunnemannshof")
- **AND** the map's location index contains that name with diacritics (e.g. "Günnemannshof")
- **THEN** the search results SHALL include that name

#### Scenario: Match is case-insensitive

- **WHEN** the user types a name in a different letter case than the index name (e.g. "ERBSTOLLENSTRASSE" or "erbstollenstrasse")
- **THEN** the search results SHALL include the matching index entry

#### Scenario: Partial prefix still matches

- **WHEN** the user types a prefix of a sharp-s street name that contains no sharp s (e.g. "Erbstollen")
- **THEN** the search results SHALL include the street whose name starts with that prefix

#### Scenario: Fully qualified address with ss-spelled street resolves

- **WHEN** the user types a full address whose street is spelled with `ss` (e.g. "Erbstollenstrasse 10 58454 Witten")
- **THEN** the search results SHALL include the matching street or house-level entry
- **AND** the results SHALL NOT consist only of administrative-region or postal-area entries

#### Scenario: Sharp-s spelling does not degrade unrelated results

- **WHEN** the user types a query that matches entries without any sharp-s spelling
- **THEN** the result set SHALL be the same as before transliteration consistency was introduced
