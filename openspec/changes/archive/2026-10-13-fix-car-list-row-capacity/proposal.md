# Proposal — fix-car-list-row-capacity

## Why

Root cause: `AddressBookScreen` builds one row per contact that
`AddressBookContactsProvider.contactsWithAddresses()` returns and never asks the connected host for its
list capacity, so its single `ItemList` holds one row per contact with an address — however many the
device's address book holds — and the Car App Library's build contract has the host ignore every row past
its limit with no signal.

Evidence:    `com.naviveylin.auto.AddressBookScreenTest#theAddressBookListFitsTheHostsCapacity` fails on HEAD
— XML `tests="2" skipped="0" failures="2" errors="0"`, `timestamp="2026-10-13T19:51:18.268Z"`, failure
`java.lang.AssertionError: expected:<[Alpha1, Alpha2, Alpha3, Alpha4, Alpha5, Alpha6, More (2)]> but
was:<[Alpha1, Alpha2, Alpha3, Alpha4, Alpha5, Alpha6, Alpha7, Bob]>` — retained as
`evidence/TEST-AddressBookScreenTest-red-on-HEAD.xml`. The same run's second case,
`thePageRowCarriesTheSearchTextToTheNextPage`, fails as `expected:<[… Alpha6, More (1)]> but
was:<[… Alpha6, Alpha7]>`.

Repro:       `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.AddressBookScreenTest" -PforceTests --rerun-tasks`

Spec:        `car-list-row-limit` / A car list never presents more rows than the host displays
(added by `car-favorites-group-level`; the requirement text already reads "A car list screen", so the
address book was in breach of it while the change applied it to the favourites lists only)
             Guideline: `guidelines/UI.md` §3 "Car-host template constraints (platform knowledge)", the
`ListTemplate` bullet — "A car list screen MUST ask the host for the capacity and present **at most** that
many rows, ending with its own page row, because rows past the limit disappear with no signal".

Authority — the tree today:

- **The screen renders every contact.** `auto/src/main/java/com/naviveylin/auto/AddressBookScreen.kt:113-121`
  adds one `Row` per `filteredContacts` entry into one `ItemList.Builder`; the file contains no reference
  to `CarListCapacityReader` and no page parameter.
- **The shipped seam exists and is screen-generic.** `auto/src/main/java/com/naviveylin/auto/CarListCapacityReader.kt:50`
  (`read(carContext)`, `LIBRARY_DEFAULT = 6` at `:96`) and
  `auto/src/main/java/com/naviveylin/auto/FavoritesScreenMapper.kt:49` (`page(rows, capacity, page)`) are
  pure functions of the host's answer and an ordered sequence; `FavoritesScreen.kt:227` applies them, adds
  the final row at `:238` with `pageRow` (`:343-344`, wording `R.string.more_items`,
  `auto/src/main/res/values/strings.xml:66`) and re-reads the capacity on its own started period (`:140`).
- **The capacity facts.** The AAOS templates host measured **100 rows at car API level 8**
  (`car-favorites-group-level` task 1.1); the library's packaged default and this app's fallback is **6**;
  the library's contract is that the host ignores every item over the limit (`guidelines/UI.md` §3).

## What Changes

- **The car address book caps its list at the host's row capacity and pages the remainder.** The screen
  asks the connected host for its `CONTENT_LIMIT_TYPE_LIST` capacity once per screen, off the host thread,
  presents at most that many contact rows and ends with the clickable `More (n)` row that opens the next
  slice of the same list — the shipped `CarListCapacityReader` + `FavoritesScreenMapper.page` + page-row
  seam, the same one `FavoritesScreen` applies to its three levels. Nothing new is introduced: this is the
  existing capability `car-list-row-limit` applied to one more car list.
- **The page carries the active search text.** The address book's rows are the *filtered* contacts
  (`AddressBookScreenMapper.filterContacts`), so the page the row opens continues that same sequence: the
  push carries the current search text, and a changed search text resets to the first page. Without it a
  page would slice a different list than the one the driver was looking at — the partition property the
  capability states ("Every item is reachable exactly once") would break.
- **The screen keeps its shape.** Still a `SearchTemplate` with the searching field, still one file, no
  call-site change: `RootScreen.kt:147` and `SearchScreen.kt:265` construct it without the page argument,
  whose default is page 0.
- **Not this change:** the three sibling entries of `TODO.md` §177 stay open with their reasons — §178
  (`SearchHistoryScreen`) is the same seam but a separate screen and is taken as its own iteration; §179
  (`PoiResultsScreen`) needs one combined decision (the search's candidate count and the row cap are one
  design decision, as its own entry says); §180 (`DiagnosticsScreen`) is a `PaneTemplate`, i.e. a different
  mechanism, and its entry asks for an extra shown-of-total wording. Applying the capability at those
  sites would be three further deltas, not this one.
- **Guideline and spec**: `guidelines/UI.md` §3 states the rule screen-generically and already names the
  shipped implementation; nothing in it contradicts the fix, so no guideline edit is owed (stated as a
  task's verification, not assumed). `car-list-row-limit` gains the address-book scenarios it was already
  claimed to cover.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `car-list-row-limit`: the two existing requirements are restated with the scenarios the address book now
  satisfies, and each gains the address-book scenario naming its case. The requirements' claims are
  unchanged — the defect is the address-book screen, not the contract. The favourites-side scenarios the
  fix does not re-deliver keep the baseline's claim strength from `car-favorites-group-level` and are
  exercised by their existing cases.

## Impact

- `auto/src/main/java/com/naviveylin/auto/AddressBookScreen.kt` — two additive constructor parameters
  (`page`, `initialQuery`, both defaulted) and the capacity read, page slice and page row (one file).
- `auto/src/test/java/com/naviveylin/auto/AddressBookScreenTest.kt` — new; the red case and the page case.
- `openspec/specs/car-list-row-limit/spec.md` — via this change's delta; `TODO.md` §177 → `fixed-by`.
- No change to `:core`, `:app`, resources, JNI/native code, the manifest or the AAOS flavour; no new
  dependency, no new string (the `More (n)` wording exists in both locales).
- Additive and non-breaking: every existing constructor call site keeps its behaviour (page 0, no search
  text). Rollback: revert the one production file and drop the new test file; the delta spec and the
  `TODO.md` status ride with the change.
