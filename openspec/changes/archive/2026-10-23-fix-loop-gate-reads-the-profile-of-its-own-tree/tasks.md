# Tasks — the profile beside the engine, and the log that names it

## 1. The red cases

- [x] 1.1 **`gate#theGateNamesTheProfileItRead`** added to `tools/loop-cases.sh`: a throwaway project whose gate is
      run once with a profile copied outside the engine and once with the engine's own, asserting that the log names
      the file that was read and that only the pinned one is called pinned. Run against the engine as `850dea2c`
      carries it — only the case list had changed at that point — 2026-10-23T14:18:02Z: FAIL,
      `'LOOP-GATE-PROFILE: …/pinned.conf' not found in: LOOP-GATE: nothing owed …`. Transcript
      `evidence/red-on-head.log`, tally `tests="2" failures="2"` in `evidence/red-on-head.xml`. **Verification:** the
      case names the line it looked for in its own failure message.
- [x] 1.2 **`gate#theChildReadsTheProfileOfTheTreeItWorksIn`** added beside it: the fixture project carries an engine
      of its own (`tools/loop`, `tools/issues/lib.sh`), the stub worker's action records the profile the run handed
      its environment and runs the worktree's own gate there, and the case asserts that the gate names the worktree's
      profile and that the environment carried none. Same run, same transcript, FAIL with
      `handed=/home/tim/projects/NaviVeylin/tools/loop/loop.conf` and no pin line at all. **Verification:** the
      failure message prints the child's two facts.

## 2. The fix

- [x] 2.1 `tools/loop/lib.sh`: `loop_resolve` records the file it sourced as `LOOP_CONF_PATH`. **Verification:**
      `bash tools/loop-selftest.sh --group state` → all 14 cases passed (the seam cases are the ones that pin it).
- [x] 2.2 `tools/loop/gate.sh`: `LOOP-GATE-PROFILE:` at the top of a running gate, with the pinned annotation when
      the profile is not the one beside the engine, and nothing in `--plan`'s output. **Verification:** the two red
      cases green (`evidence/green-focused.log`), and `--plan` unchanged for the four measure cases.
- [x] 2.3 `tools/loop/run.sh`: `export -n LOOP_CONF` in place of the export, and the empty-agent refusal naming
      `LOOP_CONF_PATH`. **Verification:** `bash tools/loop-selftest.sh --group state` (the cases that pin a profile
      for a run) and `run#engineNamesNoProjectFact` (the engine may name no project fact). The state an operator's
      shell has — no `LOOP_CONF` at all, which the driver's own export keeps every case from reaching — is probed
      directly: `export -n` under `set -uo pipefail` survives it and `LOOP_CONF_PATH` names the profile beside the
      engine (`evidence/unpinned-probe.log`).
- [x] 2.4 `tools/loop/measure.sh`: `LOOP-MEASURE-PROFILE` as the block's second line. **Verification:**
      `bash tools/loop-selftest.sh --group measure` → all 4 cases passed, the block-whole-output case among them.

## 3. The falsification

- [x] 3.1 Records `52.1` (the run's export restored), `52.2` (the pin line removed) and `52.3` (a pinned profile
      claiming to be the engine's own) in `tools/loop-mutations.sh`. **Verification:**
      `bash tools/loop-mutations.sh --check --files "tools/loop/gate.sh tools/loop/run.sh"` → all 56 selected records
      apply; `--only 52.1`, `--only 52.2`, `--only 52.3` → `red-when-mutated: 1 · not-noticed: 0 ·
      restore-failures: 0` each, in `evidence/revert-check-52.{1,2,3}.log`.

## 4. The record of the rule

- [x] 4.1 The delta `specs/agent-issue-loop/spec.md` adds "The gate reads the profile of the tree it measures" with
      its two scenarios naming the two cases. **Verification:** `openspec validate
      fix-loop-gate-reads-the-profile-of-its-own-tree --strict`.
- [x] 4.2 `guidelines/Backlog.md`, its section "The loop": one paragraph stating which profile a gate reads and that its log
      names it. **Verification:** `tools/check-doc-routes.sh`, which the profile owes for a `.md` path.

## 5. The gate and the archive

- [x] 5.1 The project's gate, run once from the change root: `./tools/loop/gate.sh`. The delta touches no compiled
      file, so the gate owes the shell suites and none of the build. **Verification:** the gate's own line and the
      tallies it wrote (`evidence/gate.log`, `evidence/gate-tallies/`).
- [x] 5.2 Evidence and archive: every cited XML copied into `evidence/`, the pointers checked mechanically
      (`bash .pi/skills/fix-loop/scripts/evidence-check.sh
      openspec/changes/fix-loop-gate-reads-the-profile-of-its-own-tree --root .`), the change archived and its delta
      confirmed in `openspec/specs/agent-issue-loop/spec.md`. **Verification:** the check's output and the archive
      directory (`evidence/archive.log`).
