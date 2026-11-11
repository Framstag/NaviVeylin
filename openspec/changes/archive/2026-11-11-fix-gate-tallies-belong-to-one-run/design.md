# Design — a gate's tallies belong to the run that wrote them

## Root cause

`tools/loop/gate.sh:554`–`566` writes each step's tally into `$tally_root`, the single path
`LOOP_GATE_TALLY_DIR` names (`tools/loop/loop.conf:702`). Nothing in the gate scopes that path to the run writing
it: there is no claim for the length of a run and no clearing of what an earlier run left, and the file name is only
the command (`name=$(printf '%s' "${cmd#./}" | tr '/ ' '__' | cut -c1-80)`), which is the same string in every run.
A carrier is therefore overwritten only by a run that happens to owe that same step — and every other one survives.
The self-test suite refuses exactly this collision for itself (`tools/loop/tests/loop-selftest.sh:354`,
`.loop-selftest.lock`, "one suite per repository", issue #295); the tally directory had no such rule.

## The fix (one mechanism)

Make the directory one run's, in the two places a run can lose it:

1. **The run claims the directory.** Before the first step, `gate.sh` takes `$tally_root/.gate.lock` — `mkdir` (the
   atomic half the suite already uses), the owner's `pid` and UTC start time in `owner` beside it, the same
   live-owner refusal and the same dead-owner takeover. A second gate of the same tree therefore refuses by name
   (`LOOP-GATE-REFUSED:`, exit 4) before it runs a step, and a gate that was killed leaves a lock the next run takes
   over with a line saying whose it was. The lock is released on `EXIT`, beside the step-output directory.
2. **The run clears what is not its own.** Having claimed the directory, the gate removes every `*.xml` in it, so a
   carrier of a run that died, or of the run before, cannot be read as this one's. Each tally it then writes carries
   `run="<pid>"`, and the console names the directory and the run (`LOOP-GATE-TALLY: <dir> run=<pid>`), so a copy made
   mid-run can still be told apart from another run's without its mtime.

No other file changes: `measure.sh`'s `tally_dir` and the gate child's evidence copy read the directory whole, which
is now exactly the steps the gate ran.

## Why not the alternatives

- **A per-run subdirectory** (`$tally_root/run-<id>/`): every reader would have to resolve which run it is reading —
  `measure.sh`'s `find "$dir" -name '*.xml'` (`tools/loop/measure.sh:132`) sums *all* of them, so a tree would
  accumulate every run's tallies and the block would report their sum; the gate child's copy step would need the
  run's name from the log; and every archived pointer to a tally's path would become run-dependent. It moves the
  problem to each reader instead of removing it.
- **A marker file per step** (`<tally>.run`) with a reader that filters by marker: leaves the directory holding
  another run's carriers, so the reader that copies it (the defect's actual cost) still copies them unless it learns
  the filter too. The marker is worth having *inside* the tally — it costs one attribute — but only the sweep makes
  the directory one run's.
- **Naming the run in the file name** (`<name>.<pid>.xml`): the file names every existing case and archived pointer
  reads would change per run, and the directory would still accumulate.
- **Nothing but the lock**: a lock stops two *concurrent* gates, not a carrier a *previous* run left — the observed
  failure was the second kind, so the sweep is the half that answers it.

## Blast radius

- `tools/loop/gate.sh` — one lock pair of functions, one sweep, one attribute, one console line, one exit code.
- `tools/loop/tests/loop-cases.sh` — two cases and two dispatcher lines.
- `tools/loop/tests/loop-mutations.sh` — two records.
- `guidelines/Backlog.recipes.md`, the section "The gate and its suite" — one paragraph.
- No compiled file, no Gradle task, no profile key: the directory, its owner record and its lock are the engine's,
  the path stays the profile's.

## Rollback

Revert the four files. The lock and the sweep leave nothing behind: the lock lives inside a directory the build
already owns and is removed on exit, and the sweep removes only carriers the gate would have overwritten or
shadowed anyway. A tree that keeps running the old gate reads a directory the new one left holding one run's
carriers, which is what the old readers wanted.

## Failure modes the fix keeps visible

- A step that overruns still names itself in its own tally's `LOOP-GATE-STEP:` line and is counted as an error — the
  sweep does not touch a line the current run writes after it.
- A gate refused by the lock writes no `LOOP-GATE:` line, so it cannot be read as a green one; the run's own check
  for a green claim finds no fresh tally and refuses, exactly as it does for the runs-max refusal (`exit 3`).
