# Design — fix-auto-map-renderer-file-size

## Root cause (one)

`AutoMapRenderer.kt:56` declares the file's only class, and that class carries every topic of the car-surface
renderer — state, surface lifecycle, loops, extrapolation and zoom-walk motion, the render/blit pipeline, the
markers, the follow anchor, the overlay setters — so the file's 2 307 lines are the sum of every topic that
ever landed there. Nothing else in the tree adds a line to it: the bound is per file
(`tools/sensors/metrics/source-size.sh` counts `git ls-files -z '*.kt' '*.java'`), so a file that carries every
topic is over it exactly when the topics collectively are.

## The single fix

Split by topic into six files beside the class, and move the topic members out as **extension functions** on
`AutoMapRenderer` — the shape the two earlier file-size changes in this tree used
(`2026-10-24-fix-map-canvas-view-model-file-size`, `2026-10-24-fix-map-renderer-test-file-size`) and the one
that changes no statement: an extension function has the same body, the same name and the same receiver, so
nothing is retyped and no call site changes.

| file | what it holds | lines |
|---|---|---|
| `AutoMapRenderer.kt` | the class: constructor, fields, `init` blocks, companion, nested `ViewportState`, surface lifecycle, overlay/viewport setters, follow commands, render requests, invalidation, the MockK-intercepted members; plus the file-foot `renderLogMessage` | 1 009 |
| `AutoMapRendererLoops.kt` | `startRenderLoop`, `startExtrapolationLoop`, `startZoomWalkLoop`, `frameIteration`, `displayTickIteration`, `zoomWalkIteration`, `loopFaultStreak`, `maybeFailForTest` | 191 |
| `AutoMapRendererMotion.kt` | `extrapolationTick`, `extrapolationGateActive`, `advanceDisplayedMagnification`, `advanceZoomWalk`, `commitZoom`, `displayedScale`, `displayedMagnification`, `roundOffset`, `fractionalZoom`, `zoomStep` | 275 |
| `AutoMapRendererFollow.kt` | `followDiagnosticLine`, `followAnchor`, `anchorCenterFor`, `resolvedFollowAnchor`, `hostTopInset`, `hostPaneRtl`, `displayedLat/Lon/Mag/Angle` | 143 |
| `AutoMapRendererRender.kt` | `renderFrame`, `fullRender`, `drawToSurface`, `blitToSurface`, `clearOverrunBuffer`, `isCurrentSurface`, `isSurfaceFailed`, `reportFailureIfCurrent`, `reportSurfaceFailure` | 529 |
| `AutoMapRendererOverlays.kt` | `markerScreenPosition`, `markerPosition`, `blitOffset`, `setBlitOffsetForTest`, `emitViewportState`, `overrunSize` | 95 |
| `AutoMapRendererMarkers.kt` | `drawGpsMarker`, `drawDestinationMarker`, the draw-object cache (`MarkerDrawObjects`, `markerDrawObjects`, the two test readers) | 323 |

**The one visibility change the split forces.** A member function may see a `private` field, an extension
function in another file may not, so the class's fields become `internal` — module-visible, invisible outside
`:auto`. The companion's shared members (`TAG`, `surfaceLock`, `RENDER_DEBOUNCE_MS`, `MAX_DEST_LABEL_CHARS`,
and the tunables the motion code reads) become `internal` for the same reason and are imported as
`AutoMapRenderer.Companion.<name>` where a topic file reads one. `nextRendererId` and the default viewport
constants stay `private`: no extension function reads them.

**The constraint that decides which members stay.** MockK intercepts *member dispatch*: a `mockk<AutoMapRenderer>`
or `spyk(...)` sees `every { renderer.setViewport(...) }` only while `setViewport` is a member — as an extension
the call is static, the body runs on the mock, and the assertion that reads the mock's internals throws. The
probe (`evidence/member-dispatch-probe.log`) moves `committedViewportCenter` — which the split keeps a member —
into `AutoMapRendererOverlays.kt` and `CarMapViewportTest` fails with
`io.mockk.MockKException: no answer found for AutoMapRenderer(#1).getOverrunLat$auto()`; the first split attempt
moved the gate setters the same way and the focused run reported the same class of failure for the verifies on
`setViewport`, `setGpsMarker`, `setFavoriteLocations` and `setDarkPresentation`. So these 21 members stay member
functions:

| member | the case that intercepts it |
|---|---|
| `setGpsMarker`, `setFavoriteLocations`, `setDestinationMarker`, `setFollowAnchor`, `setHostPaneRtl`, `setHostTopInset`, `setHostBottomInset`, `setViewport`, `reCenter`, `setDarkPresentation`, `invalidateStyle`, `invalidateData`, `resume`, `pause`, `shutdown`, `onSurfaceCreated`, `updateProjectionDpi` | `RendererGateTest` — the `RendererGate` replay slots, verified with `verify`/`verifyOrder` |
| `reengageFollow`, `setViewport` | `FreeDrivingScreenTest.commitAutoZoomIsTransitionEligibleAndReengagesFollow` |
| `committedViewportCenter`, `markerViewport` | `CarMapViewportTest` (both cases), `DetailsScreenTest.aStoppedMapPublishesTheLastDrawnFrameCenter` |
| `activeBackgroundJobCount` | `RendererTestRuleTest.teardownFailsWhenARendererKeepsRunningBackgroundWork` |

That is a documented consequence, not a gap in the split: the class is 1 009 lines, 43 % of what it was, and
every topic that is *not* a gate entry point or a mock seam moved out.

## Alternatives the evidence eliminated

1. **Retune or baseline the row** (`tools/sensors/sensors.json:70`, `baseline`): impossible as a fix — the
   bound is the owner's recorded decision of 2026-10-21 (1 500 lines, file level, tests included,
   `guidelines/Backlog.md` §9), and a baseline would make the row stop reporting the condition it exists to
   report. The item's own lifecycle is "the path is the identity, so an item closes when that file drops under
   the bound".
2. **A guard test that asserts the file's line count**: impossible — it duplicates the row, whose scope is
   every tracked `.kt`/`.java` file, and it would redden the build for the one other file the same probe finds
   past the bound and that is another item's work (`MapCanvasScreen.kt`, 3 384 lines,
   `evidence/red-probe-head.log`). It also gives the same file two owners.
3. **Turning the class into a facade over topic helper classes** (`class RenderPipeline(owner: AutoMapRenderer)`):
   impossible at this size — every helper would need a getter for each field it reads (the class has ~40
   `@Volatile` fields), so the facade plus the accessors would be larger than the file being split, and it
   changes object identity on the render thread, which `guidelines/Design.md` §8 and
   `guidelines/MapRendering.md` §14 pin.
4. **Moving the gate setters out too and rewriting the five MockK test files**: rejected — a split that changes
   880-case test wiring is not a mechanical move, and the seam (`RendererGate` replay is verified through the
   renderer's own members) is the contract those cases exist to pin. The probe shows what the rewrite would be
   undoing.
5. **Splitting by `@Nested` inner classes**: impossible — the measurement is lines per file, and a nested class
   does not remove a line from the file.

## Blast radius

- 6 files created, 2 files rewritten (the class, and one comment in `:core`), 0 tests, 0 specs, 0 guidelines,
  0 build logic.
- Both flavours compile `:auto`; the focused run is `:auto`'s whole unit suite, the gate adds `:app`, `:core`
  and `:osmscout-client-java` plus the shell suites.
- Threading, ownership and lifecycle are untouched: the extension functions run on the same threads as the
  members they replace (the loops' dispatcher, the render thread, the screen's main thread), and no
  `synchronized` block, `@Volatile` field or coroutine launch moved to another file's *execution* — only its
  declaration.

## Why no delta

No requirement changes: the `source-size` row's bound, unit, scope and fingerprint are untouched, the class's
public surface is unchanged, and the behaviour is identical line for line. `.openspec.yaml` declares
`skip_specs: true`; the requirement the change satisfies —
`openspec/specs/backlog-sensors/spec.md:412`, "A source file's size is a declared check" — already exists and
is what the red probe and the green probe are read against.
