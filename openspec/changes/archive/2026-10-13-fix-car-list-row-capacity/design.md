# Design — fix-car-list-row-capacity

See `proposal.md` — Why for the root cause and its evidence. This document records the one fix, the
alternatives the evidence eliminated, the blast radius and the rollback.

## Context

`AddressBookScreen` (`auto/src/main/java/com/naviveylin/auto/AddressBookScreen.kt`) is a car
`SearchTemplate` whose rows are `AddressBookScreenMapper.filterContacts(allContacts, query)` — one `Row`
per contact with a postal address, built at `:113-121` into one `ItemList` with no cap. The contact load
runs in the screen's `init` scope on `Dispatchers.Default` (`:64-80`). The Car App Library's contract for
that list is that the host ignores every item over the capacity it reports (measured 100 rows at car API
level 8; the library's packaged default, and this app's fallback, is 6 — `guidelines/UI.md` §3).

The shipped seam is `CarListCapacityReader.read(carContext)`
(`auto/src/main/java/com/naviveylin/auto/CarListCapacityReader.kt:50`, fallback `LIBRARY_DEFAULT = 6` at
`:96`) plus `FavoritesScreenMapper.page(rows, capacity, page)`
(`auto/src/main/java/com/naviveylin/auto/FavoritesScreenMapper.kt:49`) plus the clickable final row whose
wording is `R.string.more_items`. `FavoritesScreen` applies all three to its three levels: it reads the
capacity on its started period (`:140` via `:164-172`), slices at `:227` and adds the row at `:237-239`
from `pageRow` (`:343-344`).

## Goals / Non-Goals

**Goals:** the address book presents at most the host's rows and keeps the remainder one tap away, on
every host, with the page continuing the list the driver was looking at.

**Non-Goals:** changing the favourites side of the capability (it already complies); the three sibling
screens of `TODO.md` §177 (§178 `SearchHistoryScreen`, §179 `PoiResultsScreen`, §180 `DiagnosticsScreen`);
a new capability, a new string, a native/JNI change, or a change to the search provider's candidate count.

## Decisions

### D1 — Apply the shipped seam, add nothing

`CarListCapacityReader.read` + `FavoritesScreenMapper.page` + the `More (n)` row, exactly as
`FavoritesScreen` uses them. The screen's list is a plain ordered sequence, so the arithmetic is the
mapper's already-tested one; no new capability, no new wording.

Eliminated by the evidence:

- **Let the host page the list.** There is nothing to page with: the library's own build contract is that
  the host *ignores* the items over its limit (`guidelines/UI.md` §3, quoted from car-app 1.7.0), and the
  measured AAOS host reports 100 rows while ignoring the rest silently. Waiting for the host is the defect.
- **Cap at a fixed constant** (e.g. 20, the number §179 and §180 carry). It under-fills a host that reports
  more and still loses the tail on a host that reports fewer, and the requirement says the capacity SHALL
  come from the host rather than from a fixed constant.
- **Replace the `SearchTemplate` with a `ListTemplate`.** The screen exists to search contacts by name
  (`guidelines/UI.md` §6b: the car search surface is a `SearchTemplate`); a list template has no search
  field, so the fix would remove the screen's purpose.

### D2 — Read the capacity once per screen, off the host thread

The read joins the screen's existing `init` scope: `scope.launch { withContext(Dispatchers.Default) {
CarListCapacityReader.read(carContext) } … invalidate() }`, and the field starts at
`CarListCapacityReader.LIBRARY_DEFAULT` so the first template — which the host may ask for before the
answer arrives — is already bounded. The requirement asks for the capacity per screen, which one read per
screen instance satisfies; the address book, unlike `FavoritesScreen`, has no started-period observation
to hang it on.

Eliminated: **reading it inside `buildTemplate()`** — that method runs on the host's answering path, and
the archived capacity measurement (car-app 1.7.0 sources, `car-favorites-group-level` task 1.1) shows the
query is a binder call that belongs off that thread; a template build must not query the host.

### D3 — The page is a constructor parameter and the page row pushes a new screen

`page: Int = 0`, following `FavoritesScreen` (its design D4): the page row pushes another
`AddressBookScreen` carrying `page + 1`, so the host's own back navigation returns to the page that opened
it.

Eliminated: **advancing the page in place** (mutating the current screen's rows). That makes the page row
a one-way jump with no way back to the previous page, which the requirement's "the way back to the
previous page SHALL exist" forbids.

### D4 — The page carries the active search text; a changed search text returns to page 0

New parameter `initialQuery: String = ""`, used both to filter the loaded contacts and to keep the page
slicing the *filtered* sequence; the page row pushes `initialQuery = query`. A changed search text resets
the shown page to 0 and leaves the page number alone when the text repeats.

Forced by the capability, not a product choice: the screen's items are the filtered contacts, so a page
that dropped the text would slice `allContacts` instead — it would present contacts the filter excluded
and skip matching ones, breaking "Every item is reachable exactly once". (The evidence shows the shape: 7
matches behind a `More (1)` row would open a page of unfiltered contacts.) Resetting on a *changed* text
keeps the driver on a real page: the filter produced a new list, whose first page is where a look at it
starts. `SearchScreen.kt:103-105` already carries a query into a pushed screen's field
(`setInitialSearchText`), which is the same mechanism here.

Eliminated: **paging `allContacts` and ignoring the filter** — the rows the driver tapped were filtered,
so the continuation would be a different list; **hiding the page row while a search text is active** — the
matching tail would then be unreachable, i.e. the defect survives under a search.

## Blast radius

- `auto/src/main/java/com/naviveylin/auto/AddressBookScreen.kt` — one production file: the two additive
  parameters, the capacity field and read, the page slice and the page row. No other car screen, no
  `:core`, no resources, no manifest, no native code.
- `auto/src/test/java/com/naviveylin/auto/AddressBookScreenTest.kt` — new; the harness follows
  `FavoritesScreenTest` (mock `EntryPoints.get`, a fake host answering `getContentLimit`, the screen
  driven through `dispatchLifecycleEvent`).
- `TODO.md` §177 → `fixed-by`; the delta into `openspec/specs/car-list-row-limit/spec.md` on archive.
- The existing `AddressBookScreen` call sites (`RootScreen.kt:147`, `SearchScreen.kt:265`) need no edit:
  both new parameters are defaulted.

Threading: the host call stays off the host thread (the `init` scope's `Dispatchers.Default` hop); all
state writes and `invalidate()` stay on the screen's main dispatcher. Lifecycle: unchanged — the load and
the capacity read live on the screen's scope, which `carScreenScope` owns.

## Risks / Trade-offs

- **The page row is the (capacity + 1)-th row of the list.** The shipped favourites lists have the same
  shape and the AAOS host renders their `More` row (the archived change's device run), but a host that
  enforced its list budget strictly would drop exactly that row and hide the remainder. → Keep the shape
  identical to the shipped one instead of inventing a reserved row, and record the address-book device
  check (capacity line + `More` row on the AAOS emulator) as a task, pending while no device is attached.
- **A pushed page is a new screen instance.** Its contacts are re-read from the provider and the search
  field is re-filled from `initialQuery`; a host that delivers no `onSearchTextChanged` for a prefilled
  field is covered because the page filters by `initialQuery` itself. → `setInitialSearchText` is set only
  for a non-blank carried text.
- **The new cases drive a real `Dispatchers.Default` hop** in the contact load (the screen has no
  injectable dispatcher and this change does not add one). → The capacity case makes the host report the
  library default, so the asserted cap holds whether or not the asynchronous answer has landed; the page
  case awaits the loaded template through the pattern the screen's existing
  `TemplateFaultIsolationTest` case uses. The await is a backstop; the assertion is on the state.

## Migration Plan

None needed: no persisted state, no schema, no API. Rollback is `git revert` of the one production file
(and dropping the new test file); the defaulted parameters keep every existing call site compiling and
behaving as before.

## Open Questions

None that would change the specs, the approach or the tasks. The device verification (the host's reported
capacity with the address book open, and the `More` row on a stationary emulator) is recorded as a pending
task, not as a decision to make.
