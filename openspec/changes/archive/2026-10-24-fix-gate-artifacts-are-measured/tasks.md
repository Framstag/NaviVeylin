# Tasks — fix-gate-artifacts-are-measured

## 1. Measure the item's three findings on the base tree

- [x] 1.1 Finding 1 (a green run that executes no test) is landed: `-PforceTests --no-build-cache` in
  `app/build.gradle.kts:161-167` and the three other modules, the requirement
  `openspec/specs/build-test-gate/spec.md:39-45` and the invocation in `guidelines/Build.md` §3.
  Verify: `grep -rn 'forceTests' app/build.gradle.kts core/build.gradle.kts auto/build.gradle.kts
  osmscout-client-java/build.gradle.kts` — four modules, each with the `upToDateWhen { false }` wiring.
- [x] 1.2 Finding 2 (bare `1. [x]` items zeroing the task counts) is landed as the pipeline step "Check
  OpenSpec artifact hygiene". Verify: `.github/workflows/build.yml` carries the step; its three classes
  are now `tools/check-openspec-hygiene.sh`, which the step calls.
- [x] 1.3 Finding 3 (a stale unit-test asset APK) **does not hold** on this tree. Verify: two invocations
  of `./gradlew :app:packageMobileDebugUnitTestForUnitTest` — `UP-TO-DATE` (7 s) on an unchanged tree;
  with `bus_stop.png` moved out of the real submodule leaf, `:app:syncSubmoduleIcons`,
  `:app:mergeMobileDebugAssets` and the packaging task all executed (15 s) and `unzip -l
  …/apk-for-local-test.ap_ | grep -c bus_stop` was 0; after the restore it was 1. Raw logs:
  `evidence/asset-freshness/`.

## 2. The cases

- [x] 2.1 `tools/check-gate-artifacts-selftest.sh` — three cases: `#forcingRunsOnlyTheTestTasks`,
  `#aRealAssetChangeRefreshesTheLocalTestApk`, `#anUnchangedTreeLeavesTheLocalTestApkUpToDate`. Each takes
  one Gradle invocation (the forcing case one warming compile), and the asset mutation is restored by a
  `trap` with a post-condition that exits 2 if the icon is still missing.
  Verify: `bash tools/check-gate-artifacts-selftest.sh` → `all 3 cases passed`, `git status --porcelain`
  and `git -C app/src/main/cpp/libosmscout status --porcelain` empty afterwards
  (`evidence/gate-artifacts-cases.log`).
- [x] 2.2 `tools/check-openspec-hygiene.sh` — the step's three classes, one script, `--root` so a throwaway
  tree can be checked. Verify: `bash tools/check-openspec-hygiene.sh` on this repository → exit 0 and the
  verdict line naming the open change count.
- [x] 2.3 `tools/check-openspec-hygiene-selftest.sh` — six cases, one per class plus the clean tree and
  the nested capability spec, each in a throwaway OpenSpec tree. Three ids are the ones the shipped
  requirement already names (`artifact-hygiene#aWrittenPurposePasses`, `#theArchivePlaceholderIsReported`,
  `#aNestedCapabilitySpecIsReported`), and the checker prints that requirement's verdict sentence.
  Verify: `bash tools/check-openspec-hygiene-selftest.sh` → `all 6 cases passed`
  (`evidence/hygiene-cases.log`), and `bash tools/check-openspec-hygiene.sh` on this repository → the
  sentence plus the combined verdict, exit 0.

## 3. Wiring

- [x] 3.1 `.github/workflows/build.yml` — the hygiene step calls the script and its self-test, and a new
  step "Check the gate's artifacts" runs the case file after the unit-test step (it needs the vcpkg tree
  and the SBOM CLI: the packaging task is refused under `-PjvmTestsOnly` by name).
  Verify: `python3` YAML parse of the workflow, `bash tools/check-action-runtimes.sh`, and
  `bash tools/loop-selftest.sh --case 'gate#theMapCoversEveryPipelineStep'` — the case that refuses a
  pipeline `Check` command the suite map does not name.
- [x] 3.2 `tools/loop/loop.conf` — the suite map owes both new commands for `openspec/*`,
  `tools/check-openspec-hygiene*`, `app/build.gradle.kts`, `core/build.gradle.kts`, `buildSrc/*`,
  `app/src/main/cpp/libosmscout/*` and `tools/check-gate-artifacts*`.
  Verify: `bash tools/loop-selftest.sh --case 'gate#theMapCoversEveryPipelineStep'` and
  `--case 'gate#aPipelineCheckIsOwedByItsPaths'` → both PASS.

## 4. Documentation

- [x] 4.1 `guidelines/Build.md` §4f — the local-test asset APK rule, the measurement that falsifies the
  2026-09-29 reading, and the pointer to the §10j trap it is the same as.
  Verify: `bash tools/check-doc-routes.sh` (a section the documentation cites must exist) and the
  repository's markdown checks.
- [x] 4.2 `.pi/skills/run-tests/SKILL.md` — "Reading a packaged asset": the APK path, the honest mutation,
  the probe, and the case file that pins both directions.

## 5. Revert-check and the change's suites

- [x] 5.1 Four mutations, each with the case that reddens and its output: `Sync`→`Copy` for
  `syncSubmoduleIcons` (case 1: "the APK task was not re-executed after a real asset change: … UP-TO-DATE");
  the `assets.srcDir("build/generated/assets")` line removed from the `:app` android block (case 2: "does
  not carry the icon its source has"); the `if (forceTests) { it.outputs.upToDateWhen { false } }` block
  removed from `core/build.gradle.kts` (case 3: "-PforceTests did not force the test task"); and the
  checker's own class 2 disabled (case `artifact-hygiene#bareNumberedMarkersAreReported`: "bare numbered
  markers passed (exit 0)"). Each mutation was restored, and the two Gradle build scripts through
  `git checkout --`. Verify: the four transcripts in `evidence/revert-check.md`.
- [x] 5.2 Every mutation restored, then the cases green again in one run of each case file — and one real
  defect in the case file itself found this way (a `pipefail`/`grep -q` pipeline answering "not present"
  whatever the APK held; fixed with `grep -c`, M2 re-run for a non-vacuous red).
  Verify: `bash tools/check-gate-artifacts-selftest.sh` → `all 3 cases passed`,
  `bash tools/check-openspec-hygiene-selftest.sh` → `all 6 cases passed`; `git status --porcelain` names
  only this change's paths and `git -C app/src/main/cpp/libosmscout status --porcelain` is empty.
- [x] 5.3 The change's own suites, from `bash tools/loop/gate.sh --plan --base HEAD`:
  `tools/loop-selftest.sh --from-git HEAD` (all 215 cases passed),
  `bash tools/loop-mutations.sh --check` (all 142 cases passed),
  `tools/check-action-runtimes.{sh,-selftest.sh}` (10 pins Node 24 / all 10 cases),
  `tools/check-doc-routes.{sh,-selftest.sh}` (153 section + 4 skill references / 19 passed),
  `tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list`,
  `tools/check-todo-references.sh` (63 citations resolve), and both case files.
  Verify: the per-suite verdicts in `evidence/suites.log`.
