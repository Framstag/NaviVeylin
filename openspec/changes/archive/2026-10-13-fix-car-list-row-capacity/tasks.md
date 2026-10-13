# Tasks

Parent spec: `car-list-row-limit` (MODIFIED — "A car list never presents more rows than the host displays",
"Items beyond the capacity stay reachable"). Order: reproduce, fix, falsify the new bound, gate,
device follow-up, bookkeeping. Task 2.4 closes the scenario-to-case map.

## 1. Reproduce the defect (red on HEAD)

- [x] 1.1 Write the capacity case for the car address book in a new
  `auto/src/test/java/com/naviveylin/auto/AddressBookScreenTest.kt`
  (`theAddressBookListFitsTheHostsCapacity`, harness following `FavoritesScreenTest`: mock
  `EntryPoints.get`, a fake host answering `getContentLimit`, the screen driven through
  `dispatchLifecycleEvent`). Spec: `car-list-row-limit` — The address book list fits the host's capacity.
  Verification: the red-on-HEAD run is retained at
  `openspec/changes/fix-car-list-row-capacity/evidence/TEST-AddressBookScreenTest-red-on-HEAD.xml` —
  `tests="2" skipped="0" failures="2" errors="0"`, `timestamp="2026-10-13T19:51:18.268Z"`, the case failing
  as `expected:<[Alpha1, Alpha2, Alpha3, Alpha4, Alpha5, Alpha6, More (2)]> but
  was:<[Alpha1, Alpha2, Alpha3, Alpha4, Alpha5, Alpha6, Alpha7, Bob]>`; command
  `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.AddressBookScreenTest" -PforceTests --rerun-tasks`.
- [x] 1.2 Write the page case in the same file (`thePageRowCarriesTheSearchTextToTheNextPage`): after the
  search text `Alpha`, page 0 carries six matching rows and `More (1)`, and the row tap opens the page that
  begins with `Alpha7`. Spec: `car-list-row-limit` — The address-book page continues the list the driver
  was shown. Verification: it is red in the same retained XML, failing as
  `expected:<[… Alpha6, More (1)]> but was:<[… Alpha6, Alpha7]>`.

## 2. Cap the address book list (`AddressBookScreen`)

- [x] 2.1 Add the capacity field (started at `CarListCapacityReader.LIBRARY_DEFAULT`) and the read on the
  screen's own scope off the host thread (`withContext(Dispatchers.Default) { CarListCapacityReader.read(carContext) }`,
  then publish and `invalidate()`). Spec: `car-list-row-limit` — The address book list fits the host's
  capacity; the capacity is taken from the host, and a host that cannot be asked uses the library's
  default. Verification: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.AddressBookScreenTest" -PforceTests --rerun-tasks`
  is green for `theAddressBookListFitsTheHostsCapacity`, which also carries the requirement's host-source
  half ("SHALL take that capacity from the host rather than from a fixed constant"): the case asserts one
  capacity query per screen start and that three template builds add none.
  `theTemplateBuildPerformsNoHostQuery` in `FavoritesScreenTest` still passes (the read did not move into a
  template build), carried by `evidence/auto-module-suite/TEST-com.naviveylin.auto.FavoritesScreenTest.xml`
  in the module run quoted in 4.1.
  — ran 2026-10-13 (remediation round): the read is `AddressBookScreen.kt:97` (`readRowCapacity`, joined the
  screen's own scope in `init`, `withContext(Dispatchers.Default)`) feeding the field at `:82`; the case
  now also asserts the host-source half — one capacity query per screen start, and none added by three
  template builds — and passed (`tests="2" skipped="0" failures="0" errors="0"`,
  `timestamp="2026-10-13T20:28:58.180Z"`, `BUILD SUCCESSFUL in 19s`, 59 actionable tasks: 59 executed,
  forced), retained as `evidence/TEST-AddressBookScreenTest-green-focused.xml`.
  `FavoritesScreenTest.theTemplateBuildPerformsNoHostQuery` stayed green in the module run quoted in 4.1,
  carried by `evidence/auto-module-suite/TEST-com.naviveylin.auto.FavoritesScreenTest.xml`
  (`tests="37" skipped="0" failures="0" errors="0"`, `timestamp="2026-10-13T20:26:33.946Z"`).
  Revert-check for this task's new assertion (one mutation): with the host read at `:99` replaced by
  `CarListCapacityReader.LIBRARY_DEFAULT`, `theAddressBookListFitsTheHostsCapacity` failed at it
  (`java.lang.AssertionError: one capacity query per screen start expected:<1> but was:<0>`, XML
  `tests="2" skipped="0" failures="1" errors="0"`, `timestamp="2026-10-13T20:28:31.799Z"`), retained as
  `evidence/TEST-AddressBookScreenTest-revert-check-host-source-mutation.xml`; the line was restored
  exactly and `grep -rn "REVERT-CHECK MUTATION" auto/src` returned nothing.
- [x] 2.2 Add `page: Int = 0` and slice the filtered contacts with
  `FavoritesScreenMapper.page(filteredContacts, capacity = rowCapacity, page = page)`; when the slice holds
  more, add the clickable final row (`R.string.more_items`) that pushes another `AddressBookScreen` with
  `page + 1`. Spec: `car-list-row-limit` — The final row opens the next page. The push is also what keeps
  the restated baseline scenario "Back navigation returns to the previous page" true (design D3: the host
  keeps the opening page on its own stack); no case in this change asserts that mechanism, so the delta
  names none for it — stated in 2.4. Verification: `theAddressBookListFitsTheHostsCapacity` and
  `thePageRowCarriesTheSearchTextToTheNextPage` green; the pushed screen is a `AddressBookScreen` the fake
  `ScreenManager` recorded being pushed (not the same instance mutated).
  — ran 2026-10-13: the slice is `AddressBookScreen.kt:168`, the final row `:193-205`, pushed through
  `armScreenPush` with `page = currentPage + 1`; both cases passed in the green forced run quoted in 2.1
  (`tests="2" failures="0" errors="0"`, `timestamp="2026-10-13T20:28:58.180Z"`).
- [x] 2.3 Carry the active search text into the page (`initialQuery`, defaulted to `""`), filter the loaded
  contacts by it, prefill the field only for a non-blank carried text (`setInitialSearchText`, the mechanism
  `SearchScreen.kt:103-105` uses), and return to page 0 when the delivered search text differs from the
  current one. Spec: `car-list-row-limit` — The address-book page continues the list the driver was shown;
  Every item is reachable exactly once. Verification:
  `thePageRowCarriesTheSearchTextToTheNextPage` green: page 1 of the filtered list carries `Alpha1`–`Alpha6`
  and `More (1)`, and its page begins with `Alpha7`, not with an unfiltered contact.
  — ran 2026-10-13: `initialQuery` at `AddressBookScreen.kt:52` is carried by the page push (`:202`), prefills
  the field through `setInitialSearchText` only for a non-blank carried text (`:137`), and a changed search text
  returns to page 0 (`:256`); the case drives the field through the template's search delegate and passed in
  the green forced run quoted in 2.1.
- [x] 2.4 Confirm the delta's scenario-to-case map and state what the restated baseline keeps: every
  scenario in `specs/car-list-row-limit/spec.md` names exactly one case (the two new address-book cases and
  the existing `FavoritesScreenTest`/`FavoritesScreenMapperTest` cases) — except the restated baseline
  scenario "Back navigation returns to the previous page", which names none: the push of 2.2 delivers the
  mechanism, but no case asserts it, so the delta carries no `Case:` line for it rather than a false one,
  and the scenario keeps the baseline's claim strength from `car-favorites-group-level`. The
  favourites-side scenarios this fix does not re-deliver keep the baseline's claim strength too.
  Verification: `openspec validate fix-car-list-row-capacity --strict` exits 0, and each `**Case**:` line
  names a case that exists in the named module's test sources (grep the case name in `auto/src/test`).
  — ran 2026-10-13 (remediation round): `openspec validate fix-car-list-row-capacity --strict` printed "Change
  'fix-car-list-row-capacity' is valid" (exit 0) with the back-navigation scenario carrying no `Case:` line,
  and all nine remaining `**Case**:` lines resolve to a `fun <case>(` in
  `auto/src/test/java/com/naviveylin/auto/` — eight distinct cases: `FavoritesScreenTest` for the four
  capacity scenarios and the final-row scenario, `FavoritesScreenMapperTest` for the partition and
  last-page scenarios, `AddressBookScreenTest` for the two address-book scenarios.

## 3. Falsify the bound the fix introduces

- [x] 3.1 Revert-check (`.pi/skills/revert-check`): mutate the single production line that produces the
  bound — in `AddressBookScreen.buildList()` pass `capacity = filteredContacts.size` to
  `FavoritesScreenMapper.page` instead of `capacity = rowCapacity` — and confirm
  `theAddressBookListFitsTheHostsCapacity` fails for the right reason (the uncapped list:
  `… Alpha7, Bob` with no `More (2)` row). Restore the line, confirm the working tree carries no
  `REVERT-CHECK MUTATION` marker and `git diff --stat auto/src/main` lists only this change's
  `AddressBookScreen.kt`, then re-run the focused case green.
  Verification: the mutation run's XML tally and failure message, the clean restore, and the green re-run
  are recorded in this task's note; the case's own tally in the module gate is quoted from
  `tests="2" skipped="0" failures="0" errors="0"`
  (`evidence/auto-module-suite/TEST-com.naviveylin.auto.AddressBookScreenTest.xml`,
  `timestamp="2026-10-13T20:26:24.267Z"`).
  — ran 2026-10-13 (remediation round, against the updated test file): with `capacity = filteredContacts.size`
  at `AddressBookScreen.kt:168` both cases the guard protects failed at the bound's assertion
  (`theAddressBookListFitsTheHostsCapacity`: `expected:<[Alpha1, Alpha2, Alpha3, Alpha4, Alpha5, Alpha6, More (2)]> but was:<[Alpha1, Alpha2, Alpha3, Alpha4, Alpha5, Alpha6, Alpha7, Bob]>`;
  `thePageRowCarriesTheSearchTextToTheNextPage`: `expected:<[… Alpha6, More (1)]> but was:<[… Alpha6, Alpha7]>`,
  XML `tests="2" skipped="0" failures="2" errors="0"`, `timestamp="2026-10-13T20:27:13.507Z"`), retained as
  `evidence/TEST-AddressBookScreenTest-revert-check-mutation.xml`; the line was restored exactly,
  `grep -rn "REVERT-CHECK MUTATION" auto/src` returned nothing and `git diff --stat auto/src/main` lists only
  this change's `AddressBookScreen.kt`; the forced re-run after the restore was green (`tests="2" failures="0"
  errors="0"`, `timestamp="2026-10-13T20:28:58.180Z"`, 59 actionable tasks: 59 executed). This mutation leaves
  the host read in place, so the host-source assertion of 2.1 still holds under it; that assertion carries its
  own single mutation, recorded in 2.1.

## 4. Gate (one forced run for this change shape)

- [x] 4.1 Run the forced gate for this change's scope — the changed module's own suite,
  `./gradlew :auto:testDebugUnitTest -PforceTests --rerun-tasks` (`guidelines/Build.md` §2, the scope block:
  a production change runs the forced gate, scoped to what it touched), with
  `.pi/skills/fix-loop/scripts/evidence-check.sh` in live mode green before it. The whole-suite
  `./gradlew test -PforceTests --rerun-tasks` is **excluded**, not skipped silently: its `:app` half carries 15
  pre-existing `AppNotIdleException: Compose did not get idle … in 60 SECONDS` failures in
  `BasemapSectionComposeTest` (2) and `MapManagerSourceSelectorComposeTest` (13) — a module this change does
  not touch — and the run was killed before any `BUILD` marker, so it is no gate result either way; the
  attribution is carried by `evidence/gate-full-suite-app-compose-preexisting.txt`.
  Verification: every XML cited is copied into `evidence/` (never a build directory or `/tmp`); the module
  run's tally is quoted from `evidence/gate-auto-module-suite.txt` and from the run's copied XMLs under
  `evidence/auto-module-suite/`.
  — ran 2026-10-13T20:26:02Z–20:26:44Z (remediation round, on the tree the updated test file and the
  restored production file leave): `BUILD SUCCESSFUL in 42s`, `59 actionable tasks: 59 executed`, no
  `FAILED` line; the run's 81 XMLs sum to 81 classes, 840 cases, 0 skipped, 0 failures, 0 errors (XML
  timestamps `2026-10-13T20:26:15.910Z`–`2026-10-13T20:26:43.511Z`); `AddressBookScreenTest`
  `tests="2" skipped="0" failures="0" errors="0"` at `timestamp="2026-10-13T20:26:24.267Z"`, and
  `FavoritesScreenTest` `tests="37"`, `FavoritesScreenMapperTest` `tests="9"`,
  `TemplateFaultIsolationTest` `tests="21"`, `SearchScreenTest` `tests="12"`, `RootScreenTest` `tests="2"`,
  all `failures="0"`. The run log is `.pi/bugfix-loop/gate-auto-module-2026-10-13-round2.log`. The known
  `MapCanvasViewModelModeTest` flake (`TODO.md` §148) did not occur in this run
  (the class is `:app`-side and the excluded suite is where it lives), so nothing was attributed as a flake.

## 5. Device follow-up (pending — no device attached in this session)

- [ ] 5.1 On the AAOS emulator, open the car address book with more contacts holding a postal address than
  the host's reported capacity and read (a) the coordinate-free line
  `Diag/SCREEN: list capacity N rows (host, car API level M)`, (b) the `More (n)` row on the list and the
  page it opens, (c) that the host renders the page row itself (the (capacity + 1)-th row, design
  "Risks"). Verification: the recorded logcat lines and the counts. State explicitly that no device is
  attached if that is still the case — a stationary emulator is enough for this property, but a head unit
  or projection host is not available.
  — **pending 2026-10-13**: no device was attached in this session (`adb devices` was not run; the session
  held no emulator), so the capacity line and the page row were never observed on a host. Left `[ ]`, and
  named as the change's device gap in the report rather than claimed.

## 6. Bookkeeping

- [ ] 6.1 `TODO.md`: §177 → `**status:** fixed-by \`fix-car-list-row-capacity\`` (the entry is kept), and
  §178, §179, §180 stay `open` with their reasons recorded — §178 the same seam on its own screen, §179 one
  combined decision with the search's candidate count, §180 a `PaneTemplate`, i.e. a different mechanism.
  This task is performed when the change is archived (the loop's phase F), because the status names the
  archived change; it is left unchecked by the apply pass, which does not archive.
  Verification: `grep -n "^## 177\." -A 1 TODO.md` shows the new metadata line; the `##` count still equals
  the `**id:**` count and no id collides.
- [x] 6.2 Guideline check: `guidelines/UI.md` §3's `ListTemplate` bullet already states the capacity rule
  screen-generically and names the shipped implementation (`CarListCapacityReader` +
  `FavoritesScreenMapper.page`, change `car-favorites-group-level`), so no sentence contradicts this fix and
  no edit is owed. Verification: the bullet is read and the conclusion stated in the report; edit it only
  if a sentence contradicts the applied behaviour.
  — ran 2026-10-13: `guidelines/UI.md:295-296` states the rule for any car list screen ("A car list screen
  MUST ask the host for the capacity and present **at most** that many rows, ending with its own page row")
  and names `CarListCapacityReader` + `FavoritesScreenMapper.page`; the address book now obeys both halves,
  so no sentence contradicts it and no edit was made. `grep -n "address book" guidelines/UI.md` finds only
  the phone search-surface notes (`:501`, `:504`, `:575`), none of which describes this list's row count.

## Workflow follow-up

- Phase E (independent review) and phase F (archive, spec merge, `TODO.md` bookkeeping) are the fix-loop's
  own steps and are not tracked here; this change is left unarchived and uncommitted by the apply pass.
