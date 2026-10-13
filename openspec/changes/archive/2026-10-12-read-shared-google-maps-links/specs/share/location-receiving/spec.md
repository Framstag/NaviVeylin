# Spec Delta

## MODIFIED Requirements

### Requirement: Shared coordinate disambiguated through candidate picker
The system SHALL present the objects near a shared coordinate in the candidate picker so the user can choose what was meant.

#### Scenario: Candidates found at shared coordinate
- **WHEN** the shared input yields a coordinate and the map database contains objects near it
- **THEN** the candidate picker lists the objects at that coordinate, ranked by the map database

#### Scenario: Candidate selected
- **WHEN** the user selects a candidate from the picker
- **THEN** the details sheet opens for that object with Show / Fav / Route actions

#### Scenario: No candidates at shared coordinate
- **WHEN** the shared input yields a coordinate and the map database contains no objects near it
- **THEN** the details sheet opens directly for the raw coordinate, labeled with the share subject when present, otherwise with the coordinate pair

#### Scenario: Exactly one candidate opens the details sheet
- **WHEN** the shared coordinate yields exactly one candidate object
- **THEN** the details sheet opens for that object without showing the picker

#### Scenario: Shared place name ranks the candidate list
- **WHEN** the shared input yields a coordinate and a place name, and several candidate objects are found
- **THEN** the candidates whose label contains the place name are listed before the remaining candidates

### Requirement: Shared address triggers search
The system SHALL run the existing location search when the shared input contains no coordinate.

#### Scenario: Address text shared
- **WHEN** the shared input is an address or place name without coordinates
- **THEN** the app runs a location search for the text and shows the search results

#### Scenario: Unparseable shared text
- **WHEN** the shared input is free text that is neither a coordinate nor a map link
- **THEN** the app runs a location search for the raw text

## ADDED Requirements

### Requirement: Shared map link is read for its coordinate
The system SHALL read a recognized map link out of a shared intent and use the coordinate it carries, so a link containing a position never reaches the search field as text.

#### Scenario: Google Maps place link with data coordinates
- **WHEN** the shared link is `https://www.google.com/maps/place/Eiffel+Tower/@48.8584,2.2945,17z/data=!3m1!4b1!4m6!3m5!1s0x47e66e2964e34e2d:0x8ddca9ee380ef7e0!8m2!3d48.8583701!4d2.2944813`
- **THEN** the parsed destination carries latitude 48.8583701 and longitude 2.2944813 and the name `Eiffel Tower`

#### Scenario: Google Maps viewport link
- **WHEN** the shared link is `https://www.google.com/maps/@48.8584,2.2945,17z`
- **THEN** the parsed destination carries latitude 48.8584 and longitude 2.2945

#### Scenario: Coordinate priority favors the place pin
- **WHEN** the shared link carries both a place pin and a viewport center that differ
- **THEN** the place pin coordinate is the one used

#### Scenario: Query parameter link variants
- **WHEN** the shared link is a Google Maps URL carrying the destination in `q`, `ll`, `daddr` or `destination`
- **THEN** the parsed destination carries that coordinate

#### Scenario: Country host and legacy short path
- **WHEN** the shared link uses a `google.<country>` host or the `goo.gl/maps/...` path
- **THEN** the link is recognized and parsed like its `google.com` equivalent

#### Scenario: Tracking parameters do not disturb parsing
- **WHEN** the shared link carries tracking parameters such as `g_st`, `entry` or `g_ep`
- **THEN** the parsed destination is the same as without those parameters

#### Scenario: Link resolved through a short link
- **WHEN** the shared link is a `maps.app.goo.gl/...` short link that resolves to a place link
- **THEN** the parsed destination comes from the resolved target, not from the short link text

#### Scenario: Link embedded in surrounding share text
- **WHEN** the shared text is a place name followed by a map link
- **THEN** the link is parsed and the surrounding text is used as the place name, never as the search text

### Requirement: A map link with no coordinate falls back to its place name
The system SHALL run a location search for the place name a recognized map link carries when the link contains no coordinate.

#### Scenario: Place name from the link path
- **WHEN** the shared link carries a place name in its path and no coordinate
- **THEN** the app runs a location search for that place name and shows the search results

#### Scenario: Place name from the share subject
- **WHEN** the shared intent carries a place name in its subject and the link itself has no parseable content
- **THEN** the app runs a location search for that place name and shows the search results

### Requirement: An unreadable shared map link is reported, never searched
The system SHALL treat a recognized map link that yields neither a coordinate nor a place name as unusable and SHALL surface a non-blocking hint instead of running a search for the link text.

#### Scenario: Short link cannot be resolved
- **WHEN** the shared short link cannot be resolved and carries no coordinate or place name of its own
- **THEN** no search runs, the hint is shown, and the search field keeps its previous content

#### Scenario: Link carries only an opaque place identifier
- **WHEN** the shared link carries only a place identifier such as `ftid` or `cid` and neither coordinate nor name
- **THEN** no search runs and the hint is shown

#### Scenario: Unreadable link does not open the search panel
- **WHEN** an unreadable map link is shared while the map is shown
- **THEN** the search panel is not opened and the map viewport is unchanged
