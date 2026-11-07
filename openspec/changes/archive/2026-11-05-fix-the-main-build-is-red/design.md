# Design

## Root cause

`MapCanvasViewModel.wirePersistedSettings` (`MapCanvasViewModelSettings.kt:434`) launches an unawaited
`settingsStorage.load()` and, when it returns, writes eighteen state fields — `navNorthUp` and `freeFormNorthUp`
among them — from the settings it read. `SettingsStorage.load()` is `withContext(ioDispatcher)` (line 264), so the
write lands at a time no caller controls: after the constructor, and possibly after the user's next action.

`onSetNavOrientation(true)` (the compass long press, `MapCanvasViewModelFollow.kt:705`) sets `navNorthUp = true`
and the angle to 0. If the start-up read completes after that, it writes `navNorthUp = false` back. The next fix
then reads `isNorthUp = false` (`MapCanvasViewModelFollow.kt:1032`), the fix carries no smoothed course (or one the
deadband rejects), and the rotation picks the "follow direction without a valid course" branch — `lastUsedAngle`
(line 1072) — which is the +90° the user left. That is the observed `expected:<0.0> but
was:<1.5707963267948966>`.

## The single fix

The dark-mode requirement "A user change outranks an in-flight settings read" already solved this shape for three
fields with a one-way flag per field (`darkModePreferenceSetByUser`, `ambientSensitivitySetByUser`,
`preferSymbolSetByUser`): the setter marks it, the restore consults it. The two orientation fields get the same
treatment — `navNorthUpSetByUser`, `freeFormNorthUpSetByUser` — so the restore keeps the in-state value for a field
the user chose and takes the persisted one otherwise.

Why the flag and not a re-read, an await, or a version counter:

- **Awaiting the read before any user action** would make the compass and the options sheet inert until disk IO
  returns, which is the stall the dark-mode fix deliberately did not take: the map has to stay usable while the
  read is open.
- **Re-reading after the change** would move the race into the writer: `settingsStorage.update` and `load` already
  serialize on one mutex, and the loser of that mutex is the same coin flip.
- **A settings version counter** is the same flag with a comparison added, over one field's worth of information.

The alternative the evidence eliminates is "the rotation code is wrong": the north-up branch commits 0.0
unconditionally (`MapCanvasViewModelFollow.kt:1055-1056`), and it held at the second fix of the failing case
(`MapCanvasViewModelFollowModeTest.kt:429-436`) — the map was rotated back only after the state said follow
direction again.

## Blast radius

- The two orientations, in-session only. A session that never touches the compass restores both from disk exactly
  as today; the persisted file gains no field and needs no migration.
- The three existing flags are untouched, so dark-mode, ambient sensitivity and the symbol-icon preference keep
  their behaviour.
- The follow collector, the rotation rate limit and the north-up commit are not edited at all: the change is the
  state the collector reads.

## Rollback

Revert the four production lines (two flags, two setter marks, one guarded restore each) — the tests that came
with them go back to reading the persisted value. Nothing persisted changes shape, so a rollback on a device is a
no-op for its settings file.

## Test seam

`NorthUpLateRestoreTest` reuses the mechanism `DarkModeLateRestoreTest` established: the storage's `ioDispatcher`
is a `StandardTestDispatcher` over a scheduler the case advances by hand, so "the read completes after the choice"
is an ordering, not a race — the rule `guidelines/BuildTests.md` §6e states, "a test that must wait drives the
component's seam and awaits observable state", and the seam it drives is the storage's `ioDispatcher`.

`MapCanvasViewModelFollowModeTest` — the class the CI failure came from — is given the same seam: its storage's
`ioDispatcher` is the rule's dispatcher, and its `enableFollowDirectionMode` helper drains the read *before* it
applies the mode instead of applying it twice around the drain. That removes the real IO thread the class was
racing, which is why the same case was green locally and red on a loaded runner.
