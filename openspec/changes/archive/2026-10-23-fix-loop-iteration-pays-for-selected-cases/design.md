# Design — the suite's selection and the gate's record of what it cost

Both parts are engine-local: `tools/loop-selftest.sh` owns the selection, `tools/loop/gate.sh` owns the record.
Nothing here touches the run's state file, the tracker, the worktree or a project script, and no `loop.conf` key is
added — a selection is an argument of one invocation, not a project value.

## D1 — How a case is selected

**Alternatives eliminated.**

1. *Extend `--case` with a comma-separated list* (`--case a#1,b#2`). It works, but the caller has to enumerate the
   cases it broke, which is the knowledge a worker does not have when it has just edited `run.sh`: it knows a
   *group*, or it knows a *file*. An enumeration also goes stale the moment a case is added to the group.
2. *A `--filter <regex>` over the whole case name.* One flag, but it needs a regex dialect on the command line and
   turns a typo into a silent empty selection unless the driver also proves the pattern names something — which is
   the refusal this design adds anyway. `--group` costs one glob and reads as the thing the case names carry.
3. *A selection file* (`--cases-from <file>`). It makes the caller the source of the set, so the declared set stops
   being the single truth: a deleted case would silently shrink a run. The mutation harness already shows the cost
   of that shape (`--files` filters *records*, never the declared list).
4. *No selection at all: parallelise (#260) and lower the sleep (#259) instead.* Both are filed and both are
   larger; neither makes an iteration narrow. A 178-case suite at 2 min still makes a worker wait for cases it
   knows are irrelevant.

**Chosen: `--group <group>` plus a glob in `--case`, intersected when both are given.** The case name already
carries its group as its prefix (`<group>#<name>`, the dispatch in `tools/loop-cases.sh` is keyed by the same
string), so the group needs no second declaration that could disagree with the first. The glob reuses the matching
the engine already relies on for file patterns (`gate.sh`'s `matches`), so a caller who knows `run#*` needs no new
syntax.

**The refusal is part of the selection, not a nicety.** `count_matching` counts each filter against the *whole*
declared set before the intersection, so an empty intersection names the filter that is actually wrong instead of
the one read second, and a filter that resolves to nothing exits 2 with the text `no case carries the group: …` or
`no such case: …`. Without it, `--group nope` would run zero cases and print `no cases` with exit 0 — a green gate
for a suite that executed nothing, the same lie issue #268 removed from the tally.

**Risk, and its bound.** A filtered gate run is weaker than a full one, and a worker could gate with a filter and
call it green. Two things bound that: the summary line of a filtered run names its selection
(`— selection: --group driver`), so the gate claim the run records carries it; and the filter is an argument of the
invocation, never a project value, so a run's own gate command (`LOOP_GATE_SUITES` in `loop.conf`) is unchanged and
still owes the whole suite.

## D2 — How the gate records a suite's wall clock

**Alternatives eliminated.**

1. *`date +%s` before and after each suite.* Same answer, one fork per suite on a path that already forks twice per
   suite; `SECONDS` is the shell's own integer counter and is monotonic for the lifetime of the gate process.
2. *`/usr/bin/time -f %e` around the suite.* It would also capture the suite's own stderr into the gate's stream and
   change what `gate#theSuitesOutputReachesTheConsoleWhileItRuns` asserts; and it is not available under that name on
   every host the tooling runs on.
3. *Timing outside the gate* (the worker's report, a second instrumented run). That is what happened until now, and
   it is why the number had to be recovered with a stopwatch and a `ps` on a live suite: the measurement has to be
   in the record the run already keeps, or it is not kept.
4. *An approximation from the tally's file mtime.* Filesystem timestamps have a resolution and a write order that
   the gate does not control; the number would be a guess dressed as a measurement.

**Chosen: `$SECONDS` deltas, reported three times from one observation.** Printed on the suite's line (so a live
run can be watched), carried in the `LOOP-GATE:` summary beside the command (so the run's gate claim names it), and
written as the tally's `time` attribute (so the run's own check reads it). One measurement, three readers — the same
discipline the gate applies to a suite's output since #269.

**Risk, and its bound.** A suite's duration is not stable between runs on a loaded host, so nothing may assert an
exact number: the case that owns this reads the duration out of the run and asserts it is at least the suite's own
`sleep 2`, and asserts the tally carries the same number. A recorded baseline is not added to `guidelines/Build.md`
§2c by this change — the section owns the maintained numbers, and a baseline belongs there once the selection of
#260/#259 has moved them.

## Verification

- Four cases, each named by a scenario in `openspec/changes/fix-loop-iteration-pays-for-selected-cases/specs/`.
- A red-on-HEAD run recorded in `evidence/red-HEAD.log`: all four fail against the driver and gate of `abeffcad`.
- Three mutation records (`49.1`–`49.3`) in `tools/loop-mutations.sh`, one per new invariant, run through the
  harness — mutate, the named case goes red, restore, forced green.
- The gate for this change: `tools/loop/gate.sh --base abeffcad` in the change's worktree, covering the shell
  profile only (no compiled path in the delta). Its own output demonstrates D2 on the real suites.
- An independent read-only review before archive (`.pi/skills/fix-loop`): a fresh child checks every scenario
  against the diff and the gate XMLs. The writer is not the reviewer.

## Threading model

Not applicable — no Android component, no dispatcher, no lifecycle. Every new path is shell, executed by the suite
driver in the same process as before; the parallel pool that would change that is issue #260 and explicitly not
here.
