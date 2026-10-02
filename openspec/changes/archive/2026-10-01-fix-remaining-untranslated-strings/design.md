# Design — fix-remaining-untranslated-strings

## Context

See `proposal.md` — Why. What shapes the approach:

- **`:core` already ships resources and a localization seam.** `core/src/main/res/values/strings.xml` (+
  `values-de/`) holds `nav_hint_neutral`, the turn-instruction set and the map-style failure text, and
  `core/src/main/java/com/naviveylin/core/TurnInstructionLocalizer.kt:12-18` defines
  `fun interface StringResolver { get(resId, vararg args) }` plus `Context.stringResolver()`. The phone
  notification formatter already consumes it (`service/NavigationNotificationContent.kt:7,129,156`), so
  the wording in §30/§32 has a seam to move into — no new mechanism is needed.
- **The details resolver is a pure factory.** `DetailsResolver` has no `Context` and `DetailsResolverTest`
  is plain JUnit (no Robolectric); `resolve(input, nameHint)` composes title/address/area and is called
  from the phone (`ui/map/LocationDetailsSheet.kt:120-123`, inside a `remember`) and the car
  (`auto/DetailsScreen.kt:307,327,591`).
- **The car already ships the strings it ignores.** `auto/src/main/res/values/strings.xml` has
  `favorites` and `starred_favorites` (used by the app menu); `FavoritesScreen.kt:110` builds the screen
  titles from literals instead.
- **The plurals mechanism and its pitfall are established.** `app/src/main/res/values/plurals.xml` +
  `values-de/plurals.xml` exist, are rendered via `pluralStringResource` in
  `ui/mapmanager/MapManagerScreen.kt:352`, and `PluralFormatTest` pins that the count must be passed as a
  format argument (`pluralStringResource(id, count, count)`) or `%d` stays literal.
- **Parity tests exist but are uneven.** `app/src/test/java/com/naviveylin/i18n/GermanTranslationCompletenessTest.kt`
  checks strings *and* plurals; `core`'s and `auto`'s counterparts check strings only.
- **The gates that should have caught all of this.** `checkHardcodedStrings` is an inline Gradle task in
  `app/build.gradle.kts:330-369`, wired into `preBuild`: it scans **only** `app/src/main/java` and
  returns early on any literal containing `${`. `app/lint.xml` and `auto/lint.xml` elevate
  `HardcodedText` to error with `abortOnError`; there is no `core/lint.xml`. `buildSrc` is the project's
  home for pure build-time logic with its own tests (`CoordinateLogScanner`, 52 tests).

## Goals / Non-Goals

**Goals:**
- Every user-facing string in the four recorded occurrences resolves from a resource, on both surfaces.
- One gate that sees all three shapes (wrong module, interpolated display text, notification-channel
  name) and fails the build on them, with tests pinning the rule.
- German coverage for the new keys, enforced by the existing completeness tests.
- No change to text composition logic — only where the words come from.

**Non-Goals:**
- No spec/behaviour change beyond resource sourcing: the details title order, the group-card count, the
  notification content and the car favorites list behaviour stay as they are.
- No native work: no submodule patch, no `:osmscout-client-java` override, no CMake target. Native
  English text (POI categories, turn instructions) is already localized at the frontend.
- No RTL/localization work beyond the rule restated in `guidelines/UI.md`; no new locales.
- Not a repo-wide literal audit: the scanner is the arbiter, and findings beyond the four occurrences are
  fixed only when trivial (see D6), otherwise filed in `TODO.md`.
- Out of scope: the map-data findings (§89/§90/§91) and the remaining Kotlin deprecation warnings
  (§37/§44).

## Decisions

### D1 — The generic details title is a required `String` parameter on the shared resolver
`DetailsResolver.resolveTitle`/`resolve` take a `genericTitle: String` **with no default**, and each
surface passes its own resource (`stringResource(R.string.location_title_generic)` on the phone,
`carContext.getString(...)` in `:auto`).

- *Alternative A — `@StringRes Int` + `StringResolver` in `:core`.* More consistent with
  `MapStyleLoadReporter`, but it makes the shared module choose the resource id it does not own the
  wording for, and the phone path would resolve a string on every recomposition of the details sheet.
  Rejected: no gain over a `String` here, since both call sites already sit where a `Context`/composition
  is available.
- *Alternative B — inject `Context` into the resolver.* Rejected: couples a pure factory to Android and
  breaks the plain-JUnit `DetailsResolverTest` (the class is deliberately Context-free so both surfaces
  can share it).
- *Alternative C — let `:core` ship `R.string.location_title_generic`.* Rejected: it puts the default
  English word back into the shared module, which the `i18n-l10n` delta now forbids ("shared module owns
  no wording").
- Consequence: a required parameter turns every missed call site into a compile error (intended — no
  silent fallback to the old literal). The phone's `remember(entry, objectDescription, resolvedAddress)`
  key at `LocationDetailsSheet.kt:120` must include the generic title, or a locale change would keep the
  previously computed title.

### D2 — The group-card count is a plurals resource rendered through `pluralStringResource`
Add `<plurals name="favorite_count">` (`one`/`other`) to `app/src/main/res/values/plurals.xml` and
`values-de/plurals.xml`, and render it in `FavoritesSheet.kt:693` as
`pluralStringResource(R.plurals.favorite_count, favCount, favCount)` — the count passed explicitly as the
format argument, which is the pitfall `PluralFormatTest` documents. `GroupCard`'s `favCount: Int`
parameter stays.

- *Alternative — keep string concatenation with a German-only branch.* Rejected: a German plural rule
  cannot be expressed by a suffix, and it violates `i18n-l10n`'s plural requirement.
- *Alternative — build the string in the ViewModel via `getQuantityString`.* Rejected: moves display
  formatting out of the composable that has the resource, and would need a `Context` in the ViewModel.

### D3 — Car favorites titles use the resources the module already ships
`FavoritesScreen.kt:110` reads `R.string.favorites` / `R.string.starred_favorites` instead of literals
(the keys already exist in `auto/src/main/res/values/strings.xml` and `values-de/`). The unnamed-row
fallback `fav.name ?: "Favorite"` (`:152`, `:176`) becomes one new `:auto` string with its German form.

- *Alternative — host the shared "Favorites" wording in `:core` for phone/car parity.* Rejected here:
  both modules already have their own keys of that name, and `i18n-l10n`'s parity requirement is about
  identical labels per locale, not about one physical resource per concept. Deduplicating existing keys
  is a separate refactor with no user-visible effect.

### D4 — Notification wording moves through the existing `StringResolver`
`NavigationNotificationContentFormatter` already receives a `StringResolver`; the neutral and
free-driving titles (`NavigationNotificationContent.kt:72-73,176,201,214`) become resolver lookups. The
neutral title reuses `:core`'s existing `nav_hint_neutral` (the phone constant is a byte-identical copy
of it — §32's duplication), and the free-driving title gets a new resource with a German form. The
`"Offroad"` road fallback (`ui/navigation/NavigationStateOverlay.kt:250`) becomes a resource too.
`MapDownloadService`'s channel name (`:131`) is resolved from a new resource at the point the channel is
created.

- *Alternative — keep the constants and mark them `translatable="false"`.* Rejected: channel names and
  notification titles are user-visible text, which `i18n-l10n` covers explicitly.
- Note: a channel's name/description **are** updatable by re-calling `createNotificationChannel`, so an
  existing install picks up the German name on the next service start (verified on device — see the
  migration plan); no reinstall is needed and no channel is recreated.

### D5 — The gate becomes a tested `buildSrc` scanner, not an inline regex list
Move the rule into `buildSrc` as a pure scanner (precedent: `CoordinateLogScanner`) with unit tests, and
wire one `checkHardcodedStrings` task into the `preBuild` of **`:app`, `:auto` and `:core`** so the
`:app` build that CI runs already covers the other two modules.

The rule, precisely:

1. A UI-position literal is a violation when the text **outside** its `${...}` substitutions contains at
   least one letter run (`"$favCount favorite"` → violation; `"$zoomLevel"`, `"${a}-${b}"` → exempt).
2. The existing exemptions stay: literals that contain `%` only, pure-symbol separators, and single
   camelCase identifiers (they never reach the letter-run test as display text but are kept explicit so
   the rule's intent is readable).
3. `NotificationChannel(`'s name/description arguments are a UI position.
4. `translatable="false"`-style intentional literals (e.g. `"NaviVeylin"`) stay possible via the same
   camelCase/identifier exemption rather than an allowlist.

Add `core/lint.xml` with `HardcodedText severity="error"` as the XML/`TextView` backstop.

- *Alternative — keep the task inline and cover the new shapes with more regexes.* Rejected: the existing
  inline list is already the thing that failed, it has no tests, and `buildSrc` is where this project puts
  build-time logic it intends to trust (AGENTS.md: `./gradlew -p buildSrc test` runs in every build).
- *Alternative — flag every template literal, allowlisting per call site.* Rejected: allowlist churn on
  every future dynamic label, and the three real occurrences found here are all covered by rule 1.

### D6 — Sweep with the new scanner before wiring it as a gate
Run the scanner over `:app`, `:auto` and `:core` before the gate is wired into `preBuild`, so the rule is
validated against the real tree instead of blocking the build on its first run. Findings beyond the four
recorded occurrences: fixed in this change when they are a literal plus a translation, otherwise recorded
as a new `TODO.md` entry with the scanner's output — never silently dropped, never used to grow scope.

## Risks / Trade-offs

- **[A false positive blocks every build]** → the rule is implemented and tested in `buildSrc` with one
  test per shape found here (interpolated display text, bare template, symbol separator, `%`-format,
  `NotificationChannel` argument), and the tree-wide sweep in D6 runs before the gate is wired.
- **[The `remember`-computed details title goes stale across a locale change]** → include the generic
  title in the `remember` key (`LocationDetailsSheet.kt:120`) and pin it with a Compose test using
  `RuntimeEnvironment.setQualifiers("de")` (the pattern `PluralFormatTest` already uses).
- **[Channel name on an existing install]** → the service re-creates the channel on start, which updates
  name and description; the on-device step checks Settings → Apps → NaviVeylin → Notifications shows the
  German name after a start, without a reinstall.
- **[A missed `resolveTitle` call site]** → impossible at compile time (required parameter, no default).
  The cost is a wider diff than one file; the benefit is that no surface can silently keep the literal.
- **[The car favorites titles cannot be confirmed on a device]** → the car surface is not attached; the
  row is covered by the `values-de` parity test and the screen's own template test, and the limitation is
  recorded in `TODO.md` rather than presented as verified.
- **[Scope creep from the sweep]** → capped by D6: trivial fixes only, everything else filed.
- **[Resource key drift between the three modules]** → the three `GermanTranslationCompletenessTest`s
  already enforce key parity per module; `auto`'s and `core`'s get the same plural coverage as `app`'s if
  they gain plurals (they do not in this change).

## Migration Plan

1. Land the wording fixes first (resources + call sites), so the gate's first run is green rather than
   blocking: `:app` plurals + channel name + notification titles + `Offroad`; `:auto` favorites titles +
   unnamed-row fallback; `:core` generic-title parameter and its two callers.
2. Update the tests that pin the old shape (`DetailsResolverTest.titleFallsBackToGeneric`,
   `LocationDetailsDialogComposeTest.coordinateLabelTitleShowsGenericLocation`, `auto/DetailsScreenTest`
   if it asserts the literal), add the German plural/parity cases, then move the scanner into `buildSrc`
   with its tests and wire `preBuild` per module; add `core/lint.xml`.
3. Verify: `./gradlew -p buildSrc test`, the four module suites with cleared `test-results`
   (`TODO.md` §17), and a de-DE pass on `emulator-5554` (API 37) — favorites group card, a `geo:` link
   with no name/address in the details dialog, the notification titles in the shade, the channel name in
   Settings.
4. No data migration and no manifest change. Rollback: revert the commit — a reverted build behaves
   exactly as today, and the channel name reverts on the next service start.

## Open Questions

- The German wording of the generic location title ("Standort" vs "Position") and of the unnamed-favorite
  fallback is a translation-review choice: the spec pins that the title is the surface's localized generic
  title, not the specific noun, so tests compare against the resource rather than a hardcoded word.
