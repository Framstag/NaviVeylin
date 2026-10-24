# fix-gate-artifacts-are-measured

## Why

Issue #45 (`TODO.md` §17) carries three findings about the verification gate, all three filed as silent
blind spots. Two of them are already fixed in the tree, and the third does not hold — measured 2026-10-24.

1. **`./gradlew test` reporting green without executing** — fixed by `-PforceTests --no-build-cache`
   (`app/build.gradle.kts:161-167`, `:core`, `:auto`, `:osmscout-client-java`), stated as the requirement
   "A gate run proves that its tests executed" (`openspec/specs/build-test-gate/spec.md:39`) and prescribed
   in `guidelines/Build.md` §3/§4. **Unguarded**: no case fails when the forcing is dropped, because
   nothing reads the run's own task lines.
2. **Bare `1. [x]` task markers silently zeroing a change's task counts** — fixed by the pipeline's "Check
   OpenSpec artifact hygiene" step, which now refuses the form. **Unguarded and without a second home**:
   the check lives as inline YAML, so no case has ever violated it and no local invocation can run it.
3. **A stale unit-test asset APK making asset-reading Robolectric tests lie** — **does not reproduce.**
   Measured on this tree, 2026-10-24:

   ```
   ./gradlew :app:packageMobileDebugUnitTestForUnitTest                      → UP-TO-DATE (7 s)
   # one icon moved out of the real submodule leaf
   app/src/main/cpp/libosmscout/libosmscout/data/icons/14x14/standard/bus_stop.png
   ./gradlew :app:packageMobileDebugUnitTestForUnitTest                      → executed,
       :app:syncSubmoduleIcons + :app:mergeMobileDebugAssets executed, 15 s
   unzip -l …/apk-for-local-test.ap_ | grep -c bus_stop                      → 0
   # restored
   unzip -l …/apk-for-local-test.ap_ | grep -c bus_stop                      → 1
   ```

   The APK follows the merged assets. The item's own 2026-09-29 evidence for the opposite came from its
   recorded half (1): the file was deleted from a `Sync` task's **output**, which the sync restores before
   the test runs — so the merged assets never changed and the "stale APK" was the mutation's artifact.
   `guidelines/Build.md` §10j already points at that trap one layer down. What is missing is not a build
   change but the two halves of the behaviour as cases, and the rule that names which mutation is honest.

Owning capability: `build-test-gate` — the requirement this change adds, "The local-test asset APK follows
its merged assets". Owning guideline section: `guidelines/Build.md` §4f ("Artifacts and evidence rules"),
which owns the "verify an artifact against its inputs, not by the existence of an output path" rule this
change sharpens for the unit-test APK; the reading recipe is `.pi/skills/run-tests/SKILL.md` ("Reading a
packaged asset").

### The red half

There is no JUnit case to write for either half — no test source can observe which tasks Gradle
re-executed. Both are shell cases (`tools/*-selftest.sh`), and each one is red when its mechanism is
mutated (three mutations, each recorded in `evidence/revert-check.md`):

| mutation | case that reddens |
|---|---|
| `tasks.register<Copy>("syncSubmoduleIcons")` instead of `Sync` (a stale output is no longer deleted) | `gateArtifacts#aRealAssetChangeRefreshesTheLocalTestApk` |
| `assets.srcDir("build/generated/assets")` removed from the `:app` android block | `gateArtifacts#anUnchangedTreeLeavesTheLocalTestApkUpToDate` |
| the `if (forceTests) { it.outputs.upToDateWhen { false } }` block removed from `core/build.gradle.kts` | `gateArtifacts#forcingRunsOnlyTheTestTasks` |
| a bare `1. [x]` item written into a throwaway `openspec/changes/demo/tasks.md` | `artifact-hygiene#bareNumberedMarkersAreReported` (the checker's own class 1) |
| the checker's class 2 grep removed | `artifact-hygiene#bareNumberedMarkersAreReported` |
| a `## Purpose` placeholder under `openspec/specs/share/location-receiving/` | `artifact-hygiene#aNestedCapabilitySpecIsReported` (the flat-glob trap the requirement's example names) |

### Surface

```
git status --porcelain — 8 paths:
  tools/check-gate-artifacts-selftest.sh      (new, 3 cases)
  tools/check-openspec-hygiene.sh             (new, extracted from the pipeline step)
  tools/check-openspec-hygiene-selftest.sh    (new, 6 cases — three of them the ids the shipped spec names)
  .github/workflows/build.yml                 (12+/24−: the hygiene step calls the script, one new "Check …" step)
  tools/loop/loop.conf                        (the suite map gains the two new commands and their paths)
  guidelines/Build.md                         (10+/0−, §4f)
  .pi/skills/run-tests/SKILL.md               (15+/0−, "Reading a packaged asset")
  openspec/changes/fix-gate-artifacts-are-measured/**
surface: 2 case files added (8 cases), 0 cases rewritten, 0 fixtures realigned — the two loops' checks
  that read this surface are `tools/loop-selftest.sh --case gate#theMapCoversEveryPipelineStep` and
  `gate#aPipelineCheckIsOwedByItsPaths`, both green after the map change.
```

## What changes

1. `tools/check-gate-artifacts-selftest.sh` — three cases over the two requirements: `-PforceTests` forces
   the test task and leaves the module compile up to date (both halves read from one run), a real asset
   change refreshes the unit-test asset APK, and an unchanged tree leaves it up to date. The asset mutation
   moves one file of the submodule's raster leaf and restores it through a `trap`.
2. `tools/check-openspec-hygiene.{sh,selftest.sh}` — the pipeline step's three silent classes become one
   script with one home, and six cases exercise each class against a throwaway OpenSpec tree — including
   the three ids `documentation-ownership` already promised and no case answered to
   (`artifact-hygiene#aWrittenPurposePasses`, `#theArchivePlaceholderIsReported`,
   `#aNestedCapabilitySpecIsReported`), so the checker prints the verdict sentence that requirement names.
   The step now calls the script and its self-test.
3. `tools/loop/loop.conf` — the suite map owes both new commands for the paths they guard (`openspec/*`,
   `tools/check-openspec-hygiene*`, `app/build.gradle.kts`, `core/build.gradle.kts`, `buildSrc/*`,
   `app/src/main/cpp/libosmscout/*`, `tools/check-gate-artifacts*`).
4. `guidelines/Build.md` §4f — the local-test asset APK rule and its measurement, beside the artifact rule
   it sharpens.
5. `.pi/skills/run-tests/SKILL.md` — the recipe for a case that reads a packaged asset.

No application source, no native code, no manifest, no dependency, no release state, no change to the
`:app` build script.

## Impact

| Surface | What it sees |
|---|---|
| a change that reads a packaged asset | the recipe names the APK and the honest mutation; the two cases pin both directions |
| a local session | can run the artifact-hygiene checks without the pipeline (`bash tools/check-openspec-hygiene.sh`) and see each class fail (`…-selftest.sh`) |
| CI | one replaced step (same three classes, one home) and one added step after the unit-test run: ~4 Gradle invocations, ~2 min, no new dependency |
| the fix loop | the two new suite entries run for a change that touches the paths they guard |
