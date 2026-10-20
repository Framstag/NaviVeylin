# fix-loop-host-memory-guard

## Why

The loop's budget arithmetic knew nothing about the machine it runs on. `tools/loop/run.sh` printed its header
(before this change, lines 92-98) with `items`, `minutes`, `review`, `may-close`, `push`, `max-decisions`,
`max-review-rounds`, `review-read-only`, `child-max-seconds`, `dry-run` and `reset-worktree` — not one value
about the host — and then took `state.sh next`'s verdict (counts, a deadline, a derived floor) and started the
item's child immediately (lines 543-602). `tools/loop/loop.conf` recorded the same budget facts and, beside
them, one host fact (`LOOP_CHILD_MAX_SECONDS`); the memory a build phase needs was not among them.

Measured 2026-10-19 (issue #209):

- item #126's gate hit **194 MB free RAM and 18 GB swap** and spent **30 minutes** inside
  `:app:kspMobileDebugUnitTestKotlin` before the retry died; the same 5 680 tests took **3m47s** on a quiet host;
- the same host during a run: 15 668 MB total, 13 168 MB used, **467 MB free**, **2 499 MB available**, swap
  13 915 MB of 37 887 MB — Firefox 7 600 MB, VS Code's Java language server 2 282 MB across three JVMs,
  Gradle/Kotlin daemons 1 625 MB, VS Code UI 885 MB, `cpptools` 407 MB, and the loop's own children 649 MB.

The loop was the smallest consumer and the one that failed. The consequence is not only a slow item: the run
records `wallSeconds` and gate numbers for the *desktop session*, so two runs of the same item are not
comparable, and a reviewer of the loop's own changes cannot tell a slow change from a starved host.

**Root cause and its evidence.** `run.sh` had no read of the host anywhere; the only bound it placed on a
gate-bearing phase was the child's wall-clock (`LOOP_CHILD_MAX_SECONDS`), which bounds the damage instead of
avoiding it. The red case `run#hostBelowTheFloorBlocksTheItemAndStops` fails on HEAD `fb713c1` with
`no agent process is started on a host below the floor: expected '', got 'invocation … prompt: worker-prompt.md …'`
(`evidence/red/hostBelowTheFloorBlocksTheItemAndStops.txt`) — with an unreachable recorded floor in the
configuration, the run starts the child anyway; the same tree on the fix records the item and stops. The two
companion cases fail on HEAD only on the header line the reading is named in
(`evidence/red/hostAboveTheFloorRunsTheItem.txt`, `evidence/red/hostWithoutTheReadingPasses.txt`).

**The single fix.** Before an item's child is started, `run.sh` reads `MemAvailable` from the file the recorded
key `LOOP_MEMINFO_PATH` names (`/proc/meminfo` here), compares it in MiB against the recorded floor
`LOOP_MIN_FREE_MIB` (default 1024), and below the floor records the item `blocked` with the measurement and the
floor it was compared against and stops the run with that as its stop reason. The free memory is printed in the
run header beside the budget. A host that publishes no reading passes the check, so the engine still runs in a
container without procfs.

**Spec requirement the defect contradicts:** `agent-issue-loop`, "One command starts a budgeted run, with no
dialogue" — its scenario *The run stops on its own authority* promises that the run stops when its budget cannot
pay for another item, and its opening promise is that *the same run can be repeated and audited*; a budget with
no term for the host is neither: the same item on the same tree costs 3m47s or 30 minutes depending on what the
desktop session is doing.

## Cases and evidence

Three cases in `tools/loop-cases.sh`, registered in **both** the `--list` array and the dispatch map, each
driving the real `run.sh` against the stub `gh` and stub `pi` in a throwaway repository, each with the reading
driven through `LOOP_CONF`:

- `run#hostBelowTheFloorBlocksTheItemAndStops` — the configuration names a floor no host reaches
  (`LOOP_MIN_FREE_MIB=99999999`): no child is started, the item's reason carries the measurement
  (`the host has <n> MiB available`) **and** the floor it was compared against, no branch is created, and the
  run's recorded stop reason and printed `stop:` line carry the same number.
- `run#hostAboveTheFloorRunsTheItem` — the floor is `0`: the header names `min-free-mib=0` and a numeric
  `free-mib=<n>`, the item's child is started, and the item closes through the same path as before.
- `run#hostWithoutTheReadingPasses` — `LOOP_MEMINFO_PATH` points at a file that does not exist, with the
  unreachable floor of the first case: the only thing that can let the item run is the missing reading, and it
  does — the header says `free-mib=unknown` instead of inventing a number.

Red on HEAD `fb713c1`: `evidence/red/`. Green on the fix: `evidence/green/`, which also carries the two cases
that already own this surface (`run#childOutlastingTheWallClockIsBlocked`, `run#stopReasonIsPrintedAndReported`)
re-run green in `evidence/green/existing-surface-cases.txt`. The whole offline suite is **not** run by this
change: the orchestrator runs it once on the merged tree.

## What Changes

- `tools/loop/run.sh`: a `host_free_mib` reader beside the header, the floor read from the configuration with
  the recorded default, the reading named in the run header (`min-free-mib=`, `free-mib=`), and the per-item
  guard that records and stops before an item's first child exists. `run_child`, its progress lines and its
  `LOOP_CHILD_MAX_SECONDS` cap are untouched.
- `tools/loop/loop.conf`: two recorded keys with the measurement behind them — `LOOP_MIN_FREE_MIB=1024` and
  `LOOP_MEMINFO_PATH="/proc/meminfo"`.
- `tools/loop-cases.sh`: the three cases, registered in both places.
- `.pi/skills/fix-loop/SKILL.md`: the flag table and the stop conditions name the host check and quote the
  reason it writes.
- `.pi/skills/fix-loop/REFERENCE.md`: "Measured costs" gains the host measurement and the floor.
- `tools/loop-mutations.sh`: one mutation (`24.1`) for the new refusal, so removing the guard is caught by the
  case that asserts it.

## Impact

Affected files, all of them the loop's own harness — no app, native or Gradle file is touched:

| file | module / area |
|---|---|
| `tools/loop/run.sh` | the run's phases, the item loop, `run_child`, the header, the stop conditions |
| `tools/loop/loop.conf` | the loop's recorded configuration (`lib.sh` sources it; the header and the guard read it through `loop_conf_value`) |
| `tools/loop-cases.sh` | the loop's offline self-test cases (`tools/loop-selftest.sh`) |
| `.pi/skills/fix-loop/SKILL.md` | the `fix-loop` skill's flags and stop conditions |
| `.pi/skills/fix-loop/REFERENCE.md` | the measured costs a run is planned with |
| `tools/loop-mutations.sh` | the loop's falsification harness: one mutation per invariant of `tools/loop/**` |

No Android module, manifest, resource, native source or JNI bridge is touched: this is not a phone, car or AAOS
change, so the parity rules, the native-build rules and the `submodule patch` / `local override` decision do not
arise. The portability seam keeps its shape: the two new values live in `loop.conf`, and
`tools/check-portability.sh` still finds the engine naming no project value.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **MODIFIED** "One command starts a budgeted run, with no
  dialogue": three scenarios added (the host is read and named; a host below the floor is reported instead of
  spent; a host that publishes no reading is not refused). No existing scenario is re-worded.

No other capability owns the loop's budget or the host it runs on.

## Additive or breaking, and rollback

**Additive.** A run gains one refusal it did not have, taken before anything is created for the item, and one
line in its header. No flag, state field, exit code, label or branch changes; a host above the floor behaves
exactly as before. A project that wants the old behaviour sets `LOOP_MIN_FREE_MIB=0`, and a host without procfs
never sees the check at all.

Rollback: revert this commit. The header returns to its previous line, the item loop starts every candidate as
it did, and no state file, branch, label or archived change is affected — the guard writes none of them except
the item record it blocks, which a revert stops producing. A run in flight during a revert is stopped first;
the lock refuses a second run anyway.
