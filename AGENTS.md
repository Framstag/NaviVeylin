# AGENTS.md — AI Agent Context for NaviVeylin

This file helps AI coding agents understand the project structure, conventions, and constraints.

## Project Overview

NaviVeylin is an Android navigation app using libosmscout for map rendering and routing. It targets phone, foldable, and tablet form factors with a single `app` module, with Android Auto (projection) and Android Automotive OS (AAOS) support via the Car App Library (`:auto` module + `NaviVeylinCarAppService`).

## Documentation Map

This file states **facts** — what exists, where it lives, and how to run it. Rules live in the guideline
that owns them, next to the measurement that proved them. **Read the section that owns the topic, never the
whole document** (`openspec/config.yaml` states what a whole-document read costs). Sections are named here as
`§<number> "<heading text>"`; the complete section list of any file is `grep -n '^## ' guidelines/<file>.md`,
which also lists the sections this table does not route. A row that names a lettered sub-section (`§15a`, a
section a split broke up) states what those sections cost — the rule is `openspec/specs/documentation-ownership/spec.md`,
"A routing row's cost is the measurement it claims" — and `tools/check-doc-routes.sh` reports a row that names
sub-sections and prices nothing, and a row that prices a section at a figure the section's measurement does not
give.

| what you are changing | read |
|---|---|
| module layering, DI, ViewModel/state, threading, the native boundary | `Design.md` §1, §3, §4; `DesignNative.md` §5a 8.0 kB, §5b 6.5 kB (the native boundary) |
| a new screen, sheet or composable structure, or a new dependency | `DesignStack.md` §2, §9; the recipes for a dependency, a screen, a full-screen sheet, a details sheet and a native function are in `Design.recipes.md`, under its section "Adding to the stack" |
| how the map is drawn — the pipeline, bitmaps, throttling, epochs | `MapRendering.md` §1 (the index) — §1a 6.0 kB, §1b 3.1 kB, §1c 1.3 kB, §1d 10.3 kB, §1e 2.3 kB; also §2, §4, §5 |
| a frame's delivery — front-buffer emission, the overrun display window, the rotation-gesture handoff | `MapFrames.md` §12, §13, §14 |
| follow mode, GPS, bearing, rotation, auto-zoom, markers, angles | `MapFollow.md` §3, §6, §7, §8, §9, §10, §11 |
| stylesheets, day/night, basemap, POI icons and where they come from, tile-data cache | `MapStyling.md` §15, §15a, §16, §16a, §16b, §17 — sub-sections §15a 3.2 kB, §16a 2.4 kB, §16b 0.8 kB (measured 2026-10-31) |
| label word-wrapping — the bounds the renderer configures, and which backend wraps | `MapStyling.md` §20 "Label wrapping" |
| the phone map — modes, overlays, layering, the route-planning card | `UIPhoneMap.md` §7, §7a, §7b, §8, §8b, §8c, §8a, §10a, §11 — sub-sections §7a 3.4 kB, §7b 8.0 kB, §8a 1.9 kB, §8b 3.3 kB, §8c 3.5 kB (measured 2026-10-31) |
| phone surfaces — the details dialog, search, favourites | `UIPhone.md` §5, §6a, §12, §13, §14, §15, §16, §17, §18, §19 — sub-section §6a 6.1 kB (measured 2026-10-31) |
| car screens — templates, wait notices, car search and details | `UICar.md` §2, §3, §3b, §3c, §4, §6b — sub-sections §3b 2.0 kB, §3c 2.9 kB, §6b 5.2 kB (measured 2026-10-29) |
| parity between the two variants, or a deliberate deviation from it | `UI.md` §1, §6, §8d "Cross-variant map presentation" 5.5 kB (measured 2026-10-31) |
| dark mode and text/localisation, on both surfaces | `UI.md` §9, §10 |
| the car Surface, renderer startup, host-thread safety, cross-variant wiring | `DesignCrossVariant.md` §8a 10.0 kB, §8b 2.9 kB (the car host path, the surface and the observation seam), `MapCar.md` §14, `UICar.md` §3a 5.4 kB (measured 2026-10-29); specs `car-host-fault-isolation`, `auto/screen-observation`, `auto-map-renderer`, `auto-smooth-follow` |
| a surface that must live while a phone and a car session are both active | `MapCar.md` §18, `UIPhoneMap.md` §10a "Phone surface while a car session is active" 2.4 kB (measured 2026-10-29) |
| search, favourites, settings, persistence | `DesignStack.md` §9 "Data, persistence & search", `UIPhone.md` §6a "Phone search surface" 6.1 kB (measured 2026-10-29) |
| logging, diagnostics, or what a log line may contain | `Logging.md` §1, §2, §3; the on-device logcat recipes are `BuildNative.md` §10, the disclosure and the coordinate rule `Regulatory.md` §9 and `UI.md` (gates) |
| route data and the numbers derived from it | `MapRoute.md` §19 |
| build, run, force a rerun, read a gate result, or a workflow's action pin | `Build.md` §4 is an index of its sub-sections; `BuildGate.md` §2 is an index of the gate's sub-sections. Read the one that owns the question — §2a "Agent iteration protocol" (the iteration loop), §2b "Liveness guard and shell recipes", §2c "Gate timing record and baseline", §2d "The build JVM (daemon JVM criteria)", §2e "The actions' Node runtime" (a `uses:` pin and the check that gates one); in `Build.md`: §3 "Commands reference"; in `BuildEvidence.md`: §4a "The verdict, the warnings and the failures", §4b "Which flavors and suites a run must cover", §4d "Attributing a red run", §4f "Artifacts, evidence rules and native verification" — 1.4–6.0 kB per section (bytes/1000, one decimal), against 23.2 kB and 23.4 kB for the two clusters before the split (measured 2026-10-22); §4c and §4e are reached through the §4 index — |
| the iteration protocol, the liveness guard, the daemon JVM and the action runtimes | `BuildGate.md` §2 "Common behavior", §2a "Agent iteration protocol", §2b "Liveness guard and shell recipes", §2c "Gate timing record and baseline", §2d "The build JVM (daemon JVM criteria)", §2e "The actions' Node runtime" — 1.4–6.0 kB per section (bytes/1000, one decimal) |
| verdicts, evidence rules, coverage and phone-UI measurement | `BuildEvidence.md` §4 "Result evaluation", §4a "The verdict, the warnings and the failures", §4b "Which flavors and suites a run must cover", §4c "Native scope, pruned configurations and a restored mutation", §4d "Attributing a red run", §4e "Falsification, flakes and scheduling traps", §4f "Artifacts, evidence rules and native verification", §7 "Code coverage", §11 "Measuring a phone UI finding (do this before changing code)" — 1.7–4.8 kB per section (bytes/1000, one decimal) |
| test constraints — the JNI stub, teardown, the Compose sandbox, geometry, fork budgets, concurrency, wall-clock waits, red-run diagnosis, the submodule suite | `BuildTests.md` §6 "Test constraints", §6a "The JNI stub and the classloader sandbox", §6b "Teardown, the Compose sandbox and geometry claims", §6c "Queue post-conditions and declared fork budgets", §6d "Declared test-JVM concurrency", §6e "Wall-clock waits and the conversion measured", §6f "Diagnosing a red run", §6g "Instrumented runs and the submodule's suite", §6h "The JNI stub for unit tests" — 0.9–3.0 kB per section (bytes/1000, one decimal) |
| the native build — CMake sources, the ABI→triplet selection, vcpkg dependencies, stylesheet packaging | `BuildNative.md` §12 "Native build and vcpkg" — its sub-sections §12a "Architecture", §12b "Configured feature headers", §12c "vcpkg Dependencies", §12d "Rebuilding vcpkg packages", §12e "vcpkg usage pattern (CI)", §12f "Stylesheet and icon packaging" — 0.1–2.5 kB each (bytes/1000, one decimal) |
| writing or fixing a test — constraints, fork budget, coverage, the JNI stub | `BuildTests.md` §6a "The JNI stub and the classloader sandbox" (the rule a stub-loading test follows), §6b "Teardown, the Compose sandbox and geometry claims", §6c "Queue post-conditions and declared fork budgets", §6d "Declared test-JVM concurrency", §6e "Wall-clock waits and the conversion measured", §6f "Diagnosing a red run", §6g "Instrumented runs and the submodule's suite", §6h "The JNI stub for unit tests" (what the committed stub is and where it ships) — 0.9–3.0 kB each (bytes/1000, one decimal), against 22.1 kB for §6 before the split —; in `BuildEvidence.md`: §7 "Code coverage"; `DesignCrossVariant.md` §11 "Testing" |
| release, versioning, SBOM, licence inventory | `Build.md` §5, §8, §9 |
| on-device evidence — car crash triage, phone UI measurement | `BuildNative.md` §10a "Booting the AAOS AVD and driving the car UI", §10b "Judging the car surface and the render loop", §10c "Footprint, the app's host sends and route numbers", §10d "Rejected mutations, the error notice and database registration", §10e "Expectation baselines and the car search scope", §10f "Two checks that need evidence before a behaviour is specified", §10g "Start-map selection on the phone", §10h "Stylesheet load count per start", §10i "Device recipes that cost a round every time", §10j "A calculated route's length" — 0.1–2.6 kB each (bytes/1000, one decimal), against 31.6 kB for §10 before the split —, §10k "The route-row census" (the `ROUTE` tag's one line per calculated route, and the recipe that reads it for a city and a motorway route); in `BuildEvidence.md`: §11 |
| what one project metrics report run measures, its metric definitions, its excluded classes, its surface rule | `Metrics.md` §1 "Invocation and exit status", §2 "What one run measures, and what it does not", §3, §4, §5, §6 "Surface convention" |
| legal, regulatory, Play policy, privacy, diagnostics disclosure | `Regulatory.md` §2, §3, §4, §6, §9 |
| engineering principles, and the checklist before an apply pass | `Design.md` §12 "General engineering principles"; the apply checklist is `Design.recipes.md` "Appendix A — Quick checklist (apply phase)" |
| why an earlier decision was reversed | `Design.why.md` — its "Superseded decisions" section and its "Appendix B — Provenance" |
| the design document set — which of the four documents carries which section of `Design.md` | `Design.md` §1, §3, §4, §12; `DesignStack.md` §2, §9; `DesignNative.md` §5a 8.0 kB, §5b 6.5 kB; `DesignCrossVariant.md` §6, §7, §8a 10.0 kB, §8b 2.9 kB, §10, §11 |
| a rendering regression to re-check, and the parameter table | `MapFrames.why.md` "Known Pitfalls" (the regression checklist), `MapRendering.recipes.md` "Parameter Overview" |
| what is open, and what to do next | the issue tracker (`Framstag/NaviVeylin`, operated by `tools/issues/*.sh`) — `tools/issues/list.sh --status open`, plus the two saved searches (open bugs, open improvements); `--id N` resolves one migrated entry. Skills `triage-todo` (rank), `cleanup-todo` (prune), and the four tooling skills `create-issue`, `list-issues`, `update-issue`, `close-issue` |
| whether `UI.md` is still accurate | `UI.md` §20 "Keeping this document honest" |
| the feature catalogue or the release notes, or how they are generated | `FeatureList.md` §1 "What the two documents are", §3 "One capability key identifies one requirement", §4 "The classification is the only human judgement, and an unclassified spec stops the run", §6 "The prose gates, not a reviewer's memory", §8 "The version is an input" — those 5 sections are 9 268 of the document's 13 162 chars, ~2.6 k tokens against ~3.7 k for the whole document (measured 2026-10-08) |
| how the guidelines themselves are organized | `openspec/specs/documentation-ownership/spec.md` |
| the backlog itself — where an item comes from, what the tracker holds, who may write what, how it becomes work, the loop and its refusals | `Backlog.md` §1 "Status, and what this document owns", §4 "The invariants", §5 "Who may write what" |
| the tooling over the backlog — the declared checks, using it in another project, and where an agent runs | `BacklogTooling.md` §9 "Checks", §10 "Portability", §11 "Where an agent runs" |
| the loop's procedure — the run's phases, the bounds on a child, the gate and its suite, the refusals it records and the leftovers it reports | `Backlog.md` §7 (the index) — §7a 0.2 kB, §7b 0.2 kB, §7c 0.2 kB, §7d 0.2 kB, §7e 0.1 kB, the bodies in `Backlog.recipes.md` |
| a citation in an issue body that no longer resolves, or the automatic repair of one | the spec `stale-citation-resolution`, `tools/issues/resolve-stale.sh` (detect, repair, report — a path the tree does not hold is named, never refused and never parked), and the classifier both it and the pre-screen ask: `tools/issues/lib.sh` with the rules in `tools/issues/citations.json`. Skill `cleanup-todo` |
| copying the tooling into another project — what to copy, the three files to edit, the adapter to delete, the assumptions, and what an update costs | `PORTING.md`; the concept it lands is `BacklogTooling.md` §10 "Portability", and the leak check is `tools/check-portability.sh`. Skills `portability-check` (the check, its 27 cases, the synthetic foreign project, the porting recipe) and `mutation-runner` (a versioned set of shell-gate mutations in one command) |
| a concern's rationale or procedure — `guidelines/<Concern>.why.md` (why the rule exists, the measurement that proved it, the reversal, the dated provenance) or `guidelines/<Concern>.recipes.md` (the commands, sample output, device steps, number tables) | the companion document beside its base `guidelines/<Concern>.md`; it carries `## §N` anchors into the base document's sections, so read it beside the base section its anchor names. A companion is not a routed document — it is read, not routed |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin, Java, C++20 (NDK) |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Persistence | JSON files (JNI favorites, settings, search history) |
| Navigation | Jetpack Navigation Compose |
| Native | libosmscout via NDK/CMake + JNI (Cairo rendering backend) |
| Build | Gradle (Kotlin DSL), AGP 8.7+ |
| Native build | CMake 3.22+, NDK 27 |
| ABI targets | arm64-v8a, armeabi-v7a, x86_64 |
| Min SDK | 29 (androidx.car.app:app-automotive / AAOS CarAppActivity) |
| Target SDK | 36 |

## Module Structure

```
:app                  → Main app (phone, foldable, tablet) + Android Auto/Automotive OS (`NaviVeylinCarAppService`)
:auto                 → Android Auto screens + session (Car App Library)
:core                 → Shared app helpers (`com.naviveylin.core.*`)
:osmscout-client-java → Java side of the JNI bridge: local overrides over the libosmscout-client-java submodule sources
buildSrc              → Build-time logic with its own tests: license policy evaluation, shipped/build-time classification, license asset and NOTICE generation
licenses/             → Curated license data: native license map, license policy, canonical license texts (input to the build, not generated)
```

## Key Conventions

### License compliance

The application's own code is licensed under **GPL-3.0-or-later**, and the app shows exactly the inventory the
gate validated; `guidelines/Build.md` §9 owns the curated license data, the `license` task group, the SBOM
properties, the packaged `licenses/dependencies.json` and the policy gate that CI and `release` run for both
flavors.

### Code Style
- Kotlin: official style (per `gradle.properties`)
- C++: follow libosmscout conventions
- Package: `com.naviveylin.*`

### Architecture
- Single Activity (`MainActivity`), Compose-based UI
- Hilt for DI, ViewModel + StateFlow for state
- JSON-file persistence (JNI favorites, settings, search history)
- `FavoriteRepository` wraps JNI CRUD for favorites, exposes `StateFlow`
- Favorite writes are serialised on both sides and must stay that way: `osmscout::FavoriteStore`
  (`libosmscout-client`) owns `FavoriteLocationService` behind one mutex and replaces the store as a
  whole (`ReplaceByPath`, `ReplaceAndSave`), while `FavoriteRepository` holds one write lock across
  `mutate + refreshState + persist` per operation. Do not call the native favourite methods outside
  the store, and do not add a repository write that bypasses the lock — the persist rebuilds the whole
  store from the array it is handed, so an unserialised write can drop another write's favourite.
- Native calls go through the JNI bridge: C++ side = `libosmscout-client-java` inside the libosmscout submodule; Java side = `:osmscout-client-java` module overrides (see `guidelines/Design.md` §5)

### Native Integration

C++ source lives in `app/src/main/cpp/` and the JNI bridge is split across the libosmscout submodule and the
`:osmscout-client-java` module — **patch in one place, never both**; the rest of the boundary, the submodule
pin and the one-session-owns-the-push discipline are `guidelines/Design.md` §5, and CMake and vcpkg (a Cairo
backend, not OpenGL, on purpose) are `guidelines/Build.md` §12.

### Logging

Native lines reach Logcat under the tag **`NaviVeylin`**, surfaced by the app-owned NDK bridge, and **no log
or diagnostics line may carry a coordinate** — identity instead; the rules and the remaining facts are
`guidelines/Logging.md`, and the on-device logcat recipes are `guidelines/Build.md` §10.

### Stylesheets

The submodule's `stylesheets/` directory is the single source of truth for the map styles and the build copies
it into the APK; the runtime contract is `guidelines/MapStyling.md` §15, §15a, §16, §16a and §16b, and the
sync and packaging gates are `guidelines/Build.md` §12.

### Android Auto / Android Automotive OS

**Two distribution flavors, one applicationId**, and the car path's rules — one surface owner per session,
nothing native on the host thread, no fault escaping the host path, and the two that kill a process if broken
(**no screen or renderer ever calls `Surface.release()`**; **every host-facing send and template build is
guarded**) — are `guidelines/Design.md` §8, `guidelines/UICar.md` §3, §3a, §3b and §3c,
`guidelines/MapCar.md` §18, `guidelines/Build.md` §10, and the specs `android-automotive-os`, `auto`,
`car-host-fault-isolation`, `auto/screen-observation`, `auto-map-renderer` and `auto-smooth-follow`.

## Backlog maintenance

The tracker is the live backlog, and the nine skills under `.pi/skills/*` (`create-issue`, `list-issues`,
`update-issue`, `close-issue`, `issue-metadata`, `triage-todo`, `cleanup-todo`, `process-failure-log`) own it;
read `guidelines/Backlog.md` §1 "Status, and what this document owns", §3 "What the tracker holds", §4 "The
invariants", §5 "Who may write what" and §9 "Checks". An item filed **before** the migration also carries `§N`
in its title and a
`<!-- nvid: N -->` marker in its body: that is how a `§N` quoted by a guideline, a config rule or an archived
change still finds its entry after the file it came from was deleted — `tools/issues/list.sh --id N`, or the
recorded table in the migration's `import-evidence.md` (its change directory while it is open, its archive entry
afterwards). An item filed **after**
the migration has no `§N`; its identity is its issue number, and ids are never renumbered.

## Agent iteration loop (measure first)

The loop is `guidelines/BuildGate.md` §2a, "Agent iteration protocol": **look then measure before changing code;
iterate with focused suites and gate once; batch independent questions; one builder per working tree.** It
exists because those rules cost the most rounds in `ki_processing_failures.log` — rules 1-3 from a change
whose single visual symptom needed many review rounds, rule 4 from the shared-worktree collisions
(`TODO.md` §40 item 4) — so a session follows it rather than re-deriving it.

- a defect fix inside the loop owes a test that fails on HEAD *before* the fix, and an independent read-only
  reviewer before archive — that includes a defect-shaped improvement, whose red case asserts the bound it is
  missing; the loop's contract is the spec `agent-issue-loop`, the eligibility gate the worker applies is
  `tools/loop/prompts/worker.md`, and the entry is `tools/loop/run.sh` (the gate for `tools/loop/**` is
  `tools/loop-selftest.sh`, offline).

## OpenSpec Workflow

This project uses OpenSpec with the `spec-driven` schema:

```
proposal → specs → design → tasks → apply
```

Change artifacts live in `openspec/changes/<change-name>/`.
Config: `openspec/config.yaml`

Three artifact rules are CI-gated (`.github/workflows/build.yml`, step "Check OpenSpec artifact hygiene"):

- every `tasks.md` uses `- [x]` / `- [ ]` markers — bare `1. [x]` items parse as **no** tasks (the change then
  reads as `no-tasks` with no error);
- any `operations.*.guidance` list entry containing `: ` must be **quoted**. An unquoted `KEY: text` entry
  parses as a YAML mapping, the whole array fails the array-of-strings check and the CLI silently drops that
  operation's guidance behind a single warning (that hid the entire `apply` guidance until 2026-10-04).
  Run `openspec doctor` and `openspec instructions apply --change <name> --json | jq .operationGuidance`
  after editing the config.
- a capability spec's `## Purpose` is a sentence its author wrote, not the `TBD - created by archiving …`
  sentence `openspec archive` writes for a capability it creates. The check reads every `spec.md` under
  `openspec/specs/`, nested capabilities (`auto/*`, `share/*`) included, because `openspec validate <id>
  --type spec` only warns and exits 0, and a delta's `## Purpose` is read only when the capability is
  created. The placeholders of `navigation-ongoing-notification` and `render-mode-switch` had to be repaired
  by hand (2026-10-21). The requirement that owns this is
  `openspec/specs/documentation-ownership/spec.md`.

Two more rules are the validator's own, and an author meets them in the round after the artifact is written
unless they are read here first: `openspec validate <change> --strict` fails on both, while the plain command
passes the second with the severity it carries.

- a `MODIFIED` block replaces its requirement when the change archives, so it carries every scenario the
  shipped spec still has, the ones it does not change repeated verbatim — a renamed scenario is a removal plus
  an addition, which the validator reports as the shipped name omitted.
- a requirement's body stays at most 500 characters — the body, not its scenarios, which is where an example
  or an edge case belongs — and the limit is read on an `ADDED` block before it lands (the validator's
  `Requirement text is very long (>500 characters)`) and on the shipped spec after archive, a `MODIFIED`
  block exempt because its text is the existing requirement kept whole.

Run `openspec validate <change> --strict` — or `openspec validate --changes --strict` over the open set — before
the artifact is finished rather than at archive time. Both rules and their rationale are owned by
`openspec/specs/documentation-ownership/spec.md`, "The validator's own strict rules are read before the first
write".

## Build & Test

Everything executable — the command table (build, assemble, force a rerun, coverage), the gate's result
evaluation and evidence rules, the test constraints, the on-device recipes, and the native build with vcpkg
— is `guidelines/Build.md` (§2a the iteration protocol, §3 commands, §4a results, §6a–§6h test constraints, §10a–§10j and
§11 on-device measurement, §12 native build and vcpkg). The skills `build-app`, `run-tests`, `revert-check`
and `release-build` wrap those calls; `revert-check` is the falsification step a change owes for every new
invariant (mutate it once, the named case must fail, restore, forced green — `TODO.md` §113). The recipes for
adding a dependency, a screen, a sheet or a native function are `guidelines/Design.md` §2.

Verification follows the changed files, not the habit of running everything (`guidelines/Build.md` §2, its
scope block): a delta that touches no compiled file — docs, `openspec/**`, `.pi/**`, `guidelines/**`, the
backlog — runs no build and no suite, and names the structural check it ran instead; a test-only delta runs
the changed cases through the module's `--tests` filter; a code delta runs the gate. Name any exclusion.

### Release versioning

Two things are worth knowing before `./gradlew release` (`guidelines/Build.md` §5 owns the rest):

- the version state lives in `app/release-version.properties`, which is **gitignored and machine-local** —
  never release from a fresh clone, and never from a state whose `lastDate` is in the future (`Build.md` §5
  states what `release` refuses and why Play rejects a repeated `versionCode`);
- it produces one AAB per Play track: `:app:bundleMobileRelease` (phone + Android Auto) and
  `:app:bundleAutomotiveRelease` (AAOS).

## Constraints

- The three platform constraints this project keeps — Google Play Services is optional and never a hard
  dependency (a provider abstraction, a runtime availability check and a fallback keep it replaceable), no
  Google Maps SDK (rendering is libosmscout), no Google account required — are stated as the architecture
  rule in `guidelines/Design.md` §2. Nothing is added here.
- Distribution: the app is sideloadable (automotive AAB on head units, mobile AAB on phones), and
  `./gradlew release` also produces the two AABs for Google Play, one per track — `guidelines/Build.md` §5.
