# fix-buildsrc-compiles-on-the-daemon-jvm

## Why

`buildSrc/build.gradle.kts:13-15` pinned a Java 17 **toolchain**:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

`buildSrc` is built as a build of its own and carries **no** `settings.gradle.kts`, so the
`org.gradle.toolchains.foojay-resolver-convention` plugin that the root `settings.gradle.kts:9` applies never
reaches it. Gradle can therefore only *auto-detect* a local JDK 17, and a machine without one fails at
configuration — before any task, in a message that names neither the file nor the fix:

```
./gradlew -Dorg.gradle.java.installations.auto-detect=false help
> Failed to calculate the value of task ':buildSrc:compileJava' property 'javaCompiler'.
  > Cannot find a Java installation on your machine (Linux 7.2.9-arch1-1 amd64) matching:
    {languageVersion=17, vendor=any vendor, implementation=vendor-specific, nativeImageCapable=false}.
    Toolchain download repositories have not been configured.
BUILD FAILED in 645ms
```

(`evidence/repro-on-head.log`, measured 2026-10-24. `-Dorg.gradle.java.installations.auto-detect=false` is the
proxy for the real condition — "no JDK 17 findable"; the workstation that filed issue #92 has one, which is why
the defect stayed invisible there and in CI.)

Root cause, in one sentence: **the build logic declares a JVM the build cannot provision — the only toolchain
repository the project configures reaches the main build, not `buildSrc`'s own — so a machine that satisfies the
daemon-JVM criteria can still fail to configure the build, naming neither the file nor the fix.**

The pin also bought nothing. `buildSrc` has no Java source at all (`:buildSrc:compileJava NO-SOURCE`), so the
toolchain only ever set Kotlin's `jvmTarget`; the JVM the build logic runs on is pinned by
`gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`, no vendor), which every machine that can run this
build already has.

Issue #92 (`TODO.md` §146) was filed on 2026-10-06 and parked by the loop as **not eligible — needs decision**
(gate condition 3): the only authority touching a JVM pin, `openspec/specs/build-jvm-toolchain/spec.md`, scopes
itself to the **daemon** JVM, so three fixes all satisfied it — keep the 17 and provision it, move the build
logic onto the daemon's JVM, or raise the pin to 21. Tim Teulings decided on 2026-10-24 for the second: the build
logic declares no JVM of its own. The alternatives the decision eliminated are named in `design.md`.

### The red test

`app/src/test/java/com/naviveylin/build/BuildSrcJvmRequirementTest.kt`, two of whose four cases are false on the
base tree (the scan, and the pipeline predicate it does not have yet):

```
./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.BuildSrcJvmRequirementTest" \
  -PforceTests -PjvmTestsOnly
BuildSrcJvmRequirementTest > a reintroduced toolchain is refused FAILED
BuildSrcJvmRequirementTest > the build script names no JVM of its own FAILED
    java.lang.AssertionError: buildSrc must declare no JVM toolchain of its own: … expected:<[]> but
    was:<[:13: toolchain {, :14: languageVersion = JavaLanguageVersion.of(17)]>
4 tests completed, 2 failed
```

JUnit XML `tests=4 failures=2 errors=0`, timestamp `2026-10-24T07:44:43.038Z` (`evidence/red-on-head.xml`). The
other two cases pass on the base tree by construction: the criteria case reads a file that is already correct
(the tripwire half) and the detectability case is exercised against fixtures.

Owning capability: `build-jvm-toolchain` — requirement added by this change, "The build logic compiles on the JVM
the daemon criteria select". Owning guideline section: `guidelines/Build.md` §2d, which states the daemon-JVM
rule and now the build logic's rule beside it.

### Surface

```
git status --porcelain — 4 paths: buildSrc/build.gradle.kts (10+/8−), .github/workflows/build.yml (12+/1−),
guidelines/Build.md (19+/6−), app/src/test/java/com/naviveylin/build/BuildSrcJvmRequirementTest.kt (new, 185 lines)
and, in its own commit, tools/check-action-runtimes.sh (mode 100644 → 100755, no content change): the gate's
suite entry for `.github/workflows/*` invokes that script as a bare path, so the pathspec this change owes could
not run it at all (see tasks.md §4).
grep -c toolchain buildSrc/build.gradle.kts — 3, all inside the comment that explains the absence (the live
instance of the detectability case: the scan and the CI predicate strip `//` comments, the line count alone
does not decide)
openspec/changes/archive/2026-10-24-fix-buildsrc-compiles-on-the-daemon-jvm/evidence/ — 19 files, each a reading,
none regenerated
surface: 1 case class added (4 cases), 0 cases rewritten, 0 fixtures realigned — the assertion surface is 3
places (the host case, the CI step's buildSrc predicate and the §2d check recipe), the last two of which this
change creates. Gate: `LOOP-GATE: tests=13 failures=0 errors=0` plus 5792 module tests from the XMLs, 0 failures.
```

## What changes

1. `buildSrc/build.gradle.kts` — the `java { toolchain { … } }` block goes, replaced by the comment that states
   why the build logic declares no JVM of its own. Additive in behaviour (the build logic compiles with the
   daemon JVM either way on a machine that has both), breaking for a machine that has only JDK 21 today and no
   longer needs a download: **the rollback path** is a revert of this commit, after which the toolchain is back
   and the two guards redden.
2. `app/src/test/java/com/naviveylin/build/BuildSrcJvmRequirementTest.kt` — the guard, four cases: the scan,
   the criteria that must keep pinning the JVM the build logic compiles with, the CI step's predicate, and the
   detectability case that proves the scan reports a toolchain or a `languageVersion` and ignores a comment and
   a compile *target* (`sourceCompatibility`, `jvmTarget`).
3. `.github/workflows/build.yml`, step "Check daemon JVM criteria" — a predicate that refuses a toolchain or a
   `languageVersion` in `buildSrc/build.gradle.kts` (comments stripped), so the pipeline fails in seconds
   instead of at configuration time.
4. `guidelines/Build.md` §2d — the closing paragraph now states the build logic's rule and names both guards,
   and the checks block carries the in-repository invocation as the reproduction (its old warning, "Do NOT run
   this in this repository", described exactly this defect).

No application source, no native code, no manifest, no dependency, no release state. The change is additive to
the shipped specs: one requirement with five scenarios, each naming one case or one command.

## Impact

| Surface | What it sees |
|---|---|
| a workstation with a JDK 17 | nothing — the build logic compiled with 17 before, and compiles with the daemon's 21 now |
| a workstation with only JDK 21 | the configuration no longer fails, and no JDK 17 is downloaded (the option "keep the pin and provision it" would have paid that download on every such machine) |
| CI | one more predicate in a step that already runs before the SDK/vcpkg work; Temurin 21 is the daemon JVM and the build logic's |
| `buildSrc`'s own tests | unchanged: they run on the daemon JVM the criteria select |

Modules touched: `buildSrc` (build logic only), `:app` tests, `.github/workflows/build.yml`, `guidelines/Build.md`.
