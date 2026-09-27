# Proposal

## Why

Starting free driving on the car surface snaps the map magnification to the speed-appropriate level in **one frame**. Measured on the AAOS emulator: `mag 13.000 -> 17.000` — 4 levels, a **16x area change with no transition**, while the phone animates exactly this kind of change.

```
free-driving entry (browse -> drive), AA surface
  fix #1  displayed mag 13.000          <- viewport the browse map was showing
  fix #1  AutoZoomController.onSpeed()  -> 17.000, committed in one step
  fix #1  native render at 17.000       -> 16x area jump between two consecutive frames

later fixes: at most 0.5 levels per update (spec auto-speed-zoom — Smooth zoom transitions),
             so every change AFTER the entry is already smooth. Only the entry jumps.
```

The jump is not a tuning accident, it is specified: `openspec/specs/auto-speed-zoom/spec.md` ("Speed unknown") ends with *"the magnification jumps directly to the target instead of smoothing from the default map zoom"*. That scenario is about the case where the engine has not reported a speed yet (default 20 km/h) and nothing meaningful is displayed. The implementation applies it to **every first commit** (`AutoZoomController.kt:35-36,76-80`, `committedTarget = Double.NaN` → `target`), including the one where a live speed and a meaningful displayed magnification already exist.

Why now: the follow/zoom seam has just been made frame-consistent and its remaining per-fix artefacts verified on device (`overlay-projects-against-displayed-frame` archived, `aa-follow-framing-and-zoom-parity` 27/27). The entry jump was recorded as the last open item of that work (`TODO.md` §29), and the display-frame easing it needs (P3) is already implemented and device-verified — the entry transition only has to *start from the right magnification* and commit its intermediate targets on the display tick.

## What Changes

- **The rendered frame walks to the requested magnification.** A zoom commit farther than the blit window from the magnification on screen SHALL NOT be applied in one frame: the renderer walks the committed magnification toward the request in blit-serviceable steps, one step per landed render, and the last step lands exactly on the requested value. The walk starts from what is displayed by construction, which is the "start from the displayed magnification" rule the spec states.
- **The speed still picks the target, never the start.** The controller's existing first-commit behaviour is unchanged (its committed value is the speed-derived target); what changes is that the frame no longer *lands* a large difference in one frame. A commit whose difference is already inside the blit window keeps the current single-eased-blit path.
- **The transition has its own tick.** It SHALL run whenever a transition is pending and the surface is usable — not on the follow extrapolation tick, which is gated on vehicle movement and would stall a transition started while parked. Each step requests the next native render, so the render at the target is queued while the transition plays — parity with the phone's `smooth-zoom` rule (*"the debounced native render SHALL be queued while the animation plays"*).
- **The entry transition ends on an exact native render** at the speed-appropriate target; the vehicle stays anchored (the easing is about the follow anchor, as in the existing magnification transition).
- Unchanged: the speed→magnification table and its interpolation, the 0.5/update convergence cap and epsilon deadband for speed changes, manual-zoom suspension and its re-engage rule, the heading commit deadband, and every phone behaviour.

Not **BREAKING**: additive behaviour on the car surfaces; no API, settings, persistence or spec-removal changes. The phone already animates these transitions (`smooth-zoom`) and is not touched — the change restores the parity that the car surface currently lacks.

## Capabilities

### New Capabilities

None — this modifies the existing auto-zoom contract.

### Modified Capabilities

- `auto-speed-zoom`: (a) the "Auto-zoom uses the navigation engine's reported speed" requirement — speed determines the target magnification only, never the magnification a transition starts from, and the direct jump survives only for the case where nothing is displayed yet; (b) a new requirement for the auto-zoom entry transition (walk from the displayed magnification across rendered frames, queue the target render, end on an exact render); (c) a new requirement bounding the transition's time and render requests.

## Impact

**Code / modules (Android Auto only, `:auto` module + the two car screens):**

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — a zoom transition: a walk target field, a blit-serviceable step per landed render (`ZOOM_BLIT_LIMIT`), its own tick loop next to the existing render/extrapolation loops, and the display-side easing the step relies on (`displayMag`, `ZOOM_EASE_TAU_SEC`). Design decides whether the overrun factor is raised so the transition needs fewer renders.
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` (fix path ~:430-469) and `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` (fix path ~:480) — mark their auto-zoom commit as transition-eligible, and apply the auto-zoom target immediately on the re-enable path using the screen's last known speed instead of waiting for the next fix.
- `auto/src/main/java/com/naviveylin/auto/AutoZoomController.kt` (added 2026-09-19, section 7) — seed the shared controller with the spec's default speed (20 km/h) so an unknown speed yields a target instead of "no target" (parity with the phone's `MapCanvasViewModel.lastValidSpeedKmH` seed); the two identical `autoZoomTarget` gates collapse into the one shared rule in `FreeDrivingScreen`. **Deliberately NOT seeded:** the follow/extrapolation feed (`AutoFixDerivation`) keeps reporting "unknown", so a parked car cannot glide (task 7.2).
- `auto/src/main/java/com/naviveylin/auto/RendererGate.kt` — pass the transition-eligible flag through to the renderer.
- Tests: `AutoZoomControllerTest`, `FreeDrivingScreenTest`, `NavigationScreenTest`, `AutoMapRendererTest`, `MapPanHandlerTest`.
- Android components: no manifest, resource, template or Car App Library change; no Gradle configuration change.

**Native / JNI:** none — pure Kotlin, `:auto` only. No submodule patch and no bridge-module override.

**Specs changed:** `openspec/specs/auto-speed-zoom/spec.md` (requirement "Auto-zoom uses the navigation engine's reported speed" modified; one requirement added).

**Guidelines:** `guidelines/MapRendering.md` §1.1 (AA follow — the magnification-transition bullet gains the entry rule); `guidelines/UI.md` phone↔AA parity note (the car surface joins the phone's animated zoom transitions). `guidelines/Design.md` is unaffected (no new component, no threading-model change).

**Sequencing dependency:** `openspec/changes/aa-follow-framing-and-zoom-parity` (27/27, not yet archived) carries an unsynced delta for the **same capability**, on the "Smooth zoom transitions" requirement. This change deliberately does **not** edit that requirement's text, and its delta must be applied after that change is synced/archived.

**Rollback:** revert the `:auto` edits; the entry falls back to the current single-frame jump, and the narrowed spec scenario reverts with the spec delta (the change is revertable per step — seed, transition, guidelines).

## Open Questions

1. **Auto-zoom re-enabled while driving** — the spec says the magnification *"immediately adjusts to the speed-appropriate level"*; under this change the adjustment *begins* immediately (from the last known speed) and reaches the target across the transition. **Resolved 2026-09-19 (section 7):** the car screens no longer reject an unknown speed — `AutoZoomController` carries the spec's 20 km/h default seed (`AutoZoomController.DEFAULT_SPEED_KMH`, parity with the phone's `MapCanvasViewModel.lastValidSpeedKmH`), so "the speed-appropriate level" exists from the first position estimate on and the re-enable can begin without a fix at all (tasks 7.1-7.3).
2. **Transition length vs render budget** — a 4-level entry needs ≥16 blit-serviceable steps; committing them on the display tick costs ~16 queued native renders (each a ~1296x720 overrun render on a loop measured at ~11 Hz), while the alternative (raise the overrun factor) halves the render count at a larger per-render buffer. Pick the budget in `design.md` before implementation.
3. **Navigation-start entry** — the navigation view starts at the routing-sensible default (15.0), so its entry delta is small (usually ≤1-2 levels). Apply the same transition there (uniform rule, and the transition becomes invisible), or leave the navigation entry as-is?
