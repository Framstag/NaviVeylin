# JNI Sync JavaScout

## Purpose

Port the rendering fixes already made in JavaScout into the NaviVeylin JNI bridge and native render path so the app benefits from upstream marker-projection and zoom-scaling corrections.

## ADDED Requirements

### Requirement: JavaScout rendering fixes are identified and listed

The system SHALL maintain a list of JavaScout commits or patches that address the same rendering defects (marker drift, direction correction, zoom placeholder scaling).

- The list SHALL be recorded in `design.md` and referenced from tasks.
- Each entry SHALL include the upstream commit hash or issue link and a one-line description of the fix.

#### Scenario: Developer reviews upstream fixes

- **WHEN** a developer opens `design.md`
- **THEN** they can see which JavaScout commits relate to marker rendering and zoom scaling

### Requirement: JNI bridge applies equivalent projection corrections

The system SHALL ensure the JNI wrapper and the native `libosmscout-client-java` code pass the same viewport state to the renderer that JavaScout uses.

- The JNI render method SHALL receive center, magnification, DPI, width, height, and rotation in the same order and units as JavaScout.
- The native renderer SHALL NOT apply a second rotation or scale compensation that differs from JavaScout.

#### Scenario: Same viewport state as JavaScout

- **WHEN** the app calls the JNI render method
- **THEN** the parameters SHALL match the JavaScout render call for an equivalent viewport
- **THEN** the resulting tile contents SHALL match JavaScout pixel-for-pixel for the same map database and style

### Requirement: Native marker and POI rendering matches JavaScout output

The system SHALL render synthetic nodes (including `_favorite`) and route markers using the same projection and ground resolution that JavaScout uses.

- Ground resolution computation SHALL match JavaScout.
- Symbol placement and label positioning SHALL match JavaScout for the same style and data.

#### Scenario: Favorite marker position matches JavaScout

- **GIVEN** a favorite at the same coordinates in JavaScout and NaviVeylin
- **WHEN** both render the same viewport
- **THEN** the `_favorite` marker SHALL appear at the same screen pixel in both


