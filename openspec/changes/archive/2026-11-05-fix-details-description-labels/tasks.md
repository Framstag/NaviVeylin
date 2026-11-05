# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/i18n-l10n/spec.md` for the contract.

## 1. Prove the defect on the base commit

Requirement: "Native description labels are localized at the frontend", scenarios "A German device reads the German
words".

- [x] 1.1 Add `LocationDetailsDialogComposeTest.germanDeviceTranslatesDescriptionSectionsAndLabels`: a German qualifier
      and locale, one `Way`/`Lanes`/`MaxSpeed` row (subsection header + label) and the two `Contact` rows, asserting
      `Kontakt`, `Spuren`, `Höchstgeschwindigkeit:`, `Telefon:`, `Webseite:` and the absence of `Contact`, `Lanes`,
      `MaxSpeed:`, `Phone:`, `Website:`. Verify: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.LocationDetailsDialogComposeTest" -PforceTests --no-build-cache` is red on the base
      commit with `tests=24 failures=1`, the one failure being `could not find any node … 'Kontakt'`;
      `evidence/TEST-red-head-LocationDetailsDialogComposeTest.xml`.
- [x] 1.2 Add `anUnmappedDescriptionKeyKeepsTheNativeKey`: an entry whose section and label keys map to nothing
      renders those keys verbatim and still shows the entry. Verify: the same run is green for this case (it holds on
      the base commit too — it guards the fallback the fix introduces).

## 2. The table

Requirement: scenario "A German device reads the German words", "An English device keeps the native words".

- [x] 2.1 Write the 74-value table beside the sheet, from the native constants' values, so no key is hand-copied:
      `grep -oP '(?:SECTION_NAME_|LABEL_KEY_|SUBSECTION_NAME_)[A-Za-z0-9_]+\s*=\s*"[^"]*"' …/DescriptionService.cpp |
      sed -E 's/.*=\s*"//;s/"$//' | sort -u` prints 74 values, and that list is the table's key set. Verify: the
      generated `when` has 74 branches plus `else -> null`, and `diff <(keys) <(values)` is empty
      (`evidence/description-labels.tsv`).
- [x] 2.2 Add `DescriptionLabels.kt`: `@StringRes res(key: String?): Int?` over the trimmed value, and the composable
      `descriptionLabel(key: String?): String` that falls back to the key. Verify: `:app:checkHardcodedStrings` is
      green (the branches are match keys, not text positions) and the German case of task 1.1 finds its nodes.

## 3. The two resource sets

Requirement: scenarios "A German device reads the German words", "Every mapped key has a German translation".

- [x] 3.1 Add the 74 `description_label_*` entries to `app/src/main/res/values/strings.xml`, the English value of each
      key being the key itself (`Contact` → `Contact`), so the English rendering cannot move. Verify:
      `grep -c 'name="description_label_' app/src/main/res/values/strings.xml` is 74.
- [x] 3.2 Add the same 74 keys to `app/src/main/res/values-de/strings.xml` with the German word
      (`Contact` → `Kontakt`, `Phone` → `Telefon`, `Website` → `Webseite`, `MaxSpeed` → `Höchstgeschwindigkeit`, …).
      Verify: `grep -c 'name="description_label_' app/src/main/res/values-de/strings.xml` is 74 and
      `:app:testMobileDebugUnitTest --tests "com.naviveylin.i18n.GermanTranslationCompletenessTest"` is green
      (`tests=2 failures=0`, `evidence/TEST-green-GermanTranslationCompletenessTest.xml`).

## 4. The render sites

Requirement: scenarios "A German device reads the German words", "A key the table does not carry".

- [x] 4.1 `LocationDetailsSheet.kt`: read the section header, the subsection header and the label row through
      `descriptionLabel(...)`; keep the subsection's ordinal index and the label row's `":"` outside the resource.
      Verify: `grep -c "descriptionLabel(" app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` is 4
      (three render positions, one of them a two-branch expression) and no raw `sectionKey` / `labelKey` reaches a
      `text =` argument (`grep -n "text = sectionKey\|text = descEntry.labelKey"` prints nothing).
- [x] 4.2 Re-run the red case and its class. Verify: `tests=24 failures=0 errors=0` at
      `evidence/TEST-green-applied-LocationDetailsDialogComposeTest.xml`, and the class's existing `Phone:`,
      `Website:`, `Name:`, `General` assertions are still green (English value = key).

## 5. The rule that said otherwise

Requirement: `guidelines/UI.md` §10 ("a spec contradicts a guideline → update the guideline in the same change").

- [x] 5.1 Replace `guidelines/UIPhone.md` §5's last sentence — "The labels stay the native English keys (their
      translation is issue #767)" — with the rule this change establishes: the rows read
      `R.string.description_label_*` through `DescriptionLabels`, an unmapped key renders as itself, and the car's
      identical rows are the parity follow-up. Verify: `bash tools/check-doc-routes.sh` exits 0 and the section names
      `DescriptionLabels` and the follow-up.
