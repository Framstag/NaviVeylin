# Design Stack & data guidelines — Stack, UI patterns and persistence

The stack and the data rules a screen is built against, carried over under their own numbers §2 (stack & UI
patterns) and §9 (data, persistence & search) from the `Design.md` document this cluster was cut out of. The
recipes for adding a dependency, a screen, a full-screen sheet, a details sheet or a native function are
procedure and live in `guidelines/Design.recipes.md` §2.

**Rule naming** — a rule begins with a bold `Rule:` token, and the first bold span of its bullet is the rule's
name; a name is unique across `guidelines/*.md`, and another document references it by the rule-reference form
the route check resolves.

---

## 2. Stack & UI patterns

- **Rule: single-activity architecture - screens are composables, not activities** Single-activity architecture: screens are composables, not activities.
  Compose + Material 3, Navigation Compose, ViewModel + StateFlow, Hilt,
  Coroutines. Google Play Services may be used (Fused location, car
  MapController) but MUST NOT be a hard dependency — provider abstraction,
  runtime availability check, and fallback (e.g., LocationManager) keep it
  replaceable by design. No Google Maps SDK (rendering is libosmscout); no
  Google account required; sideloading is a supported distribution path.
- **Rule: persistence is JSON-file based; no duplicate database layer** Persistence is JSON-file based (JNI favorites, settings, search history);
  don't add a database layer that duplicates what the native layer already
  persists.
- **Rule: bottom sheets are the standard overlay pattern** Bottom sheets are the standard overlay pattern; full-screen sheets for
  management surfaces that keep the map alive underneath. Both compose on top
  of the map screen via state flags — no nav-graph changes.
- **Rule: always-visible navigation info lives in overlays, not dialogs** Always-visible navigation info lives in overlays on the map canvas, not
  dialogs.
- **Rule: a dialog that must cover a bottom sheet lives at top-level screen scope** Dialogs that must cover a bottom sheet live at top-level screen scope
  (separate windows can hide behind the sheet window). The route-planning session is **one**
  surface on the phone for exactly this reason: its step summary used to be a second window
  (capped at 400 dp, rows without a click handler, its own dismissal ordering) and was
  deleted in favour of doing everything in the card (design D10, 2026-10-03).
- **Rule: route planning is a session, not a sheet** Route planning is a **session**, not a sheet (change `route-planning-session`, spec
  `route-planning-session`): the phone overlay is **two fixed card heights** (compact /
  expanded) plus a hidden pill, and docks to the side when the surface is wider than it is
  tall — the docked layout carries the selectable step list, the phone's selector is the
  step navigator inside the expanded card. The heights are fixed shares of the screen
  (expanded ≤ 45 %, so the map always keeps ≥ 55 %), never content-driven: a Material3
  sheet's partially expanded anchor is a fraction of the *content*, and the same panel
  measured 36 % (edit) and 48 % (route) for what the session called "compact" and ~97 %
  expanded on the device. The session owns the camera and the route overlay while it is
  active — a lease on top of BROWSE / FREE_DRIVE / NAVIGATION, never a fourth map mode. Its
  only exits are Start Navigation and Cancel/End; stopping navigation gives a bounded grace
  with Restart / End. The route-overview fit uses the height the card **reports**
  (`MapCanvasViewModel.setOverlayCoveredPx`, measured by the card's `onSizeChanged`) and
  re-runs when that height changes while a route is reviewed — the nominal fraction it
  replaced left 614 px of route behind the card; an unchanged height never moves a
  user-moved viewport.
- **Rule: a Compose map-content overlay sits above the frame bitmap and below the markers** Compose overlays that project map content sit above the frame bitmap and below the
  markers — see `guidelines/MapRendering.md` (analysed-route-segment overlay).


## 9. Data, persistence & search

- **Rule: maps and large files live in app-internal storage; downloads use temp-suffix files** Maps and other large files live in app-internal storage; downloads use
  temp-suffix files so partials are never picked up; register storage with
  the native layer at startup. Long-running work (downloads) keeps the
  process alive (wake lock / foreground service).
- **Rule: an installed-map update is staged beside its target and swapped in when complete** An update of installed map data is staged beside its target and swapped in
  only when it is complete and verified (`.incoming` / `.backup`, `SlotInstall`),
  so the installation a running client renders stays usable throughout; data a
  failed or interrupted attempt left in the maps tree is *recognised* by
  `SlotInstall.stagingTargetOf` rather than only hidden, and the app-start path
  removes it (spec: `map-updates`). Every reader that opens, lists, centres on or
  records a database — the start-map resolution, the map manager's installed list,
  the phone canvas, the car session — goes through `MapTreeDiscovery`, which applies
  that classification and names each leftover it drops in the diagnostics together
  with the installation it belongs to; none of them reads the raw `InstalledMaps`
  scan itself, and the update check, which does, reports the leftovers on its own. The repository update check runs at most once
  a day on the app-start path (`MapUpdates`, process-scoped) and persists its
  result — the last completed check and the installations found — in the settings
  file under the same serialised transaction as every other setting.
- **Rule: persist after completed operations, not debounced** Persist after completed operations, not debounced; persist on every
  meaningful change, not only lifecycle events (crash safety). Repositories
  wrap JNI calls in background dispatchers and expose reactive state.
- **Rule: use Android-compatible HTTP (`HttpURLConnection`), never `java.net.http`** Use Android-compatible HTTP (`HttpURLConnection`); avoid `java.net.http`.
- **Rule: an expensive scoped lookup is resolved once, cached, and reused** Expensive scoped lookups are resolved once, cached, and reused within a
  movement threshold; release native handles on close.
- **Rule: external data access is read-on-demand, never persisted, and permission-gated** External data access (contacts etc.) is read-on-demand, never persisted,
  and permission-gated.
- **Rule: search never crashes the process, whatever the data consistency** **MUST**: search never crashes the process, whatever the data consistency.
- **Rule: name matching is the map library's job** **MUST**: name matching is the map library's job: the app passes the query through
  and never re-implements matching per surface, so the phone dialog and the car
  template match identically (spec: `search-name-matching`). A matching defect
  behind an unchanged JNI contract is fixed in the library (§5), never filtered or
  patched around per surface.
- *Ordering* the library's result is not matching: a shared map link may carry a place name
  (`EXTRA_SUBJECT`, else the name in its own URL), and the phone lists the candidates whose name
  contains it first — a stable partition that drops nothing, asserted in the picker's own UI test
  as well as in the view model. A name the library did not match keeps its position, and the
  car path navigates or geocodes exactly as before (change `read-shared-google-maps-links`).

