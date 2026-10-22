# Design — fix-versioned-gradle-java-home-guard

## Root cause

The daemon JVM has two surfaces and the tree asserted only one of them. The criteria file
(`gradle/gradle-daemon-jvm.properties`) is checked three ways in the CI step "Check daemon JVM criteria" —
`toolchainVersion=21`, no `toolchainVendor`, no foojay package id — and the property source that *cannot*
select the daemon (`gradle.properties`, which the criteria override) was left to prose in
`guidelines/Build.md` §2. #91 deleted the offending line and its two quoting sites and named the guard case as
a step; the step was never implemented, so the rule exists only in a document nobody's build reads.

One mechanism, not two. The item's premise holds in the tree: the line is absent today
(`git show HEAD:gradle.properties`), the CI step reads only the criteria file (`build.yml:327-344`), and no
case reads `gradle.properties`. Because the tree is *correct*, the assertion that is false on HEAD is not
"the line is present" but "the guard reaches the line": the red case is the one about the pipeline's scope.

## The single fix

Two surfaces, one rule, and no new mechanism for either:

1. **Host case** — `app/src/test/java/com/naviveylin/build/VersionedGradleJavaHomeTest.kt`, in the package the
   project already keeps its build-configuration guards in, reading the repository root as `File("..")` the
   way `GradleScriptDeprecationTest` does. Three cases: the scan of `gradle.properties`; the step read out of
   the workflow (a host case cannot run a workflow, exactly as `GradleScriptDeprecationTest` reads the scripts
   instead of the Gradle log); and the detectability case, so a passing scan means the line is absent rather
   than that the matcher cannot see it.
2. **CI step** — the existing step grows four lines: `grep -nE '^[[:space:]]*org\.gradle\.java\.home[[:space:]]*='`
   over `gradle.properties`, `::error` and `exit 1` on a hit. It sits in the step that already fails in
   seconds, before the SDK and vcpkg work, so a reintroduced line costs a 20-second job rather than a
   nine-minute one.

`guidelines/Build.md` §2 states the paragraph as the rule and now names both surfaces; its checks block gains
the focused command. Nothing in the workflow's other steps, no task name, no Gradle configuration changes.

## Blast radius

- `app/src/test/java/com/naviveylin/build/VersionedGradleJavaHomeTest.kt` (new, three cases).
- `.github/workflows/build.yml`, the step "Check daemon JVM criteria": +4 lines, no other step touched.
- `guidelines/Build.md` §2: two sentences and one line in the checks block.
- The capability delta (`build-jvm-toolchain`, one requirement, three scenarios).

No application source, native code, manifest, dependency, resource, release-version state or Gradle script. The
`app` unit suite runs the three cases in both flavors; the fork budget of the module is unchanged (measured:
`time="0.217"` for the class in the green run).

## Alternatives the evidence eliminated

- **A shell gate (`tools/check-build-jvm.sh` + a selftest with fixtures) instead of a host case.** It would need
  wiring into the gate and into CI, a fixtures directory and a case registry — three new files for one grep —
  and the `:app` suite already runs in the documented gate and in CI's test step with no wiring. The nearest
  neighbour, the Gradle-10 script sweep, made the same choice for the same reason.
- **Scan `local.properties.template` too.** Eliminated by measurement: the template's
  `org.gradle.java.home=/path/to/jdk-17` is `JAVA_HOME` for the native build — `setup-vcpkg.sh:29` reads it
  out of the generated `local.properties` — so it is live and must stay. Only the file Gradle itself reads
  project-wide is in scope, and a second mechanism (moving the native build's JDK source) is out of this item.
- **A scan-only hook (a system property or a constructor parameter so the falsification could point the scan at
  a fixture file).** Eliminated: it makes the guard's failure path reachable only through the plumbing the
  falsification uses, and the same liveness is proved by the detectability case against real lines, which is
  the project's existing idiom.
- **Rely on the prose rule in `guidelines/Build.md` §2.** That is exactly the state #91 left and this item
  measured: the paragraph said the line must not exist, and nothing failed for 16 days when it did.
- **Make the CI step the only surface.** Eliminated: the pipeline is only reachable by a push, so a contributor
  without a fork and a workflow run gets no signal; the host case reddens in the suite everybody runs.

## Rollback

Revert the commit. The workflow step loses four lines and the host case disappears; the rule returns to
prose-only, which is how the tree stood before. No state, no data, no build output and no release version
moves. The check's green path in a correct tree is unchanged (`daemon JVM criteria: version 21, no vendor, no
package id.`).
