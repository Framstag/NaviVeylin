# UI Guidelines — cross-variant parity, shared surfaces & presentation

Rules and constraints for the user-facing UI that both variants share, distilled from the
OpenSpec requirements. Read before adding or changing a UI element — especially anything
that exists in **both** variants.

Target behavior: users get a consistent experience on both surfaces — same labels, same
visual hierarchy, same semantics — with deviations only where a car-host platform
constraint makes them unavoidable.

The car display's own surfaces (templates, the renderer's startup, the settings screens'
loading, the wait notices, the details list and the search surface) are
`guidelines/UICar.md`, which carries their sections under their own numbers. The phone's
own surfaces are `guidelines/UIPhone.md` and `guidelines/UIPhoneMap.md`. This document
carries the parity rule, the shared data resolution, the map presentation both variants use,
dark mode, localisation and the maintenance rule for both documents.

**Read the section that owns your topic, not this whole document.** The section list is
`grep -n '^## ' guidelines/UI.md`; AGENTS.md's Documentation Map routes a kind of change
here.

---

## 1. Cross-variant UI parity (general rule)

Source: spec `cross-variant-ui-parity` (change `align-details-actions-and-shared-data`).

- **Rule: same labels and visual style across variants.** Similar UI elements in the
  phone and Android Auto variants SHALL use the same **labels** and the same **visual
  style** wherever the platform constraints of both variants allow it.
- **Rule: a forced deviation is minimal and keeps the label.** When a car-host
  constraint forces a difference (e.g. rows instead of buttons, glyph marks instead of
  icons), deviate **only as much as required** and keep the **same label**.
- **Rule: the action hierarchy is reflected on both variants.** Primary/secondary/destructive
  hierarchy must be reflected in both variants' styling where the platform allows.
- **Rule: a documented deviation names its platform constraint.** A deliberate deviation
  from parity SHALL record the platform constraint that forces it, and the phone and car
  surfaces SHALL keep the same stored data even where the interaction differs.

The record of the parity decisions already made — one row per element, its phone form, its car form and
the constraint behind each deviation — is `UI.why.md` §1. Where a row states a constraint on new work,
that constraint is stated here (or in the section that owns the surface) and the row is its evidence.

The phone's own surfaces and the favourites-management affordances that this rule keeps at
parity are `guidelines/UIPhone.md` — the browsing hierarchy (§12), the row and card actions
(§13, §14), the starred chip and drag checklist (§15, §16), the vehicle-position setting
(§17) and the ongoing navigation notification (§18, §19).

---

## 6. Shared data resolution (both variants)

Source: `DetailsResolver` in `:core` (specs `auto-destination-details`,
`enhanced-details-sheet`).

- **Rule: the shared address is street, house number, postal code and city.** Address: street + house number + postal code + city
  (`"Hauptstraße 12, 44339 Dortmund"`); street from description → reverse
  lookup → digit-bearing label; postal/city from reverse lookup → hierarchy →
  IsIn.
- **Rule: the shared area falls back through the admin hierarchy.** Area: admin hierarchy → reverse region → description `IsIn` → postal.
- **Rule: the destination identity falls back name → address → area → hint.** Destination identity: name → address → area → hint.
- **Rule: one `DetailsData` bundle serves both UIs.** One `DetailsData` bundle (`DetailsResolver.resolve`) is consumed by both UIs —
  never re-derive per view; extend the resolver instead.
- **Rule: the favorites browsing hierarchy is shared across surfaces.** Group level → that group's
  favorites on both surfaces, with the group row's count drawn from the same `favorite_count` wording the
  phone's group card uses (see `UIPhone.md` §12, "Favorites browsing hierarchy"). The one deliberate
  deviation is the empty group — the car hides it, the phone's grid lists it.

## 8d. Cross-variant map presentation

Source: specs `smooth-follow`, `auto-map-layout`, `location-options-ui`, `reroute-trigger`,
`current-road-info`, `route-appearance`.

- **Rule: anchors are per-surface; labels and hierarchy are shared.** The two surfaces SHARE the same
  15-position grid, the same labels and the same hierarchy, but each SHALL store its **own** routing
  and free-driving anchor: the covered regions differ (phone: turn card, routing-status card, widget
  column; AA: a host pane that can cover ~40% of the width), so one shared value would be wrong on one
  of them. On the phone the preset fraction is relative to the **visible map area** (canvas minus the
  measured overlays); on Android Auto the host's panel is a **forbidden band**, not a remap — a preset
  inside it moves to the nearest free position while every other preset (including the default
  center/center) keeps its exact fraction. Picker labels and hierarchy stay identical.
- **Rule: one reroute policy serves the session on both surfaces.** One process-scoped navigation
  engine SHALL apply **one** reroute policy to the session both surfaces render — 30 m fast path, 4 s
  confirmation floor, 25 s cooldown, 100 m accuracy guard, 30 s tunnel guard
  (`FAST_PATH_DISTANCE_M`, `MIN_OFF_ROUTE_DURATION_MS`, `REROUTE_COOLDOWN_MS`, `MAX_REROUTE_ACCURACY`
  and `TUNNEL_REROUTE_GUARD_MS` in `NavigationEngine`; `ReroutePolicyGuidelineParityTest` holds this
  bullet to them). No surface carries its own thresholds, interval gate or confirmation rule; only the
  *presentation* of the reroute stays per surface.
- **Rule: street-name labels follow the row rule on both surfaces.** The street-name label's placement
  SHALL key on the anchor row, not on the host's stable/visible-area rects: a bottom-row anchor preset
  (`fy = 0.9`) places the label at the **top** edge, a top-row preset (`fy = 0.1`) and the middle row
  (including the default center) place it at the **bottom** edge, anchored to the real surface edges
  (16 dp padding), so the pill cannot float mid-screen. The row rule is identical on phone and Android
  Auto — only the layout container differs (Compose alignment vs Canvas anchor). Routing on Android
  Auto keeps the street name in the host travel-estimate card via `TravelEstimate.setTripText`, never
  on the map surface.
- **Rule: the free-driving street label shows the road's ref name.** The phone free-driving street
  label SHALL be a bottom-center pill (`surfaceVariant` at 0.92 alpha, 10dp rounded, `titleMedium`
  text), shown only when no route is active and a road is resolved; its text is the road's "ref name"
  (e.g. "B 1 Hauptstraße"), blank when the road has no name/ref. The road is resolved bearing-aware
  (`road-lookup-bearing`).
- **Rule: one route palette and one marker palette per presentation.** Both surfaces SHALL show
  identical route colours and one unified vehicle marker for the same presentation, and no surface may
  override them. The active route is drawn by one stylesheet rule (`_route` in the libosmscout
  submodule `stylesheets/include/route.oss`), driven by the same `daylight` flag both surfaces push;
  the vehicle marker is one unified compass arrow, 38 dp density-aware, whose palette branches on the
  resolved dark presentation while its geometry never does. The accuracy circle's fill and border
  follow the resolved presentation through the `:core` `GpsMarkerPalette`, and their radius and stroke
  width never branch — only the palette does. (The palettes' hexes and their contrast derivations are
  `UIPhone.why.md` §8d.)
- **Rule: the casing stays opaque and wider than the fill.** The route casing SHALL stay opaque and
  wider than the fill (`displayWidth` 2.2 mm vs 1.5 mm) and SHALL keep its priority, so the route is
  bordered on both sides and the composited centre is "fill over casing", independent of the road
  underneath — a street label drawn after the way fill stays readable on the route.
- **Rule: stylesheet hex literals are lowercase.** Stylesheet hex literals MUST be **lowercase** —
  `osmscout::Color::FromHexString` accepts `0-9a-f` only and asserts on uppercase, which fails the
  whole stylesheet load and then crashes the renderer; `StylesheetHexColorCaseTest` guards this for
  every packaged stylesheet.
- **Rule: the Android Auto surface indicators keep their fixed palettes.** The AA compass rose SHALL be
  56dp, the speed-limit sign 56dp with a 7dp red ring and 24sp digits, and the speed badge (128×52,
  20sp) SHALL keep its layout, changing only its overspeed state (red-600 fill at the badge's 0xCC
  alpha with white text). The rose palette follows the surface's resolved presentation; the speed
  badge and speed-limit sign keep their fixed palettes (the sign is a standard-mandated traffic sign).
- **Rule: the spoken-announcement switches are per surface, and the GPS switch is shared.** The
  **voice** switch is per surface (both default **on**); the **GPS announcements** switch is **one
  shared value** edited from either surface and default **off**, and a GPS state is spoken only after
  it has held 5 s, so a flapping fix announces the state it settles on rather than every edge.
- **Rule: the location-options-only settings are phone-only.** The overspeed delta, the maximum speed
  per vehicle and the map long-press delay SHALL be configurable in the phone's location-options sheet
  only; the car reads the same stored values but carries no control for them (no car surface has a
  long-press gesture).


## 9. Dark mode (phone + Android Auto)

Source: spec `dark-mode` (changes `aa-dark-mode-follow-host`, `phone-ambient-light-dark-mode`, `aa-chrome-theme-documentation`).

- **Rule: the phone dark-mode preference is three-state with an ambient option.** Three-state preference (On / Off / Automatic) in the on-map
  settings sheet, plus the "Adaptive by ambient light" toggle (default off).
  Automatic follows the system night mode; with the ambient option enabled it
  follows the light sensor (hysteresis 10/50 lux, 10 s debounce) instead.
  Sensor listening is foreground-only and gated on Automatic + option enabled.
  Devices without a light sensor fall back to the system signal.
- **Rule: the Android Auto dark mode has two layers, owned differently.**
  - **Rule: the template chrome is host-themed, never app-themed.** Chrome (menus, dialogs, lists, panels, navigation banner,
    ETA card) is host-rendered and host-themed — never app-themed (the car
    app library exposes no chrome-color API; verified on 1.7.0). Day/night of
    the chrome follows the host's own display theme: `Android Auto >
    Settings > Display > Theme` on projection, or the head unit's system
    theme on AAOS (often OEM-pinned dark). Chrome was dark-only by default
    from 2019; Google's system light theme started rolling out from late
    2025. A user report of "UI always black, map flips" is expected host
    behavior, not an app bug — the host-side theme switch is the control.
  - **Rule: the car map surface follows the host's day/night state.** The map surface (app-drawn) follows the HOST's day/night state
    (`CarContext.isDarkMode()`, live via `onCarConfigurationChanged`) — never
    the phone's system mode; only the surface needs the daylight-flag push.
  - **Rule: the app's dark-mode preference never influences host chrome.** It affects the surface and
    app-drawn controls only.
- **Rule: both variants render the same stylesheet variants.** Both variants render the same stylesheet variants (daylight flag
  set/unset); the environment source differs by platform (see
  `guidelines/MapStyling.md` §15).
- **Rule: status colors live outside the theme.** (change `compass-day-night-palette`.)
  A control that carries status through a fixed hue family (the compass GPS-fix
  fill; the overspeed warning red) dims by switching to a dark tone of its own
  hue family — it must never keep a light-tone status fill in dark presentation,
  and must never take the dark scheme's role for that hue (M3's dark `error` is a
  light pink: `#F28B82`). Each such palette carries its own on-color pair so
  symbol-on-status contrast is guaranteed in both presentations rather than
  inherited from a user-themable role.
- **Rule: only navigation-essential overlays stay visible in dark presentation.**
  (change `fix-map-overlay-dark-palette`, spec `dark-mode`.) Dark presentation
  darkens the map and keeps the number of visually loud elements small, so an
  app-drawn element keeps a clearly visible tone only when it carries
  navigation-essential information. The exempt set is small on purpose and is
  enumerated here rather than chosen per element: the vehicle marker, the active
  route, and the mini map's current-position dot (it is where the user is — spec
  `mini-map`). Every other app-drawn element dims to a dark tone of its own hue
  family: the compass status fill (above), the accuracy circle on both surfaces,
  and the mini map's dot halo and casing. The accuracy circle and the mini map's
  marker read the `:core` `GpsMarkerPalette` (spec `gps-location-marker`, spec
  `mini-map`), and the dot deliberately leaves the theme roles
  (`primary`/`secondary`/`tertiary`) the mini map's object markers use — that
  role *was* the dot's colour until this change (`#1A73E8` = `PrimaryLight`).
- **Rule: the overlay palette follows the applied variant, not the requested one.**
  (change `compass-day-night-palette`.) `RendererGate.setDarkPresentation` is called from the screen's daylight
  collector once the native stylesheet flag was actually applied, so the vehicle
  marker and the compass rose flip in the same frame as the map variant — the
  overlay palette can never belong to the other presentation than the map
  underneath. Surface drawers read `RendererGate.currentDarkPresentation()`.
- **Rule: every car screen that draws the map surface observes the resolved presentation.**
  `MapScreen`, `NavigationScreen` and `FreeDrivingScreen` each
  own their renderer and their gate, so each needs its own `resolvedDark` input,
  a `KEY_DARK` observation in its `*ScreenObservations` (never a bare
  `scope.launch`) and a `pushDark()` re-push once the renderer is ready. Free
  driving was missing all of it until `compass-day-night-palette`: its map kept
  the daylight variant at night and its rose could never reach the night palette.

## 10. Internationalisation / Localisation

- **Rule: every user-facing number and coordinate formatter takes an explicit locale.** (§40.26.)
  `String.format(Locale.US, …)`. Default-locale formatting broke tests and reads ambiguously in German; the
  `:core` `CoordinateFormat` seam is the fix for that family (`fix-comma-decimal-coordinate-entry`). Note
  that `"%.5f".format()` **rounds, it does not truncate**.
- **Rule: `distanceForDisplay` reports display units.** (§40.27.) Assert
  `displayDistance` plus `displayUnit`, never metres.

Source: spec `i18n-l10n` (change `i18n-l10n-support`).

- **Rule: all user-facing text lives in Android string resources.** Never
  hardcoded in composables, templates, or dialogs. English is the default
  (`res/values/strings.xml`) and the fallback; German is fully supported
  (`res/values-de/strings.xml`).
- **Rule: the phone reads strings with `stringResource`.** Read strings with `stringResource(R.string.x)` (or
  `pluralStringResource(R.plurals.x, count)` for count-dependent text) inside
  composables. Hoist to a `val` when the value is needed in a non-composable
  context (semantics blocks, lambdas, companion functions).
- **Rule: the car reads strings with `carContext.getString`.** Read strings with `carContext.getString(R.string.x)`
  (`getQuantityString` for plurals). Pure factory functions that build
  templates take a `carContext` parameter so they stay testable.
- **Rule: `:auto` keeps its own resources, and every key exists in both locales.** `:auto` keeps its own `res/` (library resources merge
  into the app at build time). Both modules carry `values/` + `values-de/`;
  every translatable key in `values/` MUST exist in `values-de/` (enforced by
  `GermanTranslationCompletenessTest` in each module).
- **Rule: a shared module owns no wording.** `:core` composes display text for both
  surfaces (details title, notification titles, turn instructions) and MUST take
  the words from its caller — a `String`, a `StringResolver` lookup or a resource
  the surface passes in — never from a literal of its own. The generic details
  title is the worked example: `DetailsResolver.resolveTitle(input, genericTitle)`
  takes the surface's `R.string.location_title_generic` (change
  `fix-remaining-untranslated-strings`). Any wording both surfaces show SHALL have
  exactly one resource home (e.g. `:core`'s `nav_hint_neutral`, used by the car
  hint and the phone notification).
- **Rule: numbers are locale-aware through the shared helpers.** Use the shared helpers in
  `core/.../DistanceFormat.kt` — `formatDistanceNumber(meters, locale)` returns
  the numeric part only (German comma decimals), `distanceUsesKilometers`
  selects the unit. The unit suffix comes from a resource
  (`distance_unit_km` / `distance_unit_m` / `size_unit_mb`), never from code.
  A **step's** duration uses `formatStepDurationText` (seconds below a minute, so a 45 s leg never reads
  "0 min"); `formatDurationText` stays with the route's total, the notification and the ETA strings.
  Do NOT use `Locale.ROOT` for display formatting.
- **Rule: coordinate strings are data, not display text.** They are the one exception
  to locale-aware numbers (spec: `i18n-l10n` — Coordinate string is
  locale-stable). Format them with `core/.../CoordinateFormat.kt` —
  `formatCoordinatePair(lat, lon, pattern)` (the phone passes the
  `coordinates_format` resource, the car uses the default pattern) and
  `formatCoordinate(value)` for an input field's prefill — so a pair always
  reads `51.51391, 7.47434` and never `51,51391, 7,47434`, which keeps
  `DetailsResolver`'s coordinate-label detector working. Entry goes the other
  way: `parseLatitude` / `parseLongitude` accept both `,` and `.`, so a
  prefilled value can be saved unchanged and a German user may type a comma.
- **Rule: count-dependent strings use `plurals.xml`.** They use `plurals.xml` (`one`/`other` for
  English and German) — e.g. `file_count`, `active_downloads`, `favorite_count`,
  `map_downloading_maps`. Pass the count as the format argument
  (`pluralStringResource(R.plurals.x, count, count)`, `getQuantityString(id, count,
  count)`), never concatenate an English suffix: a German plural rule cannot be
  expressed by one.
- **Rule: positional format args are used so translators can reorder.** Positional args use `%1$s`/`%2$s` so translators can
  reorder; never concatenate translated fragments.
- **Rule: POI category names come from resources and match across surfaces.** Category names come from resources
  (`poi_category_*`), mapped per module (phone `categoryLabelRes`, auto
  resource lookups with English fallback for unknown IDs). Phone and Auto
  SHALL show the same category labels (parity, §1).
- **Rule: user-facing diagnostics text is translated; logcat-only is exempt.** User-facing diagnostics UI text is translated; logcat-only
  debug strings are exempt.
- **Rule: a hardcoded-string gate fails the build on a literal in a UI text position.** `HardcodedText` runs at error severity in `:app`, `:auto`
  *and* `:core` (`lint.xml` + `abortOnError` in each), and a Gradle
  `checkHardcodedStrings` gate — wired into every module's `preBuild`, rule and
  message in `buildSrc` (`com.naviveylin.build.i18n.HardcodedStringScanner`,
  unit-tested) — fails the build on a literal in a UI text position. The scan
  covers, in every module:
  - a call position (`Text(`, `.setTitle(`, `.setText(`, `.addText(`,
    `.setContentTitle/Text(`, `reportError(`);
  - **a field whose name ends in a display word** — `text`, `title`, `label`,
    `description`, `hint`, `placeholder`, `message`, `error` — whatever qualifier
    the name carries: a rendered state field is *assigned*, not called, so
    `error = "Could not open map database"`, `snackbarMessage = "…"`,
    `statusText = "Starting"` and `poiSearchError = "…"` are text positions in
    every module (they were display text the scan never examined, `TODO.md` §192),
    including a conditional argument (`text = if (x) "A" else "B"`).
    `detail` is not one: it is the diagnostics log's payload, and logcat-only
    wording is exempt;
  - a position that begins **inside** an already-open string literal is not a text
    position — a diagnostics message may contain a word that looks like one
    (`" labelHint=" + …` is prose, not an assignment);
  - **interpolated display text** — a literal whose text *outside* its `${...}`
    carries a word (`"$favCount favorite"` fails; a pure value template such as
    `"$zoomLevel"` or `"${a}-${b}"` stays exempt). Hand-rolled pluralization
    cannot be translated, so it is a defect, not a template;
  - a `NotificationChannel(id, name, description)` name/description argument
    (user-visible in Settings; re-applying them on start updates an existing
    channel).

  The scan is line-based: a position and its literal must share a line, so `Text(`
  with its sentence on the next line is still invisible. Widening that means
  paren-balanced scanning across lines for every position — a change of its own.

  Deliberate exemptions (kept small on purpose): `%`-format templates,
  symbol-only separators (`"|"`, `" · "`) and a single camelCase identifier
  (an animation/label key such as `compassRotation`). A message that only
  mentions a UI word in prose MUST be reworded — the gate has no allowlist.
- **Rule: no log or diagnostics call interpolates a position.** No log or diagnostics call may interpolate a position
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
- **Rule: layouts are direction-agnostic under RTL.** `supportsRtl="true"` is set; keep layouts direction-agnostic
  (use `start`/`end` alignment, not `left`/`right`) so future RTL locales
  work without layout changes.

---

## 20. Keeping this document honest

- Specs are the contract; this document is the condensed knowledge base.
- When what a spec requirement or a code constant owns changes — a label, a style, a hierarchy, or a
  **number** — update every restatement in this document and in `UICar.md` in the same change: the parity
  table (§1), the glyph table (`UICar.md` §2), and each section listed below that quotes the value. A section
  is a restatement when it writes a value down that another artefact already owns; the owning artefact stays
  the normative home.
- **Rule: a restated value names its guard.** A section that restates such a value SHALL name the case that
  holds the two together, or the requirement that owns it — a restatement with no guard is a defect and is
  given one or removed in the change that finds it (`TODO.md` §187: the reroute-policy bullet kept
  50 m / 10 s through the change that moved the policy to 30 m / 4 s, because this rule obliged nothing
  outside §1 and `UICar.md` §2). The restatements this document owns, and the case that holds each:
  - §1 "Cross-variant UI parity (general rule)" — `GermanTranslationCompletenessTest`,
    `FavoritesViewModelTest`, `FavoritesScreenTest`
  - §10 "Internationalisation / Localisation" — `GermanTranslationCompletenessTest`

The restatements `UIPhoneMap.md` owns, and the case that holds each, are listed in its §7a, §8b, §8c and §11.
