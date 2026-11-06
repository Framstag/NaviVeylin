# Proposal — fix-gate-independent-steps-run-together

## Why

**Root cause.** The gate owes one step per owed command and runs them in a single blocking loop: at the base
`4d270814`, `tools/loop/gate.sh:284` calls `run_step "$cmd"` once per owed command, and `run_step`
(`tools/loop/gate.sh:232`) blocks until that one command has written its status — it owns one `$stream`
(`tools/loop/gate.sh:216`), one `$follow_stop` (`:220`) and one `$status_file` (`:224`) for the whole gate,
so a second step cannot start before the first has ended. Nothing in the engine or the profile says which owed
steps may overlap, so the four shell suites a `tools/loop/**` delta owes wait behind each other and behind the
build even though they share nothing but the read-only engine: each suite's scratch tree is its own `mktemp -d`
(`tools/loop-selftest.sh:193`, `tools/loop-mutations-selftest.sh:34`, `tools/issue-tooling-selftest.sh:42`) and
each writes its own tally file under the profile's tally directory.

**The red case on the base (`4d270814`)** — `gate#independentStepsRunTogether`, a profile naming three
stub suites that each sleep 3 s and touch their own output file, measured `10 s` of wall clock on the serial engine —
the sum of the three steps, not the longest — and `3 s` once the steps run together (`evidence/pool-probe.sh`, run
on this host: `PROBE pool=1 wall=10s`, `PROBE pool=3 wall=3s`). Tally and log in `evidence/`.

**What the item records, and what the tree shows.** The tracker entry (#801) records a five-command delta whose
four shell suites cost 1 088 s of a 20- to 22-minute gate phase and asks for ~420 s instead on a 16-core host. Those
five figures are the entry's, not this change's measurement — the block this tree's `measure.sh` prints carries no
gate timing yet (`LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time`), so the
only number here that this change took is the red case's own wall clock.

**Owning requirement.** `agent-issue-loop` — "A gate step is bounded, and the step that overran is named" (the
requirement this change widens: a step is still one bounded unit, and now several of them are in flight).

**Owning guideline.** `guidelines/Backlog.recipes.md`:471 — the paragraph "The steps that share nothing run
together." inside §7b "What an item costs, and the bounds on it" (`:212`), the section that states the gate's bounds
and now the pool, the two lists that run alone and both wall clocks (`:471-489`, the pool itself at `:477`).

**Surface, before the plan was fixed** — `tools/look.sh 'run_step|follow_stream|LOOP_GATE_STEP_MAX_SECONDS' --files`:
80 matches in 21 files, of which 7 are this project's (the rest is the libosmscout submodule and the archive):
the engine (`tools/loop/gate.sh`), its profile (`tools/loop/loop.conf`), the cases (`tools/loop-cases.sh`), the
consumer that reads a gate log's step blocks (`tools/gate-attribute.sh`), the mutation records anchored on the
engine's own lines (`tools/loop-mutations.sh`), the guideline above and the spec. 24 `gate#` cases already exist and
this change adds three (`git diff 4d270814 -- tools/loop-cases.sh | grep -c '^+case_gate_'` → `3`); 3 mutation records
are re-anchored because the lines they mutate move.

surface: 2 cases added, 1 profile key added, 3 mutation records re-anchored, 1 guideline section touched —
`tools/look.sh 'run_step|follow_stream|LOOP_GATE_STEP_MAX_SECONDS' --files` (80 matches, 21 files). The block's
`2 cases added` is the block's own count; the diff carries three `case_gate_*` functions
(`git diff 4d270814 -- tools/loop-cases.sh | grep -c '^+case_gate_'` → `3`), `gate#aPooledWavePrintsEachStepWholeWhenItEnds`
among them.

## What changes

1. **`tools/loop/loop.conf` — the pool is a recorded value.** `LOOP_GATE_POOL_JOBS=4` says how many steps of one
   wave may be in flight at once; it is the profile's, like `LOOP_GATE_STEP_MAX_SECONDS`, and `1` restores the
   serial engine exactly.
2. **`tools/loop/gate.sh` — one step per command, a wave of the steps that share nothing.** The owed list is built
   with the kind of list that owed each command (`first`, `compiled`, `suite`). The first steps run alone (a delta
   that is refused in seconds costs nothing else), the build runs alone (it owns the build directory the suites
   read through the environment), and the suite steps that follow run up to `LOOP_GATE_POOL_JOBS` at a time. Each
   step keeps its own output file, its own status record and its own clock; each is still bounded by
   `LOOP_GATE_STEP_MAX_SECONDS`; each still writes its own tally. The steps of a wave are recorded on the console
   and in the tallies in the profile's order, each under its own `gate: <command>` … `— <n>s` block, which is the
   shape `tools/gate-attribute.sh` attributes a failure from.
3. **`tools/loop-cases.sh` — the cases the item owes.** `gate#independentStepsRunTogether` asserts the wall
   clock is nearer the longest step than the sum; `gate#aFailingSuitesTallyIsUnchangedByItsNeighbours` asserts a
   pooled step's tally and count are its own, whatever its neighbours report; and
   `gate#aPooledWavePrintsEachStepWholeWhenItEnds` asserts a wave of several prints each step whole, under its own
   block, when it ends.
4. **`guidelines/Backlog.recipes.md`:471 — the rule beside its measurement.**

Additive: no requirement is removed. What a wave of several changes is the console's shape — a step in a wave still
writes its own `gate: <command>` … `— <n>s` block, printed when that step ends, where a wave of one keeps the live
follower (issue #269) — and a pool of `1` is the serial engine, unchanged. Rollback: set
`LOOP_GATE_POOL_JOBS=1` (the serial path is the pool of one) or revert the change; the gate's exit status, its
verdict line and its tallies are unchanged in shape.

## Impact

`tools/loop/gate.sh`, `tools/loop/loop.conf`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`,
`guidelines/Backlog.recipes.md`, `openspec/specs/agent-issue-loop/spec.md`. No compiled file, no app code, no
device evidence.
