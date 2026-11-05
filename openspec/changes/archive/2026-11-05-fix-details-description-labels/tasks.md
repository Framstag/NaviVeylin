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
      `descriptionLabel(...)`; keep the label row's `":"` outside the resource, and put the subsection's ordinal
      index inside `R.string.description_subsection_index` (`%1$s %2$d`) so a translator can reorder the two
      (`UI.md` §10 — task 6.2 below).
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

## 6. The review round's corrections

Review round 1 refused the change on one clause and named the rest as pointers, counts and one rule the new code
contradicted (`reviewer-report-1.md` in the run's item directory; `LOOP-BLOCKING: 1`, `LOOP-CORRECTIONS-ONLY: yes`).
This section is that round's work, repeated here because the round's tree was ended at a bound before it was
committed — the record is the recipe, and each item is [x] only because the tree now carries it.

Requirement: scenario "An English device keeps the native words".

- [x] 6.1 The English scenario named a case that does not exercise its first clause. Add
      `LocationDetailsDialogComposeTest.englishDeviceKeepsTheNativeDescriptionWords` — the `Contact` header and the
      `Phone:` and `Website:` rows under the default locale — and name it in the scenario's `**Case:**` line, in the
      shipped spec and in this change's delta alike. The rows' click behaviour stays where it is specified
      (`phoneValueOpensTheDialer` for a `tel:` value, `websiteValueStartsTheBrowser` for an `http`/`https` value), so
      the translation scenario claims no click. Verify: the case is in the class's green suite
      (`evidence/TEST-green-gate-mobile-LocationDetailsDialogComposeTest.xml`), and
      `openspec validate i18n-l10n --type spec` plus the change validator agree.
- [x] 6.2 `UI.md` §10 ("positional format args are used so translators can reorder") was contradicted by the
      subsection header's string concatenation. Replace it with `R.string.description_subsection_index`
      (`%1$s %2$d` in both locales) read through `stringResource`. Verify:
      `grep -c 'description_subsection_index' app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` is 1 and
      `grep -c 'name="description_subsection_index"'` is 1 in each `strings.xml`;
      `GermanTranslationCompletenessTest` stays green (the key exists in both locales).
- [x] 6.3 The pointers and counts the round found wrong: `ContactLinks.kt:88-92` → `:41-47,63`; "24 files, of which 3
      render a key" → 23 files, two render (`DetailsScreen.kt:690`, the phone sheet), the picker only matches; "8
      existing assertions" → 6, with the command that prints them; the 84/64/12/8 breakdown now carries its own
      commands. Verify: each command sits beside its number in `proposal.md` and `design.md` and prints what the
      sentence says.
- [x] 6.4 The record's unproven halves: the `getIdentifier` alternative no longer claims resource shrinking and
      lint; the on-device sentence is labelled a prediction; `design.md` carries an `## Open decisions` section;
      the rollback story names the applied spec; `UI.why.md` §1 carries the parity row for the description rows.
      Verify: `bash tools/check-change-readiness.sh openspec/changes/archive/2026-11-05-fix-details-description-labels`
      prints `LOOP-CHANGE-READY: ok`, and `bash tools/check-doc-routes.sh` exits 0.
- [x] 6.5 The evidence the round asked to carry: the compiled step's per-suite tallies instead of a `tests="0"`
      stub, this round's gate log and summary, a re-measured `measure.txt` for this tree, and a signed statement of
      what the revert-check is (`evidence/revert-check.txt`): no mutation was planted, the red-on-HEAD run is the
      red case, and the tree is restored by construction. Verify: every `LOOP-MEASURE-TALLY` line names a file in
      this change's `evidence/`.
