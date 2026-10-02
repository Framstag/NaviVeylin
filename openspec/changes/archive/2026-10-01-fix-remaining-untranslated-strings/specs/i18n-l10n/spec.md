# Spec Delta — i18n-l10n

## MODIFIED Requirements

### Requirement: All user-facing text is translatable
Every user-facing string in every first-party module that renders user-facing text — including the `:app`, `:auto` and `:core` modules — SHALL be defined in Android string resources (`res/values/strings.xml`), never hardcoded in Kotlin. This includes button labels, titles, hints, placeholders, content descriptions, dialog text, menu entries, template titles, screen titles, notification-channel names, error messages, and count text. A shared module that composes user-facing text SHALL NOT own the wording: it SHALL take the display text (or a string resource reference) from its caller, so each surface supplies its own localized resource. Logcat-only debug output is exempt.

The build SHALL enforce this over every such module's Kotlin UI sources, not only over `:app`: `HardcodedText` lint for the XML and `TextView` paths, and a source scan that covers Compose, template builders and plain factory functions. A string whose text is assembled from values SHALL NOT be exempt from the scan when it also carries display words; only a string that consists solely of substituted values (e.g. a bare `"$zoomLevel"` or a separator-joined pair) is exempt.

#### Scenario: Phone UI shows no hardcoded text
- **WHEN** the `:app` module is built
- **THEN** the build SHALL fail if any user-facing string literal appears in its Kotlin UI code

#### Scenario: Auto UI shows no hardcoded text
- **WHEN** the `:auto` module is built
- **THEN** the build SHALL fail if any user-facing string literal appears in its Car App Library screen or template code

#### Scenario: Shared module owns no wording
- **WHEN** the `:core` module is built
- **THEN** the build SHALL fail if a user-facing string literal (e.g. a generic title) appears in its Kotlin code without being supplied by the caller

#### Scenario: Interpolated display text is not exempt
- **WHEN** a user-facing string is assembled from a literal carrying display words plus a substituted value (e.g. `"$count favorite"`)
- **THEN** the build SHALL fail, and the value SHALL be rendered through a plural resource instead

#### Scenario: Notification channel name is translatable
- **WHEN** the device locale is German and the notification settings list the app's channels
- **THEN** every channel name and description SHALL render in German

### Requirement: German is fully supported
A `res/values-de/` resource set SHALL exist in the `:app`, `:auto` and `:core` modules with a complete German translation of every user-facing string and plural. German SHALL be used when the device locale is German.

#### Scenario: German device locale
- **WHEN** the device locale is German (de or de-AT, de-CH, de-DE)
- **THEN** all UI text in both the phone app and the Android Auto variant SHALL render in German

#### Scenario: German translation completeness
- **WHEN** the German resource set is validated against the English default
- **THEN** every string and plural key present in `values/` SHALL also exist in `values-de/` (no missing translations, no untranslated placeholders)

#### Scenario: German car favorites titles
- **WHEN** the device locale is German and the car favorites screen is open
- **THEN** the screen title and the starred-favorites screen title SHALL render in German, matching the phone's labels for the same concepts

### Requirement: Count-dependent strings use plurals
Strings whose form depends on a count (e.g. "1 result" vs "N results") SHALL use Android plural resources with correct forms for English and German. The count SHALL be passed to the resource as a format argument, so the number is formatted by the resource rather than concatenated in Kotlin.

#### Scenario: Singular count
- **WHEN** exactly one search result is displayed
- **THEN** the text SHALL use the singular form (English "1 result", German "1 Treffer")

#### Scenario: Plural count
- **WHEN** more than one search result is displayed
- **THEN** the text SHALL use the plural form (English "N results", German "N Treffer")

#### Scenario: Favorites group card shows a localized count
- **WHEN** the device locale is German and the favorites sheet shows a group card for a group holding 3 favorites
- **THEN** the card SHALL show the German plural form for that count (e.g. "3 Favoriten") and SHALL NOT show an English-shaped plural

#### Scenario: Counted text needs no plural in English only
- **WHEN** a count-dependent string is added
- **THEN** its English `one`/`other` forms SHALL exist in `values/` and its German forms in `values-de/`

### Requirement: Phone and Auto label parity
The phone app and the Android Auto variant SHALL use the same string resources for shared concepts (menu entries, actions, titles), so labels match across variants in every supported locale. A wording used by both surfaces SHALL have exactly one resource home; a surface SHALL NOT keep a Kotlin copy of wording another surface already ships as a resource.

#### Scenario: Shared label parity
- **WHEN** the device locale is German and the same action exists in both the phone app and the Auto variant (e.g. "Favorites")
- **THEN** both variants SHALL display the identical German label

#### Scenario: Shared wording has one home
- **WHEN** the phone and the car display the same concept text (e.g. a neutral navigation title)
- **THEN** both SHALL resolve it from the same resource, with no duplicated Kotlin literal on either surface
