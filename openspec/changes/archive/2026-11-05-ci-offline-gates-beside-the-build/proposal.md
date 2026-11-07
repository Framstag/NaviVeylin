# The offline gates run beside the build, not in front of it

## Why

Measured 2026-11-04, run 41810844107, job 126509396527 (`runs-on: ubuntu-24.04`), read from the job's own step
clock (`gh run view --job 126509396527 --json jobs`):

| step | span | duration |
|---|---|---|
| `Check agent issue loop` | 09:59:51 → 10:17:06 | **17 min 15 s** |
| `Check issue tooling` | 09:57:02 → 09:59:47 | 2 min 45 s |
| `Check the JVM-only test graph` | 10:20:09 → 10:22:00 | 1 min 51 s |
| `Check sensor tooling` | 10:17:08 → 10:18:59 | 1 min 51 s |
| `Check portability` | 10:19:00 → 10:19:36 | 36 s |
| `Check stale citation resolver` | 10:19:36 → 10:19:57 | 21 s |
| `Check feature-list tooling` | 09:56:27 → 09:56:46 | 19 s |
| `Check documentation routes` | 09:56:46 → 09:56:58 | 12 s |
| `Check the rule routes` | 09:56:58 → 09:57:02 | 4 s |
| `Check issue metadata` | 09:59:47 → 09:59:51 | 4 s |
| five more | | ≤ 3 s each |

**Root cause, in one sentence.** The workflow is one job, so every offline check the repository has is executed
before the step the build waits for: the job's first **23 min 36 s** (09:56:24 → 10:20:00) are steps 5–24, none of
which needs the Android SDK, the NDK, vcpkg, Gradle or a token — each step's own comment already says so — and
`Install vcpkg dependencies`, the step that gates everything after it, does not start until **10:22:55**.

The cost is not the checks, it is where they sit: the same checks beside a 57 min 10 s install would cost the run
nothing at all. The workflow's own comments claim exactly the property that makes the split free — `Offline, no
token, no model, no device and no sandbox tool — and it needs nothing the build does not already have` — and then
charge that property to the critical path.

**Why it has been invisible.** No step of the gate is priced anywhere a reader looks. `guidelines/BuildGate.md` §2c
records a gate's *total*, and a run's log shows one duration per step in the GitHub UI only, so the twenty-four
minutes read as "the build is slow" rather than as "twenty-four minutes of offline work is in front of a
57-minute install".

**Surface.** Twenty steps moved, one job added, no case added, no engine edited.

surface: 1 job added to `.github/workflows/build.yml` and 20 of its steps moved into it (5 `run:`-only groups stay),
1 capability created with 1 requirement and 3 scenarios, 0 cases added, 1 guideline section extended —
`gh run view --job 126509396527 --json jobs | jq -r '.jobs[].steps[] | "\(.number) \(.startedAt) \(.completedAt)
\(.name)"'` before the change, and the same command on the change's own dispatch run after it.

## What Changes

- **`.github/workflows/build.yml`** — a second job, `gates`, beside `build`. It carries the steps that need no
  build input: OpenSpec artifact hygiene, capability classification, feature-list tooling, documentation routes,
  rule routes, issue tooling, issue metadata, agent issue loop, loop spend analyser, backlog references, sensor
  tooling, build inputs, portability, stale citation resolver, action runtimes, the vcpkg cache probe, the sonar
  workflow, the Compose sandbox rule and the daemon JVM criteria. It runs on the same pinned image, needs
  `contents: read` alone, and takes no cache.
- **the steps that stay** — `Check libosmscout Android-free outside Android/` (1 s, and it guards the tree the
  build compiles), and every check that needs the Android SDK or the build itself (`Check the JVM-only test
  graph`, `Check the dex gate's producer`). They keep their place in front of the build, so a red one costs
  seconds.
- **the workflow's verdict** — the run is green when both jobs are green; both jobs are started together, so the
  gate job's cost is paid by a runner doing nothing else rather than by the build's clock.

## What This Does Not Do

- It does not make any check cheaper, and it does not narrow the loop's self-test (its own items, measured in the
  same run: 17 min 15 s of 23 min 36 s).
- It does not remove the fail-fast property for the checks that have it cheaply; the split is drawn at "needs a
  build input", not at "is slow".
