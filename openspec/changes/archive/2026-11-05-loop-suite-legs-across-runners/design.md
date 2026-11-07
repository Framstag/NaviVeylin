# Design — the pool's width is a machine, the suite has no use for that

## Root cause

`selftest_workers` (`tools/loop-selftest.sh:116`) resolves the worker count as the invocation's `--jobs`, else
`LOOP_SELFTEST_JOBS` from the profile, else `nproc`. The profile leaves `LOOP_SELFTEST_JOBS` empty
(`tools/loop/loop.conf:201`), so on the central build the width is 4 and the suite is the sum of 312 cases over
four workers — 17 min 15 s measured (2026-11-04, run 41810844107). Measured here at the same width on a 16-core
host, 469 s, and `--group run` at 261 s for its 114 cases.

The pool exists because a case shares nothing with another (issue #260): it owns a throwaway repository, a scratch
root under the loop's scratch directory, an output file and a process group. Nothing about that ownership is
per-machine — it is per-case — so the suite can be split across runners without any case learning about it. The
pool's own requirement is about a case's isolation, not about how many machines run cases.

## The split

Three legs, each `--group <selection>`, each on its own `ubuntu-24.04` runner:

| leg | selection | cases | expected |
|---|---|---|---|
| `run` | `--group run` | 114 | ~4 min 20 s at 4 workers (261 s measured here) |
| `tooling` | `prescreen gate state worktree report driver` | 138 | ~2 min |
| `rest` | the remaining thirteen groups | 62 | under 1 min |

The driver takes one `--group` today (`only_group`), so the second leg is one invocation per group in one step, or
the driver gains a repeatable `--group` — the change takes the second shape only if the first reads badly in the
workflow, and records which it took.

The legs' summaries are each the suite's own summary for the selection it ran (`N of 314 cases`), so a leg that
silently ran fewer cases is visible in its own log rather than in the run's.

## Why not the alternatives

- **Raise `--jobs` above the runner's cores.** Four workers on four vCPUs already saturate the machine; more
  workers oversubscribe the CPU and lengthen the wall clock (the cases are git- and process-bound, not sleeping).
- **A larger paid runner.** The honest lever, and the one this change does not take: it changes what the project
  pays per run rather than what the suite costs, and the project's repositories are public, where hosted-runner
  minutes are free. Recorded as the next candidate if the legs are not enough.
- **Split the suite into several case files** so each can be selected by file. That moves the problem into the
  driver's declaration (`driver#everyCaseDeclaresTheFilesItCanReach` reads one home per case) and buys nothing the
  group selection does not already give.
- **Cut the suite down.** A separate item: the case set grew from 166 to 314 in twelve days (measured 2026-10-22 →
  2026-11-03, `git show <sha>:tools/loop-cases.sh | grep -c '^case_'`), and the `run#*` end-to-end cases are the
  half that cannot be narrowed. Splitting is what makes that work payable per push while it is being done.

## Blast radius

- `gate#theMapCoversEveryPipelineStep` is the one case the split reddens: it compares every `bash`/`sh` command
  under a `Check …` step with the profile's suite map, and a leg runs the map's driver entry with a `--group
  <selection>` appended. The pin therefore strips that selection from both sides before comparing, and record
  54.11 falsifies the strip. The first dispatch of this change is the red that found it (run 42090111006, leg
  `tooling`, 166 s): `the suite map carries none of these commands the pipeline's Check steps run:
  tools/loop-selftest.sh --group "$g"`.
- The workflow's verdict becomes a matrix job's aggregate: one red leg is a red run, which is what a single red
  case is today.
- The one-suite-per-repository lease is untouched: each leg is its own checkout, so it is its own repository.
- A leg's log is a leg's log. A reader who wants the whole suite's summary reads the archive's evidence, where the
  rule already puts it (the whole set is owed before a change that owns `tools/loop/**` is archived).

## Open decisions

none
