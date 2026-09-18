## Why

`MapScreen` is the Android Auto session's initial screen: `NavigationSession.onCreateScreen` constructs it (main / host-callback thread) while the background warmup is still building the native client. Its `init` block forces the `mapRenderer` lazy, whose chain touches the Hilt `OSMScoutClient` singleton (running the native `build()` on main if warmup has not beaten it) and then loops every installed map database calling native `getDatabaseBoundingBox(...)` — all on the main thread. Result: the constructor blocks, template delivery is delayed, and every screen coroutine (settings load, observers) starts late. On cold start with a slow native build, other screens in the same session (e.g. favorites) are perceived as stuck on "Loading" because the host thread that drives template delivery is busy. The session's own doc promises "the root screen ... does not need the native client", but the root IS `MapScreen`, which contradicts that.

## What Changes

- `MapScreen` construction becomes main-thread-safe: the init block no longer forces the renderer (and no longer touches `autoClientProvider()`/`client()` or resolves the initial viewport synchronously).
- The initial-viewport resolution (client pre-touch + saved-phone-viewport / first-map-bbox `getDatabaseBoundingBox` loop) moves onto `Dispatchers.Default` in a screen-scoped coroutine; the `AutoMapRenderer` is created from those values once the viewport is known and published through a ready-handle.
- A small "renderer not ready yet" path covers the window between screen start and renderer creation: pending surface delivery (dims + DPI), dark presentation, follow/pan state, and settings-driven re-center/north-up are buffered and replayed into the renderer when it becomes available — nothing is dropped, no null renderer crashes.
- `onGetTemplate`/`buildTemplate` stays renderer-independent and delivers immediately (already the case — only the constructor blocked); the startup path no longer waits for the native client.
- The same latent main-thread forcing pattern exists in `NavigationScreen` (init sets `mapRenderer.onSurfaceFailed`) and `DetailsScreen` (its own `mapRenderer` lazy + it constructs `MapScreen` for "Show" actions); this change fixes `MapScreen` — which also covers every `DetailsScreen`-created map — and applies the same renderer-readiness handling to `NavigationScreen` and `FreeDrivingScreen`, whose init blocks force the renderer the same way (the initial "FreeDriving safe" note proved wrong during implementation — task 3.2). `DetailsScreen`'s own renderer stays out of scope.
- No native/JNI changes; the submodule is untouched.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-map-renderer`: add a requirement that renderer initialization SHALL NOT run on the car-app main thread and that the map screen SHALL remain responsive (template delivered) while the renderer initializes; pending surface/lifecycle state SHALL be replayed to the renderer on readiness so no map interaction or marker state is lost during startup.

## Impact

- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — async renderer init; init block freed of native work; buffered lifecycle/settings/surface replay; `computeInitialViewport` extracted to a testable suspend/`Default`-dispatcher function.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — same renderer-not-ready handling for its init-block forcing.
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — same handling (init forcing confirmed during implementation; task 3.2).
- `auto/src/main/java/com/naviveylin/auto/MapPanHandler.kt` — takes a renderer supplier, so constructing the pan handler no longer forces the renderer (FreeDriving + NavigationScreen).
- `auto/src/main/java/com/naviveylin/auto/DetailsScreen.kt` — unchanged behavior (its `MapScreen` constructions now benefit automatically); no API change needed.
- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — no behavioral change expected (constructor is field-only; render loop already waits for a surface); possible small addition if a replay entry point is needed.
- `auto/src/test/java/com/naviveylin/auto/MapScreenTest.kt` — placeholders stay; new unit tests for the extracted viewport-resolution function and the ready-buffer replay logic (pure Kotlin, no `CarContext`).
- `auto/src/test/java/com/naviveylin/auto/` — new test class for the renderer-readiness helper if extracted.
- Guidelines: `guidelines/UI.md` (Android Auto section) — note the "renderer ready" startup rule; `guidelines/Design.md` if it gains a general rule.
- Rollback: pure app-side change in `:auto`; revert removes the deferral. Additive, not breaking.
- Scope: Android Auto / AAOS only (this is `MapScreen` in the `:auto` module); the phone map (`MapCanvasViewModel`) already initializes its renderer off the composition thread and is out of scope.
