# Tasks

This archive is the slice of `package-structure-as-five-kinds` that landed on 2026-11-14: tasks 1.1–1.4
and the falsification 3.4. A second slice of the same plan landed behind it on 2026-11-15, carried by
the tracker item #868 and recorded in this directory's `evidence/`: the `concept` and `port` layer rules
(1.5), the baseline half of 1.7, the green-run figures and the empty-declaration refusal (1.8), the
exemption rule (1.10), and the falsifications 3.1 and 3.5. The rest of the plan — the feature rules, the
`platform` and `shell` rules of 1.7, the suite's place in the gate, the documents that own the structure,
and the pilot migration — is carried by the open working change `openspec/changes/package-structure-as-five-kinds/`,
whose `tasks.md` holds the scenarios and the tasks that did not land here.

## 1. The architecture module and its inputs

- [x] 1.1 Add `include(":architecture")` to `settings.gradle.kts` and a `kotlin("jvm")` module at `architecture/` whose only dependencies are `com.lemonappdev:konsist` and `junit:junit`, with no Android plugin and no dependency on `:app`, `:auto` or `:core`. Parent spec: `architecture-test-suite` — The structure rules run as a unit test. Verify: `./gradlew :architecture:dependencies --configuration testRuntimeClasspath` lists konsist and junit and no Android artifact, and `:architecture:compileTestKotlin` succeeds. Evidence: `evidence/architecture-module-dependencies.log`.

- [x] 1.2 Write `architecture/structure.tsv` as a 1:1 declaration over today's packages (module, package, kind, feature, may_import), classifying each of the 27 current packages into one of the five kinds as they stand — no file moves. Parent spec: `package-structure` — A source file belongs to exactly one kind. Verify: the row count equals the package count reported by `find app/src/main/java auto/src/main/java core/src/main/java \( -name '*.kt' -o -name '*.java' \) | sed 's#/[^/]*$##' | sort -u | wc -l` — 27 packages, 27 rows, 338 sources (`evidence/declaration-figures.log`) — and the file records the date of its measurement.

- [x] 1.3 Implement the declaration reader and kind resolution in `:architecture`, including the case that a row names a package the tree no longer has. Parent spec: `package-structure` — The structure declaration is the single machine-readable description. Case `structureCheckFailsOnRowWithoutPackage` fails first on a row for `com.naviveylin.gone` and passes once the row is removed. Evidence: `evidence/green-declaration-cases.xml` — `tests="4" skipped="0" failures="0" errors="0" timestamp="2026-11-14T21:08:02.722Z"`.

- [x] 1.4 Add the default-deny, the duplicate-row case and the unclassified-file case. Parent spec: `package-structure` — An undeclared package fails by default. Cases: `defaultDenyFailsForANewPackage` (a temporary package with one file fails, passes after a row claims it), `structureCheckFailsOnDuplicateRow`, `structureCheckFailsOnUndeclaredPackage`. Evidence: `evidence/green-declaration-cases.xml` — `tests="4" skipped="0" failures="0" errors="0" timestamp="2026-11-14T21:08:02.722Z"`.

## 3. Falsification — one mutation per rule

- [x] 3.4 Revert-check the default-deny: delete one declaration row, confirm the package's files are reported, restore, forced green. Parent spec: `package-structure` — An undeclared package fails by default. Evidence: `evidence/revert-check-red.xml` — `tests="4" skipped="0" failures="4" errors="0" timestamp="2026-11-14T21:06:44.419Z"`, the `core com.naviveylin.core.search` row deleted, 27 → 26 rows; and `evidence/revert-check-green.xml` — `tests="4" skipped="0" failures="0" errors="0" timestamp="2026-11-14T21:07:00.958Z"`, restored and forced.
