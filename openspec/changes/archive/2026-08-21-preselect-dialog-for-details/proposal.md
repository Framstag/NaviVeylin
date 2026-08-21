## Why

Long-pressing a map location often hits several overlapping objects (building, shop inside it, street, node). NaviVeylin currently resolves only the single "best" object and opens its details directly, so the user cannot reach the other candidates. JavaScout (upstream libosmscout, commit `ace943087`, PR #1771) already solves this with a preselection dialog that lists all reasonable candidates; NaviVeylin phone and Android Auto mode should match that behavior.

## What Changes

- **Phone**: long-press on the map no longer opens details of the single best object directly. Instead it shows a candidate picker listing all reasonable objects at the press point (ranked by the existing algorithm), and the user selects one to open the details sheet for that object.
- **Android Auto**: the car map gains the same preselection flow — long-press (or the AA-equivalent trigger, see Impact) shows a candidate list; selecting a candidate opens the details screen with "Navigate here".
- **JNI bridge** (`libosmscout-client-java` submodule): add `getDescriptionCandidates(lat, lon, magnification)` returning a ranked `List<ObjectDescription>`, one per candidate, each carrying object identity (`objectRefType`, `objectTypeName`, `objectFileOffset`). The existing single-best `getDescription` stays for other callers (search, POI, favorites).
- **Submodule**: bring in upstream commit `ace943087` (preselect-dialog feature) via submodule bump or cherry-pick onto the `naviveylin-local` branch.

## Capabilities

### New Capabilities
- `long-press-candidate-picker`: long-press on the map shows a picker dialog listing all reasonable candidate objects at the pressed coordinate, formatted like search results and ordered by the existing ranking algorithm; selecting a candidate opens its full details; dismissing the picker shows no details.

### Modified Capabilities
- `long-press-details`: long-press no longer opens the single best object's details directly — it now produces the ranked candidate list that feeds the picker. The gesture-detection and ranking-algorithm requirements stay; the "return the best-matching object's description" behavior changes to "return all ranked candidates".
- `auto-map-interaction`: the car map gains a long-press gesture (or AA-equivalent trigger) that fires the candidate lookup, alongside the existing pan/zoom/rotation gestures.
- `auto-map-destination-picker`: selecting a location on the car map may now route through the candidate picker when multiple objects are present, before showing the details screen with "Navigate here".

## Impact

| File | Change |
|------|--------|
| `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` | **Modify** — `onLongPress` calls `getDescriptionCandidates`; add candidate-list state + `onCandidateSelected`; keep `getDescription` path for search/POI/favorites |
| `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` | **Modify** — wire candidate picker UI; long-press opens picker instead of details sheet |
| `app/src/main/java/com/naviveylin/ui/map/MapCanvasUiState` | **Modify** — add candidate list + picker visibility fields |
| `app/src/main/java/com/naviveylin/ui/map/` (new) | **Add** — candidate picker composable (sheet/dialog) |
| `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` | **Modify** — tap handling queries candidates; multiple objects → candidate picker screen |
| `auto/src/main/java/com/naviveylin/auto/` (new) | **Add** — AA candidate picker screen |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/java/.../client/OSMScoutClient.java` | **Modify** — declare native `getDescriptionCandidates(double, double, int)` |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/java/.../client/ObjectDescription.java` | **Modify** — add `objectRefType`, `objectTypeName`, `objectFileOffset` identity fields |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` | **Modify** — implement `getDescriptionCandidates` (reuse existing candidate collection/ranking, marshal full ranked list with identity) |
| `app/src/main/cpp/libosmscout` (submodule) | **Bump/cherry-pick** — upstream `ace943087` (PR #1771) |
| Tests | **Modify/Add** — `MapCanvasLongPressTest`, new phone candidate-picker tests, AA `MapScreenTest` |

**Resolved design questions:**
- **AA trigger**: `androidx.car.app` `MapController.SurfaceCallback` exposes no long-press callback (only `onClick`/`onScroll`/`onScale`/`onFling`/`onPan`/`onRotate`). AA uses tap → candidate picker when multiple objects exist.
- **Candidate identity**: upstream uses file offset + ref type + type name; the picker carries full `ObjectDescription` per candidate, so no re-fetch is needed — identity fields serve display and future use.

**Open design questions (resolve in design.md):**
- **Submodule strategy**: bump to upstream master (pulls other upstream changes) vs. cherry-pick `ace943087` onto `naviveylin-local` (minimal diff, but conflicts with local ranking improvements in `OSMScoutClient.cpp`).
