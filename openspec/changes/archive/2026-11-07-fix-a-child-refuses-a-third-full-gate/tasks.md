# Tasks — a third full gate run in one child is refused with the scoped command

Every task names its own verification, and every verification below was run in the change root. The red case was
written first and is red on the base: with the engine as the base left it, the case's third run measured instead of
refusing it — `expected '3', got '0'`, `loop-selftest: 1 of 1 cases failed` (`evidence/red-run.log`,
2026-11-07T02:44:42Z).

- [x] 1. Record the bound in `tools/loop/loop.conf`: `LOOP_GATE_RUNS_MAX=2` in the project-specific block, with the
  measurement behind it and the rule that an unrecorded key or `0` is no bound.
  - verified: `grep -n '^LOOP_GATE_RUNS_MAX=2$' tools/loop/loop.conf` → `465:LOOP_GATE_RUNS_MAX=2`

- [x] 2. Read the bound and count the runs in `tools/loop/gate.sh`: `LOOP_GATE_RUNS_MAX` through `loop_conf_value`,
  the session id from the last `session` line of `LOOP_CHILD_EVENTS`, and one record line per full run it starts,
  written beside that stream (`<events>.gate-runs`) so the count is produced by the runs it counts.
  - verified: `bash -n tools/loop/gate.sh tools/loop-cases.sh tools/loop-mutations.sh tools/loop-selftest.sh` →
    no output, exit 0

- [x] 3. Refuse the run that would exceed it, after `--plan` and before the first step: exit `3`, name every earlier
  run, name the bound, print `scoped: <command>` for every command the delta owes, write no `LOOP-GATE:` verdict, and
  leave no record. Name the new status in the usage header.
  - verified: `bash tools/loop-selftest.sh --case 'gate#aThirdFullRunInOneSessionIsRefused'` →
    `PASS`, exit 0 (`evidence/green-run.log`, 2026-11-07T02:45:21Z), and the plan's own first line for a delta that
    writes a case — `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
    (`evidence/declaration-check.log`, 2026-11-07T02:45:59Z)

- [x] 4. Do not leak the run's own stream into the suite: `tools/loop-selftest.sh` unsets `LOOP_CHILD_EVENTS`, so a
  case that runs the gate is not charged for the runs the child that started the suite made.
  - verified: `bash tools/loop-selftest.sh --group gate` → `all 28 cases passed`
    (`evidence/gate-group.log`, 2026-11-07T02:46:11Z; the group was 27 cases before this delta), and the two groups
    whose cases start the run itself — `--group driver` `all 22 cases passed`, `--group prompt` `all 16 cases passed`
    (`evidence/stream-groups.log`, 2026-11-07T02:51:37Z)

- [x] 5. Name the refusal where the loop's procedure and the phase that obeys it live:
  `guidelines/Backlog.recipes.md`:530 ("The gate and its suite") and `tools/loop/prompts/worker-gate.md` step 2.
  - verified: `grep -n 'LOOP_GATE_RUNS_MAX' guidelines/Backlog.recipes.md tools/loop/prompts/worker-gate.md` →
    `565`, `82`

- [x] 6. Record the invariant in `tools/loop-mutations.sh` as record `93.1` (the `85.x`–`92.x` ids are taken on this
  base by #782, #794, #796, #803, #802, #797 and #790), so the refusal is one mutation away from the tree.
  - verified: `bash tools/loop-mutations.sh --only 93.1` → `RED 93.1 … restore … green again`,
    `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/mutation-93.1.log`, 2026-11-07T02:46:34Z)

- [x] 7. Run the structural checks the change's own paths owe outside the suite: portability, the two documentation
  checks, the OpenSpec hygiene check, the TODO references, and the mutation harness's own check and self-test.
  - verified: `evidence/checks.log` (2026-11-07T02:46:45Z) — `check-portability: OK`, `check-doc-routes: all 329
    section references resolve`, `check-openspec-hygiene: … consistent`, `check-todo-references: 66 citation(s)
    resolve`, `loop-mutations: all 266 cases passed`, both self-tests `all … cases passed`

- [x] 8. The gate itself is the gate child's: this worker's run deferred it (`LOOP-DIFF-READY`), so the full
  `--from-git` selection the delta owes is run there, once, and its tallies are the gate evidence.
  - verified: `./tools/loop/gate.sh` → `LOOP-GATE: tests=878 failures=0 errors=0` (`evidence/gate-run.log`, the run's
    own log, 2026-11-07T04:12Z), carried by `evidence/gate-summary.xml` and by the run's own per-step XMLs in
    `evidence/gate-tallies/` — the seven steps that carry cases sum to 878, 0 failures, 0 errors.
