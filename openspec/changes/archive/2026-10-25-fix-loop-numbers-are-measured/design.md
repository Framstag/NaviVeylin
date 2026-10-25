# Design — the block carries the two counts, and both tasks say what makes a number a claim

## The root cause, in one paragraph

The measuring block was built to stop the worker walking the tree for facts it could be handed (issue #246). It
carries four kinds of fact — the paths, the owed checks, the tallies, the delta's scenario/case counts — and the
two a worker still counts by hand are the two most quoted in prose: the suite's declared case count and the gate's
own timing. Neither task states what makes a quoted number admissible, so the reviewer has to reconstruct a
number's origin from artifacts that do not carry it, grades the finding as it finds it (sometimes `NOTE:`,
sometimes `BLOCKING:` — see #297/#300 against #260/#280), and a wrong count buys a second agent pass. The rule
that fixes it is one sentence in each task: **a number is the output of a command in the record; a number with no
command is not a claim.**

## The single fix

1. `LOOP_SUITE_CASES_CMD` (project key) — the command that lists the declared cases without running one. The
   block runs it and prints `cases=<n> cmd=<command>`, so the number travels with the command that printed it.
   Empty is a state the block names, not a zero.
2. The `time` attribute gate.sh already writes into each tally is read back: `time=<n>s` per tally line, summed
   into `LOOP-MEASURE-GATE seconds=<n> suites=<n>`. The gate's cost becomes a number the worker can quote.
3. `worker.md` and `reviewer.md` each gain the number rule, and the reviewer's gains its grading (`NOTE:` unless
   the change depends on the number).
4. Cases and mutation records make the rule falsifiable: the block's two lines, and the two task sentences.

## Why not the alternatives

- **Add the numbers to the prompt skeleton only, without the block.** The worker would still count by hand; the
  rule would make the count *unquotable* rather than *available*, i.e. it would delete a claim instead of
  measuring it. The issue asks for the facts the worker may cite, which is the block.
- **Have the block grep the case files itself.** The suite's name and its listing flag are the project's, and the
  engine may name no project file (the portability check and design D16). A profile key is the one seam.
- **Make the block count cases in a detached copy of the base tree.** The base's copy would give "on HEAD" exactly,
  but it means a `git worktree` per measurement — side effects inside a reading command, and a second tree the run
  does not own. The block instead names the command beside the number, so the count is reproducible where it was
  taken; the delta's own cases are already a separate line (`LOOP-MEASURE-CHANGE cases=`), which is the
  HEAD-versus-tree difference a reader needs.
- **Grade numbers in the run's script rather than in the reviewer's task.** The run reads only
  `LOOP-BLOCKING: <n>`; it cannot tell which finding was a number. The rule is a reading instruction, and the task
  text is where the repo's other reading instructions live (issues #236, #205).
- **Make a wrong count always blocking.** That is the behaviour issue #244 measures and rejects: a wrong count
  that the change does not depend on is subtractive to fix, so it is a correction. A count the change *does*
  depend on still blocks, and the reviewer is told to quote the dependent line.

## Blast radius

- `measure.sh` is a reading command: it now forks one extra command (the case listing) and reads `time` from
  tallies it already opens. No check runs, and `--plan`-style behaviour is untouched.
- `LOOP_SUITE_CASES_CMD` is optional, so the foreign-project fixture (which declares no such key) exercises the
  "names no case-listing command" arm with no fixture change; it is not a `_DEFAULT` key, so
  `portability#foreign#fixtureDeclaresEveryBudgetDefault` does not fire.
- The two prompt cases are re-run, not added to the pool's cost: `prompt#workerIsHandedTheMeasureBlock` and
  `prompt#reviewerIsBoundedToReading` already start one run each.
- The four new mutation records are scoped by `--only` while iterating and covered by every full `--check`.

## Rollback

Revert `tools/loop/measure.sh`, `tools/loop/prompts/worker.md`, `tools/loop/prompts/reviewer.md`,
`tools/loop/loop.conf`, the one added case and the two extended ones, and the four records. No state, no on-disk
format, no caller's contract changes: the block prints two fewer lines and the tasks lose one sentence each.

## Evidence

- Red on HEAD, green after: `evidence/red-on-head-*.log`, `evidence/green-*.log` for the three cases.
- Falsification: `evidence/revert-check.log` — records `61.1`–`61.4`, each red for the right reason, each restored.
