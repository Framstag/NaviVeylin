# Spec Delta

## ADDED Requirements

### Requirement: The dex gate waits for its variant's dex producer

`:app:checkClientApiInDex<Variant>` SHALL depend on the task AGP uses to write that variant's dex — the dex
builder and merge steps for a plain variant, `minify<Variant>WithR8` for a minified one — and the variant's
`assemble`/`bundle` SHALL depend on the gate.

#### Scenario: A minified variant's gate waits for R8

- **WHEN** the graph of `:app:checkClientApiInDex<Variant>` is requested for a variant with `isMinifyEnabled = true`
- **THEN** the graph holds `minify<Variant>WithR8`, the task whose output is the variant's only `*.dex`, under
  the `intermediates/dex/<variant>` tree the gate reads
- **AND** the gate's action therefore sees a non-empty tree instead of failing with "No dex archives to scan"
- **Case**: `dexGateGraph#mobileReleaseGateFollowsR8` and `dexGateGraph#automotiveReleaseGateFollowsR8`
  (`tools/check-dex-gate-graph-selftest.sh`) — red on the merge-only dependency set, whose graph is the gate
  alone (`:app:checkClientApiInDexAutomotiveRelease SKIPPED`, the reported `./gradlew release` failure);
  green with the producer named. The tree identity in the THEN is measured rather than asserted: one
  `find` after `:app:minifyAutomotiveReleaseWithR8` (`tasks.md` 1.2)

#### Scenario: A plain variant's gate waits for the dex chain

- **WHEN** the same graph is requested for a variant with `isMinifyEnabled = false`
- **THEN** it holds `dexBuilder<Variant>` and the three merge steps, and no R8 task, because no such task exists
  for that variant
- **Case**: `dexGateGraph#mobileDebugGateFollowsTheDexChain` and `dexGateGraph#automotiveDebugGateFollowsTheDexChain`
  (`tools/check-dex-gate-graph-selftest.sh`) — the wiring that was already correct, asserted so a single shape
  cannot replace the other

#### Scenario: The packaging graph holds the gate and its producer

- **WHEN** the graph of `bundle<Variant>` is requested for a release variant
- **THEN** it holds both `checkClientApiInDex<Variant>` and `minify<Variant>WithR8`, so the command that builds a
  shippable artifact carries the guard and the dex it reads
- **Case**: `dexGateGraph#mobileBundleFollowsTheGate` and `dexGateGraph#automotiveBundleFollowsTheGate`
  (`tools/check-dex-gate-graph-selftest.sh`); `./gradlew release` green afterwards, both AABs written
  (2026-10-28, `NaviVeylin release: 2026-10-28-2 (versionCode 107)`)
