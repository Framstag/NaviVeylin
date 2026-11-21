# Tasks — the map follows the writer, not a document the writer replaced

Reference: `proposal.md` for the evidence and the owning guideline section, `design.md` for the single
fix and the alternatives it eliminated, `guidelines/UIPhone.md:313` (section 20) for the rule "both
hosts share one writer" that this delta keeps.

## 1. The red case

- [x] 1.1 `app/src/test/java/com/naviveylin/ui/settings/SettingsControllerTest.kt` —
  `aLateLoadDoesNotPublishOverANewerWrite`: a read starts, the file's read returns, a write begins and
  publishes, and only then does the read return; the collected document sequence must be
  `[false, true]`.
  Verify: with the production change reverted the case fails
  `expected:<[false, true]> but was:<[false, true, false]>`, `tests=4 failures=1`
  (2026-11-21T17:11:22.489Z, `evidence/red-SettingsControllerTest.xml`).

## 2. The writer's publication

- [x] 2.1 `app/src/main/java/com/naviveylin/ui/settings/SettingsController.kt:45` — `writesStarted` is
  incremented at `update`'s entry (before its transaction suspends), and `load` publishes its document
  only while the count still equals the value read at entry.
  Verify: task 1.1's case is green (`evidence/green-SettingsControllerTest.xml`).

## 3. The follower's subscription

- [x] 3.1 `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt:452` —
  `wirePersistedSettings` subscribes `followSurfaceWrites()` before it launches the start-up read;
  the call at the end of the read is removed and `followSurfaceWrites`'s doc states why the first
  emission is the state's own defaults. The same read path also carries the `settingsReadDone` gate:
  declared at `MapCanvasViewModel.kt:442`, set at `MapCanvasViewModelSettings.kt:544` when the read
  finishes, checked at `:562` (`if (!settingsReadDone) return@collect`) — it drops every emission
  arriving before the start-up read completes, chiefly the replay, which arrives after the user's own
  change and carries values that are not the map's.
  Verify: `SymbolIconPreferenceTest#aChangeReachesTheClientOnceAndIsPersisted` is green
  (`evidence/green-SymbolIconPreferenceTest.xml`); with 3.1 reverted and 2.1 applied it fails
  `expected:<[true]> but was:<[true, false, true]>`, `tests=5 failures=1`
  (2026-11-21T17:12:53.637Z, `evidence/red-SymbolIconPreferenceTest.xml`). The record states only
  "with 3.1 reverted and 2.1 applied" — 3.1 as written then named the subscription move alone — and
  does not say whether the `settingsReadDone` gate hunks stood in that run.
  Restore evidence: the green runs after the red runs on the same tree (`evidence/after-change.log`,
  17:14:23–17:14:48, and the continuation re-run at 17:17); no separate restore-clean marker (empty
  `git diff` of the production file) is carried.

## 4. The change the requirement owns

- [x] 4.1 `openspec/changes/fix-a-stale-read-does-not-overwrite-a-change/specs/settings-surface/spec.md`
  — one ADDED requirement, "A superseded document is never published", whose two scenarios name
  exactly the two cases above.
  Verify: `./tools/loop/measure.sh --change fix-a-stale-read-does-not-overwrite-a-change` prints
  `LOOP-MEASURE-CHANGE fix-a-stale-read-does-not-overwrite-a-change scenarios=2 cases=2 unnamed=0`
  (`evidence/measure.log`).

## 5. The affected cases

- [x] 5.1 `:app` settings surface and the map's start-up path —
  `com.naviveylin.ui.settings.*`, `SymbolIconPreferenceTest`, `DarkModeLateRestoreTest`: all green in
  one focused run (`evidence/after-change.log`).
  Verify: the four cited suites' XMLs, `failures=0` (`evidence/green-*.xml`).
- [x] 5.2 `:auto` — `com.naviveylin.auto.StartupScreensTest`, the third case issue #986 recorded, is
  green on this tree (`evidence/green-StartupScreensTest.xml`); it reads no settings document, so the
  change neither touches it nor explains its recorded failure.
  Verify: the XML's `failures=0` and the run in `evidence/after-change.log`.

## 6. The structural checks this delta owes

- [x] 6.1 `bash tools/check-change-readiness.sh openspec/changes/fix-a-stale-read-does-not-overwrite-a-change`,
  `bash tools/check-case-names.sh`, `bash tools/check-openspec-hygiene.sh` and
  `bash tools/check-doc-routes.sh`.
  Verify: each exit status and line in `evidence/structural-checks.log`.
