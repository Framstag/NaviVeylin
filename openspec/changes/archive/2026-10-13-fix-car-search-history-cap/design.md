# Design — fix-car-search-history-cap

See `proposal.md` — Why for the root cause and its evidence. This document records the one fix, the
alternatives the evidence eliminated, the blast radius and the rollback.

## Context

`SearchHistoryScreen` (`auto/src/main/java/com/naviveylin/auto/SearchHistoryScreen.kt`) is a car
`ListTemplate` whose rows are the entries `AutoSearchHistoryProvider.load()` returns — one `Row` per
stored search, built at `:50-70` into one `ItemList` with no cap, loaded in the screen's `init` scope on
`Dispatchers.Default` (`:40-43`). The store behind the provider keeps up to `MAX_ENTRIES` = 50 entries
(`app/src/main/java/com/naviveylin/data/SearchHistoryRepository.kt:151`), so the list is exactly as long
as the driver's search history. The Car App Library's contract for that list is that the host ignores
every item over the capacity it reports (measured 100 rows at car API level 8; the library's packaged
default, and this app's fallback, is 6 — `guidelines/UI.md` §3).

The shipped seam is `CarListCapacityReader.read(carContext)`
(`auto/src/main/java/com/naviveylin/auto/CarListCapacityReader.kt:50`, fallback `LIBRARY_DEFAULT = 6` at
`:40`) plus `FavoritesScreenMapper.page(rows, capacity, page)`
(`auto/src/main/java/com/naviveylin/auto/FavoritesScreenMapper.kt:49`) plus the clickable final row whose
wording is `R.string.more_items` (`auto/src/main/res/values/strings.xml:66`). `FavoritesScreen` applies all
three to its three levels (read on its started period, slice at `:227`, page row at `:237-239` from
`pageRow` at `:343`), and `AddressBookScreen` applies them from its `init` scope (read at `:97`, slice at
`:168`, page-row push at `:193-205`).

The screen is constructed by `MapScreen.kt:579` when the driver opens the history from the map's search
entry, and lives until the host pops it; it holds no observation of its own — the history is read once per
screen instance.

## Goals / Non-Goals

**Goals:** the car search history presents at most the host's rows and keeps the older searches one tap
away, on every host.

**Non-Goals:** changing the favourites or address-book sides of the capability (they already comply);
changing the store's `MAX_ENTRIES` or the phone's search history; the two sibling screens of `TODO.md`
§177 (§179 `PoiResultsScreen`, §180 `DiagnosticsScreen`); a new capability, a new string, a native/JNI
change, or a change to `guidelines/UI.md`.

## Decisions

### D1 — Apply the shipped seam, add nothing

`CarListCapacityReader.read` + `FavoritesScreenMapper.page` + the `More (n)` row, exactly as
`FavoritesScreen` and `AddressBookScreen` use them. The screen's list is a plain ordered sequence, so the
arithmetic is the mapper's already-tested one; no new capability, no new wording.

Eliminated by the evidence:

- **Let the host cap the list.** There is nothing to cap with: the library's own build contract is that the
  host *ignores* the items over its limit (`guidelines/UI.md` §3, quoted from car-app 1.7.0), and the
  measured AAOS host reports 100 rows while ignoring the rest silently. Waiting for the host is the defect.
- **Cap at a fixed constant** (e.g. 20, the number §179 carries, or the store's 50). It under-fills a host
  that reports more and still loses the tail on a host that reports fewer, and the requirement says the
  capacity SHALL come from the host rather than from a fixed constant.
- **Shorten the store instead** (`SearchHistoryRepository.MAX_ENTRIES` in `:app`). The 50 entries are the
  phone's history, correct where they are; shortening them to the smallest host's list budget would discard
  the phone's data to fit a car list, and it would reach into `:app` — two files and another capability's
  contract for one car screen's defect.
- **Cap the list without a page row** (silently truncate to the capacity). The oldest searches would then be
  unreachable, which the requirement's "no item SHALL be dropped without the next page offering it" and
  "Items beyond the capacity stay reachable" forbid — the visible symptom would survive in another form.

### D2 — Read the capacity once per screen, off the host thread

The read joins the screen's existing `init` scope: `scope.launch { withContext(Dispatchers.Default) {
CarListCapacityReader.read(carContext) } … invalidate() }`, and the field starts at
`CarListCapacityReader.LIBRARY_DEFAULT` so the first template — which the host may ask for before the answer
arrives — is already bounded. This is `AddressBookScreen`'s D2, the closest precedent: the search history has
no started-period observation to hang the read on, and one read per screen instance satisfies the
requirement's "Capacity is re-read per screen", because every page row pushes a new instance that asks for
itself.

Eliminated: **reading it inside `buildTemplate()`** — that method runs on the host's answering path, and the
archived capacity measurement (car-app 1.7.0 sources, `car-favorites-group-level` task 1.1) shows the query
is a binder call that belongs off that thread; a template build must not query the host.

### D3 — The page is a constructor parameter and the page row pushes a new screen

`page: Int = 0`, following `AddressBookScreen` (its design D3): the page row pushes another
`SearchHistoryScreen` carrying `page + 1`, so the host's own back navigation returns to the page that opened
it. The call site (`MapScreen.kt:579`) needs no edit.

Eliminated: **advancing the page in place** (re-slicing the current screen's list on the row tap). That makes
the page row a one-way jump with no way back to the previous page, which the requirement's "the way back to
the previous page SHALL exist" forbids.

## Blast radius

- `auto/src/main/java/com/naviveylin/auto/SearchHistoryScreen.kt` — one production file: the additive
  `page` parameter, the capacity field and read, the page slice and the page row. No other car screen, no
  `:core`, no `:app`, no resources, no manifest, no native code.
- `auto/src/test/java/com/naviveylin/auto/SearchHistoryScreenTest.kt` — new; the harness follows
  `AddressBookScreenTest` (mock `EntryPoints.get`, a fake host answering `getContentLimit`, the screen
  driven through `dispatchLifecycleEvent`).
- `TODO.md` §178 → `fixed-by`; the delta into `openspec/specs/car-list-row-limit/spec.md` on archive.

Threading: the host call stays off the host thread (the `init` scope's `Dispatchers.Default` hop); all state
writes and `invalidate()` stay on the screen's main dispatcher. Lifecycle: unchanged — the load and the
capacity read live on the screen's scope, which `carScreenScope` owns and `onDestroy` cancels.

## Risks / Trade-offs

- **The page row is the (capacity + 1)-th row of the list.** The shipped favourites lists and the address
  book have the same shape and the AAOS host renders their `More` row (the archived change's device run), but
  a host that enforced its list budget strictly would drop exactly that row and hide the remainder. → Keep
  the shape identical to the shipped one instead of inventing a reserved row, and record the search-history
  device check (capacity line + `More` row on the AAOS emulator) as a task, pending while no device is
  attached.
- **A pushed page is a new screen instance that re-reads the history.** The store can change between two
  pages (a new search is prepended; the 51st evicts the oldest), so a page slices the list as it is at that
  moment. The mapper's partition property holds per loaded list, and no entry becomes unreachable (the page
  row is recomputed from the current list); the same property the shipped favourites and address-book pages
  carry. → No mechanism is added for it in this change; the device task records what a host shows.
- **The new cases drive a real `Dispatchers.Default` hop** in the history load (the screen has no injectable
  dispatcher and this change does not add one). → The cases await the loaded template through the pattern
  `AddressBookScreenTest` uses; the await is a backstop, the assertion is on the state.

## Migration Plan

None needed: no persisted state, no schema, no API. Rollback is `git revert` of the one production file (and
dropping the new test file); the defaulted parameter keeps the existing call site compiling and behaving as
before.

## Open Questions

None that would change the specs, the approach or the tasks. The device verification (the host's reported
capacity with the search history open, and the `More` row on a stationary emulator) is recorded as a pending
task, not as a decision to make.
