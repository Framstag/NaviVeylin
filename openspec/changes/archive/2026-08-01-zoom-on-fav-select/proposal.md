## Why

Selecting a favorite currently centers the map but keeps the existing zoom level. If user is zoomed far out, the favorite is barely visible. If zoomed too close, context is lost. Need automatic zoom that adapts to the object type: show entire area objects (buildings, parks) and use a fixed zoom for point/nodes.

## What Changes

- **`onFavoriteSelected` in `MapCanvasViewModel`**: after centering on the favorite, compute and apply a new magnification based on object type
- **New native method `getObjectBoundingBox` in `OSMScoutClient`**: returns bounding box (minLat, maxLat, minLon, maxLon) for the OSM object at a coordinate, or null for nodes
- **New zoom logic in `MapCanvasViewModel`**:
  - If object is an **area** (way/area with bounding box): compute magnification that fits the bounding box within the viewport, with padding
  - If object is a **node** (point, no bounding box): set fixed magnification (e.g., 17)
- **`FavoriteLocation`** may gain an optional `refType` field for future use, but initial implementation determines type from `ObjectDescription` / bounding box query

## Capabilities

### New Capabilities
- `fav-auto-zoom`: Automatic zoom-to-object when a favorite is selected, adapting zoom level based on whether the object is an area or a node

### Modified Capabilities
<!-- No existing specs change — this is a new capability -->

## Impact

- **`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`**: modify `onFavoriteSelected()` to compute and apply zoom after centering
- **`app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java`**: add `getObjectBoundingBox()` native method declaration
- **`app/src/main/cpp/libosmscout/libosmscout-client-java/src/`**: JNI implementation of `getObjectBoundingBox` calling into libosmscout's `MapService::SearchForObjects` or similar to retrieve object geometry
- **No new dependencies** — uses existing libosmscout geometry query APIs
