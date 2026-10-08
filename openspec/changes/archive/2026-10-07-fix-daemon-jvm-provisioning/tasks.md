# Tasks

## 1. Daemon JVM criteria file (spec: `build-jvm-toolchain`)

- [x] 1.1 Drop the vendor criterion in `gradle/gradle-daemon-jvm.properties`: delete the
      `toolchainVendor=JETBRAINS` line, keep `toolchainVersion=21`. Verify: `grep -c toolchainVendor`
      reads `0` and `grep toolchainVersion` reads `21`. (Requirement: *names a version, not a vendor*)
- [x] 1.2 Replace the ten `toolchainUrl.*` foojay package-id URLs with the six durable Adoptium
      endpoints (`https://api.adoptium.net/v3/binary/latest/21/ga/<os>/<arch>/jdk/hotspot/normal/eclipse`
      for linux/mac/windows x64+aarch64), delete the `FREE_BSD` and `UNIX` entries, and add the header
      comment stating that the file is generated but its URLs are maintained by hand, with the reason.
      Verify: `grep -c 'disco/v3.0/ids'` reads `0`; `grep -c '^toolchainUrl\.'` reads `6`; the comment
      is the file's first lines. (Requirement: *provisioning URLs are durable vendor endpoints*)
- [x] 1.3 Check every declared endpoint: `curl -sS -o /dev/null -w '%{http_code}' -L --max-redirs 0 <url>`
      for all six returns `3xx`, and each `redirect_url` names that vendor's 21 archive (e.g.
      `adoptium/temurin21-binaries/releases/download/jdk-21.0.x/...`). Record the six code/target pairs
      in the change. (Scenario: *every declared platform resolves to its vendor archive*)
- [x] 1.4 Exercise the provisioning path once, since no other check proves Gradle accepts these archives,
      in a scratch project (not this repository — `buildSrc` needs a locally detectable JDK 17, `TODO.md`
      §146): `mkdir -p /tmp/djvm-probe/gradle && cp gradle/gradle-daemon-jvm.properties /tmp/djvm-probe/gradle/ &&
      printf 'rootProject.name = "probe"\n' > /tmp/djvm-probe/settings.gradle.kts &&
      ./gradlew -p /tmp/djvm-probe -g /tmp/djvm-probe-home -Dorg.gradle.java.installations.auto-detect=false help`
      must end `BUILD SUCCESSFUL` and leave a provisioned JDK under `/tmp/djvm-probe-home/jdks` that the
      daemon runs on. Afterwards confirm the ordinary path is unchanged: `./gradlew help` still does not add a
      daemon in the real Gradle home and the JBR 21 daemon stays in place (`ps -o args= -p <pid>` shows
      `/home/tim/.jdks/jbr-21.0.11/bin/java`). (Scenarios: *a machine without the required JDK provisions
      once*, *workstation keeps the JVM it already uses*)

## 2. CI workflow (spec: `build-jvm-toolchain`)

- [x] 2.1 Add `~/.gradle/jdks` to the `Cache Gradle` step's `path` list in
      `.github/workflows/build.yml`; verify the step's path list names all three directories.
      (Design D4)
- [x] 2.2 Add the criteria guard as a workflow step, in the shape of the existing hygiene steps: fail when
      `gradle/gradle-daemon-jvm.properties` contains `/disco/v3.0/ids/` or has no `toolchainVersion=21`
      line, with an actionable `::error::` message. Verify locally by running the step's script against
      the real file (exit `0`).
- [x] 2.3 Revert-check the guard (the change's new invariant): mutate the criteria file by re-adding one
      `https://api.foojay.io/disco/v3.0/ids/deadbeef/redirect` line, run the guard script — it MUST exit
      `1` and name the file; restore the file, run it again — exit `0`; then re-run 1.3/1.4 green.
      (Scenario: *a pruned third-party record cannot break the build*)
- [x] 2.4 Correct the daemon-JVM comment on the `Set up JDK 21` / `Build debug APK` steps: state that
      daemon JVM criteria take precedence over `-Dorg.gradle.java.home`/`JAVA_HOME`, and that both stay
      because `setup-vcpkg.sh` reads the JDK path from `local.properties`. Verify by reading the comment
      against the Gradle 9.6.1 daemon manual section *Daemon JVM Toolchains*.

## 3. Guidance and backlog (spec: `build-jvm-toolchain`)

- [x] 3.1 Document the rule in `guidelines/Build.md`: the daemon JVM requirement is version 21 with no
      vendor; provisioning URLs are vendor-owned stable endpoints, never index package ids; regeneration
      by the `updateDaemonJvm` task is deliberate and its generated URLs must be replaced before
      committing; plus the three verification commands from 1.3, 1.4 and 2.2. Verify: the section states
      version, vendor-neutrality and the caveat, and is readable from a fresh checkout
      (`git show HEAD:guidelines/Build.md`). (Requirement: *the JVM rule and the regeneration caveat are
      checked in*)
- [x] 3.2 Check `AGENTS.md` and `README.md` for statements about the build JVM that this change
      invalidates; update them or record that none mention it (verification = the grep result for
      `daemon|toolchainVendor|jbr|JDK 21` over both files).
- [x] 3.3 File the adjacent finding outside this change in `TODO.md` as its own entry:
      `gradle.properties` commits a machine-local `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk`
      that every other machine (CI included) must override, and which the archive gate's
      "no hardcoded paths" rule would flag. Metadata line present (`id`/`category`/`class`/`status`).
      Verify: the entry exists with all four fields and does not duplicate an open entry.

## 4. Integration verification

- [x] 4.1 Build starts on the new criteria: `./gradlew :app:assembleMobileDebug
      -Pandroid.injected.build.abi=arm64-v8a` completes and its output contains neither
      `Unable to download toolchain` nor a request to `api.foojay.io`. (Scenarios: *CI runner starts on
      the JDK the workflow installed*, *no routine run pays for a download* — the local half)
- [x] 4.2 Existing tests still pass on the changed build configuration: `./gradlew
      :app:testMobileDebugUnitTest -PnoCoverage` (one flavor — the change touches only shared,
      non-code inputs), quoting the executed-class and test tallies.
- [x] 4.3 Push and read the CI run: the `Build debug APK` step passes and the log shows no toolchain
      download and no `api.foojay.io` request. Record the observed lines. If the runner still provisions,
      record that in the change (design Open Question) and evaluate D1's JBR-in-CI fallback.
- [x] 4.4 Record the scenario→case mapping for the spec in the change (a `traceability.md` next to
      `tasks.md`): one row per delta scenario naming the task that exercises it. Two scenarios cannot be
      produced on this machine and must say so — *a platform without a vendor build says so itself* (no
      FreeBSD/UNIX host) and *a regenerated file is not committed as generated* (the guard from 2.2/2.3
      is the substitute). (Requirement: every scenario traceable to a task)

## Workflow follow-up

- Run the routine gate for the completed change (both `:app` flavors once, per `build-test-gate`) and
  confirm `openspec validate --strict` stays green.
- Archive the change after review, then verify the archived delta landed in
  `openspec/specs/build-jvm-toolchain/spec.md`.
