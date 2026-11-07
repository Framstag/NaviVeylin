# Revert-check — #739, one mutation per new invariant (2026-11-07)

Tree: `.pi/worktrees/739` at the merge of `main` (`1ba849fb`); base `1ba849fb`, change
`car-reroute-without-losing-the-map`.

Method: mutate the production line the invariant lives on, run the case that names it, require the case
to fail, restore the file from git and require the set green again. Run by
`.pi/revert-check-739.sh` in that tree plus the two corrected mutations below.

| # | invariant | mutation | case | result |
|---|---|---|---|---|
| 1 | a reroute while navigating pushes no screen | `needsNoticeArm`: drop `!navigating` from the predicate | `CalculationNoticeDecisionTest` | **red on mutation** — 9 tests, 1 failed; restore → green |
| 2 | the strip's rect lies inside `stableArea` and clears the indicator column | `CalculationStrip.geometry`: `val inside = !stableBounds.isEmpty()` → `val inside = false` | `CalculationStripTest` | **red on mutation** — 11 tests, 1 failed; restore → green |
| 3 | the car states that the vehicle is off route | `NavigationTemplateMapper.routingInfoFromState`: `if (state.isOffRoute)` → `if (false)` | `NavigationTemplateMapperTest` | **red on mutation** — 76 tests, 2 failed; restore → green |
| 4 | the departure is spoken once | `OffRouteAnnouncementGate.onDeparture`: `if (announced) return false` → `if (false) return false` | `OffRouteAnnouncementTest` | **red on mutation** — 6 tests, 3 failed; restore → green |
| 5 | the error pane's dwell is ten seconds | `NavigationSession.ERROR_DISPLAY_MS`: `10_000L` → `4_000L` | `CalculationNoticeDwellTest` | **red on mutation** — 4 tests, 1 failed; restore → green |

A first pass mutated the *call site* for 1 and 4 (`navigating = navState.isNavigating` in the arm call,
`onDeparture(_state.value.isNavigating, …)` in the engine) and both stayed green: the case that holds each
rule reads the seam (`needsNoticeArm`, `OffRouteAnnouncementGate`) rather than the call. The seams are the
right targets — `NavigationSession` cannot be constructed in Robolectric (`needsNoticeArm`'s own comment)
— and the call sites' wiring is then covered by the device log below, not by a unit case.

## Device log, same build (AAOS AVD `emulator-5554`, Android 13, `sdk_gcar_dd_x86_64`)

Navigation to a POI started at 11:53:00 (`Diag/HOST: navigationStarted`, trip remaining 645 m). A drive
off the planned route at ~11 m/s east produced the reroute this change is about:

```
11:55:08.469  Diag/HOST: strip pct=null shown=1
11:55:08.535  Diag/HOST: strip pct=0 shown=1
11:55:08.703  Diag/ROUTE: calc done: source=reroute duration=242ms percents=1 outcome=ok reachedPct=0 reason=- nearestRoadM=-1
11:55:08.715  Diag/HOST: strip hidden
```

and `grep -c 'Push RouteCalculating'` over the same buffer: **0** — the reroute pushed no screen, the
navigation view stayed current, and the strip drew the wait and cleared itself when the route was ready.

What the buffer does **not** carry: a screenshot inside the 242 ms window (frames were shot, none landed
in it), and no forced *failure* of the calculation while navigating. Tasks 7.2 and 7.3 stay open for that.
