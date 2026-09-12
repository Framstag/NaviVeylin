# i18n-l10n Specification

## Purpose

Makes every user-facing string in NaviVeylin translatable through the Android resource system, with English as the default locale and German fully supported, so the phone and Android Auto variants render in the device language with locale-correct number and unit formatting.

## Requirements

### Requirement: All user-facing text is translatable
Every user-facing string in the `:app` and `:auto` modules SHALL be defined in Android string resources (`res/values/strings.xml`), never hardcoded in Kotlin. This includes button labels, titles, hints, placeholders, content descriptions, dialog text, menu entries, template titles, and error messages. Logcat-only debug output is exempt.

#### Scenario: Phone UI shows no hardcoded text
- **WHEN** the app is built with the `HardcodedText` lint check enabled
- **THEN** the build SHALL fail if any user-facing string literal appears in Kotlin UI code

#### Scenario: Auto UI shows no hardcoded text
- **WHEN** the `:auto` module is built with the `HardcodedText` lint check enabled
- **THEN** the build SHALL fail if any user-facing string literal appears in Car App Library screen or template code

### Requirement: English is the default locale
The default `res/values/strings.xml` SHALL contain the complete English string set. When the device locale has no matching resource qualifier, the app SHALL display English.

#### Scenario: Unsupported device locale falls back to English
- **WHEN** the device locale is a language without a resource qualifier (e.g. Japanese)
- **THEN** all UI text SHALL render in English

#### Scenario: English device locale
- **WHEN** the device locale is English
- **THEN** all UI text SHALL render in English

### Requirement: German is fully supported
A `res/values-de/` resource set SHALL exist in both `:app` and `:auto` with a complete German translation of every user-facing string. German SHALL be used when the device locale is German.

#### Scenario: German device locale
- **WHEN** the device locale is German (de or de-AT, de-CH, de-DE)
- **THEN** all UI text in both the phone app and the Android Auto variant SHALL render in German

#### Scenario: German translation completeness
- **WHEN** the German resource set is validated against the English default
- **THEN** every string key present in `values/strings.xml` SHALL also exist in `values-de/strings.xml` (no missing translations, no untranslated placeholders)

### Requirement: Locale-aware number formatting
User-facing numeric values (distances, speeds, sizes, coordinates) SHALL be formatted with the device locale, producing locale-correct decimal separators (e.g. "1,5 km" in German, "1.5 km" in English). No user-facing formatting SHALL use `Locale.ROOT` or a fixed locale.

#### Scenario: German decimal separator
- **WHEN** the device locale is German and a distance of 1.5 km is displayed
- **THEN** the text SHALL read "1,5 km"

#### Scenario: English decimal separator
- **WHEN** the device locale is English and a distance of 1.5 km is displayed
- **THEN** the text SHALL read "1.5 km"

### Requirement: Units are localized resources
Unit suffixes ("km", "m", "MB", "m away") SHALL be defined as string resources so future locales can switch unit systems (e.g. miles). German SHALL use the same metric units as English.

#### Scenario: Unit string is a resource
- **WHEN** a distance or size is displayed
- **THEN** the unit suffix SHALL come from a string resource, not a Kotlin literal

### Requirement: Count-dependent strings use plurals
Strings whose form depends on a count (e.g. "1 result" vs "N results") SHALL use Android plural resources with correct forms for English and German.

#### Scenario: Singular count
- **WHEN** exactly one search result is displayed
- **THEN** the text SHALL use the singular form (English "1 result", German "1 Treffer")

#### Scenario: Plural count
- **WHEN** more than one search result is displayed
- **THEN** the text SHALL use the plural form (English "N results", German "N Treffer")

### Requirement: Parameterized strings use format arguments
Strings with dynamic values SHALL use positional format arguments (`%1$s`, `%1$d`) so translators can reorder words for the target language.

#### Scenario: Parameterized string renders correctly
- **WHEN** a string with a format argument is displayed (e.g. search radius)
- **THEN** the argument SHALL be substituted at the position defined by the resource

### Requirement: POI category names are localized at the frontend
POI category names returned by libosmscout (hardcoded English in native code) SHALL be mapped to localized resource strings in the frontend. The native layer SHALL remain unchanged.

#### Scenario: German POI category
- **WHEN** the device locale is German and a POI category (e.g. fuel) is displayed
- **THEN** the category name SHALL render in German (e.g. "Tankstelle"), not the native English name

#### Scenario: Unknown category identifier
- **WHEN** the native layer returns a category identifier with no localized mapping
- **THEN** the app SHALL fall back to the English resource string for that category

### Requirement: Native-generated navigation text is localized at the frontend
Turn-by-turn instruction text produced by the native JNI bridge (hardcoded English in `OSMScoutClient.cpp`, e.g. "Turn left", "Exit 3", "Enter motorway", "Keep left") SHALL be rebuilt from the structured instruction fields (`turnType`, `streetName`) into localized resource strings in the frontend. The native layer SHALL remain unchanged. When a turn type or native text pattern has no localized mapping, the app SHALL fall back to the native English text.

#### Scenario: German turn instruction on Android Auto
- **WHEN** the device locale is German and the next instruction is a left turn
- **THEN** the Android Auto host instruction panel SHALL show the German cue (e.g. "Links abbiegen"), not "Turn left"

#### Scenario: German turn instruction on phone
- **WHEN** the device locale is German and the next instruction is a left turn
- **THEN** the phone next-turn overlay SHALL show the German instruction (e.g. "Links abbiegen"), not "Turn left"

#### Scenario: German roundabout exit
- **WHEN** the device locale is German and the next instruction leaves a roundabout at exit 3
- **THEN** the instruction SHALL render the exit count in German (e.g. "Ausfahrt 3"), not "Exit 3"

#### Scenario: German motorway maneuver
- **WHEN** the device locale is German and the next instruction is a motorway change or leave
- **THEN** the instruction SHALL render in German (e.g. "Links halten", "Autobahn verlassen"), not "Keep left" / "Leave motorway"

#### Scenario: Unknown turn type falls back to native text
- **WHEN** the native layer returns a turn type or text pattern with no localized mapping
- **THEN** the app SHALL display the native English text rather than an empty or wrong string

#### Scenario: English device locale
- **WHEN** the device locale is English
- **THEN** turn instructions SHALL render in English (unchanged behavior)

### Requirement: Phone and Auto label parity
The phone app and the Android Auto variant SHALL use the same string resources for shared concepts (menu entries, actions, titles), so labels match across variants in every supported locale.

#### Scenario: Shared label parity
- **WHEN** the device locale is German and the same action exists in both the phone app and the Auto variant (e.g. "Favorites")
- **THEN** both variants SHALL display the identical German label

### Requirement: Diagnostics UI text is translated
User-facing diagnostics and session-log UI (dialog titles, buttons, empty states) SHALL be translated like any other UI text. Logcat-only debug messages SHALL remain untranslated.

#### Scenario: German diagnostics dialog
- **WHEN** the device locale is German and the diagnostics dialog is opened
- **THEN** the dialog title, buttons, and empty-state text SHALL render in German

### Requirement: RTL readiness
The app SHALL keep `android:supportsRtl="true"` and SHALL not introduce layout assumptions that break under right-to-left locales, so future RTL languages (e.g. Arabic) can be added without layout rework.

#### Scenario: RTL flag present
- **WHEN** the merged manifest is inspected
- **THEN** `android:supportsRtl` SHALL be `true`
