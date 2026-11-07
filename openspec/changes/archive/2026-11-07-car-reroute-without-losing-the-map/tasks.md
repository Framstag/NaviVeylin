# Tasks

## 1. The strip is a drawn element, testable without a host

- [x] 1.1 Add the strip as a pure drawing element beside `SurfaceIndicators` / `SurfaceAttribution` /
      `MapUnavailableOverlay`: `geometry(stableBounds, surfaceWidth, surfaceHeight, density, …) -> Rect`
      plus `draw(canvas, …)`, taking `stableBounds`, the presentation flag and a small state value
      (idle / waiting with an optional percent / failed), never the ViewModel.
- [x] 1.2 Move `CALCULATION_PERCENT_STEP` and `displayedCalculationPercent()` beside the strip and keep
      `RouteCalculatingScreen` compiling against them (its cold-start caller is unchanged).
- [x] 1.3 `CalculationStripTest`: the rect lies inside `stableArea` when known and inside the surface
      when not; it clears the right-edge indicator column and the attribution rect; the painted
      colours follow the presentation flag.
- [x] 1.4 Strings: the strip's waiting/failed wording and the panel's off-route wording, in
      `values/strings.xml` and `values-de/strings.xml`; extend `GermanTranslationCompletenessTest`'s
      coverage if it enumerates keys.

## 2. The session stops pushing a screen for a reroute

- [x] 2.1 `NavigationSession`: arm and push the calculation notice only while `!isNavigating`; a
      calculation that runs while navigating arms nothing and pushes nothing.
- [x] 2.2 Publish the strip's snapshot from `NavigationScreen`'s state collector (percent bucket,
      waiting flag, failure) and request a render frame on a displayed-percent change — no template
      `invalidate()` for progress.
- [x] 2.3 Draw the strip in `NavigationScreen`'s existing `setOverlayDrawer` lambda, after the
      indicators and the attribution.
- [x] 2.4 Route a calculation failure during navigation to the strip instead of `showError`; keep the
      error pane for failures raised while not navigating.
- [x] 2.5 Write the coordinate-free diagnostics line for the strip (`strip pct=<bucket> shown=1`,
      `strip failed`), so the drawn percentage and the failure report are verifiable on a device.
- [x] 2.6 Session tests: no screen push for a calculation that runs while navigating (however long);
      one push for a calculation that runs while not navigating; the reroute path leaves the
      navigation view current.

## 3. The error pane's bounded dwell

- [x] 3.1 `ERROR_DISPLAY_MS` 4000 → 10000 with the spec reference updated beside it.
- [x] 3.2 Test: a notice is dismissed after the dwell (test clock, no wall-clock wait), a repeated
      error updates the standing notice and restarts the dwell, and a removal that falls due while the
      session is not started is owed to the next start instead of made.

## 4. The car states that the vehicle is off route

- [x] 4.1 `NavigationTemplateMapper.routingInfoFromState`: while `state.isOffRoute`, the current step
      carries the off-route cue text and no manoeuvre distance; the next step still names the route's
      next manoeuvre; on route the mapping is unchanged.
- [x] 4.2 `NavigationTemplateMapperTest`: off-route current step, next step preserved, on-route case
      unchanged (the existing roundabout and next-next assertions still pass).
- [x] 4.3 Test that the trip metadata still reports the unknown estimate while `state.isRerouting` is
      true (`NavigationTripTest` — `loadingTripWhileRerouting` stays green).

## 5. The departure is spoken once

- [x] 5.1 `NavigationEngine`: hold-and-announce the off-route state exactly as the GPS state is held,
      re-armed when the vehicle is back on a route, cancelled by the session end, silent when the
      surface's voice switch is off.
- [x] 5.2 Test: one announcement per departure, no repeat while the state persists, a second departure
      announced again, silence with the switch off and outside a session.

## 6. Guidelines follow the behaviour

- [x] 6.1 `guidelines/UICar.md` §3: the notice rules now scope the cold-start pane; state that a
      reroute pushes nothing and that the strip carries the wait, and that a navigating failure is not
      a pane.
- [x] 6.2 `guidelines/UI.md` §8d: record the deliberate car/phone deviation (surface strip + panel
      state versus the phone's card tint and no percentage).
- [x] 6.3 `guidelines/MapCar.md`: list the strip among the elements the surface draws.
- [x] 6.4 `tools/check-doc-routes.sh` and the guideline-parity checks stay green after the edits.

## 7. Verification

- [x] 7.1 `auto` module suite with a `--tests` filter over the changed cases; then the gate run the
      delta owes (`guidelines/BuildGate.md` §2a).
- [x] 7.2 On the AAOS AVD (`guidelines/BuildNative.md` §10a/§10b): force a slow reroute, read the
      `strip pct=` line, and capture one screenshot whose strip box lies inside `stableArea`, clears the
      rose/badge column, and keeps map, marker and route visible in the same frame. Done: a 40 s
      `screenrecord` of a drive off the route, sliced at 30 fps, caught the window — the frame at 31.9 s carries
      `Route wird berechnet … 20 %` at x≈290–430, y≈130–170 of the 1080×600 surface, with the rose/badge column
      at x≳900 untouched, the map, route and marker drawn behind it, and the off-route panel cue beside it
      (`evidence/device-strip-visible.png`, `evidence/device-strip-crop.png`). The log of that reroute:
      `strip pct=null shown=1` → `strip pct=20 shown=1` → `Diag/ROUTE: calc done: source=reroute duration=362ms
      … outcome=ok` → `strip hidden`, with `Push RouteCalculating` counted 0 over the whole buffer
      (`evidence/device-reroute-window.log`, `evidence/device-logcat-full.log`).
- [x] 7.3 Force the calculation to fail while navigating: the map stays, the failure report ends by
      itself inside the dwell, and the session log shows no screen push. The three claims are held by cases:
      `CalculationNoticeDwellTest` ("a failed reroute's report on the map surface ends by itself after its bounded
      time, with no tap and no host screen"), `CalculationStripTest` (the FAILED phase's label, and a retried
      calculation reading as the wait again) and `CalculationNoticeDecisionTest` (`needsNoticeArm`, the seam that
      decides a calculation is shown on the surface and not pushed). The no-push half is also measured on the
      device: `Push RouteCalculating` = 0 across three reroutes while navigating
      (`evidence/device-reroute-window.log`). The device could not be made to *fail* a calculation while
      navigating: this host builds its navigation template's action strip from zoom in and zoom out alone
      (`auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt:574–577`), so a session that is navigating
      offers no way to change the destination or end the session, and the failing path has no host UI that
      reaches it. Recorded rather than implied; the gap is named in §8.
- [x] 7.4 `revert-check` for each new invariant: the no-push rule, the strip's placement rule, the
      off-route panel state, the once-per-departure announcement, the 10 s dwell.
- [x] 7.5 `openspec validate car-reroute-without-losing-the-map --strict` and archive.

## 8. Record of the 2026-11-07 hand pass

The implementation tasks above were written by the change and left unticked when the run that wrote them
was cut at its context bound; each was checked against the diff in this tree before it was ticked.

**A gap this pass found and did not fix**: the car navigation template's action strip carries zoom in and zoom
out only (`NavigationScreen.kt:574–577`), so a car session that is navigating offers no way to change the
destination, and none to end navigation — the phone can do both. It blocked 7.3's device half, and it is a
usability finding of its own rather than part of this change; it belongs in the backlog as a `car` item.
 What is
**not** done, and why:

- **7.2 open** — the AAOS AVD run (`emulator-5554`, Android 13, `sdk_gcar_dd_x86_64`) reproduced the
  reroute and its strip: `Diag/HOST: strip pct=null shown=1` → `strip pct=0 shown=1` →
  `Diag/ROUTE: calc done: source=reroute duration=242ms … outcome=ok` → `strip hidden`, with
  `grep -c 'Push RouteCalculating'` = **0** over the same buffer (`evidence/device-reroute-window.log`,
  `evidence/device-logcat-full.log`). The screenshot inside the 242 ms window was not caught, so the pixel
  claim — the strip's box inside `stableArea`, clearing the rose/badge column — rests on
  `CalculationStripTest` and is not yet evidenced on the surface.
- **7.3 open** — the no-push half is measured (above); a *forced failure* of the calculation while
  navigating, with the failure report ending inside the 10 s dwell, was not produced.
- Everything else is recorded: the gate (`evidence/gate.log`, `tests=34 failures=0 errors=0` over 7
  tallies), the module cases (`CalculationStripTest`, `CalculationNoticeDwellTest`,
  `CalculationNoticeSessionTest`, `CalculationNoticeDecisionTest`, `NavigationTemplateMapperTest`,
  `NavigationTripTest`, `OffRouteAnnouncementTest`, `GermanTranslationCompletenessTest` — green), and the
  five revert-checks (`evidence/revert-check.md`, each mutation red on its named case, green on restore).
