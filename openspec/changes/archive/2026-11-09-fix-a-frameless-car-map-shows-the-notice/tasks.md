# Tasks

Reference: `proposal.md` for scope and the red runs, `design.md` for why,
`specs/auto/free-driving/spec.md`, `specs/auto-map-renderer/spec.md` and `specs/test-coverage/spec.md` for the
contract.

## 1. The cases first

- [x] 1.1 Add `AutoMapRendererFrameWaitTest#theSpentBudgetIsToldToTheOwnerAndClearedByALandedFrame`: one
      listener, the budget driven to exhaustion through the existing seam, then a `frameIteration()` that
      draws the outstanding request; the case asserts `[true]` then `[true, false]` and that a further
      healthy iteration reports nothing (spec: `auto-map-renderer` — The spent budget is told to the owner,
      and a landed frame clears it). Verify: red with the report mutated out — XML `tests="8" failures="1"
      errors="0"`, `2026-11-09T22:19:45.346Z` (`evidence/red-on-HEAD-renderer.xml`); green after the restore.
- [x] 1.2 Add `MapTemplateFactoryTest#theNoFrameTemplateSpendsItsContentSlotOnTheNoticeAlone`: the fallback
      template carries exactly one content row, the `map_unavailable` statement, plus the map strip and the
      right strip handed to it. Verify: green in the change's own run (`tests="10" failures="0"`), and its
      content is reachable only through the new factory function — the pre-change tree has no such call.
- [x] 1.3 Add `FreeDrivingScreenHostTest#aScreenWhoseFrameWaitBudgetIsSpentCarriesTheNoticeInTheHostSlot`:
      `ON_CREATE` answers `NavigationTemplate`, the spent-budget seam makes it answer `MapWithContentTemplate`
      with the one notice row and the two-action map strip, and the landing seam returns the
      `NavigationTemplate`. Verify: red with the screen's branch mutated to never take the fallback — XML
      `tests="6" failures="1" errors="0"`, `2026-11-09T22:19:13.257Z`
      (`evidence/red-on-HEAD-freestanding-host.xml`); green after the restore.

## 2. The trigger

- [x] 2.1 `AutoMapRenderer`: add `onFrameWaitStateChanged` (render-thread, change-only) and
      `publishFrameWaitExhausted(exhausted)` as the single writer of `frameWaitExhausted`
      (`AutoMapRenderer.kt:363`, `:371`). Verify: the three assignment sites in `AutoMapRendererLoops.kt`
      (`:100`, `:142`, `:149`) now go through it, and the case of 1.1 passes.
- [x] 2.2 No coordinate and no magnitude in the new diagnostics: the screens log identity only
      ("no frame in 4000 ms — the host content slot carries the notice", "frame landed — the bare map template
      is back") (`guidelines/Logging.md` §3). Verify: `tools/check-sonar-workflow.sh` is untouched by this
      change; the coordinate rule is the one the loop's reviewer reads.

## 3. The templates

- [x] 3.1 `MapTemplateFactory.buildMapUnavailableTemplate` (`MapTemplateFactory.kt:169`): `MapWithContentTemplate`
      with one disabled `map_unavailable` row, the caller's strips and a `MapController`, so the host keeps
      delivering a surface a later frame can draw on. Verify: case 1.2.
- [x] 3.2 `FreeDrivingScreen`: wire the report through `postTemplateRefresh` (`:386`), keep it in
      `mapFrameless`, and branch in `buildTemplate` (`:582`) between the fallback and the content-slotless
      template. Verify: case 1.3, and the existing `theTemplateIsTheBareNavigationTemplateCarryingItsActions`
      still passes.
- [x] 3.3 `NavigationScreen`: the same wiring (`:404`, `:587`) and branch (`:601`), for both the navigating
      split (map strip + action strip) and the not-navigating one (BACK alone), so the fallback loses no
      affordance. Verify: the module suite is green (`classes=98 tests=961 failures=0 errors=0`, aggregate XML not retained
      — the tally is the sum of the 98 per-class XMLs under
      `auto/build/test-results/testDebugUnitTest`; the tally copy is `evidence/auto-module-suite.txt`) —
      this screen has no host case because constructing it needs a live
      `CarContext` (`NavigationScreenTest` drives its static seams only).

## 4. The specification and the guideline

- [x] 4.1 `openspec/changes/.../specs/auto/free-driving/spec.md`, `.../auto-map-renderer/spec.md`,
      `.../test-coverage/spec.md` — the deltas, every scenario naming its case. Verify:
      `tools/check-change-readiness.sh openspec/changes/fix-a-frameless-car-map-shows-the-notice` reports
      `LOOP-CHANGE-READY: ok`.
- [x] 4.2 `guidelines/UICar.md` §3c gains the rule (`:231`); `guidelines/MapCar.md`'s degraded-notice
      sentence (`:149`) is corrected, because it stated that the two `NavigationTemplate` screens can only
      draw the notice on the surface. Verify: `tools/look.sh 'no content slot'` still finds both, and
      `tools/check-doc-routes.sh` passes.

## 5. Not done here

- [ ] 5.1 The AAOS AVD pixels measurement the decision asks for (`MapWithContentTemplate(notice)` vs
      `NavigationTemplate`, judged on `pixel-check` and on the template type the host reports). **Not run:**
      this run has no AVD. The template type is asserted by 1.2/1.3; the layout of the fallback on the host is
      unmeasured, and `design.md` §Risks says so rather than quoting a number nobody took.
- [ ] 5.2 The gate of this change is not this worker's: the run hands the diff to a fresh reviewer and then to
      the child that runs `./tools/loop/gate.sh --base c0ddb97cadaefe10cbf126ba7d19cfb41fb02b97`, which owns
      the whole selection (`LOOP-GATE-SUITE`, the compiled step and the structural checks).
