# Delta — auto/preferences

## ADDED Requirements

### Requirement: Dark mode preference applies to car rendering

When the driver sets the dark mode preference (**On**, **Off**, **Automatic**) on the car, the car's map and navigation presentation SHALL follow it: **On** renders dark presentation, **Off** renders light presentation, and **Automatic** follows the host day/night signal. A preference change or a host day/night change SHALL re-render the visible map without the driver leaving the screen or restarting the app.

#### Scenario: On forces dark

- **WHEN** the driver sets the dark mode preference to **On** and the host day/night signal is light
- **THEN** the car map and navigation screens render dark presentation

#### Scenario: Off forces light

- **WHEN** the driver sets the dark mode preference to **Off** and the host day/night signal is dark
- **THEN** the car map and navigation screens render light presentation

#### Scenario: Automatic follows host

- **WHEN** the driver sets the dark mode preference to **Automatic**
- **THEN** the car map and navigation screens follow the host day/night signal

#### Scenario: Preference change applies live

- **WHEN** the driver changes the dark mode preference on the car and returns to the map
- **THEN** the map renders the new presentation without restart

#### Scenario: Host night change applies live

- **WHEN** the dark mode preference is **Automatic** and the host day/night signal changes while the map or navigation screen is visible
- **THEN** the map re-renders the new presentation without user interaction
