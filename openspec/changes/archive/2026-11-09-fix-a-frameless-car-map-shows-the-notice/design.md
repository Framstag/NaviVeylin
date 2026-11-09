# Design

## Context

See `proposal.md` — Why. The state that shapes the approach:

- The notice's only channel is a frame. `RendererGate.setOverlayDrawer` is invoked from
  `AutoMapRendererRender.kt`'s `drawToSurface`/`blitToSurface`, both after a blit, so a screen with no
  frame has no channel at all (`MapUnavailableOverlay.kt:17-23` records it).
- The bounded wait already exists and already notices the case: `AutoMapRenderer.frameWaitIteration`
  (`AutoMapRendererLoops.kt:136-163`) retries twice and then sets `frameWaitExhausted`
  (`:149`), reported once through the diagnostics sink (bound `FRAME_WAIT_BOUND_MS = 4_000L`,
  `AutoMapRenderer.kt:1004`). Nothing read that flag outside the renderer's own retry logic.
- Both screens already wire renderer callbacks that are reported from the render thread and applied on the
  main thread through `postTemplateRefresh` (`FreeDrivingScreen.kt:386`, `NavigationScreen.kt:404` for the
  new one; `onRendererDegraded`/`onRendererRecoveryRequested` are the same shape).
- `MapWithContentTemplate` with a disabled `map_unavailable` row is shipped behaviour on
  `MapScreen`/`DetailsScreen` (`MapTemplateFactory.buildMenuContent(mapUnavailable = true)`,
  `DetailsScreen.kt:363`), so the row, its wording and its disabled shape are already validated by the host.

## Goals / Non-Goals

**Goals**

- A started full-screen car map with no frame states that the map is unavailable, in a place the host draws
  without a frame.
- The healthy path is unchanged: no extra template build, no extra `invalidate()`, no extra frame request,
  no extra log line on a screen whose frames land.
- The state has one writer (`frameWaitExhausted`) and one reader per screen, with the change-only edge that
  keeps a healthy session silent.

**Non-Goals**

- Replacing the surface-drawn notice on the `DEGRADED` path, or merging the two states.
- A second lock/draw path on the session surface (rejected by `car-frame-wait-bound` D4(b)).
- The pixels measurement of the fallback on the AVD (a device task, recorded unrun in `tasks.md` §5).

## Decisions

### D1 — Where the trigger is decided

- **(a) The screen polls `renderer.frameWaitExhausted` on a ticker.** *Rejected:* a wakeup per tick on the
  host thread for a rare event, and a poll cannot report the moment the state clears.
- **(b) The renderer publishes a flow and the screen collects it.** *Rejected:* a second state source and a
  collector per screen for one boolean; the renderer already hands its per-renderer events to the owner
  through callbacks (`onSurfaceFailed`, `onRendererDegraded`, `onRendererRecoveryRequested`), and the screen
  is the object that owns `invalidate()`.
- **(c) A change-only callback, `onFrameWaitStateChanged`, written by the single writer of the flag.**
  *Chosen:* `publishFrameWaitExhausted` (`AutoMapRenderer.kt:371`) is now the only place
  `frameWaitExhausted` changes, so the on/off edges cannot disagree with the flag, and the renderer keeps
  reporting from its own dispatcher while the screen marshals to the main thread — the discipline
  `guidelines/UICar.md` §3a states for host callbacks.

### D2 — What the screen answers while no frame has landed

- **(a) `MapWithContentTemplate` with one disabled `map_unavailable` row, the screen's own strips, and a
  `MapController`.** *Chosen:* it is the only template with a content slot that keeps the map surface and
  the map controller, so the host keeps delivering the surface a later frame draws on and the revert costs no
  re-entry of the screen. The row is the shape the map/details screens already ship, and the strips are the
  ones the screen already built, so the fallback loses no affordance.
- **(b) A pushed notice screen.** *Rejected:* the car-app library has no overlay layer — a pushed screen
  **is** the displayed template — so it would take the map away and pause the surface's renderer, and the
  driver would have to dismiss it to get back (the rule `guidelines/UICar.md` §3c states for reroutes).
- **(c) A `PaneTemplate`/`ListTemplate` with no map.** *Rejected:* it destroys the surface the healthy frame
  needs, so the fallback could never end by itself.

### D3 — When it reverts

The first draw after the spent budget clears the flag (`AutoMapRendererLoops.kt:142`), and the same callback
publishes `false`. The screen then answers the `NavigationTemplate` again on the next build. A renderer that is
re-created starts with the flag clear, and the screen reads the new instance's value when it is published, so
a stale `true` cannot outlive the renderer it belonged to.

### D4 — Why the healthy path pays nothing

`publishFrameWaitExhausted` returns before invoking anything when the value did not change, so the flag is
written on the two edges only; the screen's `onFrameWaitState` returns on an unchanged value before it logs or
invalidates. A screen whose frames land therefore performs no host call because of this change, and the
`frameWaitIteration` retry accounting is untouched.

## Open decisions

None

The one design fork this item had — fall back to a template with a content slot, or state the gap in the
spec — is settled by the decision recorded on issue #722 (option 1), including the trigger predicate
(frame-wait exhaustion, **not** `RendererState.DEGRADED`), the bound (4 000 ms), the row, and the
revert-on-landing behaviour. Nothing in this change re-opens it.

## Risks

- **A template swap mid-screen re-delivers the surface.** The host may replace the surface when the template
  type changes; the gate already absorbs a re-delivered surface (`onSurfaceAvailable`,
  `MAX_SURFACE_REFRESH_ATTEMPTS`) and the fallback keeps a `MapController` so a map surface exists on both
  sides. Residual risk: a host that drops the surface on the swap costs one frame — which is the state the
  fallback exists for, and the notice stays up until a frame lands.
- **A spurious fallback on a slow head unit** whose first frame legitimately takes more than 4 000 ms plus two
  retries. Consequence: the notice appears in the host slot until the frame lands, then disappears. The
  bound is the one `car-frame-wait-bound` measured (0.63 s push, 0.87 s surface replacement), so this needs a
  unit an order of magnitude slower than the measured one.
- **The fallback's pixels are unverified on a device.** The row is shipped behaviour on two other car screens
  and the template type is asserted by a host case, but the fallback's own layout on the AAOS host is not
  measured; recorded as an open device task, not claimed.
