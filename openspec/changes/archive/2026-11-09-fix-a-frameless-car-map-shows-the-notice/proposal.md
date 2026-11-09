# A full-screen car map that has never drawn a frame states the failure in the host content slot

## Why

The degraded notice of the two full-screen car map screens has exactly one channel, and that channel
needs a frame: `MapUnavailableOverlay.draw` is called from the screen's overlay drawer
(`auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt:437`, notice at `:478`;
`NavigationScreen.kt:304`, notice at `:361`), and the drawer is invoked only from the frame path
(`AutoMapRendererRender.kt`, `drawToSurface`/`blitToSurface`). `MapUnavailableOverlay`'s own KDoc records
the consequence: "if a screen's frame draw faults so badly that no frame lands at all, the notice cannot
be painted either — the surface then keeps its last frame." A started screen that has never drawn one
keeps nothing: the driver sees host chrome and a blank surface with no message (issue #25's situation).

**Root cause, in one sentence:** both full-screen car map screens build the content-slotless
`NavigationTemplate` (`MapTemplateFactory.buildFullScreenTemplate`, `MapTemplateFactory.kt:143`;
`FreeDrivingScreen.kt:582`, `NavigationScreen.kt:601`), so while the renderer's frame-wait budget is spent
with no frame landed there is no host-drawn place for the text — the only channel is a surface that has
nothing to draw on.

The trigger state already exists and is already bounded: `AutoMapRenderer.frameWaitExhausted` turns on when
the frame-wait budget is spent (`AutoMapRendererLoops.kt:149`, `FRAME_WAIT_BOUND_MS = 4_000L`,
`AutoMapRenderer.kt:1004`, change `car-frame-wait-bound`), and it was diagnostic-only because the notice
had no place to go (`openspec/changes/archive/2026-10-30-car-frame-wait-bound/design.md` D4(c), filed as
this item, its task 5.3).

**The red case.** On the base `c0ddb97c` the seam does not exist, so the case cannot even compile there —
the capability is absent by construction, which is the shape this repo records for a new seam
(`openspec/changes/archive/2026-10-30-car-frame-wait-bound/tasks.md` task 1.1). The behavioural red is one
mutation per invariant, and each named case fails for its own reason:

- `FreeDrivingScreenHostTest#aScreenWhoseFrameWaitBudgetIsSpentCarriesTheNoticeInTheHostSlot` — with the
  screen's branch mutated to never take the fallback, XML
  `tests="6" failures="1" errors="0"` (`2026-11-09T22:19:13.257Z`, `evidence/red-on-HEAD-freestanding-host.xml`).
- `AutoMapRendererFrameWaitTest#theSpentBudgetIsToldToTheOwnerAndClearedByALandedFrame` — with the owner
  report mutated out, XML `tests="8" failures="1" errors="0"` (`2026-11-09T22:19:45.346Z`,
  `evidence/red-on-HEAD-renderer.xml`).

Both mutations were restored and the same classes re-ran green
(`evidence/green-on-change-FreeDrivingScreenHostTest.xml`, `evidence/green-on-change-AutoMapRendererFrameWaitTest.xml`).

**Owning requirement and guideline.** `openspec/specs/auto/free-driving/spec.md:16-18` ("Free driving view
fills the surface", which requires the content-slotless template), modified with a failure-path scenario;
`openspec/specs/auto-map-renderer/spec.md:353-361` (the frame-wait requirement) carries the owner report;
`openspec/specs/test-coverage/spec.md:183-188` names the template case.
`openspec/specs/car-host-fault-isolation/spec.md:418-428` is **not** modified: its requirement and scenario
already say the car screen states that the map is unavailable, and this change supplies the place — a second
sentence there would restate the free-driving delta. Guideline: `guidelines/UICar.md` §3c "Car wait notices" (line 182), with §3 "Car-host template
constraints" (line 32) and §3a "Map renderer startup" (line 77) as the platform and threading rules the
fallback has to respect; only §3c carries the new rule. The item routes to `guidelines/UI.md` §3a, which does not exist (that document's
sections are §1, §6, §8d, §9, §10 and section 20 — the list prints with `tools/look.sh '^## ' --path
 guidelines/UI.md`); the car-surface §3a lives in `UICar.md`.

surface: 3 cases added, 0 rewritten, 8 files assert the bare-template rule (4 source + 1 host case + `guidelines/MapCar.md` + 2 shipped specs), 3 capability deltas (auto/free-driving, auto-map-renderer, test-coverage), 1 guideline section (`guidelines/UICar.md` §3c) — counted with `grep -rln 'no content slot' auto/src guidelines openspec/specs` (8 files) and `grep -rln 'NavigationTemplate' auto/src/test` (9 test files).

## What Changes

1. **The renderer tells its owner when the budget is spent, and when a frame lands.**
   `AutoMapRenderer.publishFrameWaitExhausted` (`AutoMapRenderer.kt:371`) is the single writer of
   `frameWaitExhausted` and invokes `onFrameWaitStateChanged` on the change only, so a healthy renderer
   publishes nothing and a landed frame publishes the clear.
2. **The two full-screen map screens answer a template with a content slot while no frame has landed.**
   `FreeDrivingScreen` and `NavigationScreen` post that report to the main thread, keep it in
   `mapFrameless`, and return `MapTemplateFactory.buildMapUnavailableTemplate` (`MapTemplateFactory.kt:169`)
   — `MapWithContentTemplate` with one disabled `map_unavailable` row and the strips the screen already had
   — until the first frame lands, then return the `NavigationTemplate` they answered before. A
   `MapController` is still set on the fallback, so the host keeps delivering the surface a later frame
   will draw on.
3. **The shipped requirements say so.** The `auto/free-driving`, `auto-map-renderer` and `test-coverage` deltas
   (below), `guidelines/UICar.md` §3c carries the rule, and `guidelines/MapCar.md`'s degraded-notice sentence is
   corrected. `car-host-fault-isolation` is untouched: its requirement already obliges the screen to state the
   failure, and this change is the place it states it in.

## What this does not do

- It does not touch `RendererState.DEGRADED`. That is a repeatedly faulting renderer, a different event with
  its own notice drawn on a surface that has frames; this change reads the frame-wait exhaustion only.
- It does not add a second draw or lock path on the session's surface — D4(b) of
  `car-frame-wait-bound` rejected that, and nothing here reverses it.
- It does not bound a healthy screen's traffic: the fallback is entered and left on a change of state only, so
  a screen whose frames land pays no extra template build, no extra `invalidate()` and no extra request.
- It does not measure the fallback on the AAOS AVD. The pixels measurement the decision asks for is recorded
  as an open device task in `tasks.md` — this run has no AVD, and a fabricated number would be worse than a
  named gap.
