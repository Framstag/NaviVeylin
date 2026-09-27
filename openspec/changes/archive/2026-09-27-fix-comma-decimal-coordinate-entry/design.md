# Design — fix-comma-decimal-coordinate-entry

## Context

Five coordinate-string sites exist, with three different behaviours:

```
phone  ui/map/MapCanvasViewModel.kt:2434   context.getString(R.string.coordinates_format, lat, lon)  -> device locale
phone  ui/map/MapCanvasViewModel.kt:2543   "%.5f, %.5f".format(...)                                 -> device locale
phone  ui/map/MapCanvasViewModel.kt:2518   String.format(Locale.US, "%.5f, %.5f", ...)              -> locale-stable
phone  ui/map/LocationDetailsSheet.kt:177  stringResource(R.string.coordinates_format, ...)         -> device locale
phone  ui/favorites/FavoritesSheet.kt:723  "%.5f, %.5f".format(...)                                 -> device locale
phone  ui/route/FavoritePickerDialog.kt:135 "%.5f, %.5f".format(...)                                -> device locale
car    auto/NavigationTemplateMapper.kt:346 String.format(Locale.US, ...)  (documented as "data")   -> locale-stable
car    auto/DetailsScreen.kt:543           String.format("%.5f", ...)                               -> device locale
```

Entry side (`FavoritesSheet.kt:938/941` prefill, `:977-983` parse) formats with the device locale and
parses with `toDoubleOrNull()`, which is the shipped defect: German-locale users cannot save a
favourite that the app itself prefilled.

Two further consumers depend on the coordinate string's shape, which is why one seam must own it:

- `core/details/DetailsResolver.kt:16` `COORDINATE_LABEL_REGEX = -?\d+\.\d+,\s*-?\d+\.\d+` decides
  whether a label is "not a title" (`resolveTitle` `:124-126`) and whether it may be used as a street
  (`:46`, `:50-52`). It only matches the dot form, so on a German device the coordinate label produced
  by `:2434`/`:2543` fails the match and becomes a **title** (and a candidate street) — the opposite
  of spec `enhanced-details-sheet` ("Coordinate label falls back to generic title") and of
  `guidelines/UI.md:244-245`.
- `FavoritesSheet` stores the favourite's `lat`/`lon` as Doubles in `favorites.json`; the string form
  is presentation only, so no persisted data changes.

Constraints: no native/JNI or submodule change; the app has two surfaces (phone + `:auto`); the
project already has the `:core` helper precedent `core/DistanceFormat.kt` with a
`locale: Locale = Locale.getDefault()` parameter, unit-tested beside it.

## Goals / Non-Goals

**Goals:**
- One owner for the coordinate string on both surfaces: formatting and single-value parsing.
- The add-favourite dialog round-trips: whatever it prefills can be saved unchanged; a typed `,` or
  `.` is accepted.
- A coordinate pair is never ambiguous on a comma-decimal device, and a coordinate label is
  recognisable as "not a title" on every locale.
- Phone and car show the same coordinate string for the same value.

**Non-Goals:**
- Distance/speed/size formatting (stays device-locale, unchanged; `Locale.ROOT` ban stays).
- Shared-location and deep-link *input* parsing (`app/share/SharedLocationParser.kt`,
  `core/DeepLinkParser.parseCoordinates`) — third-party text, stays strict.
- The diagnostics/log coordinate strings of `TODO.md` §68 (log text, separate compliance change).
- Any storage format, JNI or favourites-store change.

## Decisions

### D1 — One seam in `:core`, not per-site fixes

*Chosen:* new `core/src/main/java/com/naviveylin/core/CoordinateFormat.kt` with pure functions:
`formatCoordinatePair(lat, lon, pattern)`, `formatCoordinate(value)`, `parseCoordinate(text)`, and a
single `parseCoordinatePair(text)` only where the app owns both sides (none in this change — see D5).

*Alternatives:*
- (a) Fix only `FavoritesSheet`'s parse (`FavoritesSheet.kt:977-983`) — smallest diff, but leaves the
  ambiguity, the title misclassification and the car/phone divergence, i.e. four of the five
  `TODO.md` §31 sites stay wrong.
- (b) Inline `Locale.US` at each of the six call sites — no new API, but six places to keep in sync,
  which is exactly the duplication the file already exhibits (three behaviours for one value).

*Rationale:* `guidelines/Design.md` §12 ("shared logic is extracted once into `:core` … never
duplicated per variant, screen, or module") and the `DistanceFormat.kt` precedent. Unit-testable as
pure functions (§12 "make it testable").

### D2 — Coordinate strings are locale-stable data

*Chosen:* format with a fixed `Locale.US`, 5 decimals, `", "` between the values (option B of the
proposal, confirmed by the owner).

*Alternatives:*
- (a) Device locale + tolerant parse: no `i18n-l10n` change, but the pair stays ambiguous
  (`51,50000, 7,40000` = decimal commas and pair comma are the same character) and
  `COORDINATE_LABEL_REGEX` would have to accept both forms on every locale.

*Rationale:* the decisive evidence is the `DetailsResolver` contract — a dot-form regex cannot be
made to recognise a locale-form string without either a second regex branch or a re-parse; pinning
the string form makes one regex correct on every device, and matches the rule the car mapper already
documents ("a coordinate string is data, not display text").

*Consequence:* `openspec/specs/i18n-l10n/spec.md` and `guidelines/UI.md:676-681` must both state the
carve-out (numbers locale-aware; coordinate strings fixed-locale), and `guidelines/UI.md:242`
("formatted `%.5f, %.5f`") stays as-is because it already implies the dot form.

### D3 — Single-value parse grammar

*Chosen:* `parseCoordinate(text)` = trim, accept an optional `+`/`-`, digits, exactly **one**
separator which may be `.` or `,`, digits; then parse and reject anything outside `-90..90`
(latitude) / `-180..180` (longitude via the caller's range check); `null` otherwise. The field
validates against the range its field owns.

*Alternatives:*
- (a) `text.replace(',', '.').toDoubleOrNull()` — one line, but accepts exponent forms (`1e2`), group
  separators (`1,234,567`) and produces values the formatter would never emit.
- (b) A locale-aware `NumberFormat.parse` — correct for grouping, but it is lenient about prefixes
  (`"51abc"` parses as 51 in lenient mode) and needs `isParseIntegerOnly` gymnastics, and it still
  cannot accept the other separator.

*Rationale:* the field is a coordinate input, not a general number field: an explicit grammar keeps
`"51,51,391"` invalid (spec scenario "Invalid coordinate text keeps Save unavailable") and keeps the
accepted set equal to the formatable set.

### D4 — Pair layout stays a resource; the helper pins the locale

*Chosen:* keep `<string name="coordinates_format" translatable="false">%.5f, %.5f</string>` as the
layout source and call it through the helper:
`String.format(Locale.US, context.getString(R.string.coordinates_format), lat, lon)`. Both phone
sites (`LocationDetailsSheet`, `MapCanvasViewModel`) go through this; the car keeps the same literal
pattern via its mapper (no `Context` in the mapper's call path) with a test asserting the two agree.

*Alternatives:*
- (a) Drop the resource and hardcode the pattern in the helper — fewer moving parts, but the pair
  layout would then have two sources (helper + the car's literal) with no marker that they must agree.
- (b) Keep `getString(id, vararg)` / `stringResource(id, ...)` and accept the device locale —
  rejected: this *is* the defect (`Context.getString` applies the default locale, so
  `MapCanvasViewModel.kt:2434` and `LocationDetailsSheet.kt:177` produce the comma form today).

### D5 — Third-party text parsing stays strict

*Chosen:* `SharedLocationParser` and `core/DeepLinkParser.parseCoordinates` keep dot-only values and
treat `,` as the pair separator. Only the app's own single-value entry fields use the tolerant parse.

*Alternatives:*
- (a) Reuse the tolerant parse there: in `"48.8566, 2.3522"` the separator between the two values is
  a comma, and in `"48,8566"` a comma is a decimal separator — the same character with two meanings in
  one field, which is exactly the ambiguity this change removes.

### D6 — The label detector is pinned to the formatter's output

*Chosen:* keep `DetailsResolver.COORDINATE_LABEL_REGEX` as the single detector, and add a test that
the helper's output for a pair matches it (plus a negative case for a name that merely contains
digits). No behaviour change in the resolver itself.

*Alternatives:*
- (a) Move the detector into `:core` next to the formatter — closer to its input, but it is used only
  by the resolver's title/street rules and would give `:core` a details-domain concern.
- (b) Leave it untested: the drift is precisely what caused the German-device title defect.

### Threading and lifecycle

The new functions are pure, stateless, allocation-bounded (one `String` per call) top-level functions
in `:core`. No dispatcher, no coroutine, no `Context` retention, no native call, no lifecycle owner:
they are safe on the main thread, inside Compose composition, inside a car template build, and inside
the host's click path — none of the `guidelines/Design.md` §4 rules is engaged (no filesystem, no JNI,
no blocking work). The existing call sites keep their threading; nothing is added to a scope.

## Risks / Trade-offs

- **Visible string change on every surface** — coordinate strings on a comma-locale device change
  from `51,50000, 7,40000` to `51.50000, 7.40000`; on an English device nothing changes. Risk: an
  unexpected user complaint about "foreign" decimals. Mitigation: this is the owner-chosen rule; the
  entry fields still accept a typed comma, and the spec states the rule explicitly.
- **German device title/street behaviour changes** — labels that previously fell through to the
  coordinate title now resolve to generic "Location" (spec-conform) and can no longer be taken as a
  street (`DetailsResolver:50-52`). Risk: a test or on-device expectation pinned the old behaviour.
  Mitigation: covered by new resolver-facing tests; `details` resolver tests already exist
  (`core/src/test/java/com/naviveylin/core/details/DetailsResolverTest.kt`).
- **Two sources for the pair layout** (resource for phone, literal in the car mapper, D4) — Risk:
  they drift. Mitigation: the "Destination text and coordinates row agree" spec scenario plus a unit
  test asserting both produce the same string for a German and an English default locale.
- **Range/grammar edge cases** — `"-0.00000"`, `"0,00000"`, leading `+`, whitespace. Risk: a
  legitimate value rejected. Mitigation: table-driven unit tests for the accepted/rejected set.
- **Test churn** — no test currently asserts a comma-form coordinate pair (searched: none), so the
  churn is additive; the Compose dialog test is new.
- **Rollback** — revert the touched Kotlin files + the four spec deltas + the `guidelines/UI.md`
  bullet; no data migration, no persisted format depends on this.

## Verification

Unit tests (`:core`):
- `CoordinateFormatTest`: pair format under `Locale.GERMANY` and `Locale.US` default locales,
  single-value parse for `.`/`,`/sign/whitespace/multiple separators, range rejection, and
  `formatCoordinatePair` output matching `DetailsResolver`'s `COORDINATE_LABEL_REGEX`.
- `DetailsResolverTest`: a German-locale coordinate label resolves to the generic "Location" title and
  is not used as a street.

Unit / Compose tests (`:app`, `:auto`):
- Add-favourite dialog under a German default locale: prefill → Save enabled with only a name typed →
  store; comma-typed and dot-typed values both parse; `"51,51,391"` keeps Save unavailable
  (`guidelines/Build.md` §6 flavor-qualified task names: `testMobileDebugUnitTest`).
- `FavoritePickerDialog` and `MapCanvasViewModel` label/fallback strings render the locale-stable form.
- Car: `NavigationTemplateMapper.coordinatesText` and the `DetailsScreen` coordinates row produce the
  same string under a German default locale.

On-device (German-locale AVD, `guidelines/Build.md` §10 recipe):
- Add a favourite from the map center without touching either coordinate field; verify the entry in
  the group row, in `favorites.json` (Doubles unchanged) and in the car details row for the same
  point; grep logcat for the coordinate strings (`adb logcat -s NaviVeylin`).
