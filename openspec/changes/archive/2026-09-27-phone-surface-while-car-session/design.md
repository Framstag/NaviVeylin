# Design

## Context and measurement

Measured 2026-09-27, Pixel 8, car session live, phone UI toggled on/off (everything else unchanged):

```
phone UI on screen    Graphics 132 MB (EGL 51.8 + GL 80.2)   Native 170.9 MB   PSS 540.6 MB
phone UI backgrounded Graphics  28 MB (GL only)              Native 171.2 MB   PSS 444.5 MB
```

Two facts drive the design: the saving is **all graphics** (native heap does not move), and the app's
bitmap cache is **not** released when the UI goes away (62 → 77 MB) — so simply not composing the
canvas captures the graphics part, and only an explicit release captures the bitmap part. All of these
counters are high-water marks inside a process, so verification must use a fresh process per state.

## D1 — What the phone shows while suspended

| | A: a blank screen with a hint | B: a car-session surface with the guidance summary and a map action | C: keep the map, frozen |
|---|---|---|---|
| Recovers the map's GL/frame buffers | yes | yes | no — the buffers stay allocated |
| Phone screen still useful | barely | yes (destination, next turn, ETA) | yes, but stale |
| Risk of feeling broken | high | low | medium (a frozen map lies about the vehicle) |

**Chosen: B.** C is rejected on the measurement (the money is in the frame/animation buffers, which a
frozen map keeps) and on honesty (a frozen map is a stale position). A is rejected because a phone that
shows nothing during a drive reads as a crash. The guidance summary reuses the shared engine state and
the car's labels, so `cross-variant-ui-parity` holds by construction.

## D2 — Who owns the suspension decision

| | A: the screen composable observes the presence flow | B: a flag in `MapCanvasUiState`, fed by the ViewModel | C: `MainActivity` |
|---|---|---|---|
| Single gate for "request no renders" | no (the request sites are in the ViewModel) | yes | no |
| Unit-testable without Compose | no | yes | no |
| Consistency with the codebase | - | matches every other map state gate | - |

**Chosen: B.** One boolean (`phoneMapSuspended`) plus the existing render-request path means "no phone
renders while suspended" is enforced in one place and unit-tested there, not by hoping no timer fires.

## D3 — What is released on suspension

| | A: nothing (just stop composing) | B: the phone's bitmap tile cache + its pooled render targets | C: also trim the shared native cache |
|---|---|---|---|
| Graphics saving | yes (automatic) | yes | yes |
| Bitmap saving | no (measured: it stays) | yes (~60 MB measured as malloced) | - |
| Hurts the car | no | no | **yes** — the car renders from those caches; trimming would force a refetch storm on the car surface |

**Chosen: B.** C is rejected outright: the native tile-data cache is client-wide and shared, and the
car surface is the one that needs it while the phone is suspended. The sibling change
(`bound-tile-data-retention`) owns releasing that cache under pressure.

## D4 — Override scope

Per **session** (chosen) rather than a persisted setting: a persisted "never suspend" would silently
give up the saving forever, and no user has asked for it. The override is a UI state that clears when
the car session ends.

## D5 — How the map resumes

A fresh render at the retained viewport (chosen): the frame buffers were released, so there is nothing
to reuse, and rendering on resume is exactly what the map does when it is first shown. Rejected
alternative: keeping the last frame for a crossfade on resume — it would hold a 14.9 MB overrun bitmap
plus a GPU texture for the entire session, i.e. it would give back part of the saving for a cosmetic
transition.

## Threading and lifecycle (guidelines/Design.md §4)

- The suspension state is main-thread UI state, written from the presence flow's collector (main), like
  every other map state gate. The state drops the emitted frame in the same transition, so the UI never
  composes a bitmap the renderer has given up.
- The release runs on that same collector (main) — **revised during implementation**: the original plan
  put it "on the render dispatcher (`Dispatchers.Default`) after the display loop has stopped", but
  `MapRenderer.releaseRenderStorage()` performs no native render work at all (it bumps the render epoch,
  drops the queued render, drops references and takes two short locks), so a dispatcher hop would buy
  nothing. It reuses the existing ownership rules: `RenderBitmapPool.releaseIdle()` for the pool's free
  targets (the pool refuses foreign or double releases) and the renderer's own clear path for the tile
  cache. The renderer's two lifetime loops are deliberately **not** cancelled — cancelling them leaves a
  renderer that can never render again, resume included; dropping `pendingRender` plus the epoch bump
  makes a queued or in-flight render discard its result instead. No native call happens on a
  host-callback or lifecycle-callback thread.
- No new scope and no new lifetime: the presence flow is process-scoped and in-memory; the phone
  surface's display loop is composition-scoped, so it ends when the canvas stops being composed.
- Session end is the resume trigger; a session that ends while the phone is in the background resumes
  into the background without composing the map (the flag changes, the composition does not happen
  until the UI is visible again).

## Verification

### Device run 2026-09-27 (Pixel 8, Play build `2026-09-27-4`, DHU session, phone unlocked)

Every record below is from the phone's own diagnostics stream (`adb logcat`) on the installed Play build
(the new native symbol was verified inside the installed APK first, not inferred from the version string):

```
20:37:55  phone map suspended: car session active (presence=true, released tiles=0 frameBuffers=2 idleTargets=1;
            shared native tile-data cache untouched)          <- the session edge suspends by itself
20:38:26  MapCanvasVM: frameFlow: frame discarded, phone map suspended   <- an in-flight frame is dropped
20:40:57  phone map override: map requested during a car session (presence=true)
20:44:49  phone map resumed: car session ended (presence=false, override=true)  <- the override is cleared with it
20:47:06  phone map suspended: car session active (presence=true, released tiles=0 frameBuffers=2 idleTargets=3;
            shared native tile-data cache untouched)          <- a LATER session suspends again by default
```

The phone UI was read through `uiautomator` at each step: the surface's statement and its action
("Navigation auf dem Auto-Display" / "Die Karte ist auf diesem Display pausiert, während das Auto die Route
zeigt." / "Karte hier anzeigen") with **no** map control in the hierarchy while suspended, and the map itself
(a street label) with no surface text after the override — the mutually exclusive composition, observed rather
than asserted.

**The saving, measured as a same-process A/B** (both states with the car session live, minutes apart, so no
fresh-process confound):

| phone state | `Graphics` | EGL mtrack | GL mtrack | native heap | `TOTAL PSS` | malloced bitmaps |
|---|---|---|---|---|---|---|
| car-session surface (suspended) | **31.8 MB** | 20.7 MB | 11.1 MB | 220.4 MB | 338.2 MB | 62.0 MB |
| map canvas (override) | 100.2 MB | 51.8 MB | 48.4 MB | 169.4 MB | 337.3 MB | 62.0 MB |

The design predicted the surface would land near the measured "phone UI backgrounded" figure (Graphics 28 MB)
against a canvas-visible baseline of 132 MB: **31.8 MB measured, −68.4 MB against the canvas state in the same
process** (EGL −31 MB, GL −37 MB). The native heap does *not* fall (it is the tile data both surfaces read) — as
the design said it would not.

**The car keeps rendering while the phone is suspended** (task 7.2): across the sessions the car surface logged
`lock OK` counting up, exactly **one** `releasing session surface` per session end, one `detaching surface`,
**0** `surface invalid`/`lockCanvas failed`, **0** `dropping frame`, **0** `re-delivered`, and the app had
**0** FATAL and **0** `UnsatisfiedLinkError`. The shared native cache stayed for it: with the session live the
platform's UI_HIDDEN released **nothing** (TODO §97 / `bound-tile-data-retention` 5.4), and the car surface kept
drawing through a walk that refilled the cache to its full 512 tiles.


Unit:

- `MapCanvasViewModel`: a presence edge suspends and requests no render while suspended (a render
  request is refused, not queued); a frame arriving from a render in flight at the edge is discarded,
  not published; the override resumes rendering; the override clears when the session ends; the release
  happens once per suspension, not per emission; the session end renders at the CURRENT viewport.
- The release seam (`MapRendererReleaseStorageTest`, `RenderBitmapPoolTest`): the tile cache and the
  frame buffers are released, the released frame is never recycled but no longer held by the frame flow,
  the renderer renders again afterwards, a queued render is dropped, an idle pooled target is released
  while a handed-out one is untouched, and the shared native cache configuration is untouched.
- Compose test: the car-session surface shows the session identity and the guidance summary, and its
  action returns to the map canvas.
- German completeness for the new strings (existing gate).

On-device (recipe `guidelines/Build.md` §10; **fresh process per measured state** — every one of these
counters is a high-water mark that never returns inside a process):

- A car session live with the phone UI visible, then the same with the session ended: `dumpsys meminfo`
  `Graphics`, native heap, TOTAL PSS and `malloced Bitmaps`, both from freshly started processes.
- The car surface health in the same run: `lock OK` counting up, no `lockCanvas failed`, no growth in
  `dropping frame`.
- The shared cache stays for the car: with the phone suspended, the car's frames keep rendering without
  a refetch storm (render duration not collapsing into repeated full loads).
- The override: tapping it returns the phone map and raises the phone UI's graphics again.
- Session end: the phone map comes back with its pre-suspension viewport/mode.

## Risks

| Risk | Mitigation |
|---|---|
| The phone looks dead while the car drives | an informative surface with the guidance summary, not a blank screen; the map action is one tap |
| The user expects the phone map every time | the override is recorded in the diagnostics, so a future decision to persist it has evidence |
| A leftover timer keeps requesting renders while suspended | one gate in the render-request path, unit-tested; the phone's display loop is composition-scoped by construction |
| Releasing while a render is in flight | release only after the display loop has stopped; the pool refuses foreign/double releases and reports them, so a race is loud in the diagnostics rather than silent corruption |
| The suspension hides a phone-only path that the car cannot serve (e.g. a phone sheet or a debug action) | the override covers it, and the proposal's scope keeps the car side untouched |
