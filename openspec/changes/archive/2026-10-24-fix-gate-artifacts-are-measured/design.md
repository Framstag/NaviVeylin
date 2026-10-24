# Design — fix-gate-artifacts-are-measured

## Root cause, one sentence

Three gate claims were asserted rather than measured — "the tests executed", "the marker style is
enforced", "the asset APK can be stale" — and the third is false on this tree, so what is missing is not a
build change but the reading that decides each one, in one place a case can exercise.

## The fix, one mechanism

Make each claim a shell case that reads the thing it is about, and give the pipeline's inline check the
single home the cases can drive:

| claim | case | what it reads |
|---|---|---|
| forcing executes the tests and nothing else | `gateArtifacts#forcingRunsOnlyTheTestTasks` | one forced run's own task lines (`:core:testDebugUnitTest` without `UP-TO-DATE`, `:core:compileDebugKotlin` with it) and a result XML newer than the run's start |
| the unit-test APK follows the merged assets | `gateArtifacts#aRealAssetChangeRefreshesTheLocalTestApk`, `…#anUnchangedTreeLeavesTheLocalTestApkUpToDate` | the packaging task's state plus the APK's own listing, before/after moving a file of the real submodule leaf |
| the artifact classes the CLI reports silently | `artifact-hygiene#…` (6 cases) | a throwaway `openspec/` tree, one broken class per case — the three Purpose ids being the ones the shipped `documentation-ownership` requirement names |

## Blast radius

- `:app`'s build script and `core/build.gradle.kts` are **not** touched: the measurement showed the wiring
  already does what the item asked for.
- The pipeline's hygiene step keeps the same three classes and the same messages; only the code's home
  changes. Its `::error file=` annotations are printed by the script, as the other extracted checkers do.
- The suite map in `tools/loop/loop.conf` gains entries; the case
  `gate#theMapCoversEveryPipelineStep` is what proves the map still names every `Check` step's command.
- The two Gradle-driven cases cost ~4 invocations and leave the tree as they found it (a `trap` restores
  the moved icon, and the file exits 2 if the icon is missing afterwards).

## Rollback

Revert the commit: the two checks disappear, the pipeline returns to inline YAML, and the suite-map entries
go with them — nothing else reads either path. The behaviour they pin is Gradle's, so a revert restores the
previous (unguarded) state, not a different build.

## Alternatives the evidence eliminated

1. **"Wire the unit-test asset package task to the merged-assets content explicitly"** (the item's own fix
   option) — eliminated by the measurement in `proposal.md`: the task already re-executes on a real asset
   change and keeps its outputs on an unchanged tree. A declared input would be a no-op that hides the
   mechanism instead of pinning it.
2. **Clear `app/build/intermediates/apk_for_local_test/<variant>` before an asset-affecting run** — the
   item's other option, and the wrong lever: the directory is the *output* of the task under test, so a
   recipe that deletes it turns every asset-APK question into `--rerun-tasks` and buys nothing the task's
   own up-to-date check does not already do. What the recipe needed was the honest mutation (the source,
   not a `Sync` output) and the probe (the APK's listing) — both now in `run-tests`.
3. **Documentation only, no case** — leaves the two Gradle behaviours pinned by nothing: the 2026-09-29
   mistake (mutating a task output) was made by an author who had the rule in mind and still proved
   nothing. A case is what makes the next author's mutation falsifiable.
4. **A JUnit case instead of a shell case** — no test source can observe which Gradle tasks re-executed;
   the existing precedent for a task-graph claim is `tools/check-jvm-test-graph-selftest.sh`, and these two
   cases are the same shape one layer down (there, `--dry-run` configurations; here, one real invocation
   per case).
5. **Re-implement the hygiene classes inside the self-test** — would have made the pipeline's check and the
   cases two implementations that can drift, which is the defect class the extraction removes.
6. **Leave the case ids as `hygiene#…`** — would have left the shipped requirement's three named cases
   (`artifact-hygiene#…`) answered by nothing, which is the same defect one layer up: a scenario that names
   a case no file carries cannot redden anything. The names now match, and the checker prints the verdict
   sentence that requirement states.

## The mutation the case makes, and why it is safe

The asset cases move `bus_stop.png` out of
`app/src/main/cpp/libosmscout/libosmscout/data/icons/14x14/standard/` — the *source* `syncSubmoduleIcons`
reads, not its output — and restore it in the `trap`. Three properties keep that safe: the submodule's own
tree is byte-identical again afterwards (`git -C app/src/main/cpp/libosmscout status --porcelain` empty),
the sync restores its output on the next run, and the file exits 2 with a named path if the icon is still
missing after the cases. A case that fails mid-mutation therefore still leaves a correct tree.
