# Evidence — fix-map-renderer-test-file-size (item #300)

Everything below is a number taken in `$(git rev-parse --short HEAD)` = `3cc62e15` plus this change's
uncommitted tree, and each line names where the artifact sits beside this file.

## 1. The row's measurement — red on HEAD, clear after the split

| | command | finding |
|---|---|---|
| HEAD (clean tree, 2026-10-24T20:59Z) | `SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[]' bash tools/sensors/metrics/source-size.sh` | `"measurement":"2099","verdict":"violation"` — `red-probe-head.log` |
| after the split (2026-10-24T21:02Z) | the same probe with the entry's condition | `"measurement":"437","verdict":"clear"` — `green-probe.log` |

The fingerprint is the entry's own in both: `60ebe0d1c07f9c2521c7c9031a6d809b0b97190a93eb7acc7d944de316d90518`,
so the clearing finding is the finding that closes the item (`tools/sensors/metrics/source-size.sh`, the
clearing pass over the open conditions). `probe-source-size.sh` is the probe as it was run.

`red-probe-head.log` also carries the second half of the argument for the empty condition list: the same
probe on a path that is still past the bound prints an identical `violation` line with and without a
condition, because the conditions list feeds only the clearing pass.

## 2. The case roster — the invariant the split keeps

`case-roster.sh` compares HEAD's single file against the five classes that replaced it:
82 public `fun`s (the shared `@Before setUp` excluded) before and 82 after, `diff` empty —
`case-roster.log`.

## 3. The affected cases

`./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests --tests 'com.naviveylin.auto.AutoMapRenderer*'`
— `BUILD SUCCESSFUL`, no `w:` line (`focused-run.log`), ten result XMLs (`focused-TEST-*.xml`):

| class | tests | failures | errors |
|---|---|---|---|
| `AutoMapRendererTest` | 13 | 0 | 0 |
| `AutoMapRendererLifecycleTest` | 20 | 0 | 0 |
| `AutoMapRendererBlitTest` | 23 | 0 | 0 |
| `AutoMapRendererFollowAnchorTest` | 11 | 0 | 0 |
| `AutoMapRendererZoomWalkTest` | 15 | 0 | 0 |
| `AutoMapRendererLoopConfinementTest` | 5 | 0 | 0 |
| `AutoMapRendererMarkerDrawCacheTest` | 4 | 0 | 0 |
| `AutoMapRendererPooledTargetTest` | 4 | 0 | 0 |
| `AutoMapRendererRenderCadenceTest` | 5 | 0 | 0 |
| `AutoMapRendererSurfaceOwnershipTest` | 7 | 0 | 0 |
| **total** | **107** | **0** | **0** |

The 82 in the five classes the split produced are the whole of the old file; the other 25 are the sibling
`AutoMapRenderer*Test` classes the same filter matches, whose comments task 2.3 realigned. Quoted from the
gate run's own XML for one of them, `gate-TEST-com.naviveylin.auto.AutoMapRendererTest.xml`:
`tests="13" skipped="0" failures="0" errors="0"`.

## 4. The gate

`./tools/loop/gate.sh`, run once from the change root, `BUILD SUCCESSFUL` (`gate.log`, 1 017 lines). Its own
tallies, each beside this file as `gate-*.xml`:

| suite | tally |
|---|---|
| `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` | 303 s, `BUILD SUCCESSFUL`; the run's `:auto` classes are the five `gate-TEST-*.xml` here (13 / 20 / 23 / 11 / 15, no failure, no error) |
| `tools/loop-selftest.sh --from-git db888dd8…` | 218 cases, 0 failing |
| `bash tools/loop-mutations.sh --check` | 145 cases, 0 failing |
| `tools/loop-mutations-selftest.sh` | 34 cases, 0 failing |
| `tools/check-openspec-hygiene-selftest.sh` | 6 cases, 0 failing |
| `gen-feature-list.sh --check-classification`, `check-doc-routes.sh` (+ its self-test and mutation list), `check-todo-references.sh`, `check-openspec-hygiene.sh` | no case count of their own, all green |

The gate's summary line aggregates those four tallied suites into 403 cases with none failing and no error;
the `tests="0"` in `gate-gradlew_test_*.xml` is the gate's own record of the Gradle suite (Gradle prints no
JUnit summary line for it), which is why the per-class XMLs are what this change cites.

The build's two `w:` lines are in `:app`'s test sources, both on paths this change does not touch
(`StartMapResolverTest.kt:131`, `ShippedSpecClassificationTest.kt:51`); `:auto`'s test sources compile with
no warning, which is the rule `guidelines/Build.md` §4 states and the reason task 2.4 checks it.

## 5. What was not run, and why

- **No build of `:app` and no instrumented run**: no production source, no resource, no manifest and no
  native input changed — the delta is `:auto` test sources and three comments.
- **No on-device check**: no UI, rendering, surface or diagnostic line changed.
