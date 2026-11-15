# Design

## Context

Measured on 2026-11-09 with `tools/project-metrics-report.sh app auto core` and an import-graph pass over
the same tree: `:auto` holds its 78 main sources in one package with zero intra-package imports, `:core`
holds 92 with 59 of them in `core.core`, and that one package absorbs 491 of the 839 import sites in the
project (58.5%), while 79% of all dependency traffic terminates somewhere in `:core`. `app.data` is the
only package that is simultaneously a widely used library (`Ca` 85) and a client of the layer below
(`Ce` 53). Both variants import 44 of the 68 `:core` symbols `:app` uses; the other 24 are `:app`-only.

The dependency graph is not chaotic — sorted by Martin's instability it is close to monotonic, a
well-formed two-tier cake (`core.*` at `I` ≤ 0.03, `app.ui.map` at 0.98, `auto.auto` at 0.99). What is
broken is that **every business feature is sliced across both tiers**, so no feature is a unit.

Constraints that shape the approach:

- Kotlin has no package-private visibility and `internal` is module-scoped, so a package boundary
  enforces nothing by itself. The check is the structure.
- `:app` is `com.android.application`, so it can be neither a test dependency nor a compile dependency of
  another module.
- 26 OpenSpec changes are in flight, so the structure may not be red on landing.
- Both variants ship from one `applicationId`; the phone and the car host are different presentation
  contracts and the guidelines are split the same way (`UIPhoneMap.md`, `UICar.md`, `MapCar.md`).
- No version catalog exists; test dependencies are declared as literal coordinates in each
  `build.gradle.kts`.

## Goals / Non-Goals

**Goals:**

- One main source file belongs to exactly one of five kinds, and the edges between kinds are declared once
  and checked by a red test.
- A feature becomes a unit that can be named, tested and reused without reaching into another feature.
- The check is green on the day it lands, and its exemptions can only shrink.
- `guidelines/Design.md` §1 stops restating the structure and routes to the declaration.

**Non-Goals:**

- No user-visible, manifest, resource or native change; no JNI signature changes.
- No Gradle module per kind. The kinds are packages inside `:app`, `:auto`, `:core`.
- No `architecture/structure.tsv` reader in `tools/project-metrics-report.sh`. Its unclassified bucket
  becomes exact only if a later change points it at the declaration.
- No measurement of intra-package cohesion — see "What this suite cannot prove".

## Decisions

### D1 — The axis is the business feature, not the technical layer

`concept / port / feature/<name> / platform/<name> / shell`. A feature holds objects and services; a
platform package holds everything bound to a framework for that feature, composables included; `shell`
holds what assembles and starts.

*Alternatives.* (a) Keep the technical cake and only add enforcement — rejected: the enforcement would
codify `core/data/ui` as the law, and the measurement shows the law is the reason a feature has no home.
(b) A Gradle module per feature — rejected on cost, not principle: 11 feature modules plus Hilt wiring
across modules, a build-time regression and a gate-suite change, for a boundary Konsist already proves.
(c) A Gradle module per kind — same cost, and `concept`/`port` purity would be a compile error, which is
strictly better than a test; deferred, and named in D3 as the one place where a single module may earn
itself later.

*Risk.* A wrong feature list is expensive to undo once 319 files moved. Mitigated by migrating one
feature per change (D8) and by the pilot.

### D2 — Konsist enforces the rules, not ArchUnit

Konsist reads **source files**; ArchUnit reads **compiled bytecode**.

*Alternative eliminated — ArchUnit.* Three specific impossibilities: (i) `:app` is
`com.android.application`, so ArchUnit cannot be handed its classes from any other module, and the suite
would have to live in `:app/src/test`, inheriting Robolectric 4.16 — the test JVM that the open change
`fix-aggregate-run-test-flakes` is currently repairing; (ii) it would see `:auto`'s AAR classes but never
its sources, and never any test source, where 127 files of `:app` alone sit; (iii) it needs a compile, so
the check cannot be a pre-flight. ArchUnit also reads Kotlin top-level functions as synthetic `FooKt`
classes, and this codebase is full of them (`ManeuverLabels`, `RouteRows`, `ProjectionUtils`,
`DistanceFormat`).

*Alternatives eliminated — detekt and Android Lint.* detekt's `ForbiddenImport` is configurable YAML with
no test integration, so it fails at `check` rather than as a named test, and it is a fifth tool in a build
that has none. An Android Lint detector needs the Detector API and a build; it cannot see test sources.

*Alternative eliminated — extending the `tools/check-*.sh` family.* That is this repository's established
channel (10 checks, e.g. `check-compose-sandbox-rule.sh`) and it would have cost nothing new. It was
rejected because the requested loop is test-time, and because a bash check cannot express "this package
imports that kind" without growing into a worse parser than Konsist.

*Trade-off, stated honestly.* Konsist cannot see a reference to a sibling in the same package — a
same-package reference emits no import at all. ArchUnit's bytecode view can. So the one thing the chosen
tool cannot measure is exactly the one the structure is meant to maximise.

### D3 — The suite runs in a `kotlin("jvm")` module of its own

`include(":architecture")` in `settings.gradle.kts`, with `com.lemonappdev:konsist` and `junit:junit` as
its only dependencies, no Android plugin.

*Alternatives.* (a) `:app/src/test` — inherits Robolectric, the Compose sandbox rule and the flake
history; rejected. (b) `:core/src/test` — `:core`'s classpath does not reach `:auto`, and putting an
architecture check inside the module it judges is a conflict of interest; rejected. (c) a `buildSrc`
test — `buildSrc` is a build-logic module with its own suite for licence evaluation and does not read
production sources; rejected.

*Cost.* A new line in the gate's suite list (`guidelines/BuildEvidence.md` §4b), a CI job step, and one
more module to configure. Konsist finds the project root from `settings.gradle.kts`, so the module needs
no dependency on `:app`, `:auto` or `:core` — that is the whole point of choosing a source-reading tool.

*Future.* If `concept` must be provably Android-free by the compiler rather than by a test, it can be
lifted into its own `kotlin("jvm")` module without changing a single rule; the declaration row is what
moves.

### D4 — Rules come from a declaration, not from hand-written assertions

Three rule sources: a layer block for the five kinds (N lines cover N² edges), one generic assertion that
iterates `architecture/structure.tsv` rows, and one default-deny for a package no row claims.

*Alternatives.* (a) One hand-written assertion per edge — 25 packages produce 625 candidate edges; it is
the maintenance the change exists to avoid. (b) No declaration; derive the kinds from package name
conventions — rejected because it puts the structure back in code, where a rename changes it silently.
(c) A declaration in prose in `guidelines/Design.md` — rejected, it is not machine-readable and prose is
what already drifted.

*Trade-off.* The declaration is a second copy of the description. Mitigated by making it the **only**
machine-readable copy: §1 routes to it, and no guideline enumerates packages.

### D5 — A composable is `platform`, not `feature` and not `shell`

Measured: only four files assemble several features (`MapCanvasScreen` 6, `MapCanvasScreenModalBands` 5,
`MapCanvasScreenChromeBand` 2, `MapCanvasViewModel` 2); the other 39 files of `ui/map` touch zero or one
other feature. So the UI belongs to the feature it serves, and hosting belongs to `shell`.

*Alternatives.* (a) All UI in `shell` — measured cost: `shell` becomes 142 of 319 main files (45%) of
which 4 compose, and the word stops meaning anything. (b) UI inside the feature (`feature/<n>/ui`) — one
hop from logic to UI, but `feature` stops being uniformly Android-free, which turns the simplest rule in
the set into a path exclusion. `platform/<n>` mirrors `feature/<n>` by name, keeps both kinds uniform, and
keeps the purity rule a plain package predicate.

*Threading.* A composable runs on the main dispatcher and a ViewModel on `viewModelScope` — unchanged by a
move. The rules constrain package names only, never dispatchers; `guidelines/Design.md` §4 stays the owner
of threading.

### D6 — The ratchet: green on landing, shrink-only

The declaration describes today's packages one-to-one, so the suite passes with zero file moves. Every
file that has not migrated is a baseline entry naming the change that moves it; an entry whose file no
longer violates its rule fails.

*Alternatives.* (a) Land the target red — jams all 26 in-flight changes. (b) Classify only, no edge rules
— leaves the disjunction rule, which is the one that makes a feature a unit, unenforced indefinitely.

*Mechanism detail.* The baseline is an allow-list of `(file, rule)` pairs plus an owning change. Shrink is
enforced by the stale-entry case, which is the only property that keeps a baseline from becoming a record
of accepted debt.

### D7 — A TSV declaration, read by the suite

`architecture/structure.tsv`, tab-separated, one row per package: `module`, `package`, `kind`, `feature`,
`may_import`. Chosen over YAML to keep the reader in the tool that already parses the tree with
`awk`/shell, and because a tab-separated table diffs per row in review.

*Alternative.* Deriving the kind from the package path — eliminated in D4. *Alternative.* JSON — needs a
parser the build does not otherwise have on the source-reading side.

### D8 — Enforcement first, then one feature per change

This change lands the module, the declaration (1:1 with today's packages), the rules and the baseline —
**zero file moves** — plus one pilot feature end to end. The pilot is `favourites`: 8 files across
`ui/favorites` (5), `core` (2) and `data` (1), and no open change names it. Every other feature migrates
in its own later change.

*Alternatives.* Big-bang — conflicts with 26 open changes and makes the first gate run unattributable.
Pilot-free — leaves the rules unproven against a real slice.

*Risk.* `fix-poi-symbol-icons` is open and touches search row icons, which the favourites slice may reach;
checked before the move, and the slice is abandoned for a smaller one if it collides.

### D9 — The hook is the module test tasks, and there is exactly one escape hatch

"No code passes the tests while violating the structure" is only true if the check runs in the invocation
that runs the tests. The suite therefore depends from the `Test` task of every module, so
`./gradlew :app:testMobileDebugUnitTest --tests "…"` — the focused run a change actually uses — fails at the
structure check before the test reports a pass. `:architecture:test` reads files and needs no other module
compiled, so the cost is about a second and it can start beside the build.

*Alternatives.* (a) The gate alone — rejected: a contributor's focused run and the agent iteration loop both
pass, and the violation surfaces only at the closing gate, which is the review-late failure this change
exists to remove. (b) Hook `check` or `assemble` — rejected: `test` does not imply `check`, and hooking the
`build` would charge every assemble.

*One escape hatch, auditable.* Konsist can be silenced per declaration in source. That is refused and
checked, so the baseline file is the only exemption path: it is enumerable, it names an owning change, and
a stale entry fails. Two escape hatches would mean two places a reader has to look to know what the
structure actually is.

*Residual loopholes, named rather than hidden.* `-x :architecture:test` or `--exclude-task` can skip the
dependency, and a local run can never be forced. What closes that is the CI required status check — the
rule is enforced where the branch merges, not only where it is edited. A compile-time guarantee is not
available: Kotlin has no package-private visibility, so a package boundary can only ever be a test. If a
kind's boundary must be a compiler error, it has to become a Gradle module (D1, D3).

## Risks / Trade-offs

- **Conflict with in-flight work.** Mitigated by zero-move landing plus one small pilot; every later
  migration is its own change and each of those must be sequenced against the backlog.
- **What this suite cannot prove.** An import graph cannot see intra-package references, so Konsist proves
  the ladder between kinds, never cohesion inside a package. Bytecode analysis over `:core` and `:auto`
  is the only way to prove the second, and it is deliberately not part of this change. The suite must not
  be described as proving cohesion.
- **The declaration and the tree can drift** in the one way the default-deny misses: a row that names a
  package that no longer exists is caught, a row whose `may_import` is looser than the code needs is not.
  Accepted; a looser row is not a defect, a missing row is.
- **A new Gradle module** adds a configuration surface and a gate step; if CI does not run it the suite is
  decorative. The gate-list update is therefore a task, not a note.
- **The pilot may show the rules are wrong.** If `favourites` cannot be made disjunct and Android-free
  with the ports this change adds, the kind boundary is wrong and the design returns to D1 rather than
  widening the rules.

## Verification

- **The suite, falsified.** For each rule, one mutation that must fail it, restored afterwards: move a
  concept file's import to a feature (rule 1/3), add an `android.graphics` import to a `feature` file
  (purity), add a cross-feature import (disjunction), delete a row from the declaration (default-deny),
  delete a file named by a baseline entry (stale exemption). Each mutation's failure message must name the
  file, the import and the rule — the `revert-check` discipline and its evidence format.
- **The green run's figures.** Declaration rows, classified files, outstanding exemptions, rules
  evaluated; recorded in the change's tasks with their numbers.
- **The pilot's outcome.** `favourites` migrated, the disjunction and purity rules pass for it without an
  exemption, and the baseline lost 8 entries.
- **The hook, falsified.** With one rule violation in the tree, run one test class of `:app` by name — the
  invocation must fail at `:architecture:test` and the class must not report a pass; restore and re-run
  green. Then delete the declaration and confirm the check fails closed rather than reporting no violations.
- **No device verification.** No composition, rendering, GPS or car-host behaviour changes, so no logcat
  line, no UI-dump geometry and no `pixel-check` verdict is owed. Named explicitly so the absence is a
  decision rather than an omission.
- **The gate.** One full both-flavor run naming `:architecture` among the suites it covered.

## Open decisions

none
