Write tool failed — `ctx_patch` `create` dropped `new_text` on every attempt (8 tries; batch form carried it but refuses `create`). Returning the complete artifact below for the runtime to persist at `openspec/changes/fix-the-reds-the-main-carried/evidence/review-verdict.md`.

---

# Review — `fix-the-reds-the-main-carried` (#1048 "the main build is red", sensor `broken-main`; #1024's "the scan of main is red", sensor `broken-scan`)

Review-only round. No file edited, no git, no gate, no suite, no network. The one command run was the reading-set derivation.

## What was read

- `evidence/review-diff.txt` (259 lines, `git diff d885082d..HEAD`, commits e8f72493 ffeb162f 1171f5e4 c84d82b1) — read in full in windows 1–120, 120–184, 185–259.
- The five evidence files: `gate-workflows.txt` (10 lines, 4 blocks), `gate-rule-routes.txt` (3 blocks), `gate-loop-selftest.txt` (3 blocks), `gate-settings-surface.txt` (3 blocks), `red-runs.txt` (45 lines).
- `proposal.md`, `design.md`, `tasks.md`, `.openspec.yaml`.
- Reading set, derived: `bash tools/check-rule-routes.sh --diff d885082d..HEAD` → `EXIT=0`, 9 entries: `Backlog.md §1`, `Backlog.md §4`, `BacklogTooling.md §9`, `BuildEvidence.md §4`, `BuildEvidence.md §7`, `BuildEvidence.md §7 "the analysis is a consumer of the reports, and gates nothing."`, `BuildEvidence.md §7 "the scan publishes a findings record, and three sensor rows read it, and neither gates a merge."`, `BuildGate.md §2a`. Graded per name in item 7.
- Spec text: `openspec/specs/unit-test-suite-runtime/spec.md:146–180` (the two requirements the proposal cites), `:182–209` (the wall-clock-wait requirement).

**Correct (verified against the tree):**

- Surface figure. Counting the artifact's own `+`/`-` lines: `build.yml` 43/0, `sonar.yml` 29/0, `doc-routes.json` 6/1, `loop-cases.sh` 33/2, `SettingsSurfaceTest.kt` 16/1 = **127 insertions, 4 deletions** — exactly the proposal's `surface:` line. Five files, four causes, no sixth file.
- Restore-clean. `sha256sum tools/loop/tests/loop-cases.sh` = `591325326ec015deb1fc7117cd58b6249fe496501f625ff64579e7e73c8ce9be`, matching tasks.md 3.3's quoted "restored file" hash; the diff's committed blob (`index f65ca489..5f410364`) carries no mutation marker.
- Cause 4's suite is a real execution of this tree's case: `gate-settings-surface.txt` quotes `BUILD SUCCESSFUL in 44s` / `122 actionable tasks: 24 executed, 1 from cache`, and the XML on disk `app/build/test-results/testMobileDebugUnitTest/TEST-com.naviveylin.ui.settings.SettingsSurfaceTest.xml` carries `tests="23" skipped="0" failures="0" errors="0" timestamp="2026-11-24T06:46:42.335Z"` — a fresh timestamp, so the test task itself was not served from cache.
- Cause 3's full suite is a real execution: `gate-loop-selftest.txt` "all 548 cases passed (JDK 21, offline, no token, no sandbox) — 543.37 s", started 07:59:53 finished 08:08:56 on 2026-11-24; the arithmetic closes (07:59:53 + 9:03 = 08:08:56).
- Cause 2's check: `check-rule-routes: every glob matches the tree and all 658 named rule(s) are reached` / `EXIT=0`, plus the check's own 13 selftest cases.
- The settings seam is the established pattern, not a new one: `MainDispatcherRule` declares `dispatcher: TestDispatcher = StandardTestDispatcher()` (`app/src/test/java/com/naviveylin/test/MainDispatcherRule.kt:30`), so `advanceUntilIdle()` really drains the queue the change's storage now runs on; the diff's `it.ioDispatcher = mainDispatcherRule.dispatcher` (`SettingsSurfaceTest.kt:1060–1061`) sits beside the file's own `searchHistoryRepository.defaultDispatcher` (`:1068–1069`) and `viewModel.defaultDispatcher` (`:1076`).
- `SettingsStorage.ioDispatcher` exists as claimed (`app/src/main/java/com/naviveylin/data/SettingsStorage.kt:226`, `internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO`).
- `red-runs.txt` is honest about the run identity's own inconsistency: the log's `Commit:` line reads b8a594ea while the run's `headSha` is d885082d, and the file says so rather than smoothing it.

## 1. Scenario coverage

Graded per scenario, against the evidence's own timestamps.

- Cause 1, "Load does not change the failure set" (spec:171–174): **UNPROVEN.** No run on a loaded host, no comparison of failing classes. The change admits it: "The warm step's invariant (task 1.2) has no local falsifier — its absence is only observable as a 429" (tasks.md, Named exclusions). The red run is the only observation and it is pre-fix.
- Cause 2, "The review's reading route names every rule": **PASS** — `check-rule-routes` exit 0, 658 rules reached, 13 selftest cases. Provenance is thin: `gate-rule-routes.txt` carries no timestamp (see item 3).
- Cause 3, "the comparison reads the reader column as a set": **PASS** for the green half — `PASS driver#theReaderOrderIsNotPartOfTheComparison` and `PASS driver#theSharedNamesAreDeclared`, both with `EXIT=0`, inside the 07:59:53–08:08:56 run. **UNPROVEN** for the red half: the case's red-on-HEAD output is not in the evidence (item 4).
- Cause 4, "A formerly flaky case is decided by its subject" (spec:176–180): **UNPROVEN.** The scenario requires "the previously load-dependent cases run inside the full module suite" and "each is green in a forced repeated run whose tallies are recorded". The evidence is one filtered run — `--tests '*SettingsSurfaceTest'`, `tests="23"`, `0 failures` — with no repetition, no full-suite run and no tallies.

## 2. Cause or gap scope

**PASS.** Four causes, four edits, nothing else. The hunks are the workflow cache path plus warm step (`build.yml:245`, `:269`; `sonar.yml:105`, `:115`), five route rows in `tools/doc-routes.json` (6 additions, 1 comma change: lines 148, 156, 166, 174, 182), the derivation's `i = 1` and the declared-side normalisation (`loop-cases.sh` ~19516, ~19556–19558), and the two settings helpers (`:999`, `:1060–1061`). No production Kotlin, no native source, no dependency, no schema — verified against the diff, not taken from the proposal. The route rows are additive and the derivation change is a comparison, so the "minimal" reading holds.

## 3. Gate reality

- Cause 4: **PASS** — a real execution for this tree's case, fresh XML timestamp, `24 executed`, the one cached task is not the test task. No run identity is recorded beside the diff (`review-diff.txt.identity` does not exist), so "after the fix" rests on the XML timestamp 2026-11-24T06:46:42.335Z being later than the red runs' 2026-11-23T21:34:53Z — true, but not tied to a commit.
- Cause 3: **PASS** — timestamps, duration, "offline, no token, no sandbox", `/tmp/loop-selftest-full.log` present at 08:08 with 548 PASS / 0 FAIL.
- Causes 1 and 2: **UNPROVEN as executions of this tree** — `gate-workflows.txt` and `gate-rule-routes.txt` carry no timestamps and no run identity. Both are text-only checks, so the risk is low, but the "gate reality" question is unanswered by the artifact as filed.
- No gate suite was run, and the change's named exclusion says so. That exclusion's wording is wrong (see NOTE below): the settings test file is a compiled file and a Gradle test task was run.

## 4. Tasks claiming done

- **1.1** — `yq -e .` exit 0 and the path in the step's `path:` block: **satisfied** (`build.yml:245`, `:105`).
- **1.2** — **NOT satisfied.** The `*Verified:*` claims are `bash -n` on the extracted step, "the step run by hand prints `present:` for all three and exits 0", and "all three URLs answer HTTP 200 to `curl --head`". `evidence/gate-workflows.txt` contains four blocks and none of them: `yq -e . .github/workflows/build.yml` → `YAML OK`, `yq -e . .github/workflows/sonar.yml` → `YAML OK`, `check-sonar-workflow.sh` → `EXIT_SONAR=0`, `check-action-runtimes.sh` → `EXIT_RUNTIMES=0`. `design.md`'s Verification table row 1 states the same three outputs are "in `evidence/gate-workflows.txt`" — a contract contradiction between the design and the evidence file. Worse: the one run that was made prints `present:`, i.e. the skip branch; the fetch branch the step adds is unexercised.
- **1.3, 1.4, 1.5** — 1.3 and 1.4 satisfied; 1.5's "the file quotes each command's exit status" is inaccurate for the two `yq` blocks (no exit status printed).
- **2.1–2.6** — satisfied: JSON valid, route check exit 0 with the quoted sentence, 13 selftest cases, evidence filed.
- **3.1** — **NOT satisfied.** The red-on-HEAD output (the two refusals) is quoted in the task but appears in no evidence file.
- **3.2** — satisfied by the two PASS lines.
- **3.3** — **NOT satisfied.** "the mutation made the case FAILED" is a claim with no quoted output and no timestamp; `design.md`'s table row 3 says "the red-on-HEAD and mutation proofs" are in `evidence/gate-loop-selftest.txt`, which holds three blocks and neither proof. Restore-clean is independently verified (sha256 match).
- **3.4** — satisfied.
- **3.5** — **NOT satisfied and vacuous.** The bound case cannot see the file: `tools/loop/layout.tsv` has no row for `tools/loop/tests/loop-cases.sh` (its rows name `tools/loop/*.sh`; `grep 'loop-cases' tools/loop/layout.tsv` is empty), and `layout_bound_refusals` (`loop-cases.sh:19721–19744`) iterates table rows only. The `PASS` line is also only in `/tmp/loop-selftest-full.log`, not in the named evidence file.
- **4.1–4.4** — satisfied as written; 4.3's "23 cases, 0 failures" matches the XML.
- **5.1** — claimed, no output filed (a `validate --strict` exit 0 with nothing quoted).
- **5.2** — this round.
- **5.3** — correctly left `[ ]`.

## 5. Revert-check

- The mutation targets the declared-side normalisation only. The invariant "the reader column's order is not part of the comparison" is genuinely falsified by removing it — the case's fixture writes `shared_thing\ta.sh\td.sh c.sh b.sh` and the pre-fix declared side (`print $1 "\t" $2 "\t" $3`, no normalisation) would compare `d.sh c.sh b.sh` against the derivation's ascending order. Right reason, but the proof is not quoted and the "FAILED" message is absent, so the grade is UNPROVEN on evidence, not on logic.
- The derivation's `i = 1` change — the half the design blames on the runner's awk — has no dedicated falsifier. The new case's red-on-HEAD is guaranteed by the declared side alone being unsorted, so it does not isolate the derivation fix, and the design's own text says "the comparison was green on gawk 5.4 here", which means reverting only `i = 1` cannot be shown to fail locally. "Both sides or neither" is asserted, not measured.
- Restore-clean: **confirmed** by the sha256 match and the absence of a mutation marker in the committed blob.

## 6. Evidence pointers

- `127 insertions, 4 deletions` — command named in the proposal and **verified** against the artifact.
- `sha256sum` value — command named in the task, value not quoted in any evidence file; verified independently by this review.
- The warm step's `present:` output and the `curl --head` HTTP 200 claim — no command's output anywhere.
- `design.md`'s "the file's bound is unchanged" and task 3.5's `PASS driver#noEngineFileExceedsItsBound` — the PASS is real but lives only in `/tmp/loop-selftest-full.log`, and the bound it names does not apply to `loop-cases.sh`.
- `proposal.md`'s "`git diff --stat 3b6a4acf..d885082d -- app/src core/src auto/src` is empty" — command named, output not quoted; `red-runs.txt` restates it as "is empty" without the command's output. The argument it supports ("no source change, so a race") is the load-bearing one for cause 4, so the missing output matters.
- `/tmp/loop-selftest-full.log` is the only copy of the 548-case log — a `/tmp` copy does not survive the run.

## 7. Spec and guideline honesty

`skip_specs: true` is **honest**: no requirement's text changes, and the change's claim that the tree was short in four places is supported by the four checks. The delta being the tree rather than a contract is the right shape. Two qualifications:

- `unit-test-suite-runtime` "A module's failure set is independent of host load and concurrency" is cited for causes 1 and 4, but its "A formerly flaky case is decided by its subject" scenario's verification (forced repeated run, tallies, full module suite) is not done — the change's own cited contract is therefore only half met.
- `unit-test-suite-runtime` "A unit test controls the time its subject waits on" (spec:146–152) is not cited and not met: `awaitStored` keeps `composeRule.waitUntil(timeoutMillis = 5_000)` (`SettingsSurfaceTest.kt:999`) — a deadline computed from the system clock — and the change adds a drain inside that deadline loop rather than awaiting the observable state on the scheduler. Pre-existing, but the change's "each of the four checks enforced its contract" sentence does not address it.

Per the derived reading set:

- `Backlog.md §4` I10 "The loop verifies; it does not trust a claim" — violated by tasks 1.2, 3.1, 3.3 (the claims are not in the tree's evidence).
- `Backlog.md §1` "Status, and what this document owns" — the metadata names one issue (`x-issue: 1048`) while the proposal answers #1024's scan red too; the scan item's closure is not recorded.
- `BacklogTooling.md §9` "identity is finer than the check" — handled well: the red runs are identified by `headSha` and the log's divergent `Commit:` line is disclosed.
- `BuildEvidence.md §4` "Result evaluation" — the settings and loop tallies are quoted from the XML and the suite's own summary, not from a console grep: correct.
- `BuildEvidence.md §7` "the scan publishes a findings record, and three sensor rows read it, and neither gates a merge" — the change does not name the sensor row it satisfies or the findings record its next run will write; task 5.3 is the only pointer and it is open.
- `BuildGate.md §2a` "Agent iteration protocol" — the change measured first (red runs read, causes separated, one cause per edit): correct.

## 8. New overreach

- "the read won its race here and lost it on a loaded runner" (`design.md` D4; the same sentence in the diff's comment at `SettingsSurfaceTest.kt:1052–1057`) — no local pre-fix run is quoted; the tree's settings suite was only ever run post-fix.
- "the row already showed the persisted document on every device" (proposal, "Not changing") — no device evidence, and the change's own "No device verification" exclusion says there is none.
- "the comparison was green on gawk 5.4 here and red on the runner's awk" (the diff's comment at `loop-cases.sh:211–213`) — the red run's `FAIL driver#theSharedNamesAreDeclared` line is quoted without its failing assertion, so which side produced the disagreement is inferred, not shown.
- "the warm step's invariant ... its evidence is task 5.3's run" — task 5.3 is unchecked; the invariant is currently unfalsified and unverified.

## Findings

BLOCKING: Cause 1's `Verified:` claims are absent from the file the design names for them — `design.md`'s Verification table row 1 places "the warm step run by hand (`bash -n`, then all three `present:`)" in `evidence/gate-workflows.txt`, and that file holds four blocks (`yq` ×2 → `YAML OK`, `EXIT_SONAR=0`, `EXIT_RUNTIMES=0`) with no `bash -n`, no `present:` and no `curl --head`; tasks.md 1.2 claims the same three outputs. Smallest fix: append the three commands' real output to `gate-workflows.txt`, and run the step against an empty `$HOME/.m2` so the fetch branch executes rather than the skip branch.

BLOCKING: The cache half of D1 is inert as written — `build.yml:247` and `sonar.yml:107` keep `key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle.kts', 'gradle.properties', 'gradle/wrapper/gradle-wrapper.properties') }}` while adding `~/.m2/repository` to `path:`, and the diff touches no Gradle file, so the key is unchanged from the runs before this change. `actions/cache` skips the save when the key already exists, so the first post-merge run restores an `.m2`-less entry and the jars are never cached; `design.md` D1's "the cache keeps the jar, the warm step creates it on a cold key" is not achieved and every run re-downloads 3×144 MB. Smallest fix: extend the key in both workflows (e.g. a `-m2` component or a version suffix).

BLOCKING: Cause 3's red-on-HEAD and mutation proofs are missing from the file the design names — `design.md`'s Verification table row 3 says "the red-on-HEAD and mutation proofs" are in `evidence/gate-loop-selftest.txt`, which holds three blocks (two case PASSes and the full-suite summary) and neither proof; tasks.md 3.1 quotes the two refusals and 3.3 quotes "the mutation made the case FAILED" with no output and no timestamp anywhere. Smallest fix: append both outputs to `gate-loop-selftest.txt`.

BLOCKING: Cause 4's cited requirement's scenario is unverified — `unit-test-suite-runtime` "A formerly flaky case is decided by its subject" (`spec.md:176–180`) requires "the previously load-dependent cases run inside the full module suite" and "each is green in a forced repeated run whose tallies are recorded"; the evidence is one filtered run (`tests="23"`, `0 failures`, `gate-settings-surface.txt`) with no repetition, no full-suite run and no tallies. Smallest fix: run the case forced-repeated (`-PforceTests`) several times and the module suite once, and record the tallies.

NOTE: tasks.md 3.5 and `design.md` D3's Risk both claim `#noEngineFileExceedsItsBound` "still passes, so the file's bound is unchanged" — `tools/loop/layout.tsv` has no row for `tools/loop/tests/loop-cases.sh` and `layout_bound_refusals` (`loop-cases.sh:19721`) iterates table rows only, so the case cannot bound that file; the claim is vacuous and the PASS line lives only in `/tmp`.

NOTE: The derivation's `i = 1` half has no dedicated falsifier; the new case's red-on-HEAD is produced by the declared side alone, and the design's own "green on gawk 5.4 here" means reverting only `i = 1` cannot fail locally. "Both sides or neither" is asserted, not measured.

NOTE: "the change touches no compiled file" (tasks.md, Named exclusions) and "The build is not re-run for this change" (tasks.md line 2) are inaccurate — `SettingsSurfaceTest.kt` is compiled and `:app:testMobileDebugUnitTest` was run; the proposal's "no compiled production file" is the accurate wording. A test-only delta running the changed cases through `--tests` is the right scope (`Build.md` §2); only the sentence is wrong.

NOTE: tasks.md 1.5 "the file quotes each command's exit status" — the two `yq` blocks print `YAML OK` with no exit status; only `EXIT_SONAR` and `EXIT_RUNTIMES` are quoted.

NOTE: `awaitStored` keeps `composeRule.waitUntil(timeoutMillis = 5_000)` (`SettingsSurfaceTest.kt:999`), a system-clock deadline the `unit-test-suite-runtime` "A unit test controls the time its subject waits on" requirement refuses; the change adds a drain inside the deadline loop and leaves the deadline.

NOTE: The same seam stays open one line away — `darkModeController = DarkModeController(SettingsStorage(context))` (`SettingsSurfaceTest.kt:1071`) and the route panel's `settingsStorage = SettingsStorage(context)` (`:1084`) still run on the default `Dispatchers.IO`, so the same first-frame race remains reachable for those surfaces.

NOTE: `gate-workflows.txt` and `gate-rule-routes.txt` carry no timestamp and no run identity; the settings and loop runs do. `/tmp/loop-selftest-full.log` is the only copy of the 548-case log.

NOTE: The new case asserts only the no-refusal direction; no case pins that a genuine disagreement in the reader column still refuses.

NOTE: `.openspec.yaml` carries `x-issue: 1048` while the proposal also answers #1024's scan red; the scan item's closure is not recorded in the metadata.

NOTE: The warm step is duplicated ~35 lines in two workflows; `design.md` D1's "a bump is one line" understates it as two files. Under Actions' `bash -e`, a failed `mv` aborts the step despite the "a failed warm is a `::warning::`, never a failure" intent.

NOTE: The settings run's XML timestamp (2026-11-24T06:46:42.335Z) is later than the red runs but is not tied to a commit or a tree identity; `review-diff.txt.identity` does not exist.

LOOP-BLOCKING: 4
VERDICT: FAIL
LOOP-CORRECTIONS-ONLY: no
LOOP-DECISION: Two of the four fixes are unverified as filed — cause 1's workflow verification and cause 3's red-on-HEAD/mutation proofs are absent from the evidence files the design names, and the cache key makes D1's cache half inert; options: (a) worker appends the missing outputs, runs the warm step against an empty `$HOME/.m2`, extends the cache key in both workflows, and runs the settings case forced-repeated with tallies (recommended — every finding is a small, local edit and no cause's design changes); (b) accept the change as-is and let task 5.3's first green run stand as the only evidence; (c) re-open the cause-3 derivation question and measure whether the `i = 1` half is needed at all.