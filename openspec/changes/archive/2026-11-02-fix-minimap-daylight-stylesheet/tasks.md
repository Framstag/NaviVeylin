# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/mini-map/spec.md` for the contract. Every delta
scenario's case is named in the task that lands it.

## 1. The red cases, before the widget is touched

- [x] 1.1 `MiniMapComposeTest` — three cases: `darkPresentationPushesTheDarkDaylightFlag` and
      `lightPresentationPushesTheDaylightFlag` assert the widget's recorded `daylight` push for each presentation,
      and `presentationChangeReRendersTheMiniMap` flips the value while the widget is displayed and requires a
      further render (parent: "The mini map renders the resolved presentation's stylesheet variant", all three
      scenarios). The file's `launch` helper gains `isDarkPresentation`.
      Verification: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.MiniMapComposeTest" -PforceTests --no-build-cache` on the base with only the test file
      changed — `tests="10" failures="3"`, `evidence/red-on-HEAD.xml` (2026-11-02T04:56:48Z).

## 2. The fix

- [x] 2.1 `MiniMap.kt` — one `LaunchedEffect(renderer, isDarkPresentation)` composed before the size/location
      render effect pushes `client.setStyleSheetFlag(MapCanvasViewModel.STYLE_FLAG_DAYLIGHT, !isDarkPresentation)`,
      keyed on the value; on a later change it calls `renderer.invalidateStyle()` so the widget's tile cache and
      front buffer do not survive the variant switch (parent: same requirement).
      Verification: the three cases above green, plus the whole `MiniMap*` set.

- [x] 2.2 `openspec/specs/mini-map/spec.md` — one added requirement and its three scenarios, through the delta;
      `tools/check-openspec-hygiene.sh` passes (parent: all three scenarios are traceable to 1.1/2.1). The
      change is archived, so `openspec validate` has no target here — the hygiene check is the check that
      applies, and it is green in `evidence/gate-2/`.
      Verification: the gate's `openspec/*` suites.

## 3. The invariant's revert-check

- [x] 3.1 Delete the `setStyleSheetFlag` line from the effect (`// REVERT-CHECK MUTATION`), run the three cases —
      all three fail; restore, re-run `-PforceTests --no-build-cache` — green. Parent: "A presentation change
      re-renders the mini map". Mutation and both XMLs in `evidence/revert-check-*`; no marker survives.

## 4. Tests the change can affect

- [x] 4.1 `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MiniMap*" --tests
      "com.naviveylin.ui.map.LocationDetails*" --tests "com.naviveylin.ui.map.PoiSearchPanel*" --tests
      "com.naviveylin.ui.map.SearchDialog*"` — the widget's own cases and the surfaces that embed it, green and
      forced (round 1: `evidence/focused-4.1/`). Round 2 re-ran the changed classes forced and green:
      `MiniMapComposeTest` 13 cases, `MapDaylightApplierTest` 6, `MapDaylightRecordTest` 1 — 0 failures, 0
      errors, `2026-11-04T19:25:17Z` (`evidence/focused-4.2/`), plus `MapCanvasViewModelViewportRestoreTest`,
      which the full run caught and the round-2 fix repaired. Excluded: no other module reads `MiniMap`; the
      widget's palette cases are in `MiniMap*`.

## 5. The gate

- [x] 5.1 `./tools/loop/gate.sh` from the change root, once, after 4.1. **Round 2's run is the verdict**
      (`evidence/gate-2.log`, `evidence/gate-2/`, `evidence/gate-2-tallies.txt`) on `a80dc1cb`: the JVM half is
      `BUILD SUCCESSFUL in 283s` with 0 failures / 0 errors over mobile 2232, automotive 2232, `:auto` 912,
      `:core` 653 and `:osmscout-client-java` 112 (6141 tests), and the one red step is a wall-clock bound in
      `tools/loop-selftest.sh`, not code: `FAIL run#aReviewerEndedAtItsTurnBoundIsNotARefusal (exceeded 900 s)`,
      which passes alone on the same tree (`PASS` quoted in `gate-2.log`; BuildEvidence.md §4d/§4e). The
      round-1 run is history: `evidence/gate.log` ended with `errors=1` on
      `tools/gen-feature-list.sh --check-classification`, whose two unclassified spec ids belong to two other
      open changes in the shared worktree (`evidence/gate-attribution.md`).
      Parent: the whole delta.

## 6. Round 2 — the reviewer's three blocking findings

See `design.md`, "Round 2", for why each one is additive rather than a re-run.

- [x] 6.1 One app-side record for the `daylight` flag: `MapDaylightApplier` (per client and value, records only
      after `wasLastStyleLoadSuccessful()`, `force` for the `MapStyleApplyReason` re-push reasons), written by
      `MapCanvasViewModel.ensureMapStyle`/`pushDaylight` and by `MiniMap`. `loadMapStyle` records the value its
      load installed. Cases: `app: MapDaylightApplierTest` (7 cases) and `MiniMapComposeTest`
      "aClientThatAlreadyCarriesTheVariantIsNotReloaded". Revert-check: `force = true` at the widget's push
      fails that case (`evidence/revert-check-3.3.log`).
      Parent: "The push SHALL happen once per distinct value" — now per client value, not per widget mount.
- [x] 6.2 A rejected variant reload is reported once through `MapStyleLoadReporter`, and the message reaches the
      surface that embeds the widget (`onStyleLoadFailure`, wired to `uiState.snackbarMessage` by both
      embedders). Case: `MiniMapComposeTest` "aRejectedVariantReloadIsReported". Revert-check: the `FAILED`
      branch reduced to `Unit` fails it (`evidence/revert-check-3.4.log`).
      Parent: scenario "Style flag change fails" (§15a).
- [x] 6.3 The ordering half is exercised by a case that can fail a late push. `FakeOSMScoutClient` records
      `daylightAtRender` (the flag value in effect at each render), and both variant cases assert it first.
      Revert-check: `if (!hadFrame) return@LaunchedEffect` fails both on `expected:<[false]> but was:<[null]>`
      (`evidence/revert-check-3.2a.log`).
      Parent: "before it requests its first frame".
- [x] 6.4 The re-render half is mutated too, which round 1's NOTE left open: `if (hadFrame)
      renderer.invalidateStyle()` reduced to `if (false && …)` fails `presentationChangeReRendersTheMiniMap`
      (`evidence/revert-check-3.2b.log`). Parent: "it SHALL re-render its own viewport when the value changes".
- [x] 6.5 `guidelines/MapStyling.md` §15 and §15a name the shared record and the widget as a load path, §15's
      "Rule: Phone" states that each surface re-renders on its own change, and "Rule: Startup race" reconciles
      `stylePushedToNative` with the applier. The change no longer claims "no guideline text moves";
      `proposal.md`'s two unmeasured figures are corrected (`isDarkPresentation` 115 → 128 lines, measured
      2026-11-04; `MiniMapComposeTest` `@Test` 7 → 13) and the `MapRenderer.kt` pointer now reads `484-489`. The
      `AGENTS.md` §15a cost figure (`3.2 kB` → `3.4 kB`) is that edit's consequence and `tools/check-doc-routes.sh`
      is the check that verifies it (green in `evidence/gate-2/`).
      Parent: the review's finding 7.
- [x] 6.6 Each surface re-renders on **its own** variant change, not on who performed the push: the widget keeps
      `renderedDark` and invalidates outside the applier's outcome; `pushDaylight` re-renders when its own
      recorded value changed or the re-push was forced. Without this, on a presentation flip the main map pushes
      first, the applier answers `UNCHANGED`, and the mini map keeps the other variant — the second half of the
      item. Cases: `MiniMapComposeTest` "aValueAnotherWriterAppliedStillReRendersTheWidget" (another writer pushes
      first; the widget must still render, and must not push twice) and the pre-existing
      "presentationChangeReRendersTheMiniMap". Revert-check: restoring the outcome-dependent invalidation fails
      both on the render-count wait (`evidence/revert-check-3.5.log`, `-3.5-red.xml`: two timeouts).
      Parent: "it SHALL re-render its own viewport when the value changes".
- [x] 6.7 The style load's record is covered through `loadMapStyle`, not only by a hand-seeded record:
      `MapDaylightRecordTest` "aStyleLoadRecordsTheVariantItInstalled". Revert-check: deleting
      `MapDaylightApplier.record(client, daylight)` from `loadMapStyle` fails it with
      `expected:<UNCHANGED> but was:<APPLIED>` (`evidence/revert-check-3.6.log`).
      Parent: §15's one load per changed value.
- [x] 6.8 One reporting seam, not two: `rejectedLoadMessage` is the single call site of
      `MapStyleLoadReporter.reportFailure`, used by `MapCanvasViewModel.reportStyleLoadFailure` and by the
      widget's `rejectedVariantMessage`. The widget's palette follows the **applied** variant
      (`MapDaylightApplier.applied`), so a rejected reload no longer leaves palette and bitmap disagreeing
      (`guidelines/UI.md` §9). The reject case now asserts exactly one report per attempt.
      Parent: §15a's "one place" and `map-styles`' "Failure is reported once per attempt".
- [x] 6.9 Every round-2 revert-check carries its green half: restored, then
      `./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache --tests "com.naviveylin.ui.map.MiniMap*"
      --tests "com.naviveylin.ui.map.MapDaylight*"` — 20 cases, 0 failures, `2026-11-04T19:25:17Z`
      (`evidence/revert-check-3.2a…3.6-green.xml`).
      Parent: BuildEvidence.md §4e.
