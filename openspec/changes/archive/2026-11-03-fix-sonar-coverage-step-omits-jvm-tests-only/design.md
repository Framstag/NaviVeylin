# Design — the scan's report step carries the JVM-only toggle

## Root cause

One mechanism. `-PjvmTestsOnly` is the only thing that takes `generateLicenseAssets` → `generateSbom` →
`mergeNativeSbom` out of a `:app` unit-test graph (`app/build.gradle.kts:1604`, inside `onVariants`;
`JvmOnlyTestRun.kt`), and it is a per-invocation Gradle property, not a project setting — so it holds for the
invocation that passes it and for no other. `.github/workflows/sonar.yml` is two invocations: the test step
(line 113) passes it, the report step (line 120) does not, and the second invocation re-configures the same
graph, reaches `mergeNativeSbom` (`app/build.gradle.kts:879`, whose provider validates
`vcpkg/installed/vcpkg/status`), and fails on a runner where `vcpkg/` never existed. `guidelines/Build.md:121`
carries the identical pair of lines, which is why the hand recipe fails the same way.

## The fix

Pass `-PjvmTestsOnly` to the report invocation in both places. Nothing else changes in the graph: the report
tasks consume the coverage data the test step's agent wrote, the toggle refuses only a packaging invocation
(`JvmOnlyTestRun.refusalFor`, which reports nothing for `:koverXmlReport`, `:app:koverXmlReport`,
`:auto:koverXmlReport`, `:core:koverXmlReport`, `:osmscout-client-java:jacocoTestReport`), and a report task that
produced nothing still fails the step.

And a structural assertion in `tools/check-sonar-workflow.sh`, with the case
`sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag` in its selftest: every `./gradlew` line in `sonar.yml`
whose requested tasks are tests or coverage reports must carry the toggle. Without it the omission returns the
moment someone edits a step, which is exactly how it survived the review of b514b7b8 (#741): no check in the
tree reads a Gradle step's flags.

## Blast radius

Three files, no compiled path: `sonar.yml` (one `run:` line), `guidelines/Build.md` (one table row), and
`tools/check-sonar-workflow.sh` plus its selftest (one assertion, one case). The check's other assertions and
its green fixture are unchanged; the fixture is the repository's own `sonar.yml`, so the new case plants the
omission rather than a stand-in. No Gradle task, no module, no spec scenario of another capability moves; the
only scenario read is `sonar-analysis`, which this change adds one requirement to.

## Rollback

Revert the commit: the workflow's report step returns to the invocation that fails on a runner without vcpkg,
the guideline row to the recipe that mirrors it, and the check to the assertion set that does not read flags.
No data, no artefact and no other workflow depends on any of the four edits.

## Alternatives eliminated

- **Install vcpkg in the scan workflow** (`./setup-vcpkg.sh` before the steps): rejected — the report step does
  not need the native tree at all, and the job exists to measure JVM coverage. It would also make every scan pay
  a vcpkg build, and the test step already proves the tree is unnecessary.
- **Drop the separate report step and let `test` write the reports**: rejected — the agent writes execution data
  and the report tasks write the XML; merging them makes the step that fails indistinguishable from the step
  that measured. The two steps are the workflow's own record of which one produced the reports.
- **Set the property in `gradle.properties`**: rejected — `JvmOnlyTestRun.refusalFor` refuses it for a packaging
  invocation, and the release builds run from the same tree; a checked-in toggle would either refuse `assemble`
  or silently drop the licence inventory from shipped artefacts (`guidelines/Build.md` §9).

## Open decisions

none
