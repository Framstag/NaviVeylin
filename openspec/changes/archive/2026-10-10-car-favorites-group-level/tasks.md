# Tasks

Parent specs: `car-list-row-limit` (NEW — "A car list never presents more rows than the host displays",
"Items beyond the capacity stay reachable"), `auto-favorites` (MODIFIED — "ListTemplate displays
favorites", "Favorites grouped by category"; ADDED — "The all-favorites mode lists groups first"),
`fav-management-ui` (MODIFIED — "Favorites management stays on the phone").
Order: measure, then the capability, then the levels, then evidence. Task 8.1 closes the scenario-to-case
map.

## 1. Measure and verify the platform surface first

- [x] 1.1 Verify the `ConstraintManager` surface against car-app 1.7.0 and measure the capacity: the exact
  accessor for the host's car API level (design D2 assumes `carContext.carAppApiLevel`), whether
  `getContentLimit(CONTENT_LIMIT_TYPE_LIST)` may be called off the main thread, what an API-1 host does
  when it is called, and the value the fallback takes from the library's own default. Verification: run
  on the AAOS emulator and on one projection host and record in this task's note — the reported number,
  with and without sections, the accessor name as resolved by the compiler, and the fallback value read
  from the library. If no host is available, record "measurement not taken" and let tasks 2.1-2.3 use the
  fallback, so nothing downstream depends on the unmeasured number (design D2's risks).
  - **Surface verified 2026-10-10** against the car-app 1.7.0 sources and AAR (`androidx.car.app:app:1.7.0`,
    unpacked into `build/scratch-carapp/`, a gitignored local scratch):
    - the accessor is `CarContext.getCarAppApiLevel()` — public, `@CarAppApiLevel`, documented to throw
      `IllegalStateException` before the handshake, so D2's guard must also catch that (it does);
    - the query is `carContext.getCarService(ConstraintManager::class.java)
      .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)`. `ConstraintManager` is
      `@RequiresCarApi(2)` while the manifest declares `minCarApiLevel = 1`
      (`app/src/main/AndroidManifest.xml`), and `CarContext`'s own doc says no element above the reported
      level may be used, so the reader guards on the level instead of relying on the host;
    - the service factory is registered unconditionally
      (`CarContext.java:736 mManagers.addFactory(ConstraintManager.class, …)`), so the *lookup* itself
      cannot throw on an API-1 host — the level guard, not a `try`, is what keeps the API-1 path correct;
    - **off the main thread is legal**: `getContentLimit` → `HostDispatcher.dispatchForResult` →
      `RemoteUtils.dispatchCallToHostForResult` performs the binder call with no
      `ThreadUtils.checkMainThread` (the only main-thread checks in `HostDispatcher` are on
      `setCarHost`/`resetHosts`), so design D2's `backgroundDispatcher` read is valid and the
      `onStart`-on-main fallback is not needed;
    - the fallback is the library's `androidx.car.app.R.integer.content_limit_list` =
      **6** (AAR `res/values/values.xml`), which is also what `ConstraintManager.getContentLimit`
      itself returns when its remote call fails — the screen and the library therefore agree on the floor;
    - "with and without sections" is moot for the car favourites lists: this change removes the sections,
      so no header counts against the budget anywhere on the car favourites path.
  - **On-device capacity measurement: TAKEN — the AAOS host reports 100 rows at car API level 8** (this
    corrects the proposal's premise). Method on `emulator-5554` (`Automotive_Distant_Display_with_Google_Play`,
    x86_64, automotive debug build of this tree): `am force-stop` → `am start -n
    com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity` → the car UI *is* visible on the
    AVD's display 0 (it was not in the first attempt, which found the session on the map screen) → the row
    "Alle Favoriten" was located with `.pi/logs/ocr.sh` (tesseract on `screencap`, the method
    `fix-car-favorites-loading-stall` used) and tapped by coordinate → the reader logged, twice (once per
    screen start, the group level and then the opened group):
    `Diag/SCREEN: list capacity 100 rows (host, car API level 8)`.
    So the number is the **host's own**, 100 for this templates host, and the library's **6** is only the
    floor it documents and the value `ConstraintManager.getContentLimit` falls back to when its remote call
    fails — the proposal's "the AAOS emulator answers 6" was an assumption read off that default and is
    **refuted**. Captures and OCR output: `.pi/logs/car-capacity/`. No **projection** host exists in this
    session (the DHU binary is installed, the phone that would project is not), so the number is proven on
    the AAOS emulator only; a projection host's number is an open fact, and the code does not depend on either
    (it reads whatever the host answers, and the fallback is unit-tested).

## 2. The row capacity (spec: `car-list-row-limit`)

- [x] 2.1 Add the page arithmetic to `auto/src/main/java/com/naviveylin/auto/FavoritesScreenMapper.kt` as a
  pure function: given the ordered rows, the capacity, and the requested page, return that page's rows,
  the count of items left, and whether a further page exists. Verification: `FavoritesScreenMapperTest`
  cases `aListAtTheCapacityCarriesNoPageRow` ("The list fits the host's capacity"),
  `theLastPageCarriesNoPageRow` ("The last page offers no further row"), and
  `thePagesPartitionTheSequence` ("Every item is reachable exactly once") — the last one asserts the pages
  are disjoint and their union is the whole sequence.
- [x] 2.2 Read the capacity once per screen start on the screen's `backgroundDispatcher`, guarded for a
  host below car API 2 and for a failing query, cache it in a field, and have the template builder read
  only that field (design D2). Verification: cases `aLargerHostCapacityShowsMoreRows` ("The capacity
  follows the host"), `anUnanswerableHostFallsBackToTheLibraryDefault` ("A host that cannot be asked uses
  the library's default") and `eachScreenReadsTheCapacityForItself` ("Capacity is re-read per screen"),
  plus a case asserting the template build performs no host query (the fake reports a query count of 1
  per start).

## 3. The two levels (spec: `auto-favorites`)

- [x] 3.1 Give `FavoritesScreen` a `groupName: String? = null` mode beside `starredOnly` (design D3) and
  render the all-favorites level as one row per non-empty group — group name and its favourite count, in
  the stored group order, no section headers. Verification: `FavoritesScreenTest` cases
  `theAllFavoritesModeStartsAtTheGroupLevel` ("Favorites list shown"),
  `groupRowsFollowTheStoredGroupOrder` ("Group rows follow the stored group order", including the in-place
  reorder), `groupRowsShowTheNameAndCount` ("Group headers shown") and `anEmptyGroupIsNotListedOnTheCar`
  ("A group holding no favorites is not listed"); replace `theAllFavoritesModeStillUsesGroupSections`
  (`:367`) rather than deleting it silently.
- [x] 3.2 Render a group's own level: that group's favourites in the stored favourite order, selection
  starting the destination-picker flow, back navigation returning to the group level, and the page row
  from 2.1 when the group exceeds the capacity. Verification: cases `aGroupRowOpensTheGroupsFavorites`
  ("A group row opens that group's favorites") and `thePageRowOpensTheNextSlice` ("The final row opens the
  next page"); a `ClickPathDefersAndConfinesTest` case asserts the push leaves the click callback (its
  existing shape, `:118`).
- [x] 3.3 Apply the same cap to the starred mode without changing its order or its missing headers.
  Verification: the existing `starredModeRendersTheStoredStarredOrderAsOneList` and
  `aStarredReorderUpdatesTheListInPlace` stay green, plus a case where the starred order exceeds the
  capacity and the page row reaches the rest.
- [x] 3.4 Add the new wording to `auto/src/main/res/values/strings.xml` and
  `auto/src/main/res/values-de/strings.xml`: the page row with its remaining count. The group row's count
  uses the wording the phone group card already uses. Verification: `GermanTranslationCompletenessTest`
  green and no `HardcodedText` violation in the two edited Kotlin files.
  - **Done 2026-10-10.** `auto/src/main/res/values/strings.xml` and `values-de/strings.xml` carry the page
    row (`more_items`, "More (%1$d)" / "Mehr (%1$d)"), and the group row's count comes from a new
    `plurals/favorite_count` pair in `values/plurals.xml` + `values-de/plurals.xml` with exactly the phone
    card's sentences (`%d favorite`/`%d favorites`, `%d Favorit`/`%d Favoriten`), so the two surfaces cannot
    drift. `GermanTranslationCompletenessTest` gained `everyEnglishPluralHasGermanTranslation`, because the
    plurals live in a second file pair the existing case never looked at. Verification run:
    `./gradlew :app:checkNoCoordinatesInLogs :app:checkHardcodedStrings :auto:lintDebug` →
    **BUILD SUCCESSFUL in 2m 8s**, no `HardcodedText` finding (the `:auto` module elevates it to an error via
    its own `lint.xml`), no coordinate finding in the new diagnostics line; `GermanTranslationCompletenessTest`
    2/2 green in the gate of 4.2.

## 4. Build and suite

- [x] 4.1 Compile both flavours with the warning-free requirement: `./gradlew :auto:assembleDebug
  :app:assembleMobileDebug :app:assembleAutomotiveDebug` (via the `build-app` skill) — no errors and no
  new `w:` lines from the touched files (spec: `build-test-gate`).
  - **Done 2026-10-10:** `./gradlew :auto:assembleDebug :app:assembleMobileDebug :app:assembleAutomotiveDebug
    -Pandroid.injected.build.abi=x86_64` → **BUILD SUCCESSFUL in 41s**, and `grep -c '^w: '` over the whole
    log is **0** (no Kotlin/Java warning at all, from any file), no `e:` line. The automotive debug APK
    (`app/build/intermediates/apk/automotive/debug/app-automotive-debug.apk`, newer than every touched
    source) carries the x86_64 native libraries, which is the build the device runs of tasks 1.1 and 6.1
    installed with `adb install -r -t`. The full gate of 4.2 recompiled every module afterwards.
- [x] 4.2 Run the focused suites for the touched modules, then the full both-flavour gate once:
  `:auto:testDebugUnitTest`, `:app:testMobileDebugUnitTest`, `:app:testAutomotiveDebugUnitTest`,
  `:core:testDebugUnitTest`, then `./gradlew test` (via the `run-tests` skill). Verification: every
  pre-existing case in `FavoritesScreenTest`, `FavoritesScreenMapperTest`,
  `FavoritesScreenObservationsTest`, `GermanTranslationCompletenessTest` and
  `ClickPathDefersAndConfinesTest` is green.
  - **Done 2026-10-10: `./gradlew test -PforceTests --no-build-cache` → BUILD SUCCESSFUL in 8m 38s**,
    started 14:10:30Z, **5 107 cases, 0 failures, 0 errors** — `:app` mobile `tests="1877" failures="0"`,
    `:app` automotive `1877/0`, `:core` `532/0`, `:auto` `821/0` (820 before this change's mapper case).
    Freshness: every module's newest suite timestamp is later than the run start (core 14:11:11Z, auto
    14:12:39Z, automotive 14:14:59Z, mobile 14:19:04Z) and the log holds **0** `FROM-CACHE` lines, so all
    four suites executed. The focused runs before it: `:auto:testDebugUnitTest` 820/0 (the first full
    module run) and 46 case-level runs for both revert-checks (tasks 5.1, 5.2), plus
    `:app:testMobileDebugUnitTest --tests 'com.naviveylin.ui.favorites.*'` green after the phone-side
    assertion of §7.2's scenario was added.
  - **One flake, attributed and not this change's:** the *first* gate attempt failed one case,
    `:app` mobile `SettingsStorageSourceTest.unknownStoredSourceFallsBackToBuiltIn` — "the diagnostics line
    carries no coordinate", i.e. the file contained the substring `52.`, which any `…52.…` number matches
    (e.g. a `152.7ms` duration). It passes alone (`35s`, `BUILD SUCCESSFUL`), passes in a one-JVM pair with
    `DiagnosticsCoordinatePrivacyTest` (`37s`, green — the class that logs coordinates on purpose), and did
    not reproduce in this re-run. `DiagnosticsLog` is process-wide with one test-chosen file target per
    class, so a buffered line from an earlier class can drain into a later class's file — a cross-test
    fragility of the assertion, not a coordinate in the code this change adds (no new `DiagnosticsLog` line
    carries a number with that shape: the one added line is `list capacity 100 rows (host, car API level 8)`).
    Filed as **TODO.md §181** for the aggregate-flake work (§148) rather than fixed here.
  - **Scope of the tree this gate ran on:** the working tree also carries another change's uncommitted work
    (`auto/FavoritesScreenObservations.kt`, `auto/MapScreenObservations.kt`, `auto/DetailsScreen*.kt`,
    `auto/MapScreen.kt`, `core/AutoFavoritesProvider.kt`, `app/…/AutoFavoritesProviderImpl.kt`,
    `app/…/FavoriteRepository.kt`, `guidelines/Design.md` — `fix-car-favorites-loading-stall`, whose
    `backgroundDispatcher` parameter this change's screen passes on), so the green gate covers both; nothing
    in this change's diff depends on that work beyond the parameter it already had.

## 5. Falsification

- [x] 5.1 Revert-check the capacity bound (the mutation: drop the capacity from the page arithmetic, so
  every row is rendered). Verification (via the `revert-check` skill): with the mutation,
  `aListAtTheCapacityCarriesNoPageRow` and the screen-level cap case MUST fail; restore; re-run forced
  green with `-PforceTests --no-build-cache`; record the failing cases, the mutating diff and the restored
  run in this task's note.
  - **Ran 2026-10-10.** Single mutation, in `FavoritesScreenMapper.page`:
    `val perPage = if (capacity < 1) 1 else capacity` → `val perPage = rows.size.coerceAtLeast(1)`
    (marker comment `// REVERT-CHECK MUTATION (task 5.1): capacity dropped`), so no page is ever capped.
    `./gradlew :auto:testDebugUnitTest -PforceTests --no-build-cache --tests FavoritesScreenMapperTest
    --tests FavoritesScreenTest` → **46 tests, 7 failed, BUILD FAILED in 44s**, with the messages:
    `aPageNeverCarriesMoreRowsThanTheCapacity` "page 0 at capacity 1 carried 7 rows: [row1, …, row7]";
    `thePageRowOpensTheNextSlice` expected `[Group1, Group2, More (1)]` but was `[Group1, Group2, Group3]`
    (the screen-level cap case); `aLargerHostCapacityShowsMoreRows` and
    `anUnanswerableHostFallsBackToTheLibraryDefault` expected six group rows plus `More (2)` but got all
    eight; `starredOrderBeyondTheCapacityPagesThroughTheRest`; `theLastPageCarriesNoPageRow`; and
    `anUnusableCapacityStillTerminates` (`[row1]` vs all seven).
  - **One honest correction to this task's expectation:** `aListAtTheCapacityCarriesNoPageRow` stays
    green under this mutation and cannot discriminate it — its list holds *exactly* the capacity, so
    dropping the capacity is invisible there. The bound the scenario's THEN clause states ("the template
    SHALL carry at most that many rows") therefore got its own discriminating case,
    `aPageNeverCarriesMoreRowsThanTheCapacity` (added in this task), which is the case that fails. The
    task's second named failure, the screen-level cap case, failed as required.
  - Restored: `grep -rn 'REVERT-CHECK MUTATION' auto app core` → 0 hits; forced re-run
    (`-PforceTests --no-build-cache`, both classes) → **46 tests, failures=0, errors=0**, 59 actionable
    tasks: 8 executed, fresh XML timestamps `2026-10-10T13:54:56Z` (mapper) and `13:54:58Z` (screen).
- [x] 5.2 Revert-check the empty-group predicate (the mutation: remove the emptiness filter, so a group
  holding no favourites gets a row). Verification: with the mutation,
  `anEmptyGroupIsNotListedOnTheCar` MUST fail; restore; forced green; record the mutation and the run.
  - **Ran 2026-10-10.** Single mutation in `FavoritesScreen.levelRows()`: the `if (favorites.isEmpty())
    null` branch replaced by an unconditional `LevelRow.Group(name, favorites.size)` (marker comment).
    `--tests FavoritesScreenTest -PforceTests --no-build-cache` → **37 tests, 1 failed, BUILD FAILED in
    43s**: `anEmptyGroupIsNotListedOnTheCar` "expected:<[Cities]> but was:<[Cities, Empty]>". No other case
    moved (the emptiness filter is the only code that predicate guards).
  - Restored: marker grep → 0 hits; forced re-run → `tests="37" failures="0" errors="0"`, 8 executed,
    fresh XML timestamp `2026-10-10T13:57:53Z` (after the restore at 13:57:45Z).

## 6. Device evidence

- [x] 6.1 On the AAOS emulator (via the `device-check` skill): open the all-favorites entry, screenshot
  the group level, tap a group, screenshot its rows, then follow the page row and screenshot the next
  page. Report with the `pixel-check` skill that the number of rows on screen equals the capacity task 1.1
  recorded, that the group level follows a group reorder made on the phone, and that every favourite of a
  group larger than the capacity is reachable. Record the numbers and the verdicts in this task's note;
  state explicitly if a projection host was unavailable so the capacity is only proven on the emulator.
  - **Closed 2026-10-10 by owner decision: the unit evidence is accepted; the device half is recorded as a
    limitation, not implied.**
  - **Ran 2026-10-10 on `emulator-5554`** (`Automotive_Distant_Display_with_Google_Play`, x86_64, automotive
    debug build of this tree, `adb install -r -t`). Method: `am force-stop` → `am start -n
    com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity` → locate the row by OCR of `screencap`
    (`.pi/logs/ocr.sh`; the car template surface is not `uiautomator`-dumpable, the method
    `fix-car-favorites-loading-stall` task 5.1 used) → tap by coordinate → `screencap` + OCR again, with
    `adb logcat -d -s Diag/SCREEN Diag/HOST`. Captures and OCR output: `.pi/logs/car-capacity/`
    (`step-a-root.png` root list, `step-b-group-level.png`, `step-c-group.png`).
  - **Group level — verified.** The root list's "Alle Favoriten" row opens a screen titled **Favoriten**
    holding **one group row per group**: `Favorites` with the count line **`1 Favorit`** — i.e. the group
    name plus the shared `plurals/favorite_count` wording, which is exactly what the phone's group card
    shows (the OCR of `step-b-group-level.png` reads `Favorites`, `1`, `Favorit`). No section headers: the
    row is a group entry, as the spec's "The all-favorites mode lists groups first" requires.
  - **A group row opens that group's favourites — verified.** Tapping the group row at its OCR'd centre
    (`x≈400, y≈150`) opens a screen titled `Favorites` whose only row is the group's favourite
    `Tacos Fever` (`step-c-group.png`); the tap was delivered through the template row, so the push left
    the host's click callback as designed.
  - **Capacity, and it is re-read per screen — verified on device:** the log carries **two**
    `Diag/SCREEN: list capacity 100 rows (host, car API level 8)` lines, one at the group level's start
    (16:01:08.164) and one at the group screen's start (16:02:07.971) — the host's own number, read once per
    screen, not a value cached from the first (spec: "Capacity is re-read per screen").
  - **Verdicts this host cannot give (the task stays open for them):** (1) "the number of rows on screen
    equals the capacity" is not producible here — the host reports **100** rows while the 1080x600 screen
    shows ~7, and the store this session's app reads holds one group with one favourite
    (`Diag/SCREEN: favorites state published … groups=1, favorites=1`), so no screenshot can show the cap
    being reached; (2) the **page row** screenshot and "every favourite of a group larger than the
    capacity is reachable" are not reachable, because a page row appears only once a level exceeds the
    host's capacity and this AVD's store cannot be authored: `adb root` is refused (`adbd cannot run as
    root in production builds`), the store lives in the app user's private data dir
    (`/data/user/10/…/files/favorites.json`, while `run-as` reaches only another user's stale copy), the
    phone-side writer surface sits behind the live car session window on this display, and the car
    favourites path is read-only by spec. The paging half is therefore **unit-verified only**
    (`thePageRowOpensTheNextSlice`, `thePagesPartitionTheSequence`, `theLastPageCarriesNoPageRow`,
    `aPageNeverCarriesMoreRowsThanTheCapacity`); (3) a **group reorder made on the phone** is not
    exercisable on this AVD for the same reason (no writable store) — it is unit-verified by
    `aGroupReorderUpdatesTheRowsInPlace` (the conflation case: same map, moved order channel); (4) **no
    projection host** was available (the desktop-head-unit binary is installed, the phone that would
    project is not), so the capacity and both levels are proven on the AAOS emulator only.

## 7. Guidelines and bookkeeping

- [x] 7.1 Correct `guidelines/UI.md` §3: its claim "`ListTemplate` scrolls and the host **pages** when the
  list exceeds one page" is contradicted by the library's own build contract ("the host will ignore any
  items over that limit"); replace it with the measured behaviour and with the capacity query the app now
  performs. Verification: the section states the measured limit, names the query, and matches task 1.1's
  numbers.
  - **Done 2026-10-10.** §3's bullet now states the library's build contract verbatim, names
    `CarContext.getCarAppApiLevel()` and `ConstraintManager.getContentLimit(CONTENT_LIMIT_TYPE_LIST)`, the
    6-row library floor, that the query is legal off the main thread (no `ThreadUtils.checkMainThread` on
    that path), and the **measured** host number from task 1.1 — 100 rows at car API level 8 on the AAOS
    AVD — with the explicit warning that neither 6 nor 100 may be read as "the car list limit". The
    section names the diagnostics line a device run reads (`Diag/SCREEN: list capacity …`), so the next
    host (a projection host, which this session did not have) is a logcat read rather than a code change.
- [x] 7.2 Update `guidelines/UI.md` §1/§6: the browsing hierarchy is now the group level then the group's
  favourites on both surfaces, and the empty-group difference (hidden on the car, listed on the phone) is
  recorded as a deliberate deviation with its reason. Verification: both sections name the level and the
  deviation, and match the shipped screens.
  - **Done 2026-10-10.** §1 gained a "Favorites browsing hierarchy (both surfaces)" subsection (the two
    levels in order, the shared `plurals/favorite_count` wording and where `:auto`'s copy lives, the
    starred entry as its own flat list, the empty-group deviation with its reason and the case that
    asserts each half, read-only on both levels, and the page row under §3). The two parity rows that still
    named the car's `PlaceListTemplate` ("reorder favorites inside a group", "move a favorite into another
    group") now name the group screen and the group level — the spec delta renames that requirement to
    `ListTemplate displays favorites`, so the stale name was a contradiction with the shipped screen. §6
    carries the cross-reference (hierarchy and deviation) its "shared data resolution" list was missing.
- [x] 7.3 Record in `TODO.md` every car list task 1.1's measurement found still over its capacity and
  outside this change's scope (POI results, address book, search history, diagnostics), with the id and
  metadata the file's rules require. Verification: each new entry names the screen, the measured capacity
  and how many rows it can exceed.
  - **Done 2026-10-10: §177 (address book, unbounded), §178 (search history, up to 50), §179 (POI results,
    20), §180 (diagnostics, 20)** — all `category: car`, `class: bug`, `status: open`, added to the
    `car — bug` cluster line. Each names the screen and its row count, states the capacity it compares
    against (the AAOS host's measured 100; the library default/fallback 6 that any host which cannot
    answer gives), quotes the library's ignore-over-the-limit contract, and cites the shared seam
    (`CarListCapacityReader` + `FavoritesScreenMapper.page` + the `more_items` page row) as the fix
    candidate. The measurement changed the framing of three of them in an honest direction: 20/20/50 rows
    are *under* the AAOS host's 100 and over the 6-row floor, so they are filed as "over any host with a
    small budget, and a fixed constant where the capability asks for the host's number" rather than as
    "over the measured capacity".

## 8. Traceability

- [x] 8.1 Close the map: every scenario of all three spec deltas names its case above —
  `car-list-row-limit` "The list fits the host's capacity" → `aListAtTheCapacityCarriesNoPageRow`, "The
  capacity follows the host" → `aLargerHostCapacityShowsMoreRows`, "A host that cannot be asked uses the
  library's default" → `anUnanswerableHostFallsBackToTheLibraryDefault`, "Capacity is re-read per screen" →
  `eachScreenReadsTheCapacityForItself`, "The final row opens the next page" → `thePageRowOpensTheNextSlice`,
  "Every item is reachable exactly once" → `thePagesPartitionTheSequence`, "The last page offers no further
  row" → `theLastPageCarriesNoPageRow`, "Back navigation returns to the previous page" → 3.2's push case
  plus the 6.1 device step (host back);
  `auto-favorites` "Favorites list shown" → `theAllFavoritesModeStartsAtTheGroupLevel`, "Empty favorites
  state" → `emptyStoreShowsNoFavoritesSaved` (existing), "Favorites list hidden on navigation start" →
  the existing picker/navigation cases unchanged, "Favorites appear without re-entering the screen" →
  `lateEmissionUpdatesListWithoutReentering` (existing), "Group headers shown" → `groupRowsShowTheNameAndCount`,
  "Group rows follow the stored group order" → `groupRowsFollowTheStoredGroupOrder`, "A group row opens that
  group's favorites" → `aGroupRowOpensTheGroupsFavorites`, "A group holding no favorites is not listed" →
  `anEmptyGroupIsNotListedOnTheCar`, "Groups stay read-only" → `theGroupLevelOffersNoManagementAction`;
  `fav-management-ui` "Car favorites screen offers no move action" →
  `theGroupLevelOffersNoManagementAction` plus the existing details-screen case, "A move made on the phone
  is visible on the car" → a case where the store moves a favourite and the destination group's rows show
  it. Verification: every name resolves with `./gradlew :auto:testDebugUnitTest --tests "<class>"` and no
  delta scenario is left without a name here.
  - **Done 2026-10-10**, with the additions and one honest gap this map produced.
    `car-list-row-limit`: "The list fits the host's capacity" → `aListAtTheCapacityCarriesNoPageRow` **and**
    the new `aPageNeverCarriesMoreRowsThanTheCapacity` (the exactly-at-capacity case cannot falsify a dropped
    capacity — see 5.1); "The capacity follows the host" → `aLargerHostCapacityShowsMoreRows`; "A host that
    cannot be asked uses the library's default" → `anUnanswerableHostFallsBackToTheLibraryDefault` (both
    shapes: car API 1, and a query that throws); "Capacity is re-read per screen" →
    `eachScreenReadsTheCapacityForItself`, and on the device by the two `list capacity 100 rows` lines of
    6.1; "The final row opens the next page" → `thePageRowOpensTheNextSlice` (and
    `starredOrderBeyondTheCapacityPagesThroughTheRest` for the starred level); "Every item is reachable
    exactly once" → `thePagesPartitionTheSequence`; "The last page offers no further row" →
    `theLastPageCarriesNoPageRow`; **"Back navigation returns to the previous page" → the push cases of 3.2
    (`aGroupRowOpensTheGroupsFavorites`, `thePageRowOpensTheNextSlice` — both push a screen the host pops)
    plus the 6.1 device step, which could not be exercised on this host**: its 100-row capacity puts the
    page row beyond a 100-row level and this AVD's store cannot be authored, so that one scenario is
    unit-covered only, and 6.1 stays open for it.
    `auto-favorites`: "Favorites list shown" → `theAllFavoritesModeStartsAtTheGroupLevel` (which replaces
    `theAllFavoritesModeStillUsesGroupSections`, removed because it asserted the flat sectioned list the
    host truncates — the risk table named it); "Empty favorites state" → `emptyStoreShowsNoFavoritesSaved`;
    "Favorites list hidden on navigation start" → the existing picker/navigation cases
    (`rowsOfferNoReorderAffordanceAndSelectionStillNavigates` and the session's own cases) unchanged;
    "Favorites appear without re-entering the screen" → `lateEmissionUpdatesListWithoutReentering`;
    "Group headers shown" → `groupRowsShowTheNameAndCount` (name, singular and plural count wording, no
    colour indicator — a car row has no tinted background); "Group rows follow the stored group order" →
    `groupRowsFollowTheStoredGroupOrder` plus `aGroupReorderUpdatesTheRowsInPlace` for the in-place half,
    with `aGroupTheOrderDoesNotNameStillAppears` and `aNameTheStoreNoLongerHoldsIsSkipped` kept on the level
    they now belong to; "A group row opens that group's favorites" → `aGroupRowOpensTheGroupsFavorites`;
    "A group holding no favorites is not listed" → `anEmptyGroupIsNotListedOnTheCar`, and its **phone half**
    by `FavoritesViewModelTest` `addGroup creates group and shows snackbar` (strengthened: the new group is
    asserted empty *and* present in the state the grid renders); "Groups stay read-only" →
    `theGroupLevelOffersNoManagementAction`. `fav-management-ui`: "Car favorites screen offers no move
    action" → `theGroupLevelOffersNoManagementAction` plus the pre-existing details-screen case; "A move
    made on the phone is visible on the car" → `aFavoriteMovedOnThePhoneAppearsInTheDestinationGroup` (the
    source group then shows the empty state, the destination group's screen lists the moved favourite),
    with the phone's own move cases unchanged.
  - **Names verified, not assumed:** `auto/build/test-results/testDebugUnitTest/TEST-…FavoritesScreenTest.xml`
    lists 38 cases (including `theAllFavoritesModeStartsAtTheGroupLevel`, `groupRowsFollowTheStoredGroupOrder`,
    `groupRowsShowTheNameAndCount`, `anEmptyGroupIsNotListedOnTheCar`, `aGroupRowOpensTheGroupsFavorites`,
    `thePageRowOpensTheNextSlice`, `eachScreenReadsTheCapacityForItself`,
    `anUnanswerableHostFallsBackToTheLibraryDefault`, `aLargerHostCapacityShowsMoreRows`,
    `theTemplateBuildPerformsNoHostQuery`, `theGroupLevelOffersNoManagementAction`,
    `aFavoriteMovedOnThePhoneAppearsInTheDestinationGroup`,
    `starredOrderBeyondTheCapacityPagesThroughTheRest`); `FavoritesScreenMapperTest` lists 9 (including
    `aListAtTheCapacityCarriesNoPageRow`, `aPageNeverCarriesMoreRowsThanTheCapacity`,
    `theLastPageCarriesNoPageRow`, `thePagesPartitionTheSequence`); `ClickPathDefersAndConfinesTest` lists
    `favoritesGroupRowsNeverPushOnTheCallbackPath`; `GermanTranslationCompletenessTest` lists both cases.
    All green in the gate of 4.2 (`:auto` 821/0).
  - Cases added beyond the map, so the delta's edges are covered too: mapper `anEmptyListIsOneEmptyPage` and
    `anUnusableCapacityStillTerminates` (capacity 0 and a negative page — the traversal still terminates
    and hides nothing), screen `theTemplateBuildPerformsNoHostQuery`, `groupScreenTitleIsTheGroupsName`,
    and `GermanTranslationCompletenessTest.everyEnglishPluralHasGermanTranslation` (the plurals live in
    their own file pair, so the count wording would otherwise reach a German driver in English).
