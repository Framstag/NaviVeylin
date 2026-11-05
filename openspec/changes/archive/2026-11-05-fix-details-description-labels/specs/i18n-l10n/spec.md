# Spec Delta — i18n-l10n

## ADDED Requirements

### Requirement: Native description labels are localized at the frontend

The section names, subsection names and label keys that the native description service publishes SHALL be rendered
from the string resources of the surface that shows the row, in the device language. The mapping SHALL be keyed on
the key's value — the only form that crosses the boundary — and the wording SHALL live in the surface's own module,
never in a shared one. Every mapped key SHALL carry a German translation, and a key the table does not carry SHALL
render as that key.

#### Scenario: A German device reads the German words

- **WHEN** the device locale is German and the details dialog shows a section header, a subsection header and a label
  row whose keys are `Contact`, `Lanes` and `MaxSpeed`
- **THEN** the rows SHALL read `Kontakt`, `Spuren` and `Höchstgeschwindigkeit:` and SHALL NOT read `Contact`,
  `Lanes` or `MaxSpeed:`
- **Case**: `LocationDetailsDialogComposeTest.germanDeviceTranslatesDescriptionSectionsAndLabels`

#### Scenario: An English device keeps the native words

- **WHEN** the device locale is English and the details dialog shows the `Contact` rows
- **THEN** the section header SHALL read `Contact` and the label rows `Phone:` and `Website:` — the English value of
  a mapped key is the key itself, so the English rendering is unchanged
- **Case**: `LocationDetailsDialogComposeTest.englishDeviceKeepsTheNativeDescriptionWords` (asserts the
  `Contact` header and the `Phone:` and `Website:` label rows under the default locale; a row's click behaviour is
  its own scenario's — `phoneValueOpensTheDialer` for a `tel:` value, `websiteValueStartsTheBrowser` for an
  `http`/`https` value — and is not claimed here)

#### Scenario: A key the table does not carry

- **WHEN** the native layer emits a description entry whose section or label key the surface has no resource for
- **THEN** the row SHALL render that key verbatim, the entry SHALL still be shown, and the dialog SHALL NOT show an
  empty label or fail
- **Case**: `LocationDetailsDialogComposeTest.anUnmappedDescriptionKeyKeepsTheNativeKey`

#### Scenario: Every mapped key has a German translation

- **WHEN** the German resource set is validated against the English default
- **THEN** every `description_label_*` key of `values/` SHALL exist in `values-de/`
- **Case**: `GermanTranslationCompletenessTest.everyEnglishStringHasGermanTranslation`
