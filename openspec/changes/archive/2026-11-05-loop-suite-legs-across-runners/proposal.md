# The suite's wall clock must not be the runner's core count

## Why

The loop's self-test runs its 312 cases in a pool whose width is `nproc`: `LOOP_SELFTEST_JOBS` is empty in
`tools/loop/loop.conf`, so `selftest_workers` takes the host's CPU count. On `ubuntu-24.04` that is 4, and the
suite's duration is therefore the sum of its cases divided by four — nothing the suite does can make it shorter,
and the CI step measured **17 min 15 s** (2026-11-04, run 41810844107).

Measured on a 16-core host, this tree, 2026-11-04:

| invocation | cases | duration |
|---|---|---|
| `--jobs 4` (what the runner gets) | 312 | 469 s |
| `--group run --jobs 4` | 114 | 261 s (**56 %**) |
| default (`--jobs 16`, 2026-10-25, before the growth) | 221 | 264 s |

**Root cause, in one sentence.** The pool's width is a property of one machine, so the suite's cost is bounded by
the runner it happens to get; the cases are independent — each owns a throwaway repository, a scratch root and a
process group, and shares nothing but the read-only engine — so the width of the *run* is free to be several
machines rather than the cores of one.

The group selection the driver already has (`--group <name>`) is the seam: `run` alone is over half the cost, so
two legs cost about half of one, and three cost about a third.

## Why this and the delta selection are two items

They are two independent levers on the same 17 minutes, and either is worth taking alone:

- **narrowing** (`loop-selftest-allows-an-empty-delta`) makes most *pushes* cost nothing, and leaves a push that
  does owe the suite at the full 17 minutes;
- **legs** bound what a push that owes the suite can cost at all, and do nothing for a push that owes two cases.

Together they are 17 minutes down to the slowest of about two minutes; neither alone gets there, and neither
blocks the other.

surface: 4 workflow steps added or changed (1 matrix job, 2 leg selections, 1 verdict), 1 capability requirement
added with 3 scenarios, 0 cases added, 1 guideline section extended —
`jq -r '.jobs.gates.steps[] | select(.name | test("leg")) | .name' .github/workflows/build.yml` and
`bash tools/loop-selftest.sh --list --group run | grep -c .` (114, the biggest leg).

## What Changes

- **`.github/workflows/build.yml`** — the loop's self-test becomes a matrix job of legs, one leg per group
  selection: `run` (114 cases), `prescan+gate+state+worktree+report+driver` (138), and the rest (62). Each leg runs
  `bash tools/loop-selftest.sh --group <its group>`, on the same pinned image, with `contents: read` and no
  Android SDK.
- **`guidelines/Backlog.md`** §7c — the paragraph that states the suite's cost gains the leg split and what a leg
  owes: its own selection stated in its own summary, and its own exit status as its own verdict.

## What This Does Not Do

- It does not change a case, the pool, the case bound or the mutation sets. A leg is the driver invoked with a
  group selection, which the driver already does and already states.
- It does not replace the delta selection of `loop-selftest-allows-an-empty-delta`: a leg is what a push that owes
  the suite costs, not what a push owes.
