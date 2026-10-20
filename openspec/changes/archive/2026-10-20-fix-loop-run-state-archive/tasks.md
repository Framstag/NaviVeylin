# Tasks — fix-loop-run-state-archive

Parent spec for every task below: `openspec/specs/agent-issue-loop/spec.md`, the requirement this change adds,
**"A run's state outlives the run that replaces it"**. The delta is
`openspec/changes/fix-loop-run-state-archive/specs/agent-issue-loop/spec.md` (four scenarios: "The state a run
replaces is kept, named by that run's start", "A run that never recorded a stop is announced, not refused", "A
kept run is reported on after the next run started", "The budget line belongs to the run whose state it reads").

## 1. Reproduce the defect (red on HEAD)

- [x] 1.1 Add `state#initKeepsTheStateItReplaces` to `tools/loop-cases.sh`, registered in **both** the `--list`
  array and the dispatch map: `init` a 2-item / 90-min run, record a stop, `init` again, and the replaced file
  must be at `<state-dir>/runs/<run.startedAt>.json` with its own items and stop reason while the new state is
  the one in place. **Parent:** the delta scenario "The state a run replaces is kept, named by that run's
  start", case `state#initKeepsTheStateItReplaces`. **Verification:** on HEAD `8461bd2` the case fails with
  `the replaced state was not kept at …/.pi/issue-loop/runs/2026-10-20T05:48:27Z.json (found: )` —
  `evidence/red/initKeepsTheStateItReplaces.txt`.
- [x] 1.2 Add `state#initAnnouncesARunWithoutAStop` the same way: `init` over a state with no `run.stop.reason`
  exits 0 and prints `never recorded a stop reason` on stderr; over a state that recorded a stop it prints
  nothing. **Parent:** the delta scenario "A run that never recorded a stop is announced, not refused", case
  `state#initAnnouncesARunWithoutAStop`. **Verification:** on HEAD it fails with `the announcement names what is
  being replaced: 'never recorded a stop reason' not found in: ` (nothing announced) —
  `evidence/red/initAnnouncesARunWithoutAStop.txt`.
- [x] 1.3 Add `report#archivedStateIsReported`: a 4-item / 480-min run with one skip and a stop is kept by the
  next `init`, and `report.sh --state <kept file>` must print that run's budget, counters, stop reason, item row
  and skip, while the plain report is the run in place. **Parent:** the delta scenario "A kept run is reported on
  after the next run started", case `report#archivedStateIsReported`. **Verification:** on HEAD the report has no
  such option — `loop: report: unknown argument --state`, `evidence/red/archivedStateIsReported.txt`.
- [x] 1.4 Add `report#impossibleBudgetNamesAnotherRun`: a state whose window closed 423 minutes ago without a
  stop must print the wording this change introduces and no negative budget; the same state with a stop 25
  minutes into its window must print its own numbers. **Parent:** the delta scenario "The budget line belongs to
  the run whose state it reads", case `report#impossibleBudgetNamesAnotherRun`. **Verification:** on HEAD the
  report prints `closed 0 · skipped 0 · blocked 0 · budget left 2 items / -422 min · spent 452 min`, the measured
  defect — `evidence/red/impossibleBudgetNamesAnotherRun.txt`.

## 2. The fix

- [x] 2.1 `tools/loop/state.sh`: `state_resolve` resolves the runs directory — `LOOP_RUNS_DIR` when the project
  records one (relative to the project root), otherwise `runs/` beside the state file — and a new
  `keep_replaced_state` copies the state `init` is about to replace to `<runs dir>/<run.startedAt>.json`, taking
  a `.N` suffix rather than overwriting an archive, and announces a missing/empty/unparseable
  `run.stop.reason` on stderr. `cmd_init` calls it before `write_state`; the file header states both. **Parent:**
  the first two delta scenarios. **Verification:** cases 1.1 and 1.2 go green — `evidence/green/`.
- [x] 2.2 `tools/loop/report.sh`: `--state <file>` selects the state to report on (relative names against the
  project root), and every read uses it, the child that renders the item table included; the default stays
  `LOOP_STATE_FILE`. The budget line measures the spend of the run whose state it reads — `run.stop.at −
  run.startedEpoch` once the run recorded its end, `now − run.startedEpoch` only while the window is open — and
  otherwise prints `budget left unknown: these numbers belong to another run — its window closed at
  <run.deadlineAt> and it recorded no stop`. A state without numeric epoch fields takes the same branch instead
  of failing the arithmetic. **Parent:** the last two delta scenarios. **Verification:** cases 1.3 and 1.4 go
  green — `evidence/green/`.
- [x] 2.3 `tools/loop/loop.conf`: record `LOOP_RUNS_DIR=""` beside the other keys, documenting that empty means
  `runs/` beside the state file and that a relative recorded value is relative to the project root. **Parent:**
  the delta scenario "The state a run replaces is kept, named by that run's start". **Verification:**
  `grep -n -A6 LOOP_RUNS_DIR tools/loop/loop.conf`, `bash tools/check-portability.sh --quiet` clean (the default
  is derived, not a project path), and `state#defaultsComeFromLoopConf` / `state#projectKeysAreReadFromLoopConf`
  unaffected.

## 3. The documents agree with the code

- [x] 3.1 `.pi/skills/fix-loop/SKILL.md`: the `state.sh` row of the owner table names the archive and its
  directory; the report section states that `init` keeps the state it replaces (`runs/<run.startedAt>.json`, or
  `LOOP_RUNS_DIR`), that `report.sh --state <file>` reports on it, that an unstopped run is announced and never
  refused, and quotes the budget line's wording. **Parent:** all four delta scenarios. **Verification:** read
  back against `report.sh` and `state.sh` — the wording the skill quotes is the string the case asserts.
- [x] 3.2 `.pi/skills/fix-loop/REFERENCE.md`: a new section "The run's state, and the state it replaces" states
  the state file's role, the archive's name and place, the announcement, the `--state` invocation and the wording
  of the budget line, with the 2026-10-20 measurement behind it. **Parent:** all four delta scenarios.
  **Verification:** `grep -n 'LOOP_RUNS_DIR\|budget left unknown' .pi/skills/fix-loop/SKILL.md
  .pi/skills/fix-loop/REFERENCE.md tools/loop/loop.conf` names the same rule in all three.
- [x] 3.3 `guidelines/*.md` need no edit: `Backlog.md` §11's key table does not list `LOOP_RUNS_DIR`
  (`grep -n 'RUNS_DIR' guidelines/*.md` — no match), and no guideline describes the loop's state file or its
  report; `AGENTS.md` routes them to `.pi/skills/fix-loop`. Named here so the absence is deliberate. **Parent:**
  "The run's mechanics are the project's configuration, not its code". **Verification:** the grep above.

## 4. Verification (offline; this change compiles nothing)

- [x] 4.1 Structural check instead of a build: no Kotlin, Java, C++, Gradle, manifest or resource file is
  touched, so `guidelines/Build.md` §2's scope block applies and the structural check is `bash -n` on the three
  edited shell files plus the self-test gate for `tools/loop/**`. **Verification:**
  `bash -n tools/loop/state.sh tools/loop/report.sh tools/loop-cases.sh`. **Exclusion:** no Gradle build and no
  JVM suite is run, because nothing they compile is touched.
- [x] 4.2 The four new cases, green on the fix: `bash tools/loop-selftest.sh --case '<each case>'` —
  `evidence/green/`.
- [x] 4.3 The two existing cases that own this surface stay green: `state#secondRunRefusesWithExit5` (its second
  run now archives the first run's in-flight state before refusing on the lock, and still writes no label) and
  `report#headerAndTableShape` (the default report's header, table and branch list) — `evidence/green/`.
  **Exclusion:** the whole offline suite is **not** run here; the orchestrator runs it once on the merged tree,
  and no full-suite tally is quoted as green by this change.
- [x] 4.4 Falsification: the four cases run against the engine taken from HEAD (`git archive HEAD tools/`) fail
  for the reasons quoted in tasks 1.1–1.4 — `evidence/red/`. No further revert-check is owed: the archive, the
  announcement and the wording *are* the fix, not an invariant added beside the original symptom (config `tasks`
  rule for `fix-*` changes).
- [x] 4.5 `bash tools/check-portability.sh --quiet`, `openspec validate fix-loop-run-state-archive --strict` and
  `openspec doctor`, all clean. **Verification:** their output in `evidence/green/validation.txt`.
