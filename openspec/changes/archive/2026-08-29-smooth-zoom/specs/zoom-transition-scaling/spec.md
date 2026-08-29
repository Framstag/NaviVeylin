# zoom-transition-scaling

## MODIFIED Requirements

### Requirement: Native render replaces placeholder at exact target magnification

When the zoom gesture ends and the debounce expires, the system SHALL trigger a native render at the target magnification.

- The placeholder SHALL remain visible until the native render completes.
- On render completion, the front buffer SHALL swap atomically and the display SHALL blend from the currently displayed placeholder state into the freshly rendered frame such that no single-frame jump in map content is visible. If a zoom animation (smooth-zoom) is in progress when the render completes, the rendered frame SHALL replace the animated placeholder immediately at the exact target magnification.

#### Scenario: Render completes after zoom out

- **WHEN** the user finishes a pinch zoom out
- **THEN** the system debounces for 200 ms
- **THEN** a native render is queued at the target magnification
- **WHEN** the render completes
- **THEN** the display transitions from the placeholder to the rendered tiles without a single-frame jump in map content

#### Scenario: Render completes while zoom animation runs

- **WHEN** the native render at the target magnification completes while a zoom animation is scaling the placeholder
- **THEN** the rendered frame SHALL be displayed immediately at the exact target magnification
- **AND** the animation SHALL stop scaling the front buffer at that point