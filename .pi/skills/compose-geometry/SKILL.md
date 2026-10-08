---
name: compose-geometry
description: Assert a UI geometry invariant in a Compose test — a control's size, its tap target, its disjointness from a container's tap area, or a node's visibility inside its parent — instead of assertExists(). Use when a change adds a phone UI control, moves a tap area, fixes a "the tap landed somewhere else" or "the action is off screen" finding, or when a task asks for a bounds/disjointness/tap-target case.
---

# compose-geometry — bounds, not existence

`assertExists()` and `assertIsDisplayed()` answer "is the node in the composition". They do **not**
answer "can the driver hit it": a node drawn off the screen still passes both, and a control sitting
under a container's tap area is still "displayed". Every phone-UI finding of that family
(`TODO.md` §122 — the tap aimed at the stop control never reached it; §138 — the labelled End action
drawn below the screen edge; §72 — a needle stroked in raw pixels) was invisible to a suite that
asserted existence.

This skill turns such a claim into a bounds assertion, using the API this project's Compose version
actually has.

## When to use

- A change adds or moves a phone control, or moves a tap area around one
- A device run shows a tap landing on the wrong action, or an action that cannot be reached
- A task asks for a "disjoint hit areas", "at least 48 dp", "stays inside the card", or "is visible"
  case
- A spec scenario names geometry (`guidelines/UI.md` §8's tap-target rule, `getBoundsInRoot()` in a
  task's text)

## When NOT to use

- The claim is about pixels (colour, contrast, stroke weight as *rendered*): that is `pixel-check`
- The claim is about a device-only surface (car host templates, a real touch screen): that is
  `device-check`, and a Compose case can only be the host substitute (see Pitfalls 6)
- The invariant is a library contract you can neither mutate nor construct (say so in the task)

## Contract

| Assertion | How |
|---|---|
| Size / minimum tap target | `node.assertWidthIsAtLeast(48.dp)` · `node.assertHeightIsAtLeast(48.dp)` (`androidx.compose.ui.test`) |
| Disjointness | `region.getBoundsInRoot().right <= control.getBoundsInRoot().left` (± the vertical pair) |
| Visibility inside a parent | the node's bounds lie inside the container's: `node.bottom <= container.bottom` etc. |
| Which action a tap runs | one counter per callback, `performClick()`, assert exactly one moved — never "a click happened" |
| Query | `onNodeWithTag(tag, useUnmergedTree = true)` — a `testTag` on a `clickable` parent is the node that owns the tap |
| Failure message | put the numbers in it (`"stats region $statsRegion overlaps control $control"`) — the XML quote is the evidence |

**`getBoundsInRoot()` returns a `DpRect`** in this Compose version, not the Float `Rect`. So: compare
Dp bounds directly, and never do pixel arithmetic on it — `bounds.width` / `bounds.right - bounds.left`
do not compile (`Unresolved reference 'width'` / `actual type is 'Float', but 'Dp' was expected`).
Sizes go through `assert*IsAtLeast(Dp)`, which needs no arithmetic at all.

## Procedure

1. **Name the invariant in one sentence** and the case that will carry it — before writing code. Same
   discipline as `revert-check`: "the control's hit area lies outside the details tap area" is an
   invariant; "the buttons work" is not.
2. **Give every target a tag.** The control (`testTag("stopNavigation")`) *and* the container region
   whose bounds you compare it against (`testTag("navStatusDetailsRegion")`) — an untagged region
   cannot be asserted, and reading it off a screenshot is a device task.
3. **Keep the shared composable shared.** When two hosts render one row, a caller makes part of it
   tappable through a parameter (e.g. `NavigationStatsRow.leadingModifier`) instead of each host
   rendering its own copy of the control — one implementation, one place for the tag.
4. **Write the three families of case**: size (`assert*IsAtLeast`), geometry (disjointness or
   visibility), and action identity (counters per callback). A case that only clicks proves the
   callback wiring, not the geometry.
5. **Run focused**: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.<area>.*"`.
   Quote the XML's `tests/failures/errors` and its `timestamp` — a cached run proves nothing
   (`guidelines/Build.md` §4).
6. **Falsify each invariant once** with the `revert-check` skill. The mutations that work here move the
   *layout*, not the test: (a) move the tap area onto the whole row so it covers the control, (b) shrink
   the control's box (`Modifier.size(48.dp)` → `40.dp`), (c) neuter the control's own handler
   (`onClick = {}`). Each must fail its geometry/size/identity case *for that reason*; restoring returns
   the tree to a cached state, so force the green run.
7. **Record** the mutation, the failing case, its assertion message, and both runs in the task item.

## Pitfalls

1. **`assertExists()` is not visibility.** A node below the screen edge or outside its card passes it.
   Assert the bounds against the container the user actually sees (`TODO.md` §138, where
   `RoutePanelComposeTest` asserted tags and a clipped End action stayed green).
2. **A container's `clickable` does not make the control untappable — and that is exactly why the click
   case cannot falsify an overlap guard.** Measured 2026-10-05 on the stop control: with the details tap
   moved onto the whole stats row (covering the 48 dp box), the disjointness case failed while the
   click-identity case **passed** — Compose still delivered the pointer event to the innermost target.
   An overlap must be asserted as geometry; a click case would have called it fixed.
3. **`bounds.width` / px arithmetic on a `DpRect`** — see the Contract above. Use `assert*IsAtLeast` and
   bare Dp comparisons.
4. **Tags on merged semantics** need `useUnmergedTree = true`; without it the query may resolve to the
   merging ancestor and report its bounds.
5. **Don't convert to px by hand.** `composeRule.density` is not needed for any of these assertions; a
   hand-converted pixel threshold breaks on another ABI/density and is not the invariant.
6. **A device-gated surface is not proven by a phone case.** Compose the shared composable with the
   other surface's parameters instead (the car renders `NavigationStatsRow` with
   `onStopNavigation = null`) and say in the task that it is a host substitute, not a device run.
7. **No mutation, no case.** A geometry case added without a revert-check is `TODO.md` §111's shape: a
   test that hands its own inputs in and stays green while the production call site regresses.

## References

- `guidelines/UI.md` §8 — the tap-target rule for phone overlays (control never inside its container's
  tap target; ≥48 dp per dimension)
- `guidelines/Build.md` §6 — test constraints (JNI-stub classloader rule, fork budgets) and the geometry
  constraint this skill implements
- `.pi/skills/revert-check/SKILL.md` — one mutation per invariant, forced green re-run
- `.pi/skills/device-check/SKILL.md` — the on-device tap-consumer diagnosis that produces these findings
- `TODO.md` §122 (tap aimed at a control handled by its container), §138 (action clipped off screen),
  §72 (dimension in raw pixels), §111 (a case that proved nothing about the call site)
