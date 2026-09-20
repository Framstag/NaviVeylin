# Spec Delta

## ADDED Requirements

### Requirement: Free-driving anchor applies when the setting changes during a session
The free-driving follow-mode map SHALL re-frame at the free-driving anchor when the setting changes during an active session: changing the anchor SHALL NOT require a screen restart, and the street-name label placement SHALL follow the new anchor.

#### Scenario: Anchor change applies during session
- **WHEN** the driver changes the free-driving anchor in the settings dialog
- **AND** returns to the free-driving map while the session is still active
- **THEN** follow mode re-frames the map at the new anchor promptly, without restarting the screen

#### Scenario: Street label follows the anchor
- **WHEN** the free-driving anchor changes during an active session
- **AND** follow mode is active
- **THEN** the street-name label is placed for the new anchor position

### Requirement: Free-driving anchor survives a settings re-read failure
When a settings re-read fails on re-visibility, the free-driving session SHALL keep the previously applied anchor and SHALL apply a newer value on the next successful re-read.

#### Scenario: Re-read failure keeps the last anchor
- **WHEN** the free-driving screen re-reads the settings on re-visibility and the read fails
- **THEN** the free-driving map keeps using the anchor from before the failed re-read
- **AND** the failure is logged and does not disrupt driving
