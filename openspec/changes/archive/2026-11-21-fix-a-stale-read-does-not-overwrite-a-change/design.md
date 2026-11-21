# Design — the map follows the writer, not a document the writer replaced

## The root cause

`SettingsController` is the one writer and the one document both hosts read
(`guidelines/UIPhone.md:313`, "both hosts share one writer"). Two places let a reader's document
arrive after a change that supersedes it:

1. `MapCanvasViewModel.wirePersistedSettings` subscribed `followSurfaceWrites()` *after* the start-up
   `settingsController.load()` returned. `StateFlow` replays its current value on subscription, so the
   collector's first emission is that load's document. If the user changed a field while the read was
   in flight, that document carries the value the change replaced: the collector writes it back into
   `_uiState` and calls `setPreferSymbolIcons` with it, and the queued `update` then pushes the newer
   value — `[true, false, true]` for one change (`SymbolIconPreferenceTest.kt:103`).
2. `SettingsController.load()` published the document it read unconditionally. A write that began
   after the read started owns the document the file is about to hold, so publishing the read's
   document hands every follower — the map, the surface, the start screen — a value the file no
   longer holds (`[false, true, false]` in `SettingsControllerTest`).

## The single fix

- `SettingsController` counts the writes that have begun (`writesStarted`, incremented at `update`'s
  entry, before the storage transaction suspends) and `load` publishes its document only when the
  count still equals the value it saw when the read started.
- `wirePersistedSettings` subscribes `followSurfaceWrites()` before the start-up read, and that read
  path carries the `settingsReadDone` gate: `MapCanvasViewModel.kt:442 internal var
  settingsReadDone = false`, `MapCanvasViewModelSettings.kt:562 if (!settingsReadDone) return@collect`,
  set at `:544` when the start-up read finishes. What it drops: every emission the collector sees
  before the start-up read completes — chiefly the flow's replay, which is stopped there because the
  replay arrives after the user's own change, so its values are not the map's. Every later emission is
  a document the writer published.

## Why not the alternatives

- **A per-field "the map wrote this last" marker in the collector.** It duplicates the in-flight-read
  guards the view model already carries for five fields (`preferSymbolSetByUser` at
  `MapCanvasViewModel.kt:449`, `darkModePreferenceSetByUser` at `:457`, `ambientSensitivitySetByUser`
  at `:463`, `navNorthUpSetByUser` at `:472`, `freeFormNorthUpSetByUser` at `:475`), and it cannot distinguish a surface write
  from the map's own write for the same field without a second table. Rejected: the ordering fix is one
  call moved, and the marker would grow with every followed field.
- **`drop(1)` on the collector instead of subscribing earlier.** It drops the replay only if the
  collector reaches its first emission before any write lands; between the start-up read and the
  collector's first turn a surface write would be dropped instead. Rejected: the earlier subscription
  has no such window and needs no operator.
- **Not publishing from `load` at all.** The surface and the start screen read the published document
  after their own `load()` (`UIPhone.md:314`), so a load that publishes nothing leaves them on the
  defaults. Rejected: the write generation is the smaller change and keeps the documented contract
  "the value is returned as well as published".
- **Making `update` publish before the transaction completes.** It would put a value in the flow the
  file does not hold yet, which the class doc and case `SettingsControllerTest#aWriteLandsOnceAndEmits`
  forbid. Rejected.

## Blast radius

Two production functions, the `settingsReadDone` gate they add on the same read path (it drops every
emission arriving before the start-up read completes: `MapCanvasViewModel.kt:442`,
`MapCanvasViewModelSettings.kt:544`/`:562`), and one new case. The map's collectors, the surface's write path, the
storage's serialized transactions and every persisted field are unchanged; the in-flight-read guards
for dark mode, ambient sensitivity and the orientations keep doing what they did. A load that races a
write now leaves the flow on the pre-write document until the write publishes — one emission per
write, which is what `SettingsControllerTest#aWriteLandsOnceAndEmits` already requires.

## Open decisions

None

The evidence leaves one mechanism and one fix (above); the alternatives are eliminations, not options,
and no recorded owner decision is reversed.

## The guideline this delta owes, and the one it does not

`guidelines/UIPhone.md:313`, section 20, states "both hosts share one writer": the map reads
`SettingsController.settings` and writes through the same `update`. That rule is what this change keeps
— the delta repairs the seam that implements it, and no sentence of the guideline becomes false or
needs a new exception, so `guidelines/UIPhone.md` carries no edit.

## Rollback

Revert the three hunks: move the `followSurfaceWrites()` call back to the end of
`wirePersistedSettings`, drop the two `writesStarted` lines, and remove the `settingsReadDone` gate
(declare at `MapCanvasViewModel.kt:442`, set at `MapCanvasViewModelSettings.kt:544`, check at `:562`) —
left behind it stays `false` forever and the reverted tree's follower would apply nothing at all. No persisted state is rewritten by
either direction, so a rollback loses nothing but the fix.
