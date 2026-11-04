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
      `openspec validate` accepts the change and `tools/check-openspec-hygiene.sh` passes (parent: all three
      scenarios are traceable to 1.1/2.1).
      Verification: the gate's `openspec/*` suites.

## 3. The invariant's revert-check

- [x] 3.1 Delete the `setStyleSheetFlag` line from the effect (`// REVERT-CHECK MUTATION`), run the three cases —
      all three fail; restore, re-run `-PforceTests --no-build-cache` — green. Parent: "A presentation change
      re-renders the mini map". Mutation and both XMLs in `evidence/revert-check-*`; no marker survives.

## 4. Tests the change can affect

- [x] 4.1 `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MiniMap*" --tests
      "com.naviveylin.ui.map.LocationDetails*" --tests "com.naviveylin.ui.map.PoiSearchPanel*" --tests
      "com.naviveylin.ui.map.SearchDialog*"` — the widget's own cases and the surfaces that embed it, green and
      forced. Excluded: no other module reads `MiniMap`; the widget's palette cases are in `MiniMap*`.

## 5. The gate

- [x] 5.1 `./tools/loop/gate.sh` from the change root, once, after 4.1. It ran and ended with `errors=1`
      (`LOOP-GATE` in `evidence/gate.log`); the JVM half is `BUILD SUCCESSFUL in 5m 40s` over 782 files /
      6009 tests / 0 failures / 0 errors (`evidence/gate-tallies.txt`), and the one red step is
      `tools/gen-feature-list.sh --check-classification` (`tests="0" failures="0" errors="1"` in
      `evidence/gate/`), whose two unclassified spec ids belong to the other two open changes in this shared
      worktree. `evidence/gate-attribution.md` quotes the command and the two measurements that place the red
      outside this change (the base tree exits 0; a copy of this tree with those two change directories removed
      exits 0).
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
- [x] 6.5 `guidelines/MapStyling.md` §15 and §15a name the shared record and the widget as a load path, and the
      change no longer claims "no guideline text moves". `openspec/changes/archive/…/proposal.md` and
      `design.md` were corrected where round 1 quoted an unmeasured figure (the `MapRenderer.kt` pointer).
      Parent: the review's finding 7.
