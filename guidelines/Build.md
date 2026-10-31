# Build Guidelines — Build, Test & Release Skills

How to build NaviVeylin, run its tests, and produce release builds. This document
names the skills that wrap the Gradle workflows and explains when and how to use
them.

**Maintenance rule** — when a change supersedes a build convention here, update
this document in the same change. This document is the one owner of the gate's rules, of every gate
measurement, of the agent iteration protocol (§2a) and of the native build facts (§12). The other sites that mention them — `AGENTS.md`, the `openspec/config.yaml` tasks/archive
guidance, and the build/test skills — carry a section reference instead of a restated rule or measurement;
a restatement found there is repaired by pointing it at the owning section here, and a measured number is
quoted only where its run is dated (§2c, §6c–§6e).

---

## 1. Skills

Five skills wrap the existing Gradle calls and the verification discipline around them, and three more wrap
the tooling's own shell gates and the loop's spend (`tools/issues/**`, `tools/sensors/**`, `tools/loop/**`,
`tools/check-portability.sh`, `tools/loop-spend.py`). They are named
and discoverable by LLM agents (loaded on demand from `.pi/skills/`); use them whenever build, test,
verification, or release work is requested or required by the OpenSpec apply/archive guidance. The loop
they serve — when to look, when to measure, when to run which suite — is §2a's *Agent iteration protocol*;
the skills are its hands, not its rule.

| Skill | Purpose | When to use |
|---|---|---|
| `build-app` | Compile the app — debug APKs, flavors, ABIs | User asks to build/compile; verify code compiles; after Kotlin/native changes |
| `run-tests` | Execute unit + instrumented tests | User asks to run tests; verify tests pass; before marking a task complete |
| `revert-check` | Falsify a new guard: one mutation → the named case must fail → restore → forced green, with the evidence recorded | A task says "revert-check"/"one mutation"/"the case that must fail"; a change adds a test for an invariant; before claiming a test covers new behaviour |
| `mutation-runner` | Run a *versioned set* of shell-gate mutations in one command (`tools/mutate.sh`), with a guard against a mutation that never landed and one against a case that does not notice | The gate is a self-test, not Gradle; a change's falsification pass is owed for `tools/**`; a mutation set has to be run, listed or repaired |
| `portability-check` | Prove the tooling names no project value and still runs in another project (`tools/check-portability.sh` + its 21 cases + the synthetic foreign project) | A change touches `tools/issues`, `tools/sensors`, `tools/loop` or the check; a task says "portability"/"no project value"/"the foreign project"; CI's `Check portability` step fails |
| `loop-spend` | Measure what the loop's children spent and on what — the re-read context, the prompt baseline, tool-result traffic, repeated file reads, spend per role and per day, and what each lever would save (`tools/loop-spend.py` + `tools/loop-spend-selftest.sh`) | Items cost more than they used to; an item's tokens have to be explained; a change claims to reduce the loop's spend and owes a before/after |
| `compose-geometry` | Assert a UI geometry invariant (size, tap target, disjointness, visibility inside a parent) in a Compose case instead of `assertExists()` | A phone control or tap area is added/moved; a task asks for a bounds/48 dp/disjointness/visibility case; a device run shows a tap landing on the wrong action or an action that is unreachable |
| `release-build` | Produce Play-ready release AABs (mobile + automotive) | User asks for a release build or version bump; Play upload / sideload artifacts |

Each skill lives in `.pi/skills/<name>/SKILL.md`. The hand-written ones are versioned with the project; a
skill the installed OpenSpec CLI generates is not, because `openspec update` rewrites those from its own
templates and reports an edited one as drifted, so committing one breaks the install's freshness check. The
ignore rule keys on the CLI's `openspec-` prefix, so a hand-written skill whose name carries that prefix needs
its own `!` line in `.gitignore` (the two that exist are `openspec-open-changes` and
`openspec-proof-gap-audit`), and `git ls-files .pi` prints the tracked set. To use a skill across projects, copy
its directory to `~/.pi/agent/skills/<name>/`.

### Looking at a screenshot (harness prerequisite)

Pixels are read in a **throwaway child session**, never in the session that holds the work:

```text
subagent({agent: "delegate", cwd: "/home/tim/projects/NaviVeylin",
          task: "read .pi/logs/<check>/shot.png and describe the screen in words — no coordinates,
                 no numbers; write nothing, change nothing"})
  -> the answer is text; it goes into the evidence file (or the change's tasks.md as triage)
python3 tools/measure-highlight.py "$D/shot.png" --dump "$D/ui.xml" --margin 126   <- the verdict
```

Why the shape, and the one rule behind it:

- **A tool result must never carry an image block into the session holding the work.** Measured 2026-10-16: two
  image-bearing tool results in one session, then a pi error that repeated on every further prompt — the
  session was unusable and the error text was lost. Measured 2026-10-17: a `delegate` child read
  `.pi/logs/carnav1/A-browse.png` through its native `read`, *did* receive the pixels, described the frame
  (the blue "+" plate, the "Driver" chip, the climate rail readings) and completed its turn. The child's
  transcript is disposable, this session's is not, so the look happens where a loss costs one child.
- **No second Pi process and no third-party package.** The agent does not run `pi --print` (the owner's rule;
  `pi` is not in lean-ctx's shell allowlist, so the call is refused anyway). The third-party screenshot package
  that the earlier version of this section installed is gone as well: it carried a bundled TUI host that had to
  be version-pinned and filtered through an extension allowlist, i.e. harness code owned by nobody here
  (`TODO.md` §149, retired 2026-10-17). The child session is the harness-native route to the same thing — a
  process that can be thrown away.
- **A child is not a `ctx_*` runtime.** It is handed the native tools (`read`, `bash`, `edit`, `write`) and
  **not** lean-ctx's `ctx_*` tools, and native `read` returns image content only for a vision-capable model. A
  child inherits the parent's model, so a session on a vision-capable model sees the pixels while one on a
  text-only model receives a placeholder; in that case the workflow degrades to the measurement script alone,
  which answers *where* but not *what*.
- **Look first, then measure** with `tools/measure-highlight.py`: the look is triage, the script's numbers and
  exit code are the evidence (`pixel-check` skill, §11 below).

## 2. Common behavior

Moved to `BuildGate.md` §2 — the skill contract and the index of §2a–§2e.

## 2a. Agent iteration protocol

Moved to `BuildGate.md` §2a.

## 2b. Liveness guard and shell recipes

Moved to `BuildGate.md` §2b.

## 2c. Gate timing record and baseline

Moved to `BuildGate.md` §2c.

## 2d. The build JVM (daemon JVM criteria)

Moved to `BuildGate.md` §2d.

## 2e. The actions' Node runtime

Moved to `BuildGate.md` §2e.

## 3. Commands reference

| Goal | Command |
|---|---|
| Build debug APK (all 3 ABIs, both flavors) | `./gradlew :app:assembleDebug` |
| Build phone/Android Auto flavor only | `./gradlew :app:assembleMobileDebug` |
| Build AAOS flavor only | `./gradlew :app:assembleAutomotiveDebug` |
| Build single ABI (fastest iteration) | `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` |
| Run all unit tests | `./gradlew test` |
| Run all unit tests — the **full gate** (all modules, both `:app` flavors, forced) | `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` |
| Run the full gate in a tree with no vcpkg checkout | the same line: `-PjvmTestsOnly` keeps the SBOM and licence chain out of the test graph, so the suite configures without `vcpkg/` (the refusal that keeps the property out of a packaging invocation is `guidelines/Build.md` §9) |
| Run the gate a change owes, by its own pathspec (what the loop's gate command names) | `tools/loop/gate.sh --base <sha>` — the Gradle gate when a compiled path changed, else the shell commands of the pipeline's check steps that read the touched paths; `--plan` prints the selection without running it |
| Run app unit tests only (one flavor — there is no `:app:testDebugUnitTest`) | `./gradlew :app:testMobileDebugUnitTest` (or `:app:testAutomotiveDebugUnitTest`) |
| Run single test class | `./gradlew :app:testMobileDebugUnitTest --tests "<FQCN>"` |
| Run instrumented tests (device required) | `./gradlew connectedAndroidTest` |
| Release build (both AABs, bumps version) | `./gradlew release` |
| Generate SBOM for one variant | `./gradlew :app:generateSbom<MobileDebug|MobileRelease|AutomotiveRelease>` |
| Generate only the native SBOM section | `./gradlew :app:mergeNativeSbom` |

A run meant to cover the whole suite adds `--continue`, or the first failing module ends the invocation and
the remaining suites never execute; read the executed-task list in the output for what actually ran, not the
flavors one expects (§134.65).

The gate above is only as wide as the profile's map: `LOOP_GATE_SUITES` (`tools/loop/loop.conf`) has to reach
every `bash`/`sh` command a `Check …` step of the pipeline runs, or a landing the pipeline refuses is green in
the run — which is what reddened `main` on 2026-10-21. The map has two sides, and both are pinned.
The *command* side: the map's unit is a pipeline *step*, but a path carries the commands of the tool it names,
not necessarily all of the step — a tool's check, its self-test and its mutation list are carried together
(`tools/build-inputs*` offers all three), while a step's other commands are carried by the paths of their own
tools (`tools/issues/*` owes the issue tooling's self-test, the tracker sets owe their own `tools/mutate.sh …
--list`), so a step's commands are covered even where no single path reaches all of them;
`gate#theMapCoversEveryPipelineStep` pins that side against the pipeline the profile names in
`LOOP_GATE_PIPELINE`, so a step the pipeline gains and the map does not carry fails the loop's own suite and
names the missing commands. The *path* side: which paths a step's tools read is named by no workflow, so the
reader edges were measured once and are hand-kept, and every measured edge owes the *whole* step whose tool
reads the path — the pipeline runs the step as a unit and any of its commands can be the one that reddens.
Measured 2026-10-25, for `Check portability`: `tools/check-portability.sh` opens
`tools/loop/loop.conf`, `tools/tooling.json` and `tools/sensors/sensors.json`, and scans `tools/issues/*.sh`,
`tools/sensors/*.sh` and `tools/loop/*.sh` as its engine set; for `Check issue tooling`: its case
`lib#onlyTheSeamCallsTheRealCli` greps `tools/loop/*.sh` and `tools/sensors/*.sh`, and its taxonomy case reads
`tools/sensors/sensors.json`. None of those edges was owed by a command of the reading step, and
`tools/sensors/sensors.json` is read by three steps — `Check portability`, `Check the build inputs` and
`Check issue metadata` — which each owe their own commands for it.
`gate#aPathACheckReadsOwesThatStepsCommandSet` pins the side now, so a project value planted in one of the paths
is a red loop suite rather than a green run. Two edges stay residual, both hand-kept. A step whose check is
inline shell or a Gradle task is outside the pin altogether: `Check daemon JVM criteria` (over
`gradle/gradle-daemon-jvm.properties`, which no compiled pattern matches either) is green in the run today. And a
step added to the pipeline later carries its own paths and reader edges until someone adds them — the pin turns
that escape into a red loop suite for the command side, not for the path side.

## 4. Result evaluation

Moved to `BuildEvidence.md` §4.

## 4a. The verdict, the warnings and the failures

Moved to `BuildEvidence.md` §4a.

## 4b. Which flavors and suites a run must cover

Moved to `BuildEvidence.md` §4b.

## 4c. Native scope, pruned configurations and a restored mutation

Moved to `BuildEvidence.md` §4c.

## 4d. Attributing a red run

Moved to `BuildEvidence.md` §4d.

## 4e. Falsification, flakes and scheduling traps

Moved to `BuildEvidence.md` §4e.

## 4f. Artifacts, evidence rules and native verification

Moved to `BuildEvidence.md` §4f.

## 5. Release versioning

- **Rule: `./gradlew release` generates the `versionName` and bumps the `versionCode`.** It generates
  `versionName` as `<yyyy>-<MM>-<dd>-<N>` (4-digit year, zero-padded month/day, running number `N` without
  leading zeros), increments `versionCode` by one, then runs `:app:bundleMobileRelease` and
  `:app:bundleAutomotiveRelease`.
- **Rule: the release version state is machine-local and release refuses a missing or future-dated one.**
  Version state lives in `app/release-version.properties` (**gitignored**,
  machine-local — keep it on the release machine): `lastDate`,
  `runningNumber`, `versionCode`, `lastVersionName`. Same day → `N+1`; new day
  → `N` resets to 1; `versionCode` starts at 20. `release` **fails fast** if the
  file is missing or its `lastDate` is in the future (a silent reset would emit
  a duplicate `versionName` or a `versionCode` at/below the published one, which
  Play rejects). **Rule: Play's dedup key is the `versionCode`, not the `versionName`** — a
  date-`-N` name can repeat across days, but every uploaded AAB needs a
  versionCode strictly greater than every published one (incl. AABs built from
  stale state). If the state lags reality (release published elsewhere), set
  the values to the last published ones before running `release`.
- **Rule: only the `release` task bumps the version.** The bump happens at configuration time, gated on the
  `release` task being requested — every other build uses the fixed fallback `1.0.0`/`19` and never touches
  the state file. Direct `bundleRelease` without `release` reuses the last persisted values (single release
  machine assumed).
- **Rule: a release AAB is signed when `app/release.keystore` is present**; absent → a warning is logged and
  the unsigned AAB is still produced.
- **Rule: each release variant is uploaded to its own Play track.** Outputs:
  `app/build/outputs/bundle/mobileRelease/app-mobile-release.aab` (phone + Android Auto) and
  `app/build/outputs/bundle/automotiveRelease/app-automotive-release.aab` (AAOS).
- Each release variant also emits a CycloneDX SBOM next to its AAB; see
  §8 SBOM generation.

## 6. Test constraints

Moved to `BuildTests.md` §6.

## 6a. The JNI stub and the classloader sandbox

Moved to `BuildTests.md` §6a.

## 6b. Teardown, the Compose sandbox and geometry claims

Moved to `BuildTests.md` §6b.

## 6c. Queue post-conditions and declared fork budgets

Moved to `BuildTests.md` §6c.

## 6d. Declared test-JVM concurrency

Moved to `BuildTests.md` §6d.

## 6e. Wall-clock waits and the conversion measured

Moved to `BuildTests.md` §6e.

## 6f. Diagnosing a red run

Moved to `BuildTests.md` §6f.

## 6g. Instrumented runs and the submodule's suite

Moved to `BuildTests.md` §6g.

## 6h. The JNI stub for unit tests

Moved to `BuildTests.md` §6h.

## 7. Code coverage

Moved to `BuildEvidence.md` §7.

## 8. SBOM generation (CycloneDX)

Every build variant can produce a CycloneDX JSON SBOM covering the JVM
Gradle dependency graph, the native vcpkg dependency tree, and the
libosmscout submodule. Format: CycloneDX 1.7 JSON.

**Tasks** (group `sbom`, defined in `app/build.gradle.kts`):

| Task | Produces |
|---|---|
| `:app:generateSbomMobileDebug` / `...MobileRelease` / `...AutomotiveRelease` | full merged SBOM, `app/build/outputs/sbom/<variant>/bom.json` |
| `:app:generateSbomJvm<Variant>` | JVM section, `.../jvm-bom.json` (plugin direct task, `includeConfigs=<variant>RuntimeClasspath`, tests excluded) |
| `:app:mergeNativeSbom` | native section, `app/build/outputs/sbom/native/native-bom.json` (shared by all variants) |
| `:app:downloadSbomCli` | cached `cyclonedx-cli` binary under `app/build/cyclonedx-cli/` (pinned `0.33.1`, one-time download) |

**Wiring**: `./gradlew release` runs both release-variant SBOM tasks, so each
AAB has a sibling `bom.json` carrying the release `versionName`. CI
(`.github/workflows/build.yml`) generates the `mobileDebug` SBOM after the APK
build and uploads it as the `naviveylin-sbom` artifact. SBOM tasks only read
the release version state — they never bump it (versioning behavior of §5 is
untouched).

**Composition**: JVM section comes from the `org.cyclonedx.bom` 3.4.1 plugin
(direct task per variant, `schemaVersion` 1.7). Native section: vcpkg ships a
per-package SPDX SBOM (`vcpkg/installed/<triplet>/share/<port>/vcpkg.spdx.json`);
the port list is read from `vcpkg/installed/vcpkg/status` (Architecture
filtered; vcpkg-* tool ports and the intentionally-not-installed
`libosmscout` port are excluded). Each SPDX file is converted to CDX with
`cyclonedx convert`, template-URL external references (vcpkg source-origin
heuristics like `${VERSION_MAJOR_MINOR}` — invalid URIs) are stripped, and
only real `pkg:vcpkg/` components are kept, deduplicated by
group+name+version across the three triplets (arm64 / arm-neon / x64 share the
same software). The final merge combines JVM + native via the core-java model
and records the submodule SHA (`git rev-parse HEAD` of
`app/src/main/cpp/libosmscout`) as a `libosmscout` component. The root
component (`metadata.component`) is the application itself, stamped at the
build version **with the application license** (`GPL-3.0-or-later`, the same
`FIRST_PARTY_LICENSE_REF` constant the first-party components resolve
through). Every output passes `cyclonedx validate` inside the task.

**Known cyclonedx-cli quirks** (handled in the Gradle code — do not
re-introduce): `--input-files` must be repeated per file (a single
space-joined argument crashes with a .NET `PathTooLongException`), and
`merge` concatenates without deduplicating components.

**Troubleshooting**: an SBOM task fails with `Missing SBOM data for vcpkg
package ...` when an installed package predates vcpkg SBOM support (binary
cache content built by an older vcpkg or restored from cache without the
SPDX file). Rebuild the named package, e.g.:

```bash
VCPKG_BINARY_SOURCES=clear ./vcpkg/vcpkg install gettext:arm64-android --recurse \
  --overlay-ports=vcpkg-overlays --overlay-triplets=vcpkg-overlays/triplets
```

(with `ANDROID_SDK_ROOT`/`ANDROID_NDK_HOME` set as in `setup-vcpkg.sh`) and
re-run; the task names the exact missing file.

## 9. License compliance

The SBOM also carries license data, and that data is what the app shows and
what the gate enforces. Three pieces fit together:

| Piece | Where | Purpose |
|---|---|---|
| Curated data | `licenses/native-license-map.json`, `licenses/license-policy.json`, `licenses/texts/` | what each native component declares, what the build permits, where each license text comes from |
| Evaluation logic | `buildSrc/src/main/kotlin/com/naviveylin/build/licensing/` | pure Kotlin: expression/election resolution, policy gate, scope classification, asset generation — unit-tested by the build itself |
| Generated output | `licenses/dependencies.json` + `licenses/texts/*` in each APK's assets; `NOTICE` next to each SBOM | what users read, tied to the artifact that was built |

**SBOM license data.** Each component of a variant SBOM carries at least one
license identifier (`license.id`, or a `licenses[].expression` for a
`LicenseRef-`), plus properties: `naviveylin:license:scope`
(`shipped`/`buildTimeOnly`), `naviveylin:license:scope-evidence` (why),
`naviveylin:license:scope-ambiguity` (shipped without symbol evidence),
`naviveylin:license:notice`, and `naviveylin:license:caveat` (an unresolved
doubt, reported rather than hidden).

**Scope is derived from the built artifact** — the variant's stripped native
libraries under `intermediates/stripped_native_libs/<variant>/`, filtered to
the ABIs this build produces. A component is *shipped* when a packaged object
carries its name, or a probe symbol from `probeSymbols` appears in a packaged
object, or one of its static archives reaches a native `target_link_libraries`
call (recorded as ambiguous when no symbol of the component itself was found —
`expat` is the current example). Find-module cache defaults such as
`LIBXML2_LIBRARY` are **not** link inputs; counting them once made protobuf and
libxml2 look shipped. Everything else is `buildTimeOnly`. The curated `scope` in
the map is a cross-check: a disagreement fails the build rather than passing
silently. The SBOM task therefore depends on the variant's native libraries.

**The gate.** `checkLicensePolicy<Variant>` reads the generated SBOM and
`licenses/license-policy.json` and fails when a component's license is missing,
unresolved, not permitted for its scope, or a choice without a recorded
election. `checkLicensePolicy` covers the CI variant; the `release` target gates
both release flavors. It is deliberately **not** wired into `assemble`: a policy
failure must not block unrelated local work, but it must fail the gate that runs
it. Warnings surface every recorded caveat (unresolved evidence or a claim
needing re-confirmation — e.g. libosmscout's version-less LGPL mapping on
submodule bumps) and any license still awaiting the application's own license
decision (`reviewRequired`; currently empty — the GPL-3.0-or-later decision
covers the verified compatible shipped set).

> The gate enforces the policy **declared** in this repository. It is not a
> legal assessment, and passing it does not mean the project's obligations are
> satisfied. The application's own code is licensed under **GPL-3.0-or-later**
> (`LICENSE` declares `SPDX-License-Identifier: GPL-3.0-or-later`; README §License
> repeats it). Because GPL §5 requires a copy of the license with the program,
> the full GPLv3 text is embedded in the inventory (`GPL-3.0-or-later.txt`).
> Older *released* APKs built before this decision carry the previous TBD
> inventory data — immutable, and not re-generated retroactively.

**Policy file** (`licenses/license-policy.json`):

- `permitted.shipped` / `permitted.buildTimeOnly` — two separate lists on
  purpose: `GPL-3.0-only` (the `gettext` build tool) is permitted as build-time
  tooling, and the same license on a *shipped* component fails.
- `elections` — for a component whose license offers a choice, the alternative
  relied upon, and it must be one the declaration actually offers. Currently
  cairo `MPL-1.1`, freetype `FTL`, marisa-trie `BSD-2-Clause`, glib
  `LGPL-2.1-or-later AND LGPL-2.1-only`.
- `licenseRefs` — SPDX's mechanism for licenses outside the SPDX list.
  `LicenseRef-AndroidSDK` (Play Services, terms at a URL, text not distributed)
  is the only remaining entry: first-party code used to resolve through
  `LicenseRef-NaviVeylin`, but since the application license decision it
  resolves to the plain SPDX id `GPL-3.0-or-later` (in `permitted.shipped`, with
  embedded text).
- `textSources` — where each identifier's text comes from, declared rather than
  inferred: `file` (canonical text under `licenses/texts/`), `vcpkgPort` (a
  port's `copyright`), `androidNdk` (the NDK's `NOTICE.toolchain`), or `none`.
  An identifier used by a distributed component without a source fails the
  build. Two examples of why inference was abandoned: the `Apache-2.0` text
  came out as the entire NDK bundle, and `LGPL-2.1-or-later` came out as
  marisa-trie's one-line statement instead of the license text.
- `noticeRequired` — identifiers whose notice must travel with redistribution.
  Only *distributed* components are asked for one. `GPL-3.0-or-later` is listed
  so the first-party license text is embedded like any other distributed
  license.
- `reviewRequired` — identifiers awaiting the project owner's license decision;
  the gate reports them and never treats presence in the file as clearance.
  Currently **empty**: the GPL-3.0-or-later application license decision covers
  the third-party set verified GPLv3-compatible. Entries declare a `components`
  scope (empty = every carrier); the decoder honours it, so a scoped entry warns
  only the named components. The residual evidence duty for libosmscout (no
  version clause → `LGPL-2.1-or-later` conservative mapping) lives in
  `native-license-map.json`'s caveat (submodule LICENSE since 2026-03, commit
  f4a9dabe7: LGPL plus app-store exceptions, **no GPL text**) and is reported by
  the gate as a caveat on every build.

**Generated assets.** `generateLicenseAssets<Variant>` (a `buildSrc` task,
`GenerateLicenseAssets`) writes `licenses/dependencies.json` and
`licenses/texts/*` into `build/generated/assets-licenses/<variant>/`, wired as
that variant's assets source through the Variant API
(`variant.sources.assets.addGeneratedSourceDirectory`), plus `NOTICE` next to the
variant's SBOM. Two constraints learned the hard way: AGP rejects Provider
instances in the SourceSet API, and a single shared generated root leaks one
variant's inventory into another's APK (the automotive merged assets had picked
up `licenses/mobile/debug/`). The app reads `licenses/dependencies.json` from its
own assets — no shared directory, no runtime lookup by flavor.

**A JVM-only test run carries no inventory** (`-PjvmTestsOnly`, change
`fix-jvm-test-run-needs-no-native-tree`, spec `build-sbom`). Because the inventory is a variant asset
source and the unit-test tasks package the variant's assets, the test graph used to carry
`generateLicenseAssets` → `generateSbom` → `mergeNativeSbom`: a tree without a vcpkg checkout could not
configure a single test, and the native section's port list was read while `mergeNativeSbom` was being
*created*. The property leaves the asset source unwired — the chain drops out of the test graph — and the
vcpkg status file is an input only when it is there, with the port list a `Provider` read when the task
runs, so a missing tree fails `mergeNativeSbom` naming the file instead of failing the configuration of
every graph that includes it. `mergeNativeSbom` still refuses to produce an SBOM without that tree, and
"Missing native SBOM data fails generation" is unchanged. The property is **refused** when the same
invocation packages or checks the policy (`assemble*`, `bundle*`, `release`, `build`, `install*`,
`generateSbom*`, `mergeNativeSbom`, `generateLicenseAssets*`, `checkLicensePolicy*`): an artefact built
that way would ship without its `licenses/` assets, which is the guarantee this section states. The
strict path is the CI steps that assemble the APK, generate the SBOM and run the policy gate with the
vcpkg tree present; the assertion is the shell suite `tools/check-jvm-test-graph-selftest.sh`, three
`--dry-run` configurations. That suite needs the Android SDK installed and licensed — every case configures
`:app` — so its CI step sits below `Install Android SDK` and above `Clone vcpkg`, the last point at which the
tree holds no vcpkg checkout; placed above the SDK step it failed five runs with
`LicenceNotAcceptedException`.

**buildSrc.** `./gradlew -p buildSrc test` runs the license suite (52 tests); the
`build` task Gradle runs on every invocation includes them, and unchanged inputs
are up-to-date. Java 17 toolchain is pinned there so Kotlin and Java targets
agree (a newer daemon JVM otherwise emits an inconsistent-target warning).

**CI.** `Check license policy` runs after the SBOM step, and
`Upload license inventory` publishes the generated inventory
(`app/build/generated/assets-licenses/mobileDebug/licenses/**`) so a reviewer can
read it without unpacking an APK.


## 10. On-device evidence for Android Auto / AAOS (host-crash triage)

Moved to `BuildNative.md` §10.

## 10a. Booting the AAOS AVD and driving the car UI

Moved to `BuildNative.md` §10a.

## 10b. Judging the car surface and the render loop

Moved to `BuildNative.md` §10b.

## 10c. Footprint, the app's host sends and route numbers

Moved to `BuildNative.md` §10c.

## 10d. Rejected mutations, the error notice and database registration

Moved to `BuildNative.md` §10d.

## 10e. Expectation baselines and the car search scope

Moved to `BuildNative.md` §10e.

## 10f. Two checks that need evidence before a behaviour is specified

Moved to `BuildNative.md` §10f.

## 10g. Start-map selection on the phone (`fix-start-map-selection`)

Moved to `BuildNative.md` §10g.

## 10h. Stylesheet load count per start (`dedupe-stylesheet-loads`)

Moved to `BuildNative.md` §10h.

## 10i. Device recipes that cost a round every time (2026-10-05)

Moved to `BuildNative.md` §10i.

## 10j. A calculated route's length — which number is a route length (`fix-router-overall-distance`)

Moved to `BuildNative.md` §10j.

## 10k. The route-row census — what the route lists may show (`route-panel-from-steps`, `share-route-rows-in-core`)

Moved to `BuildNative.md` §10k.

## 11. Measuring a phone UI finding (do this before changing code)

Moved to `BuildEvidence.md` §11.

## 12. Native build and vcpkg

Moved to `BuildNative.md` §12.
