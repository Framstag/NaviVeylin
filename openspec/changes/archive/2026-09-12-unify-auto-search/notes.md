# Change Notes: unify-auto-search

## Spike: host rendering of empty-query item list (task 1.1)

**Status: PENDING — no emulator/head unit available on the build machine.**

`adb` and the Android emulator are not installed on this machine, so the
on-device spike (does the car host render a `SearchTemplate` item list on an
empty query, and are suggestion rows obscured by the keyboard?) could not be
executed. The implementation proceeded anyway: the change is additive, the
car-app API allows `setItemList` with an empty query, and Google Maps shows
recents this way (design R1 mitigation).

**If a host refuses to render the item list on an empty query**, stop and
revisit the `auto-search` spec delta with the user before shipping — the
fallback is to show mode rows only in the no-results state (spec change
required).

## On-device verification (task 7.2)

**Status: PENDING — same reason.** Verify on emulator/head unit:
- empty query shows mode rows + history
- contacts row appears only with `READ_CONTACTS`
- typing searches places
- no-results keeps mode rows
- history tap prefills and searches
- back from POI/contacts search restores the previous query and results
  (screen-stack state preservation)

## Verified on build machine

- `:auto:testDebugUnitTest` — full suite green (incl. new `SearchScreenTest`,
  extended `SearchScreenMapperTest`, `GermanRenderingTest` additions,
  `GermanTranslationCompletenessTest`).
- `:auto:assembleDebug` + `:app:assembleMobileDebug` — BUILD SUCCESSFUL, no
  code warnings.
