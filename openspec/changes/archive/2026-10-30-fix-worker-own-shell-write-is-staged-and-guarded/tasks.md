# Tasks — fix-worker-own-shell-write-is-staged-and-guarded

## 1. Stage the shape the fixture cannot stage

- [x] 1.1 `tools/loop/fixtures/pi`: add `PI_STUB_PROJECT_SHELL` — a script run before the `cd "$LOOP_WORKTREE"`,
      so in the project the child was started in — and name it in the fixture's env header.
      Verify: `bash -n tools/loop/fixtures/pi` and `grep -n 'PI_STUB_PROJECT_SHELL' tools/loop/fixtures/pi` (two
      lines: the header entry and the block). No existing case sets it, so no existing case changes.
- [x] 1.2 `tools/loop-cases.sh`: `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, registered in the case list
      and the dispatch, staging the shape through the hook and asserting `done`, `closed`, the recorded path and
      `attributed=none`.
      Verify: `bash tools/loop-selftest.sh --case run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` — it is red
      on HEAD's content (`evidence/red-on-head.xml`, `tests="1" failures="1"`) and green once 1.1 lands.

## 2. Record where the reader is

- [x] 2.1 `guidelines/Backlog.md`'s "The loop" section: the sentence at `:322`–`:325` names the case that pins the worker's-own-shell
      shape it names, so a reader of the rule is routed to the test of it.
      Verify: `tools/check-doc-routes.sh` still exits 0 (the clause adds no section reference), and
      `grep -n 'theWorkersOwnShellWriteIsRecordedAndNotPlaced' guidelines/Backlog.md` names one line.
- [x] 2.2 The spec delta's scenario names exactly one case, and the requirement it modifies restates the shipped
      scenarios verbatim.
      Verify: `openspec validate fix-worker-own-shell-write-is-staged-and-guarded --strict` exits 0.

## 3. Verify the change

- [x] 3.1 The cases the delta touches, run focused, all green:
      `bash tools/loop-selftest.sh --case 'run#*Write*'` and the placement/peer pair
      (`run#aWriterTheRunCanPlaceBlocksTheItem`, `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`), which are
      the two verdicts this change must not move.
- [x] 3.2 The project's gate, once, from the change root: `./tools/loop/gate.sh`.
      Verify: the `LOOP-GATE:` line names `failures=0 errors=0`, and the tally XMLs the change cites are copied
      into `evidence/`.
- [x] 3.3 `bash tools/loop/evidence-check.sh openspec/changes/fix-worker-own-shell-write-is-staged-and-guarded --root .`
      reports no dangling pointer.
- [x] 3.4 Archive with `openspec archive`, confirm the directory is under `openspec/changes/archive/` and that the
      new scenario merged into `openspec/specs/agent-issue-loop/spec.md`.

No compiled file is touched, so no build task stands here: the structural check of the fixture
(`bash -n`, 1.1) and the case that runs it (1.2) are what a build would otherwise stand in for.
