# Proposal — fix-remaining-untranslated-strings

## Why

Four separate `TODO.md` entries record one root cause: user-facing text still lives outside the
resource system, and the gates that are supposed to prevent it cannot see any of it.

- **§104 ≡ §81(b)** — the favorites group cards read `2 favorites` / `3 favorites` in a German UI
  (`app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt:693`, an inline English string with
  hand-rolled pluralization).
- **§105** — the car favorites screens are titled in English on a German head unit
  (`auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt:110`; the same file falls back to the
  literal `"Favorite"` for an unnamed row at `:152` and `:176`).
- **§80(a)** — the generic details title is the Kotlin literal `"Location"` in
  `core/src/main/java/com/naviveylin/core/details/DetailsResolver.kt:133`, so it cannot be translated
  on either surface. `enhanced-details-sheet` pins that literal in prose.
- **§30 / §32** — the map-download notification channel name
  (`app/src/main/java/com/naviveylin/service/MapDownloadService.kt:131`) and the neutral / free-driving
  notification titles (`service/NavigationNotificationContent.kt:72-73`) are constants, and the phone
  keeps its own copy of the wording that `:core` already ships as `nav_hint_neutral`.

Why the gates miss them, verified in the tree:

| gate | blind spot |
|---|---|
| `checkHardcodedStrings` (`app/build.gradle.kts:330-369`, wired into `preBuild`) | scans **only** `app/src/main/java`, so `:core` and `:auto` literals are invisible; and it `return`s early on any literal containing `${` — exactly the shape of `text = "$favCount favorite${if (favCount != 1) "s" else ""}"` |
| lint `HardcodedText` (`app/lint.xml`, `auto/lint.xml`, `abortOnError = true`) | no config exists for `:core`, and it does not resolve a template built in a `@Composable` or a plain factory function |
| any pattern | `NotificationChannel(id, CHANNEL_NAME, …)` matches no `Text(`/`setTitle(`/`text =` pattern |

Why now: `i18n-l10n` states the contract but scopes it to "the `:app` and `:auto` modules", so `:core`
was never inside it; every one of the four defects is visible on the German surface today, and each
was already observed on a device.

## What Changes

- **Favorites group-card count** — replace the inline string with a `<plurals name="favorite_count">`
  entry (`one`/`other`) in `app/src/main/res/values/plurals.xml` + `values-de/plurals.xml` and render it
  through `pluralStringResource`, the way `ui/mapmanager/MapManagerScreen.kt:352` already does. Sweep the
  sibling count strings in the same sheet and in the car mapper for the same pattern.
- **Car favorites titles** — two `:auto` string resources (`values/` + `values-de/`) for the favorites
  and starred-favorites screen titles, and a resource for the unnamed-row fallback that replaces
  `fav.name ?: "Favorite"`.
- **Generic details title** — the `:core` resolver stops owning the English word: it takes the generic
  title as a caller-supplied string (or resource id), and each surface passes its own resource
  (`:app` `strings.xml` + `values-de/`, `:auto` `strings.xml` + `values-de/`). `:core` already ships
  `res/values/strings.xml` + `values-de/` (`nav_hint_neutral`), so no new resource plumbing is needed.
- **Remaining constants (§30 / §32)** — move the map-download channel name, the neutral and
  free-driving notification titles and the `"Offroad"` road fallback (`ui/navigation/NavigationStateOverlay.kt:250`)
  into resources with German forms, and have the phone neutral title use the existing `nav_hint_neutral`
  so the wording has one home.
- **Close the gate's blind spots** — extend `checkHardcodedStrings` to every first-party module that
  holds user-facing UI code (`:app`, `:auto`, `:core`), stop excusing an interpolated literal whose
  surrounding text carries letters (a pure template such as `"$zoomLevel"` stays exempt), and flag
  `NotificationChannel(`'s name argument; add a `core/lint.xml` with the same `HardcodedText`
  elevation the other two modules carry. A gate self-test pins each of the three known shapes so the
  rule cannot silently regress.
- **Not breaking for users**: all changes are additive resources plus one internal function signature.
  The one non-additive edit is `DetailsResolver.resolveTitle`'s parameter list (two call sites and the
  two tests that pin the literal).
- **Rollback path**: revert the commit. Strings are additive, so no persisted data, no migration and no
  installed-map interaction is involved; a reverted build behaves exactly as today.

Scope: **both surfaces** — phone (`:app`) and Android Auto / AAOS (`:auto`) — plus the shared `:core`
resolver they both call. This is not AA-specific and not phone-specific.

## Capabilities

### New Capabilities

None. This change adds no capability; it makes existing behaviour obey a contract the project already
states.

### Modified Capabilities

- `i18n-l10n` — *All user-facing text is translatable*: extend the scope from "`:app` and `:auto`" to
  every first-party module that renders user-facing text (naming `:core`), and describe the gate that
  actually runs instead of the `HardcodedText`-lint-only scenarios — the multi-module scan, the rule
  that an interpolated literal is not exempt when its surrounding text carries words, and
  notification-channel names as a covered shape. *Count-dependent strings use plurals*: add the
  favorites group-card count as a scenario (the requirement exists; this instance violates it).
  *German is fully supported*: add the car favorites titles to the German rendering scenario.
- `enhanced-details-sheet` — *Title shows name or address*: the coordinate-label fallback resolves to a
  localized generic title from a string resource, not to the English word "Location". The existing
  `DetailsResolverTest.titleFallsBackToGeneric` and
  `LocationDetailsDialogComposeTest.coordinateLabelTitleShowsGenericLocation` currently pin the literal
  and move with it.

Checked and **not** modified (their requirements stay true as written): `auto-destination-details` says
"a generic title" without pinning the word, and its coordinate row is already locale-stable;
`group-grid-display` says a card shows "the group name and favorite count" without prescribing how the
count is rendered (the plural contract is `i18n-l10n`'s); `auto-favorites` and `auto-map-layout` refer
to the starred-favorites label that is already a resource; `navigation-ongoing-notification` and
`map-download-infrastructure` describe notification content but never pin its text.

## Impact

| Area | Files / modules |
|---|---|
| Phone UI | `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt`, `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt`, `app/src/main/java/com/naviveylin/service/MapDownloadService.kt`, `app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt` |
| Car UI | `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt`, the car `FavoritesScreenMapper` count path |
| Shared | `core/src/main/java/com/naviveylin/core/details/DetailsResolver.kt` (+ the two detail screens that pass the generic title) |
| Android resources | `app/src/main/res/values/plurals.xml` + `values-de/plurals.xml`, `app/src/main/res/values/strings.xml` + `values-de/`, `auto/src/main/res/values/strings.xml` + `values-de/`, `core/src/main/res/values/strings.xml` + `values-de/` |
| Build / gate | `app/build.gradle.kts` (`checkHardcodedStrings`, `preBuild` wiring), new `core/lint.xml`, optionally a `buildSrc` scanner with unit tests if the rule outgrows the inline task (`CoordinateLogScanner` is the precedent) |
| Tests | `app/src/test/java/com/naviveylin/i18n/GermanTranslationCompletenessTest.kt`, `auto/.../GermanTranslationCompletenessTest.kt`, `core/.../GermanTranslationCompletenessTest.kt`, `core` `DetailsResolverTest`, `LocationDetailsDialogComposeTest`, `PluralFormatTest`, plus the new gate self-test |
| Guidelines | `guidelines/UI.md` §Internationalisation/Localisation (lines ~752-793) — restate that the rule covers `:core` and that an interpolated literal is not exempt; `guidelines/Design.md` §12 (single source of truth) for the deduplicated neutral title |
| Native / JNI | **none** — no submodule patch, no `:osmscout-client-java` override, no CMake target. Native English text (POI categories, turn instructions) is already localized at the frontend per `i18n-l10n` and is untouched here. |
| Manifests | none |

Verification: unit tests (the three `GermanTranslationCompletenessTest`s for key parity, the resolver
and Compose tests for the renamed behaviour, a gate self-test that must flag each of the three known
shapes) plus a de-DE pass on the attached phone emulator (`emulator-5554`, API 37, `sdk_gphone64_x86_64`)
for the favorites sheet, the generic details title and the notification texts. The car favorites titles
have no car surface attached, so that row is limited to the `values-de` parity test and the screen's
own template test — the limitation is recorded rather than hidden.

### Decisions to confirm before the specs phase

1. **Gate strictness.** The proposed rule flags an interpolated literal only when its non-interpolated
   text carries at least one letter run, so `"$zoomLevel"` and `"${a}-${b}"` stay exempt while
   `"$favCount favorite"` fails. The alternative — flagging every template and allowlisting per call
   site — would catch more, at the cost of allowlist churn in every future dynamic label. The chosen
   rule is testable with the three real occurrences found here.
2. **Sweep breadth.** This change covers the four recorded occurrences (§80(a), §104/§81(b), §105,
   §30/§32). Scanning for further literals could surface more (only `i18n-l10n` documents the rule, and
   no sweep has been run); if that happens, the extra finds are either fixed in this change or filed as
   a new `TODO.md` entry, not silently expanded.
