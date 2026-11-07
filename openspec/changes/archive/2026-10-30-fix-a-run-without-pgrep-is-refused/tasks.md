# Tasks — a run without `pgrep` refuses

Each task names its parent scenario and how it is verified. No task builds: the delta touches no compiled file, so
the verification is the structural checks and the loop's own gate (`guidelines/Build.md` §2, the scope block).

## 1. The red case

- [x] 1.1 `tools/loop-cases.sh`: add `run#aHostWithoutThePlacementReadingIsRefused` — a `PATH` of symlinks holding
      every executable of the host's `PATH` except `pgrep`, then `run.sh --items 0`; assert status 2, a sentence
      naming `pgrep` and the outcome it would cause, no worktree and no child. Declare it in `LOOP_SHELL_CASES` and
      in the dispatch table.
      *Parent*: `agent-issue-loop` / *A host that cannot read the child's tree refuses the run*.
      *Verification*: `bash tools/loop-selftest.sh --case 'run#aHostWithoutThePlacementReadingIsRefused'`.
- [x] 1.2 Record the red-on-HEAD run before the fix: `bash evidence/red-on-head.sh >
      evidence/red-on-head.xml 2> evidence/red-on-head.log` on the base's `run.sh` — `tests=1 failures=1`,
      `expected '2', got '3'` (2026-10-30T09:10:53Z), copied into this change's `evidence/` and the item's
      directory.
      *Verification*: the XML exists and its `failures=` is 1 on HEAD.

## 2. The check

- [x] 2.1 `tools/loop/run.sh`: one `command -v pgrep` check beside the `LOOP_AGENT_CMD` check in the header, whose
      `loop_die` names the command and the reading it serves.
      *Parent*: `agent-issue-loop` / *A host that cannot read the child's tree refuses the run*.
      *Verification*: 1.1's case prints `PASS  run#aHostWithoutThePlacementReadingIsRefused`.

## 3. The revert direction

- [x] 3.1 `tools/loop-mutations.sh`: record `12.13` — delete the check (`if ! command -v pgrep …` → `if false;
      then`), case `run#aHostWithoutThePlacementReadingIsRefused`.
      *Parent*: `agent-issue-loop` / *A host that cannot read the child's tree refuses the run*.
      *Verification*: `bash tools/loop-mutations.sh --only 12.13` prints `RED 12.13 …` for the mutation and
      `GREEN 12.13 …` after the restore; `bash tools/loop-mutations.sh --check --only 12.13` reports the record
      applies.
- [x] 3.2 Re-run 1.1 green after the restore and copy the mutation log into `evidence/`.
      *Verification*: `PASS` line in the log and the restore line `green again`.

## 4. The documents the rule routes to

- [x] 4.1 `guidelines/Backlog.md`, section 7 "The loop": the placement-reading paragraph states that the run
      refuses a host without `pgrep` at its header and that the suite's refusal stays.
      *Parent*: `agent-issue-loop` / *A host that cannot read the child's tree refuses the run*.
      *Verification*: `tools/check-doc-routes.sh` and `tools/check-portability.sh` pass (the paragraph names no
      project path); the paragraph reads true against `run.sh`.
- [x] 4.2 Keep the spec delta's scenario list and this file's case name in step with the code.
      *Verification*: `bash tools/check-openspec-hygiene.sh`.

## 5. The gate and the archive

- [x] 5.1 `./tools/loop/gate.sh` once, from the change root; copy the tally XMLs and the gate log into
      `evidence/` at the moment they are produced.
      *Verification*: the log's `LOOP-GATE:` line reads `failures=0 errors=0`.
- [x] 5.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-a-run-without-pgrep-is-refused --root .` after
      every pointer is written; fix what it reports.
      *Verification*: the check reports no FAIL.
- [x] 5.3 Archive the change and confirm the delta merged into
      `openspec/specs/agent-issue-loop/spec.md` with the added scenario.
      *Verification*: the archive directory exists and the capability spec carries *A host that cannot read the
      child's tree refuses the run*.
