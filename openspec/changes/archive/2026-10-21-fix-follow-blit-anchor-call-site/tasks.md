# Tasks — fix-follow-blit-anchor-call-site

## 1. The guard, written first (red on HEAD)

- [x] 1.1 Write `app/src/test/java/com/naviveylin/ui/map/FollowBlitCallSiteTest.kt`: the source guard over
      `MapCanvasScreen.kt` and `FollowBlitRules.kt` — `the follow blit offset is computed by the seam and not
      inline in the frame loop` (one `FollowBlitRules.offsetFor(` call, no
      `FollowPrediction.displayOffsetPx(` call, no `activeFollowAnchor` read in the 20 code lines around the
      seam call) and `the follow offset path cannot read the raw preset` (one `resolvedAnchor` read, no
      `activeFollowAnchor` read, still calls the shared helper) — spec `smooth-follow`, "The follow blit
      offset is computed by one seam that owns the anchor", scenarios 1 and 3.
      *Verification*: run on the unmodified tree,
      `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FollowBlitCallSiteTest"
      -PforceTests --no-build-cache` → `BUILD FAILED in 5s`, XML `tests=2 failures=2`, timestamp
      `2026-10-21T08:59:28.889Z`, both cases naming the missing seam file
      (`evidence/red-head-FollowBlitCallSiteTest.xml`). The count assertions are additionally exercised
      red on HEAD's call-site shape (the inline `displayOffsetPx` call restored, seam file present):
      `tests=2 failures=1`, `2026-10-21T09:11:05.548Z`, `seamCalls=0 inlineCalls=1`, `expected:<1>
      but was:<0>` (`evidence/red-head-callsite-FollowBlitCallSiteTest.xml`). The call-site raw-preset
      assertion is exercised red by the mutation in 4.2.

## 2. The seam

- [x] 2.1 Add `app/src/main/java/com/naviveylin/ui/map/FollowBlitRules.kt`:
      `offsetFor(ui: MapCanvasUiState, frame: PanWindowRules.Frame, displayedLat, displayedLon, canvasW,
      canvasH, dpi)` reads `ui.resolvedAnchor.fx/fy` and returns
      `FollowPrediction.displayOffsetPx(…)`. No Android or Compose type in its signature (the frame is
      `PanWindowRules.Frame`).
      *Verification*: scenario 3 (`the follow offset path cannot read the raw preset`) passes on the seam
      file, and the call-site assertion in scenario 1 is green (`callSiteRawReads=0`); `:app` compiles.

- [x] 2.2 `MapCanvasScreen.kt:817`: replace the inline `FollowPrediction.displayOffsetPx(…)` with
      `FollowBlitRules.offsetFor(ui, PanWindowRules.Frame(vp.lat, vp.lon, vp.mag, vp.angle, bitmap.width,
      bitmap.height), followDisplayLat, followDisplayLon, canvasSize.width, canvasSize.height, dpi)`. The
      surrounding guard, the clamp, the refresh policy and the diagnostics stay as they are.
      *Verification*: `FollowBlitCallSiteTest` green — `seamCalls=1 inlineCalls=0`, `readsResolved=1
      readsRawPreset=0 callsOffset=1` (`evidence/green-FollowBlitCallSiteTest.xml`).

## 3. The geometry at the call-site seam

- [x] 3.1 Add `followBlitOffsetTakesTheResolvedAnchorNotTheRawPreset` to
      `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelVehicleAnchorTest.kt`: real ViewModel,
      `BOTTOM_CENTER` preset, `setMapOverlayInsets(top = 400, bottom = 400, right = 80)` → `resolvedAnchor.fy`
      below the raw `0.9`; the case asserts the persisted preset (`activeFollowAnchor.fy == 0.9`, so the
      settings fallback `0.5` cannot make the two anchors collapse), takes the frame from the production
      `viewModel.followRenderTarget(...)` and asserts it equals the resolved-anchor `anchorCenter(...)`;
      default 1.2× overrun bitmap; display drifted 40 m north; the seam's offset is unclamped and equals the
      resolved-anchor computation, the raw-preset computation of the same geometry is clamped. Both values
      printed.
      *Verification*: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.MapCanvasViewModelVehicleAnchorTest" -PforceTests --no-build-cache` → the new
      case green with its `followBlitAnchor … presetFy=0.9 resolvedFy=0.7333 activeFy=0.9 seamRawY≈0
      presetRawY=-320.0` measurement line, the class's other cases green
      (`evidence/green-round2-MapCanvasViewModelVehicleAnchorTest.xml`, `tests=16 failures=0`,
      `2026-10-21T09:11:52.035Z`).

## 4. Falsification of the new invariant

- [x] 4.1 Revert-check (`revert-check` skill): make the seam pass `ui.activeFollowAnchor.fx/fy` to
      `displayOffsetPx`, run the two cases — the source guard must fail naming the raw-preset read and the
      geometry case must fail — then restore and re-run green.
      *Verification*: mutated run XML `tests=… failures=2` with the named failures
      (`evidence/red-revert-check-FollowBlitCallSiteTest.xml`,
      `evidence/red-revert-check-MapCanvasViewModelVehicleAnchorTest.xml`), restored run green
      (`evidence/green-FollowBlitCallSiteTest.xml`, `evidence/green-MapCanvasViewModelVehicleAnchorTest.xml`),
      and `evidence/revert-checked.md` quotes both assertions.

- [x] 4.2 Round-2 revert-check on the call site: rebuild the state around the raw preset
      (`ui.copy(resolvedAnchor = ResolvedAnchor(ui.activeFollowAnchor.fx, ui.activeFollowAnchor.fy))`) at the
      seam call, run the guard — it must fail on the call-site raw-preset assertion — then restore and re-run
      green.
      *Verification*: mutated XML `tests=2 failures=1`, `2026-10-21T09:11:38.809Z`,
      `callSiteRawReads=2`, `expected:<0> but was:<2>`
      (`evidence/red-revert-check-callsite-FollowBlitCallSiteTest.xml`); restore
      `grep -c 'REVERT-CHECK MUTATION' MapCanvasScreen.kt` → `0`; both cases green,
      `2026-10-21T09:11:51.331Z` / `09:11:52.035Z` (`evidence/green-round2-*.xml`). The exact spelling quoted
      in the round-1 review (`ui.copy(resolvedAnchor = ui.activeFollowAnchor)`) does not compile — the field
      is `ResolvedAnchor`, the preset is `VehicleAnchorPosition` — so the guard was exercised by the
      compiling form; `evidence/revert-checked.md` records both.

## 5. Owning document

- [x] 5.1 `guidelines/MapRendering.md` §1.1: name `FollowBlitRules.offsetFor` as the one place the phone follow
      blit offset is computed, state that the seam takes the state (not an anchor), record that a call site
      rebuilding the state around the preset is refused by the guard, and name `FollowBlitCallSiteTest` + the
      geometry case as the guards.
      *Verification*: the section names the seam, both guards and the case, with the round-2 measurement
      (`callSiteRawReads=2`, mutated run red); the delta touches no compiled file, so this task runs no
      build — the structural check is `grep -n "FollowBlitRules.offsetFor" guidelines/MapRendering.md`.

## 6. The gate, once

- [ ] 6.1 Run the project's gate exactly as the run's configuration states it, once, on the tree the change
      leaves behind.
      *Verification — FAILED, not retried*: `./gradlew test -PforceTests --no-build-cache` →
      `BUILD FAILED in 2m 19s`, 174 actionable tasks (21 executed, 153 up-to-date), failing task
      `:app:testMobileDebugUnitTest`; `:auto:testDebugUnitTest`, `:core:testDebugUnitTest` and
      `:osmscout-client-java:test` executed green. Per-suite tallies from the XMLs that run wrote
      (`evidence/gate/gate-tally-round2.txt`): `:app` mobile `tests=2089 failures=67`, `:auto`
      `tests=866 failures=0`, `:core` `tests=642 failures=0`, `:osmscout-client-java` `tests=48 failures=0`;
      `:app` automotive has **no** XML (`tests=0 files=0`) — the build stopped at the failing mobile task, so
      `:app:testAutomotiveDebugUnitTest` was never scheduled (under `-PforceTests` it cannot be
      `UP-TO-DATE`; the round-1 record's "UP-TO-DATE" cause was wrong and is corrected here). The mobile
      failure set is environmental, quoted from the XMLs: `java.io.IOException: Der zugewiesene
      Plattenplatz (Quota) ist überschritten` (the RAM tmpfs `/tmp`, `guidelines/Build.md` §2), 25 of the 67
      failures carry it, and the settings-file cases that follow see `expected:<0.9> but was:<0.5>`.
      `FollowBlitCallSiteTest` is green in the gate (`tests=2 failures=0`, `2026-10-21T09:14:08.557Z`,
      `seamCalls=1 inlineCalls=0 callSiteRawReads=0`); the new geometry case is one of the 10 failing cases
      of its class because the quota swallowed the persisted preset and its round-2 assertion refuses the
      `0.5` fallback (`2026-10-21T09:14:24.499Z`). The item is therefore **blocked** on the gate, not
      closed: a red aggregate is not completion evidence, and the gate is not re-run here (worker-prompt,
      phase D: one run per child). The focused runs are green and unchanged in the same tree
      (`evidence/green-round2-*.xml`).

## 7. Archive

- [x] 7.1 `openspec archive fix-follow-blit-anchor-call-site` — the change sits under
      `openspec/changes/archive/<date>-fix-follow-blit-anchor-call-site/` and the requirement "The follow blit
      offset is computed by one seam that owns the anchor" with its three scenarios is merged into
      `openspec/specs/smooth-follow/spec.md`.
      *Verification*: `openspec validate smooth-follow` → `Specification 'smooth-follow' is valid`, exit 0
      (`evidence/openspec-validate-round2.log`); `openspec validate smooth-follow --strict` exits 1 on five
      pre-existing `>500 characters` requirement-text warnings and reports no structural error
      (`evidence/openspec-validate-strict-round2.log`); `openspec doctor` exit 0
      (`evidence/openspec-doctor-round2.log`). The archive directory exists and the merged spec carries the
      three scenarios.
