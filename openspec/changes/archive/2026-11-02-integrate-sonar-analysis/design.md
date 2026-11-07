# Design

## Context

See `proposal.md` — Why, for the motivation. The constraints this design works inside:

- **The submodule is on disk and is not this project's code.** `app/src/main/cpp/libosmscout` is a gitlink; its
  ~6,000 C/C++ files plus `libosmscout-client-java/java` (compiled into `:osmscout-client-java`'s `vendored`
  source set) are checked out for the build, and that tree carries its own Sonar project.
- **Coverage already exists.** `test-coverage` has CI emit `:koverXmlReport` per Kotlin module and
  `:osmscout-client-java:jacocoTestReport` (own sources) / `jacocoVendoredTestReport` (submodule sources)
  after the unit-test step. Nothing new has to be measured for Sonar; the reports have to be pointed at.
- **A pin gate already exists.** `tools/check-action-runtimes.sh` requires a recorded Node 24 floor per
  pinned action, and reports an action it does not record as a defect. Its floor table is the only list.
- **A credential-isolation precedent already exists.** `sensors.yml` is a separate workflow file precisely so
  the pipeline that validates changes never carries the tracker's credential.
- **The workflow that validates changes is heavy.** `build.yml` is a single job with a 360-minute timeout; it
  installs the NDK and builds vcpkg for all ABIs. A scan does not need any of that.
- **CI-based analysis and automatic analysis are mutually exclusive** on SonarQube Cloud; a scanner run fails
  while automatic analysis is enabled for the project.

## Goals / Non-Goals

**Goals**

- One scan of the main branch that measures only this repository's Kotlin and Java, with the boundary legible
  in the repository rather than in a UI setting.
- Coverage in the analysis from the reports the existing test run already produces, per module.
- No new build dependency: the licence policy and SBOM chain must not have to learn about a new component.
- A scan failure that names its cause, so the remedy is obvious from the log.

**Non-Goals**

- Pull-request analysis and pull-request decoration. v1 analyses `main` only; the trigger decision is revisited
  as its own change if decoration is wanted.
- C/C++ analysis. Without a build wrapper or compilation database it is not meaningful, and it would put the
  submodule's tree back in scope.
- A second measurement authority. The scan imports the existing reports; it does not introduce a new coverage
  figure for the sensor registry's `coverage` row to disagree with.
- Extracting the shared CI preamble (SDK install, caches) into a composite action. The new workflow copies the
  steps it needs from `build.yml`; the extraction is its own change with its own risk.

## Decisions

### D1 — The scan runs as a GitHub Action, not through the SonarQube Gradle plugin

Alternatives: (a) `SonarSource/sonarqube-scan-action` in a workflow with a root `sonar-project.properties`;
(b) the `org.sonarqube` Gradle plugin in the root `build.gradle.kts`, invoked as `./gradlew sonar`; (c) keep
SonarQube Cloud's automatic analysis and only maintain the badges.

(b) derives the module list and the JaCoCo report paths from the Gradle model, which removes the hand-written
module mapping D5 has to get right — but it puts a plugin on the build's classpath. In this repository that is
not free: `licenses/license-policy.json` classifies components into `shipped` and `buildTimeOnly`, the SBOM
task walks the JVM dependency graph, and a new build-classpath dependency is a new component for that chain to
classify and for `guidelines/Build.md` §9's gate to accept. It also needs network access at configuration and
scan time inside a build the project deliberately keeps offline-verifiable.

(c) needs no code at all, but automatic analysis ignores the repository's exclusion list for the parts it can
see, reports no coverage, and cannot be reconciled with a badge that is supposed to reflect this project's own
measure — and it is mutually exclusive with (a) anyway, so choosing it means never having a CI scan.

(a) is chosen: the exclusion list *is* the deliverable, it lives in a file a reviewer can read and diff, and
the build's dependency graph does not change.

### D2 — The scan runs from `main` and from an explicit dispatch, never from a pull request

Alternatives: (a) `push: [main]` + `workflow_dispatch`, guarded to `main`; (b) the same plus `pull_request`
with a guard that the head repository is this repository; (c) `workflow_run` after `build.yml` completes, to
cover fork pull requests as well.

(b) and (c) buy pull-request decoration at the cost of running a credentialed scan against code that is not
merged — and (c) is exactly the pattern that executes contributor code with the secret in scope. This
repository already separated its credentialed pipeline from its validation pipeline for that reason
(`sensors.yml`'s header), and the volume of this project's changes does not yet justify reversing that. (a)
also matches the only branch kind the free plan analyses: a scan of a feature branch would be refused by the
server, so an unguarded dispatch would be a trap.

### D3 — In scope: this repository's Kotlin and Java. Out: the whole native tree, including our own C++

Alternatives: (a) Kotlin + Java only, excluding `app/src/main/cpp/**` entirely; (b) the same plus this
project's own C++ under `app/src/main/cpp/` (`native_log_bridge.cpp`, `iconv_stub.c`, `placeholder.cpp`),
excluding only `libosmscout/**`; (c) everything, including the submodule.

(c) is what the project was created against and is what the boundary has to remove: it reports upstream
findings and inflates `ncloc` with code this repository does not own. (b) is the more complete answer, but
SonarCloud's C/C++ analysis works off a build wrapper or a compilation database; producing one means a native
configure step in the scan workflow, which is the cost the exclusion was meant to avoid — and the three own
files are small enough that the loss is legible rather than hidden. (b) is the natural follow-up if those
files grow.

### D4 — Coverage imported from the per-module reports, never the merged one

Alternatives: (a) the four per-module reports (`:koverXmlReport`, `:app:koverXmlReport`, `:auto:koverXmlReport`,
`:core:koverXmlReport`) plus `:osmscout-client-java:jacocoTestReport`; (b) the root merged Kover report plus
the JaCoCo report; (c) no coverage import.

(b) is the tempting shortcut and the known failure: a merged report carries module-relative source paths that
the scanner resolves against the project base directory, so entries land on the wrong file or nowhere, and the
project would look less covered than it is. (c) leaves the coverage badge meaningless. (a) is per module, so a
path that resolves at all resolves to the right module.

`jacocoVendoredTestReport` is deliberately not imported: its classes are the submodule's, and importing them
would put libosmscout back into the analysis through the coverage door (`sonar.coverage.exclusions` would be
the only thing standing between the two).

### D5 — Module mapping is declared per module, with the merged/aggregate paths kept out of it

Alternatives: (a) `sonar.modules` naming `app`, `auto`, `core`, `osmscout-client-java`, each with its own
`sonar.<module>.sonar.sources`, `sonar.<module>.sonar.tests` and coverage report path; (b) one flat project
whose `sonar.sources` lists the module source directories and whose `sonar.coverage.jacoco.xmlReportPaths`
lists all reports.

(b) is shorter and is what most single-module projects use, but it is also the configuration under which the
path-resolution problem in D4 appears: a source file declared as `app/src/main/.../Foo.kt` is not the same key
as the `Foo.kt` a module-relative report names. (a) is longer and is the shape that makes each module's report
resolve against that module's own base directory. Because neither can be proven without a real scan, this is
the risk in "Risks / Trade-offs" rather than a settled fact: the first scan on `main` is the measurement, and
the fallback if (a) still mis-resolves is the Gradle plugin rejected in D1, taken as its own change.

`sonar.java.binaries` has to be populated, because SonarJava analyses bytecode as well as source, and the
Kotlin analyzer needs the Java classpath for mixed files. The workflow therefore compiles by running the test
task rather than by adding a compile-only invocation: the test task already compiles exactly the source sets
that are in scope and produces the coverage reports D4 needs, in one Gradle invocation.

### D6 — The quality gate colours the badge and gates nothing

Alternatives: (a) leave `sonar.qualitygate.wait` unset, so the scan publishes and the workflow's status is the
scanner's outcome; (b) set it, so a red gate fails the workflow.

(b) is a new gate. This project's coverage position is explicitly report-only
(`guidelines/BuildEvidence.md` §7: "coverage is report-only — no threshold gate"), and the sensor registry
already owns the one place where a coverage floor is applied — deliberately, on the two
`:osmscout-client-java` keys, and with its own row definition. Adding a second, invisible-in-the-registry
threshold would make a red build attributable to a UI setting that no guideline states. (a) keeps the failure
signal where it is readable (the badge, the pull-request decoration once D2 is revisited) without inventing a
gate. A *scanner* failure still fails the workflow, because a scan that did not happen must never read as one
that passed.

### D7 — The scan workflow runs its own JVM-only test pass

Alternatives: (a) the scan workflow runs `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` and the
coverage report tasks itself; (b) it downloads `build.yml`'s `coverage-reports` artifact by run id and compiles
separately for `sonar.java.binaries`; (c) the scan moves into `build.yml` as a job after the coverage step.

(c) was the cheapest shape and was set aside deliberately: it would put the scanner credential inside the
pipeline that validates every change, which is the separation D2 preserves. (b) saves test minutes but needs
the artifact API, a run-id lookup, and a second compile anyway, for a workflow that only runs on `main`. (a)
is one self-contained job whose test invocation is the one `guidelines/Build.md` §3 already documents, so the
scan is reproducible by hand with the same command. The cost is real — a second JVM test run per push to
`main` — and is the trade-off recorded below.

### D8 — The scanner runs where Sonar can deny it, so refusals are named

Alternatives: (a) the workflow's scan step is a plain action invocation; (b) the step is a shell step that
first asserts the project's automatic-analysis state through the API and then invokes the scanner.

(b) needs an API credential and a second network contract for a condition that is a one-time project setting.
(a) is chosen, and the refusal is handled where it is observable: the scan's own failure text names automatic
analysis, and the workflow fails with it. `proposal.md`'s Impact section names the setting as the owner's
one-time action, so the failure is expected once and understood in advance.

## Open decisions

none

## Risks / Trade-offs

- **Kover XML paths do not resolve under D5's per-module mapping** → the first scan on `main` is the
  measurement: compare the reported coverage per module against `tools/sensors/metrics/coverage.sh`'s summary
  for the same commit (app, auto, core, osmscout-client-java own). A figure materially below the summary means
  the path resolution is wrong; the fallback is the Gradle plugin from D1 as its own change. The scan is
  report-only, so a wrong figure costs a badge, never a red build.
- **A second JVM test run per push to `main`** → the workflow reuses the Gradle cache and runs
  `-PjvmTestsOnly`, so it compiles no native target and installs no NDK or vcpkg. If the added minutes become
  the dominant CI cost, D7's option (b) is the mitigation, taken on its own evidence.
- **The `SONAR_TOKEN` is a new repository secret** → it is scoped to analysis for one project, the workflow
  never runs on a pull request, and the file that carries it is not the pipeline that validates changes. If
  the token is ever needed by another workflow, that is a decision to take deliberately rather than a
  precedent this one sets.
- **The exclusion list drifts from the layout** → the paths it names are the ones the proposal lists, and the
  only events that invalidate them are a module rename or a new module. The scan reports a *rise* in `ncloc`
  that a change did not cause, which is the visible signal; the `sonar-analysis` capability's first scenario
  is the check that the submodule never returns to the analysed set.
- **Automatic analysis stays enabled** → the scan fails with the server's refusal, which the workflow reports
  as-is. This is a one-time configuration step, named in `proposal.md`, not a code defect; the change is
  otherwise complete without it.
- **The floor row is wrong for a future scanner major** → the floor is recorded in the same table that already
  gates `build.yml`'s pins, with the `action.yml` URL it was read from, so the next major is a one-line edit
  and the check reports a pin below it.

## Migration Plan

1. Land the workflow, the property file, the floor row and the documentation together — the floor row is what
   keeps the repository's own pin check green, so it cannot be a follow-up.
2. Owner, before the first scan: turn off automatic analysis for `Framstag_NaviVeylin` and add the
   `SONAR_TOKEN` secret.
3. First scan on `main` is the acceptance measurement: per-module coverage against the sensor summary, and
   `ncloc` consistent with Kotlin + Java only. Record both in the change.
4. Rollback: revert the commit (workflow, property file, floor row and badges go together), delete the secret,
   re-enable automatic analysis. Nothing in the build, the app or the persisted state depends on the scan.

## Threading and lifecycle

No new component runs in the app: the change adds a CI job and a configuration file, and no code that executes
on device or on a host thread. The only concurrency is CI-side — the workflow's own job, whose Gradle
invocation inherits the test-JVM concurrency the suite already declares
(`guidelines/BuildTests.md` §6d) and the wall-clock discipline of the existing unit-test step.

## Verification

The change touches no compiled file, so `guidelines/Build.md` §2's scope block applies: no build and no suite
is owed, and the structural checks stand in.

- Unit tests: none. No production code, no test code and no Gradle file changes, so there is no behaviour to
  case. This is the named exclusion `guidelines/Build.md` §2's scope rule asks for.
- Structural checks: `tools/check-action-runtimes.sh` with the new floor row, plus
  `tools/check-action-runtimes-selftest.sh` (its own case 5 runs the checker against this repository's
  workflows); `tools/check-doc-routes.sh` and `tools/check-rule-routes.sh` after the `AGENTS.md` routing row;
  the workflow YAML parsed by the runner on its first execution.
- Device/on-device checks: none. Nothing on either surface changes.
- Acceptance measurement: the first scan of `main`, reading per-module coverage (D4/D5's risk) and the `ncloc`
  the boundary leaves (D3), against the coverage summary the same commit's build published.
