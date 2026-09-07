## Why

NaviVeylin is English-only. Most user-facing text is hardcoded as Kotlin string literals in the `:app` Compose UI (~175 literals across 56 files) and the `:auto` Car App Library screens (~77 template strings, no `res/` directory at all). The single `res/values/strings.xml` (~60 entries) is English-only and partially used. There is no German support, no locale-aware number formatting (inconsistent `Locale.ROOT` vs default-locale formatting), and no lint gate preventing hardcoded-text regressions. The app targets German-speaking users (German is the project author's language) and Android's standard resource-based i18n/l10n infrastructure is the expected, low-cost path.

## What Changes

- Externalize **all** user-facing strings to the Android resource system:
  - `app/src/main/res/values/strings.xml` — English, the default/fallback locale
  - `app/src/main/res/values-de/strings.xml` — German translation (new)
  - `auto/src/main/res/values/strings.xml` — English for the `:auto` library module (new; library resources merge into the app at build time)
  - `auto/src/main/res/values-de/strings.xml` — German (new)
- Compose UI reads strings via `stringResource(R.string.x)` / `pluralStringResource`; non-composable Kotlin via `context.getString(R.string.x)`; Car App Library screens via `carContext.getString(R.string.x)` (resolves against merged app resources).
- Count-dependent strings use Android plurals (`plurals.xml`), e.g. "1 result" / "N results" (German: "1 Treffer" / "N Treffer").
- Parameterized strings use format args (`%1$s`, `%1$d`), following the existing `poi_search_radius` pattern.
- **Locale-aware number formatting**: user-facing distances/speeds/sizes format with the device locale (German comma decimals, e.g. "1,5 km"). Replaces the current inconsistent mix of `Locale.ROOT` (`DistanceFormat.formatDistanceKm`) and default-locale (`NavigationArrowRenderer`, `NavigationHintsOverlay`, `MapManagerScreen`) formatting.
- Units ("km", "m", "MB", "m away") become resources so future languages can switch units (e.g. "mi").
- **Lint gate**: enable Android Lint `HardcodedText` check (error severity) in `:app` and `:auto` so new hardcoded user-facing strings fail the build.
- **POI category names**: libosmscout returns hardcoded English category names; the frontend maps category identifiers to localized resource strings instead of displaying native names verbatim.
- Diagnostics/session-log UI text is user-facing and translated; logcat-only debug strings stay untranslated.
- `guidelines/UI.md` gains an i18n/l10n section documenting the conventions (resource-based strings, locale-aware formatting, lint gate, parity between phone and Auto variants).
- `android:supportsRtl="true"` already present; German is LTR so no RTL layout work in this change, but the guideline records the RTL requirement for future locales.

## Capabilities

### New Capabilities
- `i18n-l10n`: horizontal requirement — every user-facing string in the app is translatable via the Android resource system, English is the default locale, German is fully supported, number/unit formatting is locale-aware, and hardcoded user-facing text is rejected by lint. Applies equally to the phone (`:app`) and Android Auto (`:auto`) variants.

### Modified Capabilities
- None. Existing specs reference English labels ("About", "Search POIs", "Cancel", …) as UI-element identifiers in WHEN/THEN scenarios, not as language requirements. The `i18n-l10n` capability supersedes the literal English wording of those labels; the elements themselves are unchanged.

## Impact

- **Resources**: `app/src/main/res/values/strings.xml` (extended, ~60 → ~300 entries), `app/src/main/res/values-de/strings.xml` (new), `auto/src/main/res/values/strings.xml` (new), `auto/src/main/res/values-de/strings.xml` (new), optional `plurals.xml` in both modules.
- **Kotlin — `:app`**: ~56 files under `app/src/main/java/com/naviveylin/ui/` (map, route, navigation, mapmanager, favorites, addressbook, about, attribution, search) plus `util/DistanceFormat.kt` and `data/` where user-facing strings are produced.
- **Kotlin — `:auto`**: ~30 files under `auto/src/main/java/com/naviveylin/auto/` (screens, template mappers, overlays, diagnostics).
- **Build**: `app/build.gradle.kts` and `auto/build.gradle.kts` (lint configuration).
- **Guidelines**: `guidelines/UI.md` (new i18n/l10n section).
- **Native boundary**: POI category identifier → localized label mapping in the frontend; no libosmscout submodule changes (native stays Android-free per CI gate).
- **Tests**: unit tests for locale-aware formatting (German/English), resource completeness checks, existing Compose/Robolectric tests updated where they assert English literals.
- **Additive, non-breaking.** Rollback: revert resource extraction; English remains the fallback so a partial migration degrades gracefully.
