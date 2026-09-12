# Road Lookup Bearing (road-lookup-bearing)

## Purpose

Bearing-aware road lookup for free-driving and off-route display: given a position and the vehicle's heading, resolve the road the vehicle is actually driving on (name, ref, type, max speed) instead of the nearest address point or nearest way.

## Requirements

### Requirement: Road lookup uses vehicle bearing to disambiguate
The system SHALL resolve the road at a position by map matching that considers the vehicle's bearing: among the ways near the position, the way whose direction at its nearest point best matches the vehicle's heading SHALL be preferred over a nearer way with a mismatched direction. When no bearing is available, the nearest way SHALL be returned.

#### Scenario: Vehicle on main road near a side street
- **WHEN** the vehicle is on a main road and a side street branches off within the lookup radius
- **THEN** the lookup returns the main road (direction matches the vehicle bearing), not the side street

#### Scenario: Bearing matches the driven direction
- **WHEN** the vehicle bearing is valid and a way's direction at the nearest point is within 45 degrees of the bearing
- **THEN** that way is ranked above a way outside the threshold even if the latter is closer

#### Scenario: No bearing available
- **WHEN** the vehicle bearing is unknown (e.g. stationary)
- **THEN** the lookup returns the nearest way, matching the pre-change behavior

#### Scenario: No road found
- **WHEN** no way is found within the lookup radius
- **THEN** the lookup returns no road info

### Requirement: Road lookup returns name, ref, type, and max speed
The system SHALL return the resolved road's name, reference (ref tag), type, and maximum allowed speed from a single lookup, so the street label and the speed-limit sign always describe the same road.

#### Scenario: Road with all attributes
- **WHEN** the resolved road has a name, ref, type, and speed limit
- **THEN** the lookup returns all four values

#### Scenario: Road without a ref
- **WHEN** the resolved road has no ref tag
- **THEN** the lookup returns an empty ref and the remaining attributes

#### Scenario: Road without a speed limit
- **WHEN** the resolved road has no defined speed limit
- **THEN** the lookup returns an undefined max speed and the remaining attributes
