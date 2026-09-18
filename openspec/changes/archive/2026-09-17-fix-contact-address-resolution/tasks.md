# Tasks — Fix contact address resolution

Specs: `address-book-search` (resolution retry contract, formatted-address source, multi-address dedup).

## 1. Component merge and candidate construction

- [x] 1.1 `app/src/main/java/com/naviveylin/data/AddressParser.kt` — add a helper that merges the structured fields with parsed `FORMATTED_ADDRESS` into the effective `(street, houseNumber, postalCode, city)`: structured values win, formatted text fills missing street/city, existing `normalizeStreetField` rules (embedded postal code is never the house number) stay authoritative (spec: address-book-search "Resolution from formatted address only", "Formatted address completes partial components", "Postal code in street field parses correctly").
- [x] 1.2 `app/src/main/java/com/naviveylin/data/AddressBookResolver.kt` — `buildQueries` uses the merged components and adds the full formatted address query (`street house PLZ city`) plus the component combinations the dialog sends; drop the bare-city collapse for an empty street name (spec: address-book-search "Search box and contact resolution agree").
- [x] 1.3 `app/src/test/java/com/naviveylin/data/AddressParserTest.kt` — cases for the merge helper: formatted-only, partial components, embedded postal code, comma and space layouts, `ß` normalization.

## 2. Evidence-driven candidate chain

- [x] 2.1 `AddressBookResolver.kt` — replace "first non-empty result set wins" (form search early return at the two call sites and the string-query loop) with "first candidate yielding at least one street-evidenced entry wins"; a street-less result set is a miss and the chain continues (spec: address-book-search "Street-less result does not abort the candidate chain", "Not found requires the whole chain to fail").
- [x] 2.2 `AddressBookResolver.kt` — keep the street-evidence gate and `AddressRanker` ranking as the acceptance filter, so free-text noise still cannot be auto-selected; ensure the empty-street case no longer bypasses the gate with a city-only query (spec: "Wrong-location free-text result not selected").
- [x] 2.3 `AddressBookResolver.kt` — per-candidate `Log.d` with query, result count and street-evidenced count on the existing `AddressBookResolver` tag (design Verification: logcat-only diagnosis of a future regression).
- [x] 2.4 `AddressBookResolverTest.kt` — new cases: street-less form result does not abort; street-less string result does not abort; a later candidate hit resolves; not-found only after the whole chain; formatted-only address resolves; candidate list contains the full formatted address query (spec: all `address-book-search` resolution scenarios).

## 3. Duplicate postal addresses

- [x] 3.1 `app/src/main/java/com/naviveylin/data/ContactsRepository.kt` — collapse identical postal addresses per contact before building `ContactAddressBookEntry`, comparing normalized components (trimmed, lower-cased) and the formatted text when components are absent (spec: address-book-search "Identical addresses from two accounts collapse").
- [x] 3.2 Extend `app/src/test/java/com/naviveylin/data/ContactsRepositoryTest.kt` (Robolectric; the file already existed — extended instead of created) — same address from two accounts collapses to one; two distinct addresses stay selectable; blank addresses still dropped; name lookup unchanged.
- [x] 3.3 `app/src/main/java/com/naviveylin/ui/addressbook/AddressBookSheet.kt` — picker item key becomes the normalized address string instead of `it.hashCode()`, so the list cannot supply duplicate keys (spec: "The address selection list SHALL render each distinct address exactly once"; design D6).
- [x] 3.4 Compose test for the picker (`AddressBookSheetComposeTest`, created): contact with two identical addresses resolves without the selection step and shows the address once in the list row; two distinct addresses render without throwing.

## 4. Verification — build and existing tests

- [x] 4.1 Run the `run-tests` skill: `:app` unit tests green, including all pre-existing `AddressBookResolverTest`, `AddressParserTest`, `MapCanvasViewModel*` and Compose tests; record the result XML counts (`app/build/test-results/testMobileDebugUnitTest/*.xml`). Known pre-existing failures unrelated to this change are listed in `TODO.md` — do not attribute them here.
  Evidence: full `./gradlew test` detached run -> BUILD SUCCESSFUL (3m 36s). Mobile debug variant XMLs: tests=894 failures=0 errors=0. New/changed classes: AddressParserTest 23, AddressBookResolverTest 26, ContactsRepositoryTest 9, AddressBookSheetComposeTest 4 — all 0 failures. (The three zoom failures recorded in TODO.md 14 are fixed in the tree since `smooth-decimal-auto-zoom`.)
- [x] 4.2 Run the `build-app` skill: `./gradlew :app:assembleMobileDebug` compiles with no warnings (spec: no build-warning policy in `openspec/config.yaml` apply guidance).
  Evidence: BUILD SUCCESSFUL in 29s; apk `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk` (124 MB); zero `^w:`/`warning:` lines in the build log.
- [x] 4.3 Same skill: `./gradlew :app:assembleAutomotiveDebug` compiles with no warnings.
  Evidence: same run, BUILD SUCCESSFUL; apk `app/build/outputs/apk/automotive/debug/app-automotive-debug.apk` (123 MB); zero warnings.
- [x] 4.4 Confirm every modified line is covered by a test that would fail on the pre-change code (revert-check the street-less abort case and the dedup case manually), and report coverage for `AddressBookResolver` / `ContactsRepository` via `./gradlew :koverHtmlReport`.
  Evidence (revert-check): with `accept()` restored to "any non-empty result set is a hit" and `ContactsRepository` dedup removed, 9 tests failed — AddressBookResolverTest: `street-less string result does not abort the chain`, `street-less form result does not abort the chain`, `not found only after every candidate was street-less`, `city-only free-text noise is never auto-selected`, `address without a street searches the formatted text when present`, `rank puts results matching street and city tokens first`; ContactsRepositoryTest: all three collapse cases. Files restored from backups afterwards; targeted rerun green again.
  Coverage: `:koverHtmlReport`/`:koverXmlReport` BUILD SUCCESSFUL, report at `build/reports/kover/html/index.html`. The per-class instruction counters are NOT usable as evidence here (ContactsRepository 5 covered/0 missed, AddressBookSheetKt 0 covered despite four passing Compose tests) — pre-existing Robolectric attribution gap documented in `TODO.md` §15; the revert-check above is the coverage evidence for this change.
- [x] 4.5 Check `ki_processing_failures.log` before starting; append every failed approach with timestamp and problem description.
  Evidence: the 2026-09-12 contact-test entry (`started()` helper) was applied; a new `## 2026-09-13 fix-contact-address-resolution` section records the four failures hit in this session (blank-query leak from a dropped loop guard, fake-provider label expectation, repeated partial-block edit, Kover attribution).

## 5. On-device verification

> Carries the on-device check delegated from `fix-address-lookup-accuracy` (its task 4.2), which is archived before this change. That change owns `location-search` / `auto-search` (search-box side); 5.1 and 5.5 are its regression evidence as well as this change's.

> **All 5.1–5.6 verified on-device 2026-09-17 (NRW map, emulator):** the failing contact resolves with the street-evidenced candidate logged under the `AddressBookResolver` tag; identical addresses from two synchronized books collapse to one; a formatted-only address resolves; an unknown address shows the transient banner and editing the query recovers; the unified search box and the contact flow resolve to the same object; the AA address book resolves the same contact to the same object.

- [x] 5.1 Emulator with the NRW map: the reported failing contact resolves; logcat `adb logcat -s AddressBookResolver NaviVeylin` shows which candidate hit and its street-evidenced count (spec: address-book-search "Address found"). Also carries the superseded change's contact-flow check (its task 4.2).
- [x] 5.2 Same build: contact whose address exists in two synchronized address books shows one address and resolves without the multi-address step (spec: "Identical addresses from two accounts collapse").
- [x] 5.3 Contact with only a formatted address resolves (spec: "Resolution from formatted address only").
- [x] 5.4 Unknown address still shows the transient banner; editing the query and picking another contact recover (spec: "Address not found", "Contact list stays reachable after failure", "Editing the query clears the not-found state").
- [x] 5.5 Search-box parity: type the same address in the unified search dialog and in the contact flow; both resolve to the same object kind and location (spec: "Search box and contact resolution agree"). Also carries the superseded change's search-box full-address check (its task 4.2, spec `location-search` / `auto-search`).
- [x] 5.6 Android Auto: the address book screen resolves the same contact to the same object (emulator/head unit or `:auto` harness) (spec: "Auto parity for resolution"; `guidelines/UI.md` §1, §6a).

## 6. Documentation and close-out

- [x] 6.1 Update `TODO.md` if any adjacent defect is found but not fixed here (apply guidance).
  Evidence: `TODO.md` §15 added — Kover/Robolectric coverage-attribution gap found while producing the coverage evidence.
- [x] 6.2 Update `AGENTS.md` / `README.md` only if the resolution contract described there changed; otherwise state in the change notes that no doc update was needed.
  Evidence: neither file mentions the address book, contacts, or the resolution contract (`grep -in "address book|addressbook|contact" AGENTS.md README.md` -> no matches), so no documentation change is required. The contract lives in the specs and in the new KDoc on `AddressBookResolver.accept` / `AddressParser.components` / `AddressParser.identityKey`.
- [x] 6.3 Archive order note for the archive step: archive `fix-address-lookup-accuracy` before this change (both modify `address-book-search`).
  Recorded here: `fix-address-lookup-accuracy` first (it established `partialMatch=true` and the search-box behaviour this change builds on), then this change. Its on-device check (task 4.2) was **delegated** to tasks 5.1/5.5 above rather than run, so it no longer holds this change open.
  **Done 2026-09-13**: `fix-address-lookup-accuracy` archived. Its spec deltas for `location-search`, `auto-search` and `address-book-search` are folded into `openspec/specs/`; the `address-book-search` requirement this change then overwrites is the superseded 8-scenario version.
- [x] 6.4 Log any repetitive on-device verification that could become a skill (apply guidance).
  Suggestion: a **revert-check** helper skill — snapshot the touched source files, revert the specific behaviour under test (accept-all / dedup-off), run the targeted test class to prove the new tests fail, restore, re-run to prove green. Done twice by hand in this session (and the pattern is required by apply/archive gates for every change). Secondary candidate: a small script that extracts `tests/failures/errors` totals per test-results directory, since the same awk one-liner was needed for both the suite verdict and the per-class evidence.
