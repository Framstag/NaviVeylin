# Design — a JVM-only test run needs no vcpkg checkout

## Root cause

Two mechanisms on one path, and the decision recorded on issue #270 names both, so the change carries both:

1. **The chain is in the test graph.** The licence inventory is a variant asset source
   (`app/build.gradle.kts:1513`), and with `isIncludeAndroidResources = true` (`:284`) the unit-test tasks
   package the variant's assets, so their graph holds `generateLicenseAssets<variant>` → `generateSbom<variant>`
   → `mergeNativeSbom` — for both `:app` flavours.
2. **The chain reads vcpkg at configuration time.** `mergeNativeSbom` called `vcpkgPorts()` inside its
   registration block to declare `inputs.property("vcpkgPorts", …)` (`:787`, before this change), and
   `vcpkgPorts()` throws on a missing `installed/vcpkg/status` (`:748`–`754`). So a task the run never uses
   fails the *configuration* of every graph that includes it.

## The single fix

Two halves, and neither is a fork of the other — the recorded decision made them one change:

- **`-PjvmTestsOnly`** (read through `BuildFlags`, like `-PforceTests`, `buildSrc/…/JvmOnlyTestRun.kt`) leaves
  the inventory's asset source unwired, so the whole chain drops out of the variant graph. The same property is
  **refused** when the requested task names include a packaging or policy task, decided by a pure function
  (`JvmOnlyTestRun.packagingTasksIn`), thrown before any task runs.
- **The lazy read**: the status file is an input only when it exists, and the port list is a `Provider`
  evaluated when the task runs. A missing tree therefore fails `mergeNativeSbom` naming the file, and the
  failure keeps its message instead of becoming Gradle's "input file does not exist".

The half that keeps the guarantee: the refusal. An artefact built with the property would ship no `licenses/`
assets, and `guidelines/Build.md` §9 makes that strict — so the build refuses rather than producing it, and CI
keeps assembling the APK, generating the SBOM and running the policy gate **with** the vcpkg tree (§9's CI
paragraph, unchanged).

## Blast radius

| File | Why it changes |
|---|---|
| `buildSrc/…/testing/JvmOnlyTestRun.kt`, `…/JvmOnlyTestRunTest.kt` | the toggle and the refusal decision, unit-tested (buildSrc's suite runs on every invocation) |
| `app/build.gradle.kts` | the property, the refusal, the guarded asset source, the lazy `mergeNativeSbom` inputs |
| `tools/check-jvm-test-graph-selftest.sh` | the three `--dry-run` configurations the requirement's scenarios name |
| `tools/loop/loop.conf` | the gate passes the property (the loop's tree keeps `VCPKG_ROOT` from `worktree.sh:102` as a fallback only), and the profile owes the new suite |
| `.github/workflows/build.yml` | the unit-test step passes the property; a step runs the suite before vcpkg is installed |
| `guidelines/Build.md` | §3, §4b, §9 state the property, the refusal and the lazy read |

The SBOM's content is untouched: `mergeNativeSbom` still requires the tree, still fails on a missing SPDX file,
and "Missing native SBOM data fails generation" is unmodified. The test tasks' behaviour is unchanged — the
property only removes tasks no test reads (no test loads `licenses/` assets; the inventory is read by the app at
runtime, not by the JVM suite).

## Rollback

Delete the property (one definition in `app/build.gradle.kts`), the guarded `if (jvmTestsOnly) return@onVariants`,
and restore the two eager input declarations; revert the two recipe sites and the suite. No generated data, no
persisted state, no native artefact is involved, so a revert is the inverse edit alone.

## Alternatives the evidence eliminated

- **Provision the tree only** (issue #270's option 1: `VCPKG_ROOT` for loop worktrees) — the loop already does
  this (`tools/loop/worktree.sh:102`), and it is why the loop's own gate was green on a host with a checkout. It
  answers nothing for a fresh clone, a stripped tree or a CI cache miss, which is the item.
- **Shape the build by the requested task names** (option 3) — the property is the explicit form; the only
  `startParameter` reader in the tree records timings (`tools/gate-timings.init.gradle.kts:138,146`), and a build
  that means different things by invocation is what the refusal exists to bound.
- **Make the native section optional in the inventory** (option 4) — it reverses "Missing native SBOM data fails
  generation" and needs `checkLicensePolicy` on every packaging path: two capability deltas, so gate 5 fails.
- **Making the licence tasks no-ops instead of unwiring the asset source** — a skipped task's outputs are absent,
  and AGP's `addGeneratedSourceDirectory` consumer fails on a missing generated directory; unwiring the source is
  the same effect without an empty-directory contract.
