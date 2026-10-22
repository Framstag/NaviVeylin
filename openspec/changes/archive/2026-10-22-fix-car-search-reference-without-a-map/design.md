# Design — the car surface's last drawn frame center, published once and read by every car search

## Root cause

`SearchScreen` resolves its distance reference from a `() -> Pair<Double, Double>?` it is handed
(`SearchScreen.kt:63`), and only `MapScreen` can hand it one, because only `MapScreen` holds the renderer that
knows the displayed center (`MapScreen.kt:607`). Every other constructor of the screen — `RootScreen.kt:132`,
`SearchHistoryScreen.kt:142`, and the history tap inside `SearchScreen` itself — leaves the default in place, so
`searchReference()` (`SearchScreen.kt:187-200`) resolves to null and the rows carry no distance at all. The
value the driver needs exists for one moment in the renderer of a screen that the push has just stopped, and
dies with it.

## The single fix

One retain-on-stop slot for that one value, published by the screen that drew the frame and read by every search:

1. `CarMapViewportProvider` (`:core`, bound `@Singleton` by `AutoServiceModule`, reached through
   `AutoEntryPoint`): `publish(lat, lon)` stores a centre, a non-finite coordinate is dropped, nothing ever
   clears it. The shape is the house pattern for a surface-independent value (`DrivingModeProvider`,
   `FreeDrivingStatusProvider`).
2. `publishCarViewportCenter(renderer, provider)` (`:auto`, `CarMapViewport.kt`) — the one place the publish rule
   lives: it reads `AutoMapRenderer.committedViewportCenter()` (new on the renderer), the center of the frame that
   is actually on the surface, so a renderer that has committed no frame publishes nothing and the previously
   published center stays. `markerViewport()` is deliberately not used: it falls back to the pending render target
   while no frame exists, which would publish a center no frame ever displayed and contradict the requirement's
   own definition of the value.
3. The four car map screens call it as the first act of `onStop`, before `rendererGate.pause()` and
   `detachSurface()` — i.e. while the last frame is still the displayed one. Every screen that can be under a
   search (`MapScreen`, `NavigationScreen`, `FreeDrivingScreen`, `DetailsScreen`) thereby publishes the frame the
   driver last saw.
4. `SearchScreen.searchReference()` takes its own centre first and the published one second, and the history tap
   passes its own `viewportCenter` on to the prefilled screen it pushes (it dropped it before, so a search opened
   from the map lost the reference on the second hop).

Read at search time, not at construction: the screen that opened the search stops *after* the pushed screen is
built, so a value captured in the constructor would miss it.

## Why this and not the alternatives

- **A per-frame publish from `AutoMapRenderer`** — the issue's own phrasing, "whenever the displayed center
  changes". Eliminated by cost and by where the value is read: the search screens read it once per search, and
  every car search is pushed onto a map screen, whose `onStop` is the moment the last committed frame is still
  the displayed one (`DetailsScreenTest.aStoppedMapPublishesTheLastDrawnFrameCenter` pins that the stop publishes
  it). A per-frame publish would add work on the render thread for every frame to keep a value that is read at
  those stops only.
- **Threading the center through the pushed screens' constructors** (`RootScreen(viewportCenter = …)` →
  `SearchHistoryScreen(…)` → `SearchScreen(…)`). Eliminated by the stack the car actually builds: `RootScreen` is
  also pushed by `NavigationSession.showRootScreen()` (`NavigationSession.kt:794`) with no map screen behind it,
  and a parameter would then have to mean "the center of a map this screen has never seen" at one call site and
  "nothing" at another. The provider says the same thing once.
- **Reading the newest saved viewport file** (`AutoInitialViewport.latestSavedAutoViewport`, which
  `resolveInitialAutoViewport` already uses). Eliminated: those are the *phone* app's `maps/viewport-*.json`
  files; the car renderer never writes one, so the value would be the last phone pan, not the car frame the
  driver is looking at.

## Blast radius

`:core` (2 files: the new provider, the entry point), `:app` (1 file: the singleton binding), `:auto` (1 new file
plus 7 edits: four `onStop` publishes, the renderer accessor, `SearchScreen`), 1 guideline bullet, and 10 cases
(3 in `SearchScreenTest`, the 5 of the new `CarMapViewportTest`, 1 in `DetailsScreenTest`, 1 in
`AutoMapRendererTest`), 1 of them rewritten (`historyTapPushesScreenWithQuery`), 1 fixture realigned
(`DetailsScreenTest`'s entry-point stub — that screen now resolves the
provider on stop). No native code, no resource, no manifest, no dependency, no build file, no CI workflow. The
phone surfaces are untouched: they resolve their centre from the map canvas (`MapCanvasScreen.kt:1701`), never
from this provider.

**Review round 1 (2026-10-22)** added the renderer accessor and the two cases that assert the wiring the first
round only described: a stopped car screen publishes the frame it displayed (`DetailsScreenTest`), the history
tap passes its live center on (`SearchScreenTest`), and a renderer that has committed no frame publishes
nothing (`CarMapViewportTest`, `AutoMapRendererTest`). Each was falsified by mutating the line it asserts
(`tasks.md` §5).

**Rollback:** delete the provider, the entry point method, the binding, `CarMapViewport.kt`, the four publish
calls and the `?: carMapViewport.center.value` fallback — every screen returns to reading its own renderer, and
the car search behaves exactly as before.
