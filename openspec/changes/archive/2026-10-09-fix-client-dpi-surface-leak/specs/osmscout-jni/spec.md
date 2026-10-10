# Spec Delta

## ADDED Requirements

### Requirement: Render entry points take the projection DPI

The JNI render entry points SHALL accept the physical DPI to project with as an explicit parameter of the render request.

- Every render entry point that produces map pixels SHALL accept the DPI parameter
- The render SHALL project with the passed value, not with a value stored in client settings
- A render request with an unusable DPI (zero or negative) SHALL NOT produce a frame; the failure SHALL surface to the caller like any other render failure

#### Scenario: Render projects with the passed DPI

- **WHEN** Java issues a render request carrying DPI X
- **THEN** the produced pixels are projected at X
- **THEN** the geographic extent covered by the pixel buffer corresponds to X

#### Scenario: Two renders with different DPI in one client

- **WHEN** two render requests with different DPI values are issued against the same client instance
- **THEN** each frame is projected with its own request's value, independent of the order in which they are issued

### Requirement: No client-wide render DPI setter

The JNI bridge SHALL expose no API that changes the projection DPI used by renders, and no render SHALL depend on a previously configured client-wide value.

- A client-wide DPI setter SHALL NOT be part of the Java bridge API
- Calls that configure other client-wide native state (style sheet, tile data cache capacity) SHALL NOT imply or alter the projection DPI

#### Scenario: No bridge method changes the projection DPI

- **WHEN** the Java bridge API is inspected by a caller
- **THEN** no method changes a client-wide render DPI
- **THEN** omitting any such call leaves every render correctly projected, because each request carries its own DPI

#### Scenario: Other client-wide configuration does not affect the projection DPI

- **WHEN** the style sheet or the native tile data cache capacity is configured on the client
- **THEN** subsequent renders are still projected with the DPI of their own request
