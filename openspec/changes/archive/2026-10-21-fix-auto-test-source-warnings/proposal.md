# fix-auto-test-source-warnings

## Why

The `:auto` unit-test set compiles with three Kotlin diagnostics no gate run reports and no test holds down
(issue #221, split off #65). The rule they break is `guidelines/Build.md` §4 "Result evaluation", bullet
**Warnings**.

```
w: …/RendererTestRule.kt:64:14 Type 'AutoMapRenderer' is final, so the value of the type parameter is predetermined.
w: …/RenderLoopSupervisorTest.kt:40:9 Condition is always 'true'.
w: …/TestCarContext.kt:26:25 Unchecked cast of 'Any?' to 'Array<Any?>'.
```

Measured on base `3211d4d` with `./gradlew :auto:compileDebugUnitTestKotlin --offline --rerun-tasks` — three
`w:` lines and no other non-opt-in one (`evidence/head-compile-warnings.log`).

**Root cause.** Nothing holds the `:auto` unit-test source set to the warning-free rule, so three constructs
whose value the compiler decides instead of the source survive: a type parameter bounded by a final class, a
block that only throws (`Nothing`) whose call is compared against `null`, and an unchecked cast.

**Red test on HEAD:** `AutoTestSourceWarningBaselineTest`, 2026-10-20T23:17:57.607Z, `tests=3 failures=3`
(`evidence/TEST-AutoTestSourceWarningBaselineTest-red.xml`), one finding per construct.

**Owning capability:** `build-test-gate`. **Owning guideline section:** `guidelines/Build.md` §4.

## What Changes

- `auto/src/test/java/com/naviveylin/auto/RendererTestRule.kt`: `track` drops the predetermined type
  parameter; every caller passes an `AutoMapRenderer`, `spyk(…)` included.
- `auto/src/test/java/com/naviveylin/auto/RenderLoopSupervisorTest.kt`: `boom` names the block's type
  (`runIteration<Unit>(…)`), so `== null` asks whether the fault was swallowed instead of stating a constant.
- `auto/src/test/java/com/naviveylin/auto/TestCarContext.kt`: the unchecked `Array<Any?>` cast becomes a
  checked star-projection `as?` cast copied into `Array<Any?>`, with the same resolved strings.
- `app/src/test/java/com/naviveylin/build/AutoTestSourceWarningBaselineTest.kt`: the guard, three cases.
- `openspec/specs/build-test-gate/spec.md`: one requirement, three scenarios, each naming its case.
- `guidelines/Build.md` §4: one sentence recording the guard beside the rule it enforces.
