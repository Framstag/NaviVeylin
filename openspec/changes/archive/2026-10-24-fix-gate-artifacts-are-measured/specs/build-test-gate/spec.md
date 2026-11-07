# build-test-gate

## ADDED Requirements

### Requirement: The local-test asset APK follows its merged assets

The APK the unit-test variant packages SHALL be rebuilt when the variant's merged assets change, and SHALL
be left up to date when they do not, so a test that reads a packaged asset reads the assets the tree
carries. A case for this SHALL change the asset's source, never a task's output.

#### Scenario: A real asset change refreshes the packaged file

- **WHEN** an asset is removed from the source a sync task reads
- **THEN** the assets are merged again and the unit-test APK no longer carries that asset — exercised by
  `tools/check-gate-artifacts-selftest.sh --case gateArtifacts#aRealAssetChangeRefreshesTheLocalTestApk`

#### Scenario: An unchanged tree is not repackaged

- **WHEN** the same APK is asked for twice with no asset change between the runs
- **THEN** the second run reports the packaging task up to date and the APK still carries the asset its
  source has — exercised by
  `tools/check-gate-artifacts-selftest.sh --case gateArtifacts#anUnchangedTreeLeavesTheLocalTestApkUpToDate`
