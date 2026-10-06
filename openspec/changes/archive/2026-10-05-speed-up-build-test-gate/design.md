# Design — Speed up the build and test gate

## Context

Motivation and the measured phase table are in `proposal.md` — Why. The state that shapes the approach:

- The gate is a Gradle invocation, not a tool: `:app:test{Flavor}DebugUnitTest`, `:auto:testDebugUnitTest`,
  `:core:testDebugUnitTest`, `:osmscout-client-java:test`, plus the docs' forcing move `--rerun-tasks`,
  which re-executes every task in the graph.
- `:app` and `:auto` declare only `maxHeapSize = "1024m"` (`app/build.gradle.kts` 267–288,
  `auto/build.gradle.kts` 24–42); no fork count is declared anywhere, so Gradle's default of one test
  JVM applies. Measured: 1635 tests / 190 s of testcase CPU / 195 s wall per flavor, one fork.
- Both `:app` flavors execute the identical 215 test classes; `app/src/` has no `testMobile` /
  `testAutomotive` source set, so the flavor reaches the tests only through the merged manifest and
  `BuildConfig`. `app/src/automotive/` exists as the AAOS manifest overlay.
- Kover 0.9.8 is applied to `:app`, `:auto`, `:core` and at the root (aggregation, `build.gradle.kts`);
  the instrumentation agent is attached to every test JVM today (`-javaagent:…/build/kover/kover`
  observed in the worker command line of both flavors).
- Native work is incremental through ninja: the forced gate run re-merged and stripped existing `.so`
  files but recompiled nothing (newest `.ninja_log` predates the run). `app/.cxx` holds five or more
  configuration-hash trees, 7.6 GB. `-Pandroid.injected.build.abi` is supported and explicitly kept
  legal for debug iteration, forbidden for `release` (`app/build.gradle.kts` 123–133).
- `gradle.properties` is committed and carries a machine-local `org.gradle.java.home`
  (`.github/workflows/build.yml` 201–202 overrides it per runner). No `.gradle/configuration-cache`
  directory exists in the project: the configuration cache is off.
- The release version bump runs at configuration time, gated on the requested task names
  (`app/build.gradle.kts` 54–133, `nextReleaseVersion()`), which `guidelines/Design.md` §10 states as the
  contract ("one version-bumping command, gated at configuration time").
- Evidence culture already in force: `guidelines/Build.md` §4 (result evaluation), §6 (fork-budget table,
  "verify a budget by CONTENT"), §7 (coverage) and the `run-tests` / `revert-check` skills.

## Goals / Non-Goals

**Goals**

- A gate whose duration is dominated by work the change actually needs, with the duplicate flavor run
  and the forced compile/packaging pass removed from the routine path.
- Use the machine's cores for test execution without breaking the attribution of a failure to a class.
- Every gate number arrives as a measurement: per-phase and per-suite timings recorded on each run, and
  one command that turns the record into the phase breakdown the procedure quotes.
- The full gate (both flavors, every shipped ABI, coverage instrumentation attached) remains the
  completion criterion — the speed-ups apply to iteration, and completion stays as strict as today.

**Non-Goals**

- No change to what any test asserts, to the JNI stub / classloader rule, or to the teardown contract.
- No change to application behaviour, manifests, resources, or the Android Auto / AAOS surfaces.
- No new test framework, no test sharding across Gradle invocations (the single-invocation rule of
  `unit-test-suite-runtime` stands), no CI provider change.
- Not a coverage-gate change: coverage stays report-only, and this change does not add thresholds.
- No submodule patch: nothing here needs the libosmscout tree to change.

## Decisions

### D1 — The affected-flavor rule is procedural, not encoded in the build

Options: (a) express the rule as a Gradle property or in the build script so a variant filter decides;
(b) a repository script (`tools/`) that derives the affected flavor from the change and invokes Gradle;
(c) a documented rule the operator or agent applies, with the run's own record naming which flavor ran.

**Chosen: (c), plus the record from D5 as the enforcement.** Rationale: the decision input is the
*change*, which Gradle cannot see — a build-script rule would have to guess from the task graph, and a
variant filter that silently drops a suite is exactly the failure mode the spec guards against
(`build-test-gate` — "Gate runs the affected flavors"). A wrong call is visible in the timing record
(one suite or two) and is caught by the "Completion requires the full gate" scenario. Alternative (b) is
a reasonable future step but adds a git-derived heuristic that has to be maintained and tested; it is
left as an open question rather than smuggled in.

The designated single flavor for shared-source changes is **mobile**: it is the base flavor (no overlay
manifest) and the projection flavor for phones and head units driven by a phone. Alternatives —
automotive (only overlay-specific risk, narrower surface) or alternating flavors (non-deterministic
evidence) — lose on evidence quality.

### D2 — Forcing execution targets the test tasks, not the graph

Options: (a) `--rerun-tasks` (today): re-executes configuration-dependent compiles, dex, packaging and
native merging as well — measured 2 m 13 s before the first test executor; (b) delete each test task's
result directory and run with `--no-build-cache`; (c) `--rerun` on the test tasks, which
`guidelines/Build.md` §4 records as ignored by AGP's unit-test tasks; (d) a property-gated
`outputs.upToDateWhen { false }` on the unit-test tasks, with `--no-build-cache` for the invocation.

**Chosen: (d), documented with (b) as the fallback.** (d) needs no knowledge of output paths, applies to
every test task including `:osmscout-client-java:test`, and survives AGP relocating an output
directory — (b) breaks silently the moment the path changes, and the failure mode is "the suite ran" or
"the suite did not run", which is indistinguishable from a cache hit. `--no-build-cache` is required in
both: a not-up-to-date task is still allowed to restore its outputs from the cache, which is the exact
trap `guidelines/Build.md` §6 records for `forkEvery`, and the one `guidelines/Build.md` §4 records as
"a cached run is not a run".

### D3 — Coverage instrumentation becomes opt-out per invocation; the default stays on

Options: (a) default on, local iteration passes an opt-out; (b) default off, CI passes an opt-in;
(c) a separate coverage invocation that re-runs the suite with the agent attached.

**Chosen: (a)** — the proposal's assumed option. Rationale: the existing CI sequence runs the test step
and then the coverage step, and `test-coverage` requires both to keep working without a workflow change;
the archive gate wants coverage available; a default-on instrumentation agent means the only runs that
skip it are the ones that say so, and the *full* completion gate does not pass the opt-out, so the
strictest evidence still carries instrumentation. (b) forces every archive and CI invocation to
remember a flag or lose the report — the failure mode is an empty coverage report discovered at review
time. (c) doubles test execution for the report.

Consequence, named: iteration runs and the completion gate execute with different instrumentation.
Mitigated by the spec scenario "Test results are the same with and without the agent" (measured once per
module) and by the completion gate running with the agent attached.

### D4 — Fork count is declared and measured; two forks is the floor, four the candidate

Options: (a) one fork (status quo, 190 s of case work serialized); (b) `maxParallelForks = 2`;
(c) `maxParallelForks = 4`; (d) shrink the per-fork heap and keep one fork.

**Chosen: measure (b) and (c) in the same session and declare the value the measurement supports; if the
measurements do not separate them, declare 2.** Rationale: (d) was already measured as the wrong axis
(`guidelines/Build.md` §6: the 2026-09-26 canary was a test race, not a heap bound), and one fork leaves
~10 of 16 cores idle. Observed per-fork RSS was 1.8 GB at a 1024 MB heap, so concurrency is bounded by
host memory, not by the JVM: the box was at ~1 GB free with 19 GB of 36 GB swap in use during the
measured run. The measurement therefore records wall time *and* peak memory, and the declared value is
the largest one that stays inside the available memory with the failure set unchanged.

### D5 — The timing record is a Gradle init script writing JSON, plus a report task

Options: (a) parse the run log; (b) parse Gradle's `--profile` HTML report; (c) an init script
(`tools/gate-timings.init.gradle.kts`, applied with `-I`) that hooks the task graph and writes
`build/gate-timings.json`, combined with the per-suite numbers already present in the JUnit XML.

**Chosen: (c).** The log format is not a contract and `--profile` emits a timestamped HTML page per
build — both make the record unparseable by anything durable. A task-graph hook sees task name, outcome
and duration directly, and the suite numbers come from the XML the run already writes, so the record
costs no extra execution. The script is repository-owned under `tools/`, applied only for gate runs, and
must carry a self-test that runs without a device (the `tools/measure-highlight.py` precedent) because
`AGENTS.md` requires a device-free self-test for new tools.

### D6 — Native scope for iteration; prune by configuration hash; CCache only if measured

Options: (a) document single-ABI iteration and prune superseded `.cxx` trees by hand; (b) an explicit
Gradle task that prunes; (c) add a compiler launcher (CCache) in `app/src/main/cpp/CMakeLists.txt`.

**Chosen: (a) as the contract, (b) as a small documented helper with a bounded keep rule, (c) as an
evaluation adopted only with a measured saving on a forced native rebuild.** Rationale: the native build
is *not* on the routine critical path (ninja recompiles nothing when sources are unchanged), so (c) is
worth an experiment, not an assumption; `.cxx` growth (five trees, 7.6 GB) is a disk and
configuration-lookup cost that a keep-the-current-plus-previous rule bounds. Pruning must be an
explicit, idle-time step: `.cxx` is AGP-owned state, and deleting the tree of a configuration AGP still
considers current would force a full reconfigure in the middle of a build.

### D7 — Configuration cache: evaluate on iteration invocations, enable only if two hazards clear

Options: (a) leave off; (b) enable unconditionally in `gradle.properties`; (c) evaluate with
`--configuration-cache` on iteration invocations and enable for real only when the hazards clear.

**Chosen: (c).** Hazards, both already suspected in `TODO.md` §813: the license-asset tasks that use the
AGP Variant API (`generateLicenseAssets*`, `checkLicensePolicy*`), and the configuration-time release
bump — a cached configuration must not be able to replay a stale bump, and `guidelines/Design.md` §10
plus `release-target` ("builds other than the release target SHALL NOT mutate the persisted version
state") require the bump to stay correct. If the bump has to move into a task to be cache-safe, that is
a change to `Design.md` §10 and to the release task in `app/build.gradle.kts`, and it is only taken when
the evaluation shows the cache is actually reusable for the iteration graph. Otherwise the measurement
is recorded and the flag stays off — deferred with evidence, not silently assumed.

### D8 — Threading and lifecycle of what this change adds

No runtime component is added: no dispatcher, no main-thread work, no Android lifecycle callback, no
native call. Nothing here touches the application process, so `guidelines/Design.md` §4 (threading) and
§5 (native boundary) are not engaged by this change's own code; the native *boundary* is untouched — only
the scope of the native invocation changes.

The concurrency this change introduces is entirely at the test-JVM level: `maxParallelForks` makes AGP
start several test JVMs as separate OS processes under one Gradle invocation. Inside each fork JUnit
executes classes sequentially on that JVM's single test thread, Robolectric drives its main looper as
before, and each class still writes its own result XML from the fork that ran it. Forks share no mutable
state but the build directory, whose paths AGP namespaces per task. The per-JVM lifecycle is unchanged by
this: a fork exits when its shard ends, and the teardown contract of `unit-test-suite-runtime` (nothing a
test creates outlives it, a leak fails its own class) is what makes the failure set comparable between
one fork and several.

### D9 — What the specs require of a regression, and how each new invariant is falsified

`guidelines/Build.md` gains the recipe and the measurement table; `AGENTS.md`'s iteration loop gains the
reduced/full distinction. Each new invariant gets one revert-check (mutation → the named case must
fail → restore → forced green): the forcing toggle (D2), the coverage opt-out (D3), the declared fork
count (D4), the timing record (D5), and the prune (D6). The flavor rule (D1) is falsified through the
record: run a shared-source change with both flavors and the record's suite count contradicts
`build-test-gate` — "Change touches only shared sources".

### D10 — Verification, and what cannot be verified here

Verification is measurement, not inspection: two gate runs per configuration with the D5 record quoted,
failure-set equality for D4, and the per-module tallies read from the XML with their freshness checked
(`guidelines/Build.md` §4). Any build logic added to `buildSrc` (a prune helper, a report task) is
covered by `./gradlew -p buildSrc test`, which runs as part of every build.

Explicitly not applicable, and stated rather than implied: no visual or rendering requirement exists in
this change, so no screenshot or `pixel-check` measurement applies and none is claimed; no on-device,
emulator, or head-unit verification is required for the gate itself, because nothing in it changes what
the app does at runtime. The one device-touching lever — single-ABI native iteration — is verified by
building one ABI and confirming only that ABI's libraries exist, with the actual device run left to the
existing `device-check` skill when a change needs it.

## Risks / Trade-offs

- **A shared-source change hides a flavor-only defect** → the completion gate still runs both flavors
  and every shipped ABI; the affected-flavor list is explicit (manifest, resources, flavor definitions,
  `app/src/automotive/**`, native inputs), and the record names the flavor that ran.
- **Parallel forks turn a green suite red without a product defect** → the equivalence scenario is the
  acceptance gate; any class that only fails under concurrency is a test defect to fix (the repo already
  fixed one such race, `FavoritesSheetReorderComposeTest`), and the declared count reverts to 1 if the
  failure sets do not match.
- **Memory starvation makes concurrency slower, not faster** → peak memory is part of the D4
  measurement; the recipe documents the host headroom needed (emulator, IDE and the second Kotlin daemon
  are the measured consumers), and the declared count is the one that fits.
- **`--no-build-cache` costs cache stores** → used only for forced runs; the store loss is bounded to
  the tasks in that invocation and refilled by the next normal run.
- **The opt-out flags leak into a completion gate** → the completion gate is documented as running with
  defaults (instrumentation on, no forcing beyond the test tasks), and the record shows whether the
  agent was attached.
- **Pruning `.cxx` during an active build corrupts state** → prune is an idle-time step that keeps the
  current configuration and its predecessor; the helper refuses to run while a Gradle build is active.
- **Configuration cache invalidation churn** (`org.gradle.java.home` differs per machine) → only
  enabled if the evaluation shows a reusable cache locally; CI's override already makes the runner's
  cache key its own.

## Migration Plan

Order: measurement record first (it is how every later claim is checked), then the two procedure
changes, then instrumentation, then flavor scope, then parallelism, then native scope, then the
configuration-cache evaluation, then CI alignment. Each step is independently revertible — all changes
live in Gradle build scripts, the init script under `tools/`, `gradle.properties`, the workflow file and
documentation. Rollback: revert the commits; the previous gate (both flavors, `--rerun-tasks`, one fork,
agent always attached) returns unchanged, and no persisted application state is involved. The spec
deltas revert with the change; nothing in `openspec/specs/**` is rewritten by hand for the modified
capabilities (the archive step syncs the deltas).

## Open Questions

- Whether the affected-flavor rule should later be derived mechanically by a `tools/` script (D1
  alternative b). Deferrable: the rule's outcome is already recorded per run, so a script would only
  remove a judgement call, and it can be added without changing any spec.
- Whether CCache earns its place (D6 alternative c). Deferrable: the native build is off the routine
  path, so the decision waits for a forced-native-rebuild measurement and changes no requirement.
