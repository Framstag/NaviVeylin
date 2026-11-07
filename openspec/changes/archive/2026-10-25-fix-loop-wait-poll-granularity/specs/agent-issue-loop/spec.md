## ADDED Requirements

### Requirement: A wait ends when the child does, not on the next whole second

A run SHALL look for a child's completion record at a fraction of a second (`LOOP_POLL_SECONDS`, default
`0.05`) and SHALL derive the progress cadence and the elapsed time it prints from seconds lived, never from the
number of looks, so a child that answers at once costs the wait its granularity rather than a whole second and
`LOOP_PROGRESS_SECONDS` keeps its meaning. The child's cap and the run's deadline SHALL stay whole-second clocks.

#### Scenario: A child that answers at once does not cost the wait a whole second

- **WHEN** a child writes its completion record 150 ms after it starts
- **THEN** the run announces the child's end less than 900 ms after announcing its start, because the wait looks
  several times inside that second instead of sleeping through it
- **Case**: `run#theWaitEndsWhenTheChildDoes`

#### Scenario: The progress line keeps its configured interval

- **WHEN** a child that runs for 4 s is watched with `LOOP_PROGRESS_SECONDS=2`
- **THEN** the run prints one `running` line per interval — between one and three of them — and not one per look,
  because the heartbeat reports elapsed seconds and not the wake-ups the finer granularity produces
- **Case**: `run#theProgressLineKeepsItsInterval`
