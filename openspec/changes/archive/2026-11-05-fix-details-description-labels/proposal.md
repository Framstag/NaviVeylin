# The details dialog reads its description labels from the surface's resources

## Why

**Symptom.** Every section header, subsection header and label row of the phone details dialog prints the native
description key verbatim, in every locale. A German device reads `Contact`, `Phone:` and `Website:` beside the
translated `Ort` / `Bereich` rows and the translated generic title.

**Root cause, in one sentence.** libosmscout's `DescriptionService` publishes the description vocabulary as English
constants that it never localizes, and the phone renders those values directly at three text positions —
`app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt:295` (section header, `text = sectionKey`),
`:317` (subsection header, `text = subsectionLabel`) and `:335` (label row, `text = descEntry.labelKey + ":"`) — so no
resource ever stands between the native key and the screen.

**Red test on HEAD.** `LocationDetailsDialogComposeTest.germanDeviceTranslatesDescriptionSectionsAndLabels` — added
by this change, run on the base commit before any production edit:

```
tests=24 skipped=0 failures=1 errors=0 timestamp=2026-11-05T10:13:16.626Z
failure: java.lang.AssertionError: Action performScrollTo() failed.
Reason: Expected exactly '1' node but could not find any node that satisfies:
(Text + EditableText contains 'Kontakt' (ignoreCase: false))
```

XML: `evidence/TEST-red-head-LocationDetailsDialogComposeTest.xml`. Command:
`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.LocationDetailsDialogComposeTest" -PforceTests --no-build-cache`.

**The vocabulary, measured.** 84 constants — 64 `LABEL_KEY_*`, 12 `SECTION_NAME_*`, 8 `SUBSECTION_NAME_*` — carry
**74 distinct values** (duplicates: `Name`, `Lanes`, `Foot`, `Bicycle`, `Car`, `Type`, `IsIn`, `Location`, `Address`,
`Ref` …), so a table keyed on the *value* needs 74 entries rather than 84:

```
grep -oP '(?:SECTION_NAME_|LABEL_KEY_|SUBSECTION_NAME_)[A-Za-z0-9_]+\s*=\s*"[^"]*"' \
  app/src/main/cpp/libosmscout/libosmscout/src/osmscout/description/DescriptionService.cpp \
  | sed -E 's/.*=\s*"//;s/"$//' | sort -u | wc -l
74
```

**Owning requirement.** spec `i18n-l10n` — "All user-facing text is translatable" and "German is fully supported";
the delta of this change adds the one requirement the defect contradicts, "Native description labels are localized
at the frontend", beside the existing "POI category names are localized at the frontend" (the same shape: a native
English identifier mapped to a resource at the frontend, native layer untouched).

**Owning guideline section.** `guidelines/UIPhone.md` §5 "Phone details dialog structure" — its last sentence is the
rule this change reverses: "The labels stay the native English keys (their translation is issue #767)". §10 of
`guidelines/UI.md` owns the resource rule ("all user-facing text lives in Android string resources"; "a shared module
owns no wording"), and §1 owns the parity rule the car side follows later.

**Surface.** 4 call sites in 1 file, 74 resource entries × 2 locales, 2 cases added, 0 cases rewritten, 1 guideline
sentence replaced — measured with `grep -c "descriptionLabel(" LocationDetailsSheet.kt` (4),
`grep -c 'name="description_label_'` over both `strings.xml` files (74 each), and
`grep -rl "labelKey\|sectionKey" --include=*.kt --include=*.java app/src core/src auto/src` (24 files, of which 3
render a key: the phone sheet, the car details screen, the car candidate picker — the car's are the parity
follow-up, see below). 8 existing `:app` assertions name a raw key (`"Phone:"`, `"Website:"`, `"Name:"`,
`"General"`); they stay green because the English resource value of each key *is* the key.

## What Changes

- **`app/src/main/java/com/naviveylin/ui/map/DescriptionLabels.kt`** (new) — `DescriptionLabels.res(key)` maps one
  trimmed raw value to `R.string.description_label_<slug>` (74 branches, `else -> null`), and the composable
  `descriptionLabel(key)` resolves it with `stringResource`, falling back to the key itself when unmapped.
- **`app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt`** — the three render sites read
  `descriptionLabel(...)` instead of the raw key: section header, subsection header (the index stays outside the
  resource, because a number is data), label row (the `":"` separator stays in Kotlin).
- **`app/src/main/res/values/strings.xml`** and **`values-de/strings.xml`** — 74 `description_label_*` entries each.
  The English value of every key is the key itself, so English behaviour does not change by one character; German
  supplies the word (`Contact` → `Kontakt`, `Phone` → `Telefon`, `Website` → `Webseite`, `MaxSpeed` →
  `Höchstgeschwindigkeit` …). The table is `evidence/description-labels.tsv`.
- **`guidelines/UIPhone.md`** §5 — the "labels stay the native English keys" sentence is replaced by the rule this
  change establishes, naming the mapping and its fallback (config rule: a change that contradicts a guideline
  updates it in the same change).
- **`openspec/specs/i18n-l10n/spec.md`** (delta) — one ADDED requirement with four scenarios, each naming its case.

**Additive or breaking: additive.** No signature changes; `:core`, `:auto` and the native layer are untouched.
The German rendering of every other string is unchanged; the English rendering of the description rows is unchanged.

## Impact

- `:app` — `ui/map/DescriptionLabels.kt` (new), `ui/map/LocationDetailsSheet.kt`, `res/values/strings.xml`,
  `res/values-de/strings.xml`, `test/.../ui/map/LocationDetailsDialogComposeTest.kt`.
- `guidelines/UIPhone.md` §5.
- `openspec/specs/i18n-l10n/spec.md` via `openspec/changes/fix-details-description-labels/`.
- Not touched: `:core` (owns no wording, stays the raw-value matcher for `DetailsResolver` and `ContactLinks`), the
  native layer, `:auto`.

**Rollback.** Revert the five `:app` files and the guideline sentence; the change directory is not applied to the
specs until archiving, so a revert leaves the shipped capability untouched.

## What This Does Not Do

- **Not the car.** `auto/src/main/java/com/naviveylin/auto/DetailsScreen.kt:690` sets the row title from
  `entry.labelKey` and `CandidatePickerScreen.kt:72` matches on the same values. The item is phone-scoped and names
  the car's rows the parity follow-up; `:auto` keeps its own `res/` (spec `i18n-l10n` — "`:auto` keeps its own
  resources"), so its side is a resource set of its own, not a copy of this Kotlin table. Until then the car prints
  the raw key and the phone does not — a deviation under `guidelines/UI.md` §1, recorded here and in the guideline.
- **No prettier English.** The English value of each key is the key, so this change does not reword `LanesForward`
  into `Lanes forward`. That is a wording decision (a translation-quality question, not the missing localization
  this item proves) and it would move 8 existing assertions for no German gain.
- **No native change.** The vocabulary crosses the boundary as values today; nothing in libosmscout is edited, and no
  constant name is spelled in Kotlin (Kotlin cannot see it).
