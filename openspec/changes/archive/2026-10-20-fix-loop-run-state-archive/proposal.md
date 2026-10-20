# fix-loop-run-state-archive

## Why

`tools/loop/state.sh` `init` writes `$LOOP_STATE_FILE` in place (`write_state`, which `mv`s a temporary file over
the state). The file is the run's only record: its budget, its queue, every finished item with its verdict and
metrics, and the stop reason `next` recorded (`cmd_stop`, issue #184). A run that replaces it therefore destroys
the previous run's record, and nothing else keeps one.

Measured 2026-10-20 (issue #207): `tools/loop/report.sh` printed

```
closed 0 · skipped 0 · blocked 0 · budget left 2 items / -422 min · spent 452 min
```

about the 100-item / 480-minute run under review (base `5d65687`) — because a later 30-minute run had
re-initialised the same file. That run left no counters anywhere: the number of items it attempted, its
exclusions and its stop reason had to be reconstructed from the console transcript and from
`.pi/issue-loop/items/*`. Two defects are visible in that one command, and both are the same one: **a number
is read from a record that does not describe the run being reported.**

- the state was overwritten, so there is nothing to report from; and
- the budget arithmetic subtracts one clock from another run's budget — `now - run.startedEpoch` assumes the
  state's `run.startedEpoch` is the start of the run in front of the reader, which is exactly what an
  overwritten state is not, and the difference came out negative.

**Root cause and its evidence:** `tools/loop/state.sh` `cmd_init` writes over `$STATE_FILE` with no copy and no
word about what it replaces (the `write_state` call at the end of `cmd_init`); `tools/loop/report.sh` computes
`spent_min=$(( ( $(date +%s) - run_epoch ) / 60 ))` and then prints `$((minutes - spent_min))`, so a state whose
`run.startedAt` predates the report by 452 minutes prints `budget left 2 items / -422 min` whatever run it
belongs to. The four red cases in `evidence/red/` fail on HEAD `8461bd2`; the fourth quotes the impossible line
verbatim:

```
closed 0 · skipped 0 · blocked 0 · budget left 2 items / -422 min · spent 452 min
```

**The single fix:** `init` copies the state it is about to replace to `runs/<run.startedAt>.json` beside the
state file (`LOOP_RUNS_DIR` in `tools/loop/loop.conf` moves that directory, and the default is derived from the
state file, never a project path), announces on stderr — without refusing — when that state recorded no
`run.stop.reason`, and `report.sh` gains `--state <file>` to report on a kept run. The report's budget line
subtracts the spend of the run whose state it reads: to `run.stop.at` once the run recorded its end, and to the
clock only while the window `run.startedEpoch` + `run.minutes` is open; otherwise it prints
`budget left unknown: these numbers belong to another run — its window closed at <deadlineAt> and it recorded no
stop`.

**Spec requirement the defect contradicts:** `agent-issue-loop`, "One command starts a budgeted run, with no
dialogue" — its scenario "A run is recorded" promises that a report exists naming each item, its verdict, its
wall time, its review rounds and what it closed. A run whose state the next run overwrites has no such report,
and the one printed about it names another run's budget. The delta adds the requirement that makes that
promise keepable; it re-words no scenario it does not have to.

## Cases and evidence

Four cases in `tools/loop-cases.sh`, each registered in **both** the `--list` array and the dispatch map:

- `state#initKeepsTheStateItReplaces` — `init` over a finished 2-item / 90-min state keeps it at
  `<state-dir>/runs/<run.startedAt>.json` with its items and its stop reason, the new state is the one in place,
  and the kept file reports its own budget.
- `state#initAnnouncesARunWithoutAStop` — `init` over a state with no `run.stop.reason` exits 0 (a crashed run
  stays restartable) and says `never recorded a stop reason` on stderr; over a state that did record one it
  says nothing.
- `report#archivedStateIsReported` — `report.sh --state <kept file>` prints the kept run's budget, counters, stop
  reason, item row and skip after a later run has replaced the state; without `--state` the report is the run in
  place, as before.
- `report#impossibleBudgetNamesAnotherRun` — a state whose window closed without a stop prints
  `budget left unknown: these numbers belong to another run — its window closed at <deadlineAt> and it recorded
  no stop` and no negative budget; the same state, having recorded its stop 25 minutes into its window, prints
  its own numbers.

Red on HEAD `8461bd2`: `evidence/red/` (the fourth quotes `budget left 2 items / -422 min`). Green on the fix:
`evidence/green/`, which also holds the two existing cases on this surface,
`state#secondRunRefusesWithExit5` and `report#headerAndTableShape`. The whole offline suite is **not** run by
this change: the orchestrator runs it once on the merged tree.

## What Changes

- `tools/loop/state.sh` — `state_resolve` resolves the runs directory (`LOOP_RUNS_DIR`, default `runs/` beside the
  state file); a new `keep_replaced_state` copies the state about to be replaced to
  `<runs dir>/<run.startedAt>.json` (a `.N` suffix if that name is taken, never an overwrite) and announces a
  state without a stop reason on stderr; `cmd_init` calls it before writing. The file header says so.
- `tools/loop/report.sh` — `--state <file>` selects the state to report on (`--json` included);
  the budget line measures the spend of the run whose state it reads (to `run.stop.at`, or to the clock while the
  window is open) and names the run the numbers belong to when it cannot.
- `tools/loop/loop.conf` — `LOOP_RUNS_DIR`, recorded and empty by default.
- `tools/loop-cases.sh` — the four cases, registered in both places.
- `.pi/skills/fix-loop/SKILL.md` — the `state.sh` row of the owner table, and the report section: the archive,
  the `--state` option, the announced unstopped run and the wording of the budget line.
- `.pi/skills/fix-loop/REFERENCE.md` — a new section "The run's state, and the state it replaces" beside the
  worktree recipe.

## Impact

Affected files, all of them the loop's own harness — no app, native or Gradle file is touched:

| file | module / area |
|---|---|
| `tools/loop/state.sh` | the run's state, budget and lock (`init` is the only command that replaces the file) |
| `tools/loop/report.sh` | the run's report, read by the run and by a reader of a finished run |
| `tools/loop/loop.conf` | the loop's recorded configuration (`lib.sh` reads it; `state_resolve` reads the key) |
| `tools/loop-cases.sh` | the loop's offline self-test cases (`tools/loop-selftest.sh`) |
| `.pi/skills/fix-loop/SKILL.md` | the `fix-loop` skill's owner table and report section |
| `.pi/skills/fix-loop/REFERENCE.md` | the facts a run needs about its state |

No Android module, manifest, resource, native source or JNI bridge is touched: this is not a phone, car or AAOS
change, so the parity rules, the native-build rules and the `submodule patch` / `local override` decision do not
arise. The portability seam is unchanged in kind — the one new key lives in `loop.conf` and its default is
derived from the state file, and `tools/check-portability.sh` still finds 42 engines naming no project value.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **ADDED** one requirement, "A run's state outlives the run that
  replaces it", with four scenarios. No existing requirement, scenario or wording is modified: the promise in
  "A run is recorded" is what the new requirement keeps, and re-wording it would rewrite text the defect does
  not contradict.

## Additive or breaking, and rollback

**Additive.** `init` keeps a copy where it used to delete, `report.sh` gains an option and its default output
keeps its shape, and the budget line changes only in the case that used to print an impossible number. No
argument, state field, exit code or label changes; a state file written before this change is reported on
exactly as one written after it (the archive is a byte copy, its `stateDigest` included).

Rollback: revert this commit. `init` overwrites the state again, `report.sh` loses `--state`, and the runs
directory already on disk is left alone — it is machine-local, under the gitignored `.pi/`, and nothing reads it
once the option is gone. No branch, label, archived change or issue is affected, because the change writes none
of them.
