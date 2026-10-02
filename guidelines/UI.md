# UI Guidelines — Phone & Android Auto

Rules and constraints for user-facing UI across the two variants (phone/tablet
app and Android Auto car display), distilled from the OpenSpec requirements.
Read before adding or changing UI elements — especially anything that exists in
**both** variants.

Target behavior: users get a consistent experience on both surfaces — same
labels, same visual hierarchy, same semantics — with deviations only where a
car-host platform constraint makes them unavoidable.

---

## 1. Cross-variant UI parity (general rule)

Source: spec `cross-variant-ui-parity` (change `align-details-actions-and-shared-data`).

- Similar UI elements in the phone and Android Auto variants SHALL use the same
  **labels** and the same **visual style** wherever the platform constraints of
  both variants allow it.
- When a car-host constraint forces a difference (e.g. rows instead of buttons,
  glyph marks instead of icons), deviate **only as much as required** and keep
  the **same label**.
- Primary/secondary/destructive hierarchy must be reflected in both variants'
  styling where the platform allows.

### Current parity decisions

| Element | Phone | Android Auto |
|---|---|---|
| Primary navigation action | "Navigate to" (filled button) | "▶ Navigate to" (first clickable row) |
| Show on map | "Show" (outlined button) | "◎ Show" (clickable row) |
| Add favorite | "Add to Favorites" (outlined button) | "★ Add to Favorites" (clickable row) |
| Remove favorite | "Remove from Favorites" (error-colored button) | "☆ Remove from Favorites" (clickable row) |
| Open-source license list | Reachable from About → "Open source licenses": components with identifiers, full license texts, links for licenses whose terms stay with their owner | Not surfaced — the car About screen keeps app identity and the map-data attribution only |
| Auto-zoom magnification changes | Animated — `smooth-zoom` scales the displayed map from the current scale toward the target while the native render is queued, and a change farther than the window the frame in hand can serve is applied as a sequence of steps, each step a native render at the magnification it displays (`ZoomWalk`), one step per landed frame; the animation pivots on the vehicle's resolved follow anchor while a follow mode is active | Animated by the same observable contract, implemented in the renderer — a magnification request larger than the blit window is WALKED across rendered frames (`AutoMapRenderer.advanceZoomWalk`, spec `auto-speed-zoom` — Auto-zoom entry transition), also pivoting on the resolved follow anchor. The observable contract (no single-frame jump, no frame showing a magnification the frame in hand cannot serve, anchored on the vehicle, ends on an exact render) is the parity requirement, the mechanism (Compose display scale over walked renders vs renderer walk) is not shared. Car gesture/zoom-button paths keep their immediate response; the phone walks every animated change, and a programmatic camera fit lands directly on both surfaces |
| Reorder favorites inside a group | Drag handle on each favorite row in the group detail list (`sh.calvin.reorderable`); the position the drag ends in is persisted | Not offered — Car App Library templates have no drag gesture. The car `PlaceListTemplate` shows the **same stored order** read-only, so the data is at parity, the interaction is not (see below) |
| Reorder favorite groups | Long-press drag handle on each group card in the grid (`sh.calvin.reorderable`, `rememberReorderableLazyGridState`); the position the drag ends in is persisted — change `reorder-favorite-groups` | Not offered — same platform constraint as the favorite order, and a per-header move action would duplicate a management task the phone owns. The car list renders the **same stored group order** read-only, so the data is at parity, the interaction is not (see below) |
| Move a favorite into another group | Row action on the favorite (overflow menu → "Move to group", shared wording `move_favorite_to_group`) opening a destination dialog that lists the other groups plus "New Group" (shared wording `new_group_title`) — change `move-favorite-between-groups` | Not offered — the destination-selection step has no template equivalent and the screen is driver-facing. The car `PlaceListTemplate` renders the resulting grouping under the destination group's header, so the data is at parity, the interaction is not (see below) |
| Reorder starred favorites | Long-press drag on a starred chip in the favorites sheet's chip bar (`sh.calvin.reorderable`, `rememberReorderableLazyListState`), which renders the one starred order spanning all groups — the position the chip is released at is persisted — change `order-starred-favorites` | Not offered — same platform constraint as the other two orders. The car's starred-favorites screen renders the **same stored starred order** read-only, as one list without group headers (a cross-group sequence cannot be rendered as group blocks), so the data is at parity, the interaction is not (see below) |
| Stylesheet could not be loaded | Non-blocking snackbar next to the map with the shared wording (`MapStyleLoadReporter` → `uiState.snackbarMessage`) | **Same wording**, also non-blocking, but a different surface: a one-shot low-importance notification (channel `map_style`, silent, auto-cancel) — the car templates have no general message slot, so `CarStyleLoadNotifier` is used instead of a template change. Guidance is never interrupted on either surface (change `fix-stylesheet-load-crash`, design D2) |
| Car session live while the phone UI is open | Advisory indication on the map (centre-left pill, shared wording `car_session_active_indicator`, translated): "Navigation on car display". Informational only — no map, search or navigation control is disabled while a car session is live (change `shared-resource-arbitration`, spec `car-session-presence`) | Not shown — the car surface is the session, so the indication would tell the driver nothing (platform constraint, not an omission) |
| Route requested without the precise location grant | Actionable dialog with the shared wording (`location_precise_required_navigation`, `PreciseLocationRequiredDialog`): "Grant precise location" re-requests the permission while the platform can still ask, and opens the app's system settings once it will not ask again — no route request reaches the routing engine meanwhile (change `fix-location-permission-scope`, spec `location-permissions` — Starting navigation requires precise location) | **Same wording**, non-blocking: the refusal is published on the shared navigation state, so the session shows it in the existing guarded error notice (one row, back action). The car SHALL NOT launch a settings screen — platform constraint, so the driver is told to grant it on the phone / in the system settings. Free driving and the map keep working on both surfaces with approximate location |

### Why the phone is not locked during a car session

The two surfaces share one navigation session: navigation has a single owner per
process (change `one-navigation-engine`), so a competing phone command cannot
exist and no lock is needed for correctness (spec `car-session-presence` — The
phone is informed, not disabled). Locking the phone map would remove legitimate
uses — a passenger browsing, or the driver picking a destination before setting
off — for no gain; the phone therefore keeps every map and navigation control and
only *informs* about the car session.

### Why favorite, group and starred reordering are phone-only

The order itself is shared (spec `fav-ordering`, spec `group-ordering`, spec `starred-ordering`): a reorder made on the phone is
what the car list renders. What cannot be shared is the gesture — Car App Library
templates expose rows, actions and clicks, but no drag; the alternative would be
a per-row (or per-header, or per-chip) "move up/down" action strip on a driver-facing list, which
duplicates a management task the phone already offers (same split as rename and group
color). The deliberate deviation is the interaction only, not the data.

The group order and the starred order need one more thing than the favorite order to be shared at all: the
car reads their sequence from the provider's **order channels**
(`AutoFavoritesProvider.groupOrder`, `AutoFavoritesProvider.starredOrder`), not from the group map's iteration order. A
reorder changes no map contents, and a `StateFlow` drops an emission equal to its
current value while `Map` equality ignores order — so a surface iterating the map
would keep the stale sequence, most visibly with groups that hold no favorites. Both
surfaces pair the channel with the map's contents through the shared
`orderedGroupNames` helper, so a group can never vanish from a list because the two
channels were observed a moment apart. The starred order needs no such pairing: it
carries its own group name per entry, and the car's starred screen renders it as one
flat list — the group headers stay a feature of the all-favorites mode.

Moving a favorite into another group follows the same split (change
`move-favorite-between-groups`, spec `fav-management-ui` — Favorites management
stays on the phone): a move made on the phone is what the car list renders, but
the car cannot express the destination-selection step — a picker would have to be
stacked on the browse list while driving — so the action stays on the phone and
the car screen stays a browse-and-select surface. Favorites management as a whole
(rename, color, reorder, move — and the order of the groups themselves) is a phone
surface by this rule; only browsing and selecting are shared.

### Favorites row actions (phone)

The favorite row in the group detail list keeps star, rename and delete as buttons
on the trailing edge and the drag handle as the leading affordance, as spec
`fav-management-ui` requires, and carries **"Move to group"** behind a compact
overflow menu (the pattern the group card uses for Set Color / Rename / Delete).
A fifth plain button does not fit a phone row: with the handle and the three
actions, the name and coordinate columns would lose the space that makes the row
readable. The action is absent when the store holds a single group, because there
is nothing to move into. The destination dialog preselects the first other group
and offers "New Group" with a name field, so the common case is two taps and a
new destination needs no detour through the group grid.

### Group grid card actions (phone)

The group card keeps its tap target (**name and favorite count** open the group), its
tint when a color is assigned, and its overflow menu (Set Color / Rename / Delete) on
the trailing edge; the **drag handle is the leading affordance** for the group order
(spec `group-grid-display`). The handle sits outside the tap target because a clickable
ancestor cancels its long press — the same rule the favorite row follows — so a tap
opens the group and only a long press starts a drag. A drag commits only the position
the card is released at: a drag that ends where it started, and a sheet dismissed
mid-drag, write nothing.

### Starred chip bar affordance (phone)

The chip bar is one flat sequence in the stored starred order (spec
`fav-starred-chip-bar`), so a chip's group is its secondary line, not a block
boundary. A **long press lifts a chip** for a drag; a short tap keeps opening the route
panel and a horizontal swipe keeps scrolling the bar. A long press is the drag
gesture, so the tap the chip's own click detector still delivers when the chip is
released without moving is suppressed for that gesture — holding a chip and letting go
must not start navigation. The suppression is cleared after a drag that committed a
move (and by the next press), so it can never swallow a later genuine tap. A drag
commits only the position the chip is released at: a drag that ends where it started,
and a sheet dismissed mid-drag, write nothing — the same rule the favorite row and the
group card follow.

### Why the license list is phone-only

The bundled dependency list is a several-hundred-row browser with a per-component
detail view. Car host `PaneTemplate` rows are not actionable (§3) and the display
is a driver surface, so the list stays on phone/tablet and the car screen keeps
its identity + ODbL attribution. This is a deliberate parity deviation, not an
omission (spec: `about-dialog` — "License list is not surfaced in the car app").

### Vehicle position setting (follow-mode anchor)

Source: change `vehicle-position-presets` (specs `auto-map-layout`,
`location-options-ui`).

- Phone and Android Auto present the same two rows — "Vehicle position
  (navigation)" / "Vehicle position (free driving)" — and the same 15 preset
  labels ("Center", "Bottom right", …) **by construction**: the labels come
  from the single shared `VehicleAnchorPosition` enum in `:core`; no
  per-surface strings.
- Layout deviation (justified by platform constraints, §1): the phone picker
  is a 5×3 grid dialog (touch), the Android Auto picker is a 15-row
  `ListTemplate` list (car templates cannot host grids). Labels/hierarchy stay
  identical.
- The value is global: editing on either surface changes both.

### Ongoing navigation notification (background driving)

Source: specs `navigation-ongoing-notification` (changes
`background-navigation-notification`, `fix-car-rail-widget-tap`) and
`auto-navigation-hints` (change `car-turn-by-turn-rail-widget`).

- **Surfaces.** The notification reaches the car as a turn-by-turn hint in the
  rail widget at the bottom of the car screen (plus an optional heads-up
  notification, which this app does not request). It is **not** relayed to a car
  notification shade: car hosts never show turn-by-turn navigation
  notifications in their Notification Center, by design. Free driving stays
  phone-only — while free driving the app is not the active navigation app, so
  the host suppresses turn hints and `NavigationManager.updateTrip` rejects
  trip updates. Those are platform constraints, not defects.
- **Tap targets.** The notification's own tap target is the **phone** UI
  (`MainActivity`) and never a car surface — a car host cannot show a phone
  activity. The rail widget has its own target on the `CarAppExtender`: a
  car-app start request (`androidx.car.app.notification.CarPendingIntent`
  addressed at `NaviVeylinCarAppService`), which the host resolves per platform
  (projection answers it with its `startCarApp` call, Android Automotive OS
  launches `CarAppActivity`). Without that car-side target the host falls back
  to the phone intent and the rail-widget tap does nothing. Free driving has
  neither a car hint nor a car tap target.
- **Parity.** Labels and guidance wording are shared: one formatter produces
  the phone text and the car hint, and the manoeuvre instruction uses the same
  wording as the on-screen next-turn display. The car may use **car-only text
  roles** through `CarAppExtender` — the instruction is its primary text and the
  distance/arrival its secondary text, while the phone notification keeps the
  destination name first. That is a rendering difference from one content
  source, not a second set of strings.
- NAVIGATION content: destination name (or the neutral "Navigation active"
  when unknown), the manoeuvre instruction in the same wording as the on-screen
  next-turn display, the distance-to-turn, arrival/remaining time and remaining
  distance, and the "Stop Navigation" action (same label as the on-screen stop
  button, §1). The car hint shows the instruction, the distance-to-turn with the
  arrival time, the manoeuvre arrow as large icon and the same stop action.
- FREE_DRIVE content: the current street/ref (same text as the free-driving
  view's street label) and the current speed; no destination-dependent guidance
  and no stop action.
- **Channels.** The phone channel stays silent and badge-free; on Android
  Automotive OS the notification uses a car channel of at least
  `IMPORTANCE_DEFAULT`, because the platform does not represent
  low-importance foreground-service notifications at all. No importance level
  requests a heads-up notification for turn hints.
- **Update cadence.** The notification is re-posted only when its host-visible
  content changed (manoeuvre, distance bucket, current road, remaining distance,
  arrival minute, free-driving speed) — the state stream emits at the position
  rate, and the car host re-renders its rail widget for every post. Guidance
  changes still post immediately, and trip metadata stays content-deduplicated.
- The notification is silent and ongoing; it never plays a sound or vibrates.

## 2. Action glyphs on the car display

Source: spec `auto-destination-details` — "Details actions visually marked".

- Actions SHALL be visually marked (leading unicode symbol) so they are
  distinguishable from attribute rows, and SHALL be positioned before the
  attribute rows.
- Current glyphs (text glyphs — no emoji, hosts render them reliably):
  - `▶` Navigate to
  - `◎` Show
  - `★` Add to Favorites
  - `☆` Remove from Favorites

## 3. Car-host template constraints (platform knowledge)

Verified against car-app 1.7.0 (`androidx.car.app`):

- `ListTemplate.Builder.addAction(...)` validates against
  `ACTIONS_CONSTRAINTS_FAB`: **icon-only, `maxCustomTitles = 0`**. A titled
  action throws `IllegalArgumentException: Action list exceeded max number of
  0 actions with custom titles` at template render time (crashed the AAOS
  emulator). → Titled actions MUST be **clickable list rows**
  (`Row.setTitle().setOnClickListener()`), never list actions.
- `PaneTemplate` rows: ~4–6 visible on most hosts, no scrolling, rows are
  **not actionable** — actions live at pane level.
- `ListTemplate` scrolls and the host **pages** when the list exceeds one page —
  the mechanism for showing arbitrarily many attributes.

## 3a. Map renderer startup (never on the host thread)

The car map renderer MUST initialize off the car-app main thread (spec:
`auto-map-renderer` — "Renderer initialization off the car-app main thread"):

- **First-touch rule**: the Hilt `OSMScoutClient` singleton (native `build()`:
  dlopen + stylesheet sync) and the initial-viewport resolution (saved
  viewport JSON / `getDatabaseBoundingBox`) run on `Dispatchers.Default` in a
  screen-scoped coroutine — never in a `Screen` constructor/`init` block and
  never via a lazy renderer forced from a main-thread call site (surface
  callbacks, lifecycle observers, settings coroutines, template builders). A
  frozen host thread during warmup delayed template delivery for every screen
  in the session. The car providers are injected as `Provider`/`Lazy` for this
  reason: resolving one must not build the client.
- **Host callbacks retain state only**: `onSurfaceAvailable` buffers the surface
  DPI (`RendererGate.surfaceDpi`) and hands it to the renderer as that renderer's
  projection DPI — resolving the client from a callback is forbidden even when the
  renderer already exists, because the callback runs on the host thread. The
  client holds no DPI of its own any more (spec: `render-projection-dpi`): every
  render request carries the DPI of the surface it draws on. The same
  applies to the stylesheet day/night flag: it is *published*
  (`RendererGate.daylightPush`, one distinct request per push so a dropped one
  stays retryable) and applied by a background collector, never set from the
  delivery callback (change `fix-car-surface-ownership-and-host-callbacks`).
- **Construct-and-publish is atomic on main**: the heavy work (client,
  viewport) returns from `withContext`, and the `AutoMapRenderer` is constructed
  and published on the main thread with no suspension in between, so a cancelled
  init can never drop an already-constructed renderer (its loops would poll
  forever).
- **Ready handle**: all surface screens use [`RendererGate`] (buffered
  last-wins slots, replay order surface → dark → viewport intents → marker →
  frames). Pre-ready surface delivery, dark presentation, GPS fixes, follow
  re-center and frame requests are replayed on readiness; `onDestroy` cancels
  a still-running init and shuts down a published renderer.
- **One owner draws, and `onStop` detaches**: each screen owns its renderer, and
  the session owns the one car surface. The session revokes the surface from the
  owner it supersedes (`CarSurfaceOwner.onCarSurfaceRevoked`, delivered from
  `attach`) before the incoming owner may draw, and a screen that stops calls
  `rendererGate.detachSurface()` after `pause()`, so a stopped screen holds no
  surface (and no overrun buffer) and cannot lock the surface another screen is
  drawing through during the start-before-stop transition.
- **Pan handler**: `MapPanHandler` takes a renderer *supplier* — constructing
  it must never force the renderer (template build happens on the main
  thread).
- Applies to `MapScreen`, `NavigationScreen`, `FreeDrivingScreen`,
  `DetailsScreen` (all four own a surface and a renderer; `DetailsScreen` gained
  the same async init in change `fix-aaos-host-crash`).

## 3b. Settings-screen loading (never trust a single invalidate)

The car settings screens must self-recover from a stalling or silently
dropped template refresh (spec: `auto/preferences` — "Settings screens never
wedge on a loading placeholder"; real hosts can drop `invalidate()` requests
while the template pipeline is busy). Settings screens with a loading
placeholder MUST use [`SettingsLoadGuard`]:

- **Watcher rule**: a one-shot watchdog re-invalidates when the placeholder is
  still showing after the load timeout — the first refresh may have been
  lost.
- **Guarded invalidate**: every `invalidate()` is wrapped; a host failure
  (`HostException`) must never kill the screen's load coroutine.
- **Recovery cap**: recovery invalidates are capped (two per load cycle,
  mirroring the surface-refresh cap) — exhaustion renders an explicit error
  row with Retry, never an infinite spinner.
- **Re-render on resume**: the screen re-renders on `onStart`/re-visibility,
  so a stale placeholder template from a previous visit cannot stick.
- **Re-read on re-visibility**: settings-bearing screens MUST re-READ the
  persisted settings on `onStart`, not just re-render the last in-memory
  snapshot — a value changed in a pushed picker (vehicle position, overspeed
  delta) appears on the row when the picker pops back (spec:
  `auto/preferences` — "Re-read on re-visibility").
- **Persist before dismissal**: picker screens that close immediately on
  selection MUST await the settings write before popping — dismissing the
  picker must never drop the write (spec: `auto-map-layout` — "Anchor
  selection survives immediate dismissal").
- **Scope hygiene**: `onDestroy` cancels the screen coroutine scope.
- Applies to `PreferencesScreen`, `VehicleAnchorPickerScreen`,
  `OverspeedDeltaPickerScreen`.

## 4. Details attribute list (Android Auto)

Source: spec `auto-destination-details` — all description attributes shown.

- Row order: Coordinates → Address → Area → every description attribute in
  native order. No row cap; the host pages long lists.
- All attributes returned by the Description API (opening hours, phone,
  website, …) MUST be reachable.
- Street/address dedup: the merged Address row covers `Location/Address` and
  `Location/Location`; those entries never appear again.
- Entries with an empty label or empty value are omitted.
- Address, area, and title resolution is shared with the phone dialog via
  `DetailsResolver` in `:core` (phone is the lead view).

## 5. Phone details dialog structure

Source: spec `enhanced-details-sheet`.

- Full-screen dialog, closes on system back (incl. predictive back API 33+).
- Layout top-to-bottom: object title → interactive mini map → coordinates →
  Address row → Area row → description sections (section headers, indented
  subsections with index) → actions.
- Coordinates always shown, formatted `%.5f, %.5f`, subdued color.
- Title precedence: object name (description `General/Name`, else the
  caller-provided name) → full address → non-coordinate label → generic
  "Location". Coordinate labels (e.g. `51.50000, 7.40000`) are NEVER titles.
- Button hierarchy: exactly one primary (filled "Navigate to"), secondary
  actions outlined ("Show", "Add to Favorites"), destructive error-colored
  ("Remove from Favorites").

## 6. Shared data resolution (both variants)

Source: `DetailsResolver` in `:core` (specs `auto-destination-details`,
`enhanced-details-sheet`).

- Address: street + house number + postal code + city
  (`"Hauptstraße 12, 44339 Dortmund"`); street from description → reverse
  lookup → digit-bearing label; postal/city from reverse lookup → hierarchy →
  IsIn.
- Area: admin hierarchy → reverse region → description `IsIn` → postal.
- Destination identity: name → address → area → hint.
- One `DetailsData` bundle (`DetailsResolver.resolve`) is consumed by both UIs —
  never re-derive per view; extend the resolver instead.

## 6a. Phone search surface (unified search dialog)

Source: spec `search-dialog` (phone only).

- ONE full-screen Material 3 search dialog on the phone map screen, opened by
  the search button, the menu "Search" entry, and the `/` key. No separate
  POI-search, history, or address-book sheets on the phone.
- Mode switch (SegmentedButton): Places (free-text location search, default),
  POIs (category chips + radius slider + explicit search button), Contacts
  (address book, hidden without `READ_CONTACTS`).
- Empty Places query shows suggestions: recent searches as chips (youngest
  first), favorite rows, current location.
- **Typed Places query also searches favorites** (spec `favorite-search`):
  favorites matching the query by name (case-insensitive substring, query ≥ 2
  chars) are listed **above** native results, each marked with a heart icon.
  Native results whose coordinates match an existing favorite (~11 m) are
  heart-marked too; a native result identical to a favorite hit is
  deduplicated (the favorite hit wins). Selecting a favorite hit behaves like
  any search result (records history, opens details).
- **Result order is the match tier rule** (spec `search-result-ranking`, change
  `search-result-ranking`): perfect matches first (nearest first), then close
  matches by match quality and distance, with a label tie-break. A perfect
  match is one whose every queried attribute matched exactly and which carries
  no criterion-class attribute the query did not name (admin region and postal
  area are context and are only criteria when the query names them). The
  dialog fetches a larger candidate set than it shows and displays the
  best-ranked 20, so a perfect match the backend's own order pushed past the
  page still appears. Favorites keep their unconditional position above native
  results (§ favorite-search) regardless of tier.
- **Row marking is one composite glyph** (`:core` `ResultMarkings`, the single
  artwork source): heart for a favorite, tick for a perfect match, both drawn
  into one bitmap when a row is both. The phone tints it with the primary
  color and exposes the facts through the content description ("Favorite",
  "Exact match", "Exact match and favorite"); the row shows the composite
  instead of stacking two icons.
- **Row distance is measured from the ranking reference**: the last known GPS
  fix, else the map center, else none (no distance shown). The same value
  ranked the list, so the km labels never contradict the order. The route-panel
  start/destination picker uses the same rule and the same marking.
- The shared search field is mode-aware: location query in Places, category
  filter in POIs, contact filter in Contacts.
- Android Auto has its own search surface — see §6b below. It is NOT a
  mirror of the phone dialog: the car `SearchTemplate` shows empty-query
  suggestions (mode rows + recent searches) instead of the phone's
  mode-switch + chips layout.

## 6b. Android Auto search surface (SearchTemplate)

Source: specs `auto-search`, `auto-search-suggestions` (change `unify-auto-search`).

- The car `SearchTemplate` shows **empty-query suggestions** instead of an
  empty list: mode rows + recent searches.
- Mode rows: "Search POIs near me" (opens the POI category picker) and
  "Search contacts" (opens the address-book person search, shown only while
  `READ_CONTACTS` is granted — same gate as the root list entry).
- Recent searches come from the shared history store (same JSON store as the
  phone, so phone searches appear on the car screen). Tapping a history row
  pushes a prefilled `SearchScreen` (the host owns the field text after
  construction, so the query cannot be set inline — design D2).
- **Typing replaces suggestions**: as soon as the user types, the mode/history
  rows are replaced by places search results; clearing the field restores the
  suggestions.
- **Favorites are searchable on the car screen too** (spec `favorite-search`,
  parity with §6a): the same rules as the phone dialog — case-insensitive
  substring name match on queries ≥ 2 chars, favorite hits listed **above**
  native results with a heart icon, native results whose coordinates match an
  existing favorite (~11 m) heart-marked too, and a native result identical
  to a favorite hit deduplicated (the favorite hits win). Same matching,
  prioritization, marking, and deduplication on both surfaces.
- **No-results state keeps mode rows**: a query with no results shows the
  "No results found" row followed by the mode rows, so the driver can pivot
  to POI/contacts search without clearing the field.
- **Same order, marking and distance as the phone** (spec
  `search-result-ranking`, change `search-result-ranking`): the car pipeline
  ranks with the same rule and the same candidate/display limits, marks rows
  with the same composite glyph, and shows the distance as a second text line
  using the same km formatting as §6a. The distance reference is the last known
  GPS fix, else the car map viewport center (the map screen passes its
  renderer's center; screens opened from the root/history have no map, so their
  results are ordered by tier and quality without distances). **Platform
  constraint (documented deviation)**: a car `Row` has a single image slot and
  at most two text lines and carries no accessibility text channel, so the
  marking is glyph-only on the car and the two facts cannot be announced — the
  phone row's content description has no car equivalent.
- **Action-phrase labels — documented parity deviation**: the suggestion rows
  use action phrases ("Search POIs near me", "Search contacts"), not the
  phone's mode labels ("Places" / "POIs" / "Contacts"). This is the
  established AA pattern (the root list already deviates: "Points of
  interest" vs "POIs") and Google Maps uses action phrases on the car
  display. The elements are new, not shared, so `cross-variant-ui-parity`
  (§1) is unaffected.
- The keyboard stays shown by default (`setShowKeyboardByDefault(true)`);
  the suggestion rows are scrollable below the field.

## 7. Phone map modes (Browse / Free drive / Navigation)

Source: spec `map-modes`.

- The phone map has an explicit mode model with three states, derived from a
  single source of truth (`MapMode` in `MapCanvasViewModel`):
  - **BROWSE** — follow off, north-up, last persisted viewport. The app
    ALWAYS starts in BROWSE; `followMode` is runtime state, never restored
    from settings (free drive is per-session intent).
  - **FREE_DRIVE** — follow on, auto-zoom on, heading-up, speed-based driving
    zoom. Entered with one tap on the compass button (short press).
  - **NAVIGATION** — route active; overrides follow mode. On navigation end
    the map returns to the mode active before navigation started.
- The drive mode toggle is a dedicated button in the right-side widget column
  directly below the compass: car icon in BROWSE (tap → FREE_DRIVE), exit icon
  in FREE_DRIVE (tap → BROWSE). Hidden during NAVIGATION. The compass button
  keeps its orientation role; its short press re-centers via the mode-dependent
  re-center action.
- Exiting FREE_DRIVE stays at the current position (follow off, north-up).
- The location-options sheet shows a header naming the current state and that
  state's options only — it never switches modes. BROWSE shows orientation;
  FREE_DRIVE and NAVIGATION share the driving section (auto-zoom + driving
  orientation). The sheet is reachable during navigation (gear in the nav
  right column).

## 7a. Phone map re-center button (per-mode)

Source: spec `map-modes` (drive suspension and reset; Browse re-center), spec
`map-recenter-button` (the control: icon, GPS-fix gate, placement).

- The re-center button (crosshair/my-location icon, content description
  "Re-center on location") appears only when a GPS fix is available **and** the
  map needs it:
  - **FREE_DRIVE**: any manual pan/zoom/rotate suspends the drive preset
    (`driveSuspended`); the button appears and tapping it resets to the
    standard drive values (follow on, auto-zoom on, heading-up, driving zoom)
    and hides the button.
  - **BROWSE**: the button follows the **measured** offset between the map
    center and the vehicle — never a remembered interaction. It appears when the
    vehicle is more than the appear threshold off the center (screen pixels, so
    the value means the same visible displacement at every zoom) and has stayed
    beyond it for a short dwell; it hides below the smaller hide threshold,
    immediately. So it is also visible on the start screen when the persisted
    viewport is not where the vehicle is, and it appears when the vehicle moves
    while browsing. Rotation about the center does not change the offset and
    therefore does not change its visibility. Tapping it puts the current GPS
    position at the center of the map, stays in BROWSE, and hides the button.
  - **NAVIGATION**: a manual zoom suspends auto-zoom; tapping re-centers on the
    current position (existing behavior).
- BROWSE framing is **not** anchor-framed: the "Vehicle position" presets
  configure the driving modes only (§1, "Vehicle position setting (follow-mode
  anchor)"). An off-center browse re-center would immediately re-show the button
  the user just dismissed.
- Placement: bottom-left in BROWSE/FREE_DRIVE; while navigating it is anchored
  directly above the routing status bar (the screen-bottom area is covered by
  `NavigationStateOverlay`, so the button must never sit at the bottom edge
  during navigation).
- There is no automatic re-engage: a manual interaction stops follow/auto-zoom in
  the driving modes until the driver taps the button. In BROWSE the map never
  follows, so the button is the standing "go to my position" offer and returns as
  soon as the vehicle is off the center again (e.g. seconds after a re-center
  while driving) — expected, not a defect.
- Phone-only: the car display has its own follow behavior via the car
  MapController and is unaffected (no phone-style auto zoom).

## 8. Phone navigation overlay sizing (driver-seat readability)

Source: specs `map-speed-widget`, `compass-button`, `next-turn-overlay`.
- All phone map overlays SHALL be readable from the driver seat; minimums:
  current speed 24sp bold, speed-limit sign 64dp with 28sp digits, turn
  distance 32sp bold, turn description/destination 22sp, next-next hint 20sp
  (smaller than the primary instruction).
- The compass button SHALL be larger than the other overlay buttons
  (56dp layout / 48dp visual vs 48dp / 40dp) so it reads at a glance. Its needle
  SHALL indicate geographic north in EVERY orientation mode (north-up and
  follow direction / heading-up alike), rotated from the map angle alone with the
  single `ProjectionUtils.compassRotationDegrees` convention shared with the
  Android Auto compass rose; the needle takes no vehicle-bearing input, so a
  noisy or absent bearing at a standstill cannot move it. There is no
  travel-direction triangle (change `compass-always-north-phone`): the travel
  direction is shown by the vehicle marker arrow and by the map's own rotation.
  Its colors (change `compass-day-night-palette`) come from a per-presentation
  palette, never from a theme color role: the fill is the GPS-fix hue family
  (red / yellow / green) in the tone of the active presentation (light
  `#FFCDD2` / `#FFF9C4` / `#C8E6C9`, dark `#93000A` / `#5C4300` / `#1B4A24`), and
  the needle, the "N" and the rim share one on-fill symbol color
  (`#1F1F1F` light, `#E8EAED` dark) — 11.7-15.4:1 light and 7.7-8.5:1 dark. The
  hue family is presentation-independent; only the tone changes. Reading a theme
  role here was the defect: the dark scheme's `on*` roles are light, so the
  needle landed on a light fill at 1.04:1. The presentation comes from the
  resolved dark-mode value (`MapCanvasUiState.isDarkPresentation`), never from
  `isSystemInDarkTheme()`, which would bypass a manual On/Off.
- The speed badge SHALL use the standard overlay card container (theme
  surface at 0.92 alpha, 12dp rounded) — the same treatment as the turn card
  and routing status — in the NORMAL state, with dark (`onSurface`) text;
  never white-on-light. At or beyond the speed limit plus the overspeed
  warning delta (`current >= max + delta`; the delta is a single global
  setting in whole km/h, 0-30, default 5, shared with Android Auto — warn at
  the limit when 0), the badge SHALL flip to the fixed warning red `#E53935`
  fill at the same 0.92 alpha and
  12dp rounding (semi-transparent overlay retained, spec `map-speed-widget`)
  with white text; the white text appears only together with the red warning
  fill. The warning red is fixed, not theme-derived, so white text stays
  readable in both light and dark schemes — the M3 dark-scheme `error`
  color is a light pink that would fail contrast with white text.
- Phone/AA anchor parity (specs `smooth-follow`, `auto-map-layout`, `location-options-ui`,
  changes `vehicle-position-presets`, `anchor-per-surface-visible-area`): the two surfaces share the
  same 15-position grid, the same labels and the same hierarchy, but each stores its **own** routing
  and free-driving anchor — the covered regions differ (phone: turn card, routing-status card,
  widget column; AA: a host pane that can cover ~40% of the width), so one shared value would be
  wrong on one of them. On the phone the preset fraction is relative to the **visible map area**
  (canvas minus the measured overlays), so an outer preset such as bottom-center stays visible above
  the routing-status card; on Android Auto the host's panel is a **forbidden band**, not a remap: a
  preset inside it moves to the nearest free position while every other preset (including the default
  center/center) keeps its exact fraction, so the head-unit default framing is unchanged.
  Picker labels/hierarchy stay identical, so the logical position is comparable across surfaces.
- Phone/AA reroute parity (spec `reroute-trigger`, change `one-navigation-engine`): one
  process-scoped navigation engine applies **one** reroute policy to the session both surfaces render —
  50 m fast path, 10 s confirmation window, 25 s cooldown, 100 m accuracy guard, 30 s tunnel guard.
  No surface carries its own thresholds, interval gate or confirmation rule (the car's former
  25 m / 15 s / 10 s-interval path is gone), so the timing is the same whichever surface displays the
  session, and a reroute re-acquires to the retained destination without a surface acting. The
  *presentation* of the reroute stays per surface: the phone redraws through its own route panel and
  map, the car through its host templates.
- Phone-only: the overspeed delta is configurable in the location-options
  sheet (slider 0-30, 1 km/h precision) and on Android Auto via the
  preferences value picker — same global property, identical on both
  surfaces (specs `map-speed-widget`, `auto-map-layout`, `location-options-ui`).
- Phone-only: Android Auto sizes text via the host template; parity applies to
  labels and hierarchy, not pixel sizes.
- Free-driving street label (phone, spec `current-road-info`): bottom-center
  pill, `surfaceVariant` at 0.92 alpha, 10dp rounded, `titleMedium` text,
  shown only when no route is active and a road is resolved; text is the
  road's "ref name" (e.g. "B 1 Hauptstraße"), blank when the road has no
  name/ref. Source: route way info while navigating, bearing-aware
  `getRoadAt` lookup in free driving (spec `road-lookup-bearing`).
- Street-name presentation (phone free-driving pill + Android Auto
  free-driving/browse labels, specs `current-road-info`, `auto/free-driving`,
  `auto/browse`; change `street-name-host-views` supersedes
  `street-name-overlay-avoid-bottom-anchor`):
  - **Routing (AA)**: the street name lives in the host travel-estimate card
    via `TravelEstimate.setTripText` — never on the map surface (spec
    `auto/navigation-view`).
  - **Free driving (AA), browse (AA) and phone free driving**: row-rule pill —
    a bottom-row anchor preset (`fy = 0.9`, e.g. bottom-center/left/right)
    places the label at the **top** edge (small padding), a top-row preset
    (`fy = 0.1`) and the middle row (including the default center) place it at
    the **bottom** edge. Placement is keyed on the anchor row, anchored to the
    real surface edges (16 dp padding) — never to host stable/visible-area
    rects, so the pill cannot float mid-screen. Parity rule: identical row
    rule on phone and Android Auto — only the layout container differs
    (Compose alignment vs Canvas anchor).
- Android Auto surface indicators (spec `auto-map-layout`): compass rose 56dp,
  speed-limit sign 56dp with 7dp red ring and 24sp digits; the speed badge
  (128×52, 20sp) is unchanged except its overspeed state — red-600 fill at
  the badge's 0xCC alpha with white text, same treatment as the phone.
  The rose palette follows the surface's resolved presentation (change
  `compass-day-night-palette`): day keeps the original chip `#CC1C1B1F` with
  white ticks and the red-600 north pointer, night keeps chip, ticks and pointer
  (17.1:1 and 4.1:1 — the best pairing on a dark chip) and adds a `0x66E8EAED`
  body rim, because the chip alone is only 1.03:1 against dark map land. A
  brighter night chip was rejected: a mid grey lifts chip/land to at most 2.44:1
  while dropping the north pointer to 1.62-2.68:1, below the 3:1 non-text floor.
  The rim is stroked inside the chip radius, so the rose silhouette, size and
  pointer direction are identical in both presentations. Speed badge and
  speed-limit sign keep their fixed palettes (the sign is a standard-mandated
  traffic sign).
- Vehicle position marker (phone + AA, spec `gps-location-marker` /
  `auto-map-renderer`, changes `unified-vehicle-marker`,
  `map-marker-route-contrast`): one unified compass arrow on both surfaces —
  38 dp density-aware (same visual size on every screen), casing ring + dark
  rim + vertical blue gradient core + soft blurred shadow. The palette
  branches on the resolved dark presentation, the geometry never does:
  - light presentation: white casing ring, dark rim, core `#42A5F5` →
    `#0D47A1`
  - dark presentation: deep blue-black casing `#0E1622` (no bright halo on
    dark land), dark rim `#0D47A1`, lighter core `#BBDEFB` → `#1E88E5` —
    lighter than the daylight core but deliberately **not** white
  Geometry and palette live in `:core` `VehicleMarkerGeometry`, shared by
  both renderers (parity by construction, spec `cross-variant-ui-parity`);
  growing the marker changes phone and car identically. The accuracy circle
  is untouched.
- Active route appearance (phone + AA, spec `route-appearance`, changes
  `map-marker-route-contrast` then `daylight-palette-route-and-roads`): one
  stylesheet rule (`_route` in the libosmscout submodule
  `stylesheets/include/route.oss`) colors the route on both surfaces, driven by
  the same `daylight` flag both surfaces push.
  Every bundled style that draws a route includes that rule (`standard`,
  `winter-sports`, `cycle` — the cycle style's own semi-transparent
  single-color rule was removed), so switching map style keeps the route
  colors. `public-transport` has no route rule at all and draws no route
  (pre-existing, tracked in `TODO.md`).
  - light presentation: translucent violet fill `#ba68c8` at 85 % alpha over an
    **opaque**, wider, magenta-leaning violet casing `#6a1b9a` (composited
    centre `#ae5dc1`). No daylight road class is violet, and the casing keeps
    its border on white residential roads.
  - dark presentation: unchanged red fill `#ff000088` with a white casing
  The casing stroke stays wider than the fill (`displayWidth` 2.2 mm vs
  1.5 mm) and keeps its priority, so the route is bordered on both sides and
  needs no extra render pass. **The casing must stay opaque and wider than the
  fill**: it then covers the whole fill footprint, so the composited centre is
  "fill over casing" and does not depend on the road underneath. That is what
  keeps a street label readable on the route — way labels are drawn *after* way
  fills (`DrawLabels` runs after `DrawWays`) and street `WAY.TEXT` carries no
  halo, so the label sits on the route fill as its backdrop (the previous
  opaque `#7b1fa2` gave a black label 2.56:1, the current pair gives 5.14:1).
  A translucent fill over a *dark* casing darkens the centre instead and
  re-breaks the label; a light casing swallows the border on white roads.
  Parity rule: both
  surfaces show identical route colors for the same presentation; no surface
  overrides them. Stylesheet hex literals MUST be **lowercase** —
  `osmscout::Color::FromHexString` accepts `0-9a-f` only and asserts on
  uppercase, which fails the whole stylesheet load and then crashes the
  renderer; `StylesheetHexColorCaseTest` guards this for every packaged
  stylesheet.

## 8a. Daylight map palette (base map)

Source: spec `daylight-map-palette` (change
`daylight-palette-route-and-roads`). One stylesheet serves both surfaces, so
phone and Android Auto cannot diverge here.

- Daylight road fills (`standard.oss`; `winter-sports.oss` carries the same two
  blues): motorway `#7d7af5`, trunk `#a3a1f5`, primary `#f58b8b`, secondary
  `#fdd08a`, tertiary `#fef271`. The night branch and `cycle.oss` (a
  deliberately desaturated cycling palette, motorway `#bbbbbb`) are out of
  scope.
- Why the lightness matters: way labels are drawn after way fills and carry no
  halo, so every road fill is a black label's backdrop. The former motorway
  `#4440ec` gave only 3.19:1; the new fills give 5.97 / 8.97 / 8.95 / 14.57.
- A constant derived from a fill must not be derived blindly — the stylesheet
  `lighten` / `darken` are plain lerps toward white / black, so a lighter base
  weakens everything derived from it:
  - **Highway shields draw WHITE text**, so they get their own darker constant
    `darken(base, 0.45)` (`#454387` / `#5a5987` / `#874c4c`), giving white text
    8.73 / 6.53 / 6.63. Reusing the road fill would leave shield text at
    3.52 / 2.34 / 2.35; the trunk and primary shields were already failing at
    3.83 / 3.90 before this change.
  - **`thinXColor`** (the `SIZE ... <0.45mm:3px` hairline branch, which the
    renderer enters only below 0.45 mm *and* 3 px, so it is never seen beside
    the fill) uses `lighten(base, 0.2)`; the former 0.3 faded to 1.99 / 1.53
    against land. A hairline is judged on **1.6:1 plus a 25-degree hue
    separation from land**, not on the text threshold — the palette already
    ships `thinSecondaryColor` at 1.22:1 and `thinTertiaryColor` at 1.08:1, so
    1.6 is above its own convention. A luminance-only floor of 2.0 is
    unsatisfiable for the new trunk and primary fills, which are themselves
    only 2.02:1 and 2.03:1 against land.
  - **`motorwayJunctionLabelColor`** uses `lighten(base, 0.3)`. The junction
    label relies on `style: emphasize` (the Cairo painter strokes a white glyph
    halo), which is part of the requirement, not an optional extra.
- **Guard:** `app/src/test/java/com/naviveylin/data/DaylightPaletteContrastTest.kt`
  computes all of the above from the **packaged** stylesheets, evaluating the
  stylesheet's own `lighten` / `darken` expressions, so a colour edit that
  re-breaks legibility fails in unit tests instead of on a device.
  `StylesheetHexColorCaseTest` guards the lowercase-hex rule.

## 9. Dark mode (phone + Android Auto)

Source: spec `dark-mode` (changes `aa-dark-mode-follow-host`, `phone-ambient-light-dark-mode`, `aa-chrome-theme-documentation`).

- **Phone**: three-state preference (On / Off / Automatic) in the on-map
  settings sheet, plus the "Adaptive by ambient light" toggle (default off).
  Automatic follows the system night mode; with the ambient option enabled it
  follows the light sensor (hysteresis 10/50 lux, 10 s debounce) instead.
  Sensor listening is foreground-only and gated on Automatic + option enabled.
  Devices without a light sensor fall back to the system signal.
- **Android Auto**: two layers, owned differently.
  - **Template chrome** (menus, dialogs, lists, panels, navigation banner,
    ETA card) is host-rendered and host-themed — never app-themed (the car
    app library exposes no chrome-color API; verified on 1.7.0). Day/night of
    the chrome follows the host's own display theme: `Android Auto >
    Settings > Display > Theme` on projection, or the head unit's system
    theme on AAOS (often OEM-pinned dark). Chrome was dark-only by default
    from 2019; Google's system light theme started rolling out from late
    2025. A user report of "UI always black, map flips" is expected host
    behavior, not an app bug — the host-side theme switch is the control.
  - **Map surface** (app-drawn) follows the HOST's day/night state
    (`CarContext.isDarkMode()`, live via `onCarConfigurationChanged`) — never
    the phone's system mode; only the surface needs the daylight-flag push.
  - The app's dark-mode preference (On/Off/Automatic) affects the surface and
    app-drawn controls only; it never influences host chrome.
- Parity: both variants render the same stylesheet variants (daylight flag
  set/unset); the environment source differs by platform (see
  `guidelines/MapRendering.md` §15).
- **Status colors live outside the theme (change `compass-day-night-palette`).**
  A control that carries status through a fixed hue family (the compass GPS-fix
  fill; the overspeed warning red) dims by switching to a dark tone of its own
  hue family — it must never keep a light-tone status fill in dark presentation,
  and must never take the dark scheme's role for that hue (M3's dark `error` is a
  light pink: `#F28B82`). Each such palette carries its own on-color pair so
  symbol-on-status contrast is guaranteed in both presentations rather than
  inherited from a user-themable role.
- **The app-drawn overlay palette follows the *applied* variant, not the
  requested one (change `compass-day-night-palette`).**
  `RendererGate.setDarkPresentation` is called from the screen's daylight
  collector once the native stylesheet flag was actually applied, so the vehicle
  marker and the compass rose flip in the same frame as the map variant — the
  overlay palette can never belong to the other presentation than the map
  underneath. Surface drawers read `RendererGate.currentDarkPresentation()`.
- **Every car screen that draws the map surface must observe the resolved
  presentation.** `MapScreen`, `NavigationScreen` and `FreeDrivingScreen` each
  own their renderer and their gate, so each needs its own `resolvedDark` input,
  a `KEY_DARK` observation in its `*ScreenObservations` (never a bare
  `scope.launch`) and a `pushDark()` re-push once the renderer is ready. Free
  driving was missing all of it until `compass-day-night-palette`: its map kept
  the daylight variant at night and its rose could never reach the night palette.

## 10. Internationalisation / Localisation

Source: spec `i18n-l10n` (change `i18n-l10n-support`).

- **All user-facing text SHALL live in Android string resources** — never
  hardcoded in composables, templates, or dialogs. English is the default
  (`res/values/strings.xml`) and the fallback; German is fully supported
  (`res/values-de/strings.xml`).
- **Phone (Compose)**: read strings with `stringResource(R.string.x)` (or
  `pluralStringResource(R.plurals.x, count)` for count-dependent text) inside
  composables. Hoist to a `val` when the value is needed in a non-composable
  context (semantics blocks, lambdas, companion functions).
- **Android Auto**: read strings with `carContext.getString(R.string.x)`
  (`getQuantityString` for plurals). Pure factory functions that build
  templates take a `carContext` parameter so they stay testable.
- **Module resources**: `:auto` keeps its own `res/` (library resources merge
  into the app at build time). Both modules carry `values/` + `values-de/`;
  every translatable key in `values/` MUST exist in `values-de/` (enforced by
  `GermanTranslationCompletenessTest` in each module).
- **A shared module owns no wording**: `:core` composes display text for both
  surfaces (details title, notification titles, turn instructions) and MUST take
  the words from its caller — a `String`, a `StringResolver` lookup or a resource
  the surface passes in — never from a literal of its own. The generic details
  title is the worked example: `DetailsResolver.resolveTitle(input, genericTitle)`
  takes the surface's `R.string.location_title_generic` (change
  `fix-remaining-untranslated-strings`). Any wording both surfaces show SHALL have
  exactly one resource home (e.g. `:core`'s `nav_hint_neutral`, used by the car
  hint and the phone notification).
- **Locale-aware numbers**: use the shared helpers in
  `core/.../DistanceFormat.kt` — `formatDistanceNumber(meters, locale)` returns
  the numeric part only (German comma decimals), `distanceUsesKilometers`
  selects the unit. The unit suffix comes from a resource
  (`distance_unit_km` / `distance_unit_m` / `size_unit_mb`), never from code.
  Do NOT use `Locale.ROOT` for display formatting.
- **Coordinate strings are data, not display text**: they are the one exception
  to locale-aware numbers (spec: `i18n-l10n` — Coordinate string is
  locale-stable). Format them with `core/.../CoordinateFormat.kt` —
  `formatCoordinatePair(lat, lon, pattern)` (the phone passes the
  `coordinates_format` resource, the car uses the default pattern) and
  `formatCoordinate(value)` for an input field's prefill — so a pair always
  reads `51.51391, 7.47434` and never `51,51391, 7,47434`, which keeps
  `DetailsResolver`'s coordinate-label detector working. Entry goes the other
  way: `parseLatitude` / `parseLongitude` accept both `,` and `.`, so a
  prefilled value can be saved unchanged and a German user may type a comma.
- **Plurals**: count-dependent strings use `plurals.xml` (`one`/`other` for
  English and German) — e.g. `file_count`, `active_downloads`, `favorite_count`,
  `map_downloading_maps`. Pass the count as the format argument
  (`pluralStringResource(R.plurals.x, count, count)`, `getQuantityString(id, count,
  count)`), never concatenate an English suffix: a German plural rule cannot be
  expressed by one.
- **Format args**: positional args use `%1$s`/`%2$s` so translators can
  reorder; never concatenate translated fragments.
- **POI categories**: category names come from resources
  (`poi_category_*`), mapped per module (phone `categoryLabelRes`, auto
  resource lookups with English fallback for unknown IDs). Phone and Auto
  SHALL show the same category labels (parity, §1).
- **Diagnostics**: user-facing diagnostics UI text is translated; logcat-only
  debug strings are exempt.
- **Lint + scan gate**: `HardcodedText` runs at error severity in `:app`, `:auto`
  *and* `:core` (`lint.xml` + `abortOnError` in each), and a Gradle
  `checkHardcodedStrings` gate — wired into every module's `preBuild`, rule and
  message in `buildSrc` (`com.naviveylin.build.i18n.HardcodedStringScanner`,
  unit-tested) — fails the build on a literal in a UI text position. The scan
  covers, in every module:
  - a text position (`Text(`, `text =`, `title =`, `label =`,
    `contentDescription =`, `placeholder =`, `hint =`, `description =`,
    `.setTitle(`, `.setText(`, `.addText(`, `.setContentTitle/Text(`),
    including a conditional assignment (`text = if (x) "A" else "B"`);
  - **interpolated display text** — a literal whose text *outside* its `${...}`
    carries a word (`"$favCount favorite"` fails; a pure value template such as
    `"$zoomLevel"` or `"${a}-${b}"` stays exempt). Hand-rolled pluralization
    cannot be translated, so it is a defect, not a template;
  - a `NotificationChannel(id, name, description)` name/description argument
    (user-visible in Settings; re-applying them on start updates an existing
    channel).

  Deliberate exemptions (kept small on purpose): `%`-format templates,
  symbol-only separators (`"|"`, `" · "`) and a single camelCase identifier
  (an animation/label key such as `compassRotation`). A message that only
  mentions a UI word in prose MUST be reworded — the gate has no allowlist.
- **Coordinates in logs**: no log or diagnostics call may interpolate a position
  (spec: `auto-diagnostics` — Diagnostics carry no coordinates); a Gradle
  `checkNoCoordinatesInLogs` gate (buildSrc `CoordinateLogScanner`, wired into
  `preBuild`, scanning `:app`/`:auto`/`:core`) fails the build naming file and
  line, and the scan is paren-balanced so it sees multi-line calls. It flags four
  shapes: a coordinate identifier, a coordinate-shaped format, a *whole*
  position-carrying value (`$request`, `$destination`, a local declared with a carrier
  type) and a whole-URI hand-over (`${original?.data}`, `${intent?.data ?: "-"}`) — a
  property read off the URI (`${intent?.data?.scheme}`) is identity and stays legal.
  Log precision-free identity instead: object label/id, map database or map file name,
  magnification, screen pixel, accuracy, bearing — and for input the app merely
  *received* (a share subject, a query, a deep-link URI) log its origin and shape
  (scheme, action, whether a subject/query was present), never its text. The gate has
  no allowlist — a message that only mentions a coordinate word in prose must be
  reworded.
- **RTL**: `supportsRtl="true"` is set; keep layouts direction-agnostic
  (use `start`/`end` alignment, not `left`/`right`) so future RTL locales
  work without layout changes.

## 10a. Phone surface while a car session is active

While a car session is live, the phone does not show the map: it shows the
**car-session surface** (`CarSessionSurface`). This is the one place where the
phone surface yields to the car — everywhere else a car session is advisory
only (§the `car-session-presence` spec: the phone is *informed, not
disabled*). The map is the exception because the car is already drawing it and
the phone's second copy costs exactly the memory a drive on a loaded device is
short of (`guidelines/MapRendering.md` §18 has the numbers).

- **What the surface states.** The car-session pill's own label
  (`car_session_active_indicator`) as its headline — the pill is the compact
  form of the same claim, shown only once the user overrides the suspension, so
  one screen never claims the same thing twice.
- **What it shows.** The guidance summary from the SHARED navigation state, in
  the labels the car surface uses for the same state: the maneuver through
  `TurnInstructionLocalizer.shortDescription` (the very call the car's
  `NavigationTemplateMapper` cue uses) and the phone's own stats row for
  arrival time / remaining time / remaining distance, which is fed by the shared
  engine state and formats through the same `com.naviveylin.core` helpers the
  car's `distanceForDisplay` delegates to. A free drive has no maneuver to
  summarize; the statement and the map action are then the whole surface.
- **Its one action** is *Show map here*: the per-session override. It lifts the
  suspension for the rest of that session only — the session's end clears it, so
  a later session suspends again by default. The override is deliberately not
  persisted: a stored "never suspend" would give the saving up forever.
- **What survives.** Nothing about the map state is reset by a suspension:
  mode, viewport and magnification are untouched, so the resumed map renders at
  the state the shared engine holds *now*, not at the state the suspension froze.
- **Diagnosis.** Every transition (suspend / override / lift) is one record on
  the diagnostics stream under the `SESSION` tag, carrying the presence edge
  that caused it and the storage the release gave up.

---

## Keeping this document honest

- Specs are the contract; this document is the condensed knowledge base.
- When a spec requirement changes labels, styling, or hierarchy, update the
  parity table (§1) and glyph table (§2) here at the same time.
