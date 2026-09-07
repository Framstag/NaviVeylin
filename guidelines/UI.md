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

## 7. Phone map re-center button (follow / auto-zoom)

Source: spec `map-recenter-button`.

- The re-center button (crosshair/my-location icon, content description
  "Re-center on location") appears when the viewport is no longer auto-driven
  **and** a GPS fix is available:
  - follow mode was disengaged by a pan/zoom (follow mode off), or
  - auto-zoom is suspended while navigating (pinch/button zoom leaves follow
    mode on but stops auto zoom).
- Placement: bottom-left in free-form mode; while navigating it is anchored
  directly above the routing status bar (the screen-bottom area is covered by
  `NavigationStateOverlay`, so the button must never sit at the bottom edge
  during navigation).
- Tapping it re-enables follow mode, unsuspends auto-zoom, re-centers on the
  current GPS position, and hides the button (existing `reCenterAction`).
- There is no automatic re-engage: a manual interaction stops follow/auto-zoom
  until the driver taps the button — the button's presence is the only signal.
- Phone-only: the car display has its own follow behavior via the car
  MapController and is unaffected (no phone-style auto zoom).

## 8. Phone navigation overlay sizing (driver-seat readability)

Source: specs `map-speed-widget`, `compass-button`, `next-turn-overlay`.
- All phone map overlays SHALL be readable from the driver seat; minimums:
  current speed 24sp bold, speed-limit sign 64dp with 28sp digits, turn
  distance 32sp bold, turn description/destination 22sp, next-next hint 20sp
  (smaller than the primary instruction).
- The compass button SHALL be larger than the other overlay buttons
  (56dp layout / 48dp visual vs 48dp / 40dp) so it reads at a glance; the
  follow-direction triangle stays ~70% of the button.
- The speed badge SHALL use the standard overlay card container (theme
  surface at 0.92 alpha, 12dp rounded) — the same treatment as the turn card
  and routing status; NEVER a fixed dark color (theme-unaware, poor contrast
  with the overspeed warning color in light mode). Badge text SHALL be dark
  (`onSurface`) on the light card in the normal state; never white-on-light.
- Phone-only: Android Auto sizes text via the host template; parity applies to
  labels and hierarchy, not pixel sizes.
- Android Auto surface indicators (spec `auto-map-layout`): compass rose 56dp,
  speed-limit sign 56dp with 7dp red ring and 24sp digits; the speed badge
  (128×52, 20sp) is unchanged.

## 9. Dark mode (phone + Android Auto)

Source: spec `dark-mode` (changes `aa-dark-mode-follow-host`, `phone-ambient-light-dark-mode`).

- **Phone**: three-state preference (On / Off / Automatic) in the on-map
  settings sheet, plus the "Adaptive by ambient light" toggle (default off).
  Automatic follows the system night mode; with the ambient option enabled it
  follows the light sensor (hysteresis 10/50 lux, 10 s debounce) instead.
  Sensor listening is foreground-only and gated on Automatic + option enabled.
  Devices without a light sensor fall back to the system signal.
- **Android Auto**: the map surface follows the HOST's day/night state
  (`CarContext.isDarkMode()`, live via `onCarConfigurationChanged`) — never
  the phone's system mode. Templates are host-rendered and follow the host
  automatically; only the app-drawn map surface needs the flag push.
- Parity: both variants render the same stylesheet variants (daylight flag
  set/unset); the environment source differs by platform (see
  `guidelines/MapRendering.md` §15).

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
- **Locale-aware numbers**: use the shared helpers in
  `core/.../DistanceFormat.kt` — `formatDistanceNumber(meters, locale)` returns
  the numeric part only (German comma decimals), `distanceUsesKilometers`
  selects the unit. The unit suffix comes from a resource
  (`distance_unit_km` / `distance_unit_m` / `size_unit_mb`), never from code.
  Do NOT use `Locale.ROOT` for display formatting.
- **Plurals**: count-dependent strings use `plurals.xml` (`one`/`other` for
  English and German) — e.g. `file_count`, `active_downloads`.
- **Format args**: positional args use `%1$s`/`%2$s` so translators can
  reorder; never concatenate translated fragments.
- **POI categories**: category names come from resources
  (`poi_category_*`), mapped per module (phone `categoryLabelRes`, auto
  resource lookups with English fallback for unknown IDs). Phone and Auto
  SHALL show the same category labels (parity, §1).
- **Diagnostics**: user-facing diagnostics UI text is translated; logcat-only
  debug strings are exempt.
- **Lint gate**: `HardcodedText` runs at error severity in both modules, and
  a Gradle `checkHardcodedStrings` grep gate (wired into `preBuild`) fails the
  build on string literals in UI text positions — including conditional
  assignments like `contentDescription = if (x) "A" else "B"`. New UI text
  MUST pass both.
- **RTL**: `supportsRtl="true"` is set; keep layouts direction-agnostic
  (use `start`/`end` alignment, not `left`/`right`) so future RTL locales
  work without layout changes.

---

## Keeping this document honest

- Specs are the contract; this document is the condensed knowledge base.
- When a spec requirement changes labels, styling, or hierarchy, update the
  parity table (§1) and glyph table (§2) here at the same time.
