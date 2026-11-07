# Design — fix-buildsrc-compiles-on-the-daemon-jvm

Bug-fix exception (the alternatives are the ones the root-cause evidence eliminated, not a menu). The root cause
is established: `buildSrc/build.gradle.kts:13-15` pins a Java 17 toolchain, `buildSrc/` has no
`settings.gradle.kts`, and the configuration fails with "Toolchain download repositories have not been
configured." before any task (`evidence/repro-on-head.log`). Issue #92 asked the one question the tree could not
answer; Tim Teulings answered it on 2026-10-24, and the three candidate answers were measured against this tree
the same day.

## D1 — which JVM the build logic compiles with

**Chosen (C): the build logic declares no JVM of its own and compiles with the daemon JVM the criteria select.**

- **(A) Keep the 17 and provision it** — `buildSrc/settings.gradle.kts` applying
  `org.gradle.toolchains.foojay-resolver-convention` 1.0.0, the plugin the root `settings.gradle.kts:9` applies.
  Eliminated by consequence, not by taste: on a machine with only JDK 21 this **downloads a JDK 17 while
  configuring the build**, for a build-time artefact that ships nowhere, and the archived change
  `fix-daemon-jvm-provisioning` wrote the rule against exactly that ("No routine run pays for a download" — spec
  `build-jvm-toolchain`). It also puts the resolver's version in a second file that must be kept in step with the
  root one.
- **(B) Raise the pin to a toolchain 21.** Not impossible, but it can only be satisfied by the JVM already running
  the build (the daemon's own 21 is `javaToolchains`' "Detected by: Current JVM"), so the declaration re-asks for
  what is already there and a future daemon bump must edit it in step — a second number to keep honest for no
  behaviour.
- **(B2) Keep 17-bytecode build logic, compiled by the daemon JVM** — `sourceCompatibility`/`targetCompatibility`
  = 17 plus Kotlin `jvmTarget = JVM_17`, the shape the app modules use. Measured 2026-10-24:
  `sourceCompatibility = 17` alone reddens the Kotlin compile —
  `Inconsistent JVM-target compatibility detected for tasks 'compileJava' (17) and 'compileKotlin' (21)` — so it
  costs the explicit Kotlin target as well, and what it buys is a class-file version for a build-time artefact:
  `buildSrc` carries no Java source at all (`:buildSrc:compileJava NO-SOURCE`), so nothing there is shipped or
  read outside Gradle.
- **(C)** costs nothing, downloads nothing, and makes the rule true by construction. Its risk — the build logic's
  bytecode follows the daemon JVM, 21 today — is accepted for the reason above, and bounded by
  `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`, no vendor), which
  `BuildSrcJvmRequirementTest` reads so the absence cannot drift into "no pinned JVM at all".

## D2 — the guard is a scan plus the CI predicate, not a nested Gradle invocation

A host case cannot run Gradle on the build it is itself part of: the `:app` test JVM sits inside that build, so a
nested configuration phase would (a) cost a second configuration per run, (b) be the one thing the defect breaks —
with the toolchain back, the nested invocation fails and cannot say which file is wrong. The scan names file and
line in its assertion, the CI predicate refuses the same thing in seconds before the SDK/vcpkg work, and the
**behavioural** proof stays the command the issue itself reproduces with:
`./gradlew -Dorg.gradle.java.installations.auto-detect=false help` — `BUILD FAILED in 645ms` before,
`BUILD SUCCESSFUL in 3s` after (`evidence/repro-on-head.log`, `evidence/repro-after.log`). `guidelines/Build.md`
§2d now prints that command as a check of this repository instead of warning against it.

## D3 — what the scan must not report

A compile **target** is not a JVM requirement: `sourceCompatibility`, `targetCompatibility` and Kotlin's
`jvmTarget` name no installation, and if (B2) is ever revisited the guard must not forbid it. Both the case and
the CI predicate strip `//` comments, so the file may explain the rule it must not violate — this change's own
comment names a toolchain three times, which is that reading's live instance.

## Verification (nothing device-bound)

| what | how |
|---|---|
| the defect | `./gradlew -Dorg.gradle.java.installations.auto-detect=false help` — failed before, succeeds after |
| the fix's own case | `BuildSrcJvmRequirementTest` in `:app`, forced, red on HEAD (`tests=4 failures=2`), green after (`tests=4 failures=0`) |
| the guards | the mutation (the pin returns) reddens the CI step (`exit 1`, `::error file=buildSrc/build.gradle.kts`) and the named case (`failures=1`), and the byte-identical restore is green again — `evidence/revert-check.txt` |
| the docs | `bash tools/check-doc-routes.sh`; `openspec validate` (change, `--strict`) and `--specs` |
| the change's scope | `bash tools/declared-cases.sh fix-buildsrc-compiles-on-the-daemon-jvm`; `./tools/loop/gate.sh` once |

Threading and lifecycle: no new component, no runtime path — the changed files are a build script, a workflow
step, a guideline section and a host case. Coverage: none of the four is application code, so no coverage
threshold moves.
