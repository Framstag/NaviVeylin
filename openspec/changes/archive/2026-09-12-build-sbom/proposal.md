# Proposal: build-sbom

## Why

NaviVeylin ships two Play AABs but produces no software bill of materials. The
dependency surface is unusually wide — a large JVM/Kotlin stack (androidx,
Compose, Hilt, kover, ...) plus a native C++ stack (cairo, pango, harfbuzz,
freetype, protobuf, ... via vcpkg) plus a libosmscout fork as git submodule —
and none of it is captured in a machine-readable, reviewable artifact for
dependency and supply-chain review.

## What Changes

- Add CycloneDX SBOM generation to the Gradle build:
  - JVM dependencies: apply `org.cyclonedx.bom` (3.x) at the root so the
    aggregate BOM covers `:app`, `:auto`, `:core`, and `:osmscout-client-java`.
  - Native dependencies: convert vcpkg's per-package SPDX SBOMs
    (`$VCPKG_ROOT/installed/<triplet>/share/<pkg>/vcpkg.spdx.json`, all three
    triplets) to CycloneDX with the pinned `cyclonedx-cli` binary and merge
    them into the JVM BOM. Identical package/version across triplets is
    deduplicated into a single component.
  - libosmscout submodule: recorded as a metadata component (pinned git SHA)
    in each BOM.
- Wire SBOM generation into the `release` target: each of
  `bundleMobileRelease` and `bundleAutomotiveRelease` gets a matching SBOM,
  emitted next to its AAB. SBOM version metadata uses the release
  `versionName`.
- Make SBOM generation variant-parameterized and runnable standalone so any
  variant (e.g. `mobileDebug`) can produce one.
- CI: `.github/workflows/build.yml` generates the `mobileDebug` SBOM and
  uploads it as a build artifact alongside the debug APK.
- Update `guidelines/Build.md` with the SBOM workflow.
- **BREAKING**: none. Additive only.

## Capabilities

### New Capabilities

- `build-sbom` — the Gradle build and CI produce a CycloneDX JSON SBOM per
  requested variant covering all JVM, native (vcpkg), and submodule
  dependencies, with release wired into `release` and CI uploading the debug
  variant.

### Modified Capabilities

- `release-target` — the release target additionally emits one SBOM per
  distribution flavor next to the AABs (requirements below unchanged; new
  requirement added).

## Impact

Affected files:

- `app/build.gradle.kts` — applies `org.cyclonedx.bom` (per-variant direct tasks need the plugin on this module's classpath; the root aggregate task is not used), registers the variant-parameterized SBOM tasks, native SPDX convert/merge chain, `cyclonedx-cli` acquisition, wiring into the custom `release` task, release version metadata for the BOM.
- `.github/workflows/build.yml` — SBOM generation step + `upload-artifact`
  step for `naviveylin-sbom` (debug/mobile flavor only; release is local).
- `guidelines/Build.md` — new SBOM section + release target documentation.

New external tooling:

- `org.cyclonedx.bom` Gradle plugin (3.x) — JVM dependency graph.
- `cyclonedx-cli` binary (pinned version, downloaded once per machine and
  cached) — SPDX→CDX conversion and BOM merging.

Native/JNI: no source changes; vcpkg SPDX files and the submodule SHA are
consumed read-only. No libosmscout submodule patch required.

Scope: general build feature affecting both distribution flavors (mobile +
automotive). No app runtime behavior changes.

Rollback: remove the plugin block, the SBOM tasks, and the CI step; all other
build behavior is untouched.
