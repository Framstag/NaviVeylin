# Design

## Root cause

The worker's task names the gate and no reading command (`tools/loop/prompts/worker.md`, its placeholder list),
so the facts an item needs before it plans are gathered one call at a time: the pathspec, the checks that
pathspec owes (`gate.sh:74-88`), the tallies the evidence already holds, and the delta's scenarios against
their cases. Measured over the five items the issue names, an item spends 73 to 244 shell calls and 22 to 79
file reads, of which 8 to 49 are a second read of a path it already read — one model turn each.

## The single fix

1. `tools/loop/measure.sh` — a reading command that prints one block: `LOOP-MEASURE base=… paths=… insertions=…
   deletions=…`, one `LOOP-MEASURE-PATH` per touched path, one `LOOP-MEASURE-CHECK` per command the pathspec
   owes, one `LOOP-MEASURE-TALLY` per tally the gate's directory and the change's `evidence/` already carry,
   `LOOP-MEASURE-CHANGE <name> scenarios=<n> cases=<n> unnamed=<n>`, `LOOP-MEASURE-SURFACE`, and
   `LOOP-MEASURE-END`. It runs nothing: the owed checks come from `gate.sh --plan`, the same call the gate
   makes, and the tallies are read, never produced.
2. `tools/loop/loop.conf` — `LOOP_MEASURE_CMD`, the project's value, rendered into the task as
   `{{MEASURE_CMD}}` beside `{{GATE_CMD}}`; the engine names no path of its own (D16).
3. `tools/loop/prompts/worker.md` — the section that hands the command over and states the two rules it
   carries: the block is what is read, not a walk of the tree, and a file read once is cited, not read again.
4. `tools/loop/gate.sh` — the profile's suite map is read from `printf '%s\n'`, so its last pair survives. The
   block's contract ("every check the pathspec owes") is false without it; measured, an 18-pair value
   iterated 17 times and a 2-pair value once.

## Blast radius

`tools/loop/measure.sh` (new, ~120 lines), `tools/loop/loop.conf` (+1 key and its measurement),
`tools/loop/prompts/worker.md` (+21 lines), `tools/loop/run.sh` (the render's placeholder at two call sites),
`tools/loop/gate.sh` (one line and its reason), `tools/loop-cases.sh` (three cases, one existing case's
configuration substituted and asserted), `tools/loop-mutations.sh` (mutation `39.1`). No compiled file, no
device: the whole delta is the shell gate's.

## Rollback

`git revert` of the change's commits. The new key and the new file are additive; the only edited behaviour is
`gate.sh`'s trailing newline, which its own case (`gate#theDeltaSelectsTheSuitesItTouches`) and
`measure#namesTheOwedChecksInOneBlock` both exercise.

## Alternatives the evidence eliminated

- **Duplicating the pathspec→checks mapping inside `measure.sh`.** Eliminated: the block would answer a
  question the gate answers, and the two mappings drift — the profile is the one place the mapping lives
  (`gate.sh`'s header says so, and `run#engineNamesNoProjectFact` is what enforces the other half).
- **Letting `measure.sh` run the owed checks.** Eliminated: the gate is run once per item, by
  `LOOP_GATE_CMD`, and `worker.md` already says a gate is evidence rather than a thing to retry; a command
  that runs the suites would also have to write the tallies the run reads, which is the gate's job. So the
  block names and the gate runs.
- **Teaching the prompt to read the profile itself.** Eliminated: a project value in a template is exactly
  what `tools/check-portability.sh` forbids (`check-portability.sh:37-42`), and the prompt already receives
  every project value through a placeholder.
- **Making the block the only entry point (a single "work" command).** Not a fix for this item: it would
  remove the worker's ability to run one focused suite while iterating, which `Build.md` §2 rule 2 requires.
