# Proposal — fix-comma-decimal-coordinate-entry

## Why

On any device whose locale uses a comma as decimal separator (de, fr, es, it, pt, nl, pl, ru, …) the
favourites sheet's add-favorite dialog is unusable: the dialog prefills latitude/longitude with the
device locale (`"%.5f".format(lat)` → `51,51391`, `FavoritesSheet.kt:938/941`) but the Save button
gates on `latText.toDoubleOrNull()` (`:977-983`), which accepts a dot only. The button stays disabled
in silence — no error, no feedback — and the prefilled value can never be saved; the user has to
retype both fields with a dot. Proven on-device on a German-locale Pixel_8 AVD (recorded in
`TODO.md` §45: Save does nothing and `files/favorites.json` keeps its mtime until the values are
retyped with dots).

The same missing convention sits under every other coordinate string: `FavoritesSheet.kt:723`
(group row), `LocationDetailsSheet.kt:175` (`coordinates_format`), `FavoritePickerDialog.kt:135`,
`MapCanvasViewModel.kt:2543` (label fallback) and the car's `DetailsScreen.kt:543` all format with the
default locale, so a comma-locale device shows `51,50000, 7,40000` — the coordinate separator and the
decimal separator are then the same character and the pair is unreadable. The car's
`NavigationTemplateMapper.coordinatesText` already documents the opposite convention in a comment
("Locale stable … a coordinate string is data, not display text", `Locale.US`), so the repo carries
two contradictory conventions today, and `i18n-l10n` currently mandates the device-locale one for
coordinates.

Doing this now: it is the highest impact-per-effort item in `TODO.md` (core data-entry flow dead for
a large locale share, no workaround), and one shared helper in `:core` closes the whole §31 /§40.26
family instead of patching one call site.

## What Changes

- **Add one coordinate string seam in `:core`** (mirroring `core/DistanceFormat.kt`): a formatter for
  a coordinate pair and for a single coordinate at 5 decimals, and a tolerant parser that accepts both
  the `.` and the `,` decimal separator. No new public API without KDoc.
- **Favourites add-dialog round-trips**: the prefill and the parse use the same seam, so whatever the
  dialog shows can be saved again; hand-typed `51,51391` and `51.51391` both parse.
- **Route the remaining phone coordinate strings through the seam**: `FavoritesSheet` group row,
  `LocationDetailsSheet` (drop/replace the `coordinates_format` literal), `FavoritePickerDialog`,
  `MapCanvasViewModel` label fallback.
- **Car parity**: `DetailsScreen`'s coordinates row uses the same seam and therefore the same rule the
  `NavigationTemplateMapper` already documents; the two car sites agree.
- **Spec deltas**: `fav-management-ui` (entry round-trip), `enhanced-details-sheet` and
  `auto-destination-details` (one stated format rule), `i18n-l10n` (coordinate strings are data, or a
  carve-out — see the decision below).
- **Not in scope**: shared-location/deep-link *input* parsing (`SharedLocationParser`,
  `core/DeepLinkParser.parseCoordinates` stay strict, they parse third-party text, not our own
  output), `TODO.md` §68 log redaction, and the favourites-store/JNI layer.
- Scope: **phone + Android Auto** (the car has no coordinate *entry* UI, only the display row).
  Additive/behavioural — no API, storage-format or JNI change; rollback is a revert of the touched
  Kotlin files and the spec deltas.

## Decision needed (large consequence)

`openspec/specs/i18n-l10n/spec.md` (Requirement: Locale-aware number formatting) explicitly names
coordinates among the values to format **with the device locale** and forbids a fixed locale. That
directly contradicts the convention the car mapper documents. Two options:

- **A — device locale everywhere, parse tolerantly.** Keep `51,51391` as the displayed/prefilled
  form; the parser accepts `,` and `.`. No `i18n-l10n` carve-out. *Consequence:* the pair string
  stays ambiguous in comma locales (`51,50000, 7,40000`) unless a pair-specific rule (labels or a
  different join) is added, so a second decision is still needed for pair display.
- **B — coordinate strings are data (recommended).** Format coordinate strings locale-stably
  (`.` separator) on both surfaces, matching the car mapper's documented rule and making every pair
  unambiguous; the parser stays tolerant so a user typing the locale form still succeeds.
  *Consequence:* needs an `i18n-l10n` delta (carve-out naming coordinate strings as locale-stable
  data); the number formatting of distances/speeds/sizes is untouched.

B is recommended: it fixes the entry bug *and* the ambiguity in one rule, and it agrees with the
convention the car mapper (`NavigationTemplateMapper.coordinatesText`) already documents.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `fav-management-ui`: adding a favorite SHALL accept a coordinate value in either decimal separator
  and the prefilled value SHALL be saveable unchanged; the group row SHALL show an unambiguous pair.
- `enhanced-details-sheet`: the "lat, lon" coordinate display SHALL follow the stated coordinate-string
  format rule instead of the device-locale default.
- `auto-destination-details`: the car coordinates row SHALL use the same coordinate-string format rule
  as `NavigationTemplateMapper.coordinatesText` (car parity).
- `i18n-l10n`: coordinate strings are locale-stable data (option B) or the device-locale requirement
  keeps them with a pair-display rule (option A).

## Impact

Phone (`:app`):
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt` (prefill `:938/941`, parse
  `:977-983`, group row `:723`)
- `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` (`:175`) and
  `app/src/main/res/values/strings.xml` (`coordinates_format`)
- `app/src/main/java/com/naviveylin/ui/route/FavoritePickerDialog.kt` (`:135`)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (label fallback `:2543`)
- tests: `app/src/test/java/com/naviveylin/ui/favorites/**`, `.../ui/map/**`, plus a `Locale.GERMANY`
  case for the dialog round-trip

Car (`:auto`):
- `auto/src/main/java/com/naviveylin/auto/DetailsScreen.kt` (`:543`), reference
  `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt:346`

Shared (`:core`):
- new `core/src/main/java/com/naviveylin/core/CoordinateFormat.kt`, tests beside `DistanceFormatTest`

Specs: `openspec/specs/fav-management-ui/spec.md`, `enhanced-details-sheet/spec.md`,
`auto-destination-details/spec.md`, `i18n-l10n/spec.md`.

Guidelines: `guidelines/Design.md` §12 (single source of truth — why one seam instead of five call
sites) and `guidelines/UI.md` (phone/AA label + value parity for the coordinate rows). No
`MapRendering.md` or `Build.md` impact; no native/JNI change, so no submodule patch.

Verification: unit tests for format/parse (both separators, `Locale.GERMANY` round-trip), Compose
test for the add-dialog Save path under a German default locale, car mapper/DetailsScreen row tests,
and an on-device check on a German-locale device or AVD (add a favourite from the map center without
touching the fields).
