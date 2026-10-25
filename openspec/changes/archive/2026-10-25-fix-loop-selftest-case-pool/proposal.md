# Proposal — fix-loop-selftest-case-pool

## Why

The loop's self-test runs its cases strictly one after another: the driver starts a case, waits for its process group,
prints its `PASS`/`FAIL` line, and only then starts the next one (`tools/loop-selftest.sh:425-433` at base
`a2489ca7`; the loop was `for case_name in $selected; do … run_case …; done`, with `run_case` waiting on `$case_pgid`
at `tools/loop-selftest.sh:326-336` there).

**Root cause (one).** The driver's iteration is sequential while nothing a case touches is shared: a case owns its
throwaway git repository, its scratch tree under the loop's gitignored scratch root, its output and its own process
group (`set -m`), and the
only thing it reads beside its own trees is the read-only engine (`tools/loop/`). The suite's wall clock is therefore
the *sum* of its cases, while the host it runs on is idle for all but one of them.

Measured on the item (2026-10-22, issue #260): 163 cases took 723 s, the 66 `run#*` cases were 481 s of that, and the
cheapest groups are just as independent (`state` 12 s, `prescreen` 23 s, `worktree` 42 s). Re-measured on this tree,
2026-10-25, with the case count grown to 221, one group at a time, the same driver bounded to one worker
(`--jobs 1`, the pre-change shape) against its own default:

```
$ time bash tools/loop-selftest.sh --group state --jobs 1        → 1:00,12 (18 cases, 95 % CPU)
$ time bash tools/loop-selftest.sh --group state                 → 20,86 s (18 cases, 288 % CPU)
$ time bash tools/loop-selftest.sh --group worktree --jobs 1     → 1:07,79 (18 cases, 87 % CPU)
$ time bash tools/loop-selftest.sh --group worktree              → 20,06 s (18 cases, 305 % CPU)
$ time bash tools/loop-selftest.sh                               → 4:24,11 (221 cases, 439 % CPU)
```

**Red cases — taken on HEAD `a2489ca7` with the tree otherwise clean, 2026-10-25T00:53Z-00:58Z**
(`evidence/red-on-head-pool.log`, `evidence/red-on-head-sweep.log`; the HEAD copy of the driver is made by
`git show HEAD:tools/loop-selftest.sh`, driven against this tree's case file, so the *cases* are this change's and the
*driver* is HEAD's):

```
# driver#theSuiteRunsItsCasesInAPool
  the suite ran its cases one after another (at most 1 was running at any moment): start stub#one
stop stub#one
start stub#two
stop stub#two
…
FAIL driver#theSuiteRunsItsCasesInAPool
loop-selftest: 1 of 1 cases failed
status=1

# driver#aCaseThatLeavesAProcessBehindIsSweptWithIt
FAIL driver#aCaseThatLeavesAProcessBehindIsSweptWithIt
loop-selftest: 1 of 1 cases failed
status=1
```

Two mechanisms are named by the item and both are in the tree. (1) The loop is serial. (2) The `/proc` sweep that ends
what a case leaves behind runs once per suite, in `on_exit` (`tools/loop-selftest.sh:113-121` at base `a2489ca7`:
`loop_end_group "$case_pgid"` for the single running case, then `loop_end_pids_under "$tmp"` over the whole scratch
root) — a pool cannot keep it there, because beside a *running* case the same sweep would end that case's processes,
and because a process a case left behind would then stand beside every case after it. The second red case is the
capability that is missing for it: the driver hands a case no scratch root of its own, so a case's own sweep has
nothing narrower than the suite to read.

Measured cost of the per-case sweep, which is what the pool has to pay for it: 664 processes → one
`loop_pids_under` pass costs 0,83 s on this host (`evidence/sweep-cost-probe.sh`, two runs: 838 ms, 832 ms), the same
"about a second" `tools/loop-selftest.sh:68-70` already records for a sweep of every process. 221 cases therefore pay
~185 s of scanning, which the pool spreads and the serial suite paid at the end only once.

Owning capability: `agent-issue-loop` (`openspec/specs/agent-issue-loop/spec.md`, requirement *A suite ended by a
signal ends the case it was in, and its processes*, line 991, and *A suite ended by a signal removes its scratch
tree*, line 921). Owning guideline: `guidelines/Backlog.md`, its section "The loop", the paragraph that states the suite's
cost, its file-set selection and the one-suite-per-repository lease (line 352-370).

Surface, counted before the plan was fixed (`grep -l`/`grep -n` over the cases, the engine and the prompts; the
count, not the first file, is what the estimate is set against):

```
$ grep -c '^case_' tools/loop-cases.sh                                   → 223 declared cases (221 selected)
$ grep -n 'run_case\|case_pgid\|on_exit\|core_ensure' tools/loop-mutations.sh
   → 3 records quote the driver's compile and its traps (48.1, 48.2, 53.11)
$ grep -rn '723\|one after another' tools/loop* tools/loop/ guidelines/*.md openspec/specs/
   → 9 statements of the suite's serial cost (loop.conf:195, gate.sh:81, loop-cases.sh:26, loop-selftest.sh:26,
     loop-mutations.sh:356,406, guidelines/Backlog.md:360, spec:993 area, this proposal)
$ grep -n 'cat "\$d/ran"\|STUB_RUN_LOG\|quick_log' tools/loop-cases.sh
   → 3 assertions on the *order* cases ran in (aFileFilter:7053, aGroupFilter:6806,6811, aSecondSuite:7287)
```

So: 2 cases added, 3 cases realigned (their `ran`-log assertions become set assertions), 1 driver rewritten in place,
1 case-file helper (`scratch()`) given the per-case root, 2 mutation records added and 1 repaired, 1 configuration key
added, and 6 places that quote the suite's cost re-measured.

## What Changes

- The driver runs its selection in a bounded pool: `--jobs <n>`, else the profile's `LOOP_SELFTEST_JOBS`, else the
  host's CPU count (`nproc`, else `getconf _NPROCESSORS_ONLN`, else one). A case starts while fewer than `n` run, and
  the next one starts the moment one finishes (`wait -n`, bash 4.3, refused with exit 2 on an older bash).
- Each case keeps its own process group (`set -m`), gains a scratch root of its own (`LOOP_CASE_TMP`, which
  `scratch()` creates the case's trees under) and a captured output, and is ended when its slot is freed: its group
  first, then the processes standing in its own root.
- `on_exit` ends *every* running case's group before it sweeps the suite's root, so a signal still ends the cases it
  interrupted; the suite-level sweep stays as the net for a process that stood in the suite root itself.
- The one compile claims its output directory with a `mkdir` (`core_ensure`), so two cases asking in the same instant
  compile once and are handed the same class path.
- Unchanged on purpose: the `PASS`/`FAIL` line per case, the counts, the summary line, the exit status, `--list`,
  `--case-files`, the one-suite-per-repository lease, and a `--case` run of one case being one case.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `agent-issue-loop` — one added requirement, *The self-test runs its cases in a bounded pool, and what a case leaves
  ends with it*, with three scenarios (`specs/agent-issue-loop/spec.md`). No shipped scenario is modified or removed,
  so the delta is `ADDED` only.

## Impact

Production sources (tooling)
- `tools/loop-selftest.sh` — the pool (`start_case`, `finish_case`, the reaping loop), the per-case root and output,
  `on_exit` over the running slots, the `wait -n` refusal, the `--jobs` flag, the claim in `core_ensure`
- `tools/loop/loop.conf` — `LOOP_SELFTEST_JOBS`, the profile's bound on the pool

Test sources (tooling)
- `tools/loop-cases.sh` — `scratch()` honours `LOOP_CASE_TMP`; two cases added (list, dispatch, body); three cases
  realigned to compare the set of cases that ran instead of the order
- `tools/loop-mutations.sh` — two records added (`59.1`, `59.2`), record `48.2` repaired to the line it now names

Docs / specs
- `openspec/specs/agent-issue-loop/spec.md` (delta), `guidelines/Backlog.md`, its section "The loop" (the paragraph that states the
  suite's cost and its lease)

Verification (each task's own step, `tasks.md`)
- the two red cases on HEAD (`evidence/red-on-head-pool.log`, `evidence/red-on-head-sweep.log`) and green on the tree
  (`evidence/green-cases.log`)
- the two new mutation records and the repaired one, each RED under its mutation and green after the restore
  (`evidence/mutation-run-new-records.log`)
- the whole suite, pooled: `evidence/full-suite-pooled.log`, 221 cases, 4 min 24 s
- the gate, once: `evidence/gate.log`, its tallies in `evidence/gate/` (`tools_loop-selftest.sh_--from-git_….xml`
  records `tests="221"`), `tests=534 failures=0 errors=0`
