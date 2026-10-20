# fix-gradle10-script-deprecations

## Why

Every build of this tree prints `Deprecated Gradle features were used in this build, making it incompatible
with Gradle 10` (measured 2026-10-20 on base `648d2bc`). The issue that asks for the sweep (#56, migrated
entry `§22`) expected the remainder to be plugin-owned; measured, **ten of the warnings come from the
project's own scripts**, from one construct.

**Root cause.** The Kotlin DSL delegated-property registration
`val checkHardcodedStrings by tasks.registering { … }` is deprecated in Gradle 9.6.1 and removed in Gradle 10,
so each of the ten registrations emits

```
The 'val name by registering { }' property delegate syntax has been deprecated. This is scheduled to be
removed in Gradle 10. Use 'val element = register(name) { }' instead.
	at Build_gradle.<init>(build.gradle.kts:377)
```

once per configuration. Measured on the base commit with
`./gradlew --offline --warning-mode all help` (`evidence/head-warning-mode-all.txt`): ten warnings, one
message family, and the ten sites are exactly

- `app/build.gradle.kts:377, 406, 444, 474, 606, 734`
- `auto/build.gradle.kts:118, 138`
- `core/build.gradle.kts:104, 125`

The second warning the build prints — `Using a Project object as a dependency notation` — is **not** ours:
with the root `kover(project(":app"))` declarations commented out, the warning is still emitted
(`evidence/kover-probe.txt`), so it comes from the Kover 0.9.8 plugin's own internals, exactly as
`guidelines/Build.md` §7 records. That one stays and is documented, not swept.

**Red test on HEAD** (`648d2bc`), added as
`app: GradleScriptDeprecationTest` "no project build script uses a removed Gradle construct": a scan of every
`*.gradle.kts` the project owns, asserting zero delegate sites (2026-10-20T12:14:18.550Z, JUnit XML
`tests=2 failures=1`, `evidence/TEST-GradleScriptDeprecationTest-red.xml`):

```
java.lang.AssertionError: a script that registers a task with the `by tasks.registering { }` delegate must
use `tasks.register("name") { }` instead — Gradle 10 removes the delegate expected:<[]> but was:<[:377: val
checkHardcodedStrings by tasks.registering {, :406: val checkNoCoordinatesInLogs by tasks.registering {, …]>
```

The case is the absence the delta requires: ten findings before, zero after. Its second case, "the removal is
still detectable", keeps the guard non-vacuous by asserting the scan *does* report a deprecated line and does
*not* report the named form beside a comment that names it.

**Owning capability:** `build-jvm-toolchain` — the build's Gradle side, beside the requirement that names the
daemon JVM. **Owning guideline section:** `guidelines/Build.md` §7, the two paragraphs that record the
deprecation baseline (configuration cache and the Kover warning); the guard is the `:app` unit-test case above,
which the documented gate and CI's test step both run.

## What Changes

- `app/build.gradle.kts`, `auto/build.gradle.kts`, `core/build.gradle.kts`: the ten
  `val <name> by tasks.registering { … }` registrations become `val <name> = tasks.register("<name>") { … }` —
  the same task name, the same configure block, the same `TaskProvider`, so every task path
  (`:app:checkHardcodedStrings`, `:app:mergeNativeSbom`, …) is unchanged.
- `app/src/test/java/com/naviveylin/build/GradleScriptDeprecationTest.kt`: the two cases above.
- `guidelines/Build.md` §7: the sweep's result recorded — the project-owned family is gone (with the command
  that measures it), the Kover plugin warning remains and why, and the guard that keeps the family out.

## Spec

`openspec/specs/build-jvm-toolchain/spec.md`, ADDED requirement "The build's scripts use no construct Gradle
10 removes", with two scenarios that each name one case of `GradleScriptDeprecationTest`.

## Cases and evidence

- `app: GradleScriptDeprecationTest` "no project build script uses a removed Gradle construct" — red on HEAD
  (ten findings), green after the rewrite.
- `app: GradleScriptDeprecationTest` "the removal is still detectable" — green on HEAD and after, the
  mutation-shaped half of the guard.
- `./gradlew --offline --warning-mode all help` — ten delegate warnings on HEAD, zero after
  (`evidence/head-warning-mode-all.txt`, `evidence/fixed-warning-mode-all.txt`).
- The ten task registrations still resolve and run: `:app:`/`:auto:`/`:core:` `checkHardcodedStrings`,
  `:app:checkNoCoordinatesInLogs`, `:app:checkCleartextTransportPolicy`, `:app:`/`:auto:`/`:core:`
  `checkNoWallClockWaits` execute under `--warning-mode all` and print no deprecation
  (`evidence/fixed-checks.log`).
- The gate: `./gradlew test -PforceTests --no-build-cache` (`evidence/`).

## Impact

Three build scripts, one new test file, one guideline section, one capability spec. No application source, no
native code, no manifest, no dependency, no release state. Behavioural change: none at build-output level — the
same tasks under the same names with the same actions; the difference is that the configuration no longer uses
a construct Gradle 10 removes, so the next Gradle upgrade is unblocked for our own scripts.

Rollback: revert the commit. The delegate form returns, every build works again on Gradle 9.6.1 and prints the
ten warnings again, and the guard case goes red — which is the intended signal, not damage.
