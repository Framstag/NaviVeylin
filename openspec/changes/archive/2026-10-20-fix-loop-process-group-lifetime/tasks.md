# Tasks — fix-loop-process-group-lifetime

Each task names the check that proves it. The change touches no compiled file, so its gate is the surface's own
`tools/loop-selftest.sh` (offline, no token, no device) — no Gradle task belongs to it.

- [x] 1. Red tests, before any fix, against `git archive HEAD tools` + this change's `tools/loop-cases.sh`:
      `clean#interruptedSuiteEndsItsProcessGroup`, `clean#reportsAndEndsAProcessLeftInAScratchTree` and
      `run#interruptedRunEndsTheChildItStarted`.
      *Verify*: each exited 1 with the message recorded in `evidence/red/` (timestamps 2026-10-20T11:35:37Z,
      11:35:38Z, 11:35:45Z).

- [x] 2. `tools/loop/lib.sh` — `loop_end_group <pgid>` (TERM, bounded grace, KILL) and `loop_end_pid <pid>`.
      *Verify*: `bash -n tools/loop/lib.sh`; used by the two callers below, whose cases are the real check.

- [x] 3. `tools/loop-selftest.sh` — the case in a process group of its own (`run_case`: `set -m`, background,
      `wait`), one `on_exit` that ends that group and then removes the scratch tree, and the case's stdin closed.
      *Verify*: `clean#interruptedSuiteEndsItsProcessGroup` green, and `clean#interruptedSuiteRemovesItsScratchTree`
      (the #210 case) still green — the tree still goes.

- [x] 4. `tools/loop/run.sh` — `active_child` set in `run_child` under `set -m` and cleared after `wait`;
      `on_exit` ends it before it writes the item's outcome.
      *Verify*: `run#interruptedRunEndsTheChildItStarted` green, `run#childOutlastingTheWallClockIsBlocked` (the
      cap) and `labels#trapRemovesInProgressOnSignal` (the traps) still green.

- [x] 5. `tools/loop/loop-clean.sh` — `leftover_pids <dir>` reading `/proc/<pid>/cwd`, the report's process
      lines, `--apply` ending exactly those before the removal, the process count in the summary.
      *Verify*: `clean#reportsAndEndsAProcessLeftInAScratchTree` green and
      `clean#reportsWithoutApplyAndRemovesWithIt` (the #210 case, whose summary line this change must not
      reshape) still green.

- [x] 6. `tools/loop-cases.sh` — the three cases registered in the `--list` array **and** the dispatch map.
      *Verify*: `bash tools/loop-selftest.sh --list | grep -c` shows all three, and each runs by `--case`.

- [x] 7. `tools/loop-mutations.sh` — 27.1 (the suite's group kill), 27.2 (the report's process reader), 27.3 (the
      run's child kill).
      *Verify*: `tools/loop-mutations.sh --only 27.1 27.2 27.3` — three MUTATION-RED, three restore-green.

- [x] 8. `.pi/skills/fix-loop/SKILL.md` and `REFERENCE.md` — the owner table's `loop-clean.sh` row, §"What the
      loop leaves on disk", invariant 3's cap paragraph, the reference section, and the skill's reference list.
      *Verify*: `tools/check-doc-routes.sh` and a grep that each file names `loop_end_group`, the process report
      and the run's child (a docs-only delta, so its check is structural, not a build).

- [x] 9. Evidence — every cited result copied into this change's `evidence/red/` and `evidence/green/`, and into
      the item's directory.
      *Verify*: `ls openspec/changes/archive/*fix-loop-process-group-lifetime/evidence/*/` after the archive.

- [x] 10. The gate, once: `tools/loop-selftest.sh` (the whole offline suite), quoted as it printed.
      *Verify*: the run's last line (`… all N cases passed`) and the failure count, recorded in
      `evidence/green/suite.txt`.

- [x] 11. Archive, and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
      *Verify*: the three new requirement headers are in the spec, and the archive directory exists.
