# Traceability — `fix-daemon-jvm-provisioning`

Every delta scenario of `specs/build-jvm-toolchain/spec.md` with the case or observation that exercises it.
Cases are the change's own tasks (`tasks.md`); the numbers are what was measured, not what was expected.

| # | Requirement → Scenario | Exercised by | Observed |
|---|---|---|---|
| 1 | version-not-vendor → *CI runner starts on the JDK the workflow installed* | task 4.3: CI run `37532517118` (job `112505184659`) | `Check daemon JVM criteria` ✓, `Build debug APK` ✓, `Generate debug SBOM` ✓, `Check license policy` ✓; **0** lines matching `api.foojay.io` or `Unable to download toolchain`; `Starting a Gradle Daemon` at 21:18:00Z, then `:app:assembleMobileDebug` green |
| 2 | version-not-vendor → *Workstation keeps the JVM it already uses* | task 1.4, second half | after the scratch probe: `./gradlew help` exit 0 (BUILD SUCCESSFUL 4s), no daemon added in the real Gradle home (`~/.gradle/jdks` has no `adoptium-21`), daemon `19544` still `/home/tim/.jdks/jbr-21.0.11/bin/java` |
| 3 | version-not-vendor → *No routine run pays for a download* | tasks 4.1, 4.2 + row 1 | workstation: `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` 21 tasks executed / 8s, `:app:testMobileDebugUnitTest -PforceTests --no-build-cache -PnoCoverage` 24 executed / 1m20s, **0** download lines in either log; CI: cf. row 1 |
| 4 | durable-endpoints → *A pruned third-party record cannot break the build* | task 2.3 (mutation), task 2.2 guard, CI attempt 1 | guard exits 0 on the real file; a re-added `…/disco/v3.0/ids/0d1a1ac…/redirect` line → exit 1 (`::error file=gradle/gradle-daemon-jvm.properties::addresses a foojay package id…`), `toolchainVendor=JETBRAINS` → exit 1, `toolchainVersion=17` → exit 1; restored byte-identical, exit 0. CI ran the same script (step ✓). Substitute for *the index pruning a record*: the mutation reproduces exactly the state the prune produces |
| 5 | durable-endpoints → *Every declared platform resolves to its vendor archive* | task 1.3 | six `curl -L --max-redirs 0`: linux/macOS/win × x64/aarch64 → **307** each, targets `OpenJDK21U-jdk_{x64,aarch64}_{linux,mac}_hotspot_21.0.12.1_1.tar.gz` and `…_windows_….zip` |
| 6 | durable-endpoints → *A machine without the required JDK provisions once* | task 1.4, first half | scratch project (`/tmp/djvm-probe`, `-g /tmp/djvm-probe-home2`, `-Dorg.gradle.java.installations.auto-detect=false`) → `BUILD SUCCESSFUL in 1m11s`, `eclipse_adoptium-21-amd64-linux.2` unpacked from `OpenJDK21U-jdk_x64_linux_hotspot_21-any-vendor-21.0.12.1_1.tar.gz`, probe daemon running `/tmp/djvm-probe-home2/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java` |
| 7 | durable-endpoints → *A platform without a vendor build says so itself* | **not produced** | No FreeBSD/UNIX host exists here, so the message cannot be observed. Substitute: the committed file declares neither `FREE_BSD.*` nor `UNIX.*` (`grep -c '^toolchainUrl\.'` = 6; keys are LINUX/MAC_OS/WINDOWS × X86_64/AARCH64), so those platforms fall to Gradle's own "No defined toolchain download url for …" — read from the Gradle 9.6.1 manual, not measured |
| 8 | rule-checked-in → *Rule survives a fresh clone* | task 3.1, commit `cf513f7` | `git show HEAD:guidelines/Build.md` contains subsection "The build JVM (daemon JVM criteria)" (§2, line 171) with the three rules, the regeneration caveat and the checks; `AGENTS.md`/`README.md` name no JVM statement that the change invalidates (3.2 grep: no match) |
| 9 | rule-checked-in → *A regenerated file is not committed as generated* | tasks 2.2/2.3 (guard) | **not produced as written**: `./gradlew updateDaemonJvm` cannot run today (the index serves no JDK 21 to generate from). Substitute: the guard fails any committed file carrying a package-id URL (row 4), which is the enforcement the scenario asks for |

## CI runs consulted

| Run | Attempt | Verdict | What it shows |
|---|---|---|---|
| `37520576283` (2026-10-06 19:39) | 1 | ✗ 4m43s | pre-change: daemon start fails, `400 Bad Request` on the foojay id — the failure this change removes |
| `37529729987` (2026-10-06 20:52) | 1 | ✗ 3m41s | same, unchanged |
| `37532517118` (2026-10-06 21:15) | 1 | ✗ 16m46s | **after the change**: daemon starts on the installed Temurin 21, no download, build/SBOM/license gate ✓; the run then fails in `Run unit tests` on `:auto:testDebugUnitTest > MapScreenTest.theTapPathDoesNotResolveTheNativeClientOnTheHostThread` (781 tests, 1 failed) — unrelated to this change, see below |
| `37532517118` (2026-10-07 04:11) | 2 | ✗ 4m31s | rerun of the same commit dies earlier, in `:buildSrc` dependency resolution (`Could not find org.jetbrains.kotlin:kotlin-stdlib:2.3.21` from `plugins.gradle.org/m2`) — a transient repository failure on the first cold Gradle cache in this series (the key moved with `3ba476b`, which changed three `*.gradle.kts`); both artifacts answer 200 from the same URL today |
| `37532517118` (2026-10-07 04:19) | 3 | ✗ 19m30s | same commit again: `Build debug APK` ✓ (0 download lines), `Run unit tests` ✗ on a **different** case — `:core:testDebugUnitTest > DiagnosticsLogWritePathTest.pendingBufferIsBoundedAndMarksTheDropOnce` (447 tests, 1 failed). Two CI-only test failures in two modules across the attempts that reached the suites; filed as `TODO.md` §148 |

## Findings outside this change (not fixed here)

- CI's aggregate test run is flaky on a loaded machine: **four aggregate runs reached the suites, four
  failed, three different cases** — `:auto` `MapScreenTest.theTapPathDoesNotResolveTheNativeClientOnTheHostThread`
  (attempt 1), `:core` `DiagnosticsLogWritePathTest.pendingBufferIsBoundedAndMarksTheDropOnce` (attempt 3),
  `:auto` `AutoMapRendererRenderCadenceTest.theInFlightFlagIsClearedAndTheDurationMeasuredAfterARender`.
  Reproduced in an isolated worktree at this commit (`git worktree add --detach /tmp/nv-148 cf513f7`,
  `test -PforceTests --no-build-cache`), which recovered the assertion messages CI never uploaded:
  `the ring held exactly its capacity expected:<5> but was:<10>` and `the in-flight flag must be cleared`.
  Five forced single-module reruns were green, and so were the single-module runs in this tree (`:app` mobile
  221/1721/0, `:auto` 76/781/0, `:core` 41/447/0), so the trigger is the loaded parallel run, not the code
  path. The JVM-vendor hypothesis is **disproved** for the `:core` case: the worktree run that reproduced it
  used the local JBR 21 daemon, not the runner's Temurin 21. Filed as `TODO.md` §148 with the mechanisms,
  the reproduction recipe and the fix direction.
- `TODO.md` §145 (machine-local `org.gradle.java.home`) and §146 (`buildSrc`'s Java 17 toolchain cannot
  provision) were filed from this change's probe; neither is part of the commit.
