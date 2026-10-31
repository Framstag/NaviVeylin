# Phone UI Guidelines

Rules and constraints for the phone/tablet app's own surfaces — the details dialog, the
search surface, favourites management and the ongoing-navigation notification rules.

This document carries the phone **surface** sections of the former `UIPhone.md` under their
own numbers. The phone's map and its overlays — modes, the re-center control, the
route-planning card, overlay sizing and layering — are `guidelines/UIPhoneMap.md`; the
cross-variant parity rule and the car surfaces stay in `guidelines/UI.md`.

**Read the section that owns your topic, not this whole document.** The section list is
`grep -n '^## ' guidelines/UIPhone.md`; AGENTS.md's Documentation Map routes a kind of
change here.

---

## 5. Phone details dialog structure

Source: spec `enhanced-details-sheet`.

- **Rule: the phone details dialog is full-screen and closes on system back.** Full-screen dialog,
  closing on system back (incl. predictive back API 33+).
- **Rule: the details dialog layout is title → mini map → coordinates → address → area → description → actions.**
  Layout top-to-bottom: object title → interactive mini map → coordinates → Address row → Area row →
  description sections (section headers, indented subsections with index) → actions.
- **Rule: coordinates are always shown, five decimals, subdued.** Coordinates always shown, formatted
  `%.5f, %.5f`, subdued color.
- **Rule: the details title precedence is name → address → label → generic.** Object name (description
  `General/Name`, else the caller-provided name) → full address → non-coordinate label → generic
  "Location". Coordinate labels (e.g. `51.50000, 7.40000`) are NEVER titles.
- **Rule: the details dialog has exactly one primary action.** Exactly one primary (filled "Navigate to"),
  secondary actions outlined ("Show", "Add to Favorites"), destructive error-colored
  ("Remove from Favorites").

## 6a. Phone search surface (unified search dialog)

Source: spec `search-dialog` (phone only).

- **Rule: the phone has one search dialog.** ONE full-screen Material 3 search dialog on the phone map
  screen, opened by the search button, the menu "Search" entry, and the `/` key. No separate POI-search,
  history, or address-book sheets on the phone.
- **Rule: the search dialog has a three-mode switch.** Places (free-text location search, default), POIs
  (category chips + radius slider + explicit search button), Contacts (address book, hidden without
  `READ_CONTACTS`).
- **Rule: an empty Places query shows suggestions.** Recent searches as chips (youngest first), favorite
  rows, current location.
- **Rule: a typed Places query also searches favorites.** (spec `favorite-search`.)
  favorites matching the query by name (case-insensitive substring, query ≥ 2
  chars) are listed **above** native results, each marked with a heart icon.
  Native results whose coordinates match an existing favorite (~11 m) are
  heart-marked too; a native result identical to a favorite hit is
  deduplicated (the favorite hit wins). Selecting a favorite hit behaves like
  any search result (records history, opens details).
- **Rule: result order is the match tier rule.** (spec `search-result-ranking`, change
  `search-result-ranking`.) Perfect matches first (nearest first), then close
  matches by match quality and distance, with a label tie-break. A perfect
  match is one whose every queried attribute matched exactly and which carries
  no criterion-class attribute the query did not name (admin region and postal
  area are context and are only criteria when the query names them). The
  dialog fetches a larger candidate set than it shows and displays the
  best-ranked 20, so a perfect match the backend's own order pushed past the
  page still appears. Favorites keep their unconditional position above native
  results (§ favorite-search) regardless of tier.
- **Rule: the row's first line identifies the entry.** (spec `location-search`, change
  `search-result-row-content`.) `label` with the entry's admin region name (`region[0]`, in practice
  the city) appended as `label - city`, omitted when the entry has none and when it is the label
  itself. Two hits with the same street name in different municipalities — which the search scope
  (a Regierungsbezirk, §6a's scope rule) makes an everyday case — are told apart without opening them.
  The **admin region hierarchy is not part of the row**: it belongs to the details sheet's "Area",
  and the row shows it nowhere.
- **Rule: the detail line carries what the first line does not.** `name` (when it differs from the label),
  `objectTypeName` and `postalArea` for results that share a label (`"restaurant · 44139"`), and — for
  a hit whose label is a POI's own name (`matchedComponent == "poi"`) — the address resolved at the
  entry's position (`"Hauptstraße 20"`). A street or address hit already carries street and
  house number in its label and is never looked up. Nothing is composed per surface: `:core`
  `ResultRowText` owns the text for both variants.
- **Rule: the row leads with its type's artwork.** (spec `search-result-row-icon`.) The icon the active
  stylesheet gives the entry's object type, resolved through the stylesheet itself (`renderTypeIcon`,
  a native entry point that draws with `MapPainterCairo` and follows the icon-versus-symbol
  preference), cached once per type, never requested on the main thread or during a car template
  build. A type the stylesheet gives nothing draws no icon and no placeholder. The icon and the
  marking share the one leading slot (see the composite bullet above) and are tinted with the
  primary colour, because the stylesheet's own colours are chosen for the map's surface, not for
  this one in both themes.
- **Rule: row marking is one composite glyph.** (`:core` `ResultMarkings`, the single
  artwork source.) Heart for a favorite, tick for a perfect match, both drawn
  into one bitmap when a row is both. The phone tints it with the primary
  color and exposes the facts through the content description ("Favorite",
  "Exact match", "Exact match and favorite"); the row shows the composite
  instead of stacking two icons. When the entry's type has artwork, the same bitmap carries it
  beside the marking (`ResultMarkings.bitmapFor(marking, typeIcon)`), so one slot holds both and the
  marking keeps the offset it has on a row without an icon.
- **Rule: row distance is measured from the ranking reference.** The last known GPS
  fix, else the map center, else none (no distance shown). The same value
  ranked the list, so the km labels never contradict the order. The route-panel
  start/destination picker uses the same rule and the same marking.
- **Rule: the search field is mode-aware.** Location query in Places, category filter in POIs, contact
  filter in Contacts.
- **Rule: the car search surface is not a mirror of the phone's.** Android Auto has its own search surface
  — see `UICar.md` §6b. The car `SearchTemplate` shows empty-query suggestions (mode rows + recent searches)
  instead of the phone's mode-switch + chips layout.
- **Rule: the typed query has a lifecycle.** (spec `search-request-lifecycle`, change
  `fix-search-cancellation`.) The phone places search runs in its own slot, debounced by 300 ms. A newer query
  supersedes the one in flight, a query shorter than 2 characters runs nothing and leaves the results area
  empty instead of holding the previous query's results, and dismissing the dialog or switching away from
  Places abandons the running search rather than letting it finish. Each request carries a generation, so a
  cancellation arriving after a newer request started is a no-op; `UICar.md` §6b states the car's side of the same rule,
  and the slots (`places`, `poi`, `route`, `car`) keep one surface's search from stopping another's.

- **Rule: a Compose dropdown popup is asserted by its field's text.** A popup does not appear in a
  `uiautomator` dump (§40.22) — assert the field's text instead of trying to tap it on a device run.

## 12. Favorites browsing hierarchy (both surfaces)

Source: change `car-favorites-group-level` (specs `auto-favorites`, `fav-management-ui` — phone and car
surfaces keep the same browsing hierarchy and the same wording for it).

- **Rule: both surfaces have two levels in the same order.** The **group level** first, then that group's own
  favorites. The phone sheet opens on the group grid and a group card opens the group detail list; the
  car's all-favorites entry opens one row per group and a group row opens that group's favorites. The
  group row carries the group name plus the `plurals/favorite_count` sentence the phone's group card
  shows — `:auto` carries its own copy of the plural, word for word, and
  `GermanTranslationCompletenessTest` guards both locales — so the count is at parity by construction,
  not by a second wording that could drift.
- **Rule: the starred entry stays a top-level list of its own.** (`Starred favorites`.) One flat sequence in the
  stored starred order, no group rows, because a cross-group sequence cannot be rendered as group blocks
  (spec `starred-ordering`).
- **Rule: a group holding no favorites is listed on the phone and hidden on the car.** The phone's grid lists it (that is how a freshly
  created group appears, and the phone is where it can be filled) and the car's group level hides it,
  because no car affordance can move a favorite into a group, so a car row for it would be a dead end
  (spec `auto-favorites` — A group holding no favorites is not listed).
- **Rule: both favourites levels are browse-and-select only.** A row tap starts the destination-picker flow; neither level
  offers a drag, a rename, a color or a move. The management split in `UI.why.md` §1 is unchanged. A group's
  own screen is reached by a template row click, so the push leaves the host's click callback like every
  other screen push (`UICar.md` §3, spec `car-host-fault-isolation`).
- **Rule: neither level exceeds the host's row budget.** Each ends with the page row that opens the remainder
  (`UICar.md` §3, spec `car-list-row-limit`).

## 13. Favorites row actions (phone)

- **Rule: the favorite row keeps star, rename and delete, with "Move to group" in an overflow menu.** The favorite row in the group detail list keeps star, rename and delete as buttons
on the trailing edge and the drag handle as the leading affordance, as spec
`fav-management-ui` requires, and carries **"Move to group"** behind a compact
overflow menu (the pattern the group card uses for Set Color / Rename / Delete).
The action is absent when the store holds a single group, because there is nothing to move into. The
destination dialog preselects the first other group and offers "New Group" with a name field.

## 14. Group grid card actions (phone)

- **Rule: the group card keeps its tap target, tint, overflow menu and a leading drag handle.** The group card keeps its tap target (**name and favorite count** open the group), its
tint when a color is assigned, and its overflow menu (Set Color / Rename / Delete) on
the trailing edge; the **drag handle is the leading affordance** for the group order
(spec `group-grid-display`). The handle sits outside the tap target because a clickable
ancestor cancels its long press — the same rule the favorite row follows — so a tap
opens the group and only a long press starts a drag. A drag commits only the position
the card is released at: a drag that ends where it started, and a sheet dismissed
mid-drag, write nothing.

## 15. Starred chip bar affordance (phone)

- **Rule: the starred chip bar is one flat sequence, and a long press drags a chip.** The chip bar is one flat sequence in the stored starred order (spec
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

## 16. Drag surface checklist (for any new draggable affordance)

- **Rule: a long-press drag suppresses the item's own tap.** A long press that lifts an item also
  leaves the clickable *inside* that item's node alive, so a long press followed by a release fires
  `onClick` as well. Whatever the surface: set a `suppressNextTap` flag in `onDragStarted` (the tap is
  delivered **before** `onDragStopped`, so setting it there is too late), consume it in the item's
  `onClick`, and clear it when a drag commits a move so a later genuine tap is not swallowed.
- **Rule: both halves of a drag are asserted.** A drag that ends where it started commits nothing, a
  drag that moved commits exactly one position, and a follow-up tap still works — all three asserted,
  not assumed.

The test recipes that hold this rule — computing a delta from the two items rather than trusting a
helper's "distance", bounding every drag to about one item width, and capturing a `dagger.Lazy`
instance inside the lambda — are `UIPhone.recipes.md` §16.

## 17. Vehicle position setting (follow-mode anchor)

Source: change `vehicle-position-presets` (specs `auto-map-layout`,
`location-options-ui`).

- **Rule: both surfaces present the same vehicle-position rows and preset labels by construction.** Phone and Android Auto present the same two rows — "Vehicle position
  (navigation)" / "Vehicle position (free driving)" — and the same 15 preset
  labels ("Center", "Bottom right", …) **by construction**: the labels come
  from the single shared `VehicleAnchorPosition` enum in `:core`; no
  per-surface strings.
- **Rule: the vehicle-position picker's layout deviates by platform; its labels do not.** Layout deviation (justified by platform constraints, §1): the phone picker
  is a 5×3 grid dialog (touch), the Android Auto picker is a 15-row
  `ListTemplate` list (car templates cannot host grids). Labels/hierarchy stay
  identical.
- **Rule: the vehicle-position value is global.** Editing on either surface changes both.

## 18. Ongoing navigation notification (background driving)

Source: specs `navigation-ongoing-notification` (changes `background-navigation-notification`,
`fix-car-rail-widget-tap`) and `auto-navigation-hints` (change `car-turn-by-turn-rail-widget`).

- **Rule: the notification reaches the car as a turn-by-turn hint, never a shade notice.** The
  notification reaches the car as a turn-by-turn hint in the rail widget at the bottom of the car
  screen (plus an optional heads-up notification, which this app does not request). It is **not**
  relayed to a car notification shade: car hosts never show turn-by-turn navigation notifications in
  their Notification Center, by design.
- **Rule: free driving stays phone-only.** While free driving the app is not the active navigation
  app, so the host suppresses turn hints and `NavigationManager.updateTrip` rejects trip updates.
- **Rule: the notification's own tap target is the phone, and the rail widget carries its own.** The
  notification's tap target is the **phone** UI (`MainActivity`) and never a car surface. The rail
  widget has its own target on the `CarAppExtender`: a car-app start request
  (`androidx.car.app.notification.CarPendingIntent` addressed at `NaviVeylinCarAppService`), which the
  host resolves per platform. Without that car-side target the host falls back to the phone intent and
  the rail-widget tap does nothing. Free driving has neither a car hint nor a car tap target.
- **Rule: labels and guidance wording are shared.** One formatter produces the phone text and the car
  hint, and the manoeuvre instruction uses the same wording as the on-screen next-turn display. The car
  MAY use car-only text roles through `CarAppExtender` (the instruction its primary text, the
  distance/arrival its secondary), while the phone notification keeps the destination name first —
  a rendering difference from one content source, not a second set of strings.
- **Rule: NAVIGATION content is destination, manoeuvre, distance, arrival and the stop action.** The
  notification carries the destination name (or the neutral "Navigation active" when unknown), the
  manoeuvre instruction in the same wording as the on-screen next-turn display, the distance-to-turn,
  arrival/remaining time and remaining distance, and the "Stop Navigation" action (the same label as
  the on-screen stop button, `UIPhoneMap.md` §7b). The car hint shows the instruction, the
  distance-to-turn with the arrival time, the manoeuvre arrow as large icon and the same stop action.
- **Rule: a known zero distance is not an unknown distance.** The distance-to-turn SHALL render a
  **known zero** as `0 m`, while a distance that is absent or `NaN` renders `--` — on the phone
  notification's stats line and the car hint's secondary text alike.
- **Rule: FREE_DRIVE content reads the shared free-driving status.** The notification carries the
  current street/ref (same text as the free-driving view's street label) and the current speed, with no
  destination-dependent guidance and no stop action. The road and speed come from the **shared
  free-driving status** the on-screen label also reads, so the shade and the label cannot disagree: one
  resolution per fix, never a lookup of the notification's own.
- **Rule: an unknown free-driving value leaves no empty fragment.** An unknown speed SHALL leave the
  street alone with no trailing separator and no empty slot, an unknown road SHALL show the off-road
  fallback, and no fix yet SHALL show the neutral title without a street or speed.
- **Rule: the channels differ by platform.** The phone channel SHALL stay silent and badge-free; on
  Android Automotive OS the notification SHALL use a car channel of at least `IMPORTANCE_DEFAULT`,
  because the platform does not represent low-importance foreground-service notifications at all. No
  importance level requests a heads-up notification for turn hints.
- **Rule: the notification is re-posted only when its host-visible content changed.** The content that
  gates a re-post is the manoeuvre, the distance bucket, the current road, the remaining distance, the
  arrival minute and the free-driving speed; guidance changes still post immediately, and trip metadata
  stays content-deduplicated.
- **Rule: the notification is silent and ongoing.** It never plays a sound or vibrates.

Why the platforms behave as stated — the host's suppression of car turn hints in free driving and the
absence of a car notification shade — is `UIPhone.why.md` §18.

## 19. Ongoing notification robustness (phone + car)

- **Rule: `Notification.actions` is nullable and its icon is read with `getIcon()`.** Test `actions?.isEmpty() != false`, and read the icon with
  `getIcon()` — the Kotlin `icon` field is deprecated and breaks a warning-free build.

