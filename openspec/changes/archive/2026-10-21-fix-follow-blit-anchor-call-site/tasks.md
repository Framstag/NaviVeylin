# Tasks — fix-follow-blit-anchor-call-site

## 1. The guard, written first (red on HEAD)

- [x] 1.1 Write `app/src/test/java/com/naviveylin/ui/map/FollowBlitCallSiteTest.kt`: the source guard over
      `MapCanvasScreen.kt` and `FollowBlitRules.kt` — `the follow blit offset is computed by the seam and not
      inline in the frame loop` (one `FollowBlitRules.offsetFor(` call, no
      `FollowPrediction.displayOffsetPx(` call) and `the follow offset path cannot read the raw preset` (one
      `resolvedAnchor` read, no `activeFollowAnchor` read, still calls the shared helper) — spec
      `smooth-follow`, "The follow blit offset is computed by one seam that owns the anchor", scenarios 1
      and 3.
      *Verification*: run on the unmodified tree,
      `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FollowBlitCallSiteTest"
      -PforceTests --no-build-cache` → `BUILD FAILED in 5s`, XML `tests=2 failures=2`, timestamp
      `2026-10-21T08:59:28.889Z`, both cases naming the missing seam file
      (`evidence/red-head-FollowBlitCallSiteTest.xml`).

## 2. The seam

- [x] 2.1 Add `app/src/main/java/com/naviveylin/ui/map/FollowBlitRules.kt`:
      `offsetFor(ui: MapCanvasUiState, frame: PanWindowRules.Frame, displayedLat, displayedLon, canvasW,
      canvasH, dpi)` reads `ui.resolvedAnchor.fx/fy` and returns
      `FollowPrediction.displayOffsetPx(…)`. No Android or Compose type in its signature (the frame is
      `PanWindowRules.Frame`).
      *Verification*: case 3 of `FollowBlitCallSiteTest` passes on the seam file; `:app` compiles.

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
      below the raw `0.9`; frame = `anchorCenter(display, resolved)` with the default 1.2× overrun bitmap;
      display drifted 40 m north; the seam's offset is unclamped and equals the resolved-anchor computation,
      the raw-preset computation of the same geometry is clamped. Both values printed.
      *Verification*: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.MapCanvasViewModelVehicleAnchorTest" -PforceTests --no-build-cache` → the new
      case green with its `followBlitAnchor …` measurement line, the class's other cases green
      (`evidence/green-MapCanvasViewModelVehicleAnchorTest.xml`).

## 4. Falsification of the new invariant

- [x] 4.1 Revert-check (`revert-check` skill): make the seam pass `ui.activeFollowAnchor.fx/fy` to
      `displayOffsetPx`, run the two cases — the source guard must fail naming the raw-preset read and the
      geometry case must fail — then restore and re-run green.
      *Verification*: mutated run XML `tests=… failures=2` with the named failures
      (`evidence/red-revert-check-FollowBlitCallSiteTest.xml`,
      `evidence/red-revert-check-MapCanvasViewModelVehicleAnchorTest.xml`), restored run green
      (`evidence/green-FollowBlitCallSiteTest.xml`, `evidence/green-MapCanvasViewModelVehicleAnchorTest.xml`),
      and `evidence/revert-checked.md` quotes both assertions.

## 5. Owning document

- [x] 5.1 `guidelines/MapRendering.md` §1.1: name `FollowBlitRules.offsetFor` as the one place the phone follow
      blit offset is computed, state that the seam takes the state (not an anchor) so the raw preset is not
      expressible there, and name `FollowBlitCallSiteTest` + the geometry case as the guards.
      *Verification*: the section names the seam, the guard and the case; the delta touches no compiled file,
      so this task runs no build — the structural check is `grep -n "FollowBlitRules.offsetFor"
      guidelines/MapRendering.md`.

## 6. The gate, once

- [ ] 6.1 Run the project's gate exactly as the run's configuration states it, once, on the tree the change
      leaves behind.
      *Verification — FAILED, not retried*: `./gradlew test -PforceTests --no-build-cache` →
      `BUILD FAILED in 2m 40s`, 173 actionable tasks (22 executed, 151 up-to-date), failing tasks
      `:app:testMobileDebugUnitTest` and `:auto:testDebugUnitTest`. Per-suite tallies from the XMLs that run
      wrote (`evidence/gate/gate-tally.txt`): `:app` mobile `tests=2089 failures=59`, `:app` automotive
      `tests=46 failures=0`, `:auto` `tests=866 failures=10`, `:core` `tests=642 failures=0`,
      `:osmscout-client-java` `tests=48 failures=0` (`evidence/gate/gate-failures.txt` names every failing
      class and its first messages). The `:app` automotive XMLs are dated `08:51`, i.e. the task was
      `UP-TO-DATE` and that suite was **not** executed by this gate run — its `failures=0` is not evidence
      about this change. One environment cause explains the set: `java.io.IOException: Der
      zugewiesene Plattenplatz (Quota) ist überschritten` (the RAM tmpfs `/tmp`, ~7.7 GB, `guidelines/Build.md`
      §2 records it; it stood at 74 % used during the run), `Unable to load Robolectric native runtime
      library`, `UnsatisfiedLinkError: android.graphics.RenderNode`, and the settings-file failures that
      follow from it — e.g. 9 cases of `MapCanvasViewModelVehicleAnchorTest` that assert a persisted preset
      see `expected:<0.9> but was:<0.5>`. **This change's two cases passed in that very run**:
      `evidence/gate/gate-app-mobile-FollowBlitCallSiteTest.xml` `tests=2 failures=0` and the new case's
      `<testcase name="followBlitOffsetTakesTheResolvedAnchorNotTheRawPreset"/>` with no failure child in
      `evidence/gate/gate-app-mobile-MapCanvasViewModelVehicleAnchorTest.xml`. The gate is not re-run here
      (worker-prompt, phase D: one run per item); the item is escalated with the gate that failed and the
      cause it printed.

## 7. Archive

- [x] 7.1 `openspec archive fix-follow-blit-anchor-call-site` — the change sits under
      `openspec/changes/archive/<date>-fix-follow-blit-anchor-call-site/` and the requirement "The follow blit
      offset is computed by one seam that owns the anchor" with its three scenarios is merged into
      `openspec/specs/smooth-follow/spec.md`.
      *Verification*: `openspec validate smooth-follow --strict` → valid; the archive directory exists and the
      merged spec carries the three scenarios.
