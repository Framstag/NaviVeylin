# Tasks

Parent spec for every task below: `backlog-sensors` — delta in `specs/backlog-sensors/spec.md` (one
requirement modified: *The checks run in a pipeline of their own, pinned and least-privileged*). Design:
`design.md` (D1 = the window, D2 = its width and the no-double-measure tiling, D3 = the caller declares it and
the pipeline declares its cadence, D4 = the refusal). This change touches no compiled file — no Kotlin, no
C++, no Gradle logic — so every verification below is a structural check or an offline shell suite
(`guidelines/Build.md` §2, scope block): **no Gradle build, no test suite, no device**, and that exclusion is
deliberate, not an omission.

## 1. Reproduce the defect

- [x] 1.1 Write the three cases that reproduce the defect and record their red-on-HEAD run before touching a
  shipped file: `run#aDelayedTickSelectsTheRowsItWasScheduledFor`, `run#theWindowDoesNotSelectThePreviousTicksRow`
  and `workflow#theTickDeclaresItsWindow` (`tools/sensor-cases.sh`), beside the issue's own two measured
  invocations (spec: backlog-sensors — The schedule fires without a person; design D1). The issue's case is the
  00:07 tick: the punctual tick selects `broken-main`, the 7-minute-late one prints `no rows selected` and
  exits 0.
  Verify: `bash tools/sensor-selftest.sh` = 3 of 43 cases failed, the 40 pre-existing cases green, and the two
  invocations' output quoted with their exit codes.
  **Done 2026-10-18, HEAD `4bd0504`:** `evidence/red-on-head.log` §1 (`--now 2026-10-18T00:00:00Z` selects
  `broken-main`, `exit=0`; `--now 2026-10-18T00:07:00Z` prints `no rows selected at 2026-10-18T00:07:00Z`,
  `exit=0`) and §2 (`the delayed tick's run is green: sensors: run: unknown argument --within: expected '0',
  got '2'`, `the far edge of the window is green: … unknown argument --within`, `every scheduled tick passes
  --within 30, the window its cadence (0,30 * * * *) declares: expected '2', got '0'`). TALLY: 3 new cases red,
  0 of the 40 pre-existing cases red.

## 2. The window in the engine (design D1–D4)

- [x] 2.1 `tools/sensors/lib.sh`: the per-minute matcher keeps its body as `sensors_cron_minute`, and
  `sensors_cron_due <cron> <epoch> [<minutes>]` becomes the window predicate — due when the expression matches
  any of the `<minutes>` minutes ending at the tick's own minute, default 1 (the punctual call shape
  unchanged), 2 for a malformed expression or a window that is not a count of minutes (spec: backlog-sensors —
  The schedule fires without a person; The window measures a row once; design D1, D2).
  Verify: `bash tools/sensor-selftest.sh --case run#selectsOnlyDueRows` still passes (the two-argument call is
  unchanged) and `bash -n tools/sensors/lib.sh` is clean.
  **Done 2026-10-18:** `PASS run#selectsOnlyDueRows`; `sensors_cron_minute` is the shipped awk body verbatim,
  `sensors_cron_due` walks `back` from 0 to `minutes - 1` and returns the first status that is 0 (due) or 2
  (cannot tell).
- [x] 2.2 `tools/sensors/run.sh`: `--within <minutes>` (default 1) is the window `--due` selects over, and a
  value that is not a whole number of minutes, or is 0, is refused with exit 2 before anything is read — a
  silently narrowed window is the defect (spec: backlog-sensors — The window measures a row once; design D3,
  D4).
  Verify: the usage block names the flag and its default (`bash tools/sensors/run.sh --help`), and
  `bash tools/sensor-selftest.sh --case run#theWindowDoesNotSelectThePreviousTicksRow` passes (the refusal's
  two halves).
  **Done 2026-10-18:** `run.sh --help` prints `[--within <minutes>]` and the paragraph that states the window
  and the refusal; the case passes both of its refusal assertions (`--within 0` and `--within half`, exit 2,
  the flag named).

## 3. The pipeline declares its window (design D3)

- [x] 3.1 `.github/workflows/sensors.yml`: both row steps pass `--within 30` — the tick cadence the schedule
  `0,30 * * * *` declares — and the header's schedule paragraph names the value and why (spec:
  backlog-sensors — The pipeline declares the window its ticks select over; design D3).
  Verify: `yq -e '.' .github/workflows/sensors.yml` parses, and `yq -r '.jobs.sensors.steps[] | select(.run !=
  null) | .name + " :: " + .run'` shows both row steps carrying the window.
  **Done 2026-10-18:** the file parses; the two row steps read
  `bash tools/sensors/run.sh --due --within 30 --rows build-duration,package-size,coverage "$SENSORS_MODE"` and
  `bash tools/sensors/run.sh --due --within 30 --rows broken-main,pr-conflicts,hotspot,stale-citations
  "$SENSORS_MODE"`.

## 4. The cases that prove it

- [x] 4.1 `tools/sensor-cases.sh`: `run#aDelayedTickSelectsTheRowsItWasScheduledFor` (the 03:07 tick still
  selects the 03:00 row, the punctual tick is unchanged, and an unwindowed late tick is the defect's own
  silent no-op, kept as the reason the window exists) and `run#theWindowDoesNotSelectThePreviousTicksRow` (the
  window's far edge at 03:29 reaches the 03:00 row, the next tick at 03:30 does not re-select it, and a
  `--within` that is not a count of minutes is refused) (spec: backlog-sensors — The schedule fires without a
  person; The window measures a row once; design D2, D4).
  Verify: both cases pass and the whole suite is 43 of 43 (was 3 of 43 red on HEAD).
  **Done 2026-10-18:** `PASS run#aDelayedTickSelectsTheRowsItWasScheduledFor`,
  `PASS run#theWindowDoesNotSelectThePreviousTicksRow`,
  `sensor-selftest: all 43 cases passed (JDK 27, offline, no token)`.
- [x] 4.2 `tools/sensor-cases.sh`: `workflow#theTickDeclaresItsWindow` — the cadence derived from the
  schedule's own cron (the shortest distance between the minutes its minute field names, an hour wrapping, so
  `0,30` is 30 and a single minute is 60), asserted against every `run.sh --due` invocation the workflow
  carries, so the declared window moves with a cadence change and a step that goes back to one minute fails
  (spec: backlog-sensors — The pipeline declares the window its ticks select over; design D3). The row-step
  pattern of `workflow#aRedRecordRunsNoRecordRow` is widened from `--due --rows` to `--due .*--rows`, because
  the step's invocation now names its window before its rows; the assertion itself (two row steps, their gates,
  their row lists exactly the registry) is unchanged.
  Verify: `PASS workflow#theTickDeclaresItsWindow`, and `PASS workflow#aRedRecordRunsNoRecordRow` with its
  `expected '2'` row-step count still asserted.
  **Done 2026-10-18:** `PASS workflow#theTickDeclaresItsWindow` (cadence 30 read from `0,30 * * * *`, 2 of 2
  `--due` invocations carrying it); `PASS workflow#aRedRecordRunsNoRecordRow`.

## 5. Falsify each new bound (guidelines/Build.md §2, `revert-check`)

- [x] 5.1 Mutation `8.12` (`tools/sensor-mutations.sh`): `lib.sh` ignores the window (`minutes="${3:-1}"` →
  `minutes=1`), so the window reaches back over nothing; `run#aDelayedTickSelectsTheRowsItWasScheduledFor` must
  go red (spec: backlog-sensors — The schedule fires without a person).
  Verify: `MUTATION 8.12: … red under the mutation, green after the restore`.
  **Done 2026-10-18:** `MUTATION 8.12: run#aDelayedTickSelectsTheRowsItWasScheduledFor red under the mutation,
  green after the restore — the window reaches back over the minutes since the previous tick`.
- [x] 5.2 Mutation `8.13`: the workflow drops ` --within 30`, so a scheduled tick declares no window;
  `workflow#theTickDeclaresItsWindow` must go red (spec: backlog-sensors — The pipeline declares the window its
  ticks select over).
  Verify: `MUTATION 8.13: … red under the mutation, green after the restore`.
  **Done 2026-10-18:** `MUTATION 8.13: workflow#theTickDeclaresItsWindow red under the mutation, green after
  the restore — every scheduled tick declares the window it selects over`.
- [x] 5.3 Mutation `8.14`: the refusal's `sensors_die` becomes the no-op `:`, so a window that is not a count of
  minutes is narrowed instead of refused; `run#theWindowDoesNotSelectThePreviousTicksRow` must go red (spec:
  backlog-sensors — The window measures a row once; design D4).
  Verify: `MUTATION 8.14: … red under the mutation, green after the restore`, and the whole harness is 14
  behaved / 0 did not.
  **Done 2026-10-18:** `MUTATION 8.14: run#theWindowDoesNotSelectThePreviousTicksRow red under the mutation,
  green after the restore — a window that is not a count of minutes is refused`; the full run
  `sensor-mutations: 14 behaved, 0 did not`.

## 6. The spec delta and its validation

- [x] 6.1 `specs/backlog-sensors/spec.md`: one MODIFIED requirement — the shipped description and its nine
  untouched scenarios copied byte-identically, *The schedule fires without a person* widened to the late tick
  and the window it selects over, and two scenarios added beside it (*The window measures a row once*, *The
  pipeline declares the window its ticks select over*), each naming the case that exercises it (spec:
  backlog-sensors; design D1–D4).
  Verify: `diff` of the shipped requirement block against the delta reports only those three scenario changes,
  and the scenario count moves 10 → 12.
  **Done 2026-10-18:** `diff` of `openspec/specs/backlog-sensors/spec.md`'s block against the delta names only
  the modified scenario's three lines and the two added scenarios (plus the block's trailing blank line);
  scenarios 10 → 12.
- [x] 6.2 `openspec validate fix-sensor-late-tick-window --type change` must exit 0.
  Verify: run the command; record its output here.
  **Done 2026-10-18:** `Change 'fix-sensor-late-tick-window' is valid`, `exit=0`, no warning of its own.

## 7. Verify the whole change (no compiled file: the suites are the gate)

- [x] 7.1 The owning suite green: `bash tools/sensor-selftest.sh` (43 cases), offline, no token, no network
  (guidelines/Build.md §2, §6; `Design.md` §11).
  Verify: the tally, quoted with the command and the timestamp, in `evidence/green.log`.
  **Done 2026-10-18:** `sensor-selftest: all 43 cases passed (JDK 27, offline, no token)` — was 3 of 43 red on
  HEAD.
- [x] 7.2 The mutation harness: `bash tools/sensor-mutations.sh` (14) — every new bound falsified, every case
  green after the restore.
  Verify: "14 behaved, 0 did not" in `evidence/green.log`.
  **Done 2026-10-18:** `sensor-mutations: 14 behaved, 0 did not`; `8.12`, `8.13` and `8.14` are the change's
  three.
- [x] 7.3 The structural checks of the touched surface: `bash tools/build-inputs-selftest.sh` (the workflow's
  tick cron is asserted there too), `bash tools/check-portability.sh` (the engine names no project value — the
  window's value lives in the workflow, not in `tools/sensors/**`), `bash tools/check-doc-routes.sh`,
  `bash tools/build-inputs.sh --check`, `bash tools/declared-cases-selftest.sh` and the CI hygiene step's
  replication for `openspec/**` (guidelines/Build.md §2, scope block).
  Verify: each command's own verdict in `evidence/green.log`.
  **Done 2026-10-18:** `11 cases, 0 failed`; `check-portability: OK — 42 engines and 2 templates name no
  project value (133 value(s) read from the project)`; `check-doc-routes: all 89 section references resolve`;
  `build-inputs: every declared input has a producer (4 rows checked)`; `12 passed, 0 failed`.
- [x] 7.4 No Gradle run, deliberately: this change touches no compiled file (no Kotlin, no C++, no Gradle logic,
  no resource), so the gate's build and unit-test steps have nothing of this change to fail —
  `guidelines/Build.md` §2, scope block. Named here so the exclusion is a decision, not an oversight.
  Verify: `git diff --name-only` names only `*.sh`, `*.md`, `*.yml` and the change's own artifacts.
  **Done 2026-10-18:** the diff's file list is two `tools/sensors/*.sh`, two `tools/*.sh`, one
  `.github/workflows/*.yml`, one `openspec/specs/*/spec.md` and the change's artifacts; no compiled file.

## 8. Archive, commit, close

- [ ] 8.1 Archive the change after the suites are green: sync the delta into
  `openspec/specs/backlog-sensors/spec.md` and move the change to
  `openspec/changes/archive/<date>-fix-sensor-late-tick-window/` (the `openspec-archive-change` skill — moved,
  not copied).
  Verify: `openspec validate --specs` is clean, the archived path exists, and `openspec list --json` no longer
  shows the change as active.
  **Done 2026-10-18:** the delta was spliced into `openspec/specs/backlog-sensors/spec.md` at the
  requirement's own boundaries (lines 1–157 and 230–312 copied byte-identically, the delta's block inserted in
  their place — `diff` of the new prefix, suffix and merged block against the delta reports only the three
  intended scenario changes), scenarios 42 → 44, no delta-style header left, `openspec validate backlog-sensors
  --type spec` → `Specification 'backlog-sensors' is valid`, `openspec validate --specs` → `Totals: 175
  passed, 0 failed`, `--changes` → `Totals: 15 passed, 0 failed`, and the change moved (not copied) to
  `openspec/changes/archive/2026-10-18-fix-sensor-late-tick-window/`, after which `openspec list --json` no
  longer names it.
- [ ] 8.2 One commit for the whole change: `fix: fix-sensor-late-tick-window (#166)`.
  Verify: `git status --porcelain` is clean afterwards (the untracked `Agentisches-Coding-NaviVeylin.md` is not
  this change's and is left alone).
  **Done 2026-10-18:** one commit, `fix: fix-sensor-late-tick-window (#166)`, carrying the engine, the
  workflow, the cases, the mutation harness, the synced main spec and the archived change (its `evidence/` and
  the closure reason file included); the tree is clean but for the pre-existing untracked
  `Agentisches-Coding-NaviVeylin.md`, which is not this change's and was left alone. The independent review of
  this diff is the parent session's review gate, which follows the archive (this session's role may not run
  one); everything that gate needs is `evidence/red-on-head.log`, `evidence/green.log` and
  `tools/sensor-mutations.sh`.
- [ ] 8.3 Close what landed: #166 gets `status/fixed-by` and a closure naming the archived change, with the
  reason file under the archived change's `evidence/` (the `close-issue` and `update-issue` skills).
  Verify: `tools/issues/list.sh --status fixed-by` names #166 with that status label (the item's identity is
  its GitHub number — `--id 166` resolves the migrated `§166`, which is a different item), and the reason
  file's path is cited in the closure comment.
  **Done 2026-10-18:**
