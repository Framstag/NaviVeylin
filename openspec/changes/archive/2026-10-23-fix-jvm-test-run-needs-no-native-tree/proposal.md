# A JVM-only test run needs no vcpkg checkout

## Why

**Verified in the tree today.** `app/build.gradle.kts:787` (before this change) read the native section's port
list while the task was being *created*:

```kotlin
inputs.property("vcpkgPorts", vcpkgTriplets.map { vcpkgPorts(vcpkgStatusFile, it) })
```

`vcpkgPorts()` throws when `vcpkg/installed/vcpkg/status` is absent (`app/build.gradle.kts:748`–`754`), so any
graph that registers `mergeNativeSbom` fails at configuration. The unit-test graph does register it: the licence
inventory is a variant asset source (`app/build.gradle.kts:1513`,
`variant.sources.assets?.addGeneratedSourceDirectory(licenseTask, …)`), and with
`isIncludeAndroidResources = true` (`app/build.gradle.kts:284`) the unit-test tasks package the variant's
assets — `generateLicenseAssets<variant>` → `generateSbom<variant>` → `mergeNativeSbom`.

**Measured on the base `f5f2a2a76e3adc73e377b0bf2747718ac17dca76`, 2026-10-23, in `.pi/worktrees/loop`.** The
decision's red command, 26.6 s wall clock, exit 1:

```
$ VCPKG_ROOT=/tmp/i270/no-vcpkg-here ./gradlew test -PforceTests --no-build-cache
FAILURE: Build failed with an exception.
* What went wrong:
Could not determine the dependencies of task ':app:generateSbomAutomotiveDebug'.
> Could not create task ':app:mergeNativeSbom'.
   > vcpkg installed-status file not found at /tmp/i270/no-vcpkg-here/installed/vcpkg/status — run ./setup-vcpkg.sh first.
BUILD FAILED in 26s
3 actionable tasks: 3 executed
```

(`/tmp/i270/red-HEAD.log`, copied as `evidence/red-HEAD.log`.) No test task reached its action: a tree without a
vcpkg checkout — a stripped kept worktree, a fresh clone before `setup-vcpkg.sh`, a CI job whose cache missed —
cannot run a single unit test, and `tools/loop/gate.sh` reports it as a failing suite.

**Root cause, one sentence.** The licence inventory's variant asset source puts `mergeNativeSbom` in the
unit-test graph, and `mergeNativeSbom` reads vcpkg's installed-status file in its *registration* block, so a
missing vcpkg tree fails configuration instead of the one task that needs it — and no JVM-only invocation can
skip a task it never uses.

**The red case** (red on the base, green after): `buildGraph#aJvmOnlyTestRunNeedsNoNativeTree` in
`tools/check-jvm-test-graph-selftest.sh` — a test invocation with the JVM-only property and `VCPKG_ROOT` pointing
at a directory that is not there must configure (exit 0) and its graph must hold no SBOM or licence task; on the
base it is exactly the failure above. Case 3,
`buildGraph#aMissingNativeTreeFailsItsOwnTask`, asserts the other half on the base's own message
(`Could not create task ':app:mergeNativeSbom'`), and case 2,
`buildGraph#aPackagingGraphRefusesTheProperty`, cannot hold on the base because the base reads no such property
(`git show f5f2a2a:app/build.gradle.kts | grep -c 'jvmTestsOnly'` → **0**). The recorded red evidence is the
decision's command, not a suite tally: the run that measures a suite against the base is the one this loop does
not take. The requirement's own scenario is `build-sbom`, "A JVM-only test run does not require the native tree".

**Owning requirement.** `build-sbom` — the capability that owns the licence inventory, its missing-data rule
("Missing native SBOM data fails generation", kept unmodified) and the asset-source wiring that puts the chain in
the test graph. `build-test-gate`'s "Iteration runs build no more native artifacts than they test" states the
intent for native *artifacts*; this change is about the inventory that pulls the native tree in, so the delta is
one ADDED requirement in `build-sbom`.

**Owning guideline section.** `guidelines/Build.md` §9 "License compliance", the paragraph that states the
inventory is a variant asset source (`guidelines/Build.md:1268`–`1277`), and the gate recipe in §3 and §4b that
now carries the property.

## What Changes

- **`buildSrc/src/main/kotlin/com/naviveylin/build/testing/JvmOnlyTestRun.kt`** (new) — the toggle: reads
  `-PjvmTestsOnly` through `BuildFlags` (like `-PforceTests`), and decides, on the requested task names alone,
  whether the invocation packages. Pure, so the rule is unit-tested instead of measured by running a build.
- **`buildSrc/src/test/kotlin/com/naviveylin/build/testing/JvmOnlyTestRunTest.kt`** (new) — the switch's rule:
  absent/`false` is off, an unreadable value is refused, a test-only invocation is never refused, and each
  packaging entry point is refused by name.
- **`app/build.gradle.kts`** — the property refuses a packaging invocation before any task runs
  (`JvmOnlyTestRun.refusalFor(gradle.startParameter.taskNames)`), leaves the inventory's asset source unwired when
  it is set (so `generateLicenseAssets` → `generateSbom` → `mergeNativeSbom` drop out of the test graph), and reads
  vcpkg's status file lazily in `mergeNativeSbom`: the file is an input only when it exists and the port list is a
  `Provider` evaluated when the task runs.
- **`tools/check-jvm-test-graph-selftest.sh`** (new) — three `--dry-run` configurations: the JVM-only graph carries
  no SBOM/licence task without a vcpkg tree, a packaging graph is refused by name, and without the property a
  missing tree fails `mergeNativeSbom` itself naming the status file (no `Could not create task`).
- **`tools/loop/loop.conf`** — `LOOP_GATE_COMPILED_CMD` passes `-PjvmTestsOnly`; the profile owes the new suite for
  `app/build.gradle.kts` and `buildSrc/*`.
- **`.github/workflows/build.yml`** — the unit-test step passes `-PjvmTestsOnly` (the three strict steps above it
  keep running with the vcpkg tree present) and a step runs the new suite before vcpkg is installed.
- **`guidelines/Build.md`** — §3's gate row, §4b's forcing bullet and §9's asset-source paragraph state the
  property, the refusal and the lazy read.
- **the spec `build-sbom`** — one ADDED requirement, "A JVM-only test run does not require the native tree", with
  four scenarios naming the three shell cases and the switch's unit case.

surface: 3 shell cases added, 1 unit-test class (7 cases) added, 0 cases rewritten, 0 fixtures realigned —
`grep -c '^total=\$((total + 1))' tools/check-jvm-test-graph-selftest.sh` → **3**;
`grep -c '@Test' buildSrc/src/test/kotlin/com/naviveylin/build/testing/JvmOnlyTestRunTest.kt` → **7**; the sites
that prescribe the gate were counted with `grep -rln 'forceTests' --include='*.md' --include='*.yml'
--include='*.sh' --include='*.kts'` over the tree → **8** files, of which **3** carry the full-gate invocation
(`guidelines/Build.md` §3 table row, `tools/loop/loop.conf` `LOOP_GATE_COMPILED_CMD`, `.github/workflows/build.yml`
unit-test step) and all three are edited here;
`grep -rn 'mergeNativeSbom\|vcpkgPorts' --include='*.sh' . | grep -v '^./openspec/changes/archive'` → **0** cases
and **0** fixtures asserted the rule before this change, so nothing was rewritten; the native-tree rule has one
producer (`vcpkgPorts()`, `app/build.gradle.kts:748`) and two readers inside `mergeNativeSbom` (`:787`
registration, `:804` action).
