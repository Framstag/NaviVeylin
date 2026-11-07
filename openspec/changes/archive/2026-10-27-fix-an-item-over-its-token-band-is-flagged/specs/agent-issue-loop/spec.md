## ADDED Requirements

### Requirement: A report warns when an item outspends a recorded band

The report printed an item's tokens with nothing to read them against, so a run whose per-item spend doubled read exactly like a healthy one (issue #662). It SHALL read each item against a recorded band, and that band SHALL only ever warn: no part of the run reads it, so no outcome is derived from it.

#### Scenario: An item above the bound is flagged, and one below it is not

- **WHEN** an item's recorded `metrics.tokens` — the sum of its input, output, cache read and cache write — is above `LOOP_ITEM_TOKENS_WARN`, and another item's is below it
- **THEN** the row above the bound carries how far above it is in a column of its own (`2.5x`), the run-level line names that item beside the bound it crossed, the row below prints `-` there and is not named, and both items keep the outcome their work earned — the flag is a warning, so a bound that is `0` prints the flag as off and flags nothing
- **Case**: `report#anItemOverTheTokenBoundIsFlagged`
