# Design — fix-gate-step-bound

## Root cause

`tools/loop/gate.sh` ran each step as one unbounded unit: `run_step()` (`:184` on the base) started the command,
waited for it (`:195`, `wait "$step"`) and returned its status. No clock inside the gate compared a step's wall
clock against any value, so the only bound above it was the run's `LOOP_CHILD_MAX_SECONDS` (`loop.conf:52`) around
the whole child — and the run's answer to a child that outlasts it is to end the child, which commits nothing. One
suite that does not return therefore spends the child's whole budget, and an item whose gate was already green is
recorded `blocked` with its work in the tree.

## The single fix

Bound each step inside the gate, and name the step the bound answered:

- `run_step` starts the command under `set -m`, so the step is its own process group — the handle a bound needs
  (`loop_end_group`, `lib.sh:289`, already used by the case bound and the run's child cap).
- The step's status is read from a record the step itself writes (`( $cmd; printf '%s\n' "$?" > "$status_file" )`),
  not from `wait`: a pid the shell has already reaped answers nothing, which is the lesson `run.sh:994` records
  (issue #227). The record is also the event the poll waits for.
- The poll compares `SECONDS - started` against `LOOP_GATE_STEP_MAX_SECONDS` once per 0.1 s look. A whole-second
  clock read from `SECONDS` is the same shape the run's cap and deadline use; a `date` per look would spend on fork
  what the granularity buys (issue #259).
- When the bound fires, `loop_end_group "$step"` ends the step and everything it started; the caller appends
  `LOOP-GATE-STEP: <command> exceeded <n> s — the gate ended it and everything it started` to the stream the tally is
  written from, prints `— the step overran its bound of <n>s and was ended` on the console line it already prints, and
  the step counts as an error (`n=0`, `f=0`, `err=1`): the gate exits 1 naming the step.
- The bound is the profile's (`LOOP_GATE_STEP_MAX_SECONDS=1800`), announced once before the first step beside the
  profile line, so a log that ends without a verdict still says which clock was in force.

1800 s is set against the measurements the tree carries: 345 s for the whole build-and-test gate on a 16-core host
(`guidelines/Build.md` §2c, 2026-10-21), 273 s for all 223 cases of the self-test (2026-10-23, the same week
`LOOP_CASE_MAX_SECONDS` records), ~5× the slowest step — and it leaves 900 s of a 2700 s child for the phases the gate
is not (evidence, archive, report, commit). A project whose steps cost more raises the key; the key is a recorded
value like the other two clocks, which is the shape `loop.conf` already documents for `LOOP_CASE_MAX_SECONDS`.

## Blast radius

- One function and one call site inside `tools/loop/gate.sh` (`run_step`, and the loop that reads its status), plus
  one `say` line at start-up and one variable read from the profile.
- The gate's observable surface: the console's step line gains a suffix *only* when the bound fired; the tally of a
  bound-ended step carries `errors="1"` and the `LOOP-GATE-STEP:` line in its `system-out`; the verdict line is
  unchanged in shape (`LOOP-GATE: tests=… failures=… errors=… (…) — failing:<cmd>`, the step named in `failing:`).
  Everything that reads a green gate (the run's evidence check, `measure.sh`, the reviewer's quoted tally) reads the
  same fields.
- Cases: the 17 `gate#*` cases are the ones that read this surface; `gate#aStepEndsWithItsCommandNotWithItsWatchers`
  parses the plain step line (`^gate: bash <suite> — <n>s$`), which the change keeps for a step that returns inside
  the bound — asserted by re-running the group (17/17 green, `evidence/green-cases.log`).
- Mutation records: 2 of the 9 records that quote `gate.sh` name a line inside `run_step` (`46.1`, `63.1`); both are
  realigned to the lines the function now carries, their invariants unchanged, and `63.2` is added for the guard
  (3 records red when mutated, 0 not-noticed, 0 restore-failures — `evidence/loop-mutations-311.log`).
- No compiled file: nothing in the Android build, the native build or the JNI bridge reads the gate.

## Rollback

Revert the branch. The profile key disappears with the guard that reads it (`loop_die` never fires on an unknown
key: `loop_conf_value` prints empty, and the guard's fallback is the documented default 1800), so a tree whose
`loop.conf` still carries the key and whose engine was rolled back gates exactly as before — the key is inert.

## Alternatives the evidence eliminated

- **Raise the child's budget from a measured baseline** (the item's second candidate). Eliminated, not ranked: the
  same gate measured 8m25s, 9m46s and 5m45s on three occasions, and the same 5 680 tests took 3m47s on a quiet host
  and spent 30 min at 194 MB free RAM (`guidelines/Build.md` §2c; the measurement behind `LOOP_MIN_FREE_MIB` in the
  profile) — a budget derived from the previous run is a guess about the next one. And a step that never returns has
  no baseline at all, which is the state the item is about.
- **Bound the gate as a whole instead of each step.** Eliminated by the observation the change exists for: the gate
  is what a change owes (`./gradlew test`, `tools/loop-selftest.sh`, the portability check, the issue tooling — four
  steps for a `tools/loop/**` delta), and a single clock over the sum would end a *set* of healthy steps whose total
  is large. The overrun has to name the step that overran.
- **`timeout(1)` around the command.** Eliminated: it signals the command's pid, not the step's tree, and the
  processes that hold the gate open are the suite's own watchers and workers (`lib.sh:280` records the five runs
  that outlived their suites for this reason). `loop_end_group` already ends what a group holds.
- **A bound that ends the gate instead of failing the step.** Eliminated: a suite that did not finish cannot be read
  as green, and the run refuses a green claim with no tally. The step's own error is what lets the gate exit non-zero
  naming it, which is what a person can act on the next round.
