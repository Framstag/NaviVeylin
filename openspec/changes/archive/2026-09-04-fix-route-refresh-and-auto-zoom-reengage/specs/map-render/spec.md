# map-render — Delta for fix-route-refresh-and-auto-zoom-reengage

## MODIFIED Requirements

### Requirement: Forced overlay renders are never dropped by the blit fast-path
The sub-region blit fast-path SHALL optimize tile pans only: when the blit covers the viewport shift it SHALL discard at most a pending non-forced render. A pending forced render (`forceFullRender=true` — route set/clear, favorites, search selection, stylesheet switch, epoch bump) SHALL survive a covering blit and execute after the debounce, even when the camera never moved.

#### Scenario: Route set with unmoved camera renders
- **WHEN** a route calculation succeeds and `MapRenderer.setRoute` submits a forced render without a camera movement, and a subsequent non-forced render request's sub-region blit covers the viewport
- **THEN** the pending forced render is retained
- **AND** the new route polyline is drawn on the next render pass without any user gesture

#### Scenario: Route cleared with unmoved camera renders
- **WHEN** `MapRenderer.clearRoute` submits a forced render and the next non-forced request's blit covers the viewport
- **THEN** the forced clear render executes
- **AND** the old route polyline disappears without any user gesture

#### Scenario: Ordinary pan keeps the blit optimization
- **WHEN** the user pans within the overrun margin (no pending forced render)
- **THEN** the sub-region blit still serves the frame
- **AND** no full native render is scheduled

#### Scenario: Favorites stylesheet update not starved
- **WHEN** a forced render is pending and repeated covering blits arrive (e.g. follow-mode GPS ticks)
- **THEN** the pending forced render still executes after its debounce
- **AND** repeated blits cannot starve overlay changes indefinitely

## ADDED Requirements

### Requirement: New route appears without user interaction
After a reroute, the replacement route SHALL appear on the map promptly once the calculation succeeds, without requiring a pan, zoom, or rotation gesture.

#### Scenario: Reroute success refreshes the map
- **WHEN** navigation is active, a reroute calculation succeeds, and the map camera has not moved
- **THEN** the new route polyline replaces the old one on screen without any user gesture
- **AND** turn instructions and the drawn route refer to the same route

#### Scenario: Reroute failure keeps the last route
- **WHEN** a reroute calculation fails while navigating
- **THEN** the previously drawn route remains on screen (unchanged, per `reroute-route-visibility`)
- **AND** the failure is surfaced (log + snackbar, per `reroute-route-visibility`)
