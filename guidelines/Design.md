# Design Guidelines — Architecture & Conventions

Common design principles distilled from the OpenSpec changes (`openspec/changes/**`)
and consolidated specs (`openspec/specs/**`).

**Document map** — pick the right doc: `Design.md` = architecture principles
(this file); `UI.md` = UI rules (phone + Android Auto); `MapRendering.md` =
render-pipeline details and pitfalls; `AGENTS.md` = project facts, build,
logging, stylesheet mechanics.

**Maintenance rule** — when a change supersedes a principle here, update this
document in the same change (see §13 for how supersessions are recorded).

**Severity** — **MUST** = hard invariant, breaking it is a bug. Unmarked =
strong preference.

---

## 1. Module layering

- **MUST**: `:app` is the only module that touches the JNI layer and Android
  context-bound storage.
- `:core` = shared, Android-lean logic used by both `:app` and `:auto`
  (resolution, formatting, projection, prediction, mappers). `:auto` depends
  on `:core` + the JNI jar, **never** on `:app`.
- `:auto` consumes app functionality through provider interfaces defined in
  `:core`, implemented in `:app` with Hilt. Keep providers focused; split when
  they grow.
- One shared state contract between variants (`NavigationState`); extend it
  additively (new fields defaulted) so existing code compiles unchanged.
- Single app module for all form factors via resource qualifiers / window
  size classes.

## 2. Stack & UI patterns

- Single-activity architecture: screens are composables, not activities.
  Compose + Material 3, Navigation Compose, ViewModel + StateFlow, Hilt,
  Coroutines. Google Play Services may be used (Fused location, car
  MapController) but MUST NOT be a hard dependency — provider abstraction,
  runtime availability check, and fallback (e.g., LocationManager) keep it
  replaceable by design. No Google Maps SDK (rendering is libosmscout); no
  Google account required; sideloading is a supported distribution path.
- Persistence is JSON-file based (JNI favorites, settings, search history);
  don't add a database layer that duplicates what the native layer already
  persists.
- Bottom sheets are the standard overlay pattern; full-screen sheets for
  management surfaces that keep the map alive underneath. Both compose on top
  of the map screen via state flags — no nav-graph changes.
- Always-visible navigation info lives in overlays on the map canvas, not
  dialogs.
- Dialogs that must cover a bottom sheet live at top-level screen scope
  (separate windows can hide behind the sheet window).

## 3. ViewModel & state

- One ViewModel per concern; each owns its state as an immutable UiState data
  class in a single StateFlow. Cross-ViewModel communication via collected
  flows, never two-way references.
- State co-locates with the data it displays. Don't create a ViewModel for
  state a screen already owns, and don't push purely presentational logic
  (formatting, grouping) into one — it belongs in the composable layer.
- **MUST**: displayed frames are emitted atomically — bitmap, viewport, and
  overlay snapshot travel in ONE emission. Never combine separate sub-flows
  for what is drawn; mixed-frame states jump.
- Prefer a state field over a separate flow for derived signals.
- Every async operation has an explicit error state surfaced to the user;
  errors are user-actionable; the process never crashes on data inconsistency.
- Settings persist as one `@Serializable` AppSettings JSON; new fields need
  defaults and must decode leniently; enum names are stable on disk.
- Where one value drives several layers (e.g. theme + native style flag),
  resolve it once in a single source and fan out — one truth, no divergence.
- Cross-thread shared state uses atomics/volatiles; flags read at job
  execution time, never snapshotted into queued work.
- Platform-object side effects (window refs, keep-screen-on) live in
  `DisposableEffect`, not the ViewModel.

## 4. Threading

- **MUST**: never call native/JNI code on the main thread; run it on
  background dispatchers with timeouts and loading UI.
- Native callbacks arrive on native threads — marshal state updates to the
  main thread via the ViewModel scope.
- Prefer coroutines over raw threads: conflated channels/StateFlow for
  queues, debounce by delay, cancellation via viewModelScope.
- Debounce high-frequency inputs (search keystrokes, GPS-driven renders)
  before they hit expensive paths.
- One source of truth per data signal; consumers never re-derive provider or
  pipeline knowledge.
- Play Services capabilities are optional: access via provider abstraction
  with runtime availability check and fallback (Fused vs LocationManager) —
  never a hard dependency.

## 5. Native boundary

- **MUST**: the native core stays platform-pure — no Android dependencies
  outside the frozen upstream dir, enforced by a CI gate. Platform
  integration is an app-owned bridge (log sink, load order).
- Keep the JNI surface small and explicit: integer handles for native
  objects, batched marshaling, hot paths stay in C++.
- **MUST**: C++ exceptions convert to Java exceptions at the boundary; native
  code never aborts the process (asserts kill it).
- Native method contracts are API; fix behavioral issues in Kotlin, not by
  changing native semantics.
- Mirror upstream APIs exactly so submodule syncs stay clean; keep submodule
  patches minimal and upstreamable.
- Android-specific deviations live as local overrides in a bridge module,
  never patched into the submodule.
- Serialize writes in native shared services; a render mutex serializes
  concurrent renders.
- Logging: native via `osmscout::log` + app-owned bridge; Kotlin via
  `android.util.Log` with per-class TAG. Stylesheets: submodule is the single
  source of truth, synced at build time. Mechanics: `AGENTS.md`.

## 6. Rendering pipeline

- **MUST**: double-buffered rendering — native writes to the back buffer, the
  front buffer is the only display source, swap is atomic under a lock.
- Stale renders are discarded by epoch comparison; epochs increment on
  meaningful changes (zoom/rotation/style/overlay), never on pure position
  updates — a running render must be allowed to finish.
- Render larger than the screen (overrun) and blit sub-regions for small
  pans; full re-render only beyond the margin.
- **MUST**: caches hold only immutable content keyed by render-affecting
  state; overlay or position changes never invalidate them. Overlays are
  layered on top of the display frame, never baked into buffers or tiles.
- Emit frames only on change; every path must deliver a correctly-sized
  frame.
- Mode/style switches invalidate everything (epoch bump + cache clear +
  forced full render); style flags are pushed absolutely, never toggled.
- Rotation changes force a full render so labels render correctly; keep angle
  math normalized and consistent with the native convention.
- Overlay projection always uses the viewport of the frame actually
  displayed, and the same projection math as the renderer.
- Performance budget: hot paths are allocation-free (no per-frame
  bitmap/array churn); measure before optimizing; keep renders under the frame
  budget.
- Pipeline details and pitfalls: `guidelines/MapRendering.md`.

## 7. Follow mode & GPS

- **MUST**: the marker sits on the raw (or navigation-filtered) fix; only the
  camera is smoothed — a smoothed marker drifts off the road.
- One signal, two bearings: heavy smoothing for map rotation (churn-free),
  freshest signal for the marker arrow (lag-free). Signal quality is derived
  in the location layer; render protection (deadbands, rate clamps, throttle)
  stays in the render layer.
- Derive rotation from course history, not raw bearing; reset history on
  sharp turns and teleports.
- Between fixes, extrapolate the display (fix + speed × heading × Δt) and
  ease corrections on fix arrival. **MUST**: prediction is display-only — the
  navigation engine receives real fixes only. Gate the loop when stationary,
  follow off, or the surface is invalid.
- Prefer receiver speed over derived speed; clamp conservatively while
  decelerating; treat zero/unknown speed as stopped.
- Details: `guidelines/MapRendering.md`.

## 8. Android Auto & cross-variant

- The session owns a simple screen stack (push/popToRoot); no routing
  library. Replace, don't stack, when a screen supersedes another (navigation
  replaces the map).
- Respect host constraints — they are invariants: pane rows are
  non-actionable, list actions are icon-only, hosts may not forward surface
  gestures. Use action strips and pane buttons accordingly; interactive
  actions are parked-only where the host supports it. Prefer host-rendered
  panels where positioned/controllable; draw on the surface only what the
  host cannot render, inside the stable area.
- **MUST**: the car-app surface lifecycle is strict — release every surface
  when replaced or destroyed; pause/resume renderers on screen stop/start;
  unlock in `finally`; validate before drawing; serialize across renderers;
  recover gracefully with capped invalidation.
- Cross-variant parity: same labels and visual hierarchy wherever the
  platform allows; deviate only as much as required. Shared logic and data
  have one source; one variant's UI is a thin adapter.
- **MUST**: distribution — phone and AAOS are separate build flavors under one
  applicationId; the automotive flavor declares the automotive hardware
  feature and drops the projection metadata. A single AAB declaring both is
  rejected by Play.
- OSM attribution is visible on the map and the licence is reachable at all
  times (OSMF guidelines), on both flavors.
- UI rules and parity decisions: `guidelines/UI.md`.

## 9. Data, persistence & search

- Maps and other large files live in app-internal storage; downloads use
  temp-suffix files so partials are never picked up; register storage with
  the native layer at startup. Long-running work (downloads) keeps the
  process alive (wake lock / foreground service).
- Persist after completed operations, not debounced; persist on every
  meaningful change, not only lifecycle events (crash safety). Repositories
  wrap JNI calls in background dispatchers and expose reactive state.
- Use Android-compatible HTTP (`HttpURLConnection`); avoid `java.net.http`.
- Expensive scoped lookups are resolved once, cached, and reused within a
  movement threshold; release native handles on close.
- External data access (contacts etc.) is read-on-demand, never persisted,
  and permission-gated.
- **MUST**: search never crashes the process, whatever the data consistency.

## 10. Build & release

- One version-bumping command, gated at configuration time (versions are
  consumed there); state survives clean builds; version exposed via
  BuildConfig. Everything else is deterministic and never touches version
  state.
- CI pins toolchain commits (dependency-cache stability), derives all config
  from env, verifies silently-tolerated steps explicitly, and uses caches
  aggressively.
- Native libs aligned for 16 KB pages; R8 keep rules for JNI bridge classes.
- Mechanics: `AGENTS.md`.

## 11. Testing

- Extract pure, JVM-testable logic from rendering and template code; test
  state transitions rather than timing; use injectable seams for provider
  branches.
- **MUST**: JNI-dependent unit tests use the host stub and the Robolectric
  classloader rule — never mix sandbox configs on tests that load the native
  stub.
- Compose UI tests for gesture/panel logic: extract gesture handlers into
  reusable `Modifier` factories; keep presentation composables
  callback-driven.

## 12. General engineering principles

- **Single source of truth**: shared logic is extracted once into `:core` or a
  named helper — never duplicated per variant, screen, or module.
- **Reuse over reinvention**: reuse proven mechanisms (invalidation paths,
  existing pipelines, projection helpers, controllers) instead of adding
  parallel implementations.
- **One owner per concern**: an abstraction (ViewModel, repository, provider)
  is justified by a real second consumer — not by structure.
- **No dead code**: dead code contradicts specs and invites misuse; remove it
  and fix the spec in the same change. Keep spec ↔ code in sync.
- **Keep contracts stable**: mirror upstream APIs, absorb platform
  differences in adapters, change shared/native behavior only as a last
  resort.
- **Make it testable**: extract pure functions from drawing and UI assembly;
  keep presentation composables callback-driven.
- **Survive restarts**: user choices and state that must persist live in the
  settings/persistence layer — in-memory-only resets are inconsistent.
- **Additive changes**: land changes additively; keep rollback a simple
  revert; prefer defaulted fields over data migrations.
- **Separate screen over mode flag**: when two states have different
  lifecycles, state, or exit semantics, use a separate screen, not a mode
  parameter.

## 13. Superseded decisions

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

---

## Appendix A — Quick checklist (apply phase)

- [ ] JNI/native calls off the main thread, with timeouts + loading UI?
- [ ] Native callbacks marshaled to the main thread?
- [ ] Displayed frame emitted atomically (bitmap + viewport + marker)?
- [ ] Overlays never baked into buffers or tiles?
- [ ] Epoch bumped only on meaningful changes?
- [ ] Mode/style switch invalidates cache + forces full render?
- [ ] Marker on raw fix; only the camera smoothed?
- [ ] AA surface released on stop/destroy; renderer paused/resumed?
- [ ] Same labels in both variants where platform allows?
- [ ] New settings fields defaulted + lenient decode?
- [ ] Shared logic in `:core`, not duplicated?
- [ ] Change additive; rollback = simple revert?
- [ ] Pure logic JVM-testable; tests added?
- [ ] Spec ↔ code in sync (`openspec validate`)?

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
