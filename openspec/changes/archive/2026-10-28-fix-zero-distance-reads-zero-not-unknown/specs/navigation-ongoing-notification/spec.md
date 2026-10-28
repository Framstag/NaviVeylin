# navigation-ongoing-notification

## ADDED Requirements

### Requirement: A known zero distance is not an unknown distance

The notification formatter SHALL distinguish a known zero distance-to-turn from an unknown one: a live step
with `remainingMeters = 0.0` SHALL render `0 m`, while `null` or `NaN` SHALL render `--`. The phone
notification's stats line and the car hint's secondary text SHALL follow the same rule, because one formatter
produces both.

#### Scenario: Zero and unknown distances read differently on phone and car

- **WHEN** the next manoeuvre's remaining distance is `0.0` and the same session is shown in the phone shade and as a car hint
- **THEN** both surfaces SHALL render the known zero as `0 m` — the phone stats line reads `in 0 m` and the car hint's secondary text reads `0 m · <arrival>` — and neither SHALL render `--` for it
- **AND** a `null` or `NaN` distance SHALL still render `--`, because an unknown distance is a different fact from a known zero
- **Case**: `app: NavigationNotificationContentFormatterTest` "zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown"
