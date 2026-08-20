# details-full-screen-with-map Proposal

## What Changes

The details dialog — currently a draggable `ModalBottomSheet` that floats over the map — becomes a **full-screen** dialog. Layout, top to bottom:

1. Object name (title)
2. Interactive mini map showing the object's surroundings
3. Structured description list (sections with label/value rows)

The full-screen dialog replaces the bottom sheet for all existing entry points (search result selection, long-press, POI search, favorites) and keeps the existing actions (favorite add/remove, route, show on map).

The mini map is a **reusable interactive map widget** — not a static snapshot. It supports:

- Small zoom buttons (+/−)
- Panning (single-finger drag)
- Always north-aligned (rotation locked at 0, independent of the main map's bearing)
- A marker identifying the selected object

The widget is designed for reuse: further screens (e.g., route panel, favorites detail, place cards) are planned to embed the same control.

Additional content changes in the full-screen details:

- **Admin region** becomes an explicit list entry (label/value row) instead of a plain text line under the name.
- **Address** shown as a list entry when the object has a house number (`houseNumber` / `addr:housenumber`).

Back gesture (system back + predictive back) closes the dialog and returns to the previous view (the map).

## Capabilities

### New Capabilities

- `mini-map`: Reusable interactive mini map widget. Owns an independent viewport/renderer (does not move the main map), renders the object's surroundings with small zoom buttons, single-finger pan, fixed north alignment, and an object marker overlay.

### Modified Capabilities

- `enhanced-details-sheet`: The details view changes from a draggable `ModalBottomSheet` to a full-screen dialog. Back gesture closes it. Admin region appears as a structured list entry. Address (house number) appears as a list entry when present. Map content behind the sheet no longer applies — the full-screen dialog embeds its own mini map.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` — rewrite from `ModalBottomSheet` to full-screen dialog container; layout reorder (name → mini map → list); admin region + address as list entries.
- New widget file `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt` (reusable, package-private API for now) — zoom buttons, pan gesture, north lock, marker.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — wiring only: swap sheet composition for dialog; pass entry, description, favorite state, actions unchanged.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — expose what the mini map needs (client/OSMScoutClient or a renderer factory, initial viewport from `selectedLocation`); `showDetailsSheet`/`detailsFromPoiSearch`/`showOnMap` behavior preserved.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — reused as the render backend for the widget (second instance with its own viewport); no signature changes expected.
- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` + `core/ProjectionUtils.kt` — marker projection reused for the mini map object marker.
- Data: no JNI/native changes. Address already surfaces as a native description entry (labelKey `Address`, value = house number, section "Location") via `AddressFeature`; `LocationEntry.adminRegionHierarchy` already carries the admin region.
- Tests: compose tests for the full-screen details dialog and the mini map widget (`FakeOSMScoutClient` already provides the host-side stub for unit tests).
