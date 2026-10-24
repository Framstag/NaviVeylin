# Tasks — fix-map-renderer-test-file-size

No requirement changes, so no task changes a spec; each task names the shipped requirement that holds the
claim it verifies. Parent requirements: `backlog-sensors` — "One row watches a source unit's size" (the bound
and the fingerprint the item closes under) and `unit-test-suite-runtime` — "the module's test classes
execute" (the case roster the split must keep whole).

## 1. Before the split — the two numbers the delta must move

- [x] 1.1 Take the red number with the row's own measurement rather than a second copy of it:
  `SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[]' bash tools/sensors/metrics/source-size.sh`
  → `"measurement":"2099","verdict":"violation"` under fingerprint `60ebe0d1…` on base `3cc62e15`
  (`evidence/red-probe-head.log`; the empty condition list changes nothing about a violation — the same
  probe on a still-oversized path prints the identical line with a condition supplied). Parent:
  `backlog-sensors` — the row files one item per path, over the bound, and closes it when the path drops
  under.
- [x] 1.2 Record HEAD's case roster as the invariant the split keeps: `git show
  HEAD:auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt | grep -o '^    fun [A-Za-z0-9_]*' | sort`
  → 82 case names (`setUp` excluded: it is the shared `@Before`). Parent: `unit-test-suite-runtime` — every
  test class of the module executes, and no case may vanish into a move.

## 2. The split

- [x] 2.1 Add `AutoMapRendererTestSupport.kt`: the `@get:Rule` teardown rule, the fake client and the
  renderer, the `@Before` that builds them, `fixLat`/`fixLon` and `mockSurface()`, each `protected` where a
  case class needs it. Verified by 2.4's compile — the harness must compile before a case can use it.
- [x] 2.2 Move the groups into the five case classes named in `design.md`
  (`AutoMapRendererTest`, `AutoMapRendererLifecycleTest`, `AutoMapRendererBlitTest`,
  `AutoMapRendererFollowAnchorTest`, `AutoMapRendererZoomWalkTest`), each keeping the class name the specs
  and `tools/declared-cases.sh` resolve against; `AutoMapRendererTest` keeps the six cases
  `auto-smooth-follow` names. Verified by 2.4, 3.1 and 3.2.
- [x] 2.3 Realign the three comments that named `AutoMapRendererTest` for a case or a call that now lives in
  another class (`RendererTestRuleTest.kt`, `AutoMapRendererLoopConfinementTest.kt`,
  `FakeAutoRenderClient.kt`). Verified by `grep -rn 'AutoMapRendererTest' auto/src` leaving only true
  statements — the class's own files and the harness KDoc.
- [x] 2.4 Compile the source set: `./gradlew :auto:compileDebugUnitTestKotlin -PjvmTestsOnly --offline` →
  `BUILD SUCCESSFUL`, no `w:` line, so the split adds no warning the warning rule would owe a fix for
  (`guidelines/Build.md` §4, "Warnings").

## 3. Verification — scoped by what changed (test sources only, plus one `openspec/**` tree)

- [x] 3.1 Run the affected cases: `./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests
  --tests 'com.naviveylin.auto.AutoMapRenderer*'` → ten XMLs, 107 cases, `failures=0 errors=0`; the five
  classes the split produced carry 82 of them (13 + 20 + 23 + 11 + 15) and the same filter also matches the
  five sibling `AutoMapRenderer*Test` classes (25 cases), whose comments 2.3 realigned. Each XML copied to
  `evidence/` at the moment it was produced. Parent: `unit-test-suite-runtime`.
- [x] 3.2 The roster check: the union of the five classes' public `fun` names against 1.2's 82 → `diff`
  empty, `evidence/case-roster.log`. Parent: `unit-test-suite-runtime`.
- [x] 3.3 Take the green number with the same probe as 1.1 → `"measurement":"437","verdict":"clear"` under
  the same fingerprint (`evidence/green-probe.log`): the row's clearing pass is what closes the item.
  Parent: `backlog-sensors`.
- [x] 3.4 Run the project's gate once (`./tools/loop/gate.sh`): the loop profile counts `*/src/*` as a
  compiled path, so the gate owes `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` plus the
  suites the `openspec/changes/**` paths pull in. `BUILD SUCCESSFUL`, 403 tallied cases with none failing
  across the four suites that report a count, 303 s of Gradle suite (`evidence/gate.log`, `evidence/gate-*.xml`).
  Quoted, not retried. Parent: `build-test-gate`.

## 4. Evidence and archive

- [x] 4.1 Copy every cited XML into `openspec/changes/fix-map-renderer-test-file-size/evidence/` and the run's
  `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/300`, and quote the gate's tallies in
  `evidence/evidence.md`, each beside the `gate-*.xml` that carries it. Parent: `build-test-gate` (a green
  claim has a test result in the tree).
- [x] 4.2 Pointers mechanically: `bash .pi/skills/fix-loop/scripts/evidence-check.sh
  openspec/changes/fix-map-renderer-test-file-size --root .` → `evidence-check: PASS` (C1 8 §references, C2
  tallies confirmed against the XMLs, C3 no `/tmp` pointer, C5 359 `file:line` in range).
- [x] 4.3 `openspec validate fix-map-renderer-test-file-size --strict` → "is valid"; `.openspec.yaml` declares
  `skip_specs: true` with the reason in `design.md` (no requirement changes, so no delta is invented).
- [x] 4.4 Archive the change and confirm the directory exists under `openspec/changes/archive/` — archived as
  `2026-10-24-fix-map-renderer-test-file-size`, `skip_specs: true`, so no capability spec is touched and there
  is nothing to merge into one.
- [x] 4.5 Do not commit, do not push, do not touch the tracker: the run's script owns git and the labels.
