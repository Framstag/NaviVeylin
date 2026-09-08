## Context

See proposal.md — Why. Current state that shapes the approach:

- `OSMScoutClient` is a process-wide Hilt singleton built once in `MapDownloadModule.provideOSMScoutClient`; the basemap lookup directory is baked into the native `DBThread` at build time and only registered when the basemap dir exists (`MapDownloadModule.kt:67`). Nothing can change it later — hence the restart requirement. `reloadBasemap()` already exists on the client and reopens the basemap on the DBThread worker (`OSMScoutClient.cpp:666`, `DBThread.cpp:589`), but with an empty lookup dir it is a no-op (`DBThread.cpp:559`).
- The phone map (`MapRenderer`) renders from an epoch-scoped geographic tile cache; content changes require the "epoch bump + cache clear + forced full render" invalidation pattern already used by `invalidateStyle()` (`MapRenderer.kt:410`). The Auto variant (`AutoMapRenderer`) has no tile cache — it blits from an overrun buffer, and its `invalidateStyle()` (`AutoMapRenderer.kt:552`) just invalidates blit eligibility and re-renders.
- Rendering pulls the current `basemapDatabase` from `DBThread` per frame under the shared latch (`DBThread.cpp:549`) — Java never holds a stale basemap reference. So a successful native reload is picked up by the very next render, no completion callback needed.
- `DBThread::ReloadBasemap`/`basemapLookupDirectory` are upstream libosmscout (commit 351ec5a03); the submodule is at a locally maintained commit and clean.

## Goals / Non-Goals

**Goals:**
- Basemap downloaded/updated/deleted while the app runs becomes visible without restart, including the fresh-install (no basemap at startup) case.
- Phone and Android Auto behave identically (shared singleton client, per-variant renderer invalidation).
- Keep the JNI surface minimal and the submodule patch small/upstreamable.

**Non-Goals:**
- Re-architecting `OSMScoutClient` construction or lifetime (it stays a build-once singleton).
- Live reload of regional map databases (out of scope; they already open incrementally via `openDatabase`).
- Native completion callbacks for the reload (render pulls current DB per frame — not needed).

## Decisions

### D1: Runtime basemap directory setter on DBThread (submodule patch)

Add `DBThread::SetBasemapLookupDirectory(const std::string&)` in `libosmscout-client/src/osmscoutclient/DBThread.{h,cpp}`: it schedules one async job that takes the existing `WriteLock(latch)`, updates the member, then calls `LoadBasemap()` (close old, open new; empty string = unload). Expose it through one new JNI method `OSMScoutClient.setBasemapLookupDirectory(String)` in `libosmscout-client-java` (`OSMScoutClient.cpp` + `OSMScoutClient.java`).

- **Why this over alternatives:**
  - *Alt A (Option A — unconditional `withBasemapLookupDirectory` at build, no native change):* one-line fix for the download case, but the directory is still frozen at build time — the delete/unload path (empty-string reload) and any later directory change stay broken. Spec requires live unload too. Rejected as sole fix; noted as acceptable stopgap.
  - *Alt B (full client rebuild — `close()` + `builder.build()` again):* the singleton is held by `MapRenderer`, every ViewModel and all four Auto screens; rebuilding loses DPI/style state and risks renderer/client desync. Rejected.
  - *Chosen:* the setter reuses the existing `ReloadBasemap` machinery, serializes with render jobs through the latch, and mirrors the upstream `RemoveLookupDirectory` rescan pattern — minimally invasive, upstreamable.
- Threading: the setter's async job and render jobs (`RunSynchronousJob`) both serialize on `latch` (Design.md §4: native calls never on the main thread; Java side calls on `Dispatchers.IO` via the ViewModel scope). No torn state possible.
- JNI surface stays one method (Design.md §5: keep the JNI surface small and explicit).

### D2: Data invalidation hook on both renderers

- Phone `MapRenderer`: new `invalidateData()` = `epoch.incrementAndGet()` + `tileCache.clear()` + `submitDebounced(..., forceFullRender = true)` — identical mechanics to `invalidateStyle()` (`MapRenderer.kt:410`), which matches Design.md §6 ("mode/style switches invalidate everything"). A database-set change is such a switch: cached tiles were rendered without the basemap.
- Auto `AutoMapRenderer`: new `invalidateData()` = `blitEligible = false` + `requestRender()` — mirrors its `invalidateStyle()` (`AutoMapRenderer.kt:552`); no tile cache exists there.
- **Why a new method over reusing `invalidateStyle()`:** the epochs/comments tie that method to stylesheet variants; a database-change invalidation with the same semantics but honest naming keeps intent clear and tests unambiguous. Behavior is otherwise identical.
- Forced-render path already survives the pan-blit shortcut (`MapRenderer.kt:455-463`), so the re-render executes even though the camera never moved.

### D3: `@Singleton` `BasemapReloadNotifier` (StateFlow<Long> epoch)

New small Hilt singleton (`com.naviveylin.di` or `core` package): `MutableStateFlow<Long>` counter + `bump()`.

- `BasemapViewModel` (`onComplete`, `delete()`) after initiating the native reload: `client.setBasemapLookupDirectory(dirOrEmpty)` + `client.reloadBasemap()` + `notifier.bump()`.
- `MapCanvasViewModel` collects the flow in its scope → `mapRenderer.invalidateData()`.
- The four Auto screens (`MapScreen`, `NavigationScreen`, `DetailsScreen`, `FreeDrivingScreen`) collect it → `mapRenderer.invalidateData()`.
- **Why this over alternatives:**
  - *Alt 1 (direct VM→VM callback):* both ViewModels are screen-scoped with no shared instance; brittle and lifecycle-laced. Rejected.
  - *Alt 2 (native DB-changed callback through JNI):* a new callback path plus native-thread marshaling to the main thread; unnecessary — render already pulls the current basemap DB per frame (Design.md §4: marshal native callbacks; here we need none). Rejected.
  - *Chosen:* a counter (not a Boolean) coalesces rapid successive events (download → delete → download) and mirrors the renderer epoch semantics; collectors always observe the latest. App-scoped, no per-screen state, collectors cancel with their own scopes (no leaks).
- Ordering: `reloadBasemap()` returns after the reopen job is *scheduled* (async); the notifier fires after it returns; the subsequent forced render is enqueued behind the reopen job in the DBThread FIFO and runs after the latch is free — it sees the new basemap DB. The phone renderer's debounce adds further slack.

### D4: BasemapViewModel wiring

- `download().onComplete`: `setBasemapLookupDirectory(dir)` + `reloadBasemap()` + `notifier.bump()` (dir = the completed install dir).
- `delete()`: `setBasemapLookupDirectory("")` + `reloadBasemap()` + `notifier.bump()`.
- Startup registration in `MapDownloadModule` stays unchanged (still the correct path when a basemap exists at app start; harmless duplication at runtime).

## Risks / Trade-offs

- **[Set dir that fails to open] → `LoadBasemap()` logs "Cannot open basemap db" and the view stays without basemap.** Mitigation: only set after `BasemapManager` confirms the download/install succeeded (dir exists and is non-empty); the next successful set recovers.
- **[Stale tile cached if a render sneaks between set and reopen]** → The reopened DB is served under the latch; the forced render is enqueued behind the reopen job (DBThread FIFO) so this is theoretical. Residual worst case: one frame without basemap until the next render. Acceptable; verified on device.
- **[Auto parity — four screens own renderers]** → Each must collect the notifier. Covered by a dedicated wiring + on-device task; a missed screen shows the symptom only on the car display (restart still works there as fallback).
- **[Submodule patch drift]** → Keep the setter minimal, follow the `RemoveLookupDirectory` pattern, and mirror upstream API shape (Design.md §5: "Mirror upstream APIs exactly so submodule syncs stay clean"). No Android dependencies introduced (CI Android-free gate unaffected — pure C++ change in `libosmscout-client`).
- **[Render job holding a closing DB]** → `DBInstance` is held via `shared_ptr` and close happens under the same latch; no use-after-free. Not a new risk class (already true for `ReloadBasemap` on update).

## Migration Plan

Additive. Ship order: (1) submodule commit(s) — DBThread setter + JNI method; (2) app wiring — renderer hooks + notifier + ViewModel changes; (3) spec/guideline updates. Rollback: revert the submodule commit and the app-side wiring; behavior degrades to the current restart-required path (basemap still loads at next start). No data migration; the basemap files themselves are unchanged.

## Open Questions

- None that affect specs or the approach. Auto screen wiring details (which screen holds which renderer lifetime) are resolved during tasks with on-device verification.
