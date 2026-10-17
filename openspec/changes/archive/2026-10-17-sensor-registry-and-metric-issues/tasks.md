# Tasks

This change is spec-owned by `backlog-sensors`: `specs/backlog-sensors/spec.md` defines it, so each task names the
**design decision** it lands, and its verification names the case that exercises it; the traceability task in this change maps every scenario to its case. Every task carries its own verification.

Scope guard: this change edits shell, JSON and YAML — no Gradle source set, no compiled file, no
manifest, no native source. **No Gradle build and no Gradle suite is run or required**
(`guidelines/Build.md` §2, the scope block); the gate is `tools/sensor-selftest.sh`. The one build a
metric needs is a *consumer* of an existing artifact, never a second build (D10). No device or pixel
measurement applies: no UI changes and nothing is read on a screen — say so in the change report.

Ordering: `github-issue-tooling` and `migrate-todo-to-github-issues` must be applied first; this change
reads and writes the tracker they created. `agent-issue-loop` and `issue-metadata-loop` are independent
of it, except that a sensor's `occurrence` count feeds the latter's recomputation basis.

## 1. Guards (D3)

- [x] 1.1 Write `tools/sensors/guard.sh` asserting, as separate checks with separate exit reasons: on `main` (or the local equivalent), `HEAD == origin/main`, a clean tree, an ambient login in `allowedAuthors` (by calling `tools/issues/policy.sh preflight`, not by re-reading the allowlist), and a registry whose every row's command exists and whose `sensor/<id>` label is in the taxonomy. Verify: case `guard#eachCheckHasItsOwnReason` drives all five in a throwaway repository and asserts each fails with its own message and exit code (D3).
- [x] 1.2 Case `guard#refusesOffMain` — with `HEAD` one commit ahead of `origin/main`, the guard exits non-zero naming both commits, so a `workflow_dispatch` against a branch cannot reach a write (D3).
- [x] 1.3 Case `guard#refusesARowWithoutThresholdOrConsecutive` — a registry row missing either field is refused, so "sensor" cannot mean "reporter" (D3, D9).
- [x] 1.4 Case `sensors#usesTheReservedNamespacesAndAddsNone` — this change **reads** `source/ci` and the `sensor/` namespace and adds no namespace of its own, because `github-issue-tooling` reserves every namespace so that the vocabulary stays one file with one owner. Verify: the case asserts `source/ci` and `sensor/` exist in `tools/tooling.json`, that no task of this change edits that file, and that a fixture row whose label is absent from the taxonomy is refused by the guard rather than creating it (D9, D12).

## 2. Registry, runner and harness (D9)

- [x] 2.1 Write `tools/sensors/sensors.json` with the four metric rows and the two condition rows (`broken-main`, `pr-conflicts`), each carrying `id`, `cmd`, `cron`, `threshold`, `consecutive`, `class`, `category`, `lifecycle` and its fingerprint spec. Verify: `jq -e . tools/sensors/sensors.json` succeeds; a core case `Registry#everyRowIsComplete` asserts no row omits a required field and every `class`/`category` is inside `tools/tooling.json`'s closed vocabulary (D9).
- [x] 2.2 Write `tools/sensors/lib.sh` — the sensor contract: a sensor prints one JSON object per finding (`fingerprint`, `measurement`, `verdict`), and the library validates it against the row's spec before anything is filed. Verify: case `lib#rejectsAMalformedFinding` asserts a missing `fingerprint` or a `verdict` outside `violation|clear|error` is refused with a named reason and nothing filed (D5, D9).
- [x] 2.3 Write `tools/sensors/run.sh --due` (with `--dry-run` default, `--only <id>`, `--json`): select rows whose cron is due, run each, interpret the verdict, and dispatch to the three verbs. Verify: case `run#selectsOnlyDueRows` with a fixture clock asserts exactly the due rows run, and case `run#dryRunTouchesNothing` asserts zero stub-`gh` writes (D9).
- [x] 2.4 Implement the three-valued outcome in `run.sh`: `violation` files or reopens; `clear` comments and closes once hysteresis is met; `error` posts nothing, marks the row failed and exits non-zero. Verify: case `run#errorPostsNothingAndFails` asserts no `gh issue comment`/`create`/`close` call and a non-zero exit, and **case `run#errorIsNeverTreatedAsClear`** asserts an `error` verdict and a `clear` verdict produce different outcomes (D5).
- [x] 2.5 Implement the issue-as-history rule: every measurement is posted with `tools/issues/update.sh --comment`, and the runner never writes a baseline file, never commits and never touches `.pi/` state. Verify: case `run#noBaselineFileWritten` asserts `git status --porcelain` is empty after a fixture run and that `grep -rn 'git commit\\|git add' tools/sensors/` prints nothing (D8).
- [x] 2.6 Write `tools/sensors/report.sh` printing, per row: last run, verdict, the measurement, the issue number, the consecutive counters and the recurrence count — plus a line for each row whose cron has not fired recently (the 60-day disabled-schedule signal). Verify: case `report#printsACountingLinePerRow` asserts one line per registry row with all seven fields (D5, D9).

## 3. The three issue verbs (D4, D6, D7, D8)

- [x] 3.1 **Amended by measurement**: `update.sh` already gained the measurement path as `--comment-file <file>` (change 5, `issue-metadata-loop` D4), so this task added no flag — it added the *use* of it, with the guards the task asks for. `run.sh` posts every measurement through that path, and the two cases assert the contract: `update#commentPostsExactlyOnce` (one `gh issue comment` call carrying the file's body, nothing else written) and `update#commentRefusesOnPolicyFailure` (exit 3, nothing posted, when the identity is not on the allowlist).
- [x] 3.2 Add `--sensor <id>` to `tools/issues/close.sh`: run the policy pass, then **refuse any issue that does not carry that sensor's own `sensor/<id>` label** (exit 3, nothing written), and pass `--reason completed`. Verify: case `close#sensorScopeRefusesAForeignIssue` closes nothing for an issue labelled `sensor/broken-main` while the caller is `sensor/coverage`, and case `close#sensorScopeAllowsItsOwnIssue` posts the comment then closes with `--reason completed` (D7).
- [x] 3.3 Write `tools/issues/reopen.sh`: reopen a closed issue, post the returning measurement, increment `occurrence` in the body record, set `status/open`, and **never touch `agent/eligible`**. Refuse when `state_reason` is not `completed`. Verify: case `reopen#refusesANotPlannedClosure` asserts nothing is reopened and a comment is posted instead, and case `reopen#neverWritesEligibility` asserts the label write set contains no `agent/eligible` (D6, and `github-issue-tooling` D2).
- [x] 3.4 Implement the fingerprint dispatch in `run.sh`: `violation` + a matching closed issue ⇒ `reopen.sh`; `violation` + a new fingerprint ⇒ `create.sh`; `violation` + an open issue ⇒ `--comment` only. Verify: case `run#dispatchMatrix` drives all three and asserts the exact verb chosen for each, with the issue number in the report line (D4).
- [x] 3.5 Add `sensor/<id>` support to `tools/issues/create.sh`: accept the label, use the **condition fingerprint** as the dedup anchor, and store the fingerprint and `occurrence: 1` in the body record. Verify: case `create#sensorFingerprintIsTheAnchor` asserts two different conditions under one sensor produce two issues while the same condition twice produces exactly one (exit 4 on the second) (D4).

## 4. Lifecycle policy (D5, D6)

- [x] 4.1 Implement symmetric hysteresis in `run.sh` from the row's `consecutive`: close only after N consecutive `clear` runs, reopen only after N consecutive `violation` runs, with the counters kept in the issue's body record (never in a side file). Verify: case `run#hysteresisBothDirections` asserts an alternating violation/clear sequence neither closes nor reopens until N consecutive of one kind, and case `run#hysteresisCountersSurviveARestart` asserts the counters come from the issue, not from process memory (D6, D8).
- [x] 4.2 Implement the recurrence escalation: past the row's `K` (default 3) occurrences, stop reopening, set `agent/needs-decision` and `status/on-hold`, and report the row as escalated. Verify: case `run#escalatesPastKRecurrences` asserts the third reopen is replaced by an escalation and that no fourth reopen happens (D6).
- [x] 4.3 Case `run#escalationNeverSetsEligibility` — the escalation's label write touches `agent/needs-decision` and `status/on-hold` only, so a recurring sensor condition reaches the human lane rather than the loop (D6).

## 5. The metric rows and the one new measurement (D10)

- [x] 5.1 Wire the **build duration** row to the existing `tools/gate-timing-report.sh` with a threshold expressed as a multiple of a recorded baseline, read from the issue's previous comment. Verify: case `sensors#buildDurationRow` with two fixture reports asserts no issue on the first, a `violation` past the threshold, and that the "baseline" quoted in the issue is the earlier comment's number (D8, D10).
- [x] 5.2 Write `tools/sensors/hotspot-report.sh`: per path, size × change frequency from `git log --numstat`, ranking and printing the JSON the contract expects, with the threshold and the consecutive count coming from the registry row rather than from the script. Verify: case `hotspot#ranksBySizeTimesChanges` on a fixture repository asserts the ordering and that the script contains no threshold constant (D9, D10).
- [x] 5.3 Wire the **package size** row to an existing gate or release artifact, never to a build of its own. Verify: case `sensors#packageSizeConsumesAnArtifact` asserts the row reads a file produced by another step and that `grep -n 'gradlew\\|assemble' tools/sensors/*.sh` prints nothing (D10).
- [x] 5.4 Wire the **coverage** row to the existing coverage instrumentation with a per-module floor. Verify: case `sensors#coverageRow` asserts a floor violation files once and that a second run at the same number comments instead of filing again (D4, D10).
- [x] 5.5 Write the two condition rows: `broken-main` (the build result, with the failing job and case in its fingerprint) and `pr-conflicts` (`gh pr list --json mergeable`, re-read once because the field is computed lazily, restricted to the allowlisted author's own PRs). Verify: case `sensors#brokenMainFingerprintDistinguishesCauses` asserts two different failing cases produce two issues, and case `sensors#prConflictsOnlyOwnPRs` asserts a fixture PR by another author is reported but not filed (D4; `github-issue-tooling` D2).

## 6. The isolated pipeline (D1, D2)

- [x] 6.1 Write `.github/workflows/sensors.yml`: its own file, `on: schedule` + `workflow_dispatch` only, workflow-level `permissions: { contents: read }`, job-level `issues: write`, `if: github.ref == 'refs/heads/main'`, a `concurrency` group, and no `pull_request*` or `workflow_run` trigger anywhere. Verify: `grep -n 'pull_request\\|workflow_run' .github/workflows/sensors.yml` prints nothing; case `workflow#onlyTwoTriggers` parses the YAML (via `jq` on a converted form or a grep of the trigger block) and asserts exactly `schedule` and `workflow_dispatch` (D1).
- [x] 6.2 Add the job's first step: run `tools/sensors/guard.sh` before any sensor, so the run cannot reach a write without passing D3's checks. Verify: `grep -n 'guard.sh' .github/workflows/sensors.yml` is the step preceding the runner invocation (D3).
- [x] 6.3 Pass the token as `GH_TOKEN` from a repository secret, and assert the workflow uses no `GITHUB_TOKEN`-derived `gh` authentication for issue writes. Verify: the workflow sets `GH_TOKEN: ${{ secrets.<pat> }}` on the sensor step and `grep -c 'github.token' .github/workflows/sensors.yml` is 0 (D2).
- [x] 6.4 Add `.github/CODEOWNERS` covering `.github/workflows/` with the owner as reviewer, and confirm `.github/workflows/build.yml` is otherwise untouched. Verify: `test -e .github/CODEOWNERS` and `git diff --name-only` lists no change to `build.yml` (D1). **Amended by measurement**: the CODEOWNERS file covers `/.github/workflows/`, and `build.yml` gained exactly the one token-free step task 7.2 requires (`Check sensor tooling`) — no other line in it moved, and this task itself needed no change to it. Case `workflow#onlyTwoTriggers` asserts both files' state.
- [x] 6.5 Record the actor decision and its alternatives in the change report: a repo-scoped fine-grained PAT with `Issues: write` and nothing else, chosen over allowlisting `github-actions[bot]`, with the blast-radius argument and the App upgrade path quoted from `design.md` D2. Verify: the report names the token's scope, the rejected alternative and the fallback (D2).

## 7. Self-test and CI (all)

- [x] 7.1 Write `tools/sensor-selftest.sh`: creates a throwaway git repository under the gitignored `.pi/tmp/`, puts the stub `gh` (change 1) on `PATH`, runs every case in groups 1–6, prints `PASS`/`FAIL <case>` and exits non-zero on any failure. Verify: the script exits 0 locally in an empty environment (`env -i PATH="$PATH" bash tools/sensor-selftest.sh`) and non-zero when one case is deliberately broken.
- [x] 7.2 Add one `Check sensor tooling` step to `.github/workflows/build.yml` running the self-test with no token. Verify: the step's command runs green locally and the step grants no permissions beyond `contents: read` (D1). **Amended by measurement**: a step grants no permission of its own — it runs with the job's token, and that job carries `contents: read` plus the `actions: write` and `packages: write` the existing cache and vcpkg steps already need. What the task's rule protects therefore holds and is checkable: this step writes no tracker, and the job's permission set contains no `issues: write` at all (only `.github/workflows/sensors.yml` raises that). The step runs `bash tools/sensor-selftest.sh` and `bash tools/sensor-mutations.sh --list`, both green locally with no token.
- [x] 7.3 Case `sensors#engineNamesNoProjectValue` — assert the engine is portable: `grep -rn 'broken-main\|pr-conflicts\|gradlew\|NaviVeylin\|Framstag' tools/sensors/run.sh tools/sensors/lib.sh tools/sensors/guard.sh tools/sensors/report.sh` prints nothing, every row's command comes from `sensors.json`, and the repo and allowlist come from `tools/tooling.json` (D12).

## 8. Falsification (revert-checks)

One mutation each, restored after; the named case must fail, then the suite must be green again. The
gate is `tools/sensor-selftest.sh`, not Gradle — name the exit codes in the report.

**Ran: 9 of 9 behaved.** Each mutation was applied to the shipped script, the named case run (red), the file
restored, and the case run again (green); the harness is `bash tools/sensor-mutations.sh` (listed by the CI
step so it cannot rot silently) and the table is in `report.md`.
Two cases had to be strengthened first — one asserted a digest that the finding's own marker line also
carries, and one read a body its writer had already deleted — because a case that cannot be shown red is not
evidence (`revert-check`).

- [x] 8.1 Guard: drop the `HEAD == origin/main` check. `guard#refusesOffMain` must fail. Restore; selftest exits 0 (D3).
- [x] 8.2 Trigger: add `pull_request` to the workflow's triggers. `workflow#onlyTwoTriggers` must fail. Restore; selftest exits 0 (D1).
- [x] 8.3 Ownership scope: remove the `sensor/<id>` check from `close.sh`. `close#sensorScopeRefusesAForeignIssue` must fail. Restore; selftest exits 0 (D7).
- [x] 8.4 Error outcome: make an `error` verdict post a `clear` comment. `run#errorIsNeverTreatedAsClear` must fail. Restore; selftest exits 0 (D5).
- [x] 8.5 Hysteresis: close on the first `clear` run instead of after N. `run#hysteresisBothDirections` must fail. Restore; selftest exits 0 (D6).
- [x] 8.6 Escalation: remove the `K`-recurrence limit. `run#escalatesPastKRecurrences` must fail. Restore; selftest exits 0 (D6).
- [x] 8.7 Human decision: let `reopen.sh` reopen a `not_planned` closure. `reopen#refusesANotPlannedClosure` must fail. Restore; selftest exits 0 (D6).
- [x] 8.8 Fingerprint: use the sensor id as the dedup anchor instead of the condition fingerprint. `sensors#brokenMainFingerprintDistinguishesCauses` must fail. Restore; selftest exits 0 (D4).
- [x] 8.9 Portability: hard-code a row's command or a threshold in `run.sh`. `sensors#engineNamesNoProjectValue` must fail. Restore; selftest exits 0 (D12).

## 9. Integration

- [x] 9.1 Run `tools/sensors/run.sh --due --dry-run` on the host and record its report: the rows selected, the verdicts, the measurements and what it would file. Verify: the transcript is recorded and no `gh` write occurred (`gh issue list --state all --json number` unchanged) (D9). **Recorded** in `report.md` and `evidence/`: two rows due at the chosen clock, both `error` (their artifacts do not exist on the host, so the run exits 8 and posts nothing), plus a fixture-report run showing the one filing it would make — and the tracker's issue count was 159 before and after every run.
- [ ] 9.2 Dispatch `.github/workflows/sensors.yml` once against `main` with `--dry-run`, and record the workflow run URL and the report it printed. Verify: the run's log carries the guard lines, the token is not echoed, and no issue was created (D1, D3).

  **Owed — not reproducible in this working tree**, and named as such in `report.md`; the owner's decision of
  2026-10-17 is to leave it owed rather than to push and dispatch from this session. A dispatch needs the
  workflow on the default branch of `Framstag/NaviVeylin`, a push, and `SENSORS_TOKEN` as a repository
  secret. The file's own properties (two triggers, the `main` pin, `concurrency`, the permission split, the
  guard's position, no `github.token`, the CODEOWNERS entry) are asserted by `workflow#onlyTwoTriggers` on
  every CI run; the owed part is the *run record*, and the first scheduled or dispatched run after the merge
  produces it.
- [ ] 9.3 Run one sensor for real and record the outcome: the issue number it filed (or reopened), the measurement, and the `sensor/<id>` label on it. Verify: `tools/issues/list.sh --json` shows the issue with its `class/*`, `category/*`, `source/ci` and `sensor/<id>` labels, and its `state_reason` handling is exercised on a close by running the same row after the condition clears — or the clearing case is named as not reproducible and recorded as such (D4, D5).

  **Owed by decision** (the owner's, 2026-10-17), for three reasons recorded in `report.md`: it files a real
  issue in the public tracker; it needs the standing `SENSORS_TOKEN` that task 9.2 also needs; and the guard
  refuses in this checkout (no `origin/main` ref), so the run would have to bypass the very guard this change
  installs. The behaviour it would exercise is covered offline instead: `run#dispatchMatrix` (file / reopen /
  annotate by fingerprint), `run#dispatchMatrixNotPlannedClosure` and `sensors#coverageRow` (a second run at
  the same number comments instead of filing), with the label set asserted on the recorded `gh issue create`
  call.
- [x] 9.4 Confirm the exclusions explicitly in the change report: no Gradle build and no suite was run; `build.yml` gained one token-free step only; the app, the native boundary and the release path are untouched. Verify: `git diff --name-only` lists no file under `app/`, `auto/`, `core/`, `osmscout-client-java/`, `buildSrc/`, and the report names the exclusion (`guidelines/Build.md` §2, scope block).
- [x] 9.5 Record the change's own numbers for the concept doc §6 value gate: rows run, findings filed, findings that were comments rather than issues (the dedup doing its job), errors, and the CI minutes the pipeline costs. Verify: the numbers are in the change's evidence and the report quotes them rather than describing the run.

- [x] 9.6 Classify `backlog-sensors` in `tools/feature-list/specs.json` as the capability-classification gate requires, since CI fails on a shipped spec with no line there (the gate is `tools/gen-feature-list.sh --check-classification`). Verify: adding the line makes `bash tools/gen-feature-list.sh --check-classification` exit 0, and removing it makes the same command fail naming `backlog-sensors`, so the line is load-bearing rather than decorative.
- [x] 9.7 Add the scenario-to-task traceability table for `backlog-sensors` and verify it is complete: one row per scenario naming the case that exercises it and the task that owns that case, with no scenario unmapped and no task claiming a scenario it does not cover. Verify: the row count equals `grep -c "^#### Scenario:" specs/backlog-sensors/spec.md` and the change report says so. **Done**: `traceability.md` carries 29 rows and 29 scenarios; the report's task 9.7 section names both counts.
- [x] 9.8 Keep `guidelines/Backlog.md` true for this change, and verify the routing table resolves: correct every section this change makes stale — its row in §1, and the §12 rows for the decisions it lands — then run `bash tools/check-doc-routes.sh` and `bash tools/check-doc-routes-selftest.sh` and assert both exit 0. Verify: both exit 0, the §1 row for sensor-registry-and-metric-issues is marked landed rather than planned, and no §12 row it owns still describes an intention the implementation did not take.
## Workflow follow-up

- Archive after tasks 9.2 and 9.3 have recorded a real CI dispatch and a real filed-or-reopened issue,
  or after 9.3 has recorded explicitly why the condition could not be produced.
- `issue-metadata-loop` can be applied before or after this change; applied after, it can use the
  `occurrence` count as part of the priority basis.
