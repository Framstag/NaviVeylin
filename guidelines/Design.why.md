# Design rationale — why the architecture rules are what they are

Companion to `guidelines/Design.md` (and the sections it indexes in `DesignStack.md`, `DesignNative.md` and
`DesignCrossVariant.md`): the superseded decisions, the provenance, and the measurements behind a rule. Read it
beside the section its `## §N` anchor names.

## §13 Superseded decisions

- The live guidance path built `RouteInstruction` text in native code and parsed it back in the app
  (`TurnInstructionLocalizer`'s `"Keep "`-prefix and `"Leave motorway"` matching, the five-scalar
  `nextNext` hint) → live guidance now publishes the **route step model** (change
  `route-steps-live-guidance`, spec `osmscout-jni` — Live guidance publishes route steps) — the
  instruction type could not express a motorway change or leave, so the only thing that told them
  apart was an English sentence the app matched back; and the fallback promise ("native English when
  unmapped") existed only because the native side wrote prose. `TurnInstructionLocalizer` is deleted,
  every surface words its manoeuvre through `core/ManoeuvreLabels`, and step matching in the route's
  list compares distance **and** kind instead of description text. The `RouteInstruction` type stayed in
  the API for JavaScout one change longer and was then removed with its arrays, its field tests and the
  bridge's second description walk (change `retire-instruction-prose`, submodule commit `6c8dc6f94`): the
  prose path was deleted rather than shimmed, because a shim keeps a second model with a reader-shaped
  temptation — the defect class the two changes before it removed — while a deletion shows every consumer
  the truth at compile time. JavaScout adopted the step model in `999acc16f` and words its own rows
  through `NextTurnText`; `TurnType` stayed, because the step model's direction and motorway sub-records
  carry it.

- Dual-mode single APK (phone + AAOS in one artifact,
  `2026-08-22-add-android-automotive-os`) → two-flavor split
  (`specs/android-automotive-os`) — Play rejects the combination.
- GPS markers baked into the native render (`2026-07-29-draw-map`, auto
  phase 3) → display-layer overlays (`2026-08-14-fix-marker-visibility`) —
  cached buffers must stay marker-free.
- AA instruction hints surface-drawn (archived D4/W1) → host-rendered panel
  (`2026-08-23-aa-navigation-view`) — one source of instruction UI, never
  both.
- Bearing smoothing in the ViewModel → location layer
  (`2026-08-28-gps-bearing-smoothing`) — signal quality in the location
  layer, render protection in the render layer.
- Single render path with `blitSubRegion` → TILES/DIRECT split
  (`2026-08-17-render-mode-switch`) — dead code removed, spec corrected.
- "No new GPS load — location keeps streaming from the existing
  `LocationService`" (archived `background-navigation-notification` design) →
  an invisible, non-navigating FREE_DRIVE holds **no** location lease
  (`fix-free-drive-background-liveness`, owner decision C) — the assurance
  stopped being true when the lease mechanism landed
  (`shared-resource-arbitration`): the phone map's `PHONE_MAP` lease is released
  on pause, so the background notification keeps the last known road/speed
  instead of live values. The notification and the FGS protection remain; the
  deliberate no-lease case is pinned in `specs/location-updates-lease` so a later
  reader cannot re-add always-on fixes by accident.
- The repository's readiness flag deferred as API surface (archived
  `fix-aa-favorites-latency` design: "*`initialized` flag on the repository* —
  … Deferred") → published as its own signal
  (`AutoFavoritesProvider.storeReady`, change `fix-car-favorites-loading-stall`) —
  the deferral assumed the favourites list's loading row was short-lived, but the
  store publishes from an empty map, so "not initialised yet" and "initialised and
  holds none" were the same value and the row's end was gated on whichever async
  emission happened to arrive first. The signal is what lets the car screen show
  the row only for a genuinely uninitialised store, and the owner's 2026-10-10
  report ("seconds — noticeably stuck on Loading") is why the trade-off was
  reopened.
- The route list built by parsing the native English description lines
  (`RoutePanelViewModel.parseStepDisplay` / `parseTurnType` / `isInstructionLine`, archived
  `route-panel-ui` designs) → the list is **derived from `RouteEntry.steps`**
  (`route-panel-from-steps`, spec `route-analysis` — Rows are derived from the route's steps) — the
  prose path could not see a manoeuvre the description callback never emitted, so a roundabout exit and
  every motorway manoeuvre were missing from the list, the text was never localized, and the glyph was a
  guess from prose that could not tell a motorway change from a plain turn. The structured projection had
  shipped on every route calculation since submodule commit `62a248b84` and had no consumer; the rows are
  now that consumer. The parsing path is retired in the app here, and `RouteEntry.descriptions` (with the
  `instruction*` arrays) follows in `retire-instruction-prose`, which is also where the live path's
  `TurnInstructionLocalizer` is replaced by the rows' structure-based builder.
## Appendix B — Provenance

| Section | Key sources (`openspec/`) |
|---|---|
| 1 | `changes/archive/2026-07-26-android-project-setup`, `2026-08-27-search-address-book-persons`, `2026-08-05-auto-phase-2` |
| 2 | `2026-07-26-android-project-setup`, `2026-07-31-route-panel-ui`, `specs/app` |
| 3 | `2026-08-14-fix-marker-visibility`, `2026-08-03-dark-mode`, `2026-08-17-render-mode-switch` |
| 4 | `2026-07-29-add-location-search`, `2026-08-01-turn-by-turn-navigation`, `2026-08-13-gps-render-coalescing` |
| 5 | `2026-08-21-remove-android-logging-from-libosmscout`, `2026-08-11-search-fixes`, `2026-08-13-basemap`, `specs/osmscout-jni` |
| 6 | `specs/double-buffering`, `specs/tile-cache`, `specs/canvas-overrun`, `2026-08-17-render-mode-switch`, `2026-08-18-render-performance` |
| 7 | `2026-08-13-gps-render-coalescing`, `2026-08-28-gps-bearing-smoothing`, `2026-08-28-phone-follow-smoothing`, `specs/smooth-follow` |
| 8 | `2026-08-05-auto-phase-2/3`, `2026-08-22-free-driving-mode`, `2026-08-22-aa-ui-redesign`, `specs/android-automotive-os`, `specs/cross-variant-ui-parity` |
| 9 | `2026-07-28-initial-ui-map-download`, `2026-07-29-manage-favorites`, `2026-08-14-improved-fulltext-search`, `2026-08-27-search-address-book-persons` |
| 10 | `2026-08-19-release-target`, `2026-08-18-github-build` |
| 11 | `2026-08-03-arrow-renderer`, `2026-08-28-gps-strict-fallback`, `2026-08-14-fix-two-finger-rotation`, `AGENTS.md` |
| 12 | all of the above |

## §11 Testing

- The four cases that still decide a diagnostics assertion from the whole file predate the rule that a
  diagnostics assertion is attributed to the lines its own case wrote; the requirement `unit-test-suite-runtime`,
  "A diagnostics assertion is attributed to the lines its own case wrote", names their paths
  (issue #115, change `fix-diagnostics-sink-attribution`).

## §12 General engineering principles

- The phone's band conflict: the session card and the navigation status card sit on the same bottom edge, and
  composing both hid the review entirely (measured 2026-10-06, change `fix-route-session-stop-path`, design D8).
