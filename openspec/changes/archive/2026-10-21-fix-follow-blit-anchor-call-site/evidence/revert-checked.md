# Revert-check — one mutation, two cases that must fail

Skill: `revert-check` (one mutation per invariant, production code only, every case the guard protects).

**Mutation** (`app/src/main/java/com/naviveylin/ui/map/FollowBlitRules.kt`, the seam's one anchor read):

```diff
-        val anchor = ui.resolvedAnchor
+        val anchor = ui.activeFollowAnchor // REVERT-CHECK MUTATION
```

**Mutated run** — `./gradlew :app:testMobileDebugUnitTest --tests
"com.naviveylin.ui.map.FollowBlitCallSiteTest" --tests
"com.naviveylin.ui.map.MapCanvasViewModelVehicleAnchorTest" -PforceTests --no-build-cache` →
`18 tests completed, 2 failed`, `BUILD FAILED in 11s`:

- `FollowBlitCallSiteTest > the follow offset path cannot read the raw preset FAILED` —
  `the follow offset path must not read the raw preset: it differs from the resolved anchor whenever an
  overlay moves it, and the blit would sit outside the overrun margin expected:<0> but was:<1>`
  (`red-revert-check-FollowBlitCallSiteTest.xml`, `tests=2 failures=1`, `2026-10-21T09:02:16.148Z`)
- `MapCanvasViewModelVehicleAnchorTest > followBlitOffsetTakesTheResolvedAnchorNotTheRawPreset FAILED` —
  `the seam must use the anchor the follow render target rendered with expected:<5.968558980384842E-11>
  but was:<-319.9999999999404>` (`red-revert-check-MapCanvasViewModelVehicleAnchorTest.xml`,
  `tests=16 failures=1`, `2026-10-21T09:02:16.700Z`) — 320 px is `(0.9 − 0.7333)·1920`, the anchor delta the
  item names.

**Restore** — `grep -c 'REVERT-CHECK MUTATION' FollowBlitRules.kt` → `0`.

**Green re-run, forced, after the restore** — same command →
`BUILD SUCCESSFUL in 12s`, `FollowBlitCallSiteTest` `tests=2 failures=0`
(`green-FollowBlitCallSiteTest.xml`, `2026-10-21T09:02:31.435Z`) and
`MapCanvasViewModelVehicleAnchorTest` `tests=16 failures=0` (`green-MapCanvasViewModelVehicleAnchorTest.xml`,
`2026-10-21T09:02:32.026Z`), both timestamps after the restore. `FollowAnchorFramingTest` (the geometry the
seam reuses) is green: `tests=10 failures=0` (`green-FollowAnchorFramingTest.xml`,
`2026-10-21T09:01:36.429Z`).
