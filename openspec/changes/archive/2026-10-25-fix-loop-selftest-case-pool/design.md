# Design — fix-loop-selftest-case-pool

## Root cause

`tools/loop-selftest.sh` iterated its selection sequentially (`for case_name in $selected; do … run_case …; done`) and
`run_case` waited for the case's process group before returning, so the suite's wall clock was the sum of its cases
while each case's state — repository, scratch tree, output, process group — was private. Nothing but the read-only
engine is shared, so the serialization was the only thing holding the cases apart.

## The single fix

One pool in the driver, and one per-case cleanup. The driver keeps at most `$workers` runnings slots; a slot is a
process group, a scratch root (`<slot>/tree`, handed to the case as `LOOP_CASE_TMP`), an output file and a status
file. `wait -n` reaps whichever case finished, the slot's status file decides which one that was, `finish_case` ends
the case's group, sweeps the processes standing in the case's own root, prints the case's output and its verdict, and
frees the slot for the next case. `on_exit` ends every running slot's group before its suite-level sweep, and the
compile is claimed with a `mkdir` so two asking cases compile once.

No second mechanism is needed: the pool is the whole change, and the per-case sweep is that same pool's precondition
(a sweep over the suite's root cannot run while cases are running).

## Blast radius

- `tools/loop-selftest.sh` — the driver's case execution only. The selection (filters, file sets, refusals), the
  lease, the class compile's laziness and the summary are untouched.
- `tools/loop-cases.sh` — `scratch()` reads `LOOP_CASE_TMP` (falling back to `LOOP_TMP`, the hand-run shape), two
  cases added, three cases' `ran`-log assertions compare a set instead of a sequence.
- `tools/loop/loop.conf` — one key, `LOOP_SELFTEST_JOBS`.
- `tools/loop-mutations.sh` — two records added, one repaired.
- Nothing outside `tools/**` is executed differently: no compiled file changes, so the gate owes no build.

## Rollback

`git revert` of the change restores the serial loop; nothing it wrote outlives it (every tree it makes is under the
suite's own scratch root and is removed), and `--jobs 1` on the new driver is the old behaviour for a reader who
needs it before the revert lands.

## Alternatives the evidence eliminated

- **A barrier pool (start `n`, wait for all, repeat).** Its tail is the slowest case of each batch, and the `run#*`
  cases are the suite's longest (481 of 723 s measured on the item) — with 221 cases in 14 batches of 16 a single
  30 s case would hold a whole batch. Eliminated on the item's own numbers.
- **Sweeping the suite's root once per slot instead of per case.** It is what the exit trap already does, and beside
  a running case it ends that case's processes: the sweep's unit has to be the case's own root, which is why each
  case gets one.
- **Skipping the per-case sweep when the case's group is already empty.** Cheap (the group test is a syscall) and
  unsound: the shapes that leak are exactly those in a group of their own (`run_child`'s child, a case's own
  `set -m`), which the group test cannot see. Measured cost of not skipping: 0,83 s per sweep on a 664-process host,
  ~185 s over 221 cases, spread by the pool.
- **`flock`-based slot accounting, or job control (`jobs`/`%1`) instead of `wait -n`.** The engine already refuses
  tools a host may not have; `wait -n` is bash 4.3 and is refused with exit 2 rather than emulated.
- **Running the cases in parallel *within* one `--case` invocation for the mutation harness.** Not this change: the
  harness mutates one file and restores it between runs, so its cases are serial by construction.

## Where the rule is stated

- `openspec/specs/agent-issue-loop/spec.md` — the added requirement (this change's delta).
- `guidelines/Backlog.md`, its section "The loop" — the paragraph that states the suite's cost, its file-set selection and the
  one-suite-per-repository lease, updated with the pooled measurement and the per-case sweep.
- `tools/loop-selftest.sh` — the header's usage and exit lines, and the pool's own comment block.
