# Design — fix-loop-phase-lines

## Root cause

The run announces nothing at its own phase boundaries. Its longest single call — the pre-screen, measured 53 s
on the real backlog — was wrapped in a command substitution, so its output existed only as a variable until the
call returned and was then replayed once; and the boundaries the run crosses between its header and an item's
result line (worktree ready, item picked, gate read, commit) printed nothing of their own. The console therefore
carried no interval-bounded evidence of life outside a child process, and the header named its arguments rather
than what happens next or where to look.

# tools/loop/run.sh, before this change

```sh
prescreen_out=$(bash "$LOOP_DIR/prescreen.sh" "${prescreen_args[@]}" 2>&1)
prescreen_status=$?
printf '%s\n' "$prescreen_out"          # the first of it a terminal sees, in one burst, 53 s later
```

## The alternatives the evidence eliminated

1. **A watchdog timer for the pre-screen, in the shape `run_child` uses.** It would print the run's own guess at
   liveness while the call runs, from a second process and a stop file. Eliminated because the pre-screen already
   says what it is doing — its output carries the per-record verdicts and its own exclusion summary
   (`pre-screen: 0 eligible, 1 excluded — …`) — so a timer would add a process and a lifecycle to reproduce,
   less truthfully, information the call itself emits. The case that measures this is possible only because the
   text streams: `run#prescreenAnnouncesItselfBeforeItReturns` reads the console 1.5 s into a 3 s call.
2. **Streaming to the terminal only, dropping the capture.** Eliminated by the pre-screen's second consumer: the
   dry-run listing and a person reading the transcript after the fact both need the text once the terminal has
   scrolled. `tee` gives both from one pipeline, and the state directory is already where a run writes what it
   wants to keep.
3. **A run-level heartbeat on a cadence, independent of the phase.** Eliminated because the only two waits that
   are long are the pre-screen (streamed here) and a child (already reported by the archived change
   `2026-10-20-fix-loop-progress-reporting`), and a run-level timer would have to be stopped on every exit path
   — the dry run, each refusal, a signal — for a line that says less than the phase's own.
4. **Rewriting the child's three lines into the phase format.** Eliminated because that requirement is already
   recorded in `agent-issue-loop` ("A run reports the wait it cannot get from the child") and its cases assert
   those exact lines (`run#childReportsWhileItWaits` asserts `loop: worker-prompt started`, `running`,
   `finished in`, `exit 0`). The format is unified where it is free — `fmt_elapsed` moves to `lib.sh`, so both
   kinds of line render an elapsed time the same way — and no recorded line is reworded.
5. **Carrying the progress into the state file or the report.** Eliminated by the issue's own constraint and by
   the loop's invariant that the report is derived from the state: a field that only a console line writes would
   make two runs with identical decisions record differently, and would give a reviewer a number to trust that
   no phase produced.
6. **A `--verbose` flag.** Eliminated because the defect is that the default is silent: a flag would have to be
   known and typed by the person who does not yet know whether the run is alive, and it would leave the default
   path — the one the two interrupted runs of 2026-10-19/20 took — unchanged.

## The chosen shape

`tools/loop/lib.sh` gains one function and one global:

```sh
LOOP_PROGRESS_STARTED="$(date +%s)"

progress() { # progress <phase> <what> — one line wherever the run crosses a boundary it can observe itself
  local phase="$1"; shift
  printf 'loop: %s · %s · %s\n' "$phase" "$(fmt_elapsed $(( $(date +%s) - LOOP_PROGRESS_STARTED )))" "$*"
}
```

`fmt_elapsed` (`37s`, `4m12s`) moves from `run.sh` into `lib.sh` beside it, so the phase line and the child line
render elapsed time identically; `run_child` is untouched otherwise. The elapsed time is the run's own age,
counted from the process reading the library, and not a phase's duration — a phase line answers "how long has
this run been going", which is the question a person watching asks.

The pre-screen is streamed instead of replayed:

```sh
prescreen_status=0
(
  set -o pipefail
  bash "$LOOP_DIR/prescreen.sh" "${prescreen_args[@]}" 2>&1 | tee "$prescreen_log"
) || prescreen_status=$?
```

Three properties are load-bearing and each is a line of the delta rather than a habit:

- the pipeline's status is **the pre-screen's own**: `|| prescreen_status=$?` captures it, and `pipefail` — which
  the run sets for itself at the top of `run.sh` and repeats in the subshell, so the line that needs it says so —
  makes the pipeline's status the pre-screen's rather than `tee`'s. Without the capture a failed pre-screen reads
  as a successful one, which is the one way this change could alter a decision;
- the captured text does not disappear: it is `<state dir>/prescreen.log`, beside the state file, which is where
  a run already keeps what outlives its console;
- the substitution that produced the variable, and the `printf` that replayed it, are both gone; keeping them
  would print the whole pre-screen twice.

The five boundaries print one line each: `pre-screen` (start, and end with its exit status), `worktree` (ready,
with the tree, branch and base), `item` (picked, naming `<state dir>/items/<N>/worker-report.md` — the file a
person can read while the child that writes it is still running), `gate` (the number of `test-results` XML the
run found in the tree) and `commit` (`end-item` returned, with the new HEAD and the base). The header is
followed by one line naming the next step, the state directory and `bash tools/loop/state.sh show`.

Nothing else moves: no state field, exit status, verdict, report cell or prompt changes, and no line is read
back by any script (`progress` has no caller that consumes its output — it writes to stdout like the run's other
console lines, and the state file remains the run's record).

## Threading, state and lifecycle

Single shell, no new process. `progress` is a `printf`; `tee` is one process for the duration of one call.
`LOOP_PROGRESS_STARTED` is a variable of the run's own shell, so it survives the whole item loop and is not
inherited into a child (the loop's children get their identity from `LOOP_STATE_FILE`/`LOOP_WORKTREE`, which are
unchanged). The dry run returns before the item loop and prints the pre-screen's streamed output like any other
run, which is what its case asserts.

## Risk

- The pre-screen's exit status is the only thing this change could break. It is asserted by
  `run#prescreenAnnouncesItselfBeforeItReturns`, whose second half runs a stand-in pre-screen that fails with 7
  and requires the run to leave with 7, and it is falsified by `evidence/mutation-status-capture.txt`: with the
  `|| prescreen_status=$?` capture removed from a copy of the engine that case fails (`expected '7', got '0'`).
  The same file records the honest limit of that measurement: with only `pipefail` removed and the capture kept,
  the case still passes, so the capture is what the case guards and the `set -o pipefail` in the subshell stays a
  statement of the dependency rather than a claim that a test measures it.
- `tee` is a POSIX tool the loop already assumes (`report.sh` and the cases pipe constantly); a host without it
  could not run the suite either.
- A case that reads the console mid-flight can be flaky on a starved host: it holds the pre-screen for 3 s and
  reads at 1.5 s, so the margin is a factor of two, and the assertion is on a line printed *before* the sleep
  begins.

## Blast radius

`tools/loop/lib.sh`, `tools/loop/run.sh`, `tools/loop-cases.sh`, `.pi/skills/fix-loop/SKILL.md` and this change.
No app module, resource, manifest, stylesheet or native source; no phone or car variant, so `guidelines/UI.md`
§1 does not apply. No guideline text changes: `guidelines/Build.md` §2 already owns the verification shape this
delta follows, and `guidelines/Logging.md` §1–§3 is about the app's lines, not the loop's console.

## Rollback

Revert those four files. A run then captures the pre-screen whole and prints no phase line, its state file is
byte-identical to this change's, and no consumer notices — nothing reads a progress line.

## Verification

The offline shell suite is the gate that owns `tools/loop/**` (`guidelines/Build.md` §2's scope block; the same
shape the archived change `2026-10-20-fix-loop-cut-review-answer` recorded). Each of the three new cases fails on
the base (`evidence/red-phase-lines.log`) and passes with the change (`evidence/phase-lines-cases.log`); the
whole suite is run once over the final tree (`evidence/selftest.txt`). No Gradle and no device: the delta touches
no Kotlin, Java, C++, manifest or resource file, so `./gradlew test -PforceTests --no-build-cache` is excluded
and the reason is stated rather than implied.
