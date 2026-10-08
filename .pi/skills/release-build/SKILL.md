---
name: release-build
description: Creates Play-ready release AABs (mobile + automotive) with `./gradlew release` — bumps version state, builds all ABIs, produces the two AABs for upload. Use when asked to make a release build, bump the app version, or produce AABs for Google Play upload or sideloading.
---

# Release build

## When to use

- User asks to make a release build or produce release AABs
- A version bump (versionName/versionCode) is needed
- Play upload or sideload distribution artifacts are requested

## Procedure

1. Print a status message before running, e.g.:
   `Building release AABs (mobile + automotive, all ABIs)…`
2. **Run in the FOREGROUND with the output redirected to a log** — redirect the *output* (that is what the tool's ~120s output cap applies to), never background the build. A backgrounded Gradle run (`nohup … &`) is **killed** when the tool call ends, because the harness kills the process group: the log stops mid-build with no verdict and that reads like a build failure while being a harness artifact (`setsid` is refused by the shell allowlist). `release` builds all 3 ABIs (8+ min) and still fits one call:
   ```bash
   ./gradlew release > /tmp/release-build.log 2>&1; echo "exit=$?"; \
     grep -E 'BUILD SUCCESSFUL|BUILD FAILED|^e: ' /tmp/release-build.log | head
   ```
   Give the call a generous `timeout` (2400s) and only grep the verdict back.
3. The log ends with `BUILD SUCCESSFUL` or `BUILD FAILED` — read the verdict from the log, not from a poll loop.
4. **Evaluate by log content, not the shell tool's exit code** (for a killed run the exit code reflects the kill, not Gradle):
   - Log contains `BUILD SUCCESSFUL` → success
   - Log contains `BUILD FAILED` → failure; extract error lines (Kotlin `e: `, C++ `error:`, `> Task ... FAILED`) and report
   - Log ends mid-build with no verdict (e.g. stops at a `configureCMake...`/`buildCMake...` task) → the build was killed, not failed; re-run it in the foreground with a bigger `timeout`
5. Verify both outputs exist:
   - `app/build/outputs/bundle/mobileRelease/app-mobile-release.aab` (phone + Android Auto)
   - `app/build/outputs/bundle/automotiveRelease/app-automotive-release.aab` (AAOS)
6. Report the generated version: read `app/release-version.properties` (`lastDate`, `runningNumber`, `versionCode`) and state the versionName (`<yyyy>-<MM>-<dd>-<N>`).

## Versioning semantics

- `release` generates `versionName` as `<yyyy>-<MM>-<dd>-<N>` (4-digit year, zero-padded month/day, running number `N` without leading zeros), increments `versionCode` by one, then runs `:app:bundleMobileRelease` and `:app:bundleAutomotiveRelease`.
- Version state lives in `app/release-version.properties` (**gitignored**): same day → `N+1`; new day → `N` resets to 1; `versionCode` starts at 20.
- The bump happens at configuration time, gated on the `release` task being requested — every other build uses the fixed fallback `1.0.0`/`19` and never touches the state file.
- Direct `bundleRelease` without `release` reuses the last persisted values; only `release` bumps (single release machine assumed).
- Signing: `app/release.keystore` present → signed AAB; absent → warning logged, unsigned AAB still produced.

## Notes

- Upload the mobile AAB to normal Play tracks and the automotive AAB to the dedicated "Android Automotive OS" track (same package name = single store listing). For sideloading: automotive AAB on head units, mobile AAB on phones.
- `release` builds all 3 ABIs — expect a long runtime (8+ min). The shell tool's ~120s **output** cap is what the redirect is for: run it in the foreground with `> /tmp/release-build.log 2>&1` and a generous `timeout`, never backgrounded — the harness kills a backgrounded build when the tool call ends.
- This skill is versioned with the project (`.pi/skills/release-build/`); copy the directory to `~/.pi/agent/skills/release-build/` to make it available across projects.
