# Tasks

Loop: `.pi/skills/bugfix-loop` (§118, one bug / one capability / one `fix-*` change).

## 1. The failing test that reproduces the defect (red on HEAD)

- [x] 1.1 Add `app/src/test/java/com/naviveylin/ui/map/FavAutoZoomClampRangeTest.kt`: one case,
  `the clamp scenario states the range the area favorite fit applies`, that reads the two clamp bounds **from the
  code** (`enforcedClampRange`: the floor from a 1000 km bbox, whose raw fit is read with the `minZoom` parameter
  lifted; the ceiling from a 64x bbox-size span over which the fit stops moving — both with their premise asserted,
  so neither end can pass vacuously) and compares them to the two numbers of the spec's scenario `- **WHEN**` line
  (`statedClampRange`), printing `clampRange spec=[…] enforced=[…]` before the assertions.
  *(spec: fav-auto-zoom — Magnitude clamped to valid range)* — done 2026-10-09; the file reads the spec the way
  `MapMenuBackOrderComposeTest` reads its source file (`:app` unit tests run with the module directory as the
  working directory), and stays as the guard after the fix.
- [x] 1.2 Red run on HEAD: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FavAutoZoomClampRangeTest"`
  → XML `tests="1" skipped="0" failures="1" errors="0" timestamp="2026-10-09T18:21:03.694Z"` (`time="8.228"`),
  failure `java.lang.AssertionError: the scenario's lower bound must be the floor the area-favorite fit clamps to expected:<14.0> but was:<4.0>`,
  and that run's `system-out`: `clampRange spec=[4.0, 18.0] enforced=[14.0, 20.0]`; console `BUILD FAILED in 19s`.
  *(spec: fav-auto-zoom — Magnitude clamped to valid range)* — the premises passed (the case reached the value
  assertion), so the red is the divergence and not a parse or premise failure.
  — Copies of that run are kept in this worktree: `app/build/loop118-evidence/red-on-head-TEST-FavAutoZoomClampRangeTest.xml`
  (the XML, `tests="1" failures="1"`, `18:21:03.694Z`) and `app/build/loop118-evidence/red-on-head.log` (the console
  log). They are **not versioned** (that path is under the gitignored `app/build/`, so a `clean` removes it) and are
  re-creatable by restoring the stale scenario line and re-running; everything the change claims after the fix is in
  the standard `app/build/test-results/<flavor>/` XMLs, which 4.4's gate left on disk.
  — The red run stops at the floor assertion, so the second half of the stale text (18) is falsified by 4.2's
  mutation, not by this run.

## 2. The fix

- [x] 2.1 `openspec/specs/fav-auto-zoom/spec.md`: the scenario's `- **WHEN**` line becomes "the computed
  magnification is below the area-favorites magnification floor (14) or above the maximum magnification (20)";
  the `- **THEN**` line, the requirement's prose and its other four scenarios are byte-identical to HEAD
  (`git diff` is `1 insertion(+), 1 deletion(-)` for the whole file).
  *(spec: fav-auto-zoom — Magnitude clamped to valid range)* — done 2026-10-09; focused green run
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FavAutoZoomClampRangeTest"` →
  `tests="1" skipped="0" failures="0" errors="0"`, `system-out` `clampRange spec=[14.0, 20.0] enforced=[14.0, 20.0]`,
  `113 actionable tasks: 16 executed, 97 up-to-date`, `BUILD SUCCESSFUL in 10s`. That run's XML was overwritten by
  4.4's gate, which left fresh green rows for the same case in both flavors (ts `2026-10-09T18:23:28.729Z` mobile /
  `18:26:54.350Z` automotive, `tests="1" failures="0"`, same `system-out` line).
- [x] 2.2 The change's delta (`specs/fav-auto-zoom/spec.md`) carries the same requirement block:
  `openspec validate fix-fav-auto-zoom-clamp-range` → `Change 'fix-fav-auto-zoom-clamp-range' is valid`;
  `openspec validate fav-auto-zoom --type spec` → `Specification 'fav-auto-zoom' is valid`; `openspec doctor` →
  `OpenSpec root: ok`. The two requirement blocks are byte-identical (extracted and diffed).
  *(spec: fav-auto-zoom — all scenarios)* — done 2026-10-09; the archive sync is a textual no-op because both files
  hold the same text.
- [x] 2.3 No production code changes: `git diff HEAD -- app/src/main` is empty.
  *(no spec — the divergence is the sentence)* — done 2026-10-09.

## 3. Every scenario of the delta and the case that exercises it

- [x] 3.1 Scenario "Magnitude clamped to valid range" (the modified one) → `com.naviveylin.ui.map.FavAutoZoomClampRangeTest#the clamp scenario states the range the area favorite fit applies`
  (1.1/1.2 red, green after 2.1 and in 4.4's gate in both flavors). *(spec: fav-auto-zoom — Magnitude clamped to valid range)*
- [x] 3.2 Scenario "The area-favorites floor bounds the fit" (carried unchanged) →
  `com.naviveylin.ui.map.AreaFitViewModelTest#areaFavoriteIsBoundedByTheAreaFavoritesFloor` (existing, green in 4.4).
  *(baseline — not delivered by this change)*
- [x] 3.3 Scenario "Whole-level rounding does not crop the object" (carried unchanged) →
  `com.naviveylin.ui.map.FitVerificationTest#roundedUpFitIsSteppedOutUntilTheBboxFits` (existing, green in 4.4).
  *(baseline — not delivered by this change)*
- [x] 3.4 Scenario "Rotated viewport still contains the object" (carried unchanged) →
  `com.naviveylin.ui.map.FitVerificationTest#rotatedViewportIsSteppedOutUntilTheBboxFits` and
  `com.naviveylin.ui.map.AreaFitViewModelTest#rotatedViewportKeepsBothPositionsVisible` (existing, green in 4.4).
  *(baseline — not delivered by this change)*
- [x] 3.5 Scenario "Area fits viewport" (carried unchanged) → **no case asserts it, before or after this change**;
  the 80 % target is `computeAreaZoom`'s `fitSizePx = minOf(vpWidth, vpHeight) * 0.8` (`MapCanvasViewModel.kt:4673`)
  and `grep -rn "0\.8" app/src/test` finds no case claiming it (only comments, and an unrelated 0.82 / 0.8-factor
  assertion elsewhere). Stated here instead of naming a case that does not assert it; this change neither adds one
  nor weakens the claim.
  *(baseline — a pre-existing gap, not introduced or closed here)*
- [x] 3.6 The scenarios' text in 3.2–3.5 is carried verbatim from HEAD (2.1's `git diff` shows one changed line in
  the requirement block), so their baseline claim strength is unchanged. *(spec: fav-auto-zoom — all scenarios)*

## 4. Falsification and the gate

- [x] 4.1 Revert-check, bound 1: mutation = the scenario's floor back to `(4)`, ceiling kept at `(20)`; the case
  that must fail is 1.1's, on its **floor** assertion. Result: `tests="1" skipped="0" failures="1" errors="0"
  timestamp="2026-10-09T18:22:48.181Z"`, failure `the scenario's lower bound must be the floor the area-favorite
  fit clamps to expected:<14.0> but was:<4.0>`, the same run's `system-out` `clampRange spec=[4.0, 20.0] enforced=[14.0, 20.0]`,
  `BUILD FAILED in 12s`; copy kept at `app/build/loop118-evidence/revert-check-floor-TEST-FavAutoZoomClampRangeTest.xml`
  (+ `revert-check-floor.log`, not versioned). Restored.
  *(spec: fav-auto-zoom — Magnitude clamped to valid range)*
- [x] 4.2 Revert-check, bound 2: mutation = the scenario's ceiling back to `(18)`, floor kept at `(14)`; the case
  must fail on its **ceiling** assertion — the half 1.2's red run never reached. Result: `tests="1" skipped="0"
  failures="1" errors="0" timestamp="2026-10-09T18:23:04.866Z"`, failure `the scenario's upper bound must be the
  ceiling the fit clamps to expected:<20.0> but was:<18.0>`, `system-out` `clampRange spec=[14.0, 18.0] enforced=[14.0, 20.0]`,
  `BUILD FAILED in 11s`; copy kept at `app/build/loop118-evidence/revert-check-ceiling-TEST-FavAutoZoomClampRangeTest.xml`
  (+ `revert-check-ceiling.log`, not versioned). Restored; the delta's requirement block and the main spec's are
  identical again (`diff`).
  *(spec: fav-auto-zoom — Magnitude clamped to valid range)*
- [x] 4.3 Both mutations run one at a time with a forced verdict (`-PforceTests --no-build-cache`), and this task
  records why the force flag is not optional here: the first attempt of 4.1 without it returned
  `BUILD SUCCESSFUL in 2s` and re-served the previous XML untouched (a spec file is not a declared input of the test
  task, `TODO.md` §17's cached-verdict trap). Each red above is from a forced run with a fresh XML timestamp.

## 5. The forced both-flavor gate and the regression check

- [x] 5.1 `./gradlew test -PforceTests --no-build-cache` on the restored tree → `BUILD SUCCESSFUL in 4m 49s`,
  `188 actionable tasks: 24 executed, 164 up-to-date`; `:app` mobile **227 classes / 1751 tests / 0 failures /
  0 errors** (XML timestamps `2026-10-09T18:23:28.729Z` … `18:25:58.041Z`), `:app` automotive **227 / 1751 / 0 / 0**
  (`18:26:07.129Z` … `18:28:05.421Z`), `:core` **41 / 448 / 0**, `:auto` **77 / 789 / 0**,
  `:osmscout-client-java` **2 / 26 / 0**; the new case ran in both flavors
  (`.../testMobileDebugUnitTest/TEST-com.naviveylin.ui.map.FavAutoZoomClampRangeTest.xml` ts `18:23:28.729Z` and
  `.../testAutomotiveDebugUnitTest/…` ts `18:26:54.350Z`, `tests="1" failures="0"`, each carrying
  `clampRange spec=[14.0, 20.0] enforced=[14.0, 20.0]`); the gate log has no `w:` line. Those XMLs are the retained
  artifacts of this change's after-state; `/tmp/loop-118-gate.log` is **not** quoted as evidence (a console log, and
  the next run overwrites it).
  *(spec: fav-auto-zoom — all scenarios)*
- [x] 5.2 No pinned expectation moves: the mandated grep
  (`grep -rn "14\.0\|18\.0\|MIN_AREA_ZOOM\|MAX_MAG\|computeAreaZoom" app/src/test core/src/test auto/src/test`)
  finds no case asserting the fav-auto-zoom clamp range — the two `areaFloor = 14.0` locals in `FitVerificationTest` /
  `AreaFitViewModelTest` are the fit floor *passed in* (untouched, and the fit cases are green in 4.4), and the 18.0
  hits are `SpeedZoomTableTest`, `DiagnosticsLogRetentionTest` / `LogLineCoordinatesTest` (log-line literals) and a
  pan-drag list. Moved expectations: **none** (old → new: n/a).
  *(no spec — regression check)*

## 6. Bookkeeping (after the independent review — not done in this iteration)

- [ ] 6.1 `TODO.md` §118: set `**status:** fixed-by \`fix-fav-auto-zoom-clamp-range\``, keeping the entry's id,
  order and metadata (removal is not allowed in this run). The loop's phase F does this together with the archive,
  which happens after the review child reports, so it is left open here on purpose.
- [ ] 6.2 `openspec archive fix-fav-auto-zoom-clamp-range` — phase F, after the review gate.
