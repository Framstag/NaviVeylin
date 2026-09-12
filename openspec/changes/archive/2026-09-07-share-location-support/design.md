## Context

See proposal.md — Why. The phone-side deep-link/share pipeline is a dead end: manifest filters and `DeepLinkActivity` exist, `MainActivity` drops the forwarded intent. The car-side flow (`NavigationSession.handleDeepLink` → `DeepLinkParser`) works and must not regress. The phone already has the full candidate pipeline (`onLongPress` → `getDescriptionCandidates` → `CandidatePickerSheet` → `LocationDetailsSheet` with Show/Fav/Route) that the share flow reuses.

## Goals / Non-Goals

**Goals:**
- Single parser shared by phone and car surfaces, extended with the new formats (OSM, Apple/Waze, DMS, hemisphere, short links)
- Phone-side share lands in the existing candidate/details flow with minimal new UI
- Car-side deep-link behavior unchanged

**Non-Goals:**
- AAOS / Android Auto share handling (phone-only; MainActivity trampolines to CarAppActivity on automotive)
- Outgoing share (sharing *from* NaviVeylin)
- GPX/KML file import (`EXTRA_STREAM`)

## Decisions

### D1: Move `DeepLinkParser` to `:core`
`DeepLinkParser` + `DeepLinkDestination` move from `:auto` (`com.naviveylin.auto`) to `:core` (`com.naviveylin.core`), extended with the new formats. `:auto` (NavigationSession) and `:app` both consume it from `:core`.

- **Why**: Design.md — "shared logic is extracted once into `:core`". The parser is now shared by both variants; `:app` importing a parser from the car module is backwards layering. `:core` already hosts shared search providers (`AutoSearchProvider`) used by both.
- **Alternatives**: keep in `:auto` and import from `:app` (works, but app→car dependency for a parser); duplicate in `:app` (violates single-source-of-truth).
- **Risk**: touches working car-side code. Mitigation: `DeepLinkParserTest` moves with it, `NavigationSession` imports updated, `:auto` tests re-run.

### D2: Phone-side `SharedLocationParser` wrapper in `:app`
New `share/SharedLocationParser.kt` maps an `Intent` to a `SharedLocationRequest` (coordinate + label, or query text). It handles the phone-specific bits and delegates format parsing to `DeepLinkParser`:
- label from `EXTRA_SUBJECT` (fallback: coordinate pair)
- `maps.app.goo.gl` short-link resolution (network) before parsing
- dispatch: coordinates → candidate flow; query → search flow

- **Why**: keeps `MainActivity` thin and the logic unit-testable; `DeepLinkParser` stays a pure format parser.
- **Alternatives**: parse inline in `MainActivity` (untestable); put resolution inside `DeepLinkParser` (mixes network into a pure parser, breaks the car flow's synchronous use).

### D3: `SharedLocationHandler` — `@Singleton` consume-once `StateFlow`
New `share/SharedLocationHandler.kt`: `@Singleton` with `MutableStateFlow<SharedLocationRequest?>`. `MainActivity` writes on `onCreate`/`onNewIntent`; `MapCanvasViewModel` collects in `init` and clears (consume-once) after processing.

- **Why**: reachable from both `MainActivity` and the nav-scoped `MapCanvasViewModel`; survives activity recreation; naturally queues when the map screen is not yet composed (no maps installed → `MainScreen` first).
- **Alternatives**: nav args (breaks when the map screen isn't the current destination); activity-scoped `SavedStateHandle` (clunky from Compose); activity-scoped ViewModel (MapCanvasViewModel is nav-scoped, cannot reach it).
- **Risk**: a share written but never consumed lingers until the next map-screen init. Acceptable — in-memory only, cleared on process death.

### D4: Fixed candidate zoom 16
`getDescriptionCandidates(lat, lon, 16)` — constant `SHARE_CANDIDATE_ZOOM = 16`, independent of the current viewport.

- **Why**: the shared coordinate is the anchor, not the viewport; 16 is street level with good candidate density (magnification range 4–20, default 15).
- **Alternatives**: current viewport magnification (wrong anchor — map may be at any zoom); 17 (building level, too dense).

### D5: Extract candidate flow from `onLongPress`
Extract the body of `onLongPress` (entry → `getDescriptionCandidates` → picker-or-raw) into a shared method `showCandidatesFor(lat, lon, zoom, label)`. Long-press calls it with the current viewport zoom; share calls it with 16 and `updateCenter` first.

- **Why**: one code path for both entry points; the no-candidates fallback (details on raw coordinate) is shared.
- **Alternative**: duplicate the flow in the share path (drift risk).

### D6: Short-link resolution with degrade
`maps.app.goo.gl` links resolved via `HttpURLConnection` redirect-follow on `Dispatchers.IO` with a timeout (~5 s), before parsing. Resolution failure → raw link text becomes the query (falls through to search).

- **Why**: spec requires resolve-then-parse; degrade keeps the flow usable offline.
- **Alternative**: skip resolution entirely (spec requires it).

### D7: `launchMode="singleTask"` + `onNewIntent`
`MainActivity` gains `launchMode="singleTask"`; both `onCreate` (cold start) and `onNewIntent` (reuse) parse and write to the handler. `DeepLinkActivity` already forwards with `FLAG_ACTIVITY_NEW_TASK`.

- **Why**: a second share while running must reach the existing activity, not spawn a new instance.
- **Risk**: `singleTask` changes task/back-stack semantics. Low — MainActivity is the launcher root.

### D8: Threading model
- Intent parse: main thread (cheap string work)
- Short-link resolution: `Dispatchers.IO`
- `getDescriptionCandidates`: `defaultDispatcher` (existing `onLongPress` pattern)
- Handler write/collect: main-thread `StateFlow`

## Risks / Trade-offs

- [Parser move touches working car flow] → tests move with it; `:auto` suite re-run; `NavigationSession` import update is the only car-side change
- [Short-link resolution adds network latency] → 5 s timeout, degrade to raw-text query on failure
- [DMS/hemisphere regex edge cases (negative coords, letter variants)] → dedicated parser tests per format
- [Stale pending share if user never reaches the map screen] → in-memory only, cleared on process death; acceptable

## Migration Plan

Additive. Rollback: revert `MainActivity`/`SharedLocationHandler`/`MapCanvasViewModel` changes and the parser move+extension; car-side flow and current phone behavior return. No manifest filter changes, no data migration.

## Open Questions

None — all decisions resolved; deferrable tuning (exact zoom value, resolution timeout) is constant-level and covered by tasks.
