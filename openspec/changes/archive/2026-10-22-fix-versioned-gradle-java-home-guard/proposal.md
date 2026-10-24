# fix-versioned-gradle-java-home-guard

## Why

`gradle.properties` carried `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk` — machine-local, and inert:
the checked-in daemon JVM criteria (`gradle/gradle-daemon-jvm.properties`, `toolchainVersion=21`) take
precedence over it (Gradle 9.6.1, "Daemon JVM Toolchains"), and CI passes
`-Dorg.gradle.java.home="$JAVA_HOME"` per invocation. #91 (`5b511ec`) deleted the line and the two prose sites
that quoted it, and its own evidence file named a further step it did not implement — "Then the guard case: a
host test asserting the versioned Gradle config carries no absolute `org.gradle.java.home` path"
(`.pi/issue-loop/items/91/evidence-policy-blocked-80df3a3.md:77-78`). Nothing in the tree therefore fails when
the line returns, and it returns easily: two archived designs still show it as the setting to use
(`openspec/changes/archive/2026-08-18-github-build/design.md:19`,
`openspec/changes/archive/2026-10-05-speed-up-build-test-gate/design.md:23`).

Verified against the tree today:

- `gradle.properties` (`git show HEAD:gradle.properties`) carries no `org.gradle.java.home` line — the state
  #91 left.
- `grep -rn "org\.gradle\.java\.home" .github/workflows/build.yml` finds five invocations and the comment
  block at `:458-464` that explains why the flag does not select the daemon; the step "Check daemon JVM
  criteria" (`:327-344`) checks only `gradle/gradle-daemon-jvm.properties` — the version, the vendor
  criterion and the foojay package ids.
- No case reads `gradle.properties` on the base tree:
  `grep -rn "gradle\.properties" app/src/test auto/src/test tools .github` finds 3 hits — the workflow's cache
  key, the workflow's own explanatory comment, and one fixture string in `tools/resolve-stale-cases.sh:338` —
  and no assertion.

Root cause, in one sentence: **the tree asserts the daemon-JVM rule only against the criteria file, so the
project-wide property source — the one place a machine-local path is silently wrong — has no assertion at
all, and re-adding the line reddens nothing.**

### The red test

`app/src/test/java/com/naviveylin/build/VersionedGradleJavaHomeTest.kt`, case "the pipeline refuses a
reintroduced line", on the base tree — the CI step "Check daemon JVM criteria" reads only the criteria file:

```
./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.VersionedGradleJavaHomeTest" -PforceTests
VersionedGradleJavaHomeTest > the pipeline refuses a reintroduced line FAILED
    java.lang.AssertionError: the CI step "Check daemon JVM criteria" must read the versioned property source,
    so a reintroduced org.gradle.java.home fails the pipeline instead of silently selecting no JVM
3 tests completed, 1 failed
```

JUnit XML `tests=3 failures=1`, timestamp `2026-10-22T08:48:01.161Z` (`evidence/red-pipeline-case.xml`). The
other two cases of the same class pass on the base tree by construction: the property source is correct today
(the tripwire half) and the matcher is exercised against fixtures (the detectability half) — this item is a
missing guard, so the assertion that is false on HEAD is the one about the guard's own reach.

Owning capability: `build-jvm-toolchain` — requirement added by this change, "The versioned property source
names no Java home". Owning guideline section: `guidelines/Build.md` §2, "The build JVM (daemon JVM criteria)",
which already states the absence as a rule and now names the guard that enforces it.

### Surface

```
git grep -l "org\.gradle\.java\.home" -- .: merged tree 30 versioned files, 20 of them archived changes;
base tree (this change's 8 files and its new test class excluded) 21 files, 12 of them archived — the 9-file
gap is this change. Measured 2026-10-22. `grep -rl --exclude-dir=build` reads 22 on the base because it also
skips the test class's `com/naviveylin/build/` package and counts the untracked `local.properties`
grep -rn "gradle\.properties" app/src/test auto/src/test tools .github: 3 hits on the base tree, none an assertion
grep -c "org\.gradle\.java\.home" .github/workflows/build.yml: 10 after the change — 9 pre-existing lines
(66, 71, 462, 468, 475, 482, 491, 504, 525) plus the added `::error` line at :345; the added predicate at :344
spells the key escaped (`org\.gradle\.java\.home`), so this quoted grep does not match it
```

surface: 1 case class added (3 cases), 0 cases rewritten, 0 fixtures realigned — the assertion surface is 2 places (the host case and the CI step), the second of which this change creates — measured: `grep -c "org\.gradle\.java\.home" .github/workflows/build.yml` = 10 lines (9 pre-existing, 1 added); of the four added workflow lines `:344-347`, `:344` spells the key escaped, `:345` unescaped, and `:346-347` carry no key

## What changes

1. `app/src/test/java/com/naviveylin/build/VersionedGradleJavaHomeTest.kt` — the guard, three cases: the scan
   of `gradle.properties`, the same rule read out of the workflow's daemon-JVM step, and the detectability
   case that proves the scan reports an offending line and ignores a comment.
2. `.github/workflows/build.yml` — the step "Check daemon JVM criteria" refuses an `org.gradle.java.home` line
   in `gradle.properties` with `::error`, so the pipeline fails in seconds rather than after the SDK/vcpkg
   work.
3. `guidelines/Build.md` §2 — the paragraph that states the absence now names the guard (the step and the
   case), and the checks block carries the focused command.

No application source, no native code, no manifest, no dependency, no release state. The capability delta is
one requirement with three scenarios, each naming exactly one case.
