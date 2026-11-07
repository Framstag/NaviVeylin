# Proposal — fix-auto-map-renderer-file-size

## Why

`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` is **2 307 lines**, past the `source-size` row's
bound of 1 500 — item #299, class `improvement`, and the improvement is a non-defect: the row counts lines per
tracked file and files one item per path, and the item closes when that path drops under the bound
(`tools/sensors/sensors.json:70`, `"threshold": 1500, "comparison": "greater-than"`, owned by
`guidelines/Backlog.md` §9 "One row watches a source unit's size", over the owner's recorded decision of
2026-10-21: 1 500 lines per file, tests included).

**Root cause (one).** One file carries the whole car-surface renderer class — its thirty-odd state fields, the
surface lifecycle, the three loops, the extrapolation and zoom-walk motion, the full render and blit pipeline,
the two surface-drawn markers, the follow anchor and the overlay setters — so the file's length is the sum of
every topic's code, and each topic that grows there grows that sum. The class is the file's only top-level
declaration (`AutoMapRenderer.kt:56`, opened at `AutoMapRenderer.kt:73`), which is why the two are the same
number.

**Red case — the number the delta must move, taken with the row's own measurement.** A non-defect has no
failing JUnit case to offer unless the build grows a second copy of the row, so the red case is the row's probe
(`evidence/red-probe-head.log`):

```
$ SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[{"state":"OPEN","condition":{"path":"auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt"}}]' bash tools/sensors/metrics/source-size.sh
{"path":"auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt","fingerprint":"499ec362fe8f67b31894593536b12b284388b8e033fefd16dcf6a156593790ac","measurement":"2307","verdict":"violation","baseline":"","title":"the source file is past the size bound","claim":"auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt is 2307 lines, past the row's bound of 1500"}
```

On base `d846c741` with a clean tree, 2026-10-25T18:39:28Z (the probe's own `git status --porcelain` line
reports 0 changed paths). After the split the same probe prints `"measurement":"1009","verdict":"clear"` under
the same fingerprint (`evidence/green-probe.log`) — the row's `clear` finding is what closes the item
(`tools/sensors/metrics/source-size.sh`, the clearing pass over the open conditions).

**The two invariants the split keeps, and how they are checked.** `evidence/member-roster.log`: 79 member
functions before, 79 after (`diff` empty). `evidence/line-accounting.log`: **1 164 code lines before and 1 164
after**, the multiset identical once the split's own transformations are normalised away (the extension
receiver prefix, the `private` → `internal` visibility of the class's own members, and one qualified nested
type). Nothing was retyped.

**The owning requirement.** `backlog-sensors` — "One row watches a source unit's size"
(`openspec/specs/backlog-sensors/spec.md:412`) states the row and the bound; `unit-test-suite-runtime` states
that every test class of the module executes, which is what the 880-case focused run keeps whole. The guideline
sections the delta answers to are `guidelines/Design.md` §8 "Android Auto & cross-variant" (one surface owner
per session, nothing native on the host thread) and `guidelines/MapRendering.md` §14 "Android Auto renderer":
the split moves no ownership and no thread, it only moves declarations. `guidelines/Build.md` §2a is the
iteration protocol this change followed, §4 its warning rule, §6a the classloader/JNI-stub rule the affected
cases obey.

**Surface before the plan was fixed.** `grep -rl 'AutoMapRenderer' auto/src app/src core/src` → 35 files;
`grep -rn 'AutoMapRenderer' openspec/specs | wc -l` → 8 (the six `Case:` pointers of `auto-smooth-follow` name
the *test* class `AutoMapRendererTest`, `build-test-gate` and `ci-unit-test-jni` name the class itself, none
names a file); `guidelines` → 13 mentions, all of them `AutoMapRenderer.<member>` (a member name, which an
extension function keeps); `tools` → 0. No spec, guideline or tool cites a **line** in the file
(`grep -rn 'AutoMapRenderer\.kt' guidelines openspec/specs tools` → 1 hit, the comment realigned below).

surface: 0 cases added, 0 cases rewritten, 1 comment realigned — `grep -rl 'AutoMapRenderer' auto/src app/src core/src`
(35 files), `grep -rn 'AutoMapRenderer' openspec/specs` (8) and `guidelines` (13), `tools` (0); the one comment
realigned is `core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt:8`.

## What Changes

The file becomes seven, each under the bound, and no code line changes — the class keeps its name, its
constructor, its signatures and the members a test double intercepts:

- **`AutoMapRenderer.kt`** (2 307 → **1 009 lines**) — the class itself: the constructor's collaborators, every
  state field, the `init` blocks, the companion object, the nested `ViewportState`, the surface lifecycle
  (`onSurfaceCreated`, `onSurfaceDestroyed`, `detachSurface`, `pause`, `resume`, `shutdown`), the overlay and
  viewport setters, follow-mode commands, render requests and invalidation, and the test seams the cases and
  the mocks call. The file-foot `renderLogMessage` stays where it was.
- **`AutoMapRendererLoops.kt`** (new, 191 lines) — the three loop launchers (`startRenderLoop`,
  `startExtrapolationLoop`, `startZoomWalkLoop`) and the iteration hooks their supervisor calls.
- **`AutoMapRendererMotion.kt`** (new, 275 lines) — the extrapolation tick, the displayed magnification, the
  zoom walk (`advanceZoomWalk`, `commitZoom`) and the gesture zoom steps.
- **`AutoMapRendererFollow.kt`** (new, 143 lines) — the follow diagnostic line, the vehicle anchor and its
  resolution against host chrome and insets, and the displayed-frame readers.
- **`AutoMapRendererRender.kt`** (new, 529 lines) — `renderFrame`, `fullRender`, `drawToSurface`,
  `blitToSurface`, the overrun clear and the surface-failure reports.
- **`AutoMapRendererOverlays.kt`** (new, 95 lines) — the screen-space readers an overlay draw uses and the
  viewport observation the screen reads.
- **`AutoMapRendererMarkers.kt`** (new, 323 lines) — the two surface-drawn markers and the draw-object cache
  (formerly the nested `AutoMapRenderer.MarkerDrawObjects`, now a top-level `internal class` in the same
  package; nothing outside the file named it — `grep -rn MarkerDrawObjects` outside it → 0).

**The one visibility change the split forces.** A member function may see a `private` field, an extension
function in another file may not, so the class's own fields become `internal` — module-visible, still invisible
outside `:auto`. No signature changes: a `public fun` stays `public`, an `internal fun` stays `internal`.

**The members that stay in the class, and why.** MockK intercepts member dispatch only: an extension function
is a static call, so a member a test double stubs or verifies would silently stop being stubbed. The probe in
`evidence/member-dispatch-probe.log` moves one of them (`committedViewportCenter`) into a topic file and
`CarMapViewportTest` fails inside the mock; moving the `RendererGate` replay setters the same way failed the
same way during the first split attempt. The 21 members the tests intercept
(`setGpsMarker`, `setFavoriteLocations`, `setDestinationMarker`, `setViewport`, `setFollowAnchor`,
`setHostPaneRtl`, `setHostTopInset`, `setHostBottomInset`, `reCenter`, `reengageFollow`, `setDarkPresentation`,
`pause`, `resume`, `shutdown`, `onSurfaceCreated`, `updateProjectionDpi`, `invalidateStyle`, `invalidateData`,
`committedViewportCenter`, `markerViewport`, `activeBackgroundJobCount`) therefore stay member functions, and
`design.md` records the list with the case that intercepts each.

**Additive, no breaking change.** No behaviour changes, no test changes, no spec text, no guideline, no build
logic, no manifest, no resource. Rollback path: revert this change's commit — the six new files disappear, the
2 307-line file returns, and the red probe's `violation` at `measurement=2307` reproduces.

**Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override, no CMake input.

**Scope**: `:auto` is compiled into both distribution flavors, so both are built and both test sets run.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none) — no requirement changes, so `.openspec.yaml` declares `skip_specs: true` (see `design.md`, "Why no
delta"). The rule this change satisfies is the `source-size` row's, which
`openspec/specs/backlog-sensors/spec.md` already states and which this change does not alter: the bound, the
unit, the scope and the fingerprint are untouched, and the finding that closes the item is the row's own
clearing pass.

## Impact

Production sources (`:auto`)
- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — from 2 307 to 1 009 lines
- `AutoMapRendererLoops.kt`, `AutoMapRendererMotion.kt`, `AutoMapRendererFollow.kt`,
  `AutoMapRendererRender.kt`, `AutoMapRendererOverlays.kt`, `AutoMapRendererMarkers.kt` — new, 191 / 275 / 143
  / 529 / 95 / 323 lines

Production sources (`:core`)
- `core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt` — one comment line: the Android Auto
  consumer it names is now `AutoMapRendererMarkers.kt`

Specs and guidelines — read, not edited
- `openspec/specs/backlog-sensors/spec.md` (the row's doctrine), `openspec/specs/unit-test-suite-runtime/spec.md`
  (every test class executes), `guidelines/Backlog.md` §9, `guidelines/Design.md` §8,
  `guidelines/MapRendering.md` §14, `guidelines/Build.md` §2a, §4, §6a

Verification (each task's own step, `tasks.md`)
- the row's probe before and after (`evidence/red-probe-head.log`, `evidence/green-probe.log`)
- the member roster and the code-line accounting (`evidence/member-roster.log`, `evidence/line-accounting.log`)
- the member-dispatch probe that keeps the MockK seams (`evidence/member-dispatch-probe.log`)
- `./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests --tests 'com.naviveylin.auto.*'` — 880 cases
  in 91 XMLs, none failing (`evidence/focused-tally.log`)
- `./tools/loop/gate.sh` once, because the profile counts `*/src/*` as a compiled path
