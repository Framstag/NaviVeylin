# Tasks — fix-stop-control-semantics-guard

## 1. The case

- [x] `NavigationStateOverlayComposeTest.stopControlIsTheOnlyActionableTargetOverItsOwnBox`: the merged node
      tagged `stopNavigation` carries a click action and `R.string.stop_navigation`, and exactly one actionable
      node overlaps its bounds.
      Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.navigation.NavigationStateOverlayComposeTest"`
      → `evidence/head-green.xml`, tests=13 failures=0 (2026-10-22T03:06:56Z).

## 2. The falsification the case owes

- [x] Mutate the production invariant once — `.clickable(onClick = onClick)` on the card
      (`NavigationStateOverlay.kt:71`) — the new case must fail and the other 12 must stay green.
      Verify: `evidence/revert-check-mutated.xml`, tests=13 failures=1 (2026-10-22T03:07:12Z), the failure being
      `only the stop control may be actionable over its own box (found [SemanticsNode@…, SemanticsNode@…])`.
- [x] Restore the mutation; `git diff` on `NavigationStateOverlay.kt` is empty.

## 3. The spec delta

- [x] `openspec/specs/navigation-status-details/spec.md`: the "Routing status card is clickable" requirement
      gains the semantics clause, and the scenario "The control is the only target over its own box" names the
      case.
      Verify: `openspec validate navigation-status-details --type spec` and `openspec doctor`.

## 4. The guideline rule

- [x] `guidelines/UI.md` §8, "Tap targets (phone overlays)": the rule that no actionable target other than the
      control may cover its box, the case that holds it, and why reading the two details regions was not enough.
      Verify: `tools/check-doc-routes.sh` (no new route, existing anchors still resolve).

## 5. The gate

- [x] `./tools/loop/gate.sh` — the pathspec carries a compiled test source and an `openspec/` delta, so the run
      is the Gradle gate plus the documentation/classification checks.
      Verify: `evidence/gate.log`, its tallies copied from `app/build/test-results/loop-gate/`.
