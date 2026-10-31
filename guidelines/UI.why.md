# UI — why (rationale)

The rationale layer of `guidelines/UI.md`: the parity decisions already made, the
reasoning behind a rule, and the measurements a bound was arrived at. Read beside the
section its `## §N` anchor names; it is not a document a route names.

## §1 The parity decisions, and why

### Current parity decisions

| Element | Phone | Android Auto |
|---|---|---|
| Primary navigation action | "Navigate to" (filled button) | "▶ Navigate to" (first clickable row) |
| Show on map | "Show" (outlined button) | "◎ Show" (clickable row) |
| Add favorite | "Add to Favorites" (outlined button) | "★ Add to Favorites" (clickable row) |
| Remove favorite | "Remove from Favorites" (error-colored button) | "☆ Remove from Favorites" (clickable row) |
| Open-source license list | Reachable from About → "Open source licenses": components with identifiers, full license texts, links for licenses whose terms stay with their owner | Not surfaced — the car About screen keeps app identity and the map-data attribution only |
| Auto-zoom magnification changes | Animated — `smooth-zoom` scales the displayed map from the current scale toward the target while the native render is queued, and a change farther than the window the frame in hand can serve is applied as a sequence of steps, each step a native render at the magnification it displays (`ZoomWalk`), one step per landed frame; the animation pivots on the vehicle's resolved follow anchor while a follow mode is active | Animated by the same observable contract, implemented in the renderer — a magnification request larger than the blit window is WALKED across rendered frames (`AutoMapRenderer.advanceZoomWalk`, spec `auto-speed-zoom` — Auto-zoom entry transition), also pivoting on the resolved follow anchor. The observable contract (no single-frame jump, no frame showing a magnification the frame in hand cannot serve, anchored on the vehicle, ends on an exact render) is the parity requirement, the mechanism (Compose display scale over walked renders vs renderer walk) is not shared. Car gesture/zoom-button paths keep their immediate response; the phone walks every animated change, and a programmatic camera fit lands directly on both surfaces |
| Reorder favorites inside a group | Drag handle on each favorite row in the group detail list (`sh.calvin.reorderable`); the position the drag ends in is persisted | Not offered — Car App Library templates have no drag gesture. The car's group screen (`ListTemplate`) shows the **same stored order** read-only, so the data is at parity, the interaction is not (see below) |
| Reorder favorite groups | Long-press drag handle on each group card in the grid (`sh.calvin.reorderable`, `rememberReorderableLazyGridState`); the position the drag ends in is persisted — change `reorder-favorite-groups` | Not offered — same platform constraint as the favorite order, and a per-header move action would duplicate a management task the phone owns. The car list renders the **same stored group order** read-only, so the data is at parity, the interaction is not (see below) |
| Move a favorite into another group | Row action on the favorite (overflow menu → "Move to group", shared wording `move_favorite_to_group`) opening a destination dialog that lists the other groups plus "New Group" (shared wording `new_group_title`) — change `move-favorite-between-groups` | Not offered — the destination-selection step has no template equivalent and the screen is driver-facing. The car renders the resulting grouping twice over: the moved favorite's destination group row carries the new count on the group level, and that group's own screen lists the favorite (§1, "Favorites browsing hierarchy"), so the data is at parity, the interaction is not (see below) |
| Reorder starred favorites | Long-press drag on a starred chip in the favorites sheet's chip bar (`sh.calvin.reorderable`, `rememberReorderableLazyListState`), which renders the one starred order spanning all groups — the position the chip is released at is persisted — change `order-starred-favorites` | Not offered — same platform constraint as the other two orders. The car's starred-favorites screen renders the **same stored starred order** read-only, as one list without group headers (a cross-group sequence cannot be rendered as group blocks), so the data is at parity, the interaction is not (see below) |
| Stylesheet could not be loaded | Non-blocking snackbar next to the map with the shared wording (`MapStyleLoadReporter` → `uiState.snackbarMessage`) | **Same wording**, also non-blocking, but a different surface: a one-shot low-importance notification (channel `map_style`, silent, auto-cancel) — the car templates have no general message slot, so `CarStyleLoadNotifier` is used instead of a template change. Guidance is never interrupted on either surface (change `fix-stylesheet-load-crash`, design D2) |
| Car session live while the phone UI is open | Advisory indication on the map (centre-left pill, shared wording `car_session_active_indicator`, translated): "Navigation on car display". Informational only — no map, search or navigation control is disabled while a car session is live (change `shared-resource-arbitration`, spec `car-session-presence`) | Not shown — the car surface is the session, so the indication would tell the driver nothing (platform constraint, not an omission) |
| Map updates: the list, its `[Update all]` and the check control | The map manager screen's updates section: the count, when the last check ran, one row per updatable installation with its size and `[Update]`, `[Update all]`, and `[Check for updates]` (spec `map-updates`, change `add-map-update-check`) | Not surfaced — the car has no map-manager screen at all; the checked state is process-wide, so a car session sees the same result while it keeps rendering whatever is installed. Accepted deviation per spec `cross-variant-ui-parity` ("a settings control may exist only in the phone variant when the car's surface cannot carry it") |
| Route requested without the precise location grant | Actionable dialog with the shared wording (`location_precise_required_navigation`, `PreciseLocationRequiredDialog`): "Grant precise location" re-requests the permission while the platform can still ask, and opens the app's system settings once it will not ask again — no route request reaches the routing engine meanwhile (change `fix-location-permission-scope`, spec `location-permissions` — Starting navigation requires precise location) | **Same wording**, non-blocking: the refusal is published on the shared navigation state, so the session shows it in the existing guarded error notice (one row, back action). The car SHALL NOT launch a settings screen — platform constraint, so the driver is told to grant it on the phone / in the system settings. Free driving and the map keep working on both surfaces with approximate location |
| A shared map link that carries neither a coordinate nor a place name | Non-blocking snackbar next to the map with the wording `shared_link_unreadable` ("Shared link could not be read"); the search field keeps its content and the panel is not opened — the link text is never searched for (spec: `share/location-receiving` — An unreadable shared map link is reported, never searched) | Reported on the shared navigation state, so the session shows it in the existing guarded error notice: wording `deep_link_unreadable`, no template message slot on the car, and the link is no longer geocoded (which is what the session did before — the geocode of a URL can only fail). Same intent, surface-appropriate carrier (change `read-shared-google-maps-links`, design D6) |
| A route calculation that failed | The route panel shows the wording for the failure's **reason** (`navigation_failure_*` in `:core`, chosen by `navigationFailureWordingRes`), states the affected end and the distance to the nearest usable road when the router reported one, and offers **`[Open Map Manager]`** where the cause is missing or unreadable map data (`hasMapManagerAction()`); while navigating the same wording reaches the snackbar (change `distinguish-routing-failures`, spec `routing-failure-reporting`) | **Same wording** from the same shared resource, plus one statement the platform forces: `car_failure_maps_on_phone` — a car session must not launch a phone screen, so the notice says where the action is instead of offering it. One notice, no action strip. The failure carries its origin, so each surface presents only what concerns it (`failureAppliesTo`) |
| Route description list | The route panel's step list: one row per **driving decision**, derived by the shared row policy (`core.RouteRows`) — the row's own leg distance and duration, the manoeuvre's description, the analysed step highlighted (spec `route-analysis` — Rows are derived from the route's steps) | The car route description screen lists **the same row set** from the same function over the remaining steps, with the row's leg distance and its target street as the row title and the manoeuvre's description as its text; the current decision is the marked first row. Deviation is presentation only — host `Row`s carry no duration column and no selection highlight. There is no per-step car list (change `share-route-rows-in-core`; spec `auto/navigation-view` — Route description screen) |
| A roundabout exit's turn icon | The app's own numbered roundabout glyph — the ring with the exit ordinal inside — for the next-turn row (64 dp), the next-next row (36 dp), the step icons of the route panel and the full-screen route description, and the notification's large icon (change `fix-roundabout-exit-number`, spec `roundabout-renderer`) | The car's instruction panel and rail widget are given **both** identifications: the maneuver is built as the number-bearing type (`TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW`/`_CCW` + `setRoundaboutExitNumber`) **and** the app's numbered glyph is supplied as well, because which of the two the host draws is host behaviour — phone projection is rendered by Google's host while the DHU drives the AOSP reference renderer, and neither is observable from a test here. Whichever glyph the driver sees, it carries the exit number (spec `auto/navigation-view` — A roundabout exit is identified by its ordinal). The car route-description list and the car hint take a bitmap and so use the app's glyph alone. Where the exit ordinal is unknown both variants keep the plain roundabout arrow. **The label stays identical**: the cue, the panel text and the notification wording are the same strings on both variants |


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


### Why the license list is phone-only

The bundled dependency list is a several-hundred-row browser with a per-component
detail view. Car host `PaneTemplate` rows are not actionable (§3) and the display
is a driver surface, so the list stays on phone/tablet and the car screen keeps
its identity + ODbL attribution. This is a deliberate parity deviation, not an
omission (spec: `about-dialog` — "License list is not surfaced in the car app").

### Superseded and rejected parity alternatives

Where a decision was reversed, the reversal and the alternative it eliminated are recorded with
the change that made it: the auto-zoom animation is a shared observable contract with a per-surface
mechanism; the car-list capacity is a runtime query, never a hard-coded 6 or 100; and the phone is
informed of a car session, never locked (`one-navigation-engine`, `car-session-presence`).

## §8d The car search distance reference

Issue #42, change `fix-car-search-distance`: a search opened from the root or the history list showed
no distance at all, because the map screen did not publish the center of the last frame it actually
drew; the rule that it does is what the §8d reference holds.

## §9 Why the dark chrome is not app-themed

Car template chrome was dark-only by default from 2019; Google's system light theme started rolling
out from late 2025. A user report of "UI always black, map flips" is expected host behavior, not an
app bug — the host-side theme switch is the control.
