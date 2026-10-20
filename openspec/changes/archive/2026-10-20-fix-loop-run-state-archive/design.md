# design — fix-loop-run-state-archive

## The root cause, and what it leaves as the only shape of the fix

One state file is written by one function (`write_state`, which `mv`s a temporary file over
`$LOOP_STATE_FILE`) and called by one command that ends a run's record: `cmd_init`. The report then reads that
one file and subtracts the current clock from `run.startedEpoch`. Both halves of the measured symptom come from
that single fact:

- after a later run starts, the earlier run's record does not exist anywhere (`evidence/red/` case
  `state#initKeepsTheStateItReplaces`: `the replaced state was not kept at …/.pi/issue-loop/runs/…json (found: )`);
- and the report still computes a spend, from a `run.startedEpoch` that now belongs to a different run, so it
  prints `budget left 2 items / -422 min · spent 452 min` (`evidence/red/impossibleBudgetNamesAnotherRun.txt`).

The fix is therefore not "add a feature": it is to make the record outlive its replacement, and to make the
arithmetic refuse to speak for a run it does not hold. The alternatives below are the ones the evidence
eliminates — not a menu this change picked from.

## Alternatives the evidence eliminates

### Refuse to `init` over an existing state

Impossible. The loop exists to be restarted: a run that was killed (`run#childOutlastingTheWallClockIsBlocked`,
`labels#trapRemovesInProgressOnSignal`) leaves a state with no stop reason, and a refusal would make exactly the
case restart matters for unstartable without a hand-written flag or a hand-deleted file. It also moves the
decision to the caller — and the caller is `run.sh`, which cannot ask (D2: "a script cannot ask"). The measured
consequence in the red evidence is the same refusal-shaped failure the loop already had to fix once: an
unstartable run reads as a broken harness, not as a guard.

### Append every run's record to one log file

Impossible. The record is a JSON document the reader resolves by field path (`.items[]`, `.run.stop.reason`,
`.queue`); an appended log is either not JSON or is a stream the reader would have to split, and it grows
without bound inside `.pi/` — a 100-item run's state is tens of kilobytes, and the tool would have to grow a
rotation policy, a retention rule and a reader for it. It also mixes runs in one file, which is the defect: the
report would again be reading numbers whose run it cannot name.

### Let the caller pass the path to keep the old state at

Impossible. `init` is called by `run.sh` (`loop_state init "${init_args[@]}"`, line 103) and by the cases; no
caller has a name to offer — the wrapper does not know the previous run's start time, and a caller-chosen name
would have to be invented, which is the second run naming the first run's record. `report.sh --state` is the
opposite direction of the seam and is kept: the *reader* may name a file, the *writer* may not.

### Keep a ring of the last N states under one name

Impossible. A fixed name (`run.prev.json`, `run.1.json`) is not addressable: a reader cannot tell which run it
holds, and the run under review was three runs old by the time it was asked about. A ring also silently drops
the oldest record, which is the record a review of a long run needs.

### Commit the state, or move it into the change's evidence

Impossible. The state is machine-local by rule (`state#initAndShowRoundTrips`: "the state file is not tracked or
reported", `git check-ignore`), and it carries the run's queue and base commit — a committed state would be a
tracked file the next run rewrites, and an archived change's `evidence/` is written by a child and reviewed
before it exists for most of a run. The runs directory therefore stays under `.pi/`, gitignored with the state
it copies.

### Make the report print the item count only, and drop the clock entirely

Impossible as a *fix*: it would remove the number a reader asks for first ("how much of its clock did the run
spend"), and it would still print a wrong header for a run whose state is gone. The clock is meaningful exactly
when it is measured against the run in hand — which is what the wording below makes explicit. It would also
have left the first half of the defect (the lost record) untouched, and one change fixes one defect.

## The chosen design

### The archive: beside the state, named by the run's own start time

`state_resolve` resolves a runs directory once: `LOOP_RUNS_DIR` from `loop.conf` when the project records one
(relative values are relative to the project root, as every other path the loop takes from its environment is),
and otherwise `runs/` **beside the state file**. `keep_replaced_state` in `state.sh` runs before `write_state` in
`cmd_init`, copies the existing file with `cp` and names it `<run.startedAt>.json`.

- **Beside the state, not at a fixed project path.** The engine names no project fact (D16,
  `tools/check-portability.sh`): `.pi/issue-loop/` is this project's default in `lib.sh`, and a state file the
  self-test or another project relocates with `LOOP_STATE_FILE` must take its archives with it.
- **Named by `run.startedAt`, not by the time of the copy.** The state already carries the fact, both runs are
  then distinguishable without a clock, and the name is what the report's header shows a reader. `run.startedAt`
  is written by the same `init` and is immutable for the run's life, so two runs never claim one name; a state
  without it (hand-written, or older than the field) falls back to `undated-<epoch>`.
- **Never overwrite an archive.** Two runs started inside one second would collide; the copy takes a `.N` suffix
  instead, so an archive that exists is always the file that was kept.
- **A copy, not a move.** The state in place must not be absent for the instant between `keep` and `write` — a
  reader (`report.sh`, a child's `state.sh get`) racing the run would see nothing.

### The announcement: stderr, one line, and no refusal

`keep_replaced_state` asks `jq -e '.run.stop.reason // empty'` on the file it just copied; a missing, empty or
unparseable reason means the replaced run never said why it ended, and the line names the archive it went to.
Announcing rather than refusing is the restartability argument above; announcing rather than staying silent is
the other half of the defect — a killed run's state was discarded without a word, and a reader had no way to
know that the file they were about to read was about to be someone else's. The warning is stderr and not stdout
because `run.sh` prints `init`'s stdout into the run's own log, and a normal run must not look like a warning.

### `report.sh --state <file>`

`--state` names the state to read, relative to the project root when it is relative; every read in the report
uses it, including the child that renders the item table (`LOOP_STATE_FILE="$state_file" state.sh report`), and
`--json` reports the same file. Without the flag the report is `$LOOP_STATE_FILE` — the run in place — so the
default behaviour, and every caller in `run.sh`, is byte-for-byte what it was.

### The budget line: a spend belongs to the run whose state supplies the budget

Three facts decide which number is printable:

1. `run.stop.at` present → the run ended, and its spend is `stop.at − run.startedEpoch`. This is the run's own
   fact and stays true however long ago it ended, which is what makes a report of a *kept* run meaningful.
2. no stop, and the clock is inside `run.startedEpoch + run.minutes` → the run is in flight, and
   `now − run.startedEpoch` is its spend.
3. no stop, and the window has closed → the elapsed time is not this run's spend. The line then reads

   ```
   budget left unknown: these numbers belong to another run — its window closed at <run.deadlineAt> and it recorded no stop
   ```

   and prints no negative number.

Case 3 is the measured one: a 30-minute state reported 452 minutes after its start is either a run that
stopped (then it recorded a stop — case 1) or a state that belongs to a run which never ended, in which case the
clock measures the tree, not the run. A run that overran its own deadline *and* recorded its stop falls under
case 1 and reports its real overrun, which is a truth about that run and not an impossible number; the fabricated
state in the case is built coherently (stop 25 minutes into a 30-minute window) so the arithmetic is pinned
rather than the fabrication.

- **Why not clamp at zero** (`budget left 0 min`): a clamp hides which run the numbers came from, and a reader of
  the 2026-10-20 report had no way to tell a spent budget from a state that had been overwritten.
- **Why not the item walls alone** (`spent = sum(metrics.wallSeconds)`): the run's clock includes the pre-screen,
  the gate and the waits between children, and a state written before `--wall` existed would report zero spend.
  The clock is right for a live run; the recorded stop is right for a finished one.

## Risk

- **A state directory that cannot be written.** `init` calls `loop_die` (`exit 2`) instead of writing a state it
  could not keep a copy of: refusing to start is the loud failure, silently losing the record is the defect.
  Only a permissions or disk problem reaches it.
- **Archive growth.** One state per run, tens of kilobytes, inside the gitignored `.pi/`; nothing prunes it, and
  nothing needs to — a run is a deliberate act, and the archives are the evidence of what it did. A project that
  wants them elsewhere records `LOOP_RUNS_DIR`.
- **The `--state` path is not validated against the runs directory.** A reader may report on any state file, which
  is what makes the case (and a hand-saved state) possible; the report is read-only, so a wrong file reports the
  wrong run loudly (its budget, its base, its stop) rather than damaging anything.
- **The wording is pinned by a case**, so a later re-wording must move the case with it; that is the point of
  pinning it.

## Threading and lifecycle

None to state: this change is three shell scripts, run one at a time, with no thread, no dispatcher and no
lifecycle beyond the process. The concurrency that exists is the run's own — `state.sh` is the state's only
writer, and the archive is written by the same process that would otherwise overwrite the file.

## Verification

- The four new cases, red on HEAD `8461bd2` and green on the fix: `evidence/red/`, `evidence/green/`.
- The two existing cases on this surface, green: `state#secondRunRefusesWithExit5` (a refused second run no
  longer leaves the first run's state unrecorded, and the case's own assertion — the refused run wrote no label —
  is unchanged) and `report#headerAndTableShape` (the default report's header, table and branch list).
- Structural, not a build: `bash -n` on the three edited scripts, `tools/check-portability.sh --quiet` (the
  engine still names no project value; the new key's default is derived from the state file), `openspec validate
  fix-loop-run-state-archive --strict` and `openspec doctor` — `evidence/green/validation.txt`.
- No Gradle build, no JVM suite and no device step: no compiled file, no resource and no UI is touched.
- Falsification is the red run itself: the engine taken from HEAD fails all four cases for the reasons quoted
  above. No revert-check is owed beyond it — this change adds no invariant beside the original symptom; the
  archive, the announcement and the wording *are* the fix (config `tasks` rule for `fix-*` changes).
