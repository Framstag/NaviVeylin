# Design — car-favorites-group-level

## What the ground looks like

```
  TODAY (one screen)                                limit the host imposes
  +-------------------------------------+           CONTENT_LIMIT_TYPE_LIST
  | Favorites (ListTemplate)            |           "the host will ignore any
  |   * Family      (SectionedItemList) |            items over that limit"
  |       Home / Work / Gym             |           AAOS host: 100, car API 8
  |   * Hiking                          |           no ConstraintManager use in
  |       Trail head / Lake / Cabin     |            this repository (0 hits)
  |   ...                               |           UI.md 3 claims the host PAGES
  +-------------------------------------+            -> contradicted by the library
  every group and every favourite in one list

  PROPOSED (two levels, no sections)
  +----------------------------------+   tap    +----------------------------+
  | Favorites (ListTemplate)         |  ---->   | Family (ListTemplate)      |
  |   Family            7 favorites  |          |   Home                     |
  |   Hiking           12 favorites  |          |   Work                     |
  |   Work              3 favorites  |          |   Gym                      |
  |   More (4)                       |          |   More (4)                 |
  +----------------------------------+          +----------------------------+
  group rows end at the host capacity,           the group's rows end at it too
  the rest behind a More row                     (the starred list likewise)
```

Two things follow from dropping sections on the car. A group's favourites are already one flat list, so
no section survives; and the open question "does a section header count against the host's row budget?"
becomes moot for every car favourites list, which removes an unknown this design would otherwise have to
leave to a measurement.

## D1 — The hierarchy: an unconditional group level

**Chosen: two levels always** — the all-favorites mode is the group level, a group row opens that group's
favourites — as the owner decided. It mirrors the phone's own hierarchy (`group-grid-display` — "Click
group card to view favorites" → group detail list), so `UI.md` §1/§6 can state one hierarchy with two
presentations instead of a deviation.

Alternatives:

- **Fit-adaptive** (flat list while everything fits the capacity, groups above it). Rejected: the visible
  hierarchy would depend on the host's report, so one requirement could not describe what the driver
  sees, and every rendering case would need a per-host variant.
- **Keep the single sectioned list and rely on the host.** Rejected: that is the defect — rows past the
  capacity disappear with no signal.
- **Reach the remainder through car search instead of a group level.** Rejected: the search matches by
  name, so it is not a way to browse a store whose contents the driver does not know; it also leaves the
  starred list and any large group unaddressed.

## D2 — How the screen learns its capacity

**Chosen: ask the host, once per screen start, off the main thread, and fall back to the library's own
default.** The capacity comes from `ConstraintManager.getContentLimit(CONTENT_LIMIT_TYPE_LIST)`, read in
the screen's own scope on the screen's `backgroundDispatcher` (the parameter `MapScreen` already
documents, `MapScreen.kt:69-75`) and cached in a field the template builder reads. Hosts that cannot
answer fall back to the Car App Library's default list capacity, which is also the floor its own
documentation states (6 rows for `ListTemplate` before the `ConstraintManager` existed).

Alternatives:

- **A fixed cap of 6 everywhere.** Rejected: on a host that reports more, the screen would under-fill and
  hide items behind needless pages; the owner's decision is that the cap follows the host.
- **Read the capacity inside `buildTemplate()`, on the main thread.** Rejected: that is a remote host call
  inside the host's own answering path, which `car-host-fault-isolation` — "Host callbacks answer
  promptly" — forbids; the read belongs with the screen's other background work.
- **No cap; let the host clip.** Rejected: it is today's behaviour and it loses rows silently.

Risks: `ConstraintManager` is `@ExperimentalCarApi` and `@RequiresCarApi(2)` while the manifest declares
`minCarApiLevel = 1` (`app/src/main/AndroidManifest.xml:122`), and the accessor this design relies on
(`carContext.carAppApiLevel`) and the legality of the query off the main thread must be checked against
car-app 1.7.0 before implementation (`UI.md` §3 — "Check the real car-app API surface"); if the query is
main-thread-only, it moves to `onStart` as a single short bounded call, which is the fallback, not the
plan. A capacity that arrives after the first template is applied on the next `invalidate()`, so the
first template may use the fallback for one frame.

## D3 — Where the group level lives

**Chosen: extend `FavoritesScreen` with a `groupName: String? = null` mode**, mirroring the existing
`starredOnly: Boolean` parameter, and push it from a group row through `armScreenPush` — the same guarded
push every other car screen uses (`MapScreen.kt:556-570`). The state, the observation wiring
(`FavoritesScreenObservations` over `CarScreenObservations`), the empty state and the row builder are
shared, so the new level adds a mode rather than a second screen with a second copy of the wiring.

Alternatives:

- **A dedicated `FavoritesGroupScreen` plus its own observations and mapper.** Rejected: it duplicates
  the observation wiring that `fix-details-screen-observation-scope` just consolidated, for a screen that
  observes exactly the same three sources.
- **One screen with an internal selected-group state and `invalidate()`.** Rejected: the host's back
  navigation would leave the screen entirely instead of returning to the group level, and the added
  requirement states that back returns to the group level.
- **An `ItemList` per group inside the same template** (today's sections). Rejected with D1.

Template quota: the driver's path (favourites → group → next page) sends three templates and back
navigation refunds each pop, so the host's five-template budget is not at risk; that is the library's
documented accounting, not an assumption of this design.

## D4 — The cap and the page row

A pure mapper (`FavoritesScreenMapper`) owns the arithmetic: given the ordered rows for the shown level
and the capacity, it returns the rows of the current page plus whether another page exists and the number
of items left. The screen renders the page and, when another exists, a final clickable row whose wording
is a new `:auto` string carrying the remaining count. The page opened from that row shows the next slice
of the same ordered sequence, so "every item is reachable exactly once" is a property of the mapper and
not of the host.

Applies to the group level, a group's favourites and the existing starred list — the starred list keeps
its one-flat-list, no-headers shape and gains only the cap.

## Threading and lifecycle (Design.md §4)

- Both levels are `Screen`s on the host's stack; their state and observations follow the started-period
  pattern unchanged (`onStart` starts, `onStop` stops, `onDestroy` cancels the screen's own scope).
- The capacity read runs in the screen's own scope on `backgroundDispatcher`, off the main thread; the
  template builder only reads the cached value, so a host callback performs no remote call.
- The push from a group row goes through `armScreenPush`, which confines a failing push to a logged
  no-op; nothing constructs a screen inside the host callback.

## Risks

| risk | assessment and mitigation |
|---|---|
| Unverified `ConstraintManager` surface and its thread rule | Checked against car-app 1.7.0 and then **measured**: the AAOS AVD's templates host answers `list capacity 100 rows (host, car API level 8)` (task 1.1), and the query is legal off the main thread (`HostDispatcher.dispatchForResult` has no main-thread check, task 1.1). The main-thread fallback named in D2 was therefore not needed. |
| The AAOS host reports **100**, not the library's 6 | The cap is still the host's number and the page row still bounds every list; on this host a page row appears only for a level holding more than 100 rows, which the AVD's store cannot reach — recorded as the device-evidence limitation in task 6.1 rather than worked around by lowering a threshold. |
| A host reporting a capacity below the fallback | Pages get shorter; the page row still covers every item, so only the number of taps grows. |
| Empty groups hidden on the car but listed on the phone | Recorded as the deliberate deviation in `UI.md` §1/§6 with the reason (no car affordance can move a favourite into a group). |
| Existing cases assert the flat sectioned list | `FavoritesScreenTest.theAllFavoritesModeStillUsesGroupSections` (`:367`) is replaced by the group-level cases rather than deleted silently; the group-order and starred-order cases keep their assertions, now on the level they belong to. |
| New `:auto` strings | Every new string lands in `values/strings.xml` and `values-de/strings.xml`; `GermanTranslationCompletenessTest` and the `HardcodedText` lint elevation are the guards. |
| Template quota under deep paging | Three templates on the worst driver path, refunded by pops (library accounting); no page is pushed from a template refresh. |

## Verification

- Unit (pure, no `CarContext`): `FavoritesScreenMapperTest` gains the page arithmetic — an empty list, a
  list exactly at the capacity (no page row), one item past it, the last page (no page row), and the
  invariant that the pages partition the sequence.
- Unit (screen): the group level renders one row per non-empty group in the stored order with the shared
  count wording; a group row pushes the group screen; back returns to the group level; a group's rows
  keep the stored favourite order and selecting one still starts the picker; the capacity caps the rows
  and the page row opens the rest; the starred mode keeps its order and gains the cap.
- Device (`device-check`, AAOS emulator plus a projection host): record the capacity the host reports
  (`CONTENT_LIMIT_TYPE_LIST`, with and without sections) — this is the measurement that corrects
  `UI.md` §3 — then a screenshot plus the template dump of the group level and of a page opened from the
  page row, confirming the number of rows on screen equals the reported capacity and that the remainder
  is reachable. `pixel-check` settles the row-count claim; the stationary emulator can produce all of it,
  and no driving-dependent property is claimed.
- Guideline: `UI.md` §1/§6 gain the group-level entry and the empty-group deviation; §3's "the host pages
  when the list exceeds one page" is replaced by the measured behaviour.

## Rollback

Revert the change's commits. The store, its order channels and the persisted JSON are untouched; the
phone path is untouched; the new capability's rows are additive to the car templates.
