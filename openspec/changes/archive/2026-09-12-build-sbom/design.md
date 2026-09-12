## Context

NaviVeylin builds with Gradle 9.6.1 / AGP 9.4.0 across four modules (`:app`
with `mobile`/`automotive` flavors, `:auto`, `:core`, `:osmscout-client-java`),
linking a native stack installed by vcpkg (cairo, pango, harfbuzz, freetype,
protobuf, ...) for three Android triplets, plus a libosmscout fork pulled in as
a git submodule. See proposal.md (Why) and the `build-sbom` + `release-target`
delta specs for the required behavior. This design covers only how the build
produces the SBOMs — there are no runtime components involved.

## Goals / Non-Goals

**Goals:**

- Every `release` run yields one valid CycloneDX JSON SBOM per distribution
  flavor (mobile, automotive), next to its AAB, versioned with the release
  `versionName`.
- The same pipeline is variant-parameterized so CI can emit a `mobileDebug`
  SBOM and upload it as an artifact.
- Coverage = JVM/Gradle deps + vcpkg native deps + libosmscout submodule SHA;
  identical packages across triplets deduplicated.

**Non-Goals:**

- App runtime changes, in-app SBOM display, or app-side dependency inventory.
- Generating SBOMs for CI *release* builds (release stays local per
  `release-target`; CI only builds debug).
- SBOM signing, Vulnerability Exploitability eXchange (VEX), or Dependency-Track
  import automation — consumers can feed the JSON out of the box.
- Modifying vcpkg, the submodule, or any native source.

## Decisions

### D1: CycloneDX Gradle plugin for the JVM graph

**Chosen:** `org.cyclonedx.bom` 3.x (3.4.1). It resolves
Gradle configurations after conflict resolution, is tested against Gradle
9.5/9.6 in its own CI, and has documented Android patterns
(`skipConfigs`/`includeConfigs` for variant runtime classpaths).

*Implementation note (verified during apply):* applied in `:app`, not at the
root. The per-variant direct tasks (`CyclonedxDirectTask` instances) need the
plugin classes on that module's script classpath, and the root aggregate task
is unused — the variant runtime classpath already carries `:auto`, `:core`,
and `:osmscout-client-java` as components. See proposal impact + Build.md §8.

**Alternatives:**

- *AGP built-in SBOM* — could not be confirmed as a stable, documented AGP 9.x
  feature during research. Betting the deliverable on an unverifiable feature
  is a release-blocking risk.
- *SPDX Gradle plugin* — SPDX output conflicts with the chosen CycloneDX
  format and requires explicit per-target setup; less mature.

### D2: Native coverage — vcpkg SPDX → CycloneDX

**Chosen:** vcpkg natively emits one SPDX SBOM per installed package at
`$VCPKG_ROOT/installed/<triplet>/share/<pkg>/vcpkg.spdx.json`. The pinned
`cyclonedx-cli` binary converts each to CycloneDX (`convert --input-format
spdxjson`) and the results are aggregated into the JVM BOM.
Microsoft's own vcpkg docs recommend exactly this conversion path, so the
native side rides on a supported tool rather than custom code.

*Implementation note (verified during apply):* the aggregation/merge does NOT
go through `cyclonedx-cli merge` — the CLI concatenates components without
any deduplication and its .NET argument parser treats a space-joined
`--input-files` list as one path (`PathTooLongException`). Instead the native
BOM is composed programmatically: keep only real `pkg:vcpkg/` components,
deduplicate by group+name+version across the three triplets, and merge the
JVM + native sections with `cyclonedx-core-java` model classes; the CLI is
still used for `convert` and `validate`.

**Alternatives:**

- *Hand-written SPDX→CDX merge code* — full fidelity control but custom schema
  handling to maintain; disproportionate for a hygiene artifact.
- *Raw SPDX sidecar files* — no conversion loss, but fragments the deliverable
  and contradicts the single-file-per-variant goal.

### D3: One merged BOM per variant

**Chosen:** merge the aggregate JVM BOM, the converted native components, and
the submodule record into a single CycloneDX JSON per variant. Consumers
(Play upload, scanners) ingest one file; the release SBOM pair mirrors the AAB
pair.

**Alternative:** *Separate JVM + native BOMs* — simpler pipeline but pushes
joins onto consumers; rejected as worse for the hygiene goal.

### D4: CLI acquisition — pinned download in a Gradle task

**Chosen:** a small Gradle task downloads a pinned `cyclonedx-cli` release
archive matching the host OS into a project-managed cache
(`app/build/cyclonedx-cli/`), verifies it is executable, and fails with an
actionable message on download/exec failure. First run needs network (release
machine and CI both have it); afterwards fully offline. Version pinned as a
constant so SBOMs stay reproducible.

**Alternatives:**

- *Docker* — adds a container runtime requirement to dev machines and CI;
  rejected.
- *System-installed CLI* — version drift and manual setup on every machine;
  rejected.

### D5: One SBOM task per variant, not one aggregate

**Chosen:** register one `CycloneDxTask` per required variant with
`includeConfigs` set to that variant's runtime classpath
(`mobileReleaseRuntimeClasspath`, `automotiveReleaseRuntimeClasspath`,
`mobileDebugRuntimeClasspath` ...), then chain the native convert/merge steps
per variant. This is the documented multi-task pattern for the plugin and
gives exact per-flavor SBOMs (they legitimately differ: `app-automotive`
pulls car-app dependencies).

**Alternative:** *Single aggregate task over all configurations* — one BOM
mixing debug/release and both flavors; loses the per-AAB pairing the specs
require.

### D6: All three triplets, dedupe by name+version

**Chosen:** convert every triplet's SPDX files and merge them; components equal
in name and version collapse into one entry (arm64-v8a / armeabi-v7a / x86_64
are the same software). The AAB ships all three ABIs, so omitting any triplet
would under-report what ships.

**Alternative:** *arm64 only* — halves conversion work but misrepresents the
universal AAB; rejected.

### D7: Missing native SPDX data fails generation

**Chosen:** per the spec, generation fails with the missing package/SPDX path
named when e.g. a binary-cache-restored vcpkg package predates SBOM support.
Silently emitting a JVM-only SBOM would fake "full coverage".

**Alternative:** *Warn and continue* — cheaper but produces a misleading
artifact; the spec fixed this behavior, this decision just records the
implementational consequence (fail inside the task action, message names file).

### D8: Wiring and outputs

**Chosen:** task names follow AGP casing — `:app:generateSbomMobileRelease`,
`:app:generateSbomAutomotiveRelease`, `:app:generateSbomMobileDebug` — declared
inputs (variant configurations, vcpkg installed tree, submodule `.git` HEAD,
CLI binary) for correct up-to-date checks. Outputs land in
`app/build/outputs/sbom/<variant>/bom.json` — an artifact directory symmetric
with `outputs/apk` and `outputs/bundle`, distinct from the plugin's
`build/reports/bom` report default. The custom `release` task gains a
dependency on the two release SBOM tasks; version metadata is passed from the
release version computation already living in `app/build.gradle.kts`
(see that file's `nextReleaseVersion`/`readReleaseState` helpers — SBOM tasks
must only *read* that state, never call the bump path, so version-state
behavior of `release-target` is untouched).

**Alternative:** *Plugin-default `build/reports/bom` location* — adequate as a
report, wrong for a release artifact that must sit beside AABs; rejected.

## Risks / Trade-offs

- [CycloneDX plugin 3.x vs AGP 9.4 new DSL] → Spike task in this change runs
  the plugin on this exact repo before implementation; if incompatible,
  fallback to the SPDX-adjacent path is documented but plugin 3.x reads
  configurations only, so the risk is low.
- [vcpkg binary-cache packages missing SPDX files] → Generation fails loudly
  (D7); operator rebuilds the affected package with the current pinned vcpkg
  commit. Verification task checks all three triplets' SPDX presence once.
- [SPDX→CDX conversion fidelity] → Microsoft-documented loss of some
  SPDX-only fields; acceptable for a dependency-inventory SBOM; component
  name/version/type survive the conversion, which is what the specs assert.
- [CLI download failure at release time] → Download failure fails the task with
  the pinned version + URL in the message; cached CLI keeps subsequent runs
  offline.
- [Debug-SBOM runs in CI add minutes] → The debug SBOM reuses the same JVM
  dependency graph and vcpkg tree already present in the CI job; incremental
  wiring keeps the cost to the CLI convert/merge itself.

## Migration Plan

Additive change: apply the plugin, register tasks, wire `release`, add the CI
step, document in guidelines/Build.md. Rollback: revert the three build/CI
files; no other build behavior depends on the new tasks.

## Open Questions

- Concrete cyclonedx-cli and plugin versions — pinned during the spike/implementation tasks without affecting the specs or this design.
- CycloneDX spec version of the output (plugin default 1.6) — cosmetic for scanners; not spec-relevant.
