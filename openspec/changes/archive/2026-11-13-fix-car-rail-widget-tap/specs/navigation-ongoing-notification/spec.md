# Spec Delta — navigation-ongoing-notification

## ADDED Requirements

### Requirement: Return to the app from the car rail widget

While navigation runs and the app is not the car screen's visible app, tapping the turn hint in the car rail widget SHALL return the app to the car foreground, on projection and on Automotive OS alike. The car extension SHALL carry its own tap target — a `CarPendingIntent` to the app's `CarAppService` — independent of the phone tap target; the phone shade SHALL keep returning to the phone UI. Free driving publishes no hint and SHALL have no car tap target.

#### Scenario: Rail-widget tap returns the app on Android Auto (projection)

- **WHEN** navigation is active, the driver has switched to another car app on a projected head unit, and taps the rail-widget turn hint
- **THEN** the car screen returns to the app's car UI with guidance still running
- **AND** the phone activity is not what the car screen shows and no phone UI is brought forward as the tap result

#### Scenario: Rail-widget tap returns the app on Android Automotive OS

- **WHEN** navigation is active, the driver has switched to another car app on an Automotive OS head unit, and taps the rail-widget turn hint
- **THEN** the car screen returns to the app's car UI with guidance still running

#### Scenario: Phone tap keeps returning to the phone UI

- **WHEN** the user taps the same ongoing notification in the phone shade while the car surfaces are in use
- **THEN** the phone UI comes to the front
- **AND** the car tap target is unaffected by that tap

#### Scenario: Guidance continues across the return

- **WHEN** the driver returns to the app by tapping the rail-widget turn hint
- **THEN** the active navigation session continues without restarting or re-routing
- **AND** the notification keeps showing the current manoeuvre, so the rail widget content does not regress

#### Scenario: No car tap target without a car hint

- **WHEN** free driving is active (no car turn hint is published)
- **THEN** the notification carries no car tap target, and tapping the phone notification still returns to the phone UI
