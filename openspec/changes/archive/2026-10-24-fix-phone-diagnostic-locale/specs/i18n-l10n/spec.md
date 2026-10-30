# Spec Delta — i18n-l10n

## ADDED Requirements

### Requirement: Log and diagnostic numbers are locale-independent

The numeric fields of a log or diagnostic line SHALL be formatted with a fixed locale, never the device
locale, so the same state prints the same line on every install. The phone formats them with `Locale.US`,
the Android Auto variant with `Locale.ROOT`; the car's own line is owned by `auto-diagnostics`.

#### Scenario: A phone line on a decimal-comma locale

- **WHEN** the device locale is German and the phone writes a `follow`, GPS-fix, auto-zoom or re-center line
- **THEN** every number in the line SHALL print with a `.` decimal separator
- **AND** the line SHALL be byte-identical to the one the same state writes on a dot locale
- **Case**: `DiagnosticLineLocaleTest.theGpsFixLineKeepsItsNumbersOnADecimalCommaLocale`
- **Case**: `DiagnosticLineLocaleTest.theFollowLineKeepsItsNumbersOnADecimalCommaLocale`
- **Case**: `MapCanvasViewModelBrowseReCenterTest.theReCenterEvidenceLineKeepsItsNumbersOnADecimalCommaLocale`

#### Scenario: A displayed number keeps the device locale

- **WHEN** the same German install displays a distance or a size
- **THEN** the displayed number SHALL keep its German decimal separator, because it is display text and not
  evidence
