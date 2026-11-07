# What each file here is, taken 2026-10-24

All runs are offline shell runs (`bash tools/loop-selftest.sh`, `bash tools/loop-mutations.sh`, `bash
tools/loop/gate.sh`) inside this worktree (`fix/issue-296-the-gate-case-reads-the-stream`, off main's `0f366176`)
or in `.pi/worktrees/i296-base` (detached at `0f366176`, the base). No token, no network, no device, no Gradle
suite — nothing compiled changed. Timestamps are UTC; the host's local clock is UTC+2.

| file | what it is |
|---|---|
| `red-before.log` | the unmodified case on the base `0f366176`, twice: `PASS` with the host left alone, `FAIL` (`'gate: bash' not found in: LOOP-GATE-PROFILE: …`) with the host under contention. The same case, same tree, same hour — so the reading is a host bound, not a broken engine |
| `contend.sh` | the harness that makes that contention: 24 spinning shells at nice 0 beside a case run at nice 19, on 16 CPUs (`nproc`). The case's own `sleep 1.5` is wall-clock and does not slow down; everything the gate does before it announces the suite does |
| `green-after.log` | the fixed case in this change root, one plain run — `PASS` |
| `green-repeat.log` | the same command three more times — each `PASS`, each with its own in-file timestamp (`10:27:48Z`–`10:28:10Z`) |
| `green-contended.log` | the fixed case under the *same* contention the base failed under — `PASS` |
| `timing.log` | how long after it is started the gate reaches its announcement and the suite's first line, polled every 10 ms, in two batches: 43–45 ms on a quiet host and 115–141 ms while 20 spinning shells held it, against the case's 1500 ms window |
| `timing-probe.sh` | the harness that took those numbers: it builds a throwaway project, gates `notes/*` onto the case's own stand-in suite, starts `gate.sh` and polls its console |
| `host-bound-probe.sh` · `host-bound-probe.log` | the item's shape made deterministic, three readings in one run: (1) a host whose `git` costs an added 0.6 s per call — the base reading at the fixed instant holds the gate's first line and nothing else, **red**; (2) the fixed reading on that same host — **green**; (3) a gate that captures the whole output and prints it after the command returned, read by the fixed form — **red**, `the output is streamed, not replayed` |
| `mutation-46.1.log` | `bash tools/loop-mutations.sh --only 46.1` — the shipped record that replaces the gate's `\| tee "$stream"` with a capture into the log: the case still goes red, and the restore goes green |
| `structural-checks.log` | the checks that stand in for a build for a delta that compiles nothing: `bash -n tools/loop-cases.sh`, `openspec validate … --strict`, `openspec doctor`, `tools/check-doc-routes.sh`, `evidence-check.sh` |
| `gate.log` | the delta's gate: `bash tools/loop/gate.sh --base 0f366176` — `selected 209 of 209 cases`, `LOOP-GATE: tests=209 failures=1 errors=0`, 1111 s |
| `base-red-attribution.log` | whose red that one failure is: the same case alone on the base, with the identical message, beside the archived record of the same red and the reason this delta cannot reach it |
| `post-archive-checks.log` · `postarchive-checks.sh` | the checks the *archived* delta owes — the gate's own list for the `openspec/**` and `*.md` paths it then carries — each with its `exit=` recorded; the loop suite is not re-run for them and the exclusion is named in the header |
| `review.md` | the independent read-only review of the diff against the delta's scenarios |
| `review-diff.patch` | the 40-line diff the review read, kept here so its input is resolvable after the round (`review.md` names the session-local copy it was handed) |
| `handrun46.sh` · `timing-loaded.sh` | the hand-applied mutation of `46.1`, and the timing probe under held CPUs |

## What is not asserted

- **The case was never seen red on an uncontended host, and that is the honest shape of this defect.** Seven
  polled readings put the gate's announcement 43–141 ms in (`timing.log`), so the 1500 ms sample has a ~10–35×
  margin here; the red is reproduced deliberately (`contend.sh`, `host-bound-probe.sh`) instead of waited for.
  The change is owed because the reading is a bound on the host, not because a flake was observed on this
  machine.
- **The contention the red rests on is made, not found.** 24 spinning shells at nice 0 next to a process at
  nice 19 is a *shape* of a loaded host, taken to be deterministic rather than to be typical; what the item
  reports is the same mechanism at a smaller factor. The pairing (base FAIL, same base PASS uncontented, fixed
  PASS under the same contention) is what the evidence is: three readings of one case, not a rate.
- **No full-suite baseline on the base.** The gate's single red is attributed with §4d's step 1 — the case alone
  on the base, same message (`base-red-attribution.log`) — and the base's full-suite red set for this case is
  already on record in the archive (`baseline-main-red.log`). A second 19-minute suite on the base would add no
  attribution power, and the exclusion is named here rather than left implicit.
- **The 30 s bound has no assertion of its own.** The wait's body is the assertion: a suite whose line never
  arrives fails the first-line assertion after the bound expires, which is what `46.1` does — at the price of
  the bound, measured (`mutation-46.1.log`: 36.9 s for the record's two case runs, and the first-line assertion
  quoted from the same substitution applied by hand).
- **The replay half of the guard has no mutation record of its own.** Only the silent-stream half is in
  `tools/loop-mutations.sh` (`46.1`); the replayed one is a hand probe (`host-bound-probe.sh`, run 3), because
  the harness's one-line substitution cannot restructure the pipeline into a capture. The same split, for the
  same reason, is in the sibling change's evidence.
- **No case exercises the path of a gate that dies before it announces the suite**; the design names it as a
  risk (the case then costs its bound) and no log measures it.
- **The sibling case `run#prescreenAnnouncesItselfBeforeItReturns` was not re-measured here.** It has the same
  reading, fixed by the change that owns #287 on a branch that is not on main; this change's gate re-runs it and
  the gate reports no failure of it.
- **No Gradle, device or flavor check.** The delta changes one shell case's reading and one spec scenario:
  nothing compiles, no flavor is affected, and the loop's own suite is the whole gate (`guidelines/Build.md` §2,
  the scope block).
