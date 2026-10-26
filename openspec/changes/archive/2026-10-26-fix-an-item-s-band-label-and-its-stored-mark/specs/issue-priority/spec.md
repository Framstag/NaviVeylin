## ADDED Requirements

### Requirement: A band label is written only with the body that carries its marker

A write that names a `prio/<band>` label SHALL carry, in the same call, the body whose marker records that same band — the label added must equal the marker's band and the label removed must not — and SHALL be refused before any write when it does not. A created item SHALL carry no band label at all, since the body a filing writes holds no marker.

#### Scenario: A band label named with no body is refused

- **WHEN** `update.sh` is asked to add or remove a `prio/<band>` label without a `--body`
- **THEN** nothing reaches the tracker and the refusal names `band-label-unpaired`
- **Case**: `update#refusesABandLabelWithoutItsMarker`

#### Scenario: The pair written in one call lands

- **WHEN** a call adds a band label and passes a body whose marker records the same band, removing the band it replaces
- **THEN** one `gh issue edit` carries the added label, the removed one and the body — and a body whose marker records another band is refused with `band-label-disagrees`, writing nothing
- **Case**: `update#writesABandLabelWithTheBodysMarker`

#### Scenario: A filing names no band

- **WHEN** `create.sh` is asked to file an item carrying a `prio/<band>` label
- **THEN** nothing is filed and the refusal names `band-label-unpaired`, because the body a filing writes carries no marker for the band to agree with
- **Case**: `create#refusesABandLabelAtFilingTime`
