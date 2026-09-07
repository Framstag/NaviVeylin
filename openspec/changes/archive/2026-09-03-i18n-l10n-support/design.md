## Context

See proposal.md — Why. Current state that shapes the approach:

- `:app` has `res/values/strings.xml` (~60 entries, English only) with 44 `R.string` references; ~175 user-facing literals remain hardcoded across 56 Kotlin files.
- `:auto` has **no** `res/` directory; ~77 template strings hardcoded; zero `R.string` references. It is a library module (`com.android.library`), so its resources merge into the app at build time.
- Number formatting is inconsistent: `util/DistanceFormat.formatDistanceKm` uses `Locale.ROOT` ("0.5 km"), while `NavigationArrowRenderer` (app), `NavigationHintsOverlay` (auto), and `MapManagerScreen` use default-locale `"%.1f km".format(...)` ("0,5 km" in German). Three separate distance-formatting implementations exist.
- POI category localization already exists in the phone (`PoiSearchPanel.categoryLabelRes` maps native category IDs → `R.string.poi_category_*`); the auto module duplicates this as a hardcoded `CATEGORY_LABELS` map.
- `android:supportsRtl="true"` already set. `:core` module has no user-facing strings.
- No lint configuration exists in either module's `build.gradle.kts`.

## Goals / Non-Goals

**Goals:**
- One canonical string-extraction pattern per UI framework (Compose `stringResource`, Car App Library `carContext.getString`) that future screens follow.
- Locale-aware formatting centralized in one helper, replacing the three divergent implementations.
- Lint gate that fails the build on new hardcoded user-facing text.
- German as a complete, verified translation set.

**Non-Goals:**
- No new locales beyond German (RTL layout work deferred; only the `supportsRtl` flag and layout-hygiene requirement are recorded).
- No changes to libosmscout native code (category names stay native-side; mapping happens in the frontend).
- No in-app language switcher (device locale is the single source of truth — standard Android behavior).
- No translation of logcat-only debug strings, file names, or the app name "NaviVeylin" (proper noun, `translatable="false"`).

## Decisions

### D1: `:auto` strings live in the library module's own `res/`
`auto/src/main/res/values/strings.xml` + `values-de/` are created; screens resolve via `carContext.getString(R.string.x)` against the merged app resources.

- **Alternative considered**: put all strings in `:app` and reference them from `:auto`. Rejected — couples the modules, breaks `:auto`'s standalone testability, and is not the standard library-module pattern.
- **Alternative considered**: a shared resource module. Rejected — overkill for one consumer; library res merging is the platform-standard mechanism.

### D2: Locale-aware formatting via a shared, context-free number helper + resource units
Split formatting into two layers so it stays testable without Android context:

1. `formatDistance(meters, locale)` — pure function, `Locale.getDefault()`-aware, returns the numeric part only ("1,5" / "1.5"). Replaces `DistanceFormat.formatDistanceKm` (ROOT) and the inline `"%.1f km".format(...)` in `NavigationArrowRenderer`, `NavigationHintsOverlay`, `MapManagerScreen`, `PoiResultsScreen` ("m away").
2. Unit suffix from string resources (`distance_unit_km`, `distance_unit_m`, `size_unit_mb`, …) appended at the call site.

- **Alternative considered**: keep `Locale.ROOT` everywhere for consistency with upstream `LocationSearchRanker`. Rejected — user decision: German users expect comma decimals; the upstream ranker's ROOT output is a search-ranking detail, not a display contract.
- **Alternative considered**: inline `String.format(Locale.getDefault(), ...)` at each call site. Rejected — that is exactly the current duplication; one helper prevents drift.
- **Note**: `String.format` with default locale is locale-aware for decimal separators; the helper pins the locale explicitly so behavior is deterministic and unit-testable.

### D3: POI category mapping stays per-module, resource-based
Phone keeps `categoryLabelRes(id)` (already correct). Auto's `CATEGORY_LABELS` map becomes a `categoryLabelRes(id)`-style mapping to `R.string.poi_category_*` in `auto/src/main/res/values/strings.xml`, with the same fallback-to-English behavior for unknown identifiers.

- **Alternative considered**: share one mapping via a new resource-bearing module. Rejected — the mapping is a stable `when` over ~14 known IDs; duplication is small and each module already owns its `R` class. A shared module adds build complexity for no behavioral gain.

### D4: Lint gate via `HardcodedText` (error) in both modules
`app/build.gradle.kts` and `auto/build.gradle.kts` get `lint { disable += ... }`-style config enabling `HardcodedText` at error severity (AGP: `lint { checkReleaseBuilds = true; abortOnError = true }` with the check enabled).

- **Alternative considered**: rely on code review. Rejected — the change is exactly about preventing regressions; an automated gate is the point.
- **Risk note**: lint's `HardcodedText` coverage of Compose `Text("...")` literals must be verified during implementation (it covers XML layouts definitively; Compose coverage depends on lint version). If Compose literals are not flagged, add a small custom lint check or a grep-based CI gate as fallback. Verification is an explicit task.

### D5: Plurals via `plurals.xml` in both modules
Count-dependent strings ("N results", "N favorites", "N contacts") use `plurals.xml` with `one`/`other` forms for English and German (both languages use two forms; German: "1 Treffer" / "N Treffer").

- **Alternative considered**: single string with `%1$d` and no plural forms. Rejected — grammatically wrong for both languages at count 1.

### D6: Diagnostics UI text translated; logcat-only strings exempt
`AboutDialog`/`DiagnosticsDialog` (app) and `DiagnosticsScreen` (auto) user-facing strings go through resources. Logcat-only messages (TAGs, debug lines, `Log.d` payloads) stay as literals — they are not user-facing and lint's `HardcodedText` does not flag them (they are not in UI text positions).

### D7: Guideline update in `guidelines/UI.md`
New "Internationalisation / Localisation" section: resource-based strings mandatory, `stringResource`/`carContext.getString` patterns, locale-aware formatting helper, plurals, lint gate, phone/Auto label parity, RTL hygiene. Per config rules, the guideline changes in the same change as the specs.

## Risks / Trade-offs

- [Lint `HardcodedText` may not flag all Compose literals] → Verify during implementation (explicit task); fallback to custom lint check or CI grep gate.
- [German translations may overflow car template constraints (head-unit width limits)] → Keep translations concise; verify on emulator/head unit (config rules require on-device verification for Auto changes).
- [String-key churn breaks existing tests asserting English literals] → Update affected tests in the same change; run full suite before marking complete.
- [Robolectric tests need locale control for German formatting assertions] → Use `@Config(qualifiers = "de")`; the pure formatting helper is also directly unit-testable with explicit locales.
- [Large mechanical change risks missed literals] → Lint gate + resource-completeness test (every key in `values/` exists in `values-de/`) close the loop.
- [Translation quality] → Project author is a German speaker; native review of `values-de/` before archive.

## Migration Plan

1. **App strings**: extract all `:app` literals into `values/strings.xml` (English), add `values-de/strings.xml` (German), convert call sites to `stringResource`/`getString`. Largest chunk; done module-by-module (map → route → navigation → mapmanager → favorites/addressbook → about/attribution).
2. **Auto strings**: create `auto/src/main/res/values/` + `values-de/`, convert ~30 files to `carContext.getString`.
3. **Formatting**: add shared locale-aware helper; migrate the three distance implementations + size/coordinate formatting; units to resources.
4. **Plurals**: introduce `plurals.xml` where counts are displayed.
5. **Lint gate**: enable `HardcodedText` in both modules; fix stragglers the gate surfaces.
6. **Guideline**: add i18n/l10n section to `guidelines/UI.md`.
7. **Tests**: formatting unit tests (de/en), resource-completeness test, update existing tests asserting English literals, Robolectric locale tests.
8. **Verify**: full build (all ABIs, both flavors), full test suite, on-device German check (phone + Auto emulator).

**Rollback**: additive, non-breaking. English is the fallback locale, so a partial migration degrades to English, never to missing text. Reverting the resource extraction restores prior behavior.

## Open Questions

None blocking. Deferred: whether future locales (e.g. French) should be added in the same change — no, they are separate follow-up changes once the infrastructure and German set are in place.
