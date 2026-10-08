# AGENTS.md — AI Agent Context for NaviVeylin

This file helps AI coding agents understand the project structure, conventions, and constraints.

## Project Overview

NaviVeylin is an Android navigation app using libosmscout for map rendering and routing. It targets phone, foldable, and tablet form factors with a single `app` module, with Android Auto (projection) and Android Automotive OS (AAOS) support via the Car App Library (`:auto` module + `NaviVeylinCarAppService`).

## Documentation Map

This file states **facts** — what exists, where it lives, and how to run it. Rules live in the guideline
that owns them, next to the measurement that proved them. **Read the section that owns the topic, never the
whole document** (`openspec/config.yaml` states what a whole-document read costs). Sections are named here as
`§<number> "<heading text>"`; the complete section list of any file is `grep -n '^## ' guidelines/<file>.md`,
which also lists the sections this table does not route.

| what you are changing | read |
|---|---|
| module layering, DI, ViewModel/state, threading, the native boundary | `Design.md` §1, §3, §4, §5 |
| a new screen, sheet or composable structure, or a new dependency | `Design.md` §2 — its "Adding to the stack" subsection holds the recipes for a dependency, a screen, a full-screen sheet, a details sheet and a native function |
| how the map is drawn — pipeline, bitmaps, throttling, epochs, front buffer, overrun | `MapRendering.md` §1, §2, §4, §5, §12, §13 |
| follow mode, GPS, bearing, rotation, auto-zoom, markers, angles | `MapRendering.md` §3, §6, §7, §8, §9, §10, §11 |
| stylesheets, day/night, basemap, POI icons and where they come from, tile-data cache | `MapRendering.md` §15, §15a, §16, §16a, §16b, §17 |
| phone UI — modes, overlays, layering, controls, dark mode, text | `UI.md` §7, §7a, §8, §8a, §9, §10, §11 |
| car screens — templates, wait notices, car search and details | `UI.md` §2, §3, §3b, §3c, §4, §6b |
| parity between the two variants, or a deliberate deviation from it | `UI.md` §1, §6 |
| the car Surface, renderer startup, host-thread safety, cross-variant wiring | `Design.md` §8 "Android Auto & cross-variant", `MapRendering.md` §14 "Android Auto renderer", `UI.md` §3a; specs `car-host-fault-isolation`, `auto/screen-observation`, `auto-map-renderer`, `auto-smooth-follow` |
| a surface that must live while a phone and a car session are both active | `MapRendering.md` §18 "The phone canvas while a car session is active", `UI.md` §10a "Phone surface while a car session is active" |
| search, favourites, settings, persistence | `Design.md` §9 "Data, persistence & search", `UI.md` §6a "Phone search surface" |
| logging, diagnostics, or what a log line may contain | `Logging.md` §1, §2, §3; the on-device logcat recipes are `Build.md` §10, the disclosure and the coordinate rule `Regulatory.md` §9 and `UI.md` (gates) |
| route data and the numbers derived from it | `MapRendering.md` §19 |
| build, run, force a rerun, read a gate result | `Build.md` §2 — its "Agent iteration protocol" subsection is the iteration loop —, §3, §4 |
| the native build — CMake sources, the ABI→triplet selection, vcpkg dependencies, stylesheet packaging | `Build.md` §12 |
| writing or fixing a test — constraints, fork budget, coverage, the JNI stub | `Build.md` §6 "Test constraints", §7 "Code coverage", `Design.md` §11 "Testing" |
| release, versioning, SBOM, licence inventory | `Build.md` §5, §8, §9 |
| on-device evidence — car crash triage, phone UI measurement | `Build.md` §10, §11 |
| legal, regulatory, Play policy, privacy, diagnostics disclosure | `Regulatory.md` §2, §3, §4, §6, §9 |
| engineering principles, and the checklist before an apply pass | `Design.md` §12, "Appendix A — Quick checklist (apply phase)" |
| why an earlier decision was reversed | `Design.md` §13, "Appendix B — Provenance" |
| a rendering regression to re-check, and the parameter table | `MapRendering.md` "Known Pitfalls", "Parameter Overview" |
| what is open, and what to do next | `TODO.md` — skills `triage-todo` (rank), `cleanup-todo` (prune) |
| whether `UI.md` is still accurate | `UI.md` "Keeping this document honest" |
| how the guidelines themselves are organized | `openspec/specs/documentation-ownership/spec.md` |

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

- The application's own code is licensed under **GPL-3.0-or-later** (`LICENSE`,
  `SPDX-License-Identifier: GPL-3.0-or-later`), and first-party components resolve to that SPDX identifier;
  `guidelines/Build.md` §9 owns the curated inputs (`licenses/native-license-map.json`,
  `licenses/license-policy.json`), the `license` task group, the SBOM's `naviveylin:license:*` properties and
  the policy gate that CI and `release` run for both flavors.
- The app shows exactly the inventory the gate validated, read from `licenses/dependencies.json`, which the
  build packages into its own assets — it keeps no list of its own.

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

- C++ source lives in `app/src/main/cpp/`, and the JNI bridge is split across the libosmscout submodule and
  the `:osmscout-client-java` module — **patch in one place, never both**.
- Everything else about the boundary — both sides and the five overridden files, the two database-open
  entries and why they differ, the submodule pin and clean-tree rules, the one-session-owns-the-push
  discipline — is `guidelines/Design.md` §5. CMake and vcpkg are `guidelines/Build.md` §12, including where a
  local vcpkg lives (`$VCPKG_ROOT` or `./vcpkg`) and that the SDK, NDK and Java paths come from
  `local.properties`. The Cairo rendering backend is not OpenGL on purpose: better map-rendering quality.

### Logging

- Two facts to start with: native lines reach Logcat under the tag **`NaviVeylin`**, surfaced by the
  app-owned NDK bridge (`native_log_bridge`, `NativeLogBridge`), and **no log or diagnostics line may carry a
  coordinate** — identity instead (object, database or file name, magnification, pixel, accuracy, bearing),
  enforced by the `checkNoCoordinatesInLogs` build gate.
- The rules and the remaining facts — `osmscout::log` and the Android-free rule for libosmscout, the level
  mapping, the condensed stylesheet-type report, per-class `TAG`, `DiagnosticsLog` with its retention, the
  disclosure statement — are `guidelines/Logging.md`. Inspect with `adb logcat -s NaviVeylin`; the on-device
  recipes are `guidelines/Build.md` §10.

### Stylesheets

The submodule's `stylesheets/` directory is the single source of truth for the map styles, and the raster
icon leaf ships with it; the build copies both into the APK and `AssetCopier` refreshes the on-device copy on
every app start. The runtime contract — the basemap's dedicated stylesheet, the day/night pair, the icon name
resolution and its trailing separator, the load-failure rule — is `guidelines/MapRendering.md` §15, §15a,
§16, §16a and §16b; the sync and check tasks with their packaging gates are `guidelines/Build.md` §12.

### Android Auto / Android Automotive OS

What this file keeps, with the rest owned by the specs:

- **Two distribution flavors, one applicationId.** The AAOS manifest is a flavor overlay
  (`app/src/automotive/AndroidManifest.xml`) that re-declares `android.hardware.type.automotive` with
  `required="true"`, while the main manifest strips the feature the `app-automotive` AAR merges in and removes
  the projection metadata from the automotive build. The Play constraint that forces this, the
  `tools:node="remove"` mechanics, the permission set and the two-track upload are spec
  `android-automotive-os`; the projection declarations are spec `auto`
  (`openspec/specs/auto/spec.md`, `openspec/specs/android-automotive-os/spec.md`).
- **The car path's rules and invariants** — one surface owner per session, nothing native on the host
  thread, no fault escaping the host path, the guarded seam for host mutations, observations scoped to a
  screen's started period, and the `HOST`/`SESSION`/`SCREEN`/`WARMUP`/`MEMORY` diagnosis tags — are
  `guidelines/Design.md` §8, `guidelines/UI.md` §3, §3a, §3b and §3c, `guidelines/Build.md` §10, and the specs
  `car-host-fault-isolation`, `auto/screen-observation`, `auto-map-renderer` and `auto-smooth-follow`. The
  two that kill a process if broken, in one line each: **no screen or renderer ever calls
  `Surface.release()`** (the session releases it once), and **every host-facing send and template build is
  guarded** (the library rethrows on the main thread).

## Backlog maintenance

`TODO.md` is the live backlog: numbered `## <id>.` sections plus the feature-table groups. Every entry
carries `**id:** … · **category:** … · **class:** bug|improvement|feature · **status:** …` on the line
under its heading; ids are identity and are never renumbered (commit messages, change tasks and sessions
quote `§N`). Three skills own the file:

| Skill | Job |
|---|---|
| `triage-todo` | read-only — rank what is open and name the next change |
| `cleanup-todo` | remove entries already implemented on master / in an archived change / in a merged PR, repair the metadata, optionally cluster |
| `process-failure-log` | turn `ki_processing_failures.log` entries into guardrails in `guidelines/*`, CI or a skill |

## Agent iteration loop (measure first)

The loop is `guidelines/Build.md` §2, "Agent iteration protocol": **look then measure before changing code;
iterate with focused suites and gate once; batch independent questions; one builder per working tree.** It
exists because those rules cost the most rounds in `ki_processing_failures.log` — rules 1-3 from a change
whose single visual symptom needed many review rounds, rule 4 from the shared-worktree collisions
(`TODO.md` §40 item 4) — so a session follows it rather than re-deriving it.

What this file owns about the loop is only where its parts live:

- measuring a pixel symptom: `guidelines/Build.md` §10 and §11, the `pixel-check` skill, and the
  coordinate-free diagnostics line (spec `auto-diagnostics`);
- the iteration levers and the measurements behind them: `guidelines/Build.md` §4, §6, §7;
- the device loop, an emulator with maps, and a Compose geometry assertion: the `device-check`,
  `provision-phone-emulator` and `compose-geometry` skills;
- the build, test and falsification skills: `build-app`, `run-tests`, `revert-check`, and the wrapper and
  peer-edit probes in `guidelines/Build.md` §2.

The hand-written skills under `.pi/skills/` are versioned with the project; the skills the OpenSpec CLI
generates, and the rest of `.pi/` (session logs, captures, instruction dumps, extension config), are not — the
split, and what a new skill needs, is `guidelines/Build.md` §1 "Skills". A rule that must survive a fresh
clone or another agent belongs in **this file**, in `openspec/config.yaml` or in `guidelines/*.md`; the
*tools* a skill uses belong in the repo (`tools/measure-highlight.py`, with a self-test that runs without a
device).

Screenshot reading needs one harness tool the repository cannot ship: **`view_image`**, a Pi package that
returns an image content block so a text-only model sees the pixels — and whose install, `PI_VIEW_IMAGE_BIN`
override and **pinning/filtering constraint** (it bundles a `@luan.sh/pi-libtui` host that rewrites the TUI,
so it stays version-pinned with a one-entry `"extensions": ["./src/extension.ts"]` filter) are
`guidelines/Build.md` §1, "Agent harness and the TUI package". Without the package the workflow degrades to
the measurement script alone, which answers *where* but not *what*.

## OpenSpec Workflow

This project uses OpenSpec with the `spec-driven` schema:

```
proposal → specs → design → tasks → apply
```

Change artifacts live in `openspec/changes/<change-name>/`.
Config: `openspec/config.yaml`

Two artifact rules are CI-gated (`.github/workflows/build.yml`, step "Check OpenSpec artifact hygiene"):

- every `tasks.md` uses `- [x]` / `- [ ]` markers — bare `1. [x]` items parse as **no** tasks (the change then
  reads as `no-tasks` with no error);
- any `operations.*.guidance` list entry containing `: ` must be **quoted**. An unquoted `KEY: text` entry
  parses as a YAML mapping, the whole array fails the array-of-strings check and the CLI silently drops that
  operation's guidance behind a single warning (that hid the entire `apply` guidance until 2026-10-04).
  Run `openspec doctor` and `openspec instructions apply --change <name> --json | jq .operationGuidance`
  after editing the config.

## Build & Test

Everything executable — the command table (build, assemble, force a rerun, coverage), the gate's result
evaluation and evidence rules, the test constraints, the on-device recipes, and the native build with vcpkg
— is `guidelines/Build.md` (§2 the iteration protocol, §3 commands, §4 results, §6 test constraints, §10 and
§11 on-device measurement, §12 native build and vcpkg). The skills `build-app`, `run-tests`, `revert-check`
and `release-build` wrap those calls; `revert-check` is the falsification step a change owes for every new
invariant (mutate it once, the named case must fail, restore, forced green — `TODO.md` §113). The recipes for
adding a dependency, a screen, a sheet or a native function are `guidelines/Design.md` §2.

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
