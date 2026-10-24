# Proposal — fix-map-renderer-test-file-size

## Why

`auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt` is **2 099 lines**, past the `source-size`
row's bound of 1 500 — item #300, class `improvement`, and the improvement is a non-defect: the row counts
lines per tracked file and files one item per path, and the item closes when that path drops under the bound.

**Root cause (one).** One file carries the whole `AutoMapRenderer` case set — its 82 JUnit cases *and* the
harness they share (`AutoMapRendererTest.kt:35`, the file's only class) — so the file's length is the sum of
every group's cases, and each group that lands there grows that sum. The bound is not in the file and is not
this change's to move: `tools/sensors/sensors.json:70` (`"threshold": 1500, "comparison": "greater-than"`),
owned by `guidelines/Backlog.md` §9, "One row watches a source unit's size", over the owner's recorded
decision of 2026-10-21 (1 500 lines per file, tests included).

**Red case — the number the delta must move, taken with the row's own measurement.** A non-defect has no
failing JUnit case to offer unless the build grows a second copy of the row, so the red case is the row's
probe (`evidence/red-probe-head.log`):

```
$ SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[]' bash tools/sensors/metrics/source-size.sh
{"path":"auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt","fingerprint":"60ebe0d1…","measurement":"2099","verdict":"violation","baseline":"","title":"the source file is past the size bound","claim":"auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt is 2099 lines, past the row's bound of 1500"}
```

On base `3cc62e15` with a clean tree, 2026-10-24T20:59Z. The condition list is empty on purpose and changes
nothing about the finding: `source-size.sh` emits a violation for every tracked file past the bound and reads
`SENSORS_CONDITIONS` only in its *clearing* pass, which the same probe on a still-oversized path shows
(identical `violation` line with and without a condition, `evidence/red-probe-head.log`'s second half). After the split the same probe prints `"measurement":"437","verdict":"clear"` under the same fingerprint (`evidence/green-probe.log`) — the row's `clear`
finding is what closes the item (`source-size.sh`, the "Every condition this row owns and does not report as
present has cleared" pass).

**The case roster is the second thing the delta promises to keep**, and it is checked as a number
(`evidence/case-roster.log`): the 82 public `fun`s of the old file against the union of the classes that
replace it — `diff` empty, so no case was added, renamed or dropped.

**Surface before the plan was fixed** (`grep -rn 'AutoMapRendererTest' openspec auto/src guidelines`):
`surface: 0 cases added, 0 rewritten, 0 fixtures realigned — 6 shipped spec `Case:` pointers keep resolving
because the class that carries them keeps its name, 1 spec mentions the class, 3 in-test comments name it and
are realigned`. The six `Case:` pointers are shipped scenarios of `auto-smooth-follow`
(`cornerOvershootStaysInBand`, `subPixelCorrectionIsImperceptible`, `backwardFixDoesNotHaltCarDisplay`,
`curveSequenceStaysInBand`, `sharedCorrectionBoundAppliesToCar`, `staleBaseDoesNotStopCarDisplay`) and the
mention is `ci-unit-test-jni`'s scenario about `:auto:testDebugUnitTest` running the class. `grep -rn
'AutoMapRendererTest' openspec/specs | wc -l` = 7, and no file under `tools/` names the class
(`tools/declared-cases.sh` derives its selection from the change's artifacts and the diff, so the new class
names enter it by being named here).

## What Changes

- **`auto/src/test/java/com/naviveylin/auto/AutoMapRendererTestSupport.kt`** (new, 49 lines) — an abstract
  harness: the `RendererTestRule` (teardown), the fake client and the renderer, the `@Before` that builds
  them, `fixLat`/`fixLon`, and `mockSurface()`. `client`, `renderer`, `fixLat`, `fixLon` and `mockSurface`
  become `protected`; nothing else about them changes.
- **`AutoMapRendererTest.kt`** (437 lines) — keeps the class name and the prediction band: the extrapolation
  gate, the displayed position between fixes, and the six `auto-smooth-follow` cases plus the two helpers
  those need (`eastMeters`, `metersBetween`). 13 cases.
- **`AutoMapRendererLifecycleTest.kt`** (new, 275 lines) — surfaces, shutdown, the marker and favourite
  setters, single-step pinch zoom, the coordinate-free render diagnostic lines and the route-without-geometry
  entry. 20 cases.
- **`AutoMapRendererBlitTest.kt`** (new, 702 lines) — the overrun bitmap, full render versus blit,
  invalidation, the blit offset a marker and a pane band ride, and the frame a display commit lands on.
  23 cases.
- **`AutoMapRendererFollowAnchorTest.kt`** (new, 364 lines) — follow re-engage, the resolved anchor against
  host pane insets, and the locale-independent diagnostic numbers. 11 cases.
- **`AutoMapRendererZoomWalkTest.kt`** (new, 386 lines) — the auto-zoom walk and its three helpers. 15 cases.
- **`RendererTestRuleTest.kt`, `AutoMapRendererLoopConfinementTest.kt`,
  `com/framstag/libosmscout/client/FakeAutoRenderClient.kt`** — three comments that named
  `AutoMapRendererTest` for a case or a call that now lives in another of the five classes; each names the
  class that holds it now. `RendererTestRuleTest.kt`'s "the six in-test `renderer.shutdown()` calls" becomes
  "the in-test calls in the `AutoMapRenderer*` case classes" — the count it claimed was already five on HEAD
  and the five are now spread over three files.

**Additive, no breaking change.** No production Kotlin, no C++, no manifest, no resource, no Gradle logic, no
spec text and no guideline: the delta is test sources and three comments. Rollback path: revert this change's
commit — the six files disappear, the 2 099-line file returns, and the red probe's `violation` at
`measurement=2099` reproduces.

**Not a native/JNI change**: no submodule patch and no `:osmscout-client-java` override; the native tree is
untouched.

**Scope**: phone, foldable, tablet, Android Auto and AAOS alike — the `:auto` unit-test set only, no runtime
behaviour on any surface.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none) — no requirement changes. The delta is the mechanical split of one test file; the rule it satisfies
is the `source-size` row's, which `openspec/specs/backlog-sensors/spec.md` already states and which this change
does not alter. The cases assert what they asserted before, under the names they already carried, so
`auto-smooth-follow`'s six `Case:` pointers and `ci-unit-test-jni`'s mention stay true and need no MODIFIED
block.

## Impact

Test sources (`:auto`) — the whole delta
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTestSupport.kt` — new, the shared harness
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt` — from 2 099 to 437 lines, 13 cases
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererLifecycleTest.kt` — new, 20 cases
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererBlitTest.kt` — new, 23 cases
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererFollowAnchorTest.kt` — new, 11 cases
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererZoomWalkTest.kt` — new, 15 cases
- `auto/src/test/java/com/naviveylin/auto/RendererTestRuleTest.kt` — one comment
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererLoopConfinementTest.kt` — one comment
- `auto/src/test/java/com/framstag/libosmscout/client/FakeAutoRenderClient.kt` — one KDoc line

Specs and guidelines — read, not edited
- `openspec/specs/backlog-sensors/spec.md` (the row's doctrine), `openspec/specs/auto-smooth-follow/spec.md`
  (six `Case:` pointers, unchanged), `openspec/specs/ci-unit-test-jni/spec.md` (the class mention, unchanged),
  `guidelines/Backlog.md` §9 (the row), `guidelines/Build.md` §2a (the scope block) and §6a–§6h (test
  constraints), `guidelines/Design.md`'s "Testing" section

Verification (each task's own step, `tasks.md`)
- `./gradlew :auto:testDebugUnitTest --tests 'com.naviveylin.auto.AutoMapRenderer*'` — the 82 cases in the
  five classes the split produced (plus the 25 the same filter matches in the five sibling
  `AutoMapRenderer*Test` classes), five result XMLs, none failing
- the row's probe before and after, and the case-roster `diff`
- `tools/loop/gate.sh` once, because the profile counts `*/src/*` as a compiled path
