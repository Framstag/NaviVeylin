# Design — one case over the actionable targets of the stop control's box

## Root cause

`NavigationStateOverlayComposeTest` reads the two details regions and the control's own tag in the unmerged
tree; nothing iterates the **actionable targets** of the merged tree, so a target the card gains over the
control's pixels (`NavigationStateOverlay.kt:70` — the card the archived `fix-nav-overlay-stop-tap` change
stripped of `.clickable`) is invisible to all 12 cases.

## The single fix

One case, `stopControlIsTheOnlyActionableTargetOverItsOwnBox` (`NavigationStateOverlayComposeTest.kt`):

1. the merged-tree node carrying the tag `stopNavigation` has a click action and the control's own content
   description (`R.string.stop_navigation`);
2. of every node with a click action, exactly one overlaps the control's bounds.

`SemanticsNode.boundsInRoot` and `Rect.overlaps` are the measurement; no production code changes, so the change
cannot regress the overlay.

## Why this and not the alternatives

- **A case asserting only `assertHasClickAction()`** (the item's own fix-candidate wording) was written first
  and measured: it is green on HEAD *and* green under the card-wide fold, because a merging parent does not
  absorb a merging child — the IconButton stays its own node. It would have held nothing the item names
  (measured 2026-10-22T03:05Z: card-wide `.clickable` added, all 13 cases green). Eliminated by measurement.
- **Counting nodes that carry the stop label** (`hasClickAction() and hasContentDescription(label)`) is green on
  HEAD and green under the same fold, for the same reason. Eliminated by the same probe.
- **A production change** — the fold is a test-only gap, the production code already satisfies the contract
  (`evidence/head-green.xml`: the control is actionable, labelled, and the only target over its box).

## Blast radius

One test class (`:app`, mobile flavor unit tests), the capability's requirement text and one guideline bullet.
No production source, no native code, no manifest, no dependency, no build file, no CI workflow. The car
surfaces render the same `NavigationStatsRow` and are unaffected (its `onStopNavigation = null` path already
has its own case).

## Evidence

- green on HEAD: `evidence/head-green.xml` (tests=13 failures=0, 2026-10-22T03:06:56Z);
- revert-check: the card gains `.clickable(onClick = onClick)`, and only the new case fails —
  `evidence/revert-check-mutated.xml` (tests=13 failures=1, 2026-10-22T03:07:12Z), restored afterwards
  (`git diff` on `NavigationStateOverlay.kt` empty).

## Rollback

Revert the commit. The guard disappears and the behaviour is unchanged.
