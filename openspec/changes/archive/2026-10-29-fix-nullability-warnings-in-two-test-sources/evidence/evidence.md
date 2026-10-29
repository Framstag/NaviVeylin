# Evidence — fix-nullability-warnings-in-two-test-sources

## The measuring block, as `./tools/loop/measure.sh` printed it

Quoted from `evidence/measure.txt`, taken at the change root before the plan. With no change open it measured
the newest archived change; the lines that are this change's own are `LOOP-MEASURE-SUITE` and
`LOOP-MEASURE-GATE`.

```
LOOP-MEASURE base=42d3e0004206ebfee39e11942db0bef375e1909e paths=168 insertions=5729 deletions=22
LOOP-MEASURE-SUITE cases=277 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-CHANGE 2026-10-29-fix-ui-navigation-content-zero-distance scenarios=0 cases=0 unnamed=0
```

## The two diagnostics on the base tree

Command, once per `:app` flavor:

```
./gradlew :app:compileMobileDebugUnitTestKotlin --rerun-tasks
./gradlew :app:compileAutomotiveDebugUnitTestKotlin --rerun-tasks
```

Each compile printed:

```
w: .../app/src/test/java/com/naviveylin/data/StartMapResolverTest.kt:131:78 Java type mismatch: inferred type is 'String?', but 'String' was expected.
w: .../app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt:51:20 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'File?'.
```

Two call sites, each compiled by both flavors: four lines, the item's count.

## Red on HEAD

`./gradlew :app:testMobileDebugUnitTest -PforceTests -PnoCoverage --no-build-cache --tests "com.naviveylin.build.UnitTestSourceNullabilityBaselineTest"`
→ `3 tests completed, 2 failed`, `BUILD FAILED in 7s`; `evidence/red-on-head.xml` carries
`tests="3" skipped="0" failures="2" errors="0" timestamp="2026-10-29T09:38:23.282Z"` and its `system-out`:

```
FINDING :51: .parentFile.r in app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt
FINDING :131: .contains(first) in app/src/test/java/com/naviveylin/data/StartMapResolverTest.kt
```

## Green

`./gradlew :app:testMobileDebugUnitTest -PforceTests -PnoCoverage --no-build-cache --tests "com.naviveylin.build.UnitTestSourceNullabilityBaselineTest" --tests "com.naviveylin.data.StartMapResolverTest" --tests "com.naviveylin.featurelist.ShippedSpecClassificationTest"`
→ `BUILD SUCCESSFUL in 12s`, with

| XML | tests | failures | errors | timestamp |
|---|---|---|---|---|
| `evidence/green-guard.xml` | 3 | 0 | 0 | 2026-10-29T09:38:36.944Z |
| `evidence/green-start-map-resolver.xml` | 9 | 0 | 0 | 2026-10-29T09:38:37.141Z |
| `evidence/green-shipped-spec.xml` | 1 | 0 | 0 | 2026-10-29T09:38:37.255Z |

## The families after the fix

`./gradlew :app:compileMobileDebugUnitTestKotlin :app:compileAutomotiveDebugUnitTestKotlin --rerun-tasks`
(`evidence/compile-after-fix.log`): `grep -c "^w: " evidence/compile-after-fix.log` reads `0` — neither
family, and no other `w:` line, is printed by the two `:app` unit-test compiles.

## The gate

`./tools/loop/gate.sh`, run once from the change root (`evidence/gate.log`). Its `LOOP-GATE` line names 676
executed cases with no failure and `errors=0`, and the steps the change's `*/src/*` and `*.md` pathspec owes:
`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` 329s, `tools/loop-selftest.sh --from-git` 628s,
`tools/loop-mutations-selftest.sh` 397s, `tools/issue-tooling-selftest.sh` 105s,
`tools/check-portability-selftest.sh` 30s, `bash tools/gen-feature-list.sh --check-classification` 5s,
`check-doc-routes(.sh/-selftest)`, `check-todo-references`, `check-openspec-hygiene(.sh/-selftest)`,
`check-compose-sandbox-rule(.sh/-selftest)`, `loop-mutations --check`, `mutate-selftest.sh` and the six
`mutate.sh --list` maps. Each step's own tally is a file under `evidence/gate-tallies/`.

## The guard is load-bearing

One `revert-check` mutation, from the change root: `ShippedSpecClassificationTest.kt`'s `parent.relativeTo(specsDir)`
was replaced by the pre-fix `spec.parentFile.relativeTo(specsDir)` (marked, then restored). The guard failed as
it exists to: `3 tests completed, 1 failed`, `evidence/revert-check-mutated.xml` carries
`tests="3" skipped="0" failures="1" errors="0" timestamp="2026-10-29T10:06:49.781Z"` and the finding
`FINDING :57: .parentFile.r in app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt`.
After the restore the same invocation is green — `evidence/revert-check-restored.xml`,
`tests="3" skipped="0" failures="0" errors="0" timestamp="2026-10-29T10:06:59.874Z"` (`evidence/revert-check.log`).

## Surface

```
grep -rn "\.parentFile\.\|listOf([^)]*)\.contains(" --include=*.kt app/src/test auto/src/test core/src/test
```

`2` matches in `2` files on the base tree (the two call sites), `0` on this change's tree.
