# Tasks

This change is spec-owned by `tooling-portability`: `specs/tooling-portability/spec.md` defines it, so each task names the
**design decision** it lands, and its verification names the case that exercises it; the traceability task in this change maps every scenario to its case. Every task carries its own verification.

Scope guard: this change edits shell, JSON, markdown and one CI step — no Gradle source set, no compiled
file, no manifest, no native source. **No Gradle build and no Gradle suite is run or required**
(`guidelines/Build.md` §2, the scope block); the gate is `tools/check-portability-selftest.sh`. No device
or pixel measurement applies: no UI changes and nothing is read on a screen — say so in the change report.

Ordering: all five preceding changes must be applied first — this change proves *their* engines and
changes none of them. If one of the five is not yet applied, the fixture simply runs fewer tools, and the
task says which were skipped.

## 1. The portability check (D1)

- [x] 1.1 Write `tools/check-portability.sh`: read `tools/tooling.json`'s `repo`, `allowedAuthors` and every namespace value set, plus each per-tool config's own values, then fail when any of those literals appears in an engine file — printing the value, the file and the line, and printing the engine set and the excluded set it used. Verify: case `portability#plantedRepoNameFails` with `Framstag/NaviVeylin` inserted into `tools/issues/create.sh` exits 1 naming the file and line; case `portability#plantedCategoryFails` does the same for `car` (D1).
- [x] 1.2 Case `portability#excludesTheAdapterAndFixtures` — the same planted value in `tools/issues/import/todo-md.sh` or in a fixture is **not** reported, and the run prints those exclusions so they are visible rather than silent (D1).
- [x] 1.3 Case `portability#valueSetIsReadNotHardCoded` — add a new category to `tools/tooling.json`, plant it in an engine, and assert the check reports it; then remove it from the profile and assert the check no longer reports the same literal. This is the case that proves the check cannot drift into a stale ban list (D1).
- [x] 1.4 Case `portability#passesOnTheRealTree` — the check exits 0 on the repository as it stands, and its printed engine set names every engine of the five changes (D1).
- [x] 1.5 Write `tools/check-portability-selftest.sh` proving each case can go red, following the `tools/*-selftest.sh` convention, creating its throwaway repository under the gitignored `.pi/tmp/`. Verify: it exits 0 on the fixtures and non-zero when one fixture is made benign.
- [x] 1.6 Add one `Check portability` step to `.github/workflows/build.yml`, with no token and no network. Verify: the command runs green locally with `env -i PATH="$PATH" bash tools/check-portability.sh` and the step grants no permissions beyond `contents: read` (D5).

## 2. Discovery, asserted where it fails (D3)

- [x] 2.1 Case `discovery#fromASubdirectory` — run `tools/issues/list.sh` with the working directory set to a subdirectory of the throwaway repository and assert the profile and repo resolve to that repository's (D3).
- [x] 2.2 Case `discovery#fromAWorktree` — create a git worktree of the throwaway repository, give the worktree its **own** profile with a different repo value, run a tool inside it, and assert the worktree's profile was used and no main-tree path was read. This is the case the loop's `.pi/worktrees/loop` depends on (D3).
- [x] 2.3 Case `discovery#ignoresGithubRepositoryEnv` — set `GITHUB_REPOSITORY` to a wrong value and assert the resolved repo still comes from the tree, with `--repo` remaining the only override (D3).
- [x] 2.4 Case `discovery#outsideAGitTree` — run a tool in a directory that is not a repository and assert a clear message and exit 2, with no default profile invented (D3).

## 3. The foreign project (D2)

- [x] 3.1 Write `tools/fixtures/foreign-project/tools/tooling.json` with a repository (`acme/widget`), an allowlist (`ada`), a category vocabulary (`core`, `docs`, `infra`) and a band set (`p0`–`p2`, TTL 30) that **all differ** from this project's. Verify: case `foreign#profileDiffersFromOurs` asserts none of its repo, allowlist entries or category values appears in this project's profile, so a passing fixture cannot be passing by accidental similarity (D2).
- [x] 3.2 Write `tools/fixtures/foreign-project/findings.json` (three findings, one a deliberate duplicate of another), its `tools/sensors/sensors.json` (one row whose command is a fixture script, threshold 1), its `tools/loop/loop.conf` (`LOOP_AGENT_CMD` at the stub `pi`, **`LOOP_GATE_CMD` empty**) and its `README.md` stating what it models and what it does not. Verify: `jq -e .` succeeds on each JSON file and the `README.md` lists at least the three unmodelled differences (D2).
- [x] 3.3 Write the foreign-project `gh` fixture responses so the stub `gh` answers for `acme/widget` without any of this repository's names. Verify: case `foreign#noProjectNameInTheStub` asserts `grep -rn 'Framstag\\|NaviVeylin' tools/fixtures/foreign-project/` prints nothing (D2).
- [x] 3.4 Implement the fixture harness in the self-test: copy the engines into a throwaway repository under `.pi/tmp/`, overlay the foreign-project directory, and run the toolchain there. Verify: case `foreign#toolchainRunsEndToEnd` executes create → list → update → close → reopen → one sensor row → one metadata pass → a `--dry-run` loop and asserts every one exited 0 (D2).
- [x] 3.5 Case `foreign#duplicateIsRefused` — the duplicate finding among the three is refused with exit 4 and the existing issue number, so the dedup works against a vocabulary it was not written for (D2).
- [x] 3.6 Case `foreign#emptyGateCommandStillRunsThePhases` — with `LOOP_GATE_CMD` empty, the dry-run loop completes its phases and its report says the gate was skipped, proving the phases call the project's gate rather than owning one (D2).

## 4. `PORTING.md` (D4, D6)

- [x] 4.1 Write `PORTING.md`: what to copy, the three files to edit, what to delete (the `TODO.md` adapter), the prerequisites (`git`, an authenticated `gh`, `bash`, `jq`, a JDK), the assumption list (GitHub Issues, the namespace *names* as the contract, the label taxonomy), what an upstream update costs, and one command per step. Verify: every heading a reader needs is present, and the document names the three config files and the adapter (D4, D6).
- [x] 4.2 Extract and **run** every command in `PORTING.md` from the self-test against the foreign-project fixture. Verify: case `porting#everyCommandInTheRecipeRuns` executes each extracted command and fails on the first non-zero exit, so the recipe cannot rot (D4).
- [x] 4.3 Case `porting#recipeDoesNotAssumeOurBuild` — assert the recipe's steps contain no `./gradlew`, no `NaviVeylin` and no path outside the copied tree, so a project with no Gradle follows it unchanged (D6).
- [x] 4.4 State the copy cost in `PORTING.md`: an upstream engine fix is a re-copy, a local engine edit means owning the merge, and extraction to a shared repository is deferred with the profile seam as the reason it stays cheap. Verify: the document contains all three statements (D6).
- [x] 4.5 Add one line to `AGENTS.md` naming `PORTING.md` as where copying the tooling to another project is described. Verify: the line exists, the path resolves, and `tools/check-doc-routes.sh` stays green (D4).

## 5. Falsification (revert-checks)

One mutation each, restored after; the named case must fail, then the suite must be green again. The gate
is `tools/check-portability-selftest.sh`, not Gradle — name the exit codes in the report.

- [x] 5.1 Value set: hard-code the category list inside `check-portability.sh`. `portability#valueSetIsReadNotHardCoded` must fail. Restore; self-test exits 0 (D1).
- [x] 5.2 Engine set: add `tools/issues/import/todo-md.sh` to the checked set. `portability#excludesTheAdapterAndFixtures` must fail. Restore; self-test exits 0 (D1).
- [x] 5.3 Discovery: resolve the profile from `$HOME` instead of the git root. `discovery#fromASubdirectory` and `discovery#fromAWorktree` must fail. Restore; self-test exits 0 (D3).
- [x] 5.4 Environment: let `GITHUB_REPOSITORY` override the tree. `discovery#ignoresGithubRepositoryEnv` must fail. Restore; self-test exits 0 (D3).
- [x] 5.5 Fixture likeness: change the foreign profile's repo to this project's. `foreign#profileDiffersFromOurs` must fail. Restore; self-test exits 0 (D2).
- [x] 5.6 Gate ownership: make the loop's worker prompt run a hard-coded gate command. `foreign#emptyGateCommandStillRunsThePhases` must fail. Restore; self-test exits 0 (D2).
- [x] 5.7 Recipe: break one command in `PORTING.md`. `porting#everyCommandInTheRecipeRuns` must fail. Restore; self-test exits 0 (D4).

## 6. Integration

- [x] 6.1 Run the full self-test on a clean checkout with all six changes applied and record its output with the timestamp and the exit code as this change's gate evidence. Verify: every case prints `PASS` and the run exits 0.
- [x] 6.2 Run `tools/check-portability.sh` on the real tree and record that it is green **and** which engines it checked, so the proof names its own scope. Verify: the printed engine set covers `tools/issues/**`, `tools/sensors/**` and `tools/loop/**`, and the excluded set names the adapter (D1).
- [x] 6.3 Record the portability result of each of the five preceding changes: for each, the engine files it contributed and whether the check passes over them. Verify: the report names all five and no engine is unaccounted for (`github-issue-tooling` D11, `migrate-…` D14, `agent-issue-loop` D16, `sensor-…` D12, `issue-metadata-loop` D2).
- [x] 6.4 Confirm the exclusions explicitly in the change report: no Gradle build and no suite was run; no engine file was changed by this change; no second repository, token or network call is involved; and the fixture lives under `tools/`, not in `.pi/`, so it is versioned. Verify: `git diff --name-only` lists no file under `app/`, `auto/`, `core/`, `osmscout-client-java/`, `buildSrc/`, and no engine file (`guidelines/Build.md` §2, scope block).

- [x] 6.5 Classify `tooling-portability` in `tools/feature-list/specs.json` as the capability-classification gate requires, since CI fails on a shipped spec with no line there (the gate is `tools/gen-feature-list.sh --check-classification`). Verify: adding the line makes `bash tools/gen-feature-list.sh --check-classification` exit 0, and removing it makes the same command fail naming `tooling-portability`, so the line is load-bearing rather than decorative.
- [x] 6.6 Add the scenario-to-task traceability table for `tooling-portability` and verify it is complete: one row per scenario naming the case that exercises it and the task that owns that case, with no scenario unmapped and no task claiming a scenario it does not cover. Verify: the row count equals `grep -c "^#### Scenario:" specs/tooling-portability/spec.md` and the change report says so.
- [x] 6.7 Keep `guidelines/Backlog.md` true for this change, and verify the routing table resolves: correct every section this change makes stale — its row in §1, and the §12 rows for the decisions it lands — then run `bash tools/check-doc-routes.sh` and `bash tools/check-doc-routes-selftest.sh` and assert both exit 0. Verify: both exit 0, the §1 row for tooling-portability is marked landed rather than planned, and no §12 row it owns still describes an intention the implementation did not take.
## Workflow follow-up

- Archive after task 6.1's full self-test run and task 6.2's green check are recorded.
- This change is the last of the series: after it, a second project copies the engines and writes a
  profile. Any later engine fix belongs in this repository first, then in the copy.
