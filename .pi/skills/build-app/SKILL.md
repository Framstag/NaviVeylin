---
name: build-app
description: Builds the NaviVeylin Android app with Gradle — debug APKs for all ABIs and both flavors, or fast single-flavor / single-ABI iteration builds. Use when asked to build or compile the app, produce a debug APK, or verify the code compiles (e.g. OpenSpec apply/archive build gates).
---

# Build the app

## When to use

- User asks to build/compile the app or produce a debug APK
- OpenSpec apply/archive guidance requires verifying the build compiles without errors
- After Kotlin or native (C++) changes, before running tests
- After a libosmscout submodule bump (smoke check)

## Procedure

1. Print a status message before running, e.g.:
   `Building debug APK (all ABIs, both flavors)…`
2. **Run in the FOREGROUND with the output redirected to a log** — redirect the *output* (that is what the tool's ~120s output cap applies to), never background the build. A backgrounded Gradle run (`nohup … &`) is **killed** when the tool call ends, because the harness kills the process group: the log stops mid-build with no verdict, and that reads like a build failure while being a harness artifact (`setsid` is refused by the shell allowlist). A redirected foreground run has no such limit — both flavors with all three ABIs plus the native build complete in one call:
   ```bash
   ./gradlew :app:assembleDebug > /tmp/build-app.log 2>&1; echo "exit=$?"; \
     grep -E 'BUILD SUCCESSFUL|BUILD FAILED|^e: ' /tmp/build-app.log | head
   ```
   Give the call a generous `timeout` (600s for one flavor/ABI, 2400s for both flavors) and only grep the verdict back — never dump the whole log into the transcript. Fast iteration builds finish well inside that.

   Before starting, confirm no other run is active — and make the probe able to match: `pgrep -af 'gradle-wrapper\.ja[r]'`. The `GradleWrapper[M]ain` pattern can **never** match, because the main class lives *inside* `gradle-wrapper.jar` and never appears in the argument vector, so such a guard always reports "idle" while a build is running (`guidelines/Build.md` §2). The bracket keeps the probe from matching its own command line; an idle Gradle daemon is not a run either. Cross-check the `BUILD SUCCESSFUL|BUILD FAILED` verdict line before believing either.
3. The log ends with `BUILD SUCCESSFUL` or `BUILD FAILED` — read the verdict from the log, not from a poll loop.
4. **Evaluate by log content, not the shell tool's exit code** (for a killed run the exit code reflects the kill, not Gradle):
   - Log contains `BUILD SUCCESSFUL` → success
   - Log contains `BUILD FAILED` → failure
   - Log ends mid-build with no verdict (e.g. stops at a `configureCMake...`/`buildCMake...` task) → the build was killed, not failed; re-run it in the foreground with a bigger `timeout` (and confirm the run's `timeout` was not the limit)
5. On failure, extract and report the actionable error lines:
   - Kotlin compile errors: lines starting with `e: `
   - C++/NDK errors: `error:` lines from CMake/ninja
   - Failed tasks: `> Task ... FAILED` lines
   - `checkSubmoduleStylesheets` failure → libosmscout submodule not initialized; fix with `git submodule update --init --recursive`
6. Report the verdict with a status message, e.g.:
   `Build succeeded — APK at app/build/outputs/apk/debug/app-debug.apk` or `Build failed — <error excerpt>`

## Commands

| Goal | Command |
|---|---|
| All 3 ABIs (arm64-v8a, armeabi-v7a, x86_64), both flavors | `./gradlew :app:assembleDebug` |
| Phone/Android Auto flavor only (faster iteration) | `./gradlew :app:assembleMobileDebug` |
| AAOS flavor only (head-unit build) | `./gradlew :app:assembleAutomotiveDebug` |
| Single ABI (fastest iteration) | `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` |
| Prune superseded native configurations (dry run first) | `bash tools/prune-native-configs.sh --dry-run --skip tools app/.cxx` |

## Notes

- First build compiles all native code (libosmscout + vcpkg deps) and can take a long time (8+ min); later builds are incremental. The shell tool's ~120s **output** cap is what the redirect is for: run it in the foreground with `> /tmp/build-app.log 2>&1` and a generous `timeout`, never backgrounded — the harness kills a backgrounded build when the tool call ends.
- For native C++ changes, verify **all** target ABIs compile (full `assembleDebug`), per OpenSpec apply guidance.
- Build only the targets needed for the task to minimize resource usage and compilation time.
- The build must have no warnings — report warnings you see, don't ignore them.
- This skill is versioned with the project (`.pi/skills/build-app/`); copy the directory to `~/.pi/agent/skills/build-app/` to make it available across projects.
