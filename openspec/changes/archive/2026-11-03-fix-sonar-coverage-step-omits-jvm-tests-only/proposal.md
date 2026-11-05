# The scan's report step carries the JVM-only toggle

## Why

Every run of `.github/workflows/sonar.yml` fails in its "Generate coverage reports" step, before the scanner
runs, so this project has no analysis at all.

**Root cause, in one sentence.** The licence inventory is a variant asset source (`app/build.gradle.kts:1604`),
so a `:app` unit-test graph carries `generateLicenseAssets` → `generateSbom` → `mergeNativeSbom`, and
`mergeNativeSbom` (`app/build.gradle.kts:879`) reads vcpkg's installed-status file while it is configured;
`-PjvmTestsOnly` takes that chain out of a test-only invocation
(`buildSrc/src/main/kotlin/com/naviveylin/build/testing/JvmOnlyTestRun.kt`), the workflow's *test* step passes it
(`.github/workflows/sonar.yml:113`), and the *coverage* step two steps later does not
(`.github/workflows/sonar.yml:120`) — so its graph regains `mergeNativeSbom`, and CI, which installs the Android
SDK and never runs `./setup-vcpkg.sh`, dies evaluating the property:

```
Execution failed for task ':app:mergeNativeSbom' (registered in build file 'app/build.gradle.kts').
> Error while evaluating property 'vcpkgPorts' of task ':app:mergeNativeSbom'.
   > vcpkg installed-status file not found at /home/runner/work/NaviVeylin/NaviVeylin/vcpkg/installed/vcpkg/status
```

The toggle refuses only a packaging invocation (`JvmOnlyTestRun.PACKAGING_TASK_PREFIXES` — `assemble`, `bundle`,
`package`, `install`, `release`, `build`, `generateSbom`, `mergeNativeSbom`, `generateLicenseAssets`,
`checkLicensePolicy`), so `:koverXmlReport` and `jacocoTestReport` are allowed: the report step reads what the
test step produced, which is why they are two steps.

**The same omission on the guideline side.** `guidelines/Build.md:121` (§3 "Commands reference") documents the
same two-command recipe and its second command carries the same omission, while claiming
"`.github/workflows/sonar.yml` runs exactly these steps" — so the hand recipe fails identically on a tree without
a vcpkg checkout (this worktree has none: `ls -d vcpkg` → no such file or directory).

**The red test.** `sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag`, added to
`tools/check-sonar-workflow-selftest.sh`: it copies this tree's own `sonar.yml` into a throwaway tree, drops
`-PjvmTestsOnly` from its Gradle steps, and requires `tools/check-sonar-workflow.sh` to fail naming the file and
line. On base `55b5d658` the checker carries no assertion about a Gradle step's flags, so the case fails — the
defect is that nothing in the tree reads them (`evidence/red-on-HEAD.log`).

**Owning capability and guideline section.** `sonar-analysis`, "The scan reads the coverage the test run already
measures" (`openspec/specs/sonar-analysis/spec.md`), which the change extends with the workflow's own Gradle
steps; the commands are `guidelines/Build.md` §3 "Commands reference" (the row at line 121), and the SBOM chain
the flag removes is `guidelines/Build.md` §9 through `BuildEvidence.md` §4b "`-PjvmTestsOnly` joins it".

**Surface.** Two Gradle steps carry the recipe, one guideline row claims to mirror them, no check reads a Gradle
step's flags, and no case asserts one today:

surface: 2 workflow lines edited, 1 guideline row edited, 1 assertion added, 1 case added, 0 fixtures realigned — `grep -n '\./gradlew' .github/workflows/sonar.yml; grep -rn 'runs exactly these steps' guidelines/; grep -rn 'koverXmlReport' tools/*.sh`

```
grep -n '\./gradlew' .github/workflows/sonar.yml          → 2 (lines 113, 120)
grep -rn 'runs exactly these steps' guidelines/           → 1 (Build.md:121)
grep -rn 'koverXmlReport' tools/*.sh                      → 1 (check-sonar-workflow.sh:269, a coverage-report
                                                             input the properties file must not name)
```

so the surface is: 2 workflow lines edited, 1 guideline row edited, 1 assertion added to
`tools/check-sonar-workflow.sh`, 1 case added to its selftest, 0 fixtures realigned (the selftest's fixture is the
repository's own `sonar.yml`).
