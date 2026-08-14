## Why

JavaScout (libosmscout's Java demo app) has a long-press-on-map feature that resolves the closest OSM object at the pressed location and shows a structured description dialog. NaviVeylin needs this same capability. The existing `LocationDetailsSheet` (opened from search results) only shows basic info — label, admin region, coordinates — and is not draggable. Reusing and enhancing this sheet for long-press gives users rich object details (type, address, contact, opening hours, etc.) with a modern draggable UX.

## What Changes

- **Long-press gesture** on map canvas: detect 500ms hold, convert screen coords to geo, trigger object lookup
- **JNI `getDescription()` implementation** in `OSMScoutClient.cpp`: query objects in bounding box around press point, rank candidates by (has description data, visible at zoom, proximity, type priority), call `DescriptionService::GetDescription()`, marshal result to Java
- **Enhanced `LocationDetailsSheet`**: show structured `ObjectDescription` sections (General, Location, Contact, Way, etc.) with section headers, label/value rows; make sheet draggable (remove `skipPartiallyExpanded`, add drag handle)
- **ViewModel wiring**: add `onLongPress(lat, lon)` → call `client.getDescription()` on background coroutine → update state with `ObjectDescription`
- **Reuse search details sheet**: long-press opens same `LocationDetailsSheet` composable, now enhanced with description data

## Capabilities

### New Capabilities
- `long-press-details`: Long press on map resolves closest relevant OSM object (node/way/area) at the pressed coordinate and retrieves a structured `ObjectDescription` via `DescriptionService`. Object selection uses a ranking algorithm: query objects in small bounding box, rank by (has description data, visible at current zoom, proximity), prefer nodes over ways over areas at equal distance.
- `enhanced-details-sheet`: Draggable `ModalBottomSheet` displaying structured object description with section headers, subsection headers, and label/value rows. Supports add/remove favorites. Sheet is draggable to dismiss (not `skipPartiallyExpanded`). Content adapts to available description entries — sections like General (type, name), Location (address, postal code), Contact (phone, website), Way (max speed, lanes), etc.

### Modified Capabilities
- *(none — no existing NaviVeylin capability has spec-level behavior changes)*

## Impact

| Component | Impact |
|-----------|--------|
| `app/src/main/java/.../ui/map/MapCanvasScreen.kt` | **Modify** — add long-press gesture detection via `pointerInput` with timer; wire `onLongPress` callback to ViewModel |
| `app/src/main/java/.../ui/map/MapCanvasViewModel.kt` | **Modify** — add `onLongPress(lat, lon)` method; add `objectDescription` and `isLongPress` to `MapCanvasUiState`; call `client.getDescription()` on `Dispatchers.Default` |
| `app/src/main/java/.../ui/map/LocationDetailsSheet.kt` | **Modify** — accept `ObjectDescription` parameter; render structured sections; make sheet draggable (remove `skipPartiallyExpanded`, add drag handle) |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` | **Modify** — implement JNI `getDescription(lat, lon)`: query objects in bbox, rank candidates, call `DescriptionService::GetDescription()`, marshal `DescriptionEntry` list to Java |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/java/.../client/OSMScoutClient.java` | **No change needed** — `native ObjectDescription getDescription(double, double)` already declared |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/java/.../client/ObjectDescription.java` | **No change needed** — model class exists |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/java/.../client/DescriptionEntry.java` | **No change needed** — model class exists |
