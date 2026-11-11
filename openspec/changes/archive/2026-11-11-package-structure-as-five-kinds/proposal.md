# Proposal

## Why

Every business feature of this app is cut across the module cake: `search` exists as 17 files in
`core/search`, 4 in `data`, and its dialog in `ui/map`; `navigation` as 18 files in `navigation`, 8 in
`core`, 4 in `ui/navigation`; `route` as 9 in `ui/route`, 2 in `core`, 1 in `navigation`. A feature is
therefore not a unit anyone can reason about, test, or reuse, and nothing in the build can see it.

The measurement that makes this actionable — `tools/project-metrics-report.sh app auto core` plus an
import-graph pass over the same tree, on 2026-11-09:

| measured | figure |
|---|---|
| `:auto` main sources | 79 files, **one** package, zero intra-package imports |
| `:core` main sources | 92 files, **one** package holds 59 of them |
| coupling absorbed by `core.core` | **491 of 839** import sites — 58.5% |
| dependency traffic terminating in `:core` | **79%** of all sites |
| `app.data` | `Ca` 85, `Ce` 53 — both a widely used library and a client of the layer below |
| `:core` symbols `:app` imports but `:auto` does not | **24 of 68** — not cross-variant concepts at all |
| files that actually assemble several features | **4** (`MapCanvasScreen` 6, `MapCanvasScreenModalBands` 5, `MapCanvasScreenChromeBand` 2, `MapCanvasViewModel` 2) |

Nothing enforces any of this. `app/navigation/NavigationEngine.kt:32` imports
`com.naviveylin.ui.route.routeLengthMeters` — a non-UI layer reaching upward into UI for a pure
function — and no check notices. `guidelines/Design.md` §1 states that `:core` is "shared, Android-lean
logic"; 11 of its files import `android.graphics`, `android.view` or `android.content`, among them
`ManeuverSymbols`, which draws with `Bitmap`/`Canvas`/`Paint`/`Path`. A described structure with no
executable check decays, and this one has.

## What Changes

- Five package kinds replace the technical cake, as packages inside the existing `:app`, `:auto` and
  `:core` modules — no new production Gradle module:
  `concept/` (what things are made of), `port/` (the OS/hardware abstracted, interfaces only),
  `feature/<name>/` (objects + services per business capability, Android-free and mutually disjunct),
  `platform/<name>/` (everything bound to a framework or the OS, **including every composable and
  ViewModel**), `shell/` (the only kind that assembles several features and wires the process).
- **BREAKING (internal only):** every main source file of `:app`, `:auto` and `:core` moves to a new
  package path, so every `import` line naming `com.naviveylin.*` changes. No public API, no manifest
  entry, no resource, no user-visible behaviour changes.
- A machine-readable structure declaration (`architecture/structure.tsv`) is added: one row per
  package, naming its kind, its feature and what it may import. It is the single machine-readable copy
  of the structure; `guidelines/Design.md` §1 routes to it instead of restating it.
- A **Konsist** architecture test suite is added, enforcing six rules: three layer edges, the
  feature-disjunction rule, the `platform/<n>` to `feature/<n>` mirror rule, and a default-deny that
  fails when a package no declaration row claims. Konsist is chosen over ArchUnit because ArchUnit
  reads compiled bytecode and cannot see `com.android.application` (`:app`), could not read the test
  sources, and would force the suite into `:app`'s Robolectric test JVM.
- A **ratchet** makes the suite green on day one: the declaration describes today's packages, and every
  file that has not migrated yet is a named baseline entry carrying the change that moves it. A baseline
  entry whose file has already moved fails, so the baseline can only shrink.
- `~10 port interfaces` are added (settings, contacts, favourites, http, notification, location, voice,
  surface, resources, clock) — `:feature` cannot be Android-free while features hold a `Context`, and
  `app/data` today has **no interfaces at all**.
- One pilot feature (`favourites`, 8 files, no in-flight change) is migrated end to end, proving the
  rules and the ratchet on a real slice.

## Capabilities

### New Capabilities

- `package-structure`: the five kinds, what each may contain, the structure declaration as the single
  machine-readable description, and the six dependency rules that hold between the kinds — including
  feature disjunction and the default-deny on an undeclared package.
- `architecture-test-suite`: the test-time enforcement of those rules — where the suite runs, which
  source sets it reads, the ratchet baseline and its shrink-only property, and the rule that a
  violation is reported with the file, the import and the declaration row it contradicts.

### Modified Capabilities

None. `openspec/specs/project-metrics-report/spec.md` already classifies a file by
`<module>/src/<set>/{java,kotlin}` layout; this change introduces a different notion of layout and does
not alter that requirement. A later change may point the metrics report at the declaration so its
unclassified bucket becomes exact — named here as a follow-up, deliberately out of scope.

## Impact

**Modules.** `:app` (149 main Kotlin files), `:auto` (78), `:core` (92). No change to the dependency
graph between them (`:app` → `:auto`, `:core`; `:auto` → `:core`), to `:osmscout-client-java`, to
`buildSrc`, or to any manifest or resource.

**New build inputs.** Konsist (`com.lemonappdev:konsist`, test scope only) and a suite to run it. Whether
that suite is a new `kotlin("jvm")` module or a test source set inside an existing module is decided in
`design.md`; the recommendation there is a `:architecture` JVM module so it does not inherit Robolectric
4.16 from `:app`'s test JVM, which is the subject of the open change `fix-aggregate-run-test-flakes`.
No version catalog exists in this project, so the dependency is declared the way the project's other test
dependencies are.

**Guidelines.** `guidelines/Design.md` §1 (its "`:core` = shared, Android-lean logic" rule is superseded
and routes to the declaration), `AGENTS.md` (the module-layering row of the Documentation Map and the
module-structure block), `guidelines/BuildEvidence.md` §4b (the new suite joins the set a gate run must
cover) and `guidelines/Metrics.md` (the report's layer rows and the declaration). `guidelines/UI.md` §8d
gains the parity-package symmetry rule that `platform/phone` and `platform/car` make checkable.

**Specs.** No existing capability's requirements change. Two are created.

**Native/JNI.** Not applicable — no C++ file, no JNI signature and no submodule patch is touched; the
`:osmscout-client-java` bridge module is unchanged.

**Scope.** General to all three modules and both variants. It is a restructuring change, not a feature:
nothing a user or the car host does changes.

**Risk — in-flight work.** 26 changes are open. Every one that edits a moved file conflicts. The scope
above is deliberately sequenced against that: the suite and the declaration land first with **zero file
moves**, so they conflict with nothing; the pilot migration touches one small feature that no open change
names; the remaining features migrate as separate follow-up changes, each one its own slice.

**Rollback.** The declaration and the suite are additive — deleting the suite and its dependency restores
the previous build exactly. Each migration step is a pure package move with no behaviour change, so it
reverts independently. A half-migrated tree is legal, because the declaration describes it and the
baseline names every file still in its old home.

surface: 34 scenarios across 2 delta files, 1 new guideline document plus its rationale companion, 1 new
Gradle module and 1 declaration file, 8 pilot files moved — `grep -c '^#### Scenario:' openspec/changes/package-structure-as-five-kinds/specs/*/spec.md`
