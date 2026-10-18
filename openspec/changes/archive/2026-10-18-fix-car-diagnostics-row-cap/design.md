# Design — fix-car-diagnostics-row-cap

See `proposal.md` — Why for the root cause and its evidence. This document records the one fix, the
alternatives the evidence eliminated, the blast radius and the rollback.

## Context

`DiagnosticsScreen` (`auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt`) is a car `PaneTemplate`:
`init` starts a background load (`:60-67`), `loadEntries` publishes
`DiagnosticsLog.readEntriesAsync().takeLast(MAX_ROWS).asReversed()` (`:178`) into `entries`, and
`buildTemplate` adds the disclosure row plus one `Row` per published entry into a `Pane.Builder` (`:92-150`).
`MAX_ROWS = 20` (`:186`) is the window's only bound. The screen is constructed by `RootScreen.kt:163` and
`MapScreen.kt:583` and lives until the host pops it.

Two measured platform facts decide the shape (both `guidelines/UI.md` §3):

- the host **ignores** the rows over its list capacity — the library's own contract for `ItemList`, whose
  packaged default is 6 and whose AAOS measurement is 100 rows at car API level 8;
- `PaneTemplate` rows are **not actionable** and do not scroll; a host shows ~4–6 of them.

So the shipped screen was wrong in two ways at once, and one mechanism explains both: it presents its window
in a container that has neither a host-supplied bound nor a page, so on any host the visible depth is decided
by something other than the screen's own window.

The shipped seam is `CarListCapacityReader.read(carContext)` (`:50`, fallback `LIBRARY_DEFAULT = 6` at `:40`)
plus `FavoritesScreenMapper.page(rows, capacity, page)` (`FavoritesScreenMapper.kt:49`) plus the clickable
final row worded `R.string.more_items` (`auto/src/main/res/values/strings.xml:66`). `FavoritesScreen`,
`AddressBookScreen` and `SearchHistoryScreen` apply all three; `SearchHistoryScreen` is the closest precedent
(same shape: an unordered-in-length source, a background load in `init`, one `ListTemplate`).

## Goals / Non-Goals

**Goals:** every entry of the diagnostics window is readable on the car, at the depth the connected host
allows, with nothing dropped silently.

**Non-Goals:** the window's size (20 stays), the log file's caps and retention, the phone diagnostics viewer,
`MAX_TITLE_CHARS`, and the sibling entry `§179` / issue #113 (`PoiResultsScreen`), which is held for an owner
decision of its own.

## Decisions

### D1 — The screen becomes a `ListTemplate` and takes the shipped capacity seam

`PaneTemplate` → `ListTemplate` (built through `carListTemplate`, so the fault-isolation wrapper matches the
declared type), the disclosure row as the list's first item, then
`FavoritesScreenMapper.page(loaded, capacity = rowCapacity, page = page)` rows and, when the slice leaves
entries, the `More (n)` page row. `MAX_ROWS = 20` stays as the window the pages partition.

Eliminated by the evidence:

- **Keep the pane and cap it at the host's pane capacity.** A pane cannot offer the remainder: its rows take
  no click listener (`guidelines/UI.md:289-290`), so the entries past the host's ~4–6 visible rows would be
  unreachable, which the capability's "Items beyond the capacity stay reachable" forbids. The requirement
  would be satisfied on paper and the driver's failure mode left in place.
- **Keep the constant and add a "showing N of M" row.** The requirement says the capacity SHALL come from the
  host rather than from a fixed constant; and the count row is itself a row, so it pushes the window further
  over a host's budget instead of making the rest reachable.
- **Page the whole file instead of the 20-entry window.** That reverses a recorded decision: the file holds
  the history under a size cap and a 7-day retention, and this screen is deliberately a bounded recent view
  of it (`guidelines/Logging.md` §3, and the entry's own scope note). The defect is the bound's source and
  its container, not the bound.
- **Reorder `readRowCapacity`/`loadEntries` so the screen's own capacity line stays out of the window.** That
  line is a legitimate diagnostics entry, and the ordering between the two coroutines is a race, not a
  guarantee: the fix would be an accident of scheduling. The window is "the recent entries", whatever they
  are — and a page re-reads the window, exactly as the shipped search-history page does.

### D2 — Read the capacity once per screen, off the host thread

The read joins the screen's existing `init` scope: `scope.launch { withContext(Dispatchers.Default) {
CarListCapacityReader.read(carContext) } … invalidate() }`, with the field starting at
`CarListCapacityReader.LIBRARY_DEFAULT` so the first template — which the host may ask for before the answer
arrives — is already bounded. This is `SearchHistoryScreen`'s D2 verbatim: the screen has no started-period
observation to hang the read on, and one read per instance satisfies "Capacity is re-read per screen",
because every page row pushes a new instance that asks for itself.

Eliminated: **reading it inside `buildTemplate()`** — that method runs on the host's answering path, and the
query is a binder call (`guidelines/UI.md:307-309`); the archived precedent states the same rule.

### D3 — The page is a constructor parameter and the page row pushes a new screen

`page: Int = 0`, following `SearchHistoryScreen`: the row pushes a new `DiagnosticsScreen` carrying
`page + 1` through `armScreenPush`, so the host's own back navigation returns to the page that opened it.
Both call sites need no edit; the constructor is additive.

Eliminated: **advancing the page in place** (re-slicing the current screen's list on the row tap) — that
makes the page row a one-way jump with no way back, which the requirement's "the way back to the previous
page SHALL exist" forbids.

### D4 — The disclosure stays the first row of every page and is not a window entry

`auto-diagnostics` requires the disclosure to reach the driver before the entries, and it is a static row,
not a log entry. Counting it against the host's capacity would shrink the window by one row per page for no
requirement's sake; leaving it out keeps the capacity meaning "window entries shown", which is exactly what
the delta's diagnostics scenario asserts. The page row is the same kind of row and is likewise not an entry —
the shipped screens already carry it beyond the item count, and the AAOS host renders it.

## Blast radius

- `auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt` — one production file: the template kind, the
  additive `page` parameter, the capacity field and read, the page slice and the page row.
- `auto/src/test/java/com/naviveylin/auto/DiagnosticsScreenTest.kt` — the two red cases plus a template-aware
  row helper, a fake host (`ConstraintManager`) and a recording `ScreenManager`.
- `auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt`,
  `GermanRenderingTest.kt`, `TemplateFaultIsolationTest.kt` — they read this screen's template kind (row
  titles, the German disclosure, the error-template case); each is a one-line adaptation.
- Threading: unchanged in kind — the file read and the host query stay on `Dispatchers.Default`, all state
  writes and `invalidate()` stay on the screen's main dispatcher, and the scope's `onDestroy` still cancels.
- Lifecycle and the guarded seam: unchanged (`carScreenScope`, `carListTemplate`, `armScreenPush`).
- No `:core`, `:app`, resource, manifest, native/JNI or flavour change; no new string, no new dependency.

## Risks / Trade-offs

- **The page row is the (capacity + 1)-th row of the template**, and the disclosure row is ahead of that. The
  shipped lists have the same shape and the AAOS host renders their `More` row (the archived device run), so
  this change keeps the shape identical instead of inventing a reserved-row arithmetic; the device follow-up
  task records what a host shows.
- **A pushed page re-reads the window**, so a page can be composed of a slightly different list than the page
  that opened it (the screen's own capacity line, a newer entry). The mapper's partition holds for the list it
  is given, which is the same property the shipped search-history page carries; the cases assert the page's
  rows and its remaining count, not a particular line.
- **The screen's own `list capacity …` line is inside its window** — a diagnostics screen showing its own
  measurement is the intent of that line (`spec auto-diagnostics`), and the row titles it produces carry no
  `entry-<n>`, so nothing in the capability's arithmetic depends on it.

## Migration Plan

None needed: no persisted state, no schema, no API. Rollback is `git revert` of the one production file and
the four test files; the defaulted parameter keeps the call sites compiling and behaving as before.

## Open Questions

None that would change the specs, the approach or the tasks. The device verification (the host's reported
capacity with the diagnostics screen open, and the `More` row on a stationary emulator) is recorded as a
pending task, not as a decision to make.
