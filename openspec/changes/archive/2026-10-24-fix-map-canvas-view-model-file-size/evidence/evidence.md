# Evidence — fix-map-canvas-view-model-file-size (item #297)

Every number below was taken at `$(git rev-parse --short HEAD)` = `c5b58332` plus this change's uncommitted
tree, on 2026-10-24, and each names the artifact beside this file that carries it.

## 1. The row's measurement — red on HEAD, clear after the split

| | command | finding |
|---|---|---|
| HEAD (the 5 096-line file restored in place, clean `git status`, 2026-10-24T21:42:03Z) | `bash evidence/source-size-probe.sh .` | `"measurement":"5096","verdict":"violation"` — `red-probe-head.log` |
| after the split (2026-10-24T21:45:00Z, 9 changed paths) | the same probe | `"measurement":"842","verdict":"clear"` — `green-probe.log` |

The fingerprint is the entry's own in both: `44a78d48ed1eb15b6501fe7ffdb328e516e279e52405d64844b16d65d3cb89ac`,
so the clearing finding is the finding that closes the item (`tools/sensors/metrics/source-size.sh`, the
clearing pass over the open conditions). `source-size-probe.sh` is the probe as it was run; `red-probe-head.log`
also reports the probe's own `git status --porcelain | wc -l` = 0, i.e. the red number is HEAD's tree.

The bound is not this change's: `tools/sensors/sensors.json:70` (`"threshold": 1500`,
`"comparison": "greater-than"`) under `guidelines/Backlog.md` §9, over the owner's recorded decision of
2026-10-21.

## 2. The member roster — the invariant the split keeps

`member-roster.sh` compares HEAD's single class with the six files the split produced:
**147 member functions before, 147 extension functions after**, `diff` empty — `member-roster.log`.

The 147 exclude two things, both named on the log's own lines: `onCleared` (an `override`, so it must stay a
member) and the **15 `wire*` functions** that the `init` block's statement groups became — statements have no
name, so they cannot appear in a before/after name comparison. The only name the roster maps across is
`poiFitMagnification` → `poiEntryFitMagnification`, the one forced rename (`design.md`).

## 3. The statement accounting — what makes the move a move

`line-accounting.sh` compares the old file's code lines with the new files' as sorted multisets, after
normalising away only the split's own transformations (declaration prefix, property/constructor visibility,
a qualified companion member, an explicit `this` label, the two renamed names): **2 514 statements before,
2 514 after, `diff` empty** — `line-accounting.log`. Nothing was retyped, dropped or added.

Comment and KDoc lines are outside the comparison, and so are brace-only lines (the 15 `wire*` wrappers); the
log says so on its own lines. A lost brace cannot compile, which §5's build proves.

The eight files together are 5 334 lines against the old file's 5 096 (+238, +4.7 %): the difference is the
15 `wire*` declarations and their one-paragraph KDoc, the 15 calls that replace the `init` block's statement
groups, and the blank line each file's section separators add. No statement was added.

## 4. The affected cases

`./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly -PforceTests --tests 'com.naviveylin.ui.map.*'
--tests 'com.naviveylin.di.CrossSurfaceSearchOrderTest'` — `BUILD SUCCESSFUL in 1m 11s`, **838 cases in 115
result XMLs, `failures=0 errors=0`** (`focused-tally.log`, one row per XML, read from the run's own files).

Eight XMLs are carried whole, one per seam the split created (`focused-TEST-*.xml`):

| class | tests | failures | errors | seam |
|---|---|---|---|---|
| `MapCanvasViewModelFollowModeTest` | 24 | 0 | 0 | `…Follow.kt`, `wireFollowMode` |
| `MapCanvasViewModelRouteFitTest` | 21 | 0 | 0 | `…Route.kt` |
| `MapCanvasViewModelDarkModeTest` | 9 | 0 | 0 | `…Settings.kt`, `wireDarkPresentation` |
| `MapCanvasViewModelFixQualityTest` | 9 | 0 | 0 | `…Follow.kt`, `wireFixQuality` |
| `MapCanvasViewModelCarSessionTest` | 8 | 0 | 0 | `…Camera.kt`, `wireCarSession` |
| `MapCanvasViewModelFreeTextSearchTest` | 6 | 0 | 0 | `…Search.kt`, `wirePlaceSearch` |
| `MapCanvasViewModelInitThreadingTest` | 4 | 0 | 0 | `…MapInit.kt` |
| `CrossSurfaceSearchOrderTest` | 1 | 0 | 0 | the one out-of-package call site (the two imports) |

Quoted from one of them, `focused-TEST-com.naviveylin.ui.map.MapCanvasViewModelFollowModeTest.xml`:
`tests="24" skipped="0" failures="0" errors="0"`, `timestamp="2026-10-24T21:45:58.715Z"`.

## 5. The gate

`./tools/loop/gate.sh`, run once from the change root. Its summary line — the whole of it, with every suite
and its wall clock — is quoted verbatim in `gate-summary.log`; the number it aggregates is
`tests=403 failures=0` errors=0, **not retained** as one XML because 403 is the sum of the four tallies
below and no single suite reports it. Each of those four is carried by its own `gate-*.xml` beside this file.

Its tallied suites, each beside this file as `gate-<name>.xml`:

| suite | tally |
|---|---|
| `tools/loop-selftest.sh --from-git db888dd8…` | `tests=218 failures=0 errors=0` (980 s) |
| `bash tools/loop-mutations.sh --check` | `tests=145 failures=0 errors=0` |
| `tools/loop-mutations-selftest.sh` | `tests=34 failures=0 errors=0` (134 s) |
| `tools/check-openspec-hygiene-selftest.sh` | `tests=6 failures=0 errors=0` |

`gate-gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` carries `tests="0"`: Gradle prints no
JUnit summary line for the whole suite, so the gate records the run and its 245 s wall clock instead of a
count. The count is the run's own result XMLs, tallied after the gate by `gradle-suite-tally.sh`
(`gradle-suite-tally.log`): **5 792 cases in 738 XMLs, 0 failures, 0 errors**, per module and variant —

| result set | XMLs | cases | failures | errors |
|---|---|---|---|---|
| `:app` mobile debug unit test | 289 | 2 109 | 0 | 0 |
| `:app` automotive debug unit test | 289 | 2 109 | 0 | 0 |
| `:auto` | 91 | 880 | 0 | 0 |
| `:core` | 61 | 646 | 0 | 0 |
| `:osmscout-client-java` | 8 | 48 | 0 | 0 |

Three of the gate run's own XMLs are carried beside this file, so the gate's Gradle half is not only a
summary: `gate-mobile-TEST-MapCanvasViewModelFollowModeTest.xml` (`tests="24" failures="0"`,
`timestamp="2026-10-24T21:48:06.471Z"`), `gate-mobile-TEST-MapCanvasViewModelRouteFitTest.xml`
(`tests="21"`, `21:47:54.684Z`) and `gate-automotive-TEST-MapCanvasViewModelFollowModeTest.xml`
(`tests="24"`, `21:50:08.971Z`) — the same class, run under both flavours.

The gate printed no `w:` line for the delta: the compile of both source sets is clean (task 2.6), which is the
rule `guidelines/Build.md` §4 states.

## 6. Pointers, checked mechanically

`bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/2026-10-24-fix-map-canvas-view-model-file-size --root .`
— the report is in `evidence-check.log` beside this file.

## 7. What was not run, and why

- **No instrumented or on-device run**: no UI, rendering, surface, manifest or native input changed. The
  delta is Kotlin that the JVM suite already exercises (838 cases around the ViewModel, 5 792 in the gate).
- **No `revert-check` mutation**: the change introduces no invariant, bound, predicate or refusal path — the
  bound it satisfies is the row's, unchanged. The falsifiable claims it does make are the three structural
  checks in §1–§3, and each is a command whose exit status flips if the claim is false (`member-roster.sh` and
  `line-accounting.sh` print `DIFFERENT` and exit 1; the sensor probe prints `violation` instead of `clear`).
- **No second gate run**: the gate is evidence and is quoted, not refreshed.
