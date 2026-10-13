# Spec Delta

## MODIFIED Requirements

### Requirement: Deep link parsed into a destination
The system SHALL parse a deep-link intent into either destination coordinates or a location query. A map short link IS resolved to its target before parsing, on the phone and in the car session alike.

#### Scenario: geo URI with coordinates
- **WHEN** the intent URI is `geo:48.8566,2.3522`
- **THEN** the parser yields latitude 48.8566, longitude 2.3522, no query

#### Scenario: geo URI with query
- **WHEN** the intent URI is `geo:0,0?q=48.8566,2.3522(Eiffel%20Tower)`
- **THEN** the parser yields the coordinate pair and the query label

#### Scenario: Google Maps URL with q parameter
- **WHEN** the intent URI is `https://maps.google.com/?q=48.8566,2.3522`
- **THEN** the parser yields the coordinate pair

#### Scenario: Google Maps place link with data coordinates
- **WHEN** the intent URI is a `https://www.google.com/maps/place/<name>/@<lat>,<lon>,<zoom>z/data=...!3d<lat>!4d<lon>` link
- **THEN** the parser yields the place pin coordinate pair and the place name as the query label

#### Scenario: Google Maps URL with address query
- **WHEN** the intent URI is `https://maps.google.com/?q=Eiffel+Tower`
- **THEN** the parser yields no coordinates and the query text

#### Scenario: Shared text with coordinates
- **WHEN** the share intent `EXTRA_TEXT` is `48.8566, 2.3522`
- **THEN** the parser yields the coordinate pair

#### Scenario: OSM URL with mlat/mlon parameters
- **WHEN** the intent URI is `https://www.openstreetmap.org/?mlat=48.8566&mlon=2.3522`
- **THEN** the parser yields the coordinate pair

#### Scenario: OSM URL with map hash segment
- **WHEN** the intent URI is `https://www.openstreetmap.org/#map=16/48.8566/2.3522`
- **THEN** the parser yields the coordinate pair

#### Scenario: Apple or Waze URL with ll parameter
- **WHEN** the intent URI is `https://maps.apple.com/?ll=48.8566,2.3522` or `https://waze.com/ul?ll=48.8566,2.3522`
- **THEN** the parser yields the coordinate pair

#### Scenario: DMS coordinate text
- **WHEN** the share intent `EXTRA_TEXT` is `48°51'23.8"N 2°21'8.0"E`
- **THEN** the parser yields the coordinate pair

#### Scenario: Hemisphere coordinate text
- **WHEN** the share intent `EXTRA_TEXT` is `48.8566N 2.3522E`
- **THEN** the parser yields the coordinate pair

#### Scenario: Short link resolves to a map target
- **WHEN** the intent URI is a `https://maps.app.goo.gl/...` short link that resolves to a map URL
- **THEN** the parser yields the destination parsed from the resolved target, and the car session starts navigation to that coordinate

#### Scenario: Short link does not resolve
- **WHEN** the intent URI is a `https://maps.app.goo.gl/...` short link that cannot be resolved and carries no coordinate or place name of its own
- **THEN** the parser yields no destination, the raw link text is not offered as a query, and the session surfaces an error instead of geocoding the link

#### Scenario: Unparseable input
- **WHEN** the intent URI or text cannot be parsed
- **THEN** the parser yields no destination and the session surfaces an error

### Requirement: Deep-link activity forwards to phone app
The system SHALL forward the deep-link intent from `DeepLinkActivity` to `MainActivity`, and the phone surface SHALL consume it: a shared coordinate opens the candidate/details flow, a shared address runs search.

#### Scenario: Phone app opened by deep link
- **WHEN** `DeepLinkActivity` receives a deep link
- **THEN** it starts `MainActivity` with the original intent data and finishes itself

#### Scenario: Share subject survives the forward
- **WHEN** the received deep link carries a place name in its subject extra
- **THEN** the forwarded intent carries that subject and the phone surface uses it as the place-name hint for a shared coordinate

#### Scenario: Phone surface consumes a shared coordinate
- **WHEN** `MainActivity` receives a forwarded intent that yields coordinates
- **THEN** the phone map shows the candidate picker for that coordinate and opens the details flow on selection

#### Scenario: Phone surface consumes a shared address
- **WHEN** `MainActivity` receives a forwarded intent that yields a query without coordinates
- **THEN** the phone app runs a location search for the query

#### Scenario: Phone surface receives an unreadable map link
- **WHEN** `MainActivity` receives a forwarded map link that yields neither coordinates nor a place name
- **THEN** the phone app shows the hint and does not run a search for the link text
