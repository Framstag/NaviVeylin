# aa-better-details-view

## Why

The Android Auto destination details view lags the phone variant: rows mix labeled and unlabeled attributes (bare region/postal rows next to labeled description entries), the pane offers only a single "Navigate here" action, no map preview is shown, and the header title is the generic "Location". Drivers get less context and fewer options than phone users before starting navigation.

## What Changes

- **Consistent label/value rows**: every attribute row shows a label with its value. Region/postal rows gain labels ("Area", "Postal code"), the coordinates row gains a label, and description entries keep their label/value pairing. No bare unlabeled rows remain.
- **Second pane action "Show"**: shows the destination on the map — dismisses the details view and centers the browse map on the location, matching the phone's "Show on map" action (POI search flow). "Navigate here" stays the primary action.
- **Map preview in the details view**: switch from `PaneTemplate` to `MapWithContentTemplate` with the pane embedded on a libosmscout-rendered map surface (`AutoMapRenderer` + `SurfaceCallback`, destination marker at the location). The pane template design guide explicitly supports embedding a pane in the Map + Content template.
- **Better title**: the header shows the destination name (description "Name" entry), falling back to the resolved address, then the search label, then "Location" — mirroring the phone's title logic.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-destination-details`: requirements change — the details screen shows a map preview with a destination marker, consistent labeled attribute rows, a second "Show" action, and a destination-derived title instead of the generic "Location".

## Impact

- `:auto` module:
  - `DetailsScreen.kt` — `PaneTemplate` → `MapWithContentTemplate` (map surface + pane content); row building gains consistent labels; second "Show" action; header title from destination name/address.
  - `MapScreen.kt` — map-surface plumbing (surface callback lifecycle, renderer setup) may be shared or mirrored for the details screen's map preview.
  - `auto/src/test/java/com/naviveylin/auto/DetailsScreenTest.kt` — updated for the new template, rows, actions, and title.
- Spec: `openspec/specs/auto-destination-details/spec.md` updated (map preview, "Show" action, labeled rows, title).
- No new dependencies: car-app 1.7.0 already provides `MapWithContentTemplate` and `PaneTemplate`-as-content; the auto module already renders maps.
