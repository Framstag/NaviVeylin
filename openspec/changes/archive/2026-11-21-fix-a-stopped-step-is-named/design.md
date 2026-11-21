# Design — a step that is stopped is named while it is stopped

## Root cause

The wave poll (`tools/loop/gate.sh:705-756`, base commit `8e3861c4`: `:702-726`) asks a step three questions — has
it written its status file, did it overrun, has its clock reached `step_max` — and, when all three are false, does
nothing but sleep. A step whose processes a signal stopped answers "no" to all three for as long as the stop lasts,
so the poll has no fact to print and the run has no way to tell a stop from slowness. The bound then ends the step
and records it as an overrun (`rc=124`), which is a claim about the step's own fault that no measurement supports.

## The single fix

One arm, after the bound's and before `done_all=0`:

- the poll keeps a sample of the states of every process group, read **once a second** and **once for the whole
  wave** (`:712-716`): `ps -A -o pgid=,stat=` filtered by `awk` down to `" <pgid>:<letter> "` for each group that
  carries a member whose state starts `T`, `t` or `Z`. `SECONDS` is the poll's own clock, so the sample is refreshed
  on the second the wave is already measuring against;
- a slot whose process group appears in that sample prints one line, once (`st_stopped[slot]`, declared at `:653`
  and cleared for a new step at `:667`), through `say` — so it reaches the console *and* the gate's log, and
  `--quiet` still leaves it in the log:

  `gate: <command> — the step is stopped, not running (state <letter>); the bound at <n> s is the only thing that
  will end it`

- nothing else changes: the slot stays live, `done_all=0` is set as before, and the bound at the next second that
  reaches it ends the group exactly as it did.

The group id is `st_pid[slot]`: `start_step` starts every step under `set -m`, so the step's process group is the
background job's own pid, which is also the id `loop_end_group` already ends the step with.

## Blast radius

- **A step that returns inside the bound, or overruns, is unaffected**: its status file or the bound's arm answers
  before this arm is reached, both as they are today, and the case `gate#aStepIsBoundedAndTheOverrunNamesIt` reads
  the unchanged console and tally lines.
- **Cost**: one `ps -A` fork per second for the whole wave, not one per slot and not one per 0.1 s look; the poll's
  own comment (issue #259) is the rule this keeps. A host where `ps` refuses `-A -o pgid=,stat=` prints nothing from
  the awk, so the arm never fires and the gate behaves exactly as before — the degradation is silence, never a
  wrong line.
- **The stop is reported once per slot per wave**: a wave of four that is stopped in all four slots prints four
  lines, one per step, and no more for the rest of the bound.
- **No profile key, no tally shape, no verdict, no bound moves.** A stop still ends at the bound, as `rc=124`, so
  the run's own record of an overrun is unchanged — the item states that ending a stopped step early is out of
  scope, because the bound is the engine's one authority over a step's clock and a second authority would be a
  second bound.

## Alternatives the evidence eliminated

1. **End a stopped step early (`SIGKILL` on the first stopped look).** Eliminated by the item's own scope and by the
   rule behind it: two authorities over one step's clock is two bounds, and the run's overrun accounting
   (`rc=124`, `LOOP-BASERED-STEP:…|1`) would then have a third shape nothing measures.
2. **Read the slot leader's state only** (`ps -o stat= -p "${st_pid[slot]}"`). Eliminated by the case itself: a stop
   is a per-process fact, and the leader here is the subshell `start_step` leaves in `wait`, which stays in `S` while
   the command it waits for is stopped — the shape a `SIGTTIN` on a reading child has, and the shape
   `gate#aStoppedStepIsNamedBeforeItsBound` now starts (`kill -STOP $$` in the suite). A leader-only read would print
   nothing for that case, which is the silence this item exists for; the step is the group the engine already ends
   (`loop_end_group "${st_pid[slot]}"`), so the group is what is read.
3. **`ps --pgid <id>` or `ps -g <id>`.** Eliminated by the hosts this engine runs on: the measured host's
   `ps` (procps-ng 4.0.7) refuses `--pgid` (`ps -o stat= --pgid $$` → `unbekannte lange GNU-Option`, this host's
   German locale), and its `-g` selects by *effective group* while BSD's `-g` selects by process group — one flag,
   two meanings, one engine. Listing with `-A` and matching the `pgid` column is the form both carry.
4. **Keep the state sample on the step's stream file instead of the console.** Eliminated as unasked: the incident's
   evidence is the gate's console log (a base-red pre-flight writes no tally), and the gate's own `gate: <cmd>`
   lines already go through `say` for that reason.
5. **A second, shorter bound for a stopped step.** Eliminated with option 1: it is the same second authority under
   another name.

## Rollback

Revert the arm, the `st_stopped` array and the guideline paragraph; delete the case and the mutation record; drop
the delta. Nothing is stored, migrated or cached, so the tree returns to the base behaviour byte for byte and the
one thing lost is the line.
