# Design — fix-gate-independent-steps-run-together

## The defect, mechanically

`run_step` owns five facts whose names have no step in them: `$stream`, `$follow_stop`, `$status_file`, `$step`
and `$step_overran` (`tools/loop/gate.sh:216`, `:220`, `:224` at `4d270814`, all inside `run_step`, `:232`). Its
blocking loop `while [ ! -e "$status_file" ]` (`:252`) is the whole gate's loop, so the engine cannot express two steps at once even when
the profile owes them and nothing connects them. The consequence is not a wrong number — the numbers are right —
it is the wall clock: the four shell suites and the build are added instead of overlapped.

## The single fix

A step becomes three calls on its own slot instead of one function on shared globals:

- `start_step <slot> <cmd>` — writes the step's own output file, status record, clock and flags into the slot's
  array entries and starts the command as its own process group (`set -m`, the handle the bound already uses).
- `wave_wait <slot> …` — one poll loop over the slots it was given: a step that reaches
  `LOOP_GATE_STEP_MAX_SECONDS` is ended with its group (`loop_end_group`) exactly as before, and the wave ends when
  every slot has either written its status or been ended. A wave of one keeps the live follower
  (`follow_stream`, issue #269): one writer, so nothing can interleave with it.
- `finish_step <slot>` — the step's console block, its summary parsing, its tally and its contribution to the
  gate's totals, all read from that slot's own output file.

The run loop becomes a walk over the owed list with the wave size taken from the kind:

| owed by | run as | why |
|---|---|---|
| `LOOP_GATE_FIRST_STEPS` | alone | a step that refuses a delta in seconds must not pay for the suites it would refuse (issue #792) |
| `LOOP_GATE_COMPILED_CMD` | alone | it owns the build directory every suite reads through the environment |
| `LOOP_GATE_SUITES` | up to `LOOP_GATE_POOL_JOBS` together | each suite resolves a scratch tree of its own and writes its own tally; they share only the read-only engine |

Ordering is preserved where it is observable: the owed list keeps the profile's order (first steps, then the build,
then the suites), the dedupe is on the command (`awk -F'\t' '!seen[$2]++'`, the first list that named it owns how it
runs), and a wave's steps are recorded in that order after the wave has drained — so the verdict line, the
`— failing:` list and the tallies are the same bytes in the same order a serial gate wrote, only sooner.

## Why not the alternatives

- **One step per `&` with `wait` and no per-step record.** The tally, the summary and the overrun line are all read
  from `$stream`; with one shared file a pooled step's summary would be its neighbour's — the failure mode the
  second case exists to refuse. One file per slot is the fix, not an optimisation.
- **Stream every step's output live into one console.** Two writers' bytes interleave, and `tools/gate-attribute.sh`
  attributes a failing case to the step whose `gate: <command>` … `— <n>s` block carries it
  (`tools/gate-attribute.sh:76-100`). Interleaved output produces a log whose failing case belongs to the step that
  printed next — a red gate read as a defect in the wrong suite. A wave of one therefore streams (nothing can
  interleave) and a wave of several prints each step whole when it ends, which is the same block shape both paths
  produce.
- **Decide which steps may overlap by parsing the command's name.** The engine may hold no project fact: the
  profile's own three lists already say which command is a refusal, which is the build and which is a suite, so the
  kind travels with the owed entry instead of being guessed from a string.
- **A pool size derived from `nproc` inside the gate.** The pool is a recorded project value like the two bounds
  beside it, so it is read from the profile, named on the console (`LOOP-GATE-POOL:`) and can be set to `1` on a host
  where the suites do contend.

## Blast radius

`tools/loop/gate.sh` (the step engine and the run loop), `tools/loop/loop.conf` (one key), `tools/loop-cases.sh`
(two cases, registered in the case list and the dispatcher), `tools/loop-mutations.sh` (three records whose anchor
lines moved: 49.3, 63.2, 84.1; 46.1 and 63.1 still name the lines they mutate, because the step's start and its
follower kept their local names). Nothing else reads `$stream`, `$status_file` or `step_overran`; a gate
log's shape, its exit status, its verdict line, its per-step bound and its tallies are unchanged.

## Risk

- **Two suites that do contend.** The mutation self-test starts nested single-case runs of the loop's own suite, and
  the loop suite's per-repository lock (`tools/loop-selftest.sh:336`) refuses only a second *suite*, not a single
  named case, so the nested runs are not refused by it. A host where the four suites collide is answered by
  `LOOP_GATE_POOL_JOBS=1` — the serial engine — without reverting the change. This is a real risk that only a gate
  run on this host settles, and it is the gate child's to settle, not this worker's.
- **Wall clock under load.** A pooled step's own bound is measured from its own start, so a wave of four slow
  candidates can hold the gate for one bound rather than four — a shortening, never an extension.
- **A step that fails early in a wave.** The rest of the wave still runs, exactly as the serial engine runs the rest
  of the owed list after a failure; the verdict is still the collected numbers and the `— failing:` list.

## Verification

`tools/loop-selftest.sh --case 'gate#*'` — the three new cases plus the 24 existing ones (27, each `PASS` in the
item's gate log), which pin the step block shape, the live stream, the per-step bound, the tally derivation, the
overrun line and the profile line. The declaration guard (`driver#everyCaseDeclaresTheFilesItCanReach`) is run first,
as the gate's plan puts it first for a delta that writes a case. `bash tools/loop-mutations.sh --check` applies the
three re-anchored records. No compiled file changes, so no build.
