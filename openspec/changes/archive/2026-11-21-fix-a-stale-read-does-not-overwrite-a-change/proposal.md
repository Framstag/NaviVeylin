# Proposal — a settings document the writer has superseded is never published

## Why

**Root cause.** `MapCanvasViewModel` started following the one writer's document at the end of its
start-up read — the `followSurfaceWrites()` call sat inside `wirePersistedSettings`, after
`settingsController.load()` returned — so the collector's first emission is the `StateFlow` replay
of the document the read just published, not a change the settings surface made.
When the user changes a field *before* that read completes, the replay carries the value that change
replaced: the collector writes it back into `_uiState` and hands it to the native client, and the
write the setter already queued then hands the newer value over again — two native calls for one
change. `SettingsController.load()` has the same shape: it publishes the document it read even when a
write began after the read did, so the hosts see the replaced value after the writer already holds
the new one.

**Evidence, verified against the tree (issue #986).** The build is red on the item's base
`c3d2ebd3ee2851780eee68b45d5d0cb1c19fa66e`:

- `app/src/test/java/com/naviveylin/ui/map/SymbolIconPreferenceTest.kt:99` —
  `aChangeReachesTheClientOnceAndIsPersisted` calls `onSetPreferSymbolPoiIcons(true)` and advances the
  test clock; the client records `[true, false, true]` where the case requires `[true]`
  (`SymbolIconPreferenceTest.kt:103`). Red XML `tests=5 failures=1`
  (2026-11-21T17:12:53.637Z, `evidence/red-SymbolIconPreferenceTest.xml`); the same failure stands in
  the archived `settings-taxonomy-and-surface` gate log, `.../evidence/group4-gate.log:234`.
- `app/src/test/java/com/naviveylin/ui/settings/SettingsControllerTest.kt` — a read that returns
  after a write began publishes its document: the collected sequence is `[false, true, false]`
  (`tests=4 failures=1`, 2026-11-21T17:11:22.489Z,
  `evidence/red-SettingsControllerTest.xml`).
- `app/src/test/java/com/naviveylin/ui/settings/SettingsSurfaceTest.kt:725` —
  `aChangePersistsWithoutASaveAction` races the same stale publication when the map's start-up read
  lands after the surface's write; under the suite's two forks it is the case issue #986 records.

**The owning requirement.** `openspec/specs/settings-surface/spec.md:147` — "A change applies
immediately and persists", and `openspec/specs/dark-mode/spec.md:199` — "A user change outranks an
in-flight settings read", whose rule extends to the document a read publishes. The change adds one
requirement to `settings-surface`: a document the writer superseded is never published, and the map's
follower applies the writer's publications only after the start-up read completes.

**The owning guideline section.** `guidelines/UIPhone.md` section 20 "Phone settings surface", the rule
"both hosts share one writer" (`UIPhone.md:313`): the map reads `SettingsController.settings` and
writes through the same `update`. The change keeps that rule and states when a publication is the
writer's decision rather than a follower's replay.

**Surface count.** `tools/look.sh 'followSurfaceWrites|SettingsController\.settings|_settings\.value'`
→ `11 matches in 7 files`: two production files (`MapCanvasViewModelSettings.kt`,
`SettingsController.kt`), one guideline section that states the rule (`UIPhone.md:313`), the open
change's own records, and one archived landing note. One case is added
(`SettingsControllerTest#aLateLoadDoesNotPublishOverANewerWrite`), two existing cases are the ones the
defect reddens (`SymbolIconPreferenceTest#aChangeReachesTheClientOnceAndIsPersisted`,
`SettingsSurfaceTest#aChangePersistsWithoutASaveAction`), and no fixture moves.

surface: 1 case added, 2 cases reddened, 0 fixtures — `tools/look.sh 'followSurfaceWrites|SettingsController\.settings|_settings\.value'` (11 matches in 7 files)

## What changes

- `SettingsController.update` marks the write before its transaction suspends, and
  `SettingsController.load` publishes its document only while no write has begun since the read did.
- `MapCanvasViewModel.wirePersistedSettings` subscribes `followSurfaceWrites()` before the start-up
  read instead of after it, and the read path gains the `settingsReadDone` gate: the collector
  returns before `settingsReadDone` is set (`MapCanvasViewModel.kt:442`, check at
  `MapCanvasViewModelSettings.kt:562`, set at `:544` when the read finishes), so what it drops is
  every emission arriving while the start-up read is still in flight — chiefly the replay, which
  arrives after the user's own change and carries values that are not the map's.
- `SettingsControllerTest` gains the case that owns the second half: one write, one published
  document, even when a read returns after the write began.

## What does not change

The one writer, its serialized transactions, the map's in-flight-read-wins guards for the fields the
user changes, and the surface's own write path. No new seam, no second document, no option.

## Rollback

Revert the three hunks (two production functions, one case), the `settingsReadDone` gate among them:
the collector returns to subscribing at the end of the read, the gate is removed (left standing with
the declaration `false` it would drop every emission forever, so the reverted follower would apply
nothing), and `load` publishes unconditionally. Nothing persists differently.
