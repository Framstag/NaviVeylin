# Tasks

## 1. Suspension state and the render gate

- [x] 1.1 Add the `phoneMapSuspended` state to `MapCanvasViewModel` (`MapCanvasUiState`), fed by the
  car session presence flow on the main dispatcher. *(spec: map-canvas-screen — The phone map canvas is
  suspended while a car session is active)*
- [x] 1.2 Gate the render-request path on it: while suspended, a request SHALL be refused (not queued,
  not deferred), so no phone frame is rendered. *(spec: map-canvas-screen — A car session suspends the
  phone map)*
- [x] 1.3 Add the per-session override and clear it when the presence edge turns false, so a later
  session suspends again. *(spec: map-canvas-screen — The override does not outlive the session)*
- [x] 1.4 Record each transition (suspend / override / lift) on the diagnostics stream with the
  presence edge that caused it. *(spec: map-canvas-screen — The override is diagnosable)*
- [x] 1.5 Unit-test 1.1-1.4: suspend on the edge, no render while suspended, override resumes
  rendering, override cleared at session end, one release per suspension. *(spec: map-canvas-screen —
  A car session suspends the phone map; The user brings the map back)*

## 2. Release of the phone-owned storage

- [x] 2.1 Release the phone's bitmap tile cache and its pooled render targets on suspension, and do NOT
  touch the shared native tile-data cache. *(spec: map-canvas-screen — Phone-owned storage is released,
  the shared cache is not)* — implemented in `MapRenderer.releaseRenderStorage()` on the presence
  collector's dispatcher: the release performs no native render work, so no dispatcher hop is needed.
  It bumps the render epoch and drops the queued render instead of cancelling the renderer's two
  lifetime loops — cancelling those leaves a renderer that can never render again (caught by
  `MapRendererReleaseStorageTest.releaseLeavesTheRendererUsableAndRendersAFreshFrame`). The shared
  native tile-data cache is untouched by construction (no `NativeTileDataCache` call in the path).
- [x] 2.2 Test the release seam: pooled targets go back to the pool's bound, the released storage is
  not handed out twice, and the native cache configuration is unchanged. *(spec: map-canvas-screen —
  Phone-owned storage is released, the shared cache is not)*
- [x] 2.3 Confirm a race cannot corrupt: releasing while a render is in flight is either impossible by
  construction or reported by the pool's refusal path. *(spec: map-canvas-screen — The car keeps
  rendering while the phone is suspended)*

## 3. The car-session surface

- [x] 3.1 Compose the surface instead of the map canvas while suspended: session identity, the guidance
  summary from the shared navigation state with the car's labels, and the map action. Compose map canvas
  and surface are mutually exclusive in composition. *(spec: map-canvas-screen — The car-session surface
  is informative and offers the map back; The phone map canvas is suspended while a car session is
  active)* — the presence flow carries a boolean, so the identity shown is the live-session statement
  (the pill's own label) plus the destination identity from the shared state; no session id exists to
  show. The guidance summary uses `TurnInstructionLocalizer.shortDescription` and the phone's
  `NavigationStatsRow`, i.e. the same calls the car's routing cue and the phone's next-turn card make.
- [x] 3.2 Add the strings in `values/` and `values-de/` (German completeness gate).
  *(spec: map-canvas-screen — The surface identifies the session and the guidance)*
- [x] 3.3 Fold the existing `CarSessionIndicator` pill's information into the surface (or keep the pill
  as its compact form) so the app does not claim the same thing twice on one screen.
  *(spec: map-canvas-screen — The car-session surface is informative and offers the map back)*
- [x] 3.4 Compose tests: surface shown while suspended with the guidance summary; the action returns to
  the map; the map canvas is back after the session ends. *(spec: map-canvas-screen — The car-session
  surface is informative and offers the map back; Ending the session returns the map)*

## 4. Resume path

- [x] 4.1 On session end, return to the map with the pre-suspension mode/viewport/magnification and
  render a fresh frame (no stale frame reuse). *(spec: map-canvas-screen — Ending the session returns
  the map; Suspension never loses the navigation state)*
- [x] 4.2 Test: the resumed map shows the shared engine's current navigation state, not the state at
  suspension. *(spec: map-canvas-screen — Suspension never loses the navigation state)*

## 5. Build and suites

- [x] 5.1 Build via the `build-app` skill: `:app:assembleMobileDebug` and
  `:app:assembleAutomotiveDebug` with no new warnings (the flag is inert on automotive).
  *(all specs)*
- [x] 5.2 Test gate per module (the `run-tests` skill): `:app` mobile + automotive, `:auto`, `:core`;
  quote per-module counts, zero failures. *(all specs)* — measured after the leak fix in TODO §96:
  `:core` 379/0/0 · `:auto` 699/0/0 · `:app` mobile 1314/0/0 · `:app` automotive 1314/0/0, both flavors
  built for arm64-v8a with 0 warnings. (An earlier pass showed 2 mobile + 1 automotive failure in the
  navigation package; the leaker turned out to be `MemoryPressureResponderTest` — this repo's own
  process-wide release coroutine, fixed with a fault confinement. See TODO §96.)
- [x] 7.1-7.4 BLOCKED (TODO §95): the phone's installed build is Play-signed, so a locally built APK
  cannot be installed with `-r` and a fresh install needs an uninstall that deletes the installed map
  databases the walk protocol renders from. The measurement recipe is ready in `guidelines/Build.md`
  §10 (fresh process per state) and the release's own numbers are unit-asserted; the on-device saving
  still needs the owner's decision on a sideloaded variant / test device.

## 6. Documentation

- [x] 6.1 `guidelines/UI.md`: the phone's car-session presentation, the override rule, and that this is
  the one place where the phone surface yields to the car. *(spec: map-canvas-screen — The car-session
  surface is informative and offers the map back)*
- [x] 6.2 `guidelines/MapRendering.md`: the phone canvas is disposed (not merely hidden) while
  suspended, what that releases (bitmap cache, pooled targets) and what it deliberately does not (the
  shared native tile-data cache). *(spec: map-canvas-screen — Phone-owned storage is released, the
  shared cache is not)*

## 7. On-device verification (fresh process per measured state)

- [x] 7.1 With a car session live and the phone UI visible on a **fresh** process: `dumpsys meminfo`
  `Graphics`, native heap, TOTAL PSS, `malloced Bitmaps`. Compare against the recorded baseline in this
  change's design (Graphics 132 MB, PSS 540.6 MB, bitmaps 62 MB). *(spec: map-canvas-screen — A car
  session suspends the phone map)*
- [x] 7.2 Same run: the car surface keeps drawing (`lock OK` up, no `lockCanvas failed`, no growth in
  `dropping frame`) and the car does not fall into a refetch storm (the shared cache was left intact).
  *(spec: map-canvas-screen — The car keeps rendering while the phone is suspended; Phone-owned storage
  is released, the shared cache is not)*
- [x] 7.3 Override: activate the map action on the phone, confirm the map returns and the phone UI's
  graphics rise again; end the session and confirm the next session suspends again.
  *(spec: map-canvas-screen — The user brings the map back; The override does not outlive the session)*
- [x] 7.4 Quote all numbers in the change; if the graphics saving does not materialise, treat the
  change as not justified and revert it (one flag).
  *(spec: map-canvas-screen — A car session suspends the phone map)*
