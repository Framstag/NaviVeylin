# Design — fix-map-renderer-test-file-size

## Root cause (one)

`AutoMapRendererTest.kt:35` declares the only class in the file, and that class carries all 82
`AutoMapRenderer` cases plus the harness they share, so the file's 2 099 lines are the sum of every group of
cases that ever landed there. Nothing else in the tree adds a line to it: the bound is per file
(`tools/sensors/metrics/source-size.sh` counts `git ls-files -z '*.kt' '*.java'`), so a file that carries
every group is over it exactly when the groups collectively are.

## The single fix

Split the file by what each group of cases holds down, into five case classes of 13 to 23 cases each (437,
275, 702, 364, 386 lines) beside one new abstract harness class (49 lines), and move the harness's shared
members into it. Nothing about a case changes: same method name, same body, same assertion.

| file | the cases it holds | lines | cases |
|---|---|---|---|
| `AutoMapRendererTestSupport.kt` (`abstract`) | the teardown rule, the fake client, `@Before`, `fixLat`/`fixLon`, `mockSurface()` | 49 | — |
| `AutoMapRendererTest.kt` | prediction band: extrapolation gate, displayed position between fixes, the smooth-follow correction bound; `eastMeters`, `metersBetween` | 437 | 13 |
| `AutoMapRendererLifecycleTest.kt` | surfaces, shutdown, marker/favourite setters, single-step pinch zoom, the diagnostic lines, route without geometry | 275 | 20 |
| `AutoMapRendererBlitTest.kt` | overrun bitmap, full render vs blit, invalidation, blit offsets, the committed frame | 702 | 23 |
| `AutoMapRendererFollowAnchorTest.kt` | follow re-engage, resolved anchor against host insets, locale-independent numbers | 364 | 11 |
| `AutoMapRendererZoomWalkTest.kt` | the auto-zoom walk; `rendererWithFrame`, `requestAutoZoom`, `walkToCompletion` | 386 | 15 |

**Why the class name `AutoMapRendererTest` stays** on the prediction band: six shipped scenarios of
`auto-smooth-follow` name their case as `AutoMapRendererTest.<case>` and `ci-unit-test-jni`'s scenario names
the class itself, so renaming it would make seven shipped `Case:` pointers dangling and turn a mechanical
split into a spec delta across two capabilities. The cases that stayed are the six the spec names, which
keeps each pointer true by construction; the class KDoc says so, so a later reader does not "fix" the name.

**Threading, lifecycle, sandbox** — unchanged, and that is the point of keeping the harness in one place:
`RendererTestRule` is still the `@get:Rule` of every class, its teardown still shuts down every renderer the
class created, `@RunWith(RobolectricTestRunner::class)` is still declared on each class (never inherited by
accident), and `FakeAutoRenderClient` still loads the committed JNI stub inside the default Robolectric
sandbox (`guidelines/Build.md` §6a, §6h, `AGENTS.md`'s classloader rule).

## Alternatives the evidence eliminated

1. **Retune or baseline the row** (`tools/sensors/sensors.json:70`, `baseline`): impossible as a fix — the
   bound is the owner's recorded decision of 2026-10-21 (1 500 lines, file level, tests included,
   `guidelines/Backlog.md` §9), and a baseline would make the row stop reporting the condition it exists to
   report. The item's own lifecycle is "the path is the identity, so an item closes when that file drops
   under the bound".
2. **A guard test in the suite that asserts the file's line count**: impossible — it duplicates the row,
   whose scope is every tracked `.kt`/`.java` file, and it would redden the build for the three other files
   past the bound that are other items' work (5 096, 3 384 and 2 307 lines; same probe,
   `evidence/red-probe-head.log`). It also gives the same file two owners.
3. **Splitting by JUnit `@Nested` inner classes**: impossible — the measurement is lines per file, and nested
   classes do not remove a line from the file.
4. **Moving groups into the five sibling `AutoMapRenderer*Test` files** (`LoopConfinement`, `MarkerDrawCache`,
   `PooledTarget`, `RenderCadence`, `SurfaceOwnership`): impossible — each of those is a class that owns a
   different behaviour and its own ~150-line file; pouring 2 099 lines into them grows five files and
   duplicates the harness five times (there is no shared base today), which is the same defect moving.
5. **Deleting cases to reach the bound**: impossible — the 82 cases are the `AutoMapRenderer` set, and six of
   them are the acceptance cases of shipped `auto-smooth-follow` scenarios.

## Blast radius

- 5 files created, 1 file rewritten, 3 comments realigned in 3 other test files.
- 0 production files, 0 spec texts, 0 guidelines, 0 Gradle logic, 0 resources, 0 native sources.
- Test count unchanged: 82 cases in the five classes (13 + 20 + 23 + 11 + 15), verified as a set against HEAD
  (`evidence/case-roster.log`).
- The `:auto` suite's runtime is unchanged in kind: the same 82 cases in the same JVM, five result XMLs
  where there was one (`unit-test-suite-runtime`'s "one result XML exists per executed class" holds either
  way).

## Verification

- `bash /tmp`-free probes recorded in `evidence/`: the row's own `source-size.sh` measurement before
  (2 099, `violation`) and after (437, `clear`) under the entry's fingerprint, and the case-roster `diff`.
- `./gradlew :auto:testDebugUnitTest --tests 'com.naviveylin.auto.AutoMapRenderer*'` — the affected cases,
  82 cases in five XMLs, none failing.
- `./tools/loop/gate.sh` once: the loop profile counts `*/src/*` as a compiled path, so the gate owes
  `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` plus the suites the `openspec/**` paths pull
  in (`gen-feature-list.sh --check-classification`, `check-doc-routes.sh`, `check-openspec-hygiene.sh`).
- No on-device task: no UI, rendering or surface code changes, and no diagnostic line is added.
- No new invariant, bound, predicate or refusal path is introduced — the bound this change satisfies is the
  row's, unchanged — so there is no revert-check mutation to name; the falsifiable claim is the case roster,
  and `evidence/case-roster.log` is its check (a case renamed or dropped makes it print `DIFFERENT` and exit
  1; the case filter, not a mutation, is what would be seen).

## Rollback

Revert the change's commit: the five new files and the support class disappear, `AutoMapRendererTest.kt`
returns to 2 099 lines, the three comments return to their old wording, and the row's probe prints
`measurement=2099 verdict=violation` again. Nothing outside the test source set moves, so no other rollback
step exists.
