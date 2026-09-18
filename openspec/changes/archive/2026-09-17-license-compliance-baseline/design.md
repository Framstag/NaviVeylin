# Design: license-compliance-baseline

## Context

See `proposal.md` — Why. Constraints that shape the approach:

- The SBOM pipeline already exists and is wired into `release` and CI
  (`guidelines/Build.md` §8, `app/build.gradle.kts` lines ~370–520): a per-variant
  JVM SBOM from `org.cyclonedx.bom` 3.4.1, a native section converted from vcpkg's
  per-port SPDX files with pinned `cyclonedx-cli`, merged into
  `app/build/outputs/sbom/<variant>/bom.json`.
- vcpkg's SPDX files carry no license data, but each installed port ships full
  license text at `vcpkg/installed/<triplet>/share/<port>/copyright`. Port
  `vcpkg.json` license fields exist for many ports but are incomplete, and
  `marisa-trie` is our own overlay port (`vcpkg-overlays/`).
- Generated assets already have a project pattern: `syncSubmoduleStylesheets`
  syncs the submodule stylesheet tree into `build/generated/assets`, registered via
  `assets.srcDir` (`app/build.gradle.kts` lines 239–247) and wired into `preBuild`
  and every `merge*Assets` task (lines 341–368). No generated content is committed.
- The packaged native set is analyzable from the built artifact: `lib/<abi>/*.so`
  plus ELF `NEEDED` entries and defined symbols. This is how the shipped inventory
  in `proposal.md` was measured (cairo/freetype/fontconfig/libpng/zlib statically
  inside `libosmscout_map_cairo*.so`; `libomp.so` present and `NEEDED` but absent
  from the SBOM; `pango`/`glib`/`harfbuzz`/`fribidi`/`libiconv` absent entirely).
- Threading and build conventions: `guidelines/Design.md` §4 (never block the main
  thread), §10 (deterministic builds, no version-state mutation), §11 (JVM-testable
  logic, Robolectric classloader rule).

## Goals / Non-Goals

**Goals:**

- One artifact is authoritative for the license inventory: the merged SBOM.
- License data is derived from sources the project controls, with every
  unresolvable case failing loudly instead of defaulting to a placeholder.
- Shipped vs build-time-only classification is derived from the built artifact.
- The app shows the same inventory the gate validated — no second list.

**Non-Goals:**

- Deciding or documenting the application's own license (proposal: out of scope).
- Legal sufficiency. The gate enforces a declared policy; it does not judge
  whether that policy satisfies every obligation. This must be stated in
  `guidelines/Build.md`.
- Any change to the native build's link graph, dependency set, or ABI matrix.
- Any change to map-data attribution (`osm-attribution` owns it).

## Decisions

### D1 — The merged SBOM is the single source of truth

Chosen: extend the existing CycloneDX pipeline so the merged SBOM carries license
identifiers and distribution scope per component; the gate and the in-app list are
both derived from that SBOM.

- Alternatives: (a) an independent license manifest maintained beside the SBOM —
  rejected: two inventories drift, and the gate would validate something other than
  what the build produces; (b) AboutLibraries' Gradle plugin as the inventory of
  record — rejected for the same reason, and it cannot see the native half at all.
- AboutLibraries is still considered for its **license-text catalogue** only (D5);
  that is a data source, not an inventory.

### D2 — Native licenses come from a curated map, auto-filled where safe

Chosen: a committed mapping file (new `licenses/native-license-map.json`) is
authoritative for native component → SPDX id. Where a port's `vcpkg.json` declares
an unambiguous SPDX license, that value is used as the automatic default; the map
overrides it. The port's `copyright` file must exist, since it supplies the text
shown to users and the evidence for review.

- Alternatives: (a) rely on vcpkg metadata alone — rejected: incomplete, and
  silently yields assertion-less components (today's failure); (b) scan vendored
  sources with Scancode/ORT — rejected: heavyweight and non-deterministic for a
  fixed 20-port set; (c) parse the `copyright` files into SPDX ids — rejected: they
  are aggregate free text, not machine-readable license declarations.
- Measured (task 1.1): 38 of 42 installed ports ship a `copyright` file;
  `dirent`, `libiconv`, `pthread` and `pthreads` do not, and none of the four
  installs a library for Android, so all four are build-time only. Three ports
  carry no license field in their `vcpkg.json` (`libiconv`, `liblzma`, `pthread`)
  and therefore need a map entry.
- Text requirements follow distribution, not installation: a component whose code
  is shipped needs its license text (the app shows it), while build-time-only
  plumbing needs a resolvable identifier only. The map records the source of each
  identifier so a reviewer can check the claim.
- Missing `copyright` file for a **shipped** component, or unmapped port → build
  failure naming the port (spec: `build-sbom` "Missing native SBOM data fails
  generation").

### D3 — Dual-license elections live in a committed file

Chosen: `licenses/license-policy.json` holds both the permitted SPDX set and the
per-component elections (cairo `MPL-1.1`, freetype `FTL`, marisa-trie
`BSD-2-Clause`). Elections are data, not code, so a change is reviewable.

- Alternatives: (a) express elections in Gradle build logic — rejected: a legal
  choice buried in build script, easy to change unnoticed; (b) allow any
  alternative in the license expression — rejected by the spec: an expression must
  be satisfied by an alternative the policy permits, so the choice must be
  explicit and recorded.
- A component whose expression offers a choice with no recorded election fails the
  build (spec: `license-compliance` "Dual-licensed components record an explicit
  election").

### D4 — Shipped/build-time classification is derived from the packaged artifact

Chosen: a build task inspects the packaged native library directory and the ELF
link/symbol information to determine which components' code is actually present,
and to attribute statically linked libraries to the shared object that contains
them. Classification is written into the SBOM as a component property. The CMake
link graph is used to corroborate static attribution where symbol visibility hides
evidence.

- Alternatives: (a) hand-maintained shipped list — rejected: rots on every
  dependency or link change, and is exactly the class of error this change exists
  to remove; (b) CMake link graph alone — rejected: it is build configuration, and
  it is what missed `libomp`, which is linked through compiler flags and never
  appeared as a CMake package.
- Known limit: symbol analysis is heuristic under hidden visibility or LTO. When
  evidence is inconclusive the component is classified by the CMake graph, and the
  ambiguity is recorded in the component properties so reviewers can see it.

### D5 — License texts are embedded at build time

Chosen: generate a compact, deduplicated license-text catalogue at build time —
texts keyed by SPDX identifier, sourced from the bundled catalogue data for the JVM
half and from vcpkg `copyright` files for native components — and package it with
the dependency list.

- Alternatives: (a) fetch texts at runtime from the SPDX license list — rejected:
  the spec requires the list to work offline, and a shipped artifact's obligation
  is to include the text, not to link to it; (b) per-component text copies —
  rejected: the same text repeated 121 times for Apache-2.0 alone.
- If AboutLibraries is adopted for the JVM catalogue, it is used as a data input;
  if its text coverage proves insufficient or its version churn undesirable, the
  catalogue is vendored instead. This does not affect the specs.
- Measured (task 1.4): the JVM half needs texts for exactly two SPDX identifiers
  (`Apache-2.0`, `MIT`), and the inventory needs no extra tooling because the
  CycloneDX plugin already reads POM license metadata. Outcome: vendor those texts
  as curated data and do not add a catalogue dependency. The one JVM case that is
  not an SPDX identifier is the Play Services license (TODO.md §18) and is handled
  by the non-SPDX modelling decision, not by a catalogue.

### D6 — The gate is our own task over the merged SBOM

Chosen: a Gradle task (new `license` task group) reads the merged SBOM plus
`licenses/license-policy.json` and fails on missing, unresolved, or non-permitted
licenses. It runs standalone, is wired into CI, and is not part of the default
`assemble` path so local iteration is not blocked by a policy failure it did not
cause.

- Alternatives: (a) `jk1/dependency-license-report` or `licensee` — rejected:
  JVM-only, so the entire native half would be unenforced, which is the half with
  the copyleft risk; (b) `cyclonedx-cli` — rejected: it has no policy engine;
  (c) an external service (FOSSA, ORT) — rejected: new pinned external tooling and
  network dependency for a 159-component graph, with no capability we need beyond
  the policy check. Revisit only if generated relink kits or written offers become
  a requirement.

### D7 — In-app license data is a generated asset, not committed content

Chosen: a build task writes the dependency/license JSON and text catalogue into
`build/generated/assets/licenses/`, registered like the stylesheet tree
(`assets.srcDir("build/generated/assets")`, wired into `preBuild` and all
`merge*Assets` tasks). The runtime reads these assets directly.

- Alternatives: (a) commit a generated snapshot — rejected: project convention
  forbids committed generated content, and dependency changes would silently
  outdate it; (b) generate Kotlin constants — rejected: large generated source,
  compile-time cost, and no benefit over an asset read.
- `AssetCopier` is deliberately **not** used: it exists because native code reads
  stylesheets from the filesystem, while license assets are read through
  `AssetManager` and are immutable for a given build.

### D8 — Android Auto keeps its current about screen

Chosen: no change to `auto/src/main/java/com/naviveylin/auto/AboutScreen.kt`. The
car screen keeps app identity and OSM attribution; the dependency list is not
surfaced in the car.

- Alternatives: (a) a `ListTemplate` drill-down per component — rejected: the car
  display is a driver surface, and a several-hundred-row license browser is not a
  driving-appropriate interaction; (b) a pane action linking to a hosted license
  page — rejected: introduces a hosting dependency and a network path where the
  spec requires offline availability.
- Recorded as a deliberate parity deviation in `guidelines/UI.md` (spec:
  `about-dialog` "License list is not surfaced in the car app").

### D11 — Build-time license logic lives in `buildSrc`, runtime parsing in app code

Chosen: the pure parts of the build-side work — license-expression resolution,
election validation, and policy evaluation — live in a new `buildSrc` module as
plain Kotlin with its own unit tests. `app/build.gradle.kts` keeps the IO and
adaptation: reading the merged SBOM, the curated map and the policy, calling the
pure logic, and writing the result. The runtime side (parsing the generated
license assets, the screen's state model) stays in app code with Robolectric
tests.

- Alternatives: (a) put everything in `app/build.gradle.kts` as today — rejected:
  task 4.2 requires unit tests for the evaluator, and build-script code is not
  reachable from the app test suite; (b) a separate Gradle module wired into the
  build's classpath — rejected: unsupported for build-script classpath deps
  without a composite build, and heavier than the problem needs; (c) duplicate the
  logic (build + app) — rejected: two implementations drift.
- Consequence: `buildSrc` is a new build component. Its `check` runs as part of
  every Gradle invocation, so the evaluator is verified continuously; the
  runtime-side tests stay in the app suite and remain visible to Kover.
- The license data written into the SBOM is produced inside the existing
  per-variant SBOM task rather than by a second task writing a second file, so
  there is never an SBOM artifact without license data next to one with it.

### D10 — Licenses without an SPDX identifier are declared as `LicenseRef-`

Chosen: a license that has no SPDX identifier is modelled with SPDX's own
`LicenseRef-<id>` mechanism. `licenses/license-policy.json` declares each such
identifier with its display name, canonical source address, the components it
applies to, and whether its text may be distributed. The license screen shows the
name and offers the canonical address; no third-party terms text is embedded for
these entries.

- Applied case: the four `com.google.android.gms` artifacts (Play Services)
  declare `Android Software Development Kit License` with a URL and no SPDX id →
  `LicenseRef-AndroidSDK`.
- Alternatives: (a) vendor the Android SDK terms text so the screen stays fully
  offline — rejected: it reproduces a third party's terms inside our artifact and
the copy ages silently; (b) a bare named exception with no identifier — rejected:
  it weakens the "no assertion-less value" rule that this change exists to
  enforce, for a case SPDX already models.
- Consequence: the identifier rule in `license-compliance` accepts an SPDX id or a
  policy-declared `LicenseRef-`; an undeclared non-SPDX license still fails. The
  offline requirement in `about-dialog` applies to texts the application
  distributes, and a `LicenseRef-` entry must render as a link rather than an empty
  text pane.

### D9 — Threading and lifecycle for the license screen

- A `@HiltViewModel` scoped to the license screen reads the assets on
  `Dispatchers.IO` in its `init`, publishing an immutable
  `StateFlow<LicenseUiState>` (`Loading` / `Content` / `Error`). No main-thread
  IO, per `guidelines/Design.md` §4.
- The data is immutable for the process lifetime, so it is read once per screen
  open and cached in the ViewModel; no invalidation, no refresh path, no
  background work after the first load.
- Cancellation rides `viewModelScope`; the screen holds no references outside
  Compose state. No native/JNI or Play Services involvement.
- `AboutDialog` only gains a navigation entry point; it stays a stateless
  composable driven by callbacks.

## Risks / Trade-offs

- [Curated native map drifts from what is actually linked] → the map must cover
  every native component or the build fails; the classification task flags
  mapped-but-not-shipped and shipped-but-unmapped components.
- [Symbol-based classification wrong under hidden visibility/LTO] → CMake graph
  corroboration, ambiguity recorded in component properties, and the rule that an
  inconclusive component is never silently reported as shipped.
- [Generated license assets bloat the APK] → deduplicate texts by SPDX id; measure
  the APK size delta as part of verification; assets are compressed in the APK.
- [The new gate fails CI for pre-existing unknowns] → land enrichment and
  classification before the gate; the policy file starts from the observed set;
  the gate message names the component and the missing data.
- [Adopting AboutLibraries pulls in a UI/plugin dependency whose API churns] →
  scope it to catalogue data only; if that proves fragile, vendor the texts. No
  spec or gate behavior depends on it.
- [Readers may over-read the gate as legal clearance] → `guidelines/Build.md`
  documents explicitly that the gate enforces the declared policy and is not a
  legal assessment; the license decision remains open per the proposal.
- [New user-facing strings diverge across translations] → follow
  `guidelines/UI.md` §10; add the new keys to every existing translation variant,
  falling back to English where a translation is not yet available.
- [`licenses/` adds a new top-level directory] → keep it to two data files and
  document it in `guidelines/Build.md` §8 and `AGENTS.md` so it is discoverable.

## Verification

Gradle-level (build machine with vcpkg installed):

- Enrichment: generate the SBOM for a variant and assert every component has at
  least one SPDX id, no `NOASSERTION`, and a distribution scope; assert `libomp`
  is present as shipped.
- Classification: assert linked native libraries are `shipped` and installed-but-
  unlinked ports (pango, glib, harfbuzz, fribidi, gettext-libintl, libiconv) are
  `build-time only`.
- Gate: with a fixture policy and a fixture component set, assert failure on a
  missing license, an unresolved license, a non-permitted license, and a
  choice-expression without an election; assert pass on the real set.
- Asset generation: assert the generated license assets are produced for
  `mobileDebug`, `mobileRelease`, and `automotiveRelease`, and that the JSON
  parses.

Unit tests (JVM):

- License data parsing and aggregation: deduplication of texts by SPDX id,
  component ordering, and the running-build content contract.
- Policy evaluation: permitted/denied/unresolved/election branches, including the
  expression-with-alternatives case.
- Classification logic on synthetic ELF/NEEDED fixtures where feasible.
- Compose UI test for the license screen (Robolectric; no native stub is loaded by
  this screen, and sandbox configuration rules from `guidelines/Design.md` §11 and
  `AGENTS.md` apply).

On-device (phone; emulator acceptable):

- Open About → license list; verify components, versions, and license ids are
  shown and a component's full text opens.
- Switch to airplane mode and repeat: the list and texts must render offline
  (`adb logcat` shows no asset-read failure).
- Android Auto: open the car about screen and confirm identity and map-data
  attribution are unchanged and no dependency list is offered.
- Build with a dependency added or removed and confirm the list reflects that
  build without any manual edit.
