# Tasks — fix-car-search-reference-without-a-map

## 1. The value the car surface publishes

- [x] `core/src/main/java/com/naviveylin/core/CarMapViewportProvider.kt`: a `:core` singleton holding the last
      drawn car frame center — `publish(lat, lon)` drops a non-finite coordinate, nothing clears it,
      `center` is a `StateFlow` read at search time.
      Verify: `CarMapViewportTest.aCenterThatIsNotALocationIsDropped` and `noCarMapHasDrawnYetMeansNoCenter`
      (`evidence/green-CarMapViewportTest.xml`, tests=4 failures=0, 2026-10-22T03:40Z).
- [x] `core/.../AutoEntryPoint.kt` gains `carMapViewportProvider()`, and
      `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` binds it `@Singleton`.
      Verify: the compile of `:app` and `:auto` inside the gate's `./gradlew test` (an unresolved Hilt binding
      fails the build, not a case).

## 2. The publish rule, in one place

- [x] `auto/src/main/java/com/naviveylin/auto/CarMapViewport.kt`: `publishCarViewportCenter(renderer, provider)`
      publishes the renderer's center and leaves the previous one in place when the screen drew nothing.
      Verify: `CarMapViewportTest.theLastDrawnFrameCenterIsPublished` and
      `aScreenThatDrewNothingKeepsTheCenterAlreadyPublished`.

## 3. The four car map screens publish as they stop drawing

- [x] `MapScreen.kt`, `DetailsScreen.kt`, `FreeDrivingScreen.kt`, `NavigationScreen.kt`: the first act of
      `onStop` is the publish, before `rendererGate.pause()`/`detachSurface()` — so the frame published is the
      one still displayed.
      Verify: `DetailsScreenTest` (31 cases, its two `ON_STOP` cases among them) passes with the new
      `AutoEntryPoint` stub — `evidence/green-DetailsScreenTest.xml`, tests=31 failures=0.

## 4. The screen reads it

- [x] `SearchScreen.searchReference()` takes its own center first and the published one second; the history tap
      passes its own `viewportCenter` on to the screen it pushes.
      Verify: `evidence/green-SearchScreenTest.xml` — tests=18 failures=0 (2026-10-22T03:40:25.791Z), the red
      case `aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter` among them, plus
      `theMapScreensOwnCenterWinsOverThePublishedOne` and `noFixAndNoDrawnCarMapMeansNoDistance`.

## 5. The red test, and its falsification

- [x] Red on HEAD: `SearchScreenTest.aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter` —
      `evidence/red-HEAD-SearchScreenTest.xml`, tests=17 failures=1 (2026-10-22T03:37:48.362Z), the assertion
      naming the mechanism: `[2]: argument: null, matcher: eq(SearchReference(lat=52.52, lon=13.405))`.
      Taken on a tree carrying only the provider type and its binding — the two behaviour lines were absent.
- [x] Falsification — `revert-check`: mutate `val center = viewportCenter() ?: carMapViewport.center.value`
      (`SearchScreen.kt:208`) to `val center = viewportCenter()`, i.e. remove the fallback the change adds. The
      red case fails alone: `evidence/mutation-SearchScreenTest.xml`, tests=18 failures=1
      (2026-10-22T03:41:03.896Z), and `CarMapViewportTest` stays green (4/0) in the same run.
- [x] Restore the mutation; `git diff` on `SearchScreen.kt` shows the fallback line back, and the three classes
      are green again (`evidence/green-focused.log`).

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
      Verify: `evidence/gate.log` and `evidence/gate-tally.txt` — `LOOP-GATE: tests=325 failures=0`, and the
      gradle half it ran (its own tally line reads `tests=0` for that command because Gradle prints no
      `<name>: all N cases passed` summary, so the numbers come from the module XMLs): 2 098 mobile + 2 098
      automotive + 873 `:auto` + 642 `:core` + 48 `:osmscout-client-java` = 5 759 tests, 0 failures, every module's
      newest suite timestamp inside the run (03:41:59Z … 03:45:41Z).
