# Tasks

## 1. The comparison in `:core`

- [x] 1.1 Fetch the region index for a check and match every installed directory of the active repository source to the leaf it came from, with KDoc naming the rule it implements; a directory that matches no leaf or more than one leaf is reported as unknown and is never probed at a guessed path (spec `map-updates` — "An installed repository database is compared with its own version slot"; design D2). Verify: `MapUpdateCheckTest#aDirectoryMatchingNoLeafIsUnknown`, `#twoLeavesOnOneDirectoryNameAreUnknown`, `#installedDataOfAnotherRepositoryIsNotCompared`; `./gradlew :core:test --tests "com.naviveylin.core.mapsource.MapUpdateCheckTest"`.
- [x] 1.2 Compare an installed regional database with its own version slot's metadata: a newer creation time is an update carrying the published size, a not-newer one is up to date, an absent slot is no longer offered with the installation kept, unreadable installed metadata is unknown, and a newer version slot is never offered (spec `map-updates` — "An installed repository database is compared with its own version slot"). Verify: `#aNewerDatabaseIsPublishedForTheClientsVersion`, `#thePublishedDatabaseIsNotNewer`, `#aSlotThatIsNoLongerPublishedIsNotAnUpdate`, `#theInstalledMetadataCannotBeRead`, `#aNewerVersionSlotIsNotOffered`.
- [x] 1.3 Give every compared installation exactly one of the four states, and isolate one failing comparison from the others' results (spec `map-updates` — "Each compared item carries one update state"). Verify: `#oneUnreachableItemDoesNotHideTheOthers`, `#onlyUpdateAvailableItemsAreActionable`.
- [x] 1.4 Compare an installed repository basemap against the availability manifest's entry for the version this client reads, using the metadata stored with the installation (spec `basemap-discovery` — "Report basemap version for updates"). Verify: `BasemapUpdateComparisonTest#aNewerManifestChangeTimeIsAnUpdate`, `#aManifestMatchingTheInstallationIsUpToDate`, `#aVersionThisClientCannotReadIsNotOffered`.
- [x] 1.5 Exclude data the built-in provider installed from the comparison: a directory with no marker or a provider marker reports no state and triggers no request (spec `map-updates` — "The check covers the active repository source only"). Verify: `MapUpdateCheckTest#aProviderInstalledMapReportsNoUpdateState`, including a request-count assertion in the same case.
- [x] 1.6 Module compiles and the focused `:core` suite is green with no new warnings (`guidelines/Build.md` §2 iteration protocol; `run-tests` skill).
- [x] 1.7 Revert-check the slot-absent rule: mutate the comparison so an absent version slot reads as an update, run `./gradlew :core:test --tests "*MapUpdateCheckTest*" -PforceTests --no-build-cache`, confirm `#aSlotThatIsNoLongerPublishedIsNotAnUpdate` fails, restore, re-run forced green, and record both runs.

- [x] 1.8 Recognise the data an earlier update attempt left in the maps tree as staging data of an installation, never comparing it as a map and reporting it as a leftover (spec `map-updates` — "Data left by an interrupted update is recognised, not mistaken for a map"; design D5). Verify: `MapUpdateCheckTest#aLeftoverStagingDirectoryIsNotCompared`, `#stagingDataOfAMapThatIsNotInstalledIsStillReported`.

## 2. The non-destructive install in `:core`

- [x] 2.1 Install a regional update through a sibling `.incoming` directory, verification against the metadata it was transferred from, and a swap that keeps the installed directory and its name, removes the incoming data when the transfer is interrupted, cancelled or fails, and restores the previous data when the swap fails (spec `map-updates` — "An update replaces the installation only when it is complete"; design D5). Verify: `RepositoryDatabaseInstallerTest#aFailedVerificationLeavesTheInstalledMapIntact`, `#anInterruptedUpdateRemovesTheIncomingData`, `#aSuccessfulSwapKeepsTheDirectoryNameAndReplacesItsContents`.
- [x] 2.2 Route the basemap slot through the same install implementation, so one path writes and swaps both kinds (spec `map-updates` — "An update replaces the installation only when it is complete"). Verify: the existing basemap install cases stay green unchanged and the new regional cases share the helper (`RepositoryBasemapInstallTest`, `RepositoryDatabaseInstallerTest`).
- [x] 2.3 Remove leftover `.incoming`/`.backup` data before an update attempt (spec `map-updates` — "An update replaces the installation only when it is complete"; migration and rollback). Verify: `RepositoryDatabaseInstallerTest#aLeftoverIncomingDirectoryIsSweptBeforeTheNextAttempt`.
- [x] 2.4 `:core` compiles and its suite is green with no new warnings.
- [x] 2.5 Revert-check the install ordering: mutate the installer to place the transferred data before verifying it, run the forced focused suite, confirm `#aFailedVerificationLeavesTheInstalledMapIntact` fails, restore, re-run forced green, and record both runs.

- [x] 2.6 Name what a staging directory stages for, so an installation can be told from what an attempt left behind (`SlotInstall.stagingTargetOf`), and make that vocabulary usable by every consumer of the maps tree (spec `map-updates` — "Data left by an interrupted update is recognised, not mistaken for a map"). Verify: `RepositoryDatabaseInstallerTest#stagingNamesAreRecognised`, `#aLeftoverIncomingDirectoryIsSweptBeforeTheNextAttempt`.

## 3. The state holder and the daily check in `:app`

- [x] 3.1 Persist the last completed check and its update records in `AppSettings` through `SettingsStorage.update {}`; a new result replaces the previous one rather than merging, and a result is reconciled against the installed set when it is read (spec `map-updates` — "The check result survives a restart"; spec `settings-persistence` — "A settings change is one transaction"). Verify: `SettingsStorageUpdateStateTest#theUpdateStateRoundTrips`, `MapUpdatesTest#aNewCheckReplacesTheStoredResult`, `#theListIsTheSameAfterAColdStart`.
- [x] 3.2 Add `MapUpdates`, a `@Singleton` with KDoc that owns the check state: a process-lifetime scope with its own fault handler, one check at a time, the 24 h throttle, and a check that did not complete is not recorded (spec `map-updates` — "The app checks for newer repository map data once a day"; `guidelines/Design.md` §4). Verify: `MapUpdatesTest#theFirstAppStartOfTheDayRunsTheCheck`, `#aSecondStartInTheSameDayRunsNoCheck`, `#anUnfinishedCheckIsRetriedAtTheNextStart`, `#aCheckNeverRunsTwiceAtOnce`.
- [x] 3.3 Trigger the check from `NaviVeylinApp.onCreate` without blocking it and without touching native code, so the app-start path only launches it (spec `map-updates` — "The app checks for newer repository map data once a day"). Verify: `MapUpdatesTest#checkIfDueIsANoOpWhenNotDue`, `#checkIfDueLaunchesOnABackgroundDispatcher`; the real start path is measured on device in 7.2.
- [x] 3.4 Add the manual check entry point: it runs the same comparison, counts as the day's check, reports when the last completed check ran and what it found, and reports a check that could not complete as such without replacing the state it could not recompute (spec `map-updates` — "The user can run the check on demand"). Verify: `MapUpdatesTest#aManualCheckCountsAsTheDaysCheck`, `#aFailingManualCheckIsReportedAsSuchAndKeepsThePreviousState`, `#theReportStaysUntilTheNextCheck`.
- [x] 3.5 Emit diagnostics for the check: one line per compared item naming the item and its state, and one line per completed check with its counts — identity only, no coordinates (spec `map-updates`; `guidelines/Logging.md` §3). Verify: `MapUpdatesTest#theCheckReportsOneLinePerComparedItem`, and the build gate `checkNoCoordinatesInLogs` in `preBuild` stays green.
- [x] 3.6 `:app` compiles, the focused map-manager suite is green, and no new warnings appear.
- [x] 3.7 Revert-check the throttle: remove the 24 h guard, run the forced focused suite, confirm `#aSecondStartInTheSameDayRunsNoCheck` fails, restore, re-run forced green, and record both runs.

- [x] 3.8 Remove leftover staging data on the app-start path and nowhere else, so a check the user runs on demand cannot delete what a transfer of the running process is writing (spec `map-updates` — "Leftover staging data is removed on the app-start path only"; design D5). Verify: `MapUpdatesTest#theAppStartSweepsLeftoverStagingData`, `#aManualCheckLeavesStagingDataAlone`, `#theCheckReportsOneLinePerComparedItem`.

## 4. The repository basemap's update state, and the removed refusal

- [x] 4.1 Report the repository basemap's update state from the manifest, and update to the published version this client reads through the existing install path, instead of reporting no update state (spec `basemap-discovery` — "Report basemap version for updates"; spec `map-updates`). Verify: `BasemapSourceSelectionTest#repositorySourceOffersTheUpdateControlWhenTheManifestIsNewer` and `#repositorySourceOffersNoControlWhenTheManifestMatchesTheInstallation` replace `#repositorySourceOffersNoUpdateControl`.
- [x] 4.2 Remove what remains of the deleted `map-repository-source` refusal — the code comments and guards that assert the repository performs no update check (spec `map-repository-source` — "The repository source performs no update check", removed). Verify: `grep -rn "no update check" app/src core/src` returns nothing and the map-manager suite is green.
- [x] 4.3 Revert-check the basemap comparison: mutate the comparison so the installed version is reported as up to date regardless of the manifest, run the forced focused suite, confirm `#repositorySourceOffersTheUpdateControlWhenTheManifestIsNewer` fails, restore, re-run forced green, and record both runs.

## 5. The updates surface in the map manager

- [x] 5.1 Map the check result into `MapManagerUiState` and expose the manual check and the per-item update actions from `MapManagerViewModel` (spec `map-updates` — "The updates section lists what can be updated"). Verify: `MapManagerUpdatesStateTest#onlyUpdatableItemsAreListed`, `#everyUpdatableItemIsListed`, `#theCheckControlReportsItsOutcome`, `#noCheckYetIsStated`; and `MapManagerScreenOrderingTest#theSectionIsAbsentForTheBuiltInProvider` for the section's absence.
- [x] 5.2 Add the `MapUpdatesSection` composable: the section is shown for a repository source and absent for the built-in provider, carries the last-checked or the no-check-yet line and the check control, and lists each updatable item with its name, download size and `[Update]` plus one `[Update all]` action. Include a Compose preview for the new composable (spec `map-updates` — "The updates section belongs to the repository source", "The updates section lists what can be updated"; `guidelines/Design.md` §2). Verify: `MapUpdatesSectionTest` for the four presence states and both actions, and the preview compiles.
- [x] 5.3 Place the section at its fixed position in the screen's section order (spec `map-download-ui` — "Section ordering"). Verify: extend `MapManagerScreenOrderingTest#sectionsRenderInFixedOrder` and `#emptySectionsOmitted` for the new section, and assert its position with a `compose-geometry` measurement (below active downloads, above the basemap section).
- [x] 5.4 Add the new labels and messages to the default and every existing locale resource, in the wording the shared sections use (spec `map-updates`; `guidelines/UI.md` §10). Verify: the Compose tests resolve every new string id and the localisation gate for missing translations is green.
- [x] 5.5 `:app` compiles, the focused Compose and ViewModel suites are green, and no new warnings appear.

## 6. The queue and the running client

- [x] 6.1 Run `[Update all]` as a queue of one transfer at a time over the existing repository download path — each item cancellable on its own, a failure reported per item without stopping the queue, and the transfer inside the existing foreground service, wake lock and active-downloads lifecycle (spec `map-updates` — "Updating every listed item is a queue"). Verify: `MapManagerUpdatesQueueTest#theQueueRunsOneTransferAtATime`, `#aFailureDoesNotStopTheQueue`, `#anItemIsCancelledOnItsOwn`, `#theQueueIsVisibleAsADownload`.
- [x] 6.2 Add `MapDataRegistrar` (mirroring `BasemapRegistrar`): after a swap it re-opens the installed database set and bumps the shared reload signal, off the main thread, with the native boundary confined so a throwable is reported instead of escaping (spec `map-updates` — "An installed update is rendered without an app restart"; `guidelines/Design.md` §4). Verify: `MapDataRegistrarTest#theUpdateReopensTheSetAndBumpsTheNotifier`, `#aNativeFailureIsConfinedAndReported`; the rendered result is measured in 7.3.
- [x] 6.3 Keep the updated map's identity: same directory and name, same source label, saved viewport still applied, and the updated basemap rendered (spec `map-updates` — "An installed update is rendered without an app restart"). Verify: `MapManagerInstalledRowSourceComposeTest` and the viewport cases stay green across an update, and `BasemapViewModelTest` stays green for the basemap path.
- [x] 6.4 Confirm the car variant needs no code for this change: the updates surface stays phone-only and the car keeps rendering installed data (spec `map-updates` — "The updates surface is the phone's"). Verify: `git diff --stat` shows no `:auto` source change, and the `:auto` suite is green in 7.1.
- [x] 6.5 `:app` compiles, the focused suites are green, and no new warnings appear.
- [x] 6.6 Revert-check the queue's serialization: let the queue start every queued transfer at once, run the forced focused suite, confirm `#theQueueRunsOneTransferAtATime` fails, restore, re-run forced green, and record both runs.

## 7. Integration and device verification

- [x] 7.1 Run the full both-flavor gate once (`build-app` and `run-tests` skills; `guidelines/Build.md` §3, §4) on the complete change: all three ABIs build, both flavors, and the tallies are recorded with the command that produced them. Evidence 2026-10-10: `:core:testDebugUnitTest :app:testMobileDebugUnitTest -PforceTests --no-build-cache` → 558/0/0 and 1927/0/0; `:app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest :osmscout-client-java:test` (same flags) → 1927/0/0, 821/0/0, 34/0/0 (classes/tests/failures/errors, 0 errors throughout); `:app:assembleMobileDebug` green with `lib/arm64-v8a`, `lib/armeabi-v7a` and `lib/x86_64` in the APK. The aggregate `./gradlew test -PforceTests --no-build-cache` run failed one case that the per-module runs pass: `:core` `DiagnosticsLogWritePathTest` (the shared-diagnostics-file family `TODO.md` §148/§181 owns, still open), and it logged an unattributed `StackOverflowError` from a leaked `map-download-*` thread (`TODO.md` §183).
- [x] 7.2 Measure the comparison on a device against a locally served mapgen repository (layout and its own decision-matrix script: `Documentation/MapRepository.md` §6, `scripts/mapgen/client-check-test.sh`): install a region, regenerate its slot, let the check run, then read `adb logcat -s NaviVeylin` and the in-app diagnostics. Record the actual lines — one per compared item naming the item and its state, one per completed check — and confirm the second app start within the day issues no comparison request (`guidelines/Build.md` §10). Evidence 2026-10-10 on `emulator-5554`, against the real mapgen repository `http://truenas.home.framstag.com:30123` (its index: 1 region, 16 leaves; basemap v27, changedAt 2026-10-04):
  - the source test in the map manager: `Repository OK — 1 Regionen, 16 Karten`
  - the app-start path: `Diag/WARMUP: MapUpdates.checkIfDue took 1ms`
  - the region installed **by the app** from the repository (24 files, 20 981 558 bytes, every size and CRC-32 verified host-side too): `Diag/MapUpdates: update check: region europe-germany-bremen -> up to date` / `update check completed: 1 compared, 0 with an update`
  - with the installed metadata aged to simulate a stale installation: `region europe-germany-bremen -> update available, 20981558 bytes` / `1 compared, 1 with an update` — the size the check reported equals the slot's declared total byte for byte
  - a second start within the day: `MapUpdates: checkIfDue: last check was 46267ms ago, nothing to do`, and **0** `update check` lines in that start (`grep -c` = 0)
  Both lines are coordinate-free: a kind, a directory name, a state and a size (`guidelines/Logging.md` §3).
- [x] 7.3 Measure the rendered-data claim with pixels: a screenshot pair of the same viewport before and after the update, compared with the `pixel-check` skill, together with the swap and database re-open lines from the log (spec `map-updates` — "An installed update is rendered without an app restart"). If a genuinely regenerated slot for that region cannot be produced in the harness, record the pixel half as unmeasured and give the metadata change plus the re-open lines as the evidence instead. Evidence 2026-10-10: **the pixel half is unmeasured**, and for a stated reason — the repository publishes one slot per database format version, so the "update" that the aged installation produces re-fetches the *same* bytes; there is no newer map content to render, and no regenerated slot can be produced from a live server. What is recorded instead: the installation's own metadata before the update (`generatedAt: 2026-09-01T00:00:00Z`, the aged value) and after it (`2026-10-09T18:12:29Z`, the server's), 24 data files plus `db.json` in place, and the process id unchanged across the update (8218 before and after) with no new `Process started` line — the app was not restarted. Screenshots of the map manager around the update are in `.pi/logs/device-check/{before,after}-update.png`; they differ in the updates section (the row is gone), which is UI state, not map data.
- [x] 7.4 Settle design D5 on a device: with a route navigating over a repository-installed map, trigger the update and confirm guidance continues, the instruction and road lines keep updating, the map keeps rendering, and the re-open lines appear. Record the outcome with its numbers. If the navigation session does not survive, stop and report it — the fallback is a spec delta plus its scenarios, not a silent guard (design D5; `guidelines/Build.md` §10). Evidence 2026-10-10 — **partly measured, and the unmeasured half is named**: navigation over the repository-installed Bremen database started and ran (`NavigationEngine: start: started vehicle=CAR`, `LocationService: lease acquire: nav-engine (held=2)`, a 2,5 km / 2 min route computed on that database, the overlay showing "2,5 km") — and the map manager is **not reachable while the phone is navigating**: the navigating screen carries `Navigation beenden`, `Standortoptionen`, `Vergrößern/Verkleinern` and no `Menü` node, so an update cannot be triggered from the phone UI during a *phone* navigation session at all. The case design D5 actually asks about — a session on the car while the phone updates the same database (one process, Android Auto projection) — is **not measured here**: it needs the host's desktop head unit attached to this emulator, and no car session exists in this session. The fallback (refuse while a navigation session owns the database) therefore stays an owner decision, not something this change can settle from a phone-only run.
- [x] 7.5 Repeat the manual check and one update on a release-signed install if the device carries one, since a local build cannot be installed over it (`TODO.md` §95); otherwise record that the device half ran on a debug build and say so. Evidence 2026-10-10: the AVD carries **no** release-signed install (the app was replaced by `adb install -r -t app-mobile-debug.apk`, `lastUpdateTime=2026-10-10 21:53`), so the whole device half ran on the **debug** build and its data — the map the app installed from the repository, the aged metadata and the update — is the debug build's own.

## 8. Guidelines and documentation

- [x] 8.1 Widen `guidelines/MapRendering.md` §5 ("Epoch Rules") so the invalidation contract names a replaced regional database, not only the basemap. Verify: the section states both, and no rule in the change's specs contradicts it.
- [x] 8.2 Update `guidelines/Design.md` §9 ("Data, persistence & search") with the check's home, the update state and what is persisted. Verify: the section names where the check runs and which fields persist.
- [x] 8.3 Record the phone-only updates surface as an accepted deviation in `guidelines/UI.md` §1 ("Cross-variant UI parity"). Verify: the entry names the surface it belongs to and what the car does instead.
- [x] 8.4 Classify the new capability: add `map-updates` to `tools/feature-list/specs.json` with its area, visibility and surface, and add its capability markers where the catalogue shows them (`guidelines/FeatureList.md` §4). Verify: the feature-list gate runs green with no unclassified shipped spec id reported. Evidence 2026-10-10: `bash tools/gen-feature-list.sh --release 2026-10-10-1` → `unclassified spec ids: 0` (the entry is `offline-maps`, userVisible, surface `phone`; `stale classification entries: 1` is `map-updates` itself, which archiving resolves). The run does **not** write the catalogue: four entry paragraphs of *other* capabilities exceed the 320-character prose limit, recorded as `TODO.md` §184 — not caused by this change and not fixable inside it.
- [x] 8.5 Update `AGENTS.md` and `README.md` only where what they state has changed. Verify: a grep for the update check finds it where it belongs, or finds no stale statement in either file. Evidence 2026-10-10: neither states anything the change falsified — `README.md` does not mention map downloads or updates at this level, and `AGENTS.md`'s Documentation Map already routes map data and persistence to `guidelines/Design.md` §9, where the update state is now stated — so both are left unchanged (`bash tools/check-doc-routes.sh` → all 82 section references resolve).

## Scenario coverage

Every delta scenario and the case or device step that exercises it.

| Scenario (spec) | Exercised by |
|---|---|
| map-updates: First app start of the day runs the check | `MapUpdatesTest#theFirstAppStartOfTheDayRunsTheCheck` |
| map-updates: A second start in the same day runs no check | `MapUpdatesTest#aSecondStartInTheSameDayRunsNoCheck` (+ 7.2 device) |
| map-updates: An unfinished check is retried at the next start | `MapUpdatesTest#anUnfinishedCheckIsRetriedAtTheNextStart` |
| map-updates: A check never runs twice at once | `MapUpdatesTest#aCheckNeverRunsTwiceAtOnce` |
| map-updates: The control reports what the check found | `MapManagerUpdatesStateTest#theCheckControlReportsItsOutcome` |
| map-updates: The report stays until the next check | `MapUpdatesTest#theReportStaysUntilTheNextCheck` |
| map-updates: A manual check defers the next automatic one | `MapUpdatesTest#aManualCheckCountsAsTheDaysCheck` |
| map-updates: A manual check that cannot complete is reported as such | `MapUpdatesTest#aFailingManualCheckIsReportedAsSuchAndKeepsThePreviousState` |
| map-updates: A provider-installed map reports no update state | `MapUpdateCheckTest#aProviderInstalledMapReportsNoUpdateState` |
| map-updates: Installed data of another repository is not compared | `MapUpdateCheckTest#installedDataOfAnotherRepositoryIsNotCompared` |
| map-updates: A newer database is published for the client's version | `MapUpdateCheckTest#aNewerDatabaseIsPublishedForTheClientsVersion` |
| map-updates: The published database is not newer | `MapUpdateCheckTest#thePublishedDatabaseIsNotNewer` |
| map-updates: The client's version slot is no longer published | `MapUpdateCheckTest#aSlotThatIsNoLongerPublishedIsNotAnUpdate` |
| map-updates: The installed metadata cannot be read | `MapUpdateCheckTest#theInstalledMetadataCannotBeRead` |
| map-updates: A newer version of the database format is published | `MapUpdateCheckTest#aNewerVersionSlotIsNotOffered` |
| map-updates: One unreachable item does not hide the others | `MapUpdateCheckTest#oneUnreachableItemDoesNotHideTheOthers` |
| map-updates: Only update-available items are actionable | `MapUpdateCheckTest#onlyUpdateAvailableItemsAreActionable` |
| map-updates: The section is shown for a repository source | `MapUpdatesSectionTest` — repository presence case |
| map-updates: The section is absent for the built-in provider | `MapUpdatesSectionTest` — provider absence case |
| map-updates: Nothing to update is stated, not left blank | `MapUpdatesSectionTest` — nothing-to-update case |
| map-updates: No check has run yet is stated | `MapUpdatesSectionTest` — no-check-yet case |
| map-updates: Every updatable item is listed | `MapManagerUpdatesStateTest#everyUpdatableItemIsListed` |
| map-updates: Only updatable items are listed | `MapManagerUpdatesStateTest#onlyUpdatableItemsAreListed` |
| map-updates: Update one item | `MapManagerUpdatesQueueTest#theQueueRunsOneTransferAtATime` |
| map-updates: Update every listed item | `MapManagerUpdatesQueueTest#theQueueRunsOneTransferAtATime` |
| map-updates: The list is the same after a cold start | `MapUpdatesTest#theListIsTheSameAfterAColdStart` |
| map-updates: A stale result is replaced, not merged | `MapUpdatesTest#aNewCheckReplacesTheStoredResult` |
| map-updates: The queue runs the updates one at a time | `MapManagerUpdatesQueueTest#theQueueRunsOneTransferAtATime` |
| map-updates: A failure does not stop the queue | `MapManagerUpdatesQueueTest#aFailureDoesNotStopTheQueue` |
| map-updates: An item is cancelled on its own | `MapManagerUpdatesQueueTest#anItemIsCancelledOnItsOwn` |
| map-updates: The queue is visible as a download | `MapManagerUpdatesQueueTest#theQueueIsVisibleAsADownload` |
| map-updates: The installed map stays usable while the update transfers | `RepositoryDatabaseInstallerTest#aSuccessfulSwapKeepsTheDirectoryNameAndReplacesItsContents` (+ the installed-list case in 6.3) |
| map-updates: An interrupted update leaves the installed map intact | `RepositoryDatabaseInstallerTest#anInterruptedUpdateRemovesTheIncomingData` |
| map-updates: A failed verification installs nothing | `RepositoryDatabaseInstallerTest#aFailedVerificationLeavesTheInstalledMapIntact` |
| map-updates: The updated data is rendered | 7.3 (pixels) + `MapDataRegistrarTest#theUpdateReopensTheSetAndBumpsTheNotifier` |
| map-updates: The map keeps its identity | 6.3 (`MapManagerInstalledRowSourceComposeTest`, viewport cases) |
| map-updates: An updated basemap is rendered | `BasemapViewModelTest` + 7.3 |
| map-updates: A car-only session keeps rendering | `:auto` suite in 7.1 + 6.4 |
| map-updates: The car does not offer the controls | 6.4 (no `:auto` change) |
| basemap-discovery: Update available | existing basemap cases, unchanged |
| basemap-discovery: No basemap installed | existing basemap cases, unchanged |
| basemap-discovery: Up to date | `BasemapUpdateComparisonTest#aManifestMatchingTheInstallationIsUpToDate` |
| basemap-discovery: A repository source compares its manifest | `BasemapUpdateComparisonTest#aNewerManifestChangeTimeIsAnUpdate`, `BasemapSourceSelectionTest#repositorySourceOffersTheUpdateControlWhenTheManifestIsNewer` |
| basemap-discovery: Source publishes no comparable version | `BasemapUpdateComparisonTest#aVersionThisClientCannotReadIsNotOffered` |
| map-download-ui: Sections render in fixed order | `MapManagerScreenOrderingTest#sectionsRenderInFixedOrder` (+ the `compose-geometry` position assertion) |
| map-download-ui: Empty sections omitted | `MapManagerScreenOrderingTest#emptySectionsOmitted` |

| map-updates: A leftover is recognised, not compared | `MapUpdateCheckTest#aLeftoverStagingDirectoryIsNotCompared`, `#stagingDataOfAMapThatIsNotInstalledIsStillReported` |
| map-updates: The app start removes a leftover | `MapUpdatesTest#theAppStartSweepsLeftoverStagingData` |
| map-updates: A check on demand leaves a leftover alone | `MapUpdatesTest#aManualCheckLeavesStagingDataAlone` |

`map-repository-source` contributes no scenario: its one requirement is removed, and 4.2 removes the code that encoded it.

## Workflow follow-up

- Independent read-only review of the diff against the delta scenarios and the gate XMLs, then archive with the evidence: the red-on-HEAD case, the green full-gate tallies, the device measurements from 7.2 to 7.4, and the D5 outcome.
- `TODO.md` §160 moves to `fixed-by add-map-update-check` on archive; §163's relation to it is re-checked, since it cites §160.
- If 7.4 shows a navigation session cannot survive the re-open, that is a spec delta before archive, not an implementation detail.
