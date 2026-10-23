# Design — the profile beside the engine, and a log that says which one

## Root cause

`tools/loop/run.sh:66` exported the run's resolved configuration, `LOOP_CONF="${LOOP_CONF:-$LOOP_DIR/loop.conf}"`,
and `LOOP_DIR` is **run.sh's own** engine directory — the project the run was started from. Every child inherits it,
and `tools/loop/lib.sh:116` prefers an inherited `LOOP_CONF` over the profile beside the running engine. A child
works in the worktree (`tools/loop/run.sh:680`) and runs the worktree's engine (`LOOP_GATE_CMD`, `loop.conf:139`), so
its gate resolved the project's profile while measuring the worktree's paths. One mechanism, one symptom: the gate
of a change that edits `loop.conf` measures the rule the change replaces.

## The single fix

Two halves of one rule, both already stated by the engine's own header — `tools/loop/lib.sh:9`–`12`: the project is
discovered, never told, and `LOOP_CONF` "exists so the self-test can exercise the configuration seam".

1. **The seam stays a seam.** `lib.sh`'s `loop_resolve` records the file it sourced in `LOOP_CONF_PATH`; the default
   remains `$LOOP_DIR/loop.conf`, the profile beside the engine that is running.
2. **The run stops promoting its own profile.** `run.sh` reads `LOOP_CONF` as before and then takes it out of its
   children's environment (`export -n LOOP_CONF`). A child that works in another tree resolves the profile beside
   that tree's engine.
3. **The log attests it.** `gate.sh` writes `LOOP-GATE-PROFILE: <path>` before it decides what is owed, and names a
   profile read from outside the engine's own tree as pinned beside the one the engine ships.
   `measure.sh` writes the same fact as `LOOP-MEASURE-PROFILE`.

The pin is written only when the gate is going to run something: `--plan` prints the commands a delta owes and
nothing else, because `measure.sh` reads that output as its check list.

## The alternatives the evidence eliminated

- **Read both profiles, print the delta, refuse when they differ.** Eliminated: the two profiles differing is not an
  error, it is the case the item exists to make workable. A run whose worker edits `loop.conf` differs by
  construction, so a refusal turns every such item into `blocked` — the state #273 had to be rescued from by hand.
  The difference is worth naming, not refusing, and the pin line names it.
- **Document the pin (`LOOP_CONF=<worktree>/tools/loop/loop.conf` in the worker prompt).** Eliminated by the item's
  own measurement: the round's log is written by the engine, and a worker that forgets the pin writes a green log
  attesting the wrong rule. The prompt is the one place the rule would live that a fresh clone or another agent may
  not read.
- **Let `run.sh` export the profile only for the measure command and not for the gate.** Not implementable: both are
  children of the same agent process, and the environment is inherited whole. The two commands are also the two
  readers of the profile, so they must agree; making the child resolve it once, beside its own engine, is what keeps
  them consistent.
- **Unset `LOOP_CONF` in `gate.sh` instead.** Eliminated: the gate is also run by hand, and the archived #273 change
  itself records a hand-pinned re-run as the repair. A gate that ignores a pin cannot be re-run that way.

## Blast radius

- `tools/loop/lib.sh:39`–`42`, `:120`–`122` — the global and the one assignment.
- `tools/loop/gate.sh:56`–`74` — the guard, the two pin lines, and the comment that says why `--plan` stays pure.
- `tools/loop/run.sh:63`–`71` — one export replaced by `export -n`, and the empty-agent refusal naming
  `LOOP_CONF_PATH` at `:99`.
- `tools/loop/measure.sh:45`–`48` — one `LOOP-MEASURE-PROFILE` line.
- `tools/loop-cases.sh` — two cases and their declarations.
- `tools/loop-mutations.sh` — records `52.1`–`52.3`.
- the delta `specs/agent-issue-loop/spec.md`, and one paragraph of `guidelines/Backlog.md`, in its section "The loop".

What changes for a reader: a gate's log gains one line at its top, and the profile it names is the one beside the
engine the gate ran from; a worker's measure block gains `LOOP-MEASURE-PROFILE`. Nothing else in either output moves:
the pathspec, the plan, the suites, the numbers, the tallies and the exit statuses are the current code's.

What changes for a child: its environment no longer carries `LOOP_CONF`. Every engine script the loop ships
resolves its own profile from its own directory, so a child's engine reads the tree it was started in. The self-test
still sets `LOOP_CONF` for the scripts it calls directly, which is the seam's purpose.

## Rollback

Revert the four engine hunks (they are independent of every other change in the tree), drop the two cases and their
three mutation records, and delete the delta. The spec's requirement leaves with the delta; `guidelines/Backlog.md`'s
paragraph is the one sentence to remove. No state, no persisted file and no tally name depends on this change.
