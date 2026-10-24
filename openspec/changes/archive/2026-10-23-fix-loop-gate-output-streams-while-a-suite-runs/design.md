# Design — the gate's log moves while a suite runs

## Root cause

`tools/loop/gate.sh:111` (HEAD) read

```bash
  out=$($cmd 2>&1)
```

Two consequences follow from that one line, and both are the symptom: the console sees the suite's bytes only
after the command exits (so a suite's whole wall clock is silence), and the only bytes that outlive the run are
the three the tally keeps (`tail -3`, HEAD `tools/loop/gate.sh:133`), so a red suite's failure text is lost with the variable.
One mechanism, one fix.

## The fix

One `mktemp` file per gate run, and the suite is run through it:

```bash
  if [ "$quiet" -eq 1 ]; then
    $cmd >"$stream" 2>&1
    rc=$?
  else
    $cmd 2>&1 | tee "$stream"
    rc=${PIPESTATUS[0]}
  fi
  out=$(cat "$stream")
```

Everything downstream — the `<name>: all N cases passed` / `M of N cases failed` parse, the tally's numbers,
the `tail -3` system-out — reads `out`, which is now the file's content, so the tally and the console are one
observation rather than two. The exit status is taken from `${PIPESTATUS[0]}` explicitly: `set -o pipefail` is
already on in this file, but naming the answer keeps it the suite's even if the pipeline is later edited to
end elsewhere. `--quiet` keeps the console for the verdict and writes the same file, so the tally is unchanged
in both modes.

The trap removes the file on every exit path, and a `mktemp` failure is a `loop_die`, not a fallback to the old
capture: a gate that cannot keep what it prints is the defect this change removes.

## Blast radius

- `tools/loop/gate.sh` — the run loop only. The selection (`--plan`), the "nothing owed" path, the tally format,
  the tally directory and the final `LOOP-GATE:` line are untouched; the numbers they carry are the same.
- Consumers of the gate's stdout: the worker, which quotes the `LOOP-GATE:` line into its report, and the
  mutation harness's `--check`. Neither parses anything but that line, and it is still the last thing printed.
- The tally XML: same name, same `tests=`/`failures=`, same `<system-out>` tail. The run's check for a green
  claim reads exactly what it read before.
- `tools/loop-cases.sh` gains two cases and `tools/loop-mutations.sh` one record; no existing case or fixture
  asserts the capture, so none is realigned.

## Rollback

`git revert` of the one commit, or by hand: restore `out=$($cmd 2>&1)` in `tools/loop/gate.sh`, drop the two
cases, the dispatch entries and record `46.1`, and remove the guideline sentence. No state, no config key and
no evidence format depends on the stream.

## Alternatives the evidence eliminated

1. **A heartbeat: print `gate: <suite> still running, <n> s` on an interval.** Rejected. It proves the gate is
   alive but not which case a failure is in — and the item's own session needed the second half: a build that
   died at configuration in 21 s was found only by reading the Gradle daemon log. A heartbeat also needs a
   second reading of the suite's state to keep the tally honest, which is the two observations this change
   removes.
2. **Keep the capture and additionally `tail -f` a log file.** Rejected: nothing writes a log file while the
   output is in a shell variable; the file has to exist first, which means the suite's stdout has to go to it,
   which is the fix.
3. **Stream to the console only, and re-read the gate's own stdout for the tally.** Rejected: the gate's stdout
   is a pipe to the caller, so re-reading it means a second process, a second copy of the bytes and a
   disagreement the moment either is truncated.
4. **Stream to a log the run keeps as evidence.** Rejected for this delta as a second concern (where a gate's
   log lives, and how long it is kept); the tally the run already requires stays the evidence, and the console
   is what the operator reads. The worker's own log carries the stream when it is redirected.

## The one decision this change does not make

How long the gate may run stays `LOOP_CHILD_MAX_SECONDS` (45 min) in the run, unchanged: this delta makes that
bound observable, it does not move it.
