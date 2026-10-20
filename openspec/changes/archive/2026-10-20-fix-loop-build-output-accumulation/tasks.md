# Tasks — fix-loop-build-output-accumulation

Parent spec for every task below: `openspec/specs/agent-issue-loop/spec.md`, the three requirements this change
adds — **"A kept worktree carries the sources, not the build output"**, **"A suite ended by a signal removes its
scratch tree"** and **"One command reports and removes what the loop left"**. The delta is
`openspec/changes/fix-loop-build-output-accumulation/specs/agent-issue-loop/spec.md` (six scenarios).

## 1. Reproduce the defect (red on HEAD `5dd5b35`)

- [x] 1.1 Add `worktree#keepsSourcesNotBuildOutput` to `tools/loop-cases.sh`, registered in **both** the
  `--list` array and the dispatch map: a run whose child writes 102 400 B into `app/build` and puts a symlink at
  another recorded path (`core/build` → a directory outside the tree) must end by keeping the worktree with
  `app/build` gone, the symlink and its target intact, the change's source file present, the tree clean and the
  branch where the run left it, and must print the bytes it removed. **Parent:** the delta scenarios "The run
  removes the recorded build directories from the tree it keeps" and "The removal stays inside the worktree and
  leaves what is not build output". **Verification:** on HEAD it fails with `the kept worktree still carries its
  build output: 116K` — `evidence/red/keepsSourcesNotBuildOutput.txt`.
- [x] 1.2 Add `clean#interruptedSuiteRemovesItsScratchTree`, registered the same way: the project's own driver,
  driven with a cases file whose one case sleeps and with `LOOP_TMP_DIR` pointing at a directory of the case's
  own, creates its scratch tree there; `kill -TERM` on the suite's pid must leave no tree and must not exit 0.
  **Parent:** the delta scenario "A suite interrupted in the middle of a case leaves nothing behind".
  **Verification:** on HEAD it fails with `the suite never created a scratch tree under the recorded root …` —
  `evidence/red/interruptedSuiteRemovesItsScratchTree.txt`.
- [x] 1.3 Add `clean#reportsWithoutApplyAndRemovesWithIt`, registered the same way: `loop-clean.sh` on a kept
  worktree with 65 536 B of build output plus one stale and one fresh scratch tree must report without removing,
  remove with `--apply` (leaving the fresh tree and the sources), report the same bytes both times, and find
  nothing on a second `--apply`. **Parent:** the delta scenarios "The report removes nothing", "With `--apply` it
  removes what it reported, and nothing more" and "A second run is a no-op". **Verification:** on HEAD it fails
  with `…/tools/loop/loop-clean.sh: Datei oder Verzeichnis nicht gefunden` —
  `evidence/red/reportsWithoutApplyAndRemovesWithIt.txt`.

## 2. The fix

- [x] 2.1 `tools/loop/loop.conf`: record `LOOP_WORKTREE_SCRATCH_PATHS` (the module build directories a kept
  worktree loses; empty for a project with no build system), `LOOP_TMP_DIR` (empty means `.pi/tmp` under the
  project root) and `LOOP_TMP_STALE_MINUTES`, each with the measurement behind it. **Parent:** all three
  requirements — the list and the scratch root are what the first and the third read.
  **Verification:** `grep -n -A5 LOOP_WORKTREE_SCRATCH_PATHS tools/loop/loop.conf`, and
  `state#defaultsComeFromLoopConf` / `state#projectKeysAreReadFromLoopConf` unaffected.
- [x] 2.2 `tools/loop/lib.sh`: `LOOP_TMP_DIR`/`LOOP_SELFTEST_PREFIX`, `loop_tmp_resolve`, `loop_scratch_paths`,
  `loop_bytes_of` and `loop_scratch_remove` — the one implementation of "remove a recorded build path from a
  tree, or say why it stays", with the four refusals (outside the tree, a symlink, a directory that resolves
  outside it, a path git tracks). **Parent:** the first requirement's two scenarios. **Verification:** cases 1.1
  goes green, and mutation 25.1 (the symlink refusal) makes it red again —
  `evidence/green/validation.txt`.
- [x] 2.3 `tools/loop/worktree.sh`: the `strip-scratch` command, printing one line per recorded path and a total
  of the bytes removed, plus its header and usage lines. **Parent:** the first requirement.
  **Verification:** `bash tools/loop/worktree.sh --help` names it; case 1.1 asserts the total it prints.
- [x] 2.4 `tools/loop/run.sh`: `end_run` calls `worktree.sh strip-scratch` when it keeps the tree, and not under
  `--clean-worktree` or `--reset-worktree`. Nothing else in `run.sh` changes — not `run_child`, not the report,
  not the state, not the floor. **Parent:** the first requirement. **Verification:** cases 1.1,
  `worktree#keepsByDefaultAndRemovesOnRequest` and `run#resetWorktreeDiscardsWhatItNames` green, and mutation
  13.7 (the end-of-run form) still caught by the case it names.
- [x] 2.5 `tools/loop/loop-clean.sh`: the new entry point, `[--worktree <dir>] [--apply] [--quiet]`, reporting
  the recorded build paths of a worktree and the self-test scratch trees older than `LOOP_TMP_STALE_MINUTES`
  under `LOOP_TMP_DIR`, removing them only with `--apply`. **Parent:** the third requirement.
  **Verification:** case 1.3 green, and mutation 25.2 (the age gate) makes it red again —
  `evidence/green/validation.txt`.
- [x] 2.6 `tools/loop-selftest.sh`: trap `HUP`, `INT` and `TERM` beside `EXIT` (each leaving by `exit`, so the
  one removal runs), create the scratch tree under `LOOP_TMP_DIR` through `lib.sh` instead of a hard-coded
  `$root/.pi/tmp`, and say in the comment that `SIGKILL` cannot be trapped. **Parent:** the second requirement.
  **Verification:** case 1.2 green.
- [x] 2.7 `tools/check-portability.sh`: add the five recorded build paths to the value set, so an engine that
  names one fails the check. **Parent:** the first requirement's third scenario.
  **Verification:** `bash tools/check-portability.sh --quiet` clean at 103 values, and
  `tools/loop-mutations.sh --only 25.1` unaffected by it.
- [x] 2.8 `tools/loop-cases.sh`: the `run_clean` helper, the assertion in `run#engineNamesNoProjectFact` that
  the recorded list stays in the configuration, and the `core/build` line in the throwaway project's
  `.gitignore` (by whole path, not by `build/`, so the symlink is untracked and the removal has to refuse it on
  its own account). **Parent:** the first requirement's third scenario. **Verification:** the case 1.1 mutation
  check above turns red for the symlink reason, not the tracked-path reason.

## 3. The documents agree with the code

- [x] 3.1 `.pi/skills/fix-loop/SKILL.md`: a new section "What the loop leaves on disk" (the kept tree, the
  signal traps, and the three `loop-clean` invocations), the `worktree.sh` and `loop-clean.sh` rows of the owner
  table, the `--clean-worktree` row, and the reference list. **Parent:** all three requirements.
  **Verification:** read back against `worktree.sh`, `loop-clean.sh` and `tools/loop-selftest.sh` — the strings
  the skill quotes are the ones the scripts print.
- [x] 3.2 `.pi/skills/fix-loop/REFERENCE.md`: "Running the loop in a worktree" says a kept tree is a warm
  checkout, and a new section "What a kept worktree keeps, and what the loop leaves on disk" carries the
  2026-10-20 measurements, the two flags the removal respects and the `loop-clean` commands. **Parent:** all
  three requirements. **Verification:** `grep -n 'LOOP_WORKTREE_SCRATCH_PATHS\|loop-clean\|SIGKILL'
  .pi/skills/fix-loop/SKILL.md .pi/skills/fix-loop/REFERENCE.md tools/loop/loop.conf` names the same three facts
  in the same words.
- [x] 3.3 No `guidelines/*.md` change: the loop's disk cost is described in
  `.pi/skills/fix-loop/REFERENCE.md` ("Measured costs"), not in `guidelines/Build.md`, and `AGENTS.md` routes
  the loop to the skill. **Parent:** —. **Verification:** `grep -rn "build directory\|\.pi/tmp"
  guidelines/*.md` finds no statement this change contradicts.

## 4. Verification

- [x] 4.1 Red on HEAD `5dd5b35`: the three cases at `evidence/red/`, each the failure the task describes.
  **Parent:** all three requirements.
- [x] 4.2 Green on the fix: the three cases plus the two existing cases on this surface
  (`worktree#keepsByDefaultAndRemovesOnRequest`, `run#resetWorktreeDiscardsWhatItNames`) at
  `evidence/green/`, and the measured report against the developer host's kept worktree — 4 paths,
  **55 271 424 B** — at `evidence/green/loop-clean-on-the-kept-worktree.txt`.
- [x] 4.3 Revert-checks: mutation 25.1 (the symlink refusal in `lib.sh`) and 25.2 (the age gate in
  `loop-clean.sh`) — both red when mutated, both green again after the restore,
  `tools/loop-mutations.sh --only '25.1 25.2'` (`red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`).
- [x] 4.4 Structural checks: `bash -n` on the eight edited or new scripts,
  `bash tools/check-portability.sh --quiet` (43 engines, 2 templates, 103 values, OK),
  `openspec validate fix-loop-build-output-accumulation --strict` (valid), `openspec doctor` (clean) —
  `evidence/green/validation.txt`.
- [x] 4.5 Excluded on purpose: the whole offline suite (`tools/loop-selftest.sh` without `--case`) — the
  orchestrator runs it once on the merged tree; and any Gradle, JVM, device or emulator run, because no compiled
  file of the application is touched (`guidelines/Build.md` §2, the scope block).
