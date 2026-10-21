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

**Re-landed 2026-10-21, both flavors.** The batch merge and its revert left this change out of `main`, so it
was re-applied and the two classes re-run per flavor rather than re-quoting the round-2 files: `:app:
testMobileDebugUnitTest` → `MapCanvasViewModelVehicleAnchorTest` `tests=16 failures=0`
(`green-reland-mobile-MapCanvasViewModelVehicleAnchorTest.xml`, `2026-10-21T18:29:42.119Z`) and
`FollowBlitCallSiteTest` `tests=2 failures=0` (`green-reland-mobile-FollowBlitCallSiteTest.xml`,
18:29:41.578Z); `:app:testAutomotiveDebugUnitTest` → the same two, `tests=16 failures=0` / `tests=2
failures=0` (`green-reland-automotive-MapCanvasViewModelVehicleAnchorTest.xml`,
`2026-10-21T18:30:08.889Z`; `green-reland-automotive-FollowBlitCallSiteTest.xml`, 18:30:08.272Z). The mobile
XML's `system-out` carries `followBlitAnchor presetFy=0.9 resolvedFy=0.7333 activeFy=0.9 seamRawY≈0
presetRawY=-320.0 seamClamped=false presetClamped=true`, the same geometry this file's round-2 run measured.

## Round 2 — the call-site mutation the seam's state parameter admits

The seam has no anchor parameter, but a caller can rebuild the state. The exact spelling quoted in the
round-1 review, `ui.copy(resolvedAnchor = ui.activeFollowAnchor)`, **does not compile** (the field is
`ResolvedAnchor`, the preset is `VehicleAnchorPosition`):

```
e: MapCanvasScreen.kt:826:54 Argument type mismatch: actual type is 'VehicleAnchorPosition',
  but 'ResolvedAnchor' was expected.
```

A compiling form does reintroduce the preset, and the call-site guard refuses it:

```diff
-                            ui,
+                            ui.copy(
+                                resolvedAnchor = ResolvedAnchor(
+                                    ui.activeFollowAnchor.fx, ui.activeFollowAnchor.fy
+                                )
+                            ), // REVERT-CHECK MUTATION
```

**Mutated run** — `./gradlew :app:testMobileDebugUnitTest --tests
"com.naviveylin.ui.map.FollowBlitCallSiteTest" -PforceTests --no-build-cache` → `2 tests completed,
1 failed`, `BUILD FAILED in 10s`; XML `tests=2 failures=1`, `2026-10-21T09:11:38.809Z`
(`red-revert-check-callsite-FollowBlitCallSiteTest.xml`): `followBlitCallSite seamCalls=1
inlineCalls=0 callSiteRawReads=2` — `the offset call site must not reconstruct the state with the raw
preset … expected:<0> but was:<2>`.

**Restore** — `grep -c 'REVERT-CHECK MUTATION' MapCanvasScreen.kt` → `0`; green re-run of both cases,
`2026-10-21T09:11:51.331Z` / `09:11:52.035Z`, `failures=0` (`green-round2-*.xml`).

## Round 2 — the count assertions exercised red on HEAD's call-site shape

With `MapCanvasScreen.kt` restored to HEAD's inline `FollowPrediction.displayOffsetPx(…,
ui.resolvedAnchor.fx, ui.resolvedAnchor.fy)` call (the seam file still present, so the file-existence
assertion passes), the guard's count assertions fail for the right reason: XML `tests=2 failures=1`,
`2026-10-21T09:11:05.548Z` (`red-head-callsite-FollowBlitCallSiteTest.xml`), `followBlitCallSite
seamCalls=0 inlineCalls=1 callSiteRawReads=0`, `expected:<1> but was:<0>` at the seam-call assertion.

