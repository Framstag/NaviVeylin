# gps-fix-quality — deltas

## MODIFIED Requirements

### Requirement: Re-evaluation stays off the main dispatcher while the quality is unchanged

The re-evaluation of the fix quality that runs while the location source is silent SHALL NOT dispatch
onto the main dispatcher as long as the derived quality equals the published one: neither the
re-evaluation itself nor the value it publishes SHALL reach the main dispatcher in that case. A derived
quality that differs from the published one SHALL still reach every consumer through the single
publication path, so the re-evaluation stays observable without a new fix.

#### Scenario: Unchanged quality causes no main-dispatcher dispatch

- **WHEN** the location source is silent
- **AND** the re-evaluated quality equals the published quality
- **THEN** the re-evaluation SHALL NOT dispatch anything onto the main dispatcher
- **AND** the published quality SHALL remain unchanged
- **AND** this SHALL hold for every re-evaluation of a bounded sequence of them, not only the first
- **Case**: `MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher`

#### Scenario: Unrelated main-dispatcher traffic is not attributed to the re-evaluation

- **WHEN** another subsystem of the process dispatches onto the main dispatcher from a background thread
  that is neither the test's nor the re-evaluation's, while the re-evaluation runs with an unchanged
  quality
- **THEN** that dispatch SHALL be attributed to that subsystem, as that subsystem's traffic, and SHALL
  NOT fail the re-evaluation's guarantee
- **AND** if that guarantee fails, a dispatch the case attributed to the re-evaluation SHALL be reported
  with the stack that issued it, alongside the per-window counts, so a misattribution is visible in the
  failure
- **Case**: `MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher`

#### Scenario: A change still reaches the consumers

- **WHEN** the re-evaluated quality differs from the published quality
- **THEN** the new quality SHALL become visible to consumers through the same publication path a new
  fix uses
- **AND** a change that the location source cannot report (an aged-out fix, a disabled location
  service) SHALL become visible without a further location update
- **Case**: `MapCanvasViewModelFixQualityTest.anAgedOutFixBecomesNoneWithoutANewFix`,
  `MapCanvasViewModelFixQualityTest.aDisabledSourceIsNoneWithoutANewFix`
