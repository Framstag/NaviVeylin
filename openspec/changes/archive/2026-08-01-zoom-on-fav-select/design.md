## Context

See `proposal.md` — Why. Current `onFavoriteSelected()` in `MapCanvasViewModel` centers the map on the favorite but preserves existing zoom. The `ObjectDescription` returned by `client.getDescription()` provides object location but no bounding box. `LocationEntry.refType` distinguishes node/way/area for search results but `FavoriteLocation` has no type info — type must be determined at selection time via native query.

## Goals / Non-Goals

**Goals:**
- Determine whether a favorite location is a node or area object
- For areas: compute magnification that fits bounding box in viewport
- For nodes: set fixed magnification (17)
- Override current zoom regardless of prior user zoom
- Keep implementation in Kotlin/Java layer; native changes minimal

**Non-Goals:**
- Not modifying `FavoriteLocation` data model (type determined at selection time)
- Not adding UI controls for zoom behavior
- Not changing search result or long-press zoom behavior
- Not implementing smooth zoom animation (single jump is acceptable)

## Decisions

### Decision 1: New native method `getObjectBoundingBox` on `OSMScoutClient`

**Chosen:** Add `double[] getObjectBoundingBox(double lat, double lon, int magnification)` returning `[minLat, maxLat, minLon, maxLon]` or null for nodes.

**Alternatives considered:**
- **Parse `DescriptionEntry` sections for geometry hints** — rejected. Description entries are display-oriented, not structured for bounding box extraction.
- **Use `LocationEntry.refType` stored on `FavoriteLocation`** — rejected. Requires data model change and doesn't provide actual bounds.
- **Call `MapService::SearchForObjects` from JNI** — this is the approach. The native implementation queries libosmscout's `MapService` to find the object at the coordinate, retrieves its type and geometry, and returns the bounding box.

### Decision 2: Zoom computation in Kotlin (`MapCanvasViewModel`)

**Chosen:** Compute target magnification in Kotlin using viewport dimensions and bounding box.

**Formula:**
```
mag = log2(min(vpWidthPx, vpHeightPx) * 0.8 / max(boxWidthMeters, boxHeightMeters) * baseResolution)
```
Clamped to `[MIN_MAG, MAX_MAG]` (4–18).

**Alternatives considered:**
- **Compute in native code** — rejected. Viewport dimensions and padding are UI-layer concerns; keeping computation in Kotlin avoids threading complexity.

### Decision 3: Fixed zoom 17 for nodes

**Chosen:** Magnification 17 for all node-type favorites.

**Rationale:** At mag 17, individual buildings and street-level details are clearly visible. This matches typical "arrival" zoom for a point destination.

### Decision 4: Integration in `onFavoriteSelected()`

**Chosen:** Modify `onFavoriteSelected()` to call `getObjectBoundingBox` after `getDescription`, then compute and apply zoom before `renderMap()`.

**Flow:**
1. Center on favorite (existing)
2. Call `client.getDescription()` (existing)
3. Call `client.getObjectBoundingBox()` (new)
4. If bounding box returned → compute area zoom
5. If null → use fixed node zoom (17)
6. Update `viewport.magnification` in UI state
7. Render map (existing)

## Risks / Trade-offs

- **Native method adds complexity** — JNI implementation must handle missing objects gracefully. Mitigation: return null for nodes, no crash on failure.
- **Bounding box query is async** — runs on `Dispatchers.Default` like `getDescription`. Mitigation: both calls can be parallelized with `async {}`.
- **Area zoom may be too tight** — large areas (parks, stadiums) at computed zoom may show only part. Mitigation: clamp minimum zoom to 14 so very large areas still show context.
- **Performance** — two native calls per favorite selection. Mitigation: both are fast lookups (index + geometry read), negligible on modern devices.
