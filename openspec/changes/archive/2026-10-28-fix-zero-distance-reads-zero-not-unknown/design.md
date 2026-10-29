# Design

## Context

`NavigationNotificationContentFormatter` is the one content source for the ongoing notification and the car
hint (`guidelines/UI.md` §1). Both read the same private `formatDistanceCompact`, whose only unknown sentinel
is the string `"--"` and whose guard is `meters.isNaN() || meters <= 0.0`
(`NavigationNotificationContent.kt:253`). A known zero therefore collapses into the unknown branch. The
phone stats line also substitutes `0.0` when no step exists, so it currently relies on the same collapse to
render `--` for a missing step.

## Root Cause

The formatter's parameter type `Double` cannot express "unknown", so the implementation spends one numeric
value — zero — as the unknown sentinel. The live step model already distinguishes the two facts: a step with
`remainingDistance = 0.0` is zero, `Double.NaN` is unknown, and the absence of a step is unknown. The
formatter erased that distinction.

## Single Fix

`formatDistanceCompact` takes `Double?`. `null` and `NaN` return `--`; every other finite value is clamped to
at least `0.0` and formatted with the existing `formatDistanceNumber` and unit suffix, so zero reads `0 m`.
The two call sites pass the nullable step distance: `carHint` keeps its existing fallback to
`state.remainingDistance` when no live step exists, and `navigationContent` drops the `?: 0.0` substitution
so a missing step remains unknown.

## Alternatives Eliminated

- **Keep `<= 0.0` as the unknown branch.** This is the reported mechanism; the red run
  (`evidence/red-zero-distance.xml`) shows it rendering `in --` for a real step with `remainingMeters = 0.0`.
- **Render a localized "now".** The stats template is `"in $distanceToTurn · …"`, so the same string would
  produce `in now`; using it only on the car side would break the parity requirement, and changing the
  template would need a second wording plus new resources in both locales. `0 m` is the issue's other
  accepted form and needs no new string.
- **Special-case zero at each call site.** Both surfaces call the same helper and the defect is inside it;
  two branches would be two answers to the same question and would drift.

## Blast Radius

One private formatter and its two call sites in `NavigationNotificationContent.kt`, one test case in
`NavigationNotificationContentFormatterTest.kt`, and the English resolver form of `nav_hint_distance_eta`
that the car-hint assertion reads. The on-screen next-turn overlay (`NextTurnOverlay.kt:95`) and the car
session surface (`CarSessionSurface.kt:134`) read the distance through the `:core` extension `remainingMeters`,
which still maps an unpublished progress to `0.0`; they are untouched, so the zero/unknown distinction stops at
the notification and the car hint and extending it to those two surfaces is a follow-up.

## Rollback

Restore the `meters <= 0.0` guard and the `?: 0.0` argument at `navigationContent`. The new case fails again
with `in --` and the car hint returns `-- · <arrival>`.

## Verification

The red-on-HEAD case (`tests=1 failures=1`, `2026-10-28T18:08:55.848Z`) turns green, with its unknown half
asserted on both surfaces for an absent and for a `NaN` distance and its clamp asserted for a negative one;
the neighbouring `CarHintContentTest` still passes; one revert-check mutation (`|| meters <= 0.0`) makes the
case fail for the expected reason, then the restored tree runs green. The project gate runs once for the
shared app code.
