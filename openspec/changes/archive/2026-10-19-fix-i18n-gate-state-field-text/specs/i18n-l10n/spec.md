# i18n-l10n — deltas

## ADDED Requirements

### Requirement: The scan covers display text assigned to a rendered field

The hardcoded-string scan SHALL treat as a text position, in every module it scans, any assignment whose
field or parameter name ends in a display word (`text`, `title`, `label`, `description`, `hint`,
`placeholder`, `message`, `error`) and the message argument of a report call (`reportError(`), so display
text that reaches a surface through a rendered state field or a report is a violation like a Compose text
position is. A field whose name ends in no display word SHALL NOT become a text position, and a position
that begins inside an already-open string literal SHALL NOT be treated as a text position. The scan is
line-based: a position and its literal SHALL share a line, so a sentence that begins on the next line of an
assignment or of a report call is outside the scan's coverage.

#### Scenario: A rendered state field is a text position

- **WHEN** a Kotlin source assigns a display sentence to a field whose name ends in a display word, in a
  module the scan covers (`error = "Could not open map database"`)
- **THEN** the scan reports the literal, so the build fails
- **Case**: `HardcodedStringScannerTest.stateFieldIsFlagged`

#### Scenario: A qualified rendered field is a text position

- **WHEN** the field name carries a qualifier (`snackbarMessage = "…"`, `poiSearchError = "…"`)
- **THEN** the scan reports the literal
- **Case**: `HardcodedStringScannerTest.prefixedStateFieldIsFlagged`

#### Scenario: A report call's message is a text position

- **WHEN** a Kotlin source passes a display sentence as the first argument of `reportError(`
- **THEN** the scan reports the literal
- **Case**: `HardcodedStringScannerTest.reportCallMessageIsFlagged`

#### Scenario: A position inside an open string literal is not a text position

- **WHEN** a display word appears inside an already-open string literal on the line (`" labelHint=" + …`,
  a diagnostics message rather than an assignment)
- **THEN** the scan reports nothing, because no position begins there
- **Case**: `HardcodedStringScannerTest.aPositionInsideALiteralIsNotAnAssignment`

#### Scenario: A log payload field is not a text position

- **WHEN** a literal is assigned to a field whose name is not a display word (`detail = "avail=512MB …"`,
  the diagnostics log's payload)
- **THEN** the scan reports nothing, because logcat-only wording is exempt
- **Case**: `HardcodedStringScannerTest.logPayloadFieldIsNotFlagged`
