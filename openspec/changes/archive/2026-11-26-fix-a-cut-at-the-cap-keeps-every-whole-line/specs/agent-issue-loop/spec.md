## MODIFIED Requirements

### Requirement: The two readings of a round the run cut twice are kept

When a review round is ended at a bound, restarted, and the restart is ended at a bound as well so the item is
recorded `cut`, the run SHALL keep both readings' `BLOCKING:` and `NOTE:` lines in one file beside the item's
reports, each under the name of the report its whole stands in and bounded by `LOOP_REVIEW_INPUT_MAX_BYTES`, the
quote cut at the line boundary the cap falls on: every finding line that ends inside the cap SHALL be kept whole, a
cap that falls inside a line SHALL drop only the fragment it left, and the `[LOOP-CUT: …]` marker SHALL name how many
of the report's finding lines the quote carries; the item's note SHALL name that file, which the item's one
`resume #<item>` line prints. The first of the two readings SHALL be the report the run held when it named the second,
handed in by the caller that named it, and SHALL NOT be derived from the second report's own name: a restart is named
`<round>-restart.md` whichever attempt it restarted, and a round whose answer is cut on its way to the file is asked
again before it is restarted, so a name cannot say which reading the second stood in place of. Neither reading writing
a finding SHALL write no file and add no clause, and a second reading whose replaced reading is the same report SHALL
write no file either.

#### Scenario: The two readings of a round the run cut twice are kept

- **WHEN** a review round is ended at a bound, the run restarts it, and the restart is ended at a bound as well, so
  the item is recorded `cut`
- **THEN** one file beside the item's reports carries the `BLOCKING:`/`NOTE:` lines of the first reading and of the
  restart, each under the name of the report its whole stands in and quoted to `LOOP_REVIEW_INPUT_MAX_BYTES` with the
  `[LOOP-CUT: …]` marker; the item's note names that file, so the report's one `resume #<item>` line carries it; and
  neither reading writing a finding writes no file and adds no clause
- **Case**: `run#aRoundCutTwiceKeepsBothReadingsFindings`

#### Scenario: A restart of a retried answer quotes the retried answer

- **WHEN** a round's answer is cut on its way to the file, the round is asked again as `<round>-retry.md`, that retry
  is ended at a bound, and the restart the run then pays for is ended at a bound as well
- **THEN** the file's first section quotes the retry's `BLOCKING:`/`NOTE:` line under `<round>-retry.md`, the
  restart's own reading stands in its section beside it, and the round's first attempt — which wrote nothing and which
  the restart was never handed — is not named as the reading the restart replaced
- **Case**: `run#aRestartQuotesTheRetriedReadingItReplaced`

#### Scenario: A retried restart quotes the restart it retried

- **WHEN** a round is ended at a bound and restarted, the restart's own answer is cut on its way to the file, the run
  asks once more as `<round>-restart-retry.md`, and that retry is ended at a bound as well
- **THEN** the file's first section quotes the restart's `BLOCKING:`/`NOTE:` line under `<round>-restart.md`, the
  retry's own reading stands in its section beside it, and the round's first attempt — a reading the retry was never
  handed — is not quoted in place of the restart's
- **Case**: `run#aRetriedRestartQuotesTheReadingItRetried`

#### Scenario: A findings quote over the cap keeps every whole line it ends

- **WHEN** one reading's `BLOCKING:`/`NOTE:` lines are longer than `LOOP_REVIEW_INPUT_MAX_BYTES`, so the quote is cut,
  and the cap falls on the newline that ends a finding line
- **THEN** every finding line that ends inside the cap is quoted whole — the line the cap ends included — the finding
  after it is the cut the `[LOOP-CUT: …]` marker accounts for, and the marker names how many of the report's finding
  lines the quote carries beside the report the whole of them stands in
- **Case**: `evidence#aCutAtTheCapKeepsEveryWholeLine`

#### Scenario: A cap inside a line hands no fragment

- **WHEN** one reading's findings are longer than `LOOP_REVIEW_INPUT_MAX_BYTES` and the cap falls inside a finding
  line
- **THEN** the quote carries the whole finding lines before the cap and no fragment of the line it cut, and the
  marker names that count beside the report the whole of them stands in
- **Case**: `evidence#aCutAtTheCapKeepsEveryWholeLine`
