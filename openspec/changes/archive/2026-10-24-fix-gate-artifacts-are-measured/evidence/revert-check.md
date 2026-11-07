# Revert-check — fix-gate-artifacts-are-measured

One mutation per case, each applied to the mechanism the case names, run against that case alone, then
restored. Measured 2026-10-24, base `daa82b21`, after the case file's own `pipefail` defect was fixed (see
the last section).

## M1 — `Sync` → `Copy` for `syncSubmoduleIcons`

The mutation the 2026-09-29 note made by hand, now in the build script: a `Copy` never deletes a stale
output, so removing the asset from the source no longer changes the merged assets.

```
$ sed -i 's/tasks.register<Sync>("syncSubmoduleIcons")/tasks.register<Copy>("syncSubmoduleIcons")/' app/build.gradle.kts
$ bash tools/check-gate-artifacts-selftest.sh --case 'gateArtifacts#aRealAssetChangeRefreshesTheLocalTestApk'
FAIL gateArtifacts#aRealAssetChangeRefreshesTheLocalTestApk: the APK task was not re-executed after a real asset change: > Task :app:packageMobileDebugUnitTestForUnitTest UP-TO-DATE
check-gate-artifacts-selftest: 1 of 1 cases failed — gateArtifacts#aRealAssetChangeRefreshesTheLocalTestApk
$ git checkout -- app/build.gradle.kts
```

The message is the item's own 2026-09-29 symptom — `UP-TO-DATE` after an asset change — reproduced by
mutating a task's *output* behaviour rather than its source.

## M2 — the generated assets root unwired from `:app`

```
$ # app/build.gradle.kts: assets.srcDir("build/generated/assets") replaced by a comment
$ bash tools/check-gate-artifacts-selftest.sh --case 'gateArtifacts#anUnchangedTreeLeavesTheLocalTestApkUpToDate'
FAIL gateArtifacts#anUnchangedTreeLeavesTheLocalTestApkUpToDate: app/build/intermediates/apk_for_local_test/mobileDebugUnitTest/packageMobileDebugUnitTestForUnitTest/apk-for-local-test.ap_ does not carry the icon its source has
check-gate-artifacts-selftest: 1 of 1 cases failed — gateArtifacts#anUnchangedTreeLeavesTheLocalTestApkUpToDate
$ git checkout -- app/build.gradle.kts
```

This is the half that makes case 1's assertion non-vacuous: without the wiring the APK carries no asset at
all, and the case that expects the asset present fails.

## M3 — the forcing removed from `core/build.gradle.kts`

```
$ # core/build.gradle.kts: the `if (forceTests) { it.outputs.upToDateWhen { false } }` block replaced by a comment
$ bash tools/check-gate-artifacts-selftest.sh --case 'gateArtifacts#forcingRunsOnlyTheTestTasks'
FAIL gateArtifacts#forcingRunsOnlyTheTestTasks: -PforceTests did not force the test task: > Task :core:testDebugUnitTest UP-TO-DATE
check-gate-artifacts-selftest: 1 of 1 cases failed — gateArtifacts#forcingRunsOnlyTheTestTasks
$ git checkout -- core/build.gradle.kts
```

## M4 — the checker's own class 2 disabled

```
$ # tools/check-openspec-hygiene.sh: the bare-marker grep replaced by a comment
$ bash tools/check-openspec-hygiene-selftest.sh --case 'artifact-hygiene#bareNumberedMarkersAreReported'
FAIL artifact-hygiene#bareNumberedMarkersAreReported: bare numbered markers passed (exit 0)
check-openspec-hygiene-selftest: 1 of 6 cases failed — artifact-hygiene#bareNumberedMarkersAreReported
```

Restored by re-applying the block; the file's hash is the pre-mutation hash (measured in both passes: after
M4 the checker was restored, and the run that followed the case-rename re-mutated and re-restored it). The
case's fixture carries one well-formed `- [x]` item beside the bare ones, so only class 2 can redden it —
the first fixture had bare items alone, and the case passed for class 1's reason instead (found and fixed in
the same pass).

## The shipped requirement's own three case ids

`documentation-ownership` — "A capability spec states what the capability is for" — names
`artifact-hygiene#theArchivePlaceholderIsReported`, `#aWrittenPurposePasses` and
`#aNestedCapabilitySpecIsReported`, and required the check to print `Every capability spec states its
Purpose.` No case answered to any of the three and no checker printed that sentence, so the scenarios could
not redden anything. This change names its cases accordingly (the nested case is new: the requirement's own
example is `openspec/specs/share/location-receiving/spec.md:4`, which a flat `specs/*/spec.md` glob would
miss) and the checker prints the sentence. Both halves were verified with
`bash tools/check-openspec-hygiene-selftest.sh` → `all 6 cases passed`.

## The case file's own defect, found by the cases

The first green run of `check-gate-artifacts-selftest.sh` reported
`#anUnchangedTreeLeavesTheLocalTestApkUpToDate` **failed while the APK held the icon**:

```
FAIL gateArtifacts#anUnchangedTreeLeavesTheLocalTestApkUpToDate: … does not carry the icon its source has
$ unzip -l …/apk-for-local-test.ap_ | grep -c bus_stop
1
```

Cause: the file runs under `pipefail`, and `apk_carries` piped `unzip -l` into `grep -q`; the early exit
sent `unzip` SIGPIPE, so the pipeline's status was 141 and the test answered "not present" whatever the APK
held. It made case 1's content assertion vacuous in the same way (M2's first red proof came from the broken
check, not from the APK). Fixed with `grep -c` — which reads the whole listing — and M2 was re-run to give a
non-vacuous red. The measurement is the comment above `apk_carries` in the case file.
