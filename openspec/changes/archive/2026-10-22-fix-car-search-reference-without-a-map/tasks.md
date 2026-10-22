# Tasks — fix-car-search-reference-without-a-map

## 1. The value the car surface publishes

- [x] `core/src/main/java/com/naviveylin/core/CarMapViewportProvider.kt`: a `:core` singleton holding the last
      drawn car frame center — `publish(lat, lon)` drops a non-finite coordinate, nothing clears it,
      `center` is a `StateFlow` read at search time.
      Verify: `CarMapViewportTest.aCenterThatIsNotALocationIsDropped` and `noCarMapHasDrawnYetMeansNoCenter`
      (`evidence/green-round2-CarMapViewportTest.xml`, tests=5 failures=0, 2026-10-22T04:03:40.753Z).
- [x] `core/.../AutoEntryPoint.kt` gains `carMapViewportProvider()`, and
      `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` binds it `@Singleton`.
      Verify: the compile of `:app` and `:auto` inside the gate's `./gradlew test` (an unresolved Hilt binding
      fails the build, not a case).

## 2. The publish rule, in one place

- [x] `auto/src/main/java/com/naviveylin/auto/CarMapViewport.kt`: `publishCarViewportCenter(renderer, provider)`
      publishes the renderer's **committed** frame center
      ([AutoMapRenderer.committedViewportCenter]) and leaves the previous one in place when the renderer has
      committed no frame or does not exist. The accessor is new: `markerViewport()` answers with the pending
      render target while no frame exists, which would publish a center no frame ever displayed.
      Verify: `CarMapViewportTest.theLastDrawnFrameCenterIsPublished`,
      `aScreenWhoseFrameNeverLandedKeepsTheCenterAlreadyPublished` (the mock's `markerViewport()` is stubbed to
      the pending target, so a publish that read it would move the center) and
      `aScreenWithoutARendererKeepsTheCenterAlreadyPublished`; the accessor's own contract is
      `AutoMapRendererTest.committedViewportCenterIsNullBeforeTheFirstFrame`
      (`evidence/green-round2-CarMapViewportTest.xml` 5/0, `evidence/green-round2-AutoMapRendererTest.xml` 82/0).

## 3. The four car map screens publish as they stop drawing

- [x] `MapScreen.kt`, `DetailsScreen.kt`, `FreeDrivingScreen.kt`, `NavigationScreen.kt`: the first act of
      `onStop` is the publish, before `rendererGate.pause()`/`detachSurface()` — so the frame published is the
      one still displayed.
      Verify: `DetailsScreenTest.aStoppedMapPublishesTheLastDrawnFrameCenter` drives `ON_CREATE`/`ON_START`/
      `ON_STOP` with a renderer whose committed center is stubbed and asserts the published value; the class is
      32 cases / 0 failures (`evidence/green-round2-DetailsScreenTest.xml`, 2026-10-22T04:03:41.321Z). Deleting
      the publish from `DetailsScreen.onStop` makes that case fail alone
      (`evidence/mutation-A-DetailsScreenTest.xml`, tests=32 failures=1, 2026-10-22T04:02:36.256Z).

## 4. The screen reads it

- [x] `SearchScreen.searchReference()` takes its own center first and the published one second; the history tap
      passes its own `viewportCenter` on to the screen it pushes.
      Verify: `evidence/green-round2-SearchScreenTest.xml` — tests=18 failures=0 (2026-10-22T04:03:45.350Z), the red
      case `aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter` among them, plus
      `theMapScreensOwnCenterWinsOverThePublishedOne` and `noFixAndNoDrawnCarMapMeansNoDistance`. The pass-through
      itself is asserted by `historyTapPushesScreenWithQuery`: the tapping screen carries a live center of
      `52.52, 13.405` while the surface has `51.5136, 7.4653` published, and the pushed screen's search is
      verified against `SearchReference(52.52, 13.405)`. Dropping `viewportCenter = viewportCenter` from the push
      makes that case fail (`evidence/mutation-B-SearchScreenTest.xml`, tests=18 failures=1).

## 5. The red test, and its falsification

- [x] Red on HEAD: `SearchScreenTest.aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter` —
      `evidence/red-HEAD-SearchScreenTest.xml`, tests=17 failures=1 (2026-10-22T03:37:48.362Z), the assertion
      naming the mechanism: `[2]: argument: null, matcher: eq(SearchReference(lat=52.52, lon=13.405))`. That XML's
      case list is the evidence for the tree it ran on: 17 cases = the 15 baseline plus the red case and
      `theMapScreensOwnCenterWinsOverThePublishedOne`, with `noFixAndNoDrawnCarMapMeansNoDistance` absent (it was
      added afterwards); no captured diff of that tree survives.
- [x] Falsification — `revert-check`: mutate `val center = viewportCenter() ?: carMapViewport.center.value`
      (`SearchScreen.kt:208`) to `val center = viewportCenter()`, i.e. remove the fallback the change adds. The
      red case fails alone: `evidence/mutation-SearchScreenTest.xml`, tests=18 failures=1
      (2026-10-22T03:41:03.896Z); `CarMapViewportTest` stayed green in that same run, carried by the run's own
      summary line (`mutation-focused.log`: `22 tests completed, 1 failed` = 18 + 4), not by an XML — the
      post-restore run overwrote that run's `CarMapViewportTest` tally.
- [x] Falsification of the publish rule (review round 1, B3): `publishCarViewportCenter` reading
      `markerViewport()` instead of `committedViewportCenter()` fails
      `CarMapViewportTest.aScreenWhoseFrameNeverLandedKeepsTheCenterAlreadyPublished`
      (`evidence/mutation-C-CarMapViewportTest.xml`, tests=5 failures=2 — that case plus the one whose stub no
      longer matches, 2026-10-22T04:03:16.421Z); making the accessor fall back to the pending target fails
      `AutoMapRendererTest.committedViewportCenterIsNullBeforeTheFirstFrame`
      (`evidence/mutation-D-AutoMapRendererTest.xml`, tests=1 failures=1, 2026-10-22T04:03:25.879Z).
- [x] Restore the mutations; `git diff` on `SearchScreen.kt` shows the fallback line back, and the four classes
      are green again (`evidence/green-round2-*.xml`, `evidence/green-round2-focused.log`).

## 6. The guideline bullet

- [x] `guidelines/UI.md` §3b: the car-search distance bullet states the car-wide rule (map screen passes its own
      renderer's center; every car map screen publishes its last drawn frame's center; a search opened from the
      root or history list uses it) and that the spec's "neither exists" case now needs a session whose map has
      not drawn a frame.
      Verify: `tools/check-doc-routes.sh` (no new route; the §3b anchor still resolves) inside the gate.

## 7. The spec delta

- [x] `openspec/changes/fix-car-search-reference-without-a-map/specs/search-result-ranking/spec.md`: the
      requirement "Distance reference for ordering and display" MODIFIED (whole requirement restated), one
      scenario added for the new fallback and one for the live map's precedence, and every scenario names
      exactly one case.
      Verify: `openspec validate search-result-ranking --type spec` and the delta-vs-base read below.

- [x] Delta read against the requirement it modifies: the base requirement at
      `git show 80df3a3:openspec/specs/search-result-ranking/spec.md` plus one sentence, two scenarios and a
      `- **Case**:` line on each of the three pre-existing ones; the one pre-existing scenario whose case could
      not exist before ("No reference available") also gains the `- **AND** no car map screen has drawn a frame`
      clause that case asserts.
      Verify: `diff <(git show 80df3a3:openspec/specs/search-result-ranking/spec.md) <(this delta)`

## 8. The gate

- [x] `./tools/loop/gate.sh` — the pathspec carries compiled sources in three modules and an `openspec/` delta, so
      the run is the Gradle gate plus the shell suites the profile names for `openspec/*` and `*.md`.
      Verify: `evidence/gate-round2.log` and `evidence/gate-round2-gradle-modules.txt` —
      `LOOP-GATE: tests=325 failures=0`, and the gradle half it ran (its own tally line reads `tests=0` for that
      command because Gradle prints no `<name>: all N cases passed` summary, so the numbers come from the module
      XMLs): 2098 mobile + 2098 automotive + 876 `:auto` + 642 `:core` + 48 `:osmscout-client-java` = 5762 tests,
      0 failures, every module's newest suite timestamp inside the run (04:04:19Z … 04:08:28Z).
