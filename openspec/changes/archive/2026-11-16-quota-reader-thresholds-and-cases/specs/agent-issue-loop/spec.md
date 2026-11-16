## ADDED Requirements

### Requirement: The quota reader's comparisons are its contract

The project's quota reader SHALL decide from the recorded balance alone — a per-window floor on the remaining
percentage, and a pacing line the weekly window's burned share may not pass before its own week's elapsed fraction
allows. Each comparison SHALL be exercised from a recorded balance, offline and without a token, so that no floor,
instant or window is proven only by the reader's reading of itself.

#### Scenario: A window under its floor holds the run

- **WHEN** the recorded balance reports a session or weekly remaining percentage below that window's floor
- **THEN** the reader answers hold, names that window and its remaining percentage in the reason, and offers the
  instant that window resets, plus the reader's own margin, as the one at which to ask again
- **Case**: `quota#aWindowUnderItsFloorHoldsUntilItResets`

#### Scenario: The pacing line is the week's elapsed share less the reserve

- **WHEN** the weekly window's burned percentage is compared with the elapsed fraction of that window's own week
- **THEN** the reader goes only while the burn is below `elapsed × (100 − reserve) / 100`, and holds naming the burn,
  the line, the elapsed share and the reserve when it is not
- **Case**: `quota#thePacingLineIsTheElapsedShareLessTheReserve`

#### Scenario: A hold on the line names when the line arrives

- **WHEN** the burned percentage is past the pacing line but below the cap the reserve leaves
- **THEN** the reader offers the instant at which the line reaches that burn — not the window's reset — so the run
  wakes once, when the comparison would pass
- **Case**: `quota#aHoldOnTheLineNamesWhenTheLineArrives`

#### Scenario: An instant the reader cannot parse is named, not dropped

- **WHEN** the balance carries a reset instant that is absent, or spelled other than the one shape the reader parses
- **THEN** the pacing comparison is never skipped in silence: the reader prints one line naming the instant it could
  not read on its exit-0 path — the path whose stdout the run relays as a note — and the floors are still compared;
  a hold it takes anyway keeps its own reason as the only line it prints ahead of `resume-at`, and the `--json`
  decision object carries no note
- **Case**: `quota#anInstantItCannotParseIsNamedNotDropped`

#### Scenario: A balance it cannot read is a go with a note

- **WHEN** the balance is absent, unreadable, or not the JSON the reader knows
- **THEN** the reader exits as a go and prints one line naming what it could not read, so the run relays it and is
  never held by a check that could not be made
- **Case**: `quota#aBalanceItCannotReadIsAGoWithANote`
