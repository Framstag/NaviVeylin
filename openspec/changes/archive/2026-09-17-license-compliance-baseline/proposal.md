# Proposal: license-compliance-baseline

## Why

The project ships third-party code under a mix of licenses but cannot currently
demonstrate compliance. Measured on `app/build/outputs/sbom/mobileRelease/bom.json`:
159 components, 126 with license data, **33 with none** — every native vcpkg package
plus libosmscout — and `libomp.so` is shipped in the APK yet absent from the SBOM
entirely. vcpkg's own `vcpkg.spdx.json` carries `licenseDeclared: null`, so the
pipeline is faithful but the source metadata is empty. Nothing gates on licenses,
and the About dialog states open-source usage generically instead of listing what
is actually bundled.

Two facts make this tractable now: vcpkg ships the full license text per port at
`share/<port>/copyright` (present for every port checked), and the shipped-artifact
inventory has been traced (see Impact) so build-time-only packages can be
distinguished from distributed ones.

Deliberately out of scope: choosing the application's own license. `LICENSE` is a
bare GPLv2 text and `README.md` §License says "License information TBD"; that
decision, the LGPL relinking strategy, and any dependency swap stay open. This
change makes the facts reviewable so that decision can be made on evidence.

## What Changes

- Enrich native SBOM components with SPDX license identifiers, sourced from each
  port's `copyright` file plus an explicit, curated mapping for ports whose
  metadata is absent or ambiguous (including our own overlay port).
- Record license **elections** for dual-licensed components in one machine-readable
  file (cairo `MPL-1.1` in place of LGPL-2.1; freetype `FTL` in place of GPL-2.0;
  marisa-trie `BSD-2-Clause` in place of LGPL-2.1), so the choice is explicit
  rather than incidental.
- Mark every SBOM component with whether it is **shipped** in the APK or
  build-time only. Packages such as `pango`, `glib`, `harfbuzz`, `fribidi`,
  `gettext-libintl` and `libiconv` are installed but contribute no shipped binary;
  reporting them identically to linked code is misleading for review.
- Add `libomp.so` (`Apache-2.0 WITH LLVM-exception`) to the native inventory — it
  is `NEEDED` by `libosmscout*.so` and present in `lib/<abi>/`.
- Add a license gate that fails the build/CI when a component has no license, an
  unresolvable license, or a license outside the project's allowlist.
- Generate in-app license data at build time (dependency list, SPDX ids, full
  license texts) and show it from the About dialog, replacing the generic
  open-source statement while keeping the existing OSM/ODbL attribution.
- Generate an Apache-2.0 `NOTICE` for the dependency set, since that license
  requires propagating notices of bundled components.

Additive change. No runtime behavior changes beyond the new license screen. No
submodule patch: all work lives in local build scripts, resources, and app code.

## Capabilities

### New Capabilities

- `license-compliance`: dependency license inventory for every distributed
  build — SPDX identifiers per component, explicit elections for dual-licensed
  components, shipped/build-time classification, and a gate that fails when
  license data is missing or not allowlisted.

### Modified Capabilities

- `build-sbom`: SBOM requirements extend from "a component per dependency" to
  "a component per dependency with license data, completeness enforced". New
  requirements: native components carry SPDX license identifiers; components
  declare shipped vs build-time-only; the Android OpenMP runtime is recorded;
  generation fails when a component's license cannot be resolved; a gate
  consumes the SBOM and fails on unknown or disallowed licenses.
- `about-dialog`: the dialog's open-source statement is replaced by a reachable
  list of the dependencies actually bundled, each with its license and the full
  license text. Existing requirements (app name, version, description, author,
  copyright, close/dismiss behavior, OSM licence link) are unchanged.

## Impact

Affected files and modules (build-side):

- `app/build.gradle.kts` — SBOM task section (`~line 370+`): native license
  enrichment, shipped/build-time classification, `libomp` component, license gate
  task, and in-app license data generation into the existing generated-assets root.
- `.github/workflows/build.yml` — license-gate step alongside the existing SBOM
  generation/upload steps.
- `licenses/` (new) — curated native license mapping and the dual-license election
  file; vcpkg port metadata is incomplete and `marisa-trie` is our own overlay port.
- `app/src/main/assets` (generated, not committed) — dependency/license JSON and
  license texts, produced at build time and consumed at runtime, mirroring the
  existing submodule-stylesheet pattern (`syncSubmoduleStylesheets` →
  `build/generated/assets`).

Affected code:

- `app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt` — entry point to the
  new license screen; the generic statement string is removed.
- `app/src/main/java/com/naviveylin/ui/about/` (new `LicensesScreen.kt`) —
  Compose screen listing components, licenses, and full texts.
- `app/src/main/res/values/strings.xml` and the existing translation variants —
  new labels; no hardcoded user-facing strings.

Guidelines affected:

- `guidelines/Build.md` §8 (SBOM) — extend with license enrichment, the gate, and
  the generated license assets; add the new tasks to the task table and the
  `release` wiring description.
- `guidelines/UI.md` §3 — referenced for the Android Auto constraint below.
- `guidelines/Design.md` — build conventions (generated assets, no committed
  snapshots) must stay satisfied.

Scope:

- The license screen is **phone/tablet only**. Android Auto's `AboutScreen` uses a
  `PaneTemplate` whose rows are not actionable (`guidelines/UI.md` §3); a
  per-dependency list with drill-down cannot be built from it without an
  unbounded template. Parity deviation: AA keeps the existing app-name, version,
  and OSM/ODbL attribution rows; the dependency license list is not surfaced in
  the car. `auto/src/main/java/com/naviveylin/auto/AboutScreen.kt` is therefore
  not modified by this change.
- Map-data attribution (ODbL) is already specified by `osm-attribution` and is
  not re-specified here.

Explicitly not in scope:

- Deciding the application's own license, replacing `LICENSE`, or resolving the
  GPLv2-only vs Apache-2.0 question.
- LGPL relinking strategy, source offers, or publishing a relink kit.
- Removing, swapping, or dropping any dependency.
- A formal legal review. This change produces evidence, not a legal conclusion.

Rollback: revert the change. The generated assets and gate tasks are additive
build steps; removing them restores the current SBOM behavior, and the About
dialog falls back to the previous statement string when the generated license
asset is absent.
