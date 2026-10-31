# Build Gate — recipes

Commands and transcripts for `guidelines/BuildGate.md`. Read beside the section it anchors to.

## §2d The build JVM (daemon JVM criteria)

The checks (none of them needs a device or a JDK download):

```bash
# the guard exactly as CI runs it (expect: "daemon JVM criteria: version 21, no vendor, no package id.", exit 0)
yq -r '.jobs.build.steps[] | select(.name=="Check daemon JVM criteria") | .run' .github/workflows/build.yml > /tmp/guard.sh && bash /tmp/guard.sh

# the same rule as a host case: the property source carries no org.gradle.java.home, and the pipeline's step
# reads it (expect: 3 tests, 0 failures)
./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.VersionedGradleJavaHomeTest" -PforceTests

# every declared platform resolves to that vendor's 21 archive (expect six 307s, tar.gz on unix, zip on windows)
grep '^toolchainUrl\.' gradle/gradle-daemon-jvm.properties | while IFS='=' read -r k raw; do u=$(printf '%s' "$raw" | sed 's/\\:/:/g'); printf '%-26s %s %s\n' "${k#toolchainUrl.}" "$(curl -sS -o /dev/null -w '%{http_code}' -L --max-redirs 0 "$u")" "$(basename "$(curl -sS -o /dev/null -w '%{redirect_url}' -L --max-redirs 0 "$u")")"; done

# the provisioning path itself, in a scratch project so no other requirement interferes: with
# auto-detection off, the only way to a daemon JVM is the criteria URL. Expect BUILD SUCCESSFUL and a
# provisioned JDK under the scratch home (measured 2026-10-06: eclipse_adoptium-21-amd64-linux.2 unpacked
# from OpenJDK21U-jdk_x64_linux_hotspot_21-any-vendor-21.0.12.1_1.tar.gz, the daemon running on it,
# BUILD SUCCESSFUL in 1m11s).
mkdir -p /tmp/djvm-probe/gradle && cp gradle/gradle-daemon-jvm.properties /tmp/djvm-probe/gradle/
printf 'rootProject.name = "probe"\n' > /tmp/djvm-probe/settings.gradle.kts
./gradlew -p /tmp/djvm-probe -g /tmp/djvm-probe-home -Dorg.gradle.java.installations.auto-detect=false help

# the build logic needs no second JVM (issue #92): the same invocation in THIS repository configures, because
# buildSrc declares no toolchain of its own and compiles with the daemon JVM the criteria select. Expect
# BUILD SUCCESSFUL. It was `BUILD FAILED in 645ms` — "Cannot find a Java installation … matching:
# {languageVersion=17 …}. Toolchain download repositories have not been configured." — while buildSrc pinned a
# Java 17 toolchain that no machine could provision; measured 2026-10-24.
./gradlew -Dorg.gradle.java.installations.auto-detect=false help
```

A green `assembleDebug` proves nothing about this on a workstation that has JBR 21 — the download never
happens there. The CI run is the only evidence that the runner's installed Temurin 21 is accepted (if a run
still provisions, the criteria are not the reason the daemon starts, and the fallback is to install the same
JBR the criteria would name). The invocation at the end of the block above is the reproduction: green now,
`BUILD FAILED in 645ms` before.
