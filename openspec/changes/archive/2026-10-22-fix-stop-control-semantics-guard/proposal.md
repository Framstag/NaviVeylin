# Proposal — the stop control's own target is held by a case, not by the reader's memory

## Why

Item #7 (`§166`, filed 2026-10-09 by `bugfix-loop` after `fix-nav-overlay-stop-tap` was archived).

- **Measured in the tree today.** `NavigationStateOverlayComposeTest` pins the control's box and its two
  callbacks (`:162` `stopControlHitAreaIsAtLeast48Dp`, `:171` `stopControlBoundsLieOutsideDetailsRegionBounds`,
  `:218` `stopControlCentreEndsNavigationWithoutOpeningDetails`) and every one of them reads either the two
  details regions or the control's own tag in the **unmerged** tree
  (`app/src/test/java/com/naviveylin/ui/navigation/NavigationStateOverlayComposeTest.kt:165,174,223`). Nothing
  reads the merged semantics tree, so nothing asserts that the control is an actionable target of its own while
  no other target of the card covers the same pixels.
- **Root cause, one sentence: the class asserts the control's box and its callback but never the set of
  actionable targets that overlap that box, so a target the card gains over the control's pixels passes every
  existing case — the regression `fix-nav-overlay-stop-tap` removed, now unheld.**
- **Red-shaped measurement (the red is mutation-shaped, see below).** Adding `.clickable(onClick = onClick)`
  to the card — the container-wide tap target the archived change removed, `guidelines/UI.md` §8 "Tap targets
  (phone overlays)" — leaves every existing case of the class green: the mutation run is
  `tests=13 failures=1`, and the one failure is the new case
  (XML `evidence/revert-check-mutated.xml`, 2026-10-22T03:07:12Z).
- **No assertion is false on HEAD.** The contract holds today: the merged node `stopNavigation` carries
  `OnClick` and its own content description (`stop_navigation`), and exactly one actionable node overlaps its
  box (XML `evidence/head-green.xml`, tests=13 failures=0, 2026-10-22T03:06:56Z). The case therefore cannot be
  red on HEAD; it is red under the revert-check mutation, which is the falsification this delta owes.
- **Owning capability and guideline.** `openspec/specs/navigation-status-details/spec.md` ("Routing status
  card is clickable", `:11`); `guidelines/UI.md` §8 "Phone navigation overlay sizing (driver-seat
  readability)", its "Tap targets (phone overlays)" bullet (`:810`).

## What Changes

- `app/src/test/java/com/naviveylin/ui/navigation/NavigationStateOverlayComposeTest.kt`: one case,
  `stopControlIsTheOnlyActionableTargetOverItsOwnBox` — the merged node that carries the stop action carries
  the control's label, and exactly one actionable node overlaps the control's box.
- `openspec/specs/navigation-status-details/spec.md`: the "Routing status card is clickable" requirement gains
  the semantics clause and the scenario that names the case.
- `guidelines/UI.md` §8, "Tap targets (phone overlays)": the rule that no target other than the control may be
  actionable over its box, and the case that holds it.

## Spec

`openspec/specs/navigation-status-details/spec.md`, MODIFIED requirement "Routing status card is clickable":
the control SHALL be an actionable target of its own carrying its own accessible label, and no other
actionable target of the card SHALL overlap its hit area. Two scenarios name one case each.

## Cases and evidence

- `app: NavigationStateOverlayComposeTest.stopControlIsTheOnlyActionableTargetOverItsOwnBox` — green on HEAD
  (`evidence/head-green.xml`, tests=13 failures=0, 2026-10-22T03:06:56Z), red under the revert-check mutation
  (`evidence/revert-check-mutated.xml`, tests=13 failures=1, 2026-10-22T03:07:12Z).
- `./gradlew test -PforceTests --no-build-cache` — the gate (`evidence/gate.xml`, both flavors).

## Impact

One test file, one capability requirement, one guideline bullet. No production code, no native code, no
manifest, no dependency, no release state, no CI workflow: the gate already runs `:app`'s unit tests.

Rollback: revert the commit. Every existing assertion and the archived change's behaviour are unaffected;
the guard is gone and the regression class returns unnoticed.
