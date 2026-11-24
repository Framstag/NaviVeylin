# Structure — the five package kinds and the declaration that holds them

Every main source file of `:app`, `:auto` and `:core` belongs to exactly one of five package kinds, and the
edges those kinds may hold are declared once, in `architecture/structure.tsv`, where a check reads them. This
document owns the rules of that structure: what each kind holds, which rule set the kinds obey, how a package
is added, how the ratchet shrinks, how one feature migrates, and what the suite cannot prove.

It assigns no kind by prose: the declaration is the only machine-readable copy of which package is which kind,
and a section that states the structure routes to it rather than restating it (`package-structure`, "The
structure declaration is the single machine-readable description"). The rationale layer, with the measurements
these rules were set from, is `guidelines/Structure.why.md`.

Measured 2026-11-14, in `architecture/structure.tsv:5`: **338 main sources over 27 source packages** — the
sources `find app/src/main/java auto/src/main/java core/src/main/java -name '*.kt' | wc -l`, the packages the
same `find` piped through `sed 's#/[^/]*$##' | sort -u | wc -l`. A row count that moves without a package moving
is a defect in the declaration, not structure that appeared.

## 1. The five kinds, and what each holds

| kind | what it holds | may import |
|---|---|---|
| `concept` | what things are made of — geometry, units, time, manoeuvre and route vocabulary | nothing of ours, and no `android.*`/`androidx.*` |
| `port` | the OS and the hardware abstracted: interfaces only, no implementation | `concept` |
| `feature/<name>` | the objects and services of one business capability: rules, not UI | `concept`, `port` |
| `platform/<name>` | everything bound to a framework or the OS for that one capability — **every composable and every ViewModel**, mirroring `feature/<name>` by name | `concept`, `port`, `feature/<name>` |
| `shell` | the only kind that assembles several features and wires the process | any |

The five kinds are packages inside the three modules that already exist. `concept` and `port` may sit in any
of them, and a package name carries its module root, so the declaration looks a package up by its full name.

- **Rule: a file belongs to exactly one of the five kinds, and its package's row says which.** A file in a
  package no row claims is a violation, and so is a package two rows claim: the kind is what every other rule
  reads, so a file with two of them has no rule set at all.

- **Rule: a composable and a ViewModel are `platform`, and hosting is `shell`.** The UI belongs to the feature
  it serves, one kind up the ladder, because the kind must stay uniform: `feature` is Android-free, and a
  composable inside it would turn that rule into a path exclusion. Measured 2026-11-14 —
  `git ls-files app/src/main/java/com/naviveylin/ui/map` is 43 files — and on 2026-11-09 exactly four of those
  assembled more than one feature (`MapCanvasScreen` 6 features, `MapCanvasScreenModalBands` 5,
  `MapCanvasScreenChromeBand` 2, `MapCanvasViewModel` 2), the other 39 touching zero or one; which is also why
  "all UI in `shell`" was rejected: `shell` would have become 142 of that day's 319 main files, 4 of which
  compose anything.

- **Rule: the kinds are packages inside `:app`, `:auto` and `:core`, never Gradle modules.** A module per kind
  would make `concept`'s purity a compile error, which is strictly stronger — and it would cost a Hilt wiring
  change per module, a build-time regression and a gate-suite change for a boundary a source-reading check
  already proves. The declaration is what a later split would move, so the choice stays reversible.

## 2. The rule set

The check is `:architecture:test`: `architecture/src/main/kotlin/com/naviveylin/architecture/StructureCheck.kt`
reads `architecture/structure.tsv` and the main sources of the three modules and returns one finding per
violation. The three rules it evaluates today are the ids at
`architecture/src/main/kotlin/com/naviveylin/architecture/StructureCheck.kt:7`, which carries — one row per package, a row naming a package the tree lacks, and the
default deny. The same module carries the one documentation case, `StructureDocumentationTest`:
`guidelines/Design.md` §1 must name this declaration and list no package, so a section that restates the
structure instead of routing to it goes red (tasks 4.1 and 6.1, `package-structure`, "The structure declaration is the
single machine-readable description"). The kind rules below are the law this structure is measured by, and each is evaluated by the same
check as the change that adds it lands (change `package-structure-rules-and-suite`); until then the tree carries
them as the measurement each cites, not as a pass.

- **Rule: `concept` is self-contained.** A `concept` file imports nothing of ours and no framework type: a
  concept holds what things are made of, so it depends on nothing. Measured 2026-11-14 —
  `./tools/look.sh '^import android\.(graphics|view|content)' --path core/src/main` reports **21 import lines in
  11 files**, among them `core/ManeuverSymbols.kt:3-6` (`Bitmap`, `Canvas`, `Paint`, `Path`). Those packages are
  therefore declared `platform`, not `concept`, and the files still sitting in a `concept` package owe the
  baseline an entry (§4) until they move.

- **Rule: `port` holds abstraction only.** A `port` file declares interfaces and types, imports `concept` and
  nothing else, and implements nothing: what a platform does for a feature is stated once, where the feature can
  see it. `core.port` is the pilot's — `architecture/structure.tsv:8`, added 2026-11-13 with `FavouritesStore`
  and `Resources`, implemented behind `app/src/main/java/com/naviveylin/data/FavoriteRepositoryFavouritesStore.kt`.

- **Rule: features are mutually disjunct.** A `feature/<name>` file imports no other feature and no `shell`; one
  feature reaching into another is what makes a feature "not a unit anyone can reason about". Measured
  2026-11-14: `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:33` imports
  `com.naviveylin.ui.route.routeLengthMeters` — a non-UI layer reaching upward for a pure function — and
  `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt:11` imports
  `com.naviveylin.ui.route.RoutePanelViewModel`. Both are baseline entries until the two features are cut apart.

- **Rule: a feature is Android-free.** A `feature/<name>` file imports no `android.*` or `androidx.*` type: the
  rules a feature holds are testable without a device, and the platform that binds them is the other half of the
  same name. A feature that needs the OS reaches a `port`.

- **Rule: a platform package mirrors a feature by name.** `platform/<name>` exists for `feature/<name>`, and a
  `platform` package serves no second feature: the name is the seam between the two halves, so a platform that
  serves two of them has no row that states which. Measured 2026-11-14: `architecture/structure.tsv` declares 12
  `platform` rows, seven of which name a feature (`about`, `addressbook`, `attribution`, `favourites`, `map`,
  `mapsource`, `navigation`) and five of which name none (`data`, `location`, `memory`, `service`, `ui.theme`) —
  the first group is the pairing the rule checks and the second is the shape it refuses once a row claims no
  feature.

- **Rule: an undeclared package fails by default.** A package added to any of the three modules and claimed by
  no row is a violation, so adding structure costs a declaration row and never a new rule. This is one of the
  three rules the check evaluates today.

- **Rule: the check fails closed on a declaration it cannot read.** A declaration that is missing, unparsable,
  empty, or names a kind outside the five is a red run and never an empty rule set: a check that reports
  "no violations" because it read nothing is the failure the suite exists to prevent. Today's reader refuses a
  line that is not the five declared columns (`StructureDeclaration.kt`, `DeclarationParseError`).

- **Rule: a violation names the file, the import, the kind and the contradicted row.** A finding a reader cannot
  act on is a finding a reader argues with: every report carries the pointer into the tree and the pointer into
  `architecture/structure.tsv`.

## 3. Adding a package — the declaration is edited, not a rule

- **Rule: adding structure is one declaration row, never a rule.** A new package is a row: `module`, `package`,
  `kind`, `feature`, `may_import`, tab-separated. The rule set is five kinds wide because it is read from the
  rows; one hand-written assertion per edge over the 27 packages the declaration declares today is 729 candidate
  edges of maintenance.

- **Rule: a declaration row states the permission its kind gives, not the imports the code has today.** `-` is
  "nothing of ours", `any` belongs to `shell` only, and the columns are stated in `architecture/structure.tsv:4`.
  A row that records today's imports would need editing by every migration and would say, of a violating file,
  that the violation is legal.

- **Rule: no document enumerates a package.** A section that states the structure names
  `architecture/structure.tsv` and `guidelines/Structure.md` and stops there: prose is what drifted from the tree
  before the declaration existed, and a second machine-readable copy has no reader.

## 4. The ratchet baseline, and how it shrinks

The tree lands green with zero file moves: the declaration describes today's packages one-to-one, and the check
reports no violation on the tree it was measured against. Every file that has not migrated is a named entry in
the baseline — a file beside the declaration in `architecture/`, which the change that adds the ratchet writes
(change `package-structure-rules-and-suite`). A half-migrated tree is legal, because the declaration describes it.

- **Rule: the tree lands green, and every unmigrated file is a named baseline entry.** Landing the target red
  would jam every change in flight; landing classification alone would leave the disjunction rule — the one that
  makes a feature a unit — unenforced indefinitely.

- **Rule: a baseline entry carries its file, its rule and the owning change.** An entry is a `(file, rule)` pair
  with the change that moves the file, so a reader can find who owes the work and what removing the entry costs.

- **Rule: a baseline entry whose file no longer violates its rule fails.** This is the only property that makes a
  baseline shrink: a list that only grows is a record of accepted debt, and nobody re-reads it.

- **Rule: the baseline is the only exemption.** A suppression annotation naming the check exempts nothing. One
  escape hatch is enumerable, names its owner and goes stale loudly; two mean a reader has to look in two places
  to know what the structure actually is.

- **Rule: a green run prints what it verified.** The declaration's row count, the classified file count, the
  outstanding exemptions and the rules evaluated — the same four numbers a change quotes when it claims the
  structure covers something. A run that prints nothing has proved nothing.

## 5. Migrating one feature

One feature per change, moved end to end. The pilot is `favourites`, chosen because no change in flight named it
(change `package-structure-as-five-kinds`, design D8; item #981 landed the move):
`FavoriteOrder`, `GroupOrder` and `StarredOrder` move to `feature/favourites`; `FavoritesSheet` and
`FavoritesViewModel` move to `platform/favourites`; `FavoriteRepository` keeps its write lock and is reached
through `port/FavouritesStore`.

The slice's one general rule stays where it is: `core.GroupOrdering`'s `orderedGroupNames` keeps its `concept` row
in `core`, because the phone's sheet and the car's screen both call it and `:auto` depends on `:core` and never on
`:app` — moving it into the feature would stop the car compiling.

- **Rule: one feature per change.** A migration is a package move over every file of one feature, so two features
  in one change make the diff unattributable and the first red gate unreadable.

- **Rule: a migration moves packages and changes no behaviour.** No rule, no threshold and no user-visible
  behaviour changes in the same diff: a move that also alters logic cannot be reverted by reverting the move.

- **Rule: a migration deletes the entries its move satisfies.** The green run's outstanding-exemption figure
  drops by exactly the number of entries removed, and a stale entry left behind fails.

- **Rule: a migration runs the three modules' suites and the structure check, forced.** `:app`, `:auto` and
  `:core` unit tests plus `:architecture:test`, with `-PforceTests --no-build-cache`, and any exclusion named. A
  cached run proves the cache, not the tree.

- **Rule: a change that edits a baselined file migrates it, or names why it does not.** The baseline names the
  file and the change that owes its move, so an ordinary change that edits that file is already inside the move
  it owes, and leaving it there is how the debt outlives the reader who knew why. The change's proposal states
  either the migration, or the reason the file stays and the entry keeps its owner. The rule is checked by review
  and not by a gate: the diff's baselined files are what
  `comm -12 <(git diff --name-only "$base"...HEAD | sort -u) <(grep -v '^#' architecture/baseline.tsv | cut -f1 | sort -u)`
  prints, and the reviewer reads each against the proposal's statement. A gate arm would need the base ref, which
  a working-tree run does not have; the review is the cheap half and it is the one that names the owner.

## 6. What the suite cannot prove

- **Rule: the suite proves the ladder between kinds, never cohesion inside a package.** It reads imports, so it
  sees that a feature imports nothing of another feature and cannot see that a package's own files belong
  together. Bytecode analysis over `:core` and `:auto` is the only way to prove the second, and it is deliberately
  not part of this suite.

- **Rule: a package boundary is a test, never a compiler error.** Kotlin has no package-private visibility and
  `internal` is module-scoped, so a package boundary enforces nothing by itself and the check is the structure. If
  a kind's boundary must be a compile error, that kind has to become a Gradle module (§1).

- **Rule: only the merge closes the exclude-task escape.** `-x :architecture:test` and `--exclude-task` skip the
  hook, and a local run can never be forced. What closes it is a required status check where the branch merges —
  the rule is enforced where the change lands, not only where it is edited.
