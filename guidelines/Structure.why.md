# Structure — the rationale

This is the rationale layer of `guidelines/Structure.md`. An anchor here names a section number the base
document carries, so the rule and the measurement that set it are read together. Every figure below states the
date it was taken and the command that took it; a figure without both is an estimate, and an estimate is what
this document exists to replace.

## §1 Why the axis is the business feature, and not the technical layer

Every business feature of this app was cut across the module cake, and the measurement below is why the five
kinds replace it. Taken 2026-11-09 with `tools/project-metrics-report.sh app auto core` plus an import-graph pass
over the same tree — 319 main files then, 338 on 2026-11-14 per `architecture/structure.tsv:5`:

| measured | figure |
|---|---|
| `:auto` main sources | 78 files, **one** package, zero intra-package imports |
| `:core` main sources | 92 files, **one** package holds 59 of them |
| coupling absorbed by `core.core` | **491 of 839** import sites — 58.5 % |
| dependency traffic terminating in `:core` | **79 %** of all sites |
| `app.data` | a widely used library (`Ca` 85) **and** a client of the layer below (`Ce` 53) |
| `:core` symbols `:app` imports and `:auto` does not | **24 of 68** — not cross-variant concepts at all |
| files that assemble several features | **4** of `ui/map`'s 43 |

The dependency graph itself was never chaotic: sorted by Martin's instability it is close to monotonic, a
well-formed two-tier cake (`core.*` at `I` ≤ 0.03, `app.ui.map` at 0.98, `auto.auto` at 0.99). What was broken is
that each feature was sliced across both tiers, so no feature was a unit — `search` 17 files in `core/search`,
4 in `data` and its dialog in `ui/map`; `navigation` 18 in `navigation`, 8 in `core`, 4 in `ui/navigation`;
`route` 9 in `ui/route`, 2 in `core`, 1 in `navigation`.

Two measured shapes made the cake self-contradicting rather than merely wide. The `map` ↔ `route` cycle:
`app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt:89` imports `com.naviveylin.ui.map.RowAddressCache` while
six files of `ui/map` import `ui/route`, and the two features cannot be described as a ladder in either
direction. And the upward reach: `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:33` imports
`com.naviveylin.ui.route.routeLengthMeters`, a non-UI layer taking a pure function from UI, which no rule noticed.

Alternatives and their measured cost. *Keep the cake and only enforce it*: enforcement would codify
`core/data/ui` as the law, and the measurement above says the law is why a feature has no home. *A Gradle module
per feature*: 11 modules, Hilt wiring across module boundaries, a build-time regression and a gate-suite change —
rejected on cost, not principle. *A Gradle module per kind*: the same cost, and `concept`/`port` purity would
become a compile error, which is strictly better than a test; deferred, and named in `Structure.md` §1 as the one
place where a module may earn itself later.

Why a composable is `platform` and not `feature`: 39 of `ui/map`'s 43 files touch zero or one other feature, so
the UI belongs to the feature it serves; putting it in the feature itself would make `feature` no longer
uniformly Android-free, and putting all UI in `shell` would make `shell` 142 of 319 main files of which 4
compose anything.

## §2 Why the rules are read from a declaration, and why ArchUnit is not the tool

Three rule sources instead of many: a layer block for the five kinds (N lines cover N² edges), one generic
assertion iterating the declaration's rows, and one default deny for a package no row claims. One hand-written
assertion per edge would be 625 candidate edges over 25 packages — the maintenance this change exists to avoid.
Deriving the kind from the package path was refused because it puts the structure back in code, where a rename
changes it silently; a prose declaration was refused because prose is what had already drifted — 11 files of the
`concept` package importing the framework while `guidelines/Design.md` §1 called `:core` "shared, Android-lean
logic" (superseded; `guidelines/Design.why.md` §13), measured 2026-11-14 with `./tools/look.sh '^import
android\.(graphics|view|content)' --path core/src/main`
(21 import lines in 11 files).

*ArchUnit eliminated, on three specific impossibilities.* It reads compiled bytecode, so (i) `:app` is
`com.android.application` and cannot be handed its classes from any other module — the suite would have to live in
`:app/src/test`, inheriting Robolectric 4.16 and the Compose sandbox rule from the very JVM the flake history is
about; (ii) it cannot read the test source sets at all; (iii) it cannot see a package that holds no class the
suite is given, which is exactly the default-deny case. Konsist reads source text, so all three are covered by one
reader. `:architecture` finds the project root from `settings.gradle.kts`, so the module needs no dependency on
`:app`, `:auto` or `:core` — that is the whole point of a source-reading tool.

*TSV, not YAML or JSON.* A tab-separated table diffs per row in review, and the reader stays in the tool that
already parses the tree; JSON would need a parser the build does not otherwise carry on the source-reading side,
and YAML would invite a schema no one reads.

## §3 Why adding a package is a row and not a rule

The declaration is a second copy of the description, and that is its only cost. It is mitigated by making it the
**only** machine-readable copy: `guidelines/Design.md` §1 routes to it and enumerates no package, and no other
document states a placement. A row states the permission its kind gives (`-` = nothing of ours, `any` = `shell`
only) rather than a census of today's imports, because a census row would need editing by every migration and
would declare a violating file legal — the declaration's comment lines say so where the rows are read.

## §4 Why a ratchet, and what makes it shrink

The suite may not be red on landing: 26 changes were in flight when this was designed, and a red check jams every
one of them. So the declaration describes today's packages one-to-one, with zero file moves, and every file that
has not migrated is an entry naming its `(file, rule)` pair and the change that moves it.

Three properties are what make that a ratchet rather than a record of debt. An entry goes stale the moment its
file stops violating its rule, and a stale entry fails — so the list cannot outlive the debt. The baseline is the
only exemption: Konsist can be silenced per declaration in source, and that is refused and checked, because two
escape hatches mean a reader has to look in two places to know what the structure is. And a green run prints the
four numbers it verified, so a claim about coverage is read from a run instead of estimated.

## §5 Why one feature per change, and why `favourites` was the pilot

A migration moves every file of one feature, so two features in one diff make the first red gate unattributable.
`favourites` was chosen on three measurements: 8 files across `ui/favorites` (5), `core` (2) and `data` (1); no
change in flight names it; and it already carries the write-serialisation rule the `port` seam must preserve —
`app/src/main/java/com/naviveylin/data/FavoriteRepository.kt` behind
`app/src/main/java/com/naviveylin/data/FavoriteRepositoryFavouritesStore.kt`. The risk was checked before the move:
`fix-poi-symbol-icons` is open and touches search row icons, which the favourites slice could reach, and the slice
would have been abandoned for a smaller one on a collision.

## §6 Why the check hangs off the module test tasks

"No code passes the tests while violating the structure" is true only if the check runs in the invocation that
runs the tests. The suite therefore depends from the `Test` task of every module, so the focused run a change
actually uses — `./gradlew :app:testMobileDebugUnitTest --tests "…"` — fails at the structure check before the
test reports a pass. The alternatives fail on the same point: the gate alone lets a contributor's focused run and
the agent iteration loop both pass, and the violation surfaces at the closing gate, which is the review-late
failure this suite exists to remove; hooking `check` or `assemble` misses it, because `test` does not imply
`check`.

Two loopholes are named rather than hidden. `-x :architecture:test` and `--exclude-task` skip the hook, and a
local run can never be forced — a required status check at merge is what closes that. And the check can never be a
compile error: Kotlin has no package-private visibility, so a package boundary can only ever be a test; making it
a compiler error means making the kind a Gradle module (§1).
