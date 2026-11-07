# Tasks — fix-loop-clean-scans-both-scratch-roots

Each task names its parent requirement `agent-issue-loop` → "One command reports and removes what the loop left".

## 1. The red case

- [x] 1.1 Add `clean#reportsTheWorktreesScratchTreesToo` to `tools/loop-cases.sh` (declared in `LOOP_SHELL_CASES`,
  dispatched in `loop_run_shell_case`): a throwaway project whose run left the kept worktree in place, a stale
  `loop-selftest.stale` and a young `loop-selftest.fresh` tree under `<worktree>/.pi/tmp`, a `sleep 300` standing
  in the stale one, and the invocation's own `LOOP_TMP_DIR` (from a case-local `loop.conf`) empty.
  **Verification:** `bash tools/loop-selftest.sh --case 'clean#reportsTheWorktreesScratchTreesToo'` — `FAIL`
  (exit 1) against `tools/loop/loop-clean.sh` at `6683376e`, 2026-10-23T15:36:22Z; tally in
  `evidence/red-HEAD.xml` (`tests="1" failures="1"`), transcript `evidence/red-HEAD.log`. The case exercises the
  requirement's scenario "The kept worktree's own scratch root is scanned beside the invocation's".

## 2. The second scratch root

- [x] 2.1 `tools/loop/lib.sh`: `loop_tmp_dir_for <tree>` — the scratch root another tree resolves, read from that
  tree's `tools/loop/loop.conf` as data and resolved against that tree (`""` → `<tree>/.pi/tmp`, relative →
  `<tree>/<value>`, absolute → itself).
  **Verification:** sourced by `tools/loop/loop-clean.sh`; exercised by the case of 1.1, whose worktree carries no
  `loop.conf` (default root) — and by 3.1, which runs it through the real command.
- [x] 2.2 `tools/loop/loop-clean.sh`: resolve `$tree`'s root into `worktree_tmp`, drop it when it is the
  invocation's own (so `--worktree .` scans one root), print one heading per root, and add it to the existing
  `for dir in …` glob so the age gate, `leftover_pids` and the removal apply unchanged to both roots.
  **Verification:** `bash tools/loop-selftest.sh --case 'clean#reportsTheWorktreesScratchTreesToo'` — `PASS`
  (`evidence/green-fix.log`).

## 3. The cases the rule already had

- [x] 3.1 Re-run the whole `clean` group, because the heading line and the glob are what three existing cases read.
  **Verification:** `bash tools/loop-selftest.sh --group clean` — `all 7 cases passed`
  (`evidence/affected-cases.log`): `clean#reportsWithoutApplyAndRemovesWithIt` (its `removed 2 path(s)` and
  report/removal byte equality), `clean#reportsAndEndsAProcessLeftInAScratchTree`, `clean#sweepsOnlyWhatIsInTheBase`
  and the four interrupted-suite/leftover cases.

## 4. Falsification

- [x] 4.1 Add record `53.1` to `tools/loop-mutations.sh`: the `worktree_tmp=$(loop_tmp_dir_for "$tree")` line
  becomes `worktree_tmp=""`, so the report scans one root again.
  **Verification:** `bash tools/loop-mutations.sh --check --only 53.1` → the record applies;
  `bash tools/loop-mutations.sh --only 53.1` → `RED 53.1 clean#reportsTheWorktreesScratchTreesToo`,
  `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/revert-check-53.1.log`).
- [x] 4.2 Confirm the three existing records that quote `loop-clean.sh` lines still apply (`25.2`, `28.2`, `42.3`):
  the lines they quote are outside the edited region.
  **Verification:** `bash tools/loop-mutations.sh --check --only '25.2 28.2 42.3'` — every selected record applies.

## 5. The guideline that names the command

- [x] 5.1 `guidelines/Backlog.md`, section "7. The loop": the leftovers paragraph gains the two roots, why they are two,
  and the measurement.
  **Verification:** `tools/check-doc-routes.sh` and the gate's own doc checks pass; no `§N` citation is added.

## 6. The gate, once

- [x] 6.1 `./tools/loop/gate.sh` from the change root, once, and its `LOOP-GATE:` line quoted into the evidence.
  **Verification:** the gate's `LOOP-GATE:` line names each suite's own summary, so it is quoted here as the three
  shell suites it ran rather than as one summed pair: `tools/loop-selftest.sh` `tests=191 failures=0`
  (`evidence/gate-tallies/tools_loop-selftest.sh.xml`), `bash tools/loop-mutations.sh --check` `tests=112 failures=0`
  (`evidence/gate-tallies/bash_tools_loop-mutations.sh_--check.xml`), `tools/loop-mutations-selftest.sh`
  `tests=34 failures=0` (`evidence/gate-tallies/tools_loop-mutations-selftest.sh.xml`) — 337 cases in all, with the
  Gradle suites evidenced by `BUILD SUCCESSFUL` and their executed task lines instead of a case count, which is the
  engine's existing convention. Every tally is copied into `evidence/` (`evidence/gate-tallies/`). One repair came after that run — `--help` prints a fixed header range and the header
  grew by three lines, so the range went `2,35` → `2,38`; no case reads the header, and the repair is recorded with
  its output in `evidence/post-gate-help-range.log` rather than covered by a re-run.
- [x] 6.2 `openspec validate fix-loop-clean-scans-both-scratch-roots --strict`, and
  `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-clean-scans-both-scratch-roots --root .`
  green. (The run itself printed it green; the transcript was not kept, which the round-1 review recorded as a
  NOTE. The round-2 correction re-ran both and kept the output — `evidence/round2-validation.log`.)
- [x] 6.3 Archive the change; the delta merges into `openspec/specs/agent-issue-loop/spec.md`.

## 7. The round-1 review's blocking finding — the dedup compared strings

`--worktree .` is resolved to `$LOOP_ROOT/.`, so `loop_tmp_dir_for` returned `$LOOP_ROOT/./.pi/tmp` and the
string comparison at the dedup never matched `$LOOP_ROOT/.pi/tmp`: one root was reported twice, its tree listed
twice and its bytes counted twice, and no case ran the flag (`--apply` hides it, because the second pass finds the
tree already gone).

- [x] 7.1 Add `clean#worktreeDotSweepsTheOneRootOnce` to `tools/loop-cases.sh` — a throwaway project with a stale
  `loop-selftest.stale` tree under its own `.pi/tmp`, run with `--worktree .`: one heading, one entry, `would remove
  1 path(s)`.
  **Verification:** red on the pre-correction tree, `expected '1', got '2'` (`evidence/red-round2.log`, exit 1,
  2026-10-23T16:00:36Z).
- [x] 7.2 `tools/loop/lib.sh`: `loop_same_dir <a> <b>` — two paths name the same existing directory when they
  resolve to it, whatever their spelling; empty or vanished paths are not the same directory. `tools/loop/loop-clean.sh`
  uses it for the dedup.
  **Verification:** `bash tools/loop-selftest.sh --case 'clean#worktreeDotSweepsTheOneRootOnce'` — `PASS`
  (`evidence/green-round2.log`).
- [x] 7.3 Record `53.2` in `tools/loop-mutations.sh`: the dedup line becomes `: # the dedup removed`.
  **Verification:** `bash tools/loop-mutations.sh --only "53.1 53.2"` — `RED 53.1`, `RED 53.2`, both restored green,
  `not-noticed: 0 · restore-failures: 0` (`evidence/revert-check-round2.log`); the failing assertion quoted through a
  copy of the engine, so the tree is not mutated to show it (`evidence/revert-check-round2-message.log`).
- [x] 7.4 The delta and the shipped spec gain the scenario `--worktree .` scans the one root it names once, carrying
  `clean#worktreeDotSweepsTheOneRootOnce`; `design.md` D2/D3 and the Risk row state the directory comparison; the
  guideline's absolute "two scratch roots, never one" says what happens when they are one.
  **Verification:** `openspec validate` green and both spec blocks byte-identical —
  `evidence/round2-validation.log`.
- [x] 7.5 The gate once for this correction, and the whole affected group.
  **Verification:** `./tools/loop/gate.sh` from the change root — exit 0, its `LOOP-GATE:` line naming each suite
  beside its seconds (`evidence/gate-round2.log`): `tools/loop-selftest.sh` `tests=192 failures=0`,
  `bash tools/loop-mutations.sh --check` `tests=113 failures=0`,
  `tools/loop-mutations-selftest.sh` `tests=34 failures=0`, and the Gradle suites with `BUILD SUCCESSFUL` and a
  tally that carries no case count (the engine's convention). Every tally is copied to
  `evidence/gate-tallies-round-2/`. `bash tools/loop-selftest.sh --group clean` — `all 8 cases passed`
  (`evidence/clean-group-round2.log`), with the round-1 log of the same group kept beside it.
