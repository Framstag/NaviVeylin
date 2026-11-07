# Proposal

## Why

A SonarQube Cloud project exists for this repository (`Framstag_NaviVeylin`, organization `framstag`) and the
owner has put the three measure badges — quality gate, coverage, lines of code — into `README.md`. Nothing in
the repository produces that analysis: no workflow calls a scanner, no `sonar-project.properties` exists, and
no coverage report reaches Sonar. The badges therefore render an empty or stale project, and the pull request
shows no analysis.

The scan cannot be naive. `app/src/main/cpp/libosmscout` is a submodule that is checked out on disk (about
6,000 C/C++ files plus the `libosmscout-client-java` sources that `:osmscout-client-java` compiles as its
`vendored` source set), and that tree already has its own Sonar project (`Framstag_libosmscout`, its own
`sonar-project.properties`). Left in scope it would be measured as this project's code: `ncloc` would be
mostly upstream, findings would be upstream's, and the analysis time would be spent on files this repository
does not own.

The coverage side needs no new measurement — `test-coverage` already has CI emit one Kover XML per Kotlin
module and one JaCoCo XML for `:osmscout-client-java`'s own sources — it needs to be handed to the scanner
with paths the multi-module layout resolves correctly.

## What Changes

- **A scan workflow, `.github/workflows/sonar.yml`.** Triggers `push` on `main` and `workflow_dispatch`
  (guarded to `main`, the shape `sensors.yml` uses, and the only branch kind SonarQube Cloud's free plan
  analyses). It checks out with submodules at `fetch-depth: 0` (Sonar's SCM/blame needs the history), sets up
  JDK 21, the Android SDK, and the Gradle cache, runs the JVM-only suite with its coverage reports, then runs
  `SonarSource/sonarqube-scan-action`.
- **A root `sonar-project.properties`** carrying the boundary explicitly: `sonar.projectKey=Framstag_NaviVeylin`,
  `sonar.organization=framstag`, `sonar.host.url=https://sonarcloud.io`, the module list, the exclusions, and
  the coverage report paths.
- **The exclusion list is the scope rule.** Out of analysis: `app/src/main/cpp/**` (the whole native tree,
  which carries libosmscout and this project's three own C++ files), the vendored client API inside
  `osmscout-client-java`, `tools/**`, `openspec/**`, `guidelines/**`, `licenses/**`, `vcpkg/**`, `**/build/**`
  and the generated-code patterns Kover already filters (`BuildConfig`, `R$*`, `dagger.hilt.*`, `*_Hilt*`,
  `*.Dagger*Component*`). In scope: the Kotlin and Java this repository owns — `app`, `auto`, `core`,
  `buildSrc`, and `osmscout-client-java/src/main/java`.
- **C++ is out, deliberately.** SonarCloud's C/C++ analysis is meaningful only behind a build wrapper or a
  compilation database; excluding the whole `app/src/main/cpp/**` tree is what makes the scan cheap, and it
  means this project's three own C++ files are not analysed either.
- **Coverage is imported, and stays report-only.** The per-module Kover XMLs (`:koverXmlReport`,
  `:app:koverXmlReport`, `:auto:koverXmlReport`, `:core:koverXmlReport`) and
  `:osmscout-client-java:jacocoTestReport` are declared as coverage inputs; the root merged Kover report and
  `jacocoVendoredTestReport` are not, because the merged report's module-relative paths are the known
  breakage and the vendored report measures libosmscout. `sonar.qualitygate.wait` is left unset, so a red
  quality gate colours the badge and the pull request without failing the workflow — consistent with
  `test-coverage`'s "coverage is report-only" position and with `guidelines/BuildEvidence.md` §7.
- **The action pin is recorded where every pin is recorded.** `tools/check-action-runtimes.sh`'s floor table
  gains the row for `SonarSource/sonarqube-scan-action` at its first Node 24 major (v7 — verified from the
  action's own `action.yml`; v6 declares `node20`), without which the checker reports the pin as unrecorded.
- **The badges land with the change.** The three `README.md` badge lines the owner wrote are committed here,
  so the file and the pipeline that feeds it change together.
- **Documentation gains the consumer.** `guidelines/BuildEvidence.md` §7 gets one rule naming the Sonar
  consumer (what it reads, that it is report-only, that it runs on `main` only), `guidelines/Build.md` §3 gets
  the command row, and `AGENTS.md`'s routing table routes "sonar / code-quality analysis" to it.

## Capabilities

### New Capabilities

- `sonar-analysis`: what leaves this repository for SonarQube Cloud — which workflow produces the analysis,
  when it runs and why only from `main`, which sources are in and out of the scan and why the boundary is
  declared rather than inferred, which coverage reports are imported, that the quality gate colours but does
  not fail the build, and that the pinned scanner action carries a recorded Node runtime floor.

### Modified Capabilities

- None. `test-coverage` still requires exactly what it required (one Kover XML per Kotlin module, one JaCoCo
  XML per `:osmscout-client-java` provenance, report-only, CI-generated and uploaded); this change adds a
  *consumer* of those reports and changes no requirement the capability already ships. `ci-action-runtimes`
  likewise keeps its contract — "an action pinned without a row SHALL be reported" — and this change satisfies
  it by adding the row, not by moving the requirement.

## Impact

**New files**: `.github/workflows/sonar.yml`, `sonar-project.properties`.

**Modified files**: `tools/check-action-runtimes.sh` (one floor row); `guidelines/BuildEvidence.md` (§7, one
rule); `guidelines/Build.md` (§3, one command row); `AGENTS.md` (one routing row); `README.md` (three badge
lines).

**Not touched**: no Gradle build file, no module source, no test source, no CMake or vcpkg input. The change
therefore adds no dependency to the build's classpath, so the licence policy and SBOM chain
(`licenses/license-policy.json`, `:app:generateSbom*`) are unaffected — which is the reason the scan runs as a
GitHub Action rather than through the SonarQube Gradle plugin.

**New credential**: a `SONAR_TOKEN` repository secret. This is the first secret outside `sensors.yml`, and its
exposure is bounded by the trigger choice: the workflow never runs on a pull request, so no contributor's code
is analysed with the token in scope.

**Required outside the repository**: SonarQube Cloud must have Automatic Analysis **disabled** for
`Framstag_NaviVeylin`; the documentation is explicit that a CI-based scan fails while it is enabled. The owner
performs this once, in the SonarQube Cloud UI.

**Scope**: build and CI tooling, for both distribution variants at once — one scan covers `app` (both
flavors), `auto` and `core`; it is not a phone-only or car-only change, and it adds no product behaviour.

**Classification**: additive, non-breaking. **Rollback**: revert the change's commit (the workflow, the
property file and the floor row disappear together), delete the `SONAR_TOKEN` secret and re-enable Automatic
Analysis; no build input, no persisted state and no shipped artifact depends on the analysis.

**Guidelines referenced**: `guidelines/BuildEvidence.md` §7 "Code coverage" (the rule the report-only choice
extends), `guidelines/Build.md` §2 (the scope block that decides what a delta with no compiled file must
verify) and §3 "Commands reference", `guidelines/BuildGate.md` §2e "The actions' Node runtime" (the pin rule
the floor row satisfies), `guidelines/BuildTests.md` §6e (the wall-clock/flake discipline the new workflow's
test step inherits).

surface: 7 requirements and 15 scenarios added, 2 new files (the scan workflow and `sonar-project.properties`),
1 new check plus its self-test, 1 pin floor row, 3 documentation places extended (`BuildEvidence.md` §7,
`Build.md` §3, the `AGENTS.md` routing row), 3 `README.md` badge lines —
`grep -c '^### Requirement:' openspec/changes/integrate-sonar-analysis/specs/sonar-analysis/spec.md`,
`grep -c '^#### Scenario:' openspec/changes/integrate-sonar-analysis/specs/sonar-analysis/spec.md`,
`git diff --name-only`.
