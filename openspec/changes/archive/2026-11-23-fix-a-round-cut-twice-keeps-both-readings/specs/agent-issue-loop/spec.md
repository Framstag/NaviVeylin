## ADDED Requirements

### Requirement: The two readings of a round the run cut twice are kept

When a review round is ended at a bound, restarted, and the restart is ended at a bound as well so the item is
recorded `cut`, the run SHALL keep both readings' `BLOCKING:` and `NOTE:` lines in one file beside the item's
reports, each under the name of the report its whole stands in and bounded by `LOOP_REVIEW_INPUT_MAX_BYTES`; the
item's note SHALL name that file, which the item's one `resume #<item>` line prints. Neither reading writing a
finding SHALL write no file and add no clause.

#### Scenario: The two readings of a round the run cut twice are kept

- **WHEN** a review round is ended at a bound, the run restarts it, and the restart is ended at a bound as well, so
  the item is recorded `cut`
- **THEN** one file beside the item's reports carries the `BLOCKING:`/`NOTE:` lines of the first reading and of the
  restart, each under the name of the report its whole stands in and quoted to `LOOP_REVIEW_INPUT_MAX_BYTES` with the
  `[LOOP-CUT: …]` marker; the item's note names that file, so the report's one `resume #<item>` line carries it; and
  neither reading writing a finding writes no file and adds no clause
- **Case**: `run#aRoundCutTwiceKeepsBothReadingsFindings`
