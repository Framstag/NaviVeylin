# Change report — `migrate-todo-to-github-issues`

The source of truth moved from `TODO.md` to the tracker, the file is gone, and every reference to a
migrated item still resolves. This report is the run's evidence; the tables of `§N → #number` are in
`import-evidence.md`, the falsification transcript in `revert-checks.txt`, the scenario map in
`traceability.md`.

## 1. What landed

| thing | measurement |
|---|---|
| the import | 137 created, 0 already present, exit 0, `2026-10-17T09:02:18Z → 09:16:35Z` |
| the tombstones | 21 created, exit 0, `09:17:26Z → 09:19:48Z` |
| the tracker | 158 issues, **all open, 0 closed**; 36 taxonomy labels + GitHub's 9 defaults, nothing else |
| the imported set | 137 open, exactly one `class/*`, `category/*`, `status/*` each (0 violations), `source/todo-import` on all 137, `agent/eligible` on none |
| idempotence | a second `--apply` → exit 4, `0 preview(s), 137 already present, 0 created` |
| every `§N` resolves | the per-section loop over `list.sh --id N` printed nothing (verbatim, task 3.5) |
| the record | `TODO.md` deleted; `git checkout HEAD -- TODO.md` restores it |

New files: `tools/check-todo-references.sh`, `tools/check-todo-references-selftest.sh`, the
`Check backlog references` CI step, `list.sh --id N` plus two cases and the `nvid` fixture, and the
migration's own `import-evidence.md`, `tombstones.sh`/`tombstones.json`, `traceability.md`,
`revert-checks.sh`/`revert-checks.txt`, `report.md`.

## 2. What the run measured that the plan did not know

The plan was written from a census taken on 2026-10-16. Five measurements contradicted it, and each was
resolved in the run rather than worked around:

1. **136 sections, not 137.** The recorded dry run already read 137, so 3.1's diff is empty against the
   *evidence*, not against the plan's prose. `proposal.md` and `design.md` were corrected in place.
2. **87 live citations named 23 ids whose entry no longer existed** (an earlier cleanup had closed and
   removed them, and the citations stayed as provenance), and four more named an id that never carried a
   section at all (`§140`, `§80a`, `§813`, `§125`). The check's second half is absolute, so these had to
   resolve before the file could go. **User decision (asked in the run): tombstone the removed ids, redirect
   the four, keep the check strict** — no citation allowlist. Group 9 records the work; 64 of the 87 sat in
   comments inside `app/`, `core/` and `buildSrc/`, which is why the plan's "no compiled file is edited"
   scope guard could not survive, and why the scoped Gradle gate below was run.
3. **The plan's census missed five instructing sites**: `guidelines/FeatureList.md:79`, `fix-loop`'s
   `REFERENCE.md` and `scripts/evidence-check.sh` (its C1 resolved `§N` against the file) and
   `scripts/loop-state.sh` (`--allow-todo-removal` → `--allow-closure`), plus the tooling's own cases that
   read the file.
4. **Task 6.3's literal command cannot pass**: `grep -rln 'TODO.md'` lists every file that *mentions* the
   record, and most of them are allowed to — a `§`-bearing citation is provenance. The verification is the
   check's own rule (below), not the file list.
5. **Task 8.2's exclusion cannot hold**: task 1.4 adds a step to `.github/workflows/build.yml`, and the
   citation repairs touch compiled files. Both tasks were amended in place, with the measurement quoted.

Two defects were found by the run's own verification and fixed:

- **The check mangled `§N` in a non-UTF-8 locale.** `env -i PATH="$PATH"` (task 1.4's own invocation) made
  awk read `§` byte-wise, so `RSTART + 1` landed inside the character: the extracted "number" was
  `<byte>100`, which resolved against nothing (202 citations "unresolved"). Fixed by stripping non-digits
  from the extraction; the check is green in the default locale, under `LC_ALL=C` and under `env -i`.
- **The plan's `4.5` claim about the renames**: they are applied from the profile's `sourceRenames` by
  `core/Taxonomy.java`, so they appear in `tools/tooling.json` (4 occurrences) and *not* in the adapter. The
  adapter's own comment said otherwise and was corrected.

## 3. Verification

**The chain (task 8.1), in order, every step exit 0:**

| # | step | result |
|---|---|---|
| 1 | `bash tools/check-todo-references-selftest.sh` | 10 passed, 0 failed |
| 2 | `bash tools/issue-tooling-selftest.sh` | all 33 cases passed (JDK 27, offline, no token) |
| 3 | `bash tools/check-todo-references.sh` **with the file present** (before 6.1) | exit 0, 81 citations resolve, 0 instructions survived |
| 4 | `bash tools/check-todo-references.sh` **with the file absent** (after 6.1) | exit 0, 62 citations resolve, 0 instructions survived |
| 5 | `env -i PATH="$PATH" bash tools/check-todo-references.sh` (no token) | exit 0 |
| 6 | `LC_ALL=C bash tools/check-todo-references.sh` | exit 0 |
| 7 | `bash tools/check-todo-references.sh --api` (cross-read against the tracker) | exit 0, 62 citations resolve through the table *and* the tracker |
| 8 | `openspec doctor` / the CI hygiene scan over `config.yaml` / `bash tools/check-doc-routes.sh` | exit 0 / 0 unquoted entries / all 87 references resolve |

**The reference check, mechanically.** With the file absent: 2041 files excluded by the printed allowlist,
62 distinct citations, 194 scanned lines, 0 instructions survived. With no token the table is the record;
with `--api` every name is taken from the tracker itself.

**Falsification (`revert-checks.txt`).** One mutation per invariant, restored after each; the named case goes
red, then green, and the three mutated files are byte-identical afterwards:

| invariant | mutation | the case that went red |
|---|---|---|
| instruction detection | the check accepts a mention without a citation | `refcheck#instructionLineFails` |
| the citation half | the check resolves every number whatever the record says | `refcheck#unresolvedCitationFails` |
| the `nvid` lookup | `list.sh` matches a substring instead of the marker | `list#idDoesNotMatchASubstring` |
| the allowlist | `guidelines/` excluded, so an instruction there is invisible | `refcheck#instructionLineFails` |
| the rewiring's completeness | the `AGENTS.md` map row names the record with no citation | `tools/check-todo-references.sh` (exit 1, naming `AGENTS.md`) |
| the import assertion | `agent/eligible: 0` relaxed to always pass | the strict form separates a labelled fixture from the relaxed one (1 vs 0) |

**The compiled delta, gated once (task 9.7).** The delta's edits inside `app/`, `core/` and `buildSrc/` are
comments and KDoc only; four test files were touched the same way, and one androidTest file's change is a
comment. So:

```
./gradlew :core:compileDebugKotlin :app:compileMobileDebugKotlin :app:compileMobileDebugAndroidTestKotlin
  → BUILD SUCCESSFUL in 36s
./gradlew :app:testMobileDebugUnitTest -PforceTests --tests <the four affected classes>
  → BUILD SUCCESSFUL in 23s
  → the XML: MapCanvasViewModelModeTest 9, RoutePanelStoppedStateTest 4, NavigationEngineTest 18,
    MapDownloadServiceRepositoryLifecycleTest 7 — 38 tests, 0 failures, 0 errors, 0 skipped
```

**Exclusions, named:**

- **The full suite is not run.** No behaviour changed anywhere in the delta — every compiled-file edit is
  inside a comment or a KDoc block — and the affected classes were run instead (`guidelines/Build.md` §2,
  the scope block). The report says so rather than letting the reader assume a gate.
- **No device and no pixel measurement.** Nothing UI-visible changed; the one androidTest file's edit is a
  comment, so it is compiled, not executed (`RouteInstructionPositionDeviceTest`, which needs a device).
- **`agent-issue-loop` must not start until this change is archived**: both change `AGENTS.md`.
- **The `.pi/skills/**` rewiring is verified mechanically**: for each of the twelve skills, zero lines name
  the deleted record without a citation; over all of `fix-loop`, zero.

## 4. Files this change edits

Tracked: `AGENTS.md` · `openspec/config.yaml` · `guidelines/Build.md` · `guidelines/UI.md` ·
`guidelines/FeatureList.md` · `guidelines/Backlog.md` · `.github/workflows/build.yml` ·
`tools/feature-list/specs.json` · `tools/prune-native-configs.sh` ·
`app/src/androidTest/.../RouteInstructionPositionDeviceTest.kt` ·
`app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` ·
`app/src/test/java/com/naviveylin/navigation/NavigationEngineTest.kt` ·
`app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelModeTest.kt` ·
`app/src/test/java/com/naviveylin/ui/mapmanager/MapDownloadServiceRepositoryLifecycleTest.kt` ·
`app/src/test/java/com/naviveylin/ui/route/RoutePanelStoppedStateTest.kt` ·
`app/src/test/java/com/naviveylin/ui/route/RouteStepValuesTest.kt` ·
`buildSrc/src/main/kotlin/com/naviveylin/build/i18n/HardcodedStringScanner.kt` ·
`core/src/main/java/com/naviveylin/core/LogLineCoordinates.kt` · the seven skills that actually instructed
(`triage-todo`, `cleanup-todo`, `process-failure-log`, `fix-loop` with `REFERENCE.md`,
`scripts/evidence-check.sh` and `scripts/loop-state.sh`, `openspec-proof-gap-audit`,
`openspec-open-changes`, `run-tests` — the other five of the twelve named the record only in `§`-bearing
citations, so they were left byte-identical, which is task 5.5's rule) ·
`tools/issues/list.sh` · `tools/issues/core/Cases.java` · `tools/issues/import/todo-md.sh` ·
`tools/issues/fixtures/{todo-status-shapes,todo-renames}.md` · `tools/issues/fixtures/scenarios/nvid/` ·
`tools/issue-tooling-cases.sh` · and the deletion of `TODO.md`.

Not edited, because every occurrence in them is a `§`-bearing citation: `guidelines/Design.md`,
`MapRendering.md`, `Logging.md`, `Regulatory.md`, `app/build.gradle.kts`, `tools/measure-highlight.py`,
`tools/check-compose-sandbox-rule.sh`.

Not touched at all: the submodule and the JNI bridge (no native change), the Android manifests, and — by
design — `openspec/changes/archive/**` (292 archived artifacts keep their text) and the four provenance
specs.

## 5. Rollback

`git checkout HEAD -- TODO.md` restores the record (verified: `git show HEAD:TODO.md | head -3` prints its
header), and the rewired references revert with the rest of the diff. **The imported and tombstoned issues
cannot be un-created** — the tooling has no delete path — so a mistaken import is corrected by closing the
wrong issues with a comment naming the correction. That asymmetry is why the import was verified against the
recorded dry run before the file was touched.

The change is field-only: nothing the app does changes, and neither distribution flavour changes.
