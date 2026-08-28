## Context

NaviVeylin renders OSM-derived maps and routes via libosmscout (see proposal.md — Why). The map canvas screen already has fixed overlay controls: top-left action column (menu/search/favorites), top-right view column (compass/location/zoom), bottom-left re-center button. The bottom-right corner is currently free. The app has an existing About dialog (`about-dialog` capability) reachable from the map menu. No native/libosmscout changes are needed — attribution is pure UI.

## Goals / Non-Goals

**Goals:**
- Compliant OSM attribution on the map per OSMF Attribution Guidelines (interactive map + routing engine safe harbour).
- Licence information reachable at all times, even when the notice collapses.
- Same behavior on mobile and automotive flavors.

**Non-Goals:**
- No changes to map rendering, stylesheets, or native code.
- No dynamic per-region attribution (guidelines prefer it but do not require it).
- No changes to Android Auto projection screens (out of scope for this change; map attribution on the phone screen covers the projection use case).

## Decisions

**1. Attribution placement: bottom-right corner of the map canvas.**
The guidelines say any corner is acceptable; lower-right is traditional. Bottom-right is the only corner not occupied by existing controls (top-left action column, top-right view column, bottom-left re-center). Alternatives considered: bottom-left (conflicts with re-center button), top corners (conflict with overlay columns).

**2. Collapse behaviour: auto-hide after 5 seconds; reappear on map interaction.**
Per guidelines, the notice may fade after five seconds or on map interaction. Implementation: a `LaunchedEffect` timer fades the notice out after 5s of no interaction; any map pan/zoom gesture resets the timer and shows the notice. Alternatives considered: dismiss-only via "x" (adds a control, less discoverable), always-visible (simplest but more visual noise; guidelines explicitly allow collapse).

**3. Licence access: "(i)" button opens a licence dialog; About dialog gains an OSM licence link.**
When collapsed, the user must still find licence info. An "(i)" icon button sits next to the attribution and opens a small dialog showing the attribution text, the ODbL statement, and a link to https://www.openstreetmap.org/copyright. The About dialog also gains the same link (proposal requirement). Alternatives considered: "(i)" opens the browser directly — rejected because AAOS head units typically have no browser; the dialog shows the licence statement inline and offers the link best-effort.

**4. Links open via `ACTION_VIEW` intent with `https://www.openstreetmap.org/copyright`.**
Standard Android pattern, no new dependencies. On AAOS without a browser the intent may no-op; the licence dialog still shows the ODbL statement inline, so the requirement is met without the link.

**5. Strings in `res/values/strings.xml`; shared composable in `ui/` package.**
Attribution text ("© OpenStreetMap contributors"), ODbL statement, and "(i)" content description as string resources. A single `OsmAttributionOverlay` composable used by the map canvas screen; the licence dialog is a small `AlertDialog`/`ModalBottomSheet`-style composable. Both flavors share the same code path (no flavor-specific divergence).

**6. Android Auto: attribution drawn on the map surface + licence in the car About screen.**
The car app renders the map to a host surface via `AutoMapRenderer`; the renderer's `overlayDrawer` hook (already used for the compass rose and street-name label) draws "© OpenStreetMap contributors" at the bottom-right of the surface in both free driving and navigation. The car About screen gains an OSM licence section (ODbL statement + pane-level action linking to openstreetmap.org/copyright — PaneTemplate rows are not actionable per UI.md §3). Licence info is reachable from the map in both modes: during free driving via the content menu's About row (the `MapWithContentTemplate` action strip is host-capped at 4 actions — search/settings/zoom already fill it), and during navigation via an "(i)" info action on the `NavigationTemplate` action strip (no content slot there). Alternatives considered: host-rendered attribution in the `MapWithContentTemplate` content slot (disappears during navigation — `NavigationTemplate` has no content slot), action-strip-only (no visible notice on the map).

## Risks / Trade-offs

- [AAOS head units lack a browser, so the copyright link may not open] → Licence dialog shows the ODbL statement inline; link is best-effort. About dialog link still works on phones.
- [Attribution overlaps map content or is hard to read on light backgrounds] → Semi-transparent scrim behind the text, WCAG-checked contrast; verify visually on both light/dark map styles.
- [Auto-hide timer fights with user reading the notice] → 5s matches the guideline's safe harbour; notice reappears on any interaction, and "(i)" is always available.
- [Compose UI tests for fade timing are flaky] → Test visibility state transitions (visible → hidden → reachable via "(i)") rather than exact timing; use test clocks where needed.

## Migration Plan

New feature, no migration. Rollback: revert the change; no data or persisted state involved.

## Open Questions

None — placement, collapse behaviour, and licence access are resolved above and match the specs.
