# UI — the car surfaces (templates, renderer, search, details)

Rules and constraints for the surfaces that run on the Android Auto car display, distilled
from the OpenSpec requirements. Read before writing or changing a car screen, a car template
or anything the driver sees or taps.

The parity rule these surfaces are measured against is `UI.md` §1, and a deliberate deviation
from it names its platform constraint there. The cross-variant map presentation both variants
share is `UI.md` §8d; dark mode and text/localisation are `UI.md` §9, §10; and the maintenance
rule both documents follow is `UI.md` §20. The phone's own surfaces are
`guidelines/UIPhone.md` and `guidelines/UIPhoneMap.md`.

**Read the section that owns your topic, not this whole document.** The section list is
`grep -n '^## ' guidelines/UICar.md`; AGENTS.md's Documentation Map routes a kind of change
here.

---

## 2. Action glyphs on the car display

Source: spec `auto-destination-details` — "Details actions visually marked".

- **Rule: car details actions are visually marked and precede the attributes.** Actions SHALL be
  visually marked (leading unicode symbol) so they are distinguishable from attribute rows, and SHALL be
  positioned before the attribute rows.
- Current glyphs (text glyphs — no emoji, hosts render them reliably):
  - `▶` Navigate to
  - `◎` Show
  - `★` Add to Favorites
  - `☆` Remove from Favorites

## 3. Car-host template constraints (platform knowledge)

Verified against car-app 1.7.0 (`androidx.car.app`):

- **Rule: titled car actions are clickable list rows, never list actions.** `ListTemplate.Builder.addAction(...)` validates against
  `ACTIONS_CONSTRAINTS_FAB`: **icon-only, `maxCustomTitles = 0`**. A titled
  action throws `IllegalArgumentException: Action list exceeded max number of
  0 actions with custom titles` at template render time (crashed the AAOS
  emulator). → Titled actions MUST be **clickable list rows**
  (`Row.setTitle().setOnClickListener()`), never list actions.
- **Rule: pane rows are not actionable.** `PaneTemplate` rows: ~4–6 visible on most hosts, no scrolling, rows are
  **not actionable** — actions live at pane level.
- **Rule: a car list asks the host for its capacity and pages itself.** `ListTemplate` does **not** page on its own. The Car App Library's own build contract for
  `ItemList` is: "The number of items in the `ItemList` should be smaller or equal than the limit
  provided by `ConstraintManager#CONTENT_LIMIT_TYPE_LIST`. **The host will ignore any items over that
  limit**" — and the host, not the app, chooses that number. The library's packaged default is **6**
  (`androidx.car.app.R.integer.content_limit_list`, car-app 1.7.0, read out of the AAR), and
  `ConstraintManager.getContentLimit` returns that resource itself when its remote call fails. → A car
  list screen MUST ask the host for the capacity and present **at most** that many rows, ending with its
  own page row, because rows past the limit disappear with no signal (spec `car-list-row-limit`; the
  shipped implementation is `CarListCapacityReader` + `FavoritesScreenMapper.page`, change
  `car-favorites-group-level`).
  - The query is `carContext.getCarService(ConstraintManager::class.java)
    .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)`. `ConstraintManager` is
    `@RequiresCarApi(2)` while the manifest declares `minCarApiLevel = 1`, so a screen guards on
    `CarContext.getCarAppApiLevel()` (which throws `IllegalStateException` before the handshake) and
    falls back to the library default rather than calling it. The service factory is registered
    unconditionally, so it is the level guard, not a `try`, that keeps the API-1 path correct.
  - The call is a plain binder call (`HostDispatcher.dispatchForResult` →
    `RemoteUtils.dispatchCallToHostForResult`); nothing on that path checks the main thread, so the read
    runs on a screen's `backgroundDispatcher` — never inside a template build.
  - The number is a runtime fact and the app records it instead of hard-coding it: one line per car list
    screen start, `Diag/SCREEN: list capacity N rows (host, car API level M)` (or
    `(library default, <reason>)`). Measured 2026-10-10 on this AVD's templates host
    (`Automotive_Distant_Display_with_Google_Play`, `emulator-5554`, change `car-favorites-group-level`
    task 1.1): **100 rows at car API level 8** — the *host's* number, not the library's floor. So neither
    6 nor 100 may be read as "the car list limit": always take the query, and always leave the remainder
    reachable. No projection host was available in that session, so a projected host's number is
    unrecorded.

- **Rule: the real car-app API surface is checked before a car screen is written.** Check the real car-app API surface before writing a car screen or screen test (§40.29): getter names,
  the resolved lifecycle version, and no `Lifecycle.getObservers()`. Drive the lifecycle through
  `dispatchLifecycleEvent` in the order a host would — ON_CREATE + ON_START before ON_DESTROY, never straight
  to DESTROYED.

## 3a. Map renderer startup (never on the host thread)

The car map renderer MUST initialize off the car-app main thread (spec:
`auto-map-renderer` — "Renderer initialization off the car-app main thread"):

- **Rule: the client is first-touched off the host thread.** The Hilt `OSMScoutClient` singleton (native `build()`:
  dlopen + stylesheet sync) and the initial-viewport resolution (saved
  viewport JSON / `getDatabaseBoundingBox`) run on `Dispatchers.Default` in a
  screen-scoped coroutine — never in a `Screen` constructor/`init` block and
  never via a lazy renderer forced from a main-thread call site (surface
  callbacks, lifecycle observers, settings coroutines, template builders). A
  frozen host thread during warmup delayed template delivery for every screen
  in the session. The car providers are injected as `Provider`/`Lazy` for this
  reason: resolving one must not build the client.
- **Rule: a screen's observation body counts as a host call site.** A screen's observation body (change
  `fix-car-favorites-loading-stall`): `CarScreenObservations.observe` runs its
  body on the observation scope's main dispatcher, started from `onStart`, so a
  provider resolved there first-touches the client on the host thread and blocks
  it on the singleton lock while the session's warmup is mid-build. An
  observation therefore resolves its providers inside
  `withContext(backgroundDispatcher)`, on the injectable
  `backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default` that
  `MapScreen` introduced and `FavoritesScreen`, `DetailsScreen`,
  `MapScreenObservations` and `DetailsScreenObservations` now share; only the
  effects (state writes, `invalidate()`) stay on the main dispatcher. The
  favourites screen is the sharpest instance because the session's root is a
  menu, so it is often the first touch of the client in a session — and the
  loading row it shows for that wait is honest only while the store is
  uninitialised, which is why the store's readiness is its own signal
  (`AutoFavoritesProvider.storeReady`) rather than a guess made from an empty
  favourites map. **That wait is not the row's deadline** (change
  `fix-car-favorites-cold-template`; the wait's measurements are `UICar.why.md` §3a): the hop is a dispatch onto a shared
  background dispatcher, and on a cold session the map's first frame holds those
  threads and the CPU, so the hop entered 623–1715 ms after the observation
  started while the store had been ready for seconds. A resolved provider
  therefore hands its current state out without a dispatch
  (`AutoFavoritesProvider.snapshotIfResolved`) and `FavoritesScreenObservations`
  publishes it before the hop, so the row stands only for a store the provider has
  not resolved — on device the published state then arrives in 1–9 ms instead of
  623–1719 ms.
- **Rule: host callbacks retain state only.** `onSurfaceAvailable` buffers the surface
  DPI (`RendererGate.surfaceDpi`) and hands it to the renderer as that renderer's
  projection DPI — resolving the client from a callback is forbidden even when the
  renderer already exists, because the callback runs on the host thread. The
  client holds no DPI of its own any more (spec: `render-projection-dpi`): every
  render request carries the DPI of the surface it draws on. The same
  applies to the stylesheet day/night flag: it is *published*
  (`RendererGate.daylightPush`, one distinct request per push so a dropped one
  stays retryable) and applied by a background collector, never set from the
  delivery callback (change `fix-car-surface-ownership-and-host-callbacks`).
- **Rule: construct-and-publish is atomic on main.** The heavy work (client,
  viewport) returns from `withContext`, and the `AutoMapRenderer` is constructed
  and published on the main thread with no suspension in between, so a cancelled
  init can never drop an already-constructed renderer (its loops would poll
  forever).
- **Rule: a surface screen's readiness is a `RendererGate`.** All surface screens use [`RendererGate`] (buffered
  last-wins slots, replay order surface → dark → viewport intents → marker →
  frames). Pre-ready surface delivery, dark presentation, GPS fixes, follow
  re-center and frame requests are replayed on readiness; `onDestroy` cancels
  a still-running init and shuts down a published renderer.
- **Rule: one owner draws, and `onStop` detaches.** Each screen owns its renderer, and
  the session owns the one car surface. The session revokes the surface from the
  owner it supersedes (`CarSurfaceOwner.onCarSurfaceRevoked`, delivered from
  `attach`) before the incoming owner may draw, and a screen that stops calls
  `rendererGate.detachSurface()` after `pause()`, so a stopped screen holds no
  surface (and no overrun buffer) and cannot lock the surface another screen is
  drawing through during the start-before-stop transition.
- **Rule: the pan handler takes a renderer supplier.** `MapPanHandler` takes a renderer *supplier* — constructing
  it must never force the renderer (template build happens on the main
  thread).
- **Rule: every car surface screen initializes the client asynchronously.** Applies to `MapScreen`, `NavigationScreen`, `FreeDrivingScreen`,
  `DetailsScreen` (all four own a surface and a renderer; `DetailsScreen` gained
  the same async init in change `fix-aaos-host-crash`).

## 3b. Settings-screen loading (never trust a single invalidate)

The car settings screens must self-recover from a stalling or silently
dropped template refresh (spec: `auto/preferences` — "Settings screens never
wedge on a loading placeholder"; real hosts can drop `invalidate()` requests
while the template pipeline is busy). Settings screens with a loading
placeholder MUST use [`SettingsLoadGuard`]:

- **Rule: a stalled placeholder is re-invalidated by a one-shot watchdog.** A one-shot watchdog re-invalidates when the placeholder is
  still showing after the load timeout — the first refresh may have been
  lost.
- **Rule: every invalidate is guarded.** Every `invalidate()` is wrapped; a host failure
  (`HostException`) must never kill the screen's load coroutine.
- **Rule: recovery is capped and ends in an error row.** Recovery invalidates are capped (two per load cycle,
  mirroring the surface-refresh cap) — exhaustion renders an explicit error
  row with Retry, never an infinite spinner.
- **Rule: a screen re-renders on resume.** The screen re-renders on `onStart`/re-visibility,
  so a stale placeholder template from a previous visit cannot stick.
- **Rule: settings-bearing screens re-read on re-visibility.** Settings-bearing screens MUST re-READ the
  persisted settings on `onStart`, not just re-render the last in-memory
  snapshot — a value changed in a pushed picker (vehicle position, overspeed
  delta) appears on the row when the picker pops back (spec:
  `auto/preferences` — "Re-read on re-visibility").
- **Rule: a picker persists before it dismisses.** Picker screens that close immediately on
  selection MUST await the settings write before popping — dismissing the
  picker must never drop the write (spec: `auto-map-layout` — "Anchor
  selection survives immediate dismissal").
- **Rule: `onDestroy` cancels the screen coroutine scope.** `onDestroy` cancels the screen coroutine scope.
- Applies to `PreferencesScreen`, `VehicleAnchorPickerScreen`,
  `OverspeedDeltaPickerScreen`.

## 3c. Car wait notices (a wait the driver can see and leave)

A car screen MUST NOT leave the driver looking at nothing while the app works: any wait
that can last longer than a blink gets a notice, and a notice never outlives the thing it
describes (spec: `route-calculation-feedback`). The route-calculation wait notice
(`RouteCalculatingScreen`) is the reference implementation:

- **Rule: a wait notice appears only after a delay, armed for a calculation token.** The notice is pushed only after `CALCULATION_NOTICE_DELAY_MS`
  (400 ms) with the calculation still in flight — a fast result must never flash a screen.
  The delay is armed for a **calculation token**: a calculation that ends, or that a newer
  request superseded, before the delay elapses raises no notice.
- **Rule: one notice, updated in place.** A second state change (the next percentage) MUST update
  the notice that is up and `invalidate()` it — never push a second screen.
- **Rule: a notice keeps a minimum visible dwell.** A notice the navigation view replaces stays up for at least
  `CALCULATION_NOTICE_MIN_DWELL_MS` (700 ms) after it appeared, measured from the landed push — a route
  that arrives just after the delay must not swap a screen the driver has only glanced at (the
  measurement that set the floor is `UICar.why.md` §3c). The dwell is decided by
  `calculationNoticeDwellRemainingMs()` and armed as one cancellable job; it MUST NOT delay a
  cancellation (the driver's own tap) or the error notice that answers a failure with no navigation
  view below the notice.
- **Rule: a notice never outlives its work.** When the calculation ends the notice is removed by identity
  (`ScreenManager.remove`, never `popToRoot()`, which takes the navigation view with it);
  a calculation that ended while the session was not started MUST NOT leave a notice behind.
- **Rule: a notice offers a way out only when leaving is safe.** The notice offers Cancel while navigation is **not** active,
  and that Cancel is its **only** exit — car-app 1.7's `Screen` has no back callback (`onBackPressed`
  does not exist; back is a host-driven stack pop), so a back affordance could only *pop* the notice
  while the routing work keeps running invisibly. During a reroute guidance is still live, so the
  notice is neither cancellable nor dismissable — a driver off route must keep the instruction panel
  (spec: `auto/navigation-view`).
- **Rule: the wait notice bounds its host traffic.** The percentage is displayed in 5 % steps
  (`displayedCalculationPercent`), so a progressing calculation costs ~20 host pushes, not
  one per percent.
- **Rule: a wait notice is guarded like every other host path.** The template build goes through `carPaneTemplate`,
  the push/removal through `guardedHostCall`, and the sync is deferred (and re-applied once)
  while the session is not started — see the car-host rules in AGENTS.md.

## 4. Details attribute list (Android Auto)

Source: spec `auto-destination-details` — all description attributes shown.

- **Rule: the details attribute list order is fixed.** Row order: Coordinates → Address → Area → every description attribute in
  native order. No row cap; the host pages long lists.
- **Rule: every Description attribute is reachable.** All attributes returned by the Description API (opening hours, phone,
  website, …) MUST be reachable.
- **Rule: the street/address row is deduplicated.** The merged Address row covers `Location/Address` and
  `Location/Location`; those entries never appear again.
- **Rule: an empty attribute entry is omitted.** Entries with an empty label or empty value are omitted.
- **Rule: the car and the phone share one details resolution.** Address, area, and title resolution is shared with the phone dialog via
  `DetailsResolver` in `:core` (phone is the lead view).

## 6b. Android Auto search surface (SearchTemplate)

Source: specs `auto-search`, `auto-search-suggestions` (change `unify-auto-search`).

- **Rule: the car search shows empty-query suggestions.** The car `SearchTemplate` shows
  **empty-query suggestions** instead of an empty list: mode rows + recent searches.
- Mode rows: "Search POIs near me" (opens the POI category picker) and
  "Search contacts" (opens the address-book person search, shown only while
  `READ_CONTACTS` is granted — same gate as the root list entry).
- **Rule: recent searches come from the shared history store.** Same JSON store as the
  phone, so phone searches appear on the car screen. Tapping a history row
  pushes a prefilled `SearchScreen` (the host owns the field text after
  construction, so the query cannot be set inline — design D2).
- **Rule: typing replaces the suggestions.** As soon as the user types, the mode/history
  rows are replaced by places search results; clearing the field restores the
  suggestions.
- **Rule: favorites are searchable on the car screen too.** (spec `favorite-search`, parity with
  `UIPhone.md` §6a.) The same rules as the phone dialog — case-insensitive
  substring name match on queries ≥ 2 chars, favorite hits listed **above**
  native results with a heart icon, native results whose coordinates match an
  existing favorite (~11 m) heart-marked too, and a native result identical
  to a favorite hit deduplicated (the favorite hits win). Same matching,
  prioritization, marking, and deduplication on both surfaces.
- **Rule: the no-results state keeps the mode rows.** A query with no results shows the
  "No results found" row followed by the mode rows, so the driver can pivot
  to POI/contacts search without clearing the field.
- **Rule: the car search shows the same order, marking and distance as the phone.** (spec
  `search-result-ranking`, change `search-result-ranking`.) The car pipeline
  ranks with the same rule and the same candidate/display limits, marks rows
  with the same composite glyph, and shows the distance as a second text line
  using the same km formatting as `UIPhone.md` §6a. The distance reference is the last known
  GPS fix, else the car map viewport center — the map screen passes its own
  renderer's center while it is the screen that owns the map, and every car map
  screen publishes the center of the last frame it actually drew as it stops
  drawing — never the pending render target of a map that stopped before its first
  frame landed — so a search opened from the root or the history list measures from
  the map the driver last saw instead of showing no distance at all (the defect is `UI.why.md` §8d). The
  spec's "neither exists" case therefore needs a session whose map has not drawn a
  frame yet — only then are the results ordered by tier and quality without distances. **Platform
  constraint (documented deviation)**: a car `Row` has a single image slot and
  at most two text lines and carries no accessibility text channel, so the
  marking is glyph-only on the car and the two facts cannot be announced — the
  phone row's content description has no car equivalent.
- **Rule: the car search row's content is the shared composition.** (change `search-result-row-content`.) The title is
  `label - city`, the one description line is the postal area — the row used to print that code
  twice, because `region[]` is `[adminRegion, postalArea]` and not the hierarchy its javadoc
  promises — and the second text line is the distance. The type artwork the entry's stylesheet gives
  it joins the marking in the row's single image, resolved off the host thread and read from the
  shared cache while the template is built. The street address a phone row resolves for a name hit has
  no room here: a car `Row` allows two text lines and both are taken — the same documented,
  platform-constrained deviation as the marking, and it removes nothing the driver cannot reach, since
  the details screen shows the address.
- **Rule: the car search's action-phrase labels are a documented parity deviation.** The suggestion rows
  use action phrases ("Search POIs near me", "Search contacts"), not the
  phone's mode labels ("Places" / "POIs" / "Contacts"). This is the
  established AA pattern (the root list already deviates: "Points of
  interest" vs "POIs") and Google Maps uses action phrases on the car
  display. The elements are new, not shared, so `cross-variant-ui-parity`
  (`UI.md` §1) is unaffected.
- **Rule: the car search keyboard stays shown by default.** The keyboard stays shown by default (`setShowKeyboardByDefault(true)`);
  the suggestion rows are scrollable below the field.
- **Rule: the car search has the same request lifecycle as the phone.** (spec `search-request-lifecycle`, change
  `fix-search-cancellation`): the `SearchTemplate`'s search runs in its own slot, debounced by the same 300 ms,
  a query shorter than 2 characters runs nothing, and leaving the screen abandons the running search and
  cancels the screen's scope — so a stopped screen makes no host call for a result. `UIPhone.md` §6a holds
  the phone's side of the same rule.

